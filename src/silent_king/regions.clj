(ns silent-king.regions
  "Region generation using graph partitioning on the hyperlane network (Void Carver)."
  (:require [silent-king.state :as state]
            [silent-king.camera :as camera])
  (:import [java.util Random]
           [io.github.humbleui.skija Canvas Paint Font Typeface PaintMode]))

(set! *warn-on-reflection* true)

(def ^:private prefixes
  ["Typhon" "Victorian" "Solar" "Alpha" "Omega" "Zeta" "Crimson" "Azure" "Void" "Stygian"
   "Aether" "Nebula" "Kepler" "Orion" "Cygnus" "Lyra" "Vega" "Sirius" "Proxima" "Andromeda"
   "Tartarus" "Elysium" "Hades" "Olympus" "Titan" "Hyperion" "Kronos" "Gaia" "Terra" "Luna"])

(def ^:private suffixes
  ["Expanse" "Abyssal" "Reach" "Sector" "Zone" "Cluster" "Rift" "Waste" "Core" "Rim"
   "Marches" "Dominion" "Fields" "Sanctuary" "Wilds" "Depths" "Heights" "Belt" "Cloud" "Void"])

(defn- generate-name
  [^Random rng]
  (let [p (nth prefixes (.nextInt rng (count prefixes)))
        s (nth suffixes (.nextInt rng (count suffixes)))]
    (str p " " s)))

(defn- random-color
  [^Random rng]
  (let [r (+ 100 (.nextInt rng 155))
        g (+ 100 (.nextInt rng 155))
        b (+ 100 (.nextInt rng 155))]
    (bit-or 0xFF000000 (bit-shift-left r 16) (bit-shift-left g 8) b)))

(defn- edge-length
  [star-a star-b]
  (let [dx (- (double (:x star-a)) (double (:x star-b)))
        dy (- (double (:y star-a)) (double (:y star-b)))]
    (Math/sqrt (+ (* dx dx) (* dy dy)))))

(defn- compute-edge-stats
  [hyperlanes stars]
  (let [lengths (map (fn [{:keys [from-id to-id]}]
                       (let [a (get stars from-id)
                             b (get stars to-id)]
                         (if (and a b)
                           (edge-length a b)
                           0.0)))
                     hyperlanes)
        n (count lengths)]
    (if (pos? n)
      (let [total (reduce + lengths)
            mean (/ total (double n))
            variance (/ (reduce + (map #(let [d (- % mean)] (* d d)) lengths)) (double n))
            std-dev (Math/sqrt variance)]
        {:mean mean :std-dev std-dev})
      {:mean 0.0 :std-dev 0.0})))

(defn- find-components
  "Find connected components in the graph defined by edges."
  [star-ids edges]
  (let [adjacency (reduce (fn [acc {:keys [from-id to-id]}]
                            (-> acc
                                (update from-id (fnil conj #{}) to-id)
                                (update to-id (fnil conj #{}) from-id)))
                          {}
                          edges)
        visited (atom #{})
        components (atom [])]
    (doseq [start-id star-ids]
      (when-not (contains? @visited start-id)
        (loop [stack [start-id]
               component #{}]
          (if (empty? stack)
            (do
              (swap! components conj component)
              (swap! visited into component))
            (let [curr (peek stack)
                  rem-stack (pop stack)]
              (if (contains? component curr)
                (recur rem-stack component)
                (let [neighbors (get adjacency curr #{})
                      unvisited (remove #(contains? component %) neighbors)]
                  (recur (into rem-stack unvisited) (conj component curr)))))))))
    @components))

(defn- centroid
  [stars star-ids]
  (let [n (count star-ids)]
    (if (pos? n)
      (let [sum-x (reduce + (map #(:x (get stars %)) star-ids))
            sum-y (reduce + (map #(:y (get stars %)) star-ids))]
        {:x (/ sum-x (double n))
         :y (/ sum-y (double n))})
      {:x 0.0 :y 0.0})))

(defn generate-regions!
  [game-state]
  (println "Generating regions (Void Carver)...")
  (let [start (System/currentTimeMillis)
        stars (state/stars game-state)
        hyperlanes (state/hyperlanes game-state)
        stats (compute-edge-stats hyperlanes stars)
        threshold (+ (:mean stats) (* -0.2 (:std-dev stats))) ;; Tunable parameter: Lower = more fragments

        ;; Filter long edges (voids)
        valid-edges (filter (fn [{:keys [from-id to-id]}]
                              (let [a (get stars from-id)
                                    b (get stars to-id)]
                                (and a b (< (edge-length a b) threshold))))
                            hyperlanes)

        ;; Add debug logging
        _ (println (format "Edge stats: Mean=%.2f, StdDev=%.2f, Threshold=%.2f"
                           (:mean stats) (:std-dev stats) threshold))
        _ (println (format "Total edges: %d, Valid edges: %d"
                           (count hyperlanes) (count valid-edges)))

        components (find-components (keys stars) valid-edges)
        rng (Random. (long (or (:noise-seed @game-state) 42)))

        ;; Filter small noise components
        regions (keep-indexed (fn [idx component]
                                (when (> (count component) 3)
                                  (let [id (keyword (str "region-" idx))
                                        name (generate-name rng)
                                        center (centroid stars component)]
                                    [id {:id id
                                         :name name
                                         :color (random-color rng)
                                         :star-ids component
                                         :center center}])))
                              components)
        regions-map (into {} regions)
        elapsed (- (System/currentTimeMillis) start)]

    (state/set-regions! game-state regions-map)
    (println (format "Generated %d regions in %d ms (Threshold: %.2f)"
                     (count regions-map) elapsed threshold))
    regions-map))

(defonce ^:private typeface (Typeface/makeDefault))
(defonce ^:private font (Font. ^Typeface typeface (float 14.0)))

(defn draw-regions
  "Draw region names centered on their clusters."
  [^Canvas canvas zoom pan-x pan-y game-state]
  (let [regions (vals (state/regions game-state))
        text-paint (doto (Paint.)
                     (.setColor (unchecked-int 0xFFFFFFFF))
                     (.setMode PaintMode/FILL)
                     (.setAntiAlias true))]
    (try
      ;; Debug count
      ;; (println "Drawing" (count regions) "regions")
      (doseq [{:keys [name center color]} regions]
        (when (and name center)
          (let [{:keys [x y]} center
                screen-x (camera/transform-position (double x) zoom pan-x)
                screen-y (camera/transform-position (double y) zoom pan-y)
                ;; Scale text with zoom slightly, but clamp it
                text-size (max 12.0 (min 48.0 (* 14.0 (Math/sqrt zoom))))]
            (.setSize font (float text-size))
            (.setColor text-paint (unchecked-int color))
            ;; Draw shadow
            (let [shadow-paint (doto (Paint.)
                                 (.setColor (unchecked-int 0x80000000))
                                 (.setMode PaintMode/FILL)
                                 (.setAntiAlias true))]
              (.drawString canvas name (float (+ screen-x 2)) (float (+ screen-y 2)) font shadow-paint)
              (.close shadow-paint))
            (.drawString canvas name (float screen-x) (float screen-y) font text-paint))))
      (finally
        (.close text-paint)))))
