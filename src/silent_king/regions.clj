(ns silent-king.regions
  "Region generation using graph partitioning on the hyperlane network (Void Carver)."
  (:require [silent-king.state :as state]
            [silent-king.camera :as camera]
            [silent-king.render.commands :as commands])
  (:import [java.util Random]))

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

(defn- variation-color
  "Return a color that is a slight variation of the base color."
  [base-color ^Random rng]
  (let [a (bit-and (unsigned-bit-shift-right base-color 24) 0xFF)
        r (bit-and (unsigned-bit-shift-right base-color 16) 0xFF)
        g (bit-and (unsigned-bit-shift-right base-color 8) 0xFF)
        b (bit-and base-color 0xFF)
        ;; Variation range
        v 60
        dr (- (.nextInt rng v) (/ v 2))
        dg (- (.nextInt rng v) (/ v 2))
        db (- (.nextInt rng v) (/ v 2))
        r* (Math/max 50 (Math/min 255 (+ r dr)))
        g* (Math/max 50 (Math/min 255 (+ g dg)))
        b* (Math/max 50 (Math/min 255 (+ b db)))]
    (bit-or (bit-shift-left a 24)
            (bit-shift-left (int r*) 16)
            (bit-shift-left (int g*) 8)
            (int b*))))

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

(def ^:private greek-letters
  ["Alpha" "Beta" "Gamma" "Delta" "Epsilon" "Zeta" "Eta" "Theta" "Iota" "Kappa" "Lambda" "Mu" "Nu" "Xi" "Omicron" "Pi" "Rho" "Sigma" "Tau" "Upsilon" "Phi" "Chi" "Psi" "Omega"])

(defn- sector-name
  [idx]
  (str "Sector " (get greek-letters (mod idx (count greek-letters)) (str (inc idx)))))

(defn- generate-sectors
  "Subdivide a region into sectors using multi-source flood fill (Imperial Seed)."
  [star-ids valid-edges stars rng region-color]
  (let [star-set (set star-ids)
        star-count (count star-ids)
        sector-count (max 1 (min 6 (long (Math/ceil (/ star-count 20.0)))))

        ;; Build adjacency for flood fill, restricted to valid edges within this region
        adjacency (reduce (fn [acc {:keys [from-id to-id]}]
                            (if (and (contains? star-set from-id)
                                     (contains? star-set to-id))
                              (-> acc
                                  (update from-id (fnil conj #{}) to-id)
                                  (update to-id (fnil conj #{}) from-id))
                              acc))
                          {}
                          valid-edges)

        ;; Pick random capitals
        capitals (take sector-count (shuffle star-ids))

        ;; Initialize BFS
        ;; Queue contains [star-id sector-idx]
        initial-queue (map-indexed (fn [i capital] [capital i]) capitals)
        initial-assignments (into {} (map-indexed (fn [i capital] [capital i]) capitals))]

    (loop [queue (into clojure.lang.PersistentQueue/EMPTY initial-queue)
           assignments initial-assignments]
      (if (empty? queue)
        ;; Group results
        (let [groups (group-by val assignments)]
          (reduce (fn [acc [sector-idx assigned-ids]]
                    (let [;; assignments is {star-id sector-idx}. group-by val gives {sector-idx [[star-id sector-idx] ...]}.
                          star-ids (map first assigned-ids)
                          id (keyword (str "sector-" sector-idx))
                          name (sector-name sector-idx)
                          center (centroid stars star-ids)]
                      (assoc acc id {:id id
                                     :name name
                                     :color (variation-color region-color rng)
                                     :star-ids (set star-ids)
                                     :center center
                                     :capital-id (nth capitals sector-idx)})))
                  {}
                  groups))

        (let [[current-id sector-idx] (peek queue)
              rem-queue (pop queue)
              neighbors (get adjacency current-id)
              unassigned-neighbors (remove #(contains? assignments %) neighbors)
              new-assignments (reduce (fn [acc n] (assoc acc n sector-idx)) {} unassigned-neighbors)
              new-queue-items (map (fn [n] [n sector-idx]) unassigned-neighbors)]
          (recur (into rem-queue new-queue-items)
                 (merge assignments new-assignments)))))))

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
                                        color (random-color rng)
                                        center (centroid stars component)
                                        sectors (generate-sectors component valid-edges stars rng color)]
                                    [id {:id id
                                         :name name
                                         :color color
                                         :star-ids component
                                         :center center
                                         :sectors sectors}])))
                              components)
        regions-map (into {} regions)
        elapsed (- (System/currentTimeMillis) start)]

    (state/set-regions! game-state regions-map)
    (println (format "Generated %d regions in %d ms (Threshold: %.2f)"
                     (count regions-map) elapsed threshold))
    regions-map))

(defn plan-regions
  "Plan region and sector label draw commands. Returns {:commands [...] :rendered n}."
  [zoom pan-x pan-y game-state]
  (let [regions (vals (state/regions game-state))
        {:keys [commands rendered]}
        (reduce (fn [{:keys [commands rendered]} {:keys [name center color sectors]}]
                  (if (and name center)
                    (let [{:keys [x y]} center
                          screen-x (camera/transform-position (double x) zoom pan-x)
                          screen-y (camera/transform-position (double y) zoom pan-y)
                          text-size (max 12.0 (min 48.0 (* 14.0 (Math/sqrt zoom))))
                          shadow-color 0x80000000
                          cmds (cond-> commands
                                 true (conj (commands/text {:text name
                                                            :position {:x (+ screen-x 2) :y (+ screen-y 2)}
                                                            :font {:size text-size}
                                                            :color shadow-color})
                                            (commands/text {:text name
                                                            :position {:x screen-x :y screen-y}
                                                            :font {:size text-size}
                                                            :color color}))
                                 (> zoom 0.8)
                                 (into (for [{s-name :name s-center :center} (vals sectors)
                                             :when (and s-name s-center)]
                                         (let [sx (:x s-center)
                                               sy (:y s-center)
                                               ssx (camera/transform-position (double sx) zoom pan-x)
                                               ssy (camera/transform-position (double sy) zoom pan-y)
                                               sector-size (max 10.0 (* 0.7 text-size))]
                                           [(commands/text {:text s-name
                                                            :position {:x (+ ssx 1) :y (+ ssy 1)}
                                                            :font {:size sector-size}
                                                            :color shadow-color})
                                            (commands/text {:text s-name
                                                            :position {:x ssx :y ssy}
                                                            :font {:size sector-size}
                                                            :color 0xDDCCCCCC})]))))]
                      {:commands cmds
                       :rendered (inc rendered)})
                    {:commands commands :rendered rendered}))
                {:commands [] :rendered 0}
                regions)]
    {:commands (into [] (mapcat #(if (sequential? %) % [%]) commands))
     :rendered rendered}))
