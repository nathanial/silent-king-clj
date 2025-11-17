(ns silent-king.hyperlanes
  "Hyperlane generation and rendering using Delaunay triangulation"
  (:require [silent-king.state :as state])
  (:import [org.locationtech.jts.triangulate DelaunayTriangulationBuilder]
           [org.locationtech.jts.geom Coordinate GeometryFactory LineString]
           [io.github.humbleui.skija Canvas Paint Shader PaintMode PaintStrokeCap]))

(set! *warn-on-reflection* true)

;; Hyperlane visual configuration
(def ^:private hyperlane-config
  {:base-width 2.0
   :color-start 0xFF6699FF  ; Light blue
   :color-end 0xFF3366CC    ; Dark blue
   :glow-color 0x406699FF   ; Transparent light blue for glow
   :pulse-speed 0.5         ; Animation cycles per second
   :pulse-amplitude 0.3     ; Width variation (0-1)
   :min-visible-length 1.0}) ; Minimum screen-space length to render (pixels)

;; Non-linear transform functions (must match core.clj)
(def ^:private position-exponent 2.5)
(def ^:private size-exponent 1.3)

(defn- zoom->position-scale [zoom]
  (Math/pow zoom position-exponent))

(defn- zoom->size-scale [zoom]
  (Math/pow zoom size-exponent))

(defn- transform-position [world-pos zoom pan]
  (+ (* world-pos (zoom->position-scale zoom)) pan))

(defn- transform-size [base-size zoom]
  (* base-size (zoom->size-scale zoom)))

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
  (let [;; Query all hyperlane entities
        hyperlane-entities (state/filter-entities-with game-state [:hyperlane])
        all-entities @game-state

        ;; Determine LOD level based on zoom
        lod-level (cond
                    (< zoom 0.8) :far     ; Simple thin lines
                    (< zoom 2.0) :medium  ; Thicker colored lines
                    :else :close)         ; Gradient lines with glow and animation

        ;; Count rendered hyperlanes for debugging
        rendered (atom 0)]

    (doseq [[_ hyperlane-entity] hyperlane-entities]
      (let [hyperlane-data (state/get-component hyperlane-entity :hyperlane)
            visual (state/get-component hyperlane-entity :visual)

            ;; Get star entities and positions
            from-entity (get-in all-entities [:entities (:from-id hyperlane-data)])
            to-entity (get-in all-entities [:entities (:to-id hyperlane-data)])

            ;; Skip if either star doesn't exist
            _ (when (or (nil? from-entity) (nil? to-entity))
                (throw (ex-info "Invalid hyperlane: missing star entity"
                                {:from-id (:from-id hyperlane-data)
                                 :to-id (:to-id hyperlane-data)})))]

        (when (and from-entity to-entity)
          (let [from-pos (state/get-component from-entity :position)
                to-pos (state/get-component to-entity :position)

                ;; Transform to screen coordinates with non-linear scaling
                from-x (transform-position (:x from-pos) zoom pan-x)
                from-y (transform-position (:y from-pos) zoom pan-y)
                to-x (transform-position (:x to-pos) zoom pan-x)
                to-y (transform-position (:y to-pos) zoom pan-y)

                ;; Calculate screen-space length for distance culling
                dx (- to-x from-x)
                dy (- to-y from-y)
                screen-length (Math/sqrt (+ (* dx dx) (* dy dy)))]

            ;; Frustum culling and distance culling
            (when (and (line-segment-visible? from-x from-y to-x to-y width height)
                       (> screen-length (:min-visible-length hyperlane-config)))

              (swap! rendered inc)

              ;; Render based on LOD level
              (case lod-level
                ;; Far zoom: Simple thin lines (1px, solid color)
                :far
                (let [paint (doto (Paint.)
                              (.setColor (unchecked-int (:color-start visual)))
                              (.setStrokeWidth (float 1.0))
                              (.setMode PaintMode/STROKE))]
                  (.drawLine canvas (float from-x) (float from-y)
                             (float to-x) (float to-y) paint)
                  (.close paint))

                ;; Medium zoom: Thicker colored lines with rounded caps
                :medium
                (let [line-width (transform-size (:base-width visual) zoom)
                      paint (doto (Paint.)
                              (.setColor (unchecked-int (:color-start visual)))
                              (.setStrokeWidth (float line-width))
                              (.setStrokeCap PaintStrokeCap/ROUND)
                              (.setMode PaintMode/STROKE))]
                  (.drawLine canvas (float from-x) (float from-y)
                             (float to-x) (float to-y) paint)
                  (.close paint))

                ;; Close zoom: Gradient lines with glow effect and animated pulsing
                :close
                (let [line-width (transform-size (:base-width visual) zoom)

                      ;; Animated pulsing width
                      animation-phase (+ (* current-time (:pulse-speed hyperlane-config) Math/PI 2)
                                        (:animation-offset visual))
                      pulse (Math/sin animation-phase)
                      width-multiplier (+ 1.0 (* pulse (:pulse-amplitude hyperlane-config)))
                      animated-width (* line-width width-multiplier)

                      ;; Glow effect (draw wider transparent line underneath)
                      glow-width (* animated-width 3.0)
                      glow-paint (doto (Paint.)
                                   (.setColor (unchecked-int (:glow-color visual)))
                                   (.setStrokeWidth (float glow-width))
                                   (.setStrokeCap PaintStrokeCap/ROUND)
                                   (.setMode PaintMode/STROKE))

                      ;; Gradient shader
                      colors (int-array [(:color-start visual) (:color-end visual)])
                      shader (Shader/makeLinearGradient
                              (float from-x) (float from-y)
                              (float to-x) (float to-y)
                              colors nil)

                      ;; Main line with gradient
                      main-paint (doto (Paint.)
                                   (.setShader shader)
                                   (.setStrokeWidth (float animated-width))
                                   (.setStrokeCap PaintStrokeCap/ROUND)
                                   (.setMode PaintMode/STROKE))]

                  ;; Draw glow first (underneath)
                  (.drawLine canvas (float from-x) (float from-y)
                             (float to-x) (float to-y) glow-paint)
                  (.close glow-paint)

                  ;; Draw main line on top
                  (.drawLine canvas (float from-x) (float from-y)
                             (float to-x) (float to-y) main-paint)
                  (.close shader)
                  (.close main-paint))))))))

    ;; Return count for debugging/UI
    @rendered))
