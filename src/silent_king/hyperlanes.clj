(ns silent-king.hyperlanes
  "Hyperlane generation and rendering using Delaunay triangulation"
  (:require [silent-king.camera :as camera]
            [silent-king.render.commands :as commands]
            [silent-king.state :as state])
  (:import [org.locationtech.jts.triangulate DelaunayTriangulationBuilder]
           [org.locationtech.jts.geom Coordinate GeometryFactory LineString]))

(set! *warn-on-reflection* true)

;; Hyperlane visual configuration
(def ^:private hyperlane-config
  {:base-width 2.0
   :color-start 0xFF6699FF
   :color-end 0xFF3366CC
   :glow-color 0x406699FF
   :pulse-speed 0.5
   :pulse-amplitude 0.3
   :min-visible-length 1.0})

(def ^:private color-schemes
  {:blue {:start 0xFF6699FF
          :end 0xFF3366CC
          :glow 0x406699FF}
   :red {:start 0xFFFF5D5D
         :end 0xFFC0392B
         :glow 0x40FF5D5D}
   :green {:start 0xFF66FF99
           :end 0xFF2ECC71
           :glow 0x4066FF99}
   :rainbow {:start 0xFFFFC857
             :end 0xFF00C2FF
             :glow 0x40FFC857}})

(defn- apply-opacity
  [color opacity]
  (let [alpha (int (Math/round (* 255.0 (max 0.0 (min 1.0 opacity)))))
        rgb (bit-and color 0x00FFFFFF)]
    (unchecked-int (bit-or (bit-shift-left alpha 24) rgb))))

(defn- build-neighbors
  "Compute adjacency map {:star-id [{:neighbor-id .. :hyperlane h} ...]}."
  [hyperlanes]
  (reduce (fn [acc {:keys [from-id to-id] :as hyperlane}]
            (-> acc
                (update from-id (fnil conj []) {:neighbor-id to-id
                                                :hyperlane hyperlane})
                (update to-id (fnil conj []) {:neighbor-id from-id
                                              :hyperlane hyperlane})))
          {}
          hyperlanes))

(defn generate-hyperlanes
  "Pure hyperlane generator using Delaunay triangulation.
  Accepts a sequence of star maps with :id/:x/:y.
  Returns {:hyperlanes [...] :neighbors-by-star-id {...} :next-hyperlane-id n}."
  [stars]
  (let [start-time (System/currentTimeMillis)
        coords (mapv (fn [{:keys [id x y]}]
                       [id (Coordinate. (double x) (double y))])
                     stars)
        builder (DelaunayTriangulationBuilder.)
        _ (.setSites builder ^java.util.Collection (mapv second coords))
        triangulation (.getEdges builder (GeometryFactory.))
        num-edges (.getNumGeometries triangulation)
        epsilon 0.1
        find-id (fn [cx cy]
                  (some (fn [[id ^Coordinate coord]]
                          (when (and (< (Math/abs (double (- (.x coord) cx))) epsilon)
                                     (< (Math/abs (double (- (.y coord) cy))) epsilon))
                            id))
                        coords))]
    (loop [i 0
           next-id 0
           acc []]
      (if (= i num-edges)
        (let [neighbors (build-neighbors acc)
              elapsed (- (System/currentTimeMillis) start-time)]
          {:hyperlanes acc
           :neighbors-by-star-id neighbors
           :next-hyperlane-id next-id
           :elapsed-ms elapsed})
        (let [^LineString edge (.getGeometryN triangulation i)
              ^Coordinate from-coord (.getCoordinateN edge 0)
              ^Coordinate to-coord (.getCoordinateN edge 1)
              from-id (find-id (.x from-coord) (.y from-coord))
              to-id (find-id (.x to-coord) (.y to-coord))]
          (if (and from-id to-id (not= from-id to-id))
            (let [width-variation (+ 0.8 (* (rand) 0.4))
                  color-variation (int (* (rand) 40))
                  base-color (:color-start hyperlane-config)
                  varied-color (bit-xor base-color (bit-shift-left color-variation 8))
                  hyperlane {:id (inc next-id)
                             :from-id from-id
                             :to-id to-id
                             :base-width (* (:base-width hyperlane-config) width-variation)
                             :color-start varied-color
                             :color-end (:color-end hyperlane-config)
                             :glow-color (:glow-color hyperlane-config)
                             :animation-offset (* (rand) Math/PI 2)}]
              (recur (inc i) (inc next-id) (conj acc hyperlane)))
            (recur (inc i) next-id acc)))))))

(defn generate-hyperlanes!
  "Generate hyperlanes from the current world's stars and persist them."
  [game-state]
  (let [stars (state/star-seq game-state)
        {:keys [hyperlanes neighbors-by-star-id next-hyperlane-id elapsed-ms] :as result}
        (generate-hyperlanes stars)]
    (state/set-hyperlanes! game-state hyperlanes)
    (state/set-neighbors! game-state neighbors-by-star-id)
    (swap! game-state assoc :next-hyperlane-id next-hyperlane-id)
    (println "Generated" (count hyperlanes) "hyperlanes in" (or elapsed-ms 0) "ms")
    result))

