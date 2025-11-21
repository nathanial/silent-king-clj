(ns silent-king.voronoi
  "Voronoi cell generation and rendering over the star field."
  (:require [silent-king.camera :as camera]
            [silent-king.render.commands :as commands]
            [silent-king.state :as state])
  (:import [org.locationtech.jts.geom Coordinate Envelope GeometryFactory Polygon]
           [org.locationtech.jts.triangulate VoronoiDiagramBuilder]))

(set! *warn-on-reflection* true)

(def ^:const ^double clip-padding-min 200.0)
(def ^:const ^double clip-padding-scale 0.18)
(def ^:const ^double epsilon 1.0e-6)
(def ^:const ^long relax-iterations-max 5)
(def ^:const ^double relax-convergence-epsilon 1.0e-3)
(def ^:const ^double border-epsilon 1.0e-3)

(def ^:private default-relax-config
  {:iterations 0
   :step-factor 1.0
   :max-displacement nil
   :clip-to-envelope? true})

(declare generate-relaxed-voronoi)

(def ^:private color-schemes
  ;; Palettes are vectors so we can graph-color neighboring cells differently.
  {:monochrome [{:stroke 0xFF7FA5FF :fill 0x33FFFFFF}
                {:stroke 0xFF5FD7FF :fill 0x33E9FBFF}
                {:stroke 0xFF7FE5B5 :fill 0x33E4FFE1}
                {:stroke 0xFFDBA7FF :fill 0x33F4E1FF}
                {:stroke 0xFFFFC36F :fill 0x33FFE7C0}
                {:stroke 0xFFFF8A7A :fill 0x33FFD7CF}]
   :by-density [{:stroke 0xFF3ED8A2 :fill 0x333ED8A2}
                {:stroke 0xFF21A6F3 :fill 0x3321A6F3}
                {:stroke 0xFF7A6BFF :fill 0x337A6BFF}
                {:stroke 0xFFF27CC2 :fill 0x33F27CC2}
                {:stroke 0xFFFFB347 :fill 0x33FFB347}
                {:stroke 0xFF4BD6C0 :fill 0x334BD6C0}]
   :by-degree [{:stroke 0xFFE86F4F :fill 0x33E86F4F}
               {:stroke 0xFFF2B14C :fill 0x33F2B14C}
               {:stroke 0xFF7CC86B :fill 0x337CC86B}
               {:stroke 0xFF5BC0EB :fill 0x335BC0EB}
               {:stroke 0xFF9A7FFB :fill 0x339A7FFB}
               {:stroke 0xFFD86FFF :fill 0x33D86FFF}]
   :by-region []}) ;; Empty vector acts as a flag for region-based coloring

(defn- clamp
  [value min-value max-value]
  (-> value double (max min-value) (min max-value)))

(defn- normalize-relax-config
  [config]
  (let [cfg (merge default-relax-config (or config {}))
        iterations (-> (:iterations cfg 0)
                       long
                       (max 0)
                       (min relax-iterations-max))
        step (clamp (:step-factor cfg 1.0) 0.0 1.0)
        max-d (when (number? (:max-displacement cfg))
                (Math/abs (double (:max-displacement cfg))))
        clip? (if (contains? cfg :clip-to-envelope?)
                (boolean (:clip-to-envelope? cfg))
                true)]
    {:iterations iterations
     :step-factor step
     :max-displacement max-d
     :clip-to-envelope? clip?
     :envelope (:envelope cfg)}))

(defn- valid-vertex?
  [{:keys [x y]}]
  (and (number? x) (number? y) (not (Double/isNaN (double x))) (not (Double/isNaN (double y)))))

(defn- apply-opacity
  [color opacity]
  (let [base-alpha (bit-and (unsigned-bit-shift-right color 24) 0xFF)
        base-frac (/ (double base-alpha) 255.0)
        out-alpha (int (Math/round (* 255.0 base-frac (clamp opacity 0.0 1.0))))
        rgb (bit-and color 0x00FFFFFF)]
    (unchecked-int (bit-or (bit-shift-left out-alpha 24) rgb))))

(defn- palette-for-settings
  [{:keys [color-scheme]}]
  (let [scheme (get color-schemes color-scheme (:monochrome color-schemes))]
    (if (sequential? scheme) (vec scheme) [scheme])))

