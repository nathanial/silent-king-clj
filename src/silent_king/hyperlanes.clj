(ns silent-king.hyperlanes
  "Hyperlane generation and rendering using Delaunay triangulation"
  (:require [silent-king.camera :as camera]
            [silent-king.state :as state])
  (:import [org.locationtech.jts.triangulate DelaunayTriangulationBuilder]
           [org.locationtech.jts.geom Coordinate GeometryFactory LineString]
           [io.github.humbleui.skija Canvas Paint Shader PaintMode PaintStrokeCap]))

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

;; Line segment frustum culling using Cohen-Sutherland algorithm
(defn- compute-outcode [x y width height]
  "Compute outcode for Cohen-Sutherland line clipping"
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

(defn generate-delaunay-hyperlanes!
  "Generate hyperlane connections between stars using Delaunay triangulation.
  Creates hyperlane entities with :hyperlane and :visual components."
  [game-state]
  (println "Generating Delaunay hyperlanes...")
  (let [start-time (System/currentTimeMillis)
        ;; Get all star entities with positions
        star-entities (state/filter-entities-with game-state [:position])

        ;; Build map of entity-id -> position for quick lookup
        id-to-pos (into {} (map (fn [[id entity]]
                                  [id (state/get-component entity :position)])
                                star-entities))

        ;; Create JTS Coordinate array for triangulation
        coords (mapv (fn [[id entity]]
                       (let [pos (state/get-component entity :position)]
                         [id (Coordinate. (:x pos) (:y pos))]))
                     star-entities)

        ;; Build Delaunay triangulation
        builder (DelaunayTriangulationBuilder.)
        _ (.setSites builder ^java.util.Collection (mapv second coords))
        triangulation (.getEdges builder (GeometryFactory.))

        ;; Extract edges and create hyperlane entities
        edge-count (atom 0)
        num-edges (.getNumGeometries triangulation)

        ;; Helper function to find entity ID by coordinate
        find-id (fn [cx cy epsilon]
                  (some (fn [[id pos]]
                          (when (and (< (Math/abs (double (- (:x pos) cx))) epsilon)
                                    (< (Math/abs (double (- (:y pos) cy))) epsilon))
                            id))
                        id-to-pos))]

    ;; Iterate through triangulation edges
    (dotimes [i num-edges]
      (let [^org.locationtech.jts.geom.LineString edge (.getGeometryN triangulation i)
            ^org.locationtech.jts.geom.Coordinate from-coord (.getCoordinateN edge 0)
            ^org.locationtech.jts.geom.Coordinate to-coord (.getCoordinateN edge 1)
            epsilon 0.1
            from-id (find-id (.x from-coord) (.y from-coord) epsilon)
            to-id (find-id (.x to-coord) (.y to-coord) epsilon)]

        (when (and from-id to-id (not= from-id to-id))
            ;; Create hyperlane entity
            (let [;; Randomize visual properties slightly for variety
                  width-variation (+ 0.8 (* (rand) 0.4))  ; 0.8 - 1.2
                  color-variation (int (* (rand) 40))     ; Slight color variation
                  base-color (:color-start hyperlane-config)
                  varied-color (bit-xor base-color (bit-shift-left color-variation 8))

                  hyperlane (state/create-entity
                             :hyperlane {:from-id from-id
                                        :to-id to-id}
                             :visual {:base-width (* (:base-width hyperlane-config) width-variation)
                                     :color-start varied-color
                                     :color-end (:color-end hyperlane-config)
                                     :glow-color (:glow-color hyperlane-config)
                                     :animation-offset (* (rand) Math/PI 2)})]  ; Random phase
              (state/add-entity! game-state hyperlane)
              (swap! edge-count inc)))))

    (let [elapsed (- (System/currentTimeMillis) start-time)]
      (println "Generated" @edge-count "hyperlanes in" elapsed "ms"))))

(defn draw-all-hyperlanes
  "Draw all hyperlanes with LOD based on zoom level.
  Renders hyperlanes BEFORE stars so they appear as background connections."
  [^Canvas canvas width height zoom pan-x pan-y game-state current-time]
  (let [hyperlane-entities (state/filter-entities-with game-state [:hyperlane])
        all-entities @game-state
        settings (state/hyperlane-settings game-state)
        scheme (get color-schemes (:color-scheme settings) (:blue color-schemes))
        opacity (double (max 0.05 (min 1.0 (:opacity settings 0.9))))
        animation? (:animation? settings true)
        animation-speed (double (max 0.1 (min 3.0 (:animation-speed settings 1.0))))
        width-scale (double (max 0.4 (min 3.0 (:line-width settings 1.0))))
        start-color (apply-opacity (:start scheme (:color-start hyperlane-config)) opacity)
        end-color (apply-opacity (:end scheme (:color-end hyperlane-config)) opacity)
        glow-color (apply-opacity (:glow scheme (:glow-color hyperlane-config)) opacity)
        lod-level (cond
                    (< zoom 0.8) :far
                    (< zoom 2.0) :medium
                    :else :close)
        rendered (atom 0)
        pulse-frequency (* (:pulse-speed hyperlane-config) animation-speed)]
    (doseq [[_ hyperlane-entity] hyperlane-entities]
      (let [hyperlane-data (state/get-component hyperlane-entity :hyperlane)
            visual (state/get-component hyperlane-entity :visual)
            from-entity (get-in all-entities [:entities (:from-id hyperlane-data)])
            to-entity (get-in all-entities [:entities (:to-id hyperlane-data)])]
        (when (and from-entity to-entity)
          (let [from-pos (state/get-component from-entity :position)
                to-pos (state/get-component to-entity :position)
                from-x (camera/transform-position (:x from-pos) zoom pan-x)
                from-y (camera/transform-position (:y from-pos) zoom pan-y)
                to-x (camera/transform-position (:x to-pos) zoom pan-x)
                to-y (camera/transform-position (:y to-pos) zoom pan-y)
                dx (- to-x from-x)
                dy (- to-y from-y)
                screen-length (Math/sqrt (+ (* dx dx) (* dy dy)))]
            (when (and (line-segment-visible? from-x from-y to-x to-y width height)
                       (> screen-length (:min-visible-length hyperlane-config)))
              (swap! rendered inc)
              (let [line-width-base (camera/transform-size (* (:base-width visual) width-scale) zoom)]
                (case lod-level
                  :far
                  (let [paint (doto (Paint.)
                                (.setColor (unchecked-int start-color))
                                (.setStrokeWidth (float (max 1.0 (* width-scale 1.0))))
                                (.setMode PaintMode/STROKE))]
                    (.drawLine canvas (float from-x) (float from-y)
                               (float to-x) (float to-y) paint)
                    (.close paint))

                  :medium
                  (let [paint (doto (Paint.)
                                (.setColor (unchecked-int start-color))
                                (.setStrokeWidth (float line-width-base))
                                (.setStrokeCap PaintStrokeCap/ROUND)
                                (.setMode PaintMode/STROKE))]
                    (.drawLine canvas (float from-x) (float from-y)
                               (float to-x) (float to-y) paint)
                    (.close paint))

                  :close
                  (let [animation-phase (+ (* current-time pulse-frequency Math/PI 2)
                                           (:animation-offset visual))
                        pulse (if animation? (Math/sin animation-phase) 0.0)
                        width-multiplier (if animation?
                                           (+ 1.0 (* pulse (:pulse-amplitude hyperlane-config)))
                                           1.0)
                        animated-width (* line-width-base width-multiplier)
                        glow-width (* animated-width 3.0)
                        glow-paint (doto (Paint.)
                                     (.setColor (unchecked-int glow-color))
                                     (.setStrokeWidth (float glow-width))
                                     (.setStrokeCap PaintStrokeCap/ROUND)
                                     (.setMode PaintMode/STROKE))
                        colors (int-array [start-color end-color])
                        shader (Shader/makeLinearGradient
                                (float from-x) (float from-y)
                                (float to-x) (float to-y)
                                colors nil)
                        main-paint (doto (Paint.)
                                     (.setShader shader)
                                     (.setStrokeWidth (float animated-width))
                                     (.setStrokeCap PaintStrokeCap/ROUND)
                                     (.setMode PaintMode/STROKE))]
                    (.drawLine canvas (float from-x) (float from-y)
                               (float to-x) (float to-y) glow-paint)
                    (.close glow-paint)
                    (.drawLine canvas (float from-x) (float from-y)
                               (float to-x) (float to-y) main-paint)
                    (.close shader)
                    (.close main-paint)))))))))
    @rendered))