;; Line segment frustum culling using Cohen-Sutherland algorithm
(defn- compute-outcode
  "Compute outcode for Cohen-Sutherland line clipping"
  [x y width height]
  (let [code 0
        code (if (< y 0) (bit-or code 1) code)        ; TOP
        code (if (>= y height) (bit-or code 2) code)  ; BOTTOM
        code (if (< x 0) (bit-or code 4) code)        ; LEFT
        code (if (>= x width) (bit-or code 8) code)]  ; RIGHT
    code))

(defn line-segment-visible?
  "Check if line segment from (x0,y0) to (x1,y1) intersects viewport.
  Uses Cohen-Sutherland algorithm for accurate line-viewport intersection."
  [x0 y0 x1 y1 width height]
  (let [margin 200  ; Extra margin to prevent pop-in
        w (+ width margin margin)
        h (+ height margin margin)
        x0' (+ x0 margin)
        y0' (+ y0 margin)
        x1' (+ x1 margin)
        y1' (+ y1 margin)
        outcode0 (compute-outcode x0' y0' w h)
        outcode1 (compute-outcode x1' y1' w h)]
    ;; If both points share an outside zone, line is not visible
    ;; If both points are inside, line is visible
    ;; If mixed, line partially visible (we accept this case)
    (not (pos? (bit-and outcode0 outcode1)))))

(defn plan-all-hyperlanes
  "Plan hyperlane draw commands with LOD based on zoom level.
  Returns {:commands [...] :rendered n}."
  [width height zoom pan-x pan-y game-state current-time]
  (let [hyperlane-data (state/hyperlanes game-state)
        settings (state/hyperlane-settings game-state)
        scheme (get color-schemes (:color-scheme settings) (:blue color-schemes))
        opacity (double (max 0.05 (min 1.0 (:opacity settings 0.9))))
        animation? (:animation? settings true)
        animation-speed (double (max 0.1 (min 3.0 (:animation-speed settings 1.0))))
        width-scale (double (max 0.4 (min 3.0 (:line-width settings 1.0))))
        start-color (apply-opacity (:start scheme (:color-start hyperlane-config)) opacity)
        end-color (apply-opacity (:end scheme (:color-end hyperlane-config)) opacity)
        glow-color-applied (apply-opacity (:glow scheme (:glow-color hyperlane-config)) opacity)
        lod-level (cond
                    (< zoom 0.8) :far
                    (< zoom 2.0) :medium
                    :else :close)
        pulse-frequency (* (:pulse-speed hyperlane-config) animation-speed)
        {:keys [commands rendered]}
        (reduce
         (fn [{:keys [commands rendered]} {:keys [from-id to-id base-width color-start color-end glow-color animation-offset]}]
           (let [from-star (state/star-by-id game-state from-id)
                 to-star (state/star-by-id game-state to-id)]
             (if (and from-star to-star)
               (let [from-x (camera/transform-position (:x from-star) zoom pan-x)
                     from-y (camera/transform-position (:y from-star) zoom pan-y)
                     to-x (camera/transform-position (:x to-star) zoom pan-x)
                     to-y (camera/transform-position (:y to-star) zoom pan-y)
                     dx (- to-x from-x)
                     dy (- to-y from-y)
                     screen-length (Math/sqrt (+ (* dx dx) (* dy dy)))]
                 (if (and (line-segment-visible? from-x from-y to-x to-y width height)
                          (> screen-length (:min-visible-length hyperlane-config)))
                   (let [line-width-base (camera/transform-size (* base-width width-scale) zoom)
                         start (or color-start start-color)
                         end (or color-end end-color)
                         glow (or glow-color glow-color-applied)]
                     (case lod-level
                       :far {:commands (conj commands
                                             (commands/line {:x from-x :y from-y}
                                                            {:x to-x :y to-y}
                                                            {:stroke-color start
                                                             :stroke-width (max 1.0 (* width-scale 1.0))}))
                             :rendered (inc rendered)}
                       :medium {:commands (conj commands
                                                (commands/line {:x from-x :y from-y}
                                                               {:x to-x :y to-y}
                                                               {:stroke-color start
                                                                :stroke-width line-width-base
                                                                :stroke-cap :round}))
                                :rendered (inc rendered)}
                       :close (let [animation-phase (+ (* current-time pulse-frequency Math/PI 2)
                                                       (or animation-offset 0.0))
                                    pulse (if animation? (Math/sin animation-phase) 0.0)
                                    width-multiplier (if animation?
                                                       (+ 1.0 (* pulse (:pulse-amplitude hyperlane-config)))
                                                       1.0)
                                    animated-width (* line-width-base width-multiplier)]
                                {:commands (conj commands
                                                 (commands/line {:x from-x :y from-y}
                                                                {:x to-x :y to-y}
                                                                {:stroke-color start
                                                                 :stroke-width animated-width
                                                                 :stroke-cap :round
                                                                 :gradient {:start start
                                                                            :end end}
                                                                 :glow {:color glow
                                                                        :multiplier 3.0}
                                                                 :animation {:phase animation-phase
                                                                             :pulse-amplitude (:pulse-amplitude hyperlane-config)
                                                                             :enabled? animation?}}))
                                 :rendered (inc rendered)})
                       {:commands commands :rendered rendered}))
                   {:commands commands :rendered rendered}))
               {:commands commands :rendered rendered})))
         {:commands [] :rendered 0}
         hyperlane-data)]
    {:commands commands
     :rendered rendered}))
