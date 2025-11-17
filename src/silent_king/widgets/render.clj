(ns silent-king.widgets.render
  "Widget rendering system using multi-method dispatch"
  (:require [silent-king.state :as state]
            [silent-king.widgets.core :as wcore]
            [silent-king.widgets.draw-order :as draw-order]
            [silent-king.widgets.minimap :as wminimap])
  (:import [io.github.humbleui.skija Canvas Paint Font Typeface PaintMode]
           [io.github.humbleui.types Rect RRect]))

(set! *warn-on-reflection* true)

;; =============================================================================
;; Paint Caching
;; =============================================================================

(def ^:private paint-cache (atom {}))

(defn get-or-create-paint
  "Get or create a Paint object for a given color"
  [color]
  (if-let [cached (@paint-cache color)]
    cached
    (let [paint (doto (Paint.) (.setColor (unchecked-int color)))]
      (swap! paint-cache assoc color paint)
      paint)))

;; =============================================================================
;; Color Utilities
;; =============================================================================

(defn lighten
  "Lighten a color by a given factor (0-1)"
  [color factor]
  (let [a (bit-and (unsigned-bit-shift-right color 24) 0xFF)
        r (bit-and (unsigned-bit-shift-right color 16) 0xFF)
        g (bit-and (unsigned-bit-shift-right color 8) 0xFF)
        b (bit-and color 0xFF)
        lighten-fn (fn [component] (int (min 255 (+ component (* (- 255 component) factor)))))
        r' (lighten-fn r)
        g' (lighten-fn g)
        b' (lighten-fn b)]
    (unchecked-int (bit-or (bit-shift-left a 24)
                           (bit-shift-left r' 16)
                           (bit-shift-left g' 8)
                           b'))))

(defn darken
  "Darken a color by a given factor (0-1)"
  [color factor]
  (let [a (bit-and (unsigned-bit-shift-right color 24) 0xFF)
        r (bit-and (unsigned-bit-shift-right color 16) 0xFF)
        g (bit-and (unsigned-bit-shift-right color 8) 0xFF)
        b (bit-and color 0xFF)
        darken-fn (fn [component] (int (* component (- 1.0 factor))))
        r' (darken-fn r)
        g' (darken-fn g)
        b' (darken-fn b)]
    (unchecked-int (bit-or (bit-shift-left a 24)
                           (bit-shift-left r' 16)
                           (bit-shift-left g' 8)
                           b'))))

;; =============================================================================
;; Drawing Primitives
;; =============================================================================

(defn draw-rounded-rect
  "Draw a rounded rectangle with optional shadow"
  [^Canvas canvas bounds color border-radius shadow]
  (let [x (float (:x bounds))
        y (float (:y bounds))
        w (float (:width bounds))
        h (float (:height bounds))
        radius (float (or border-radius 0.0))]

    ;; Draw shadow if specified
    (when shadow
      (let [shadow-paint (doto (Paint.)
                          (.setColor (unchecked-int (:color shadow)))
                          (.setMode PaintMode/FILL))
            shadow-x (+ x (float (:offset-x shadow)))
            shadow-y (+ y (float (:offset-y shadow)))
            shadow-blur (float (:blur shadow))
            shadow-rrect (RRect/makeXYWH shadow-x shadow-y w h radius)]
        (.drawRRect canvas shadow-rrect shadow-paint)
        (.close shadow-paint)))

    ;; Draw main rectangle
    (let [paint (get-or-create-paint color)
          rrect (RRect/makeXYWH x y w h radius)]
      (.drawRRect canvas rrect paint))))

(defn draw-text
  "Draw text at position with given color and font"
  [^Canvas canvas text x y color font-size]
  (let [paint (get-or-create-paint color)
        font (Font. (Typeface/makeDefault) (float font-size))]
    (.drawString canvas (str text) (float x) (float y) font paint)
    (.close font)))

(defn draw-centered-text
  "Draw text centered in bounds"
  [^Canvas canvas text bounds color font-size]
  (let [paint (get-or-create-paint color)
        font (Font. (Typeface/makeDefault) (float font-size))
        text-line (.measureText ^Font font (str text))
        text-width (.getWidth text-line)
        x (+ (:x bounds) (/ (- (:width bounds) text-width) 2.0))
        ;; Approximate vertical centering (font metrics would be more accurate)
        y (+ (:y bounds) (/ (:height bounds) 2.0) (/ font-size 3.0))]
    (.drawString canvas (str text) (float x) (float y) font paint)
    (.close font)))

;; =============================================================================
;; Multi-method Widget Rendering
;; =============================================================================

(defmulti render-widget
  "Render a widget based on its type"
  (fn [canvas widget-entity game-state time]
    (:type (state/get-component widget-entity :widget))))

;; Panel rendering
(defmethod render-widget :panel
  [^Canvas canvas widget-entity game-state time]
  (let [bounds (state/get-component widget-entity :bounds)
        visual (state/get-component widget-entity :visual)]
    (when (and bounds (:x bounds) (:y bounds) (:width bounds) (:height bounds))
      (let [bg-color (or (:background-color visual) 0xCC222222)
            border-radius (or (:border-radius visual) 0.0)
            shadow (:shadow visual)]
        (draw-rounded-rect canvas bounds bg-color border-radius shadow)))))

;; Label rendering
(defmethod render-widget :label
  [^Canvas canvas widget-entity game-state time]
  (let [bounds (state/get-component widget-entity :bounds)
        visual (state/get-component widget-entity :visual)]
    (when (and bounds (:x bounds) (:y bounds) (:width bounds) (:height bounds))
      (let [text (or (:text visual) "")
            text-color (or (:text-color visual) 0xFFFFFFFF)
            font-size (or (:font-size visual) 14)
            text-align (or (:text-align visual) :left)
            x (case text-align
                :left (+ (:x bounds) 4)
                :center (+ (:x bounds) (/ (:width bounds) 2.0))
                :right (+ (:x bounds) (- (:width bounds) 4))
                (:x bounds))
            y (+ (:y bounds) (/ (:height bounds) 2.0) (/ font-size 3.0))]
        (if (= text-align :center)
          (draw-centered-text canvas text bounds text-color font-size)
          (draw-text canvas text x y text-color font-size))))))

;; Button rendering
(defmethod render-widget :button
  [^Canvas canvas widget-entity game-state time]
  (let [bounds (state/get-component widget-entity :bounds)
        visual (state/get-component widget-entity :visual)
        interaction (state/get-component widget-entity :interaction)]
    (when (and bounds (:x bounds) (:y bounds) (:width bounds) (:height bounds))
      (let [;; State-dependent colors
            base-color (or (:background-color visual) 0xFF3366CC)
            bg-color (cond
                      (:pressed interaction) (darken base-color 0.2)
                      (:hovered interaction) (lighten base-color 0.1)
                      :else base-color)
            text-color (or (:text-color visual) 0xFFFFFFFF)
            border-radius (or (:border-radius visual) 6.0)
            text (or (:text visual) "Button")
            font-size (or (:font-size visual) 14)]

        ;; Draw button background
        (draw-rounded-rect canvas bounds bg-color border-radius nil)

        ;; Draw button text (centered)
        (draw-centered-text canvas text bounds text-color font-size)))))

;; Slider rendering
(defmethod render-widget :slider
  [^Canvas canvas widget-entity game-state time]
  (let [bounds (state/get-component widget-entity :bounds)
        visual (state/get-component widget-entity :visual)
        value-data (state/get-component widget-entity :value)
        interaction (state/get-component widget-entity :interaction)

        track-color (or (:track-color visual) 0xFF444444)
        track-active-color (or (:track-active-color visual) 0xFF6699FF)
        thumb-color (or (:thumb-color visual) 0xFFFFFFFF)
        thumb-radius (or (:thumb-radius visual) 10.0)

        min-val (:min value-data)
        max-val (:max value-data)
        current-val (:current value-data)
        normalized (/ (- current-val min-val) (- max-val min-val))

        ;; Track dimensions (with defensive nil checks)
        track-height 4.0
        bounds-x (or (:x bounds) 0)
        bounds-y (or (:y bounds) 0)
        bounds-width (or (:width bounds) 200)
        bounds-height (or (:height bounds) 20)
        track-y (+ bounds-y (/ (- bounds-height track-height) 2.0))
        track-width bounds-width

        ;; Thumb position
        thumb-x (+ bounds-x (* normalized track-width))
        thumb-y (+ bounds-y (/ bounds-height 2.0))

        ;; Active track width
        active-width (* normalized track-width)]

    ;; Draw inactive track
    (let [paint (get-or-create-paint track-color)
          rrect (RRect/makeXYWH (float bounds-x)
                                (float track-y)
                                (float track-width)
                                (float track-height)
                                (float 2.0))]
      (.drawRRect canvas rrect paint))

    ;; Draw active track
    (let [paint (get-or-create-paint track-active-color)
          rrect (RRect/makeXYWH (float bounds-x)
                                (float track-y)
                                (float active-width)
                                (float track-height)
                                (float 2.0))]
      (.drawRRect canvas rrect paint))

    ;; Draw thumb
    (let [paint (get-or-create-paint thumb-color)
          thumb-size (if (:hovered interaction) (* thumb-radius 1.1) thumb-radius)]
      (.drawCircle canvas (float thumb-x) (float thumb-y) (float thumb-size) paint))))

;; VStack rendering (renders container, children rendered separately)
(defmethod render-widget :vstack
  [^Canvas canvas widget-entity game-state time]
  ;; VStack itself is typically transparent, but we can render background if needed
  (let [visual (state/get-component widget-entity :visual)]
    (when-let [bg-color (:background-color visual)]
      (let [bounds (state/get-component widget-entity :bounds)
            border-radius (or (:border-radius visual) 0.0)]
        (draw-rounded-rect canvas bounds bg-color border-radius nil)))))

(defmethod render-widget :minimap
  [^Canvas canvas widget-entity game-state _time]
  (let [bounds (state/get-component widget-entity :bounds)
        visual (state/get-component widget-entity :visual)]
    (when (and (state/minimap-visible? game-state)
               bounds (:width bounds) (:height bounds))
      (let [bg-color (:background-color visual)
            border-color (:border-color visual)
            border-width (:border-width visual)
            border-radius (:border-radius visual)
            viewport-color (or (:viewport-color visual) 0xFFFF0000)
            positions (wminimap/collect-star-positions game-state)
            world-bounds (wminimap/compute-world-bounds positions)
            transform (wminimap/compute-transform bounds world-bounds)
            density (wminimap/build-density-grid positions world-bounds)
            camera (state/get-camera game-state)
            viewport-size (wminimap/get-viewport-size game-state)
            viewport-world (wminimap/camera->viewport camera viewport-size)
            cells (:cells density)
            bucket-count (:bucket-count density)
            cell-width (:cell-width density)
            cell-height (:cell-height density)
            max-density (double (max 1 (if (seq cells) (apply max (vals cells)) 0)))]

        ;; Background panel
        (draw-rounded-rect canvas bounds bg-color border-radius nil)

        ;; Density-based coloring
        (doseq [[[ix iy] cell-count] cells]
          (let [intensity (/ cell-count max-density)
                color (wminimap/density-color intensity)
                paint (get-or-create-paint color)
                world-x0 (+ (:min-x world-bounds) (* ix cell-width))
                world-y0 (+ (:min-y world-bounds) (* iy cell-height))
                world-x1 (if (= (dec bucket-count) ix)
                           (+ (:min-x world-bounds) (:span-x world-bounds))
                           (+ world-x0 cell-width))
                world-y1 (if (= (dec bucket-count) iy)
                           (+ (:min-y world-bounds) (:span-y world-bounds))
                           (+ world-y0 cell-height))
                [map-x0 map-y0] (wminimap/world->minimap transform world-x0 world-y0)
                [map-x1 map-y1] (wminimap/world->minimap transform world-x1 world-y1)
                rect-x (float (min map-x0 map-x1))
                rect-y (float (min map-y0 map-y1))
                rect-w (float (max 1.0 (Math/abs (- map-x1 map-x0))))
                rect-h (float (max 1.0 (Math/abs (- map-y1 map-y0))))]
            (.drawRect canvas (Rect/makeXYWH rect-x rect-y rect-w rect-h) paint)))

        ;; Camera viewport rectangle
        (let [[vx0 vy0] (wminimap/world->minimap transform (:min-x viewport-world) (:min-y viewport-world))
              [vx1 vy1] (wminimap/world->minimap transform (:max-x viewport-world) (:max-y viewport-world))
              rect (Rect/makeXYWH (float (min vx0 vx1))
                                  (float (min vy0 vy1))
                                  (float (Math/abs (- vx1 vx0)))
                                  (float (Math/abs (- vy1 vy0))))
              viewport-paint (doto (get-or-create-paint viewport-color)
                               (.setMode PaintMode/STROKE)
                               (.setStrokeWidth 2.0))]
          (.drawRect canvas rect viewport-paint)
          (doto viewport-paint (.setMode PaintMode/FILL)))

        ;; Border
        (when (and border-color border-width)
          (let [border-paint (doto (get-or-create-paint border-color)
                              (.setMode PaintMode/STROKE)
                              (.setStrokeWidth (float border-width)))
                rrect (RRect/makeXYWH (float (:x bounds))
                                      (float (:y bounds))
                                      (float (:width bounds))
                                      (float (:height bounds))
                                      (float border-radius))]
            (.drawRRect canvas rrect border-paint)
            (doto border-paint (.setMode PaintMode/FILL))))))))

;; Default rendering (for unknown widget types)
(defmethod render-widget :default
  [^Canvas canvas widget-entity game-state time]
  nil)

;; =============================================================================
;; Widget Rendering System
;; =============================================================================

(defn render-all-widgets
  "Render all widgets sorted by z-index and hierarchy depth"
  [^Canvas canvas game-state time]
  (let [widgets (wcore/get-all-widgets game-state)
        sorted-widgets (draw-order/sort-for-render game-state widgets)]

    ;; Render each widget
    (doseq [[entity-id widget] sorted-widgets]
      (render-widget canvas widget game-state time))))
