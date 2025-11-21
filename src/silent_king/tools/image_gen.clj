(ns silent-king.tools.image-gen
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [hato.client :as http]
            [clojure.tools.cli :as cli])
  (:import [java.awt.image BufferedImage]
           [java.awt Color RenderingHints]
           [javax.imageio ImageIO]
           [java.util Base64]))

;; =============================================================================
;; Utilities
;; =============================================================================

(defn log [msg]
  (println msg))

(defn error [msg]
  (binding [*out* *err*]
    (println "Error:" msg)))

(defn exit [status msg]
  (if (zero? status)
    (log msg)
    (error msg))
  (System/exit status))

(defn file-exists? [path]
  (.exists (io/file path)))

(defn pad-number [n width]
  (format (str "%0" width "d") n))

;; =============================================================================
;; Image Handling
;; =============================================================================

(defn get-mime-type [file-path]
  (let [lower (str/lower-case (str file-path))]
    (cond
      (str/ends-with? lower ".jpg") "image/jpeg"
      (str/ends-with? lower ".jpeg") "image/jpeg"
      (str/ends-with? lower ".png") "image/png"
      (str/ends-with? lower ".webp") "image/webp"
      (str/ends-with? lower ".gif") "image/gif"
      :else "image/png")))

(defn encode-base64 [file-path]
  (let [file (io/file file-path)
        bytes (java.nio.file.Files/readAllBytes (.toPath file))
        b64 (.encodeToString (Base64/getEncoder) bytes)]
    {:b64 b64
     :mime (get-mime-type file-path)}))

(defn decode-base64 [base64-str]
  (.decode (Base64/getDecoder) base64-str))

(defn save-image [data file-path]
  (let [file (io/file file-path)]
    (io/make-parents file)
    (with-open [out (io/output-stream file)]
      (.write out data))))

(defn load-image [file-path]
  (ImageIO/read (io/file file-path)))

(defn create-grid [image-files output-path tile-size padding]
  (if (empty? image-files)
    (log "No images to create grid.")
    (try
      (let [images (map load-image image-files)
            count (count images)
            cols (int (Math/ceil (Math/sqrt count)))
            rows (int (Math/ceil (/ count cols)))
            width (+ (* cols tile-size) (* (inc cols) padding))
            height (+ (* rows tile-size) (* (inc rows) padding))
            grid (BufferedImage. width height BufferedImage/TYPE_INT_ARGB)
            g2d (.createGraphics grid)]
        
        (.setRenderingHint g2d RenderingHints/KEY_INTERPOLATION RenderingHints/VALUE_INTERPOLATION_BILINEAR)
        (.setRenderingHint g2d RenderingHints/KEY_RENDERING RenderingHints/VALUE_RENDER_QUALITY)
        (.setRenderingHint g2d RenderingHints/KEY_ANTIALIASING RenderingHints/VALUE_ANTIALIAS_ON)
        
        (.setColor g2d (Color. 20 20 20)) ;; Dark background
        (.fillRect g2d 0 0 width height)
        
        (doseq [[i img] (map-indexed vector images)]
          (let [col (mod i cols)
                row (quot i cols)
                x (+ padding (* col (+ tile-size padding)))
                y (+ padding (* row (+ tile-size padding)))]
            (.drawImage g2d img x y tile-size tile-size nil)))
        
        (.dispose g2d)
        (ImageIO/write grid "png" (io/file output-path))
        (log (str "Grid saved to: " output-path)))
      (catch Exception e
        (error (str "Failed to create grid: " (.getMessage e)))))))

;; =============================================================================
;; Prompt Parsing
;; =============================================================================

(defn parse-prompts-file [path]
  (let [lines (str/split-lines (slurp path))
        context (atom nil)
        style (atom nil)
        prompts (atom [])]
    (doseq [line (map str/trim lines)]
      (cond
        (str/starts-with? line "# Context:")
        (reset! context (str/trim (subs line 10)))

        (str/starts-with? line "# Style:")
        (reset! style (str/trim (subs line 8)))

        (str/starts-with? line "#")
        nil ;; Ignore comments

        (not (str/blank? line))
        (swap! prompts conj line)))

    (when-not @context (throw (ex-info "Missing '# Context:' line" {})))
    (when-not @style (throw (ex-info "Missing '# Style:' line" {})))
    (when (empty? @prompts) (throw (ex-info "No prompts found" {})))

    {:context @context
     :style @style
     :prompts @prompts}))

