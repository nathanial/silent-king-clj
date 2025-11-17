(ns silent-king.widgets.render
  "Widget rendering system using multi-method dispatch"
  (:require [silent-king.state :as state]
            [silent-king.widgets.core :as wcore]
            [silent-king.widgets.draw-order :as draw-order]
            [silent-king.widgets.minimap :as wminimap]
            [silent-king.widgets.config :as wconfig])
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
;; Widget-Specific Helpers
;; =============================================================================

(defn- scroll-content-height
  [item-count item-height gap]
  (if (pos? item-count)
    (+ (* item-count item-height)
       (* (max 0 (dec item-count)) gap))
    0.0))

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

(defmethod render-widget :toggle
  [^Canvas canvas widget-entity _game-state _time]
  (let [bounds (state/get-component widget-entity :bounds)
        visual (state/get-component widget-entity :visual)
        value (state/get-component widget-entity :value)
        interaction (state/get-component widget-entity :interaction)]
    (when (and bounds (:width bounds) (:height bounds))
      (let [label (:label visual "Toggle")
            label-color (:label-color visual 0xFFEEEEEE)
            checked? (:checked? value)
            track-width 48.0
            track-height 22.0
            track-x (+ (:x bounds) (- (:width bounds) track-width))
            track-y (+ (:y bounds) (/ (- (:height bounds) track-height) 2.0))
            base-color (if checked?
                         (:track-on-color visual 0xFF3DD598)
                         (:track-off-color visual 0xFF555555))
            track-color (cond
                          (:pressed interaction) (darken base-color 0.1)
                          (:hovered interaction) (lighten base-color 0.1)
                          :else base-color)
            thumb-radius 10.0
            thumb-x (if checked?
                      (+ track-x track-width -12.0)
                      (+ track-x 12.0))
            thumb-y (+ track-y (/ track-height 2.0))]
        (draw-text canvas label (+ (:x bounds) 4) (+ (:y bounds) 22) label-color 14)
        (let [paint (get-or-create-paint track-color)
              rrect (RRect/makeXYWH (float track-x) (float track-y)
                                    (float track-width) (float track-height) (float (/ track-height 2.0)))]
          (.drawRRect canvas rrect paint))
        (let [paint (get-or-create-paint (:thumb-color visual 0xFFFFFFFF))]
          (.drawCircle canvas (float thumb-x) (float thumb-y) (float thumb-radius) paint))))))

(defn- dropdown-selected-label
  [value]
  (let [{:keys [options selected]} value]
    (if-let [match (some #(when (= (:value %) selected) %) options)]
      (:label match)
      "Select")))

(defmethod render-widget :dropdown
  [^Canvas canvas widget-entity _game-state _time]
  (let [bounds (state/get-component widget-entity :bounds)
        visual (state/get-component widget-entity :visual)
        value (state/get-component widget-entity :value)]
    (when (and bounds (:width bounds) (:height bounds))
      (let [bg-color (:background-color visual 0xFF1E1E1E)
            border-color (:border-color visual 0xFF444444)
            text-color (:text-color visual 0xFFEEEEEE)
            option-hover-color (:option-hover-color visual 0x33222222)
            options (:options value)
            expanded? (:expanded? value)
            option-height (double (:option-height value))
            base-height (double (:base-height value))]
        (draw-rounded-rect canvas
                           {:x (:x bounds)
                            :y (:y bounds)
                            :width (:width bounds)
                            :height base-height}
                           bg-color 8.0 nil)
        (let [border-paint (doto (Paint.)
                             (.setColor (unchecked-int border-color))
                             (.setMode PaintMode/STROKE)
                             (.setStrokeWidth 1.5))
              rect (RRect/makeXYWH (float (:x bounds))
                                   (float (:y bounds))
                                   (float (:width bounds))
                                   (float base-height)
                                   8.0)]
          (.drawRRect canvas rect border-paint)
          (.close border-paint))
        (draw-text canvas (dropdown-selected-label value)
                   (+ (:x bounds) 12)
                   (+ (:y bounds) 24)
                   text-color 14)
        (let [arrow-x (+ (:x bounds) (- (:width bounds) 24))
              arrow-y (+ (:y bounds) 18)
              arrow-paint (get-or-create-paint text-color)]
          (.drawLine canvas (float (- arrow-x 6)) (float arrow-y)
                     (float arrow-x) (float (+ arrow-y 6)) arrow-paint)
          (.drawLine canvas (float arrow-x) (float (+ arrow-y 6))
                     (float (+ arrow-x 6)) (float arrow-y) arrow-paint))
        (when expanded?
          (let [options-y (+ (:y bounds) base-height)
                width (:width bounds)
                drop-height (+ base-height (* option-height (count options)))]
            (draw-rounded-rect canvas
                               {:x (:x bounds)
                                :y options-y
                                :width width
                                :height (- drop-height base-height)}
                               0xFF151515 8.0 nil)
            (doseq [[idx {:keys [label]}] (map-indexed vector options)]
              (let [item-y (+ options-y (* idx option-height))
                    hover? (= (:hover-index value) idx)]
                (when hover?
                  (let [paint (get-or-create-paint option-hover-color)]
                    (.drawRect canvas
                               (Rect/makeXYWH (float (:x bounds))
                                              (float item-y)
                                              (float width)
                                              (float option-height))
                               paint)))
                (draw-text canvas label
                           (+ (:x bounds) 12)
                           (+ item-y 22)
                           text-color
                           14)))))))))

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

;; Star preview rendering
(defmethod render-widget :star-preview
  [^Canvas canvas widget-entity _game-state time]
  (let [bounds (state/get-component widget-entity :bounds)
        visual (state/get-component widget-entity :visual)
        value (state/get-component widget-entity :value)]
    (when (and bounds (:width bounds) (:height bounds))
      (let [bg-color (or (:background-color visual) 0x33111111)
            border-radius (or (:border-radius visual) 12.0)
            density (double (or (:density value) 0.0))
            normalized-density (-> density (max 0.0) (min 1.0))
            star-color (wminimap/density-color normalized-density)
            center-x (+ (:x bounds) (/ (:width bounds) 2.0))
            center-y (+ (:y bounds) (/ (:height bounds) 2.0))
            radius (* 0.28 (min (:width bounds) (:height bounds)))
            pulse (+ 0.95 (* 0.05 (Math/sin (* time 4.0))))]
        (draw-rounded-rect canvas bounds bg-color border-radius nil)
        (let [glow-paint (doto (Paint.)
                           (.setColor (unchecked-int (bit-or 0x33000000 star-color))))]
          (.drawCircle canvas (float center-x) (float center-y) (float (* radius 1.8 pulse)) glow-paint)
          (.close glow-paint))
        (let [ring-paint (doto (Paint.)
                           (.setColor (unchecked-int star-color))
                           (.setMode PaintMode/STROKE)
                           (.setStrokeWidth 2.5))]
          (.drawCircle canvas (float center-x) (float center-y) (float (* radius 1.25 pulse)) ring-paint)
          (.drawCircle canvas (float center-x) (float center-y) (float (* radius pulse)) ring-paint)
          (.close ring-paint))
        (when-let [star-id (:star-id value)]
          (draw-centered-text canvas (str "Star #" star-id)
                              {:x (:x bounds)
                               :y (+ (:y bounds) (- (:height bounds) 28))
                               :width (:width bounds)
                               :height 24}
                              0xFFEEEEEE
                              14))))))

;; Scroll view rendering
(defmethod render-widget :scroll-view
  [^Canvas canvas widget-entity _game-state _time]
  (let [bounds (state/get-component widget-entity :bounds)
        visual (state/get-component widget-entity :visual)
        value (state/get-component widget-entity :value)]
    (when (and bounds (:width bounds) (:height bounds))
      (let [bg-color (or (:background-color visual) 0x22181818)
            border-radius (or (:border-radius visual) 10.0)
            scrollbar-color (or (:scrollbar-color visual) 0x55FFFFFF)
            items (:items value)
            item-height (double (or (:item-height value) 34.0))
            gap (double (or (:gap value) 6.0))
            content-height (scroll-content-height (count items) item-height gap)
            max-offset (max 0.0 (- content-height (:height bounds)))
            requested-offset (double (or (:scroll-offset value) 0.0))
            scroll-offset (-> requested-offset (max 0.0) (min max-offset))]
        (draw-rounded-rect canvas bounds bg-color border-radius nil)
        (let [clip-rect (Rect/makeXYWH (float (:x bounds))
                                       (float (:y bounds))
                                       (float (:width bounds))
                                       (float (:height bounds)))]
          (.save canvas)
          (.clipRect canvas clip-rect)
          (if (seq items)
            (doseq [[idx {:keys [primary secondary]}] (map-indexed vector items)]
              (let [item-y (+ (:y bounds) (- scroll-offset) (* idx (+ item-height gap)))]
                (when (< item-y (+ (:y bounds) (:height bounds)))
                  (draw-text canvas primary (+ (:x bounds) 12) (+ item-y 18) 0xFFEEEEEE 14)
                  (when secondary
                    (draw-text canvas secondary (+ (:x bounds) 12) (+ item-y 34) 0xFFAAAAAA 12)))))
            (draw-centered-text canvas "No connections"
                                {:x (:x bounds)
                                 :y (:y bounds)
                                 :width (:width bounds)
                                 :height (:height bounds)}
                                0xFF888888
                                14))
          (.restore canvas))
        (when (> content-height (:height bounds))
          (let [viewport-ratio (/ (:height bounds) content-height)
                scrollbar-height (* (:height bounds) (max 0.1 viewport-ratio))
                scroll-progress (if (pos? max-offset) (/ scroll-offset max-offset) 0.0)
                track-length (- (:height bounds) scrollbar-height)
                scrollbar-y (+ (:y bounds) (* scroll-progress track-length))
                scrollbar-x (+ (:x bounds) (- (:width bounds) 6))]
            (let [paint (doto (Paint.)
                          (.setColor (unchecked-int scrollbar-color)))]
              (.drawRect canvas
                         (Rect/makeXYWH (float scrollbar-x)
                                        (float scrollbar-y)
                                        4.0
                                        (float scrollbar-height))
                         paint)
              (.close paint))))))))

;; Default rendering (for unknown widget types)
(defmethod render-widget :default
  [^Canvas canvas widget-entity game-state time]
  nil)

;; =============================================================================
;; Widget Rendering System
;; =============================================================================

(defn render-all-widgets
  "Render all widgets sorted by z-index and hierarchy depth.
  Applies global UI scale using canvas transformation."
  [^Canvas canvas game-state time]
  (let [widgets (wcore/get-all-widgets game-state)
        sorted-widgets (draw-order/sort-for-render game-state widgets)]

    ;; Save canvas state, apply UI scale, render widgets, then restore
    (.save canvas)
    (.scale canvas (float wconfig/ui-scale) (float wconfig/ui-scale))

    ;; Render each widget (widgets use their base coordinates, Skia scales them)
    (doseq [[entity-id widget] sorted-widgets]
      (render-widget canvas widget game-state time))

    (.restore canvas)))
