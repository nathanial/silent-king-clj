#!/usr/bin/env clojure

(ns generate-atlas
  (:import [java.io File]
           [javax.imageio ImageIO]
           [java.awt.image BufferedImage]
           [java.awt Graphics2D RenderingHints]))

(set! *warn-on-reflection* true)

(def ^:const black-threshold 20)
(def ^:const atlas-size 4096)
(def ^:const tile-size 256)
(def ^:const tiles-per-row (/ atlas-size tile-size))

(defn remove-black-background [^BufferedImage img]
  "Make near-black pixels transparent in a BufferedImage"
  (let [width (.getWidth img)
        height (.getHeight img)
        ^BufferedImage result (BufferedImage. width height BufferedImage/TYPE_INT_ARGB)]

    ;; Copy pixels, making near-black ones transparent
    (dotimes [y height]
      (dotimes [x width]
        (let [rgb (.getRGB img x y)
              r (bit-and (unsigned-bit-shift-right rgb 16) 0xFF)
              g (bit-and (unsigned-bit-shift-right rgb 8) 0xFF)
              b (bit-and rgb 0xFF)]

          (if (and (< r black-threshold)
                   (< g black-threshold)
                   (< b black-threshold))
            (.setRGB result x y (unchecked-int 0x00000000))
            (.setRGB result x y (bit-or (unchecked-int 0xFF000000) rgb))))))

    result))

(defn downscale-image [^BufferedImage img target-size]
  "Downscale image to target-size x target-size with high quality"
  (let [^BufferedImage scaled (BufferedImage. target-size target-size BufferedImage/TYPE_INT_ARGB)
        ^Graphics2D g (.createGraphics scaled)]
    (.setRenderingHint g RenderingHints/KEY_INTERPOLATION RenderingHints/VALUE_INTERPOLATION_BICUBIC)
    (.setRenderingHint g RenderingHints/KEY_RENDERING RenderingHints/VALUE_RENDER_QUALITY)
    (.setRenderingHint g RenderingHints/KEY_ANTIALIASING RenderingHints/VALUE_ANTIALIAS_ON)
    (.drawImage g img 0 0 target-size target-size nil)
    (.dispose g)
    scaled))

(defn process-star-image [^File file]
  "Load, remove black background, and downscale a star image"
  (let [^BufferedImage original (ImageIO/read file)
        ^BufferedImage no-bg (remove-black-background original)
        ^BufferedImage scaled (downscale-image no-bg tile-size)]
    {:name (.getName file)
     :image scaled}))

(defn create-atlas [star-images]
  "Pack star images into a single atlas texture"
  (let [^BufferedImage atlas (BufferedImage. atlas-size atlas-size BufferedImage/TYPE_INT_ARGB)
        ^Graphics2D g (.createGraphics atlas)
        metadata (atom [])]

    ;; Draw each star into the atlas grid
    (doseq [[idx {:keys [name ^BufferedImage image]}] (map-indexed vector star-images)]
      (let [row (quot idx tiles-per-row)
            col (rem idx tiles-per-row)
            x (* col tile-size)
            y (* row tile-size)]

        ;; Draw the image at this grid position
        (.drawImage g image x y nil)

        ;; Record metadata
        (swap! metadata conj
               {:name name
                :atlas-x x
                :atlas-y y
                :tile-size tile-size})))

    (.dispose g)

    {:atlas atlas
     :metadata @metadata}))

(defn save-metadata [metadata output-path]
  "Save metadata as JSON"
  (let [json-str (str "[\n"
                      (clojure.string/join ",\n"
                        (map #(format "  {\"name\":\"%s\",\"x\":%d,\"y\":%d,\"size\":%d}"
                                      (:name %)
                                      (:atlas-x %)
                                      (:atlas-y %)
                                      (:tile-size %))
                             metadata))
                      "\n]")]
    (spit output-path json-str)))

(defn -main []
  (println "Star Atlas Generator")
  (println "====================")

  ;; Load all star images
  (println "\nLoading star images from assets/stars/...")
  (let [star-files (sort (.listFiles (File. "assets/stars")))
        png-files (filter #(.endsWith (.getName ^File %) ".png") star-files)]

    (println (format "Found %d star images" (count png-files)))

    ;; Process images
    (println "\nProcessing images (removing backgrounds and downscaling)...")
    (let [processed-stars (vec (map-indexed
                                 (fn [idx file]
                                   (when (zero? (mod idx 10))
                                     (println (format "  Processing %d/%d..." (inc idx) (count png-files))))
                                   (process-star-image file))
                                 png-files))]

      ;; Create atlas
      (println "\nCreating texture atlas...")
      (let [{:keys [atlas metadata]} (create-atlas processed-stars)]

        ;; Save atlas image
        (println "Saving atlas to assets/star-atlas.png...")
        (ImageIO/write atlas "PNG" (File. "assets/star-atlas.png"))

        ;; Save metadata
        (println "Saving metadata to assets/star-atlas.json...")
        (save-metadata metadata "assets/star-atlas.json")

        (println (format "\nDone! Created %dx%d atlas with %d stars"
                        atlas-size atlas-size (count metadata)))
        (println (format "Atlas can hold up to %d images" (* tiles-per-row tiles-per-row)))))))

(-main)