;; =============================================================================
;; OpenRouter API
;; =============================================================================

(def api-endpoint "https://openrouter.ai/api/v1/chat/completions")
(def model "google/gemini-2.5-flash-image")

(defn build-payload [image-data-list prompt context style size]
  (let [has-spaceship? (> (count (str/split (str/lower-case context) #"spaceship")) 1)
        spaceship-extra (if has-spaceship?
                          "CRITICAL: Must be viewed from directly above (top-down perspective). "
                          "")
        full-prompt (str "Create a stylized " context ". " prompt ". "
                         spaceship-extra
                         "Style: " style ". "
                         "IMPORTANT: Generate with a solid BLACK background. "
                         "The object should be the only visible element, centered in frame. "
                         "Size: " size "x" size " pixels. "
                         "Output: PNG format with RGBA color mode for transparency.")]
    {:model model
     :messages [{:role "user"
                 :content (concat
                           (for [{:keys [b64 mime]} image-data-list]
                             {:type "image_url"
                              :image_url {:url (str "data:" mime ";base64," b64)}})
                           [{:type "text"
                             :text full-prompt}])}]
     :max_tokens 2048}))

(defn extract-image-data [response-body]
  (let [json (json/read-str response-body :key-fn keyword)
        choices (:choices json)
        message (get-in choices [0 :message])
        images (:images message) ;; Sometimes it's here
        content (:content message)] ;; Sometimes it's markdown in content

    (cond
      (not-empty images)
      (let [url (get-in images [0 :image_url :url])]
        {:url url})

      content
      (if-let [match (re-find #"!\[.*?\]\((.*?)\)" content)]
        {:url (second match)}
        (throw (ex-info "Could not find image URL in content" {:content content})))

      :else
      (throw (ex-info "No image data found in response" {:response json})))))

(defn generate-variation [api-key base64-images prompt context style size]
  (let [payload (build-payload base64-images prompt context style size)
        options {:headers {"Authorization" (str "Bearer " api-key)
                           "Content-Type" "application/json"}
                 :body (json/write-str payload)
                 :as :string
                 :throw-exceptions false}]
    (try
      (let [response (http/post api-endpoint options)]
        (if (= 200 (:status response))
          (let [{:keys [url]} (extract-image-data (:body response))]
            (if (str/starts-with? url "data:image/")
              (let [b64 (second (str/split url #";base64,"))]
                (decode-base64 b64))
              ;; It's a URL, download it
              (let [img-resp (http/get url {:as :byte-array})]
                (if (= 200 (:status img-resp))
                  (:body img-resp)
                  (throw (ex-info "Failed to download image from URL" {:status (:status img-resp)}))))))
          (throw (ex-info (str "API Error " (:status response)) {:body (:body response)}))))
      (catch Exception e
        (throw (ex-info (str "Request failed: " (.getMessage e)) {:cause e}))))))

;; =============================================================================
;; Main Logic
;; =============================================================================

(def cli-options
  [["-p" "--prompts FILE" "Prompts file"]
   ["-r" "--reference-images FILES" "Reference images (space separated)"
    :multi true
    :default []
    :update-fn conj]
   ["-o" "--output DIR" "Output directory"]
   ["-s" "--size PIXELS" "Image size"
    :default 1024
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 %) "Must be positive"]]
   ["-h" "--help"]])

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options) (exit 0 summary)
      errors (exit 1 (str/join "\n" errors)))

    (let [{:keys [prompts reference-images output size]} options
          _ (when-not prompts (exit 1 "Missing --prompts"))
          _ (when (empty? reference-images) (exit 1 "Missing --reference-images"))
          _ (when-not output (exit 1 "Missing --output"))
          
          ;; Flatten reference images in case they were passed as multiple args or multiple flags
          ;; Actually cli/parse-opts handles repeated flags with :multi true, but space separated files 
          ;; might end up in 'arguments' if not handled carefully. 
          ;; The Lean version took `--reference-images a.png b.png`. 
          ;; tools.cli doesn't support variable number of args for a flag easily.
          ;; User might need to do `--reference-images a.png --reference-images b.png` OR
          ;; we can look at remaining arguments if we change the CLI structure.
          ;; For now, let's assume the user uses repeated flags OR we parse `arguments` if they follow the pattern.
          ;; But to match Lean exact behavior: `--reference-images f1 f2` is hard with standard CLI parsers.
          ;; Let's just stick to repeated flags or assume the user adapts.
          ;; wait, the user's previous scripts invoke it as:
          ;; `--reference-images assets/glyphs/carrier.png assets/glyphs/battleship.png`
          ;; So I should probably look at `arguments` and custom parse or just accept that `tools.cli` behaves differently.
          ;; Let's do a quick hack: if reference-images is present, we also check `arguments` for remaining files?
          ;; No, let's rely on the user passing them correctly or update the script.
          ;; Actually, let's look at how `tools.cli` works. It stops parsing options at the first non-option.
          ;; So `--reference-images a.png b.png` -> `a.png` is value, `b.png` is in `arguments`.
          ;; So I'll combine `(:reference-images options)` and `arguments` if they look like files.
          
          all-refs (concat (:reference-images options) arguments)
          api-key (or (System/getenv "OPENROUTER_API_KEY")
                      (let [env-file (io/file ".env")]
                        (when (.exists env-file)
                          (some-> (slurp env-file)
                                  (str/split-lines)
                                  (->> (filter #(str/starts-with? % "OPENROUTER_API_KEY="))
                                       first)
                                  (str/split #"=" 2)
                                  second
                                  str/trim))))]

      (when-not api-key
        (exit 1 "OPENROUTER_API_KEY not found in environment or .env"))

      (when (empty? all-refs)
        (exit 1 "No reference images provided."))

      (try
        (let [prompts-data (parse-prompts-file prompts)
              _ (log (str "Context: " (:context prompts-data)))
              _ (log (str "Style: " (:style prompts-data)))
              _ (log (str "Prompts: " (count (:prompts prompts-data))))
              
              ref-images-b64 (mapv encode-base64 all-refs)
              
              output-dir (io/file output)
              _ (.mkdirs output-dir)
              
              ;; Copy reference images
              ref-dir (io/file output-dir "reference_images")
              _ (.mkdirs ref-dir)]
          
          (doseq [ref all-refs]
            (let [src (io/file ref)
                  dest (io/file ref-dir (.getName src))]
              (io/copy src dest)
              (log (str "Copied reference: " (.getName src)))))

          (log "Starting generation...")
          
          (let [results (atom [])
                batches (partition-all 10 (map-indexed vector (:prompts prompts-data)))]
            
            (doseq [batch batches]
              (let [futures (doall (for [[i prompt] batch]
                                     (future
                                       (let [idx (inc i)
                                             filename (str (pad-number idx 3) ".png")
                                             path (io/file output-dir filename)]
                                         (try
                                           (let [img-data (generate-variation api-key ref-images-b64 prompt (:context prompts-data) (:style prompts-data) size)]
                                             (save-image img-data path)
                                             (log (str "✓ " filename))
                                             {:index idx :prompt prompt :status "success" :filename filename})
                                           (catch Exception e
                                             (error (str "✗ " filename " - " (.getMessage e)))
                                             {:index idx :prompt prompt :status "error" :error (.getMessage e)}))))))]
                (run! (fn [f] (swap! results conj @f)) futures)))
            
            ;; Save metadata
            (let [metadata-file (io/file output-dir "generation_metadata.json")
                  log-file (io/file output-dir "generation_log.json")]
              (with-open [w (io/writer metadata-file)]
                (json/write @results w))
              (with-open [w (io/writer log-file)]
                (json/write @results w))
              (log (str "Metadata saved to " metadata-file)))
            
            ;; Create Grid
            (let [success-files (->> @results
                                     (filter #(= "success" (:status %)))
                                     (map #(io/file output-dir (:filename %)))
                                     (map #(.getPath %)))]
              (create-grid success-files (io/file output-dir "grid.png") 256 16))
            
            (log "Done.")))
        (catch Exception e
          (exit 1 (str "Fatal error: " (.getMessage e))))))))