(defn- coord->map
  [^Coordinate coord]
  {:x (.x coord)
   :y (.y coord)})

(defn- same-coord?
  [^Coordinate a ^Coordinate b]
  (and a b
       (< (Math/abs ^double (- (.x a) (.x b))) epsilon)
       (< (Math/abs ^double (- (.y a) (.y b))) epsilon)))

(defn- strip-duplicate-last
  [coords]
  (let [c (count coords)]
    (if (and (> c 1)
             (same-coord? (first coords) (last coords)))
      (subvec coords 0 (dec c))
      coords)))

(defn- sort-ccw
  [vertices {:keys [x y]}]
  (let [cx (double (or x 0.0))
        cy (double (or y 0.0))]
    (->> vertices
         (sort-by (fn [{:keys [x y]}]
                    (Math/atan2 (- (double (or y 0.0)) cy)
                                (- (double (or x 0.0)) cx))))
         vec)))

(defn- vertices->bbox
  [vertices]
  (let [valid (filter #(and (number? (:x %)) (number? (:y %))) vertices)]
    (if (seq valid)
      (reduce (fn [{:keys [min-x min-y max-x max-y]} {:keys [x y]}]
                {:min-x (min (double min-x) (double x))
                 :min-y (min (double min-y) (double y))
                 :max-x (max (double max-x) (double x))
                 :max-y (max (double max-y) (double y))})
              {:min-x Double/POSITIVE_INFINITY
               :min-y Double/POSITIVE_INFINITY
               :max-x Double/NEGATIVE_INFINITY
               :max-y Double/NEGATIVE_INFINITY}
              valid)
      {:min-x 0.0 :min-y 0.0 :max-x 0.0 :max-y 0.0})))

(defn- on-envelope?
  "Return true if any vertex (or centroid fallback) lies on the clip envelope edge within tolerance."
  [{:keys [vertices centroid]} ^Envelope env]
  (when (and env (not (.isNull env)))
    (let [min-x (.getMinX env)
          max-x (.getMaxX env)
          min-y (.getMinY env)
          max-y (.getMaxY env)
          touches? (fn [{:keys [x y]}]
                     (let [x (double (or x 0.0))
                           y (double (or y 0.0))]
                       (or (<= (Math/abs (- x min-x)) border-epsilon)
                           (<= (Math/abs (- x max-x)) border-epsilon)
                           (<= (Math/abs (- y min-y)) border-epsilon)
                           (<= (Math/abs (- y max-y)) border-epsilon))))]
      (boolean
       (or (some touches? vertices)
           (when centroid (touches? centroid)))))))

(defn- expand-envelope
  [^Envelope env]
  (when (and env (not (.isNull env)))
    (let [width (.getWidth env)
          height (.getHeight env)
          span (max width height)
          padding (+ clip-padding-min (* clip-padding-scale span))
          padded (Envelope. env)]
      (.expandBy padded padding padding)
      padded)))

(defn- star-envelope
  [stars]
  (reduce (fn [^Envelope env {:keys [x y]}]
            (.expandToInclude env (double (or x 0.0)) (double (or y 0.0)))
            env)
          (Envelope.)
          stars))

(defn- nearest-site
  [sites {:keys [x y]}]
  (when (and (seq sites) (number? x) (number? y))
    (reduce (fn [{:keys [dist] :as best} {:keys [coord] :as site}]
              (let [dx (- (.x ^Coordinate coord) (double x))
                    dy (- (.y ^Coordinate coord) (double y))
                    d (+ (* dx dx) (* dy dy))]
                (if (< d (double (or dist Double/POSITIVE_INFINITY)))
                  (assoc site :dist d)
                  best)))
            nil
            sites)))

(defn- polygon->cell
  [^Polygon polygon {:keys [coord star] :as site}]
  (let [raw-ring (strip-duplicate-last (vec (.getCoordinates (.getExteriorRing polygon))))
        center (or (some-> site :coord coord->map)
                   (let [centroid (.getCentroid polygon)]
                     {:x (.getX centroid) :y (.getY centroid)}))
        vertex-maps (->> raw-ring
                         (keep (fn [^Coordinate c]
                                 (when c
                                   (let [m (coord->map c)]
                                     (when (valid-vertex? m) m)))))
                         vec)
        vertices (sort-ccw vertex-maps center)
        bbox (vertices->bbox vertices)
        centroid-geom (.getCentroid polygon)
        centroid {:x (.getX centroid-geom)
                  :y (.getY centroid-geom)}]
    (cond-> {:star-id (:id site)
             :vertices vertices
             :bbox bbox
             :centroid centroid}
      star (assoc :star star))))

(defn generate-voronoi
  "Pure Voronoi generator. Accepts a sequence of star maps with :id/:x/:y and
  returns {:voronoi-cells {id cell} :elapsed-ms n :envelope env}."
  ([stars]
   (generate-voronoi stars nil))
  ([stars {:keys [envelope]}]
   (let [start (System/currentTimeMillis)
         sites (mapv (fn [{:keys [id x y] :as star}]
                       {:id id
                        :coord (Coordinate. (double x) (double y))
                        :star star})
                     stars)
         site-count (count sites)
         env (or envelope (-> (star-envelope stars)
                              (expand-envelope)))]
     (if (< site-count 2)
       {:voronoi-cells {}
        :elapsed-ms (- (System/currentTimeMillis) start)
        :envelope env}
       (let [builder (VoronoiDiagramBuilder.)
             _ (.setSites builder ^java.util.Collection (mapv :coord sites))
             _ (when env
                 (.setClipEnvelope builder env))
             diagram (.getDiagram builder (GeometryFactory.))
             num-polys (.getNumGeometries diagram)]
         (loop [i 0
                acc {}]
           (if (= i num-polys)
             {:voronoi-cells acc
              :elapsed-ms (- (System/currentTimeMillis) start)
              :envelope env}
             (let [^Polygon poly (.getGeometryN diagram i)
                   centroid (.getCentroid poly)
                   centroid-map {:x (.getX centroid)
                                 :y (.getY centroid)}
                   user-data (.getUserData poly)
                   site (cond
                          (instance? Coordinate user-data)
                          (nearest-site sites (coord->map ^Coordinate user-data))

                          :else
                          (nearest-site sites centroid-map))
                   cell (when site
                          (let [cell (polygon->cell poly site)]
                            (assoc cell :on-envelope? (on-envelope? cell env))))
                   acc* (if (and cell (:star-id cell))
                          (assoc acc (:star-id cell) cell)
                          acc)]
               (recur (inc i) acc*)))))))))

(defn generate-voronoi!
  "Generate Voronoi cells from the current world's stars and persist them.
   Updates star positions if relaxation is enabled, preserving original coordinates."
  [game-state]
  (let [current-stars (state/star-seq game-state)
        ;; 1. Ensure original coordinates are stored so we can reset/re-relax
        stars-with-orig (mapv (fn [s]
                                (if (and (:orig-x s) (:orig-y s))
                                  s
                                  (assoc s :orig-x (:x s) :orig-y (:y s))))
                              current-stars)
        ;; 2. Reset to original positions for input to relaxation
        input-stars (mapv (fn [s]
                            (assoc s :x (:orig-x s) :y (:orig-y s)))
                          stars-with-orig)

        relax-config (state/voronoi-relax-config game-state)
        {:keys [voronoi-cells elapsed-ms relax-meta stars-relaxed] :as result}
        (generate-relaxed-voronoi input-stars relax-config)

        ;; 3. Merge relaxed positions back
        relaxed-lookup (into {} (map (juxt :id identity) stars-relaxed))
        final-stars-map (reduce (fn [acc s]
                                  (let [id (:id s)
                                        relaxed (get relaxed-lookup id)
                                        new-s (if relaxed
                                                (assoc s :x (:x relaxed) :y (:y relaxed))
                                                s)]
                                    (assoc acc id new-s)))
                                {}
                                stars-with-orig)

        iterations-used (long (or (some-> relax-meta :iterations-used) 0))
        avg-move (double (or (some-> relax-meta :iteration-stats last :avg-displacement) 0.0))
        base-msg (format "Generated %d Voronoi cells in %d ms"
                         (count voronoi-cells)
                         (long (or elapsed-ms 0)))
        suffix (when (pos? iterations-used)
                 (format " (relaxed %d iter%s, avg move %.3f)"
                         iterations-used
                         (if (= 1 iterations-used) "" "s")
                         avg-move))]

    (state/set-stars! game-state final-stars-map)
    (state/set-voronoi-cells! game-state voronoi-cells)
    (swap! game-state assoc :voronoi-generated? true)
    (println (str base-msg (or suffix "")))
    result))

(defn world-viewport
  "Return {:min-x :min-y :max-x :max-y} for the visible screen in world space."
  [width height zoom pan-x pan-y]
  (let [scale (camera/zoom->position-scale zoom)
        min-x (/ (- 0.0 pan-x) scale)
        max-x (/ (- (double width) pan-x) scale)
        min-y (/ (- 0.0 pan-y) scale)
        max-y (/ (- (double height) pan-y) scale)
        [min-x max-x] (if (< max-x min-x) [max-x min-x] [min-x max-x])
        [min-y max-y] (if (< max-y min-y) [max-y min-y] [min-y max-y])]
    {:min-x min-x
     :min-y min-y
     :max-x max-x
     :max-y max-y}))

(defn cell-visible?
  "Return true if the cell bbox intersects the expanded viewport."
  [{:keys [min-x min-y max-x max-y]} viewport margin]
  (let [mx (double (or margin 0.0))
        vx-min (double (get viewport :min-x 0.0))
        vx-max (double (get viewport :max-x 0.0))
        vy-min (double (get viewport :min-y 0.0))
        vy-max (double (get viewport :max-y 0.0))]
    (and (< (- (double min-x) mx) vx-max)
         (> (+ (double max-x) mx) vx-min)
         (< (- (double min-y) mx) vy-max)
         (> (+ (double max-y) mx) vy-min))))

(defn- transform-vertices
  [vertices zoom pan-x pan-y]
  (->> vertices
       (filter valid-vertex?)
       (mapv (fn [{:keys [x y]}]
               {:x (camera/transform-position (double x) zoom pan-x)
                :y (camera/transform-position (double y) zoom pan-y)}))))

(defn- get-region-color
  [game-state star-id]
  (let [regions (state/regions game-state)]
    (or (some (fn [[_ {:keys [color star-ids sectors]}]]
                (if (contains? star-ids star-id)
                  ;; Check if star belongs to a sector
                  (or (some (fn [[_ {:keys [star-ids color]}]]
                              (when (contains? star-ids star-id)
                                color))
                            sectors)
                      color)
                  nil))
              regions)
        ;; Default color for unregioned space (dark grey)
        0xFF303030)))

(defn plan-voronoi-cells
  "Plan Voronoi overlay commands with LOD and culling. Returns {:commands [...] :rendered n}."
  [width height zoom pan-x pan-y game-state _current-time]
  (let [settings (state/voronoi-settings game-state)
        enabled? (state/voronoi-enabled? game-state)
        cells (state/voronoi-cells game-state)
        opacity (clamp (:opacity settings 0.35) 0.05 1.0)
        line-width (clamp (:line-width settings 1.4) 0.5 4.0)
        show-centroids? (boolean (:show-centroids? settings))
        hide-border? (boolean (:hide-border-cells? settings))
        palette (palette-for-settings settings)
        color-scheme (:color-scheme settings)

        neighbors (state/neighbors-by-star-id game-state)
        ;; Greedy graph coloring so adjacent cells use different palette entries.
        cell-colors (if (= :by-region color-scheme)
                      ;; Region coloring mode
                      (into {} (map (fn [star-id]
                                      [star-id {:fill (get-region-color game-state star-id)
                                                :stroke (get-region-color game-state star-id)}])
                                    (keys cells)))
                      ;; Standard palette coloring
                      (reduce (fn [acc star-id]
                                (let [neighbor-ids (map :neighbor-id (get neighbors star-id))
                                      neighbor-colors (->> neighbor-ids (keep acc) set)
                                      chosen (or (some (fn [c] (when-not (neighbor-colors c) c)) palette)
                                                 (first palette))]
                                  (assoc acc star-id chosen)))
                              {}
                              (sort (keys cells))))

        centroid-color (apply-opacity (:stroke (get cell-colors (first (keys cells)) (first palette))) 1.0)
        stroke-width-screen (fn []
                              (max 1.5 (camera/transform-size line-width zoom)))]
    (if (and enabled? (seq cells))
      (let [visible-cells (keep (fn [[star-id {:keys [vertices centroid on-envelope?]}]]
                                  (when (and (seq vertices)
                                             (not (and hide-border? on-envelope?)))
                                    (let [screen-verts (->> (transform-vertices vertices zoom pan-x pan-y)
                                                            (filter valid-vertex?)
                                                            vec)]
                                      {:screen-verts screen-verts
                                       :color (get cell-colors star-id (first palette))
                                       :centroid centroid})))
                                cells)
            {:keys [commands rendered]}
            (reduce (fn [{:keys [commands rendered]} {:keys [screen-verts centroid color]}]
                      (let [n (count screen-verts)
                            stroke-color (apply-opacity (:stroke color) (max 0.7 opacity))
                            fill-color (apply-opacity (:fill color) opacity)
                            lw (stroke-width-screen)]
                        (cond
                          (>= n 3)
                          (let [poly {:points screen-verts
                                      :style {:fill-color fill-color}}]
                            {:commands (into commands
                                             [(commands/polygon-fill (:points poly) (:style poly))
                                              (commands/polygon-stroke (:points poly)
                                                                       {:stroke-color stroke-color
                                                                        :stroke-width lw
                                                                        :stroke-cap :round})])
                             :rendered (inc rendered)})

                          (= n 2)
                          (let [[p0 p1] screen-verts
                                mx (/ (+ (:x p0) (:x p1)) 2.0)
                                my (/ (+ (:y p0) (:y p1)) 2.0)]
                            {:commands (conj commands
                                             (commands/circle {:x mx :y my} 4.5 {:fill-color fill-color})
                                             (commands/line p0 p1 {:stroke-color stroke-color
                                                                   :stroke-width lw
                                                                   :stroke-cap :round}))
                             :rendered (inc rendered)})

                          :else
                          (if centroid
                            (let [{cx :x cy :y} centroid
                                  sx (camera/transform-position (double cx) zoom pan-x)
                                  sy (camera/transform-position (double cy) zoom pan-y)]
                              {:commands (conj commands
                                               (commands/circle {:x sx :y sy} 6.0 {:fill-color fill-color})
                                               (commands/circle {:x sx :y sy} 7.0 {:stroke-color stroke-color
                                                                                   :stroke-width lw}))
                               :rendered (inc rendered)})
                            {:commands commands :rendered rendered}))))
                    {:commands [] :rendered 0}
                    visible-cells)
            commands* (if (and show-centroids? centroid-color)
                        (into commands
                              (for [{:keys [centroid]} visible-cells
                                    :when centroid]
                                (let [{cx :x cy :y} centroid
                                      sx (camera/transform-position (double cx) zoom pan-x)
                                      sy (camera/transform-position (double cy) zoom pan-y)]
                                  (commands/circle {:x sx :y sy} 2.5 {:fill-color centroid-color}))))
                        commands)]
        {:commands commands*
         :rendered rendered})
      {:commands []
       :rendered 0})))

(defn relax-sites-once
  "Run a single Lloyd-style relaxation step over the supplied stars.
   Returns {:stars-relaxed [...] :voronoi-cells {...} :envelope env
            :stats {:max-displacement d :avg-displacement d} :elapsed-ms n}."
  [stars config]
  (let [{:keys [step-factor max-displacement clip-to-envelope? envelope]} (normalize-relax-config config)
        start (System/currentTimeMillis)
        {:keys [voronoi-cells envelope] :as voronoi} (generate-voronoi stars {:envelope envelope})
        ^Envelope env (or envelope (:envelope voronoi))
        relaxed (mapv (fn [{:keys [id x y] :as star}]
                        (let [{:keys [centroid]} (get voronoi-cells id)
                              cx (:x centroid)
                              cy (:y centroid)]
                          (if (and centroid (number? cx) (number? cy))
                            (let [dx (- (double cx) (double x))
                                  dy (- (double cy) (double y))
                                  step-dx (* step-factor dx)
                                  step-dy (* step-factor dy)
                                  len (Math/sqrt (+ (* step-dx step-dx) (* step-dy step-dy)))
                                  [step-dx step-dy] (if (and max-displacement (pos? max-displacement) (> len max-displacement))
                                                      (let [scale (/ max-displacement len)]
                                                        [(* step-dx scale) (* step-dy scale)])
                                                      [step-dx step-dy])
                                  nx (+ (double x) step-dx)
                                  ny (+ (double y) step-dy)
                                  nx* (if (and clip-to-envelope? env)
                                        (clamp nx (.getMinX env) (.getMaxX env))
                                        nx)
                                  ny* (if (and clip-to-envelope? env)
                                        (clamp ny (.getMinY env) (.getMaxY env))
                                        ny)]
                              (assoc star :x nx* :y ny*))
                            star)))
                      stars)
        {:keys [total count mx]} (reduce (fn [{:keys [total count mx]} [before after]]
                                           (let [dx (- (double (:x after)) (double (:x before)))
                                                 dy (- (double (:y after)) (double (:y before)))
                                                 dist (Math/sqrt (+ (* dx dx) (* dy dy)))]
                                             {:total (+ total dist)
                                              :count (inc count)
                                              :mx (max mx dist)}))
                                         {:total 0.0 :count 0 :mx 0.0}
                                         (map vector stars relaxed))
        avg (if (pos? count) (/ total (double count)) 0.0)
        elapsed (- (System/currentTimeMillis) start)]
    {:stars-relaxed relaxed
     :voronoi-cells voronoi-cells
     :envelope env
     :stats {:max-displacement mx
             :avg-displacement avg}
     :elapsed-ms elapsed}))

(defn relax-sites
  "Run N iterations of Lloyd relaxation, returning relaxed stars and stats."
  [stars config]
  (let [{:keys [iterations] :as cfg} (normalize-relax-config config)]
    (if (zero? iterations)
      {:stars-relaxed stars
       :iterations-used 0
       :iteration-stats []
       :envelope (:envelope cfg)
       :elapsed-ms 0}
      (loop [i 0
             current-stars stars
             env (:envelope cfg)
             iteration-stats []
             elapsed 0]
        (if (>= i iterations)
          {:stars-relaxed current-stars
           :iterations-used i
           :iteration-stats iteration-stats
           :envelope env
           :elapsed-ms elapsed}
          (let [step-result (relax-sites-once current-stars (assoc cfg :envelope env))
                {:keys [stars-relaxed envelope elapsed-ms]} step-result
                step-stats (or (:stats step-result) {:max-displacement 0.0
                                                     :avg-displacement 0.0})
                iteration (inc i)
                stat (assoc step-stats :iteration iteration)
                max-step (double (or (:max-displacement step-stats) 0.0))
                converged? (<= max-step relax-convergence-epsilon)
                elapsed* (+ elapsed (long (or elapsed-ms 0)))]
            (if converged?
              {:stars-relaxed stars-relaxed
               :iterations-used iteration
               :iteration-stats (conj iteration-stats stat)
               :envelope envelope
               :elapsed-ms elapsed*}
              (recur iteration stars-relaxed envelope (conj iteration-stats stat) elapsed*))))))))

(defn generate-relaxed-voronoi
  "Generate Voronoi cells, optionally applying Lloyd relaxation to star sites."
  [stars relax-config]
  (let [{:keys [iterations] :as cfg} (normalize-relax-config relax-config)
        start (System/currentTimeMillis)]
    (if (zero? iterations)
      (let [base (generate-voronoi stars {:envelope (:envelope cfg)})]
        (assoc base :stars-relaxed stars
               :relax-meta {:iterations-requested iterations
                            :iterations-used 0
                            :iteration-stats []
                            :relax-elapsed-ms 0}))
      (let [{:keys [stars-relaxed iterations-used iteration-stats envelope elapsed-ms]} (relax-sites stars cfg)
            relaxed-by-id (into {} (map (juxt :id identity) stars-relaxed))
            relaxed-envelope envelope
            {:keys [voronoi-cells envelope]} (generate-voronoi stars-relaxed {:envelope relaxed-envelope})
            cells (into {}
                        (map (fn [[sid cell]]
                               (if-let [relaxed (get relaxed-by-id sid)]
                                 [sid (assoc cell
                                             :relaxed? true
                                             :relaxed-site (select-keys relaxed [:x :y]))]
                                 [sid cell])))
                        voronoi-cells)
            total (- (System/currentTimeMillis) start)]
        {:voronoi-cells cells
         :stars-relaxed stars-relaxed
         :elapsed-ms total
         :envelope envelope
         :relax-meta {:iterations-requested iterations
                      :iterations-used iterations-used
                      :iteration-stats iteration-stats
                      :relax-elapsed-ms elapsed-ms}}))))
