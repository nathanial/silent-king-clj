(ns silent-king.reactui.render
  "Skija renderer for the Reactified UI tree."
  (:require [silent-king.reactui.layout :as layout])
  (:import [io.github.humbleui.skija Canvas Font Typeface Paint PaintMode]
           [io.github.humbleui.types Rect]))

(set! *warn-on-reflection* true)

(defonce ^Typeface default-typeface
  (Typeface/makeDefault))

(def ^:dynamic *overlay-collector* nil)
(def ^:dynamic *render-context* nil)

(defn- queue-overlay!
  [overlay]
  (when *overlay-collector*
    (swap! *overlay-collector* conj overlay)))

(defmulti draw-node
  (fn [_ node]
    (:type node)))

(defmulti draw-overlay
  (fn [_ overlay]
    (:type overlay)))

(defn- make-font
  [font-size]
  (Font. default-typeface (float font-size)))

(defn- approx-text-width
  [text font-size]
  (* (count (or text ""))
     font-size
     0.55))

(defn- adjust-channel
  [value factor]
  (-> (* (double value) factor)
      (double)
      (Math/round)
      (max 0)
      (min 255)))

(defn- adjust-color
  [color factor]
  (let [a (bit-and (bit-shift-right color 24) 0xFF)
        r (bit-and (bit-shift-right color 16) 0xFF)
        g (bit-and (bit-shift-right color 8) 0xFF)
        b (bit-and color 0xFF)
        nr (adjust-channel r factor)
        ng (adjust-channel g factor)
        nb (adjust-channel b factor)]
    (unchecked-int (bit-or (bit-shift-left a 24)
                           (bit-shift-left nr 16)
                           (bit-shift-left ng 8)
                           nb))))

(defn- pointer-position
  []
  (:pointer *render-context*))

(defn- pointer-in-bounds?
  [{:keys [x y width height]}]
  (when-let [{px :x py :y} (pointer-position)]
    (let [px* (double px)
          py* (double py)]
      (and (>= px* (double x))
           (<= px* (+ (double x) width))
           (>= py* (double y))
           (<= py* (+ (double y) height))))))

(defn- pointer-over-node?
  [node]
  (pointer-in-bounds? (layout/bounds node)))

(defn- active-interaction
  []
  (:active-interaction *render-context*))

(defn- active-button?
  [node]
  (let [active (active-interaction)]
    (and (= :button (:type active))
         (= (:bounds active) (layout/bounds node)))))

(defn- active-slider?
  [node]
  (let [active (active-interaction)]
    (and (= :slider (:type active))
         (= (:bounds active) (layout/bounds node)))))

(defn- active-dropdown-option?
  [node option]
  (let [active (active-interaction)]
    (and (= :dropdown-option (:type active))
         (= (:node active) node)
         (= (:value active) (:value option)))))

(defn- active-dropdown-header?
  [node]
  (let [active (active-interaction)]
    (and (= :dropdown (:type active))
         (= (:node active) node))))

(defn- clamp01
  [value]
  (-> value
      (max 0.0)
      (min 1.0)))

(defn- selected-label
  [options value]
  (or (some (fn [opt]
              (when (= (:value opt) value)
                (:label opt)))
            options)
      (when value
        (if (keyword? value)
          (name value)
          (str value)))
      "Select"))

(defn- draw-label
  [^Canvas canvas node]
  (let [{:keys [text color font-size]} (:props node)
        {:keys [x y]} (layout/bounds node)
        size (double (or font-size 16.0))
        baseline (+ y size)]
    (with-open [^Paint paint (doto (Paint.)
                               (.setColor (unchecked-int (or color 0xFFFFFFFF))))]
      (with-open [^Font font (make-font size)]
        (.drawString canvas (or text "")
                     (float x)
                     (float baseline)
                     font
                     paint)))))

(defn- draw-stack
  [^Canvas canvas node]
  (let [{:keys [background-color]} (:props node)
        {:keys [x y width height]} (layout/bounds node)]
    (when background-color
      (with-open [^Paint paint (doto (Paint.)
                                  (.setColor (unchecked-int background-color)))]
        (.drawRect canvas
                   (Rect/makeXYWH (float x) (float y) (float width) (float height))
                   paint)))
    (doseq [child (:children node)]
      (draw-node canvas child))))

(defn- draw-button
  [^Canvas canvas node]
  (let [{:keys [label background-color text-color font-size]} (:props node)
        {:keys [x y width height]} (layout/bounds node)
        bg-color (or background-color 0xFF2D2F38)
        txt-color (or text-color 0xFFFFFFFF)
        hovered? (pointer-over-node? node)
        active? (active-button? node)
        shade (cond active? 0.9
                    hovered? 1.1
                    :else 1.0)
        final-bg (if (= shade 1.0) bg-color (adjust-color bg-color shade))
        final-text (if active?
                     (adjust-color txt-color 0.95)
                     txt-color)
        size (double (or font-size 16.0))
        text (or label "")
        text-width (approx-text-width text size)
        text-x (+ x (/ (- width text-width) 2.0))
        baseline (+ y (/ height 2.0) (/ size 2.5))
        border-color (cond active? (adjust-color bg-color 0.7)
                               hovered? (adjust-color bg-color 1.2)
                               :else nil)]
    (with-open [^Paint paint (doto (Paint.)
                               (.setColor (unchecked-int final-bg)))]
      (.drawRect canvas
                 (Rect/makeXYWH (float x) (float y) (float width) (float height))
                 paint))
    (when border-color
      (with-open [^Paint border (doto (Paint.)
                                   (.setColor (unchecked-int border-color))
                                   (.setStrokeWidth 1.0)
                                   (.setMode PaintMode/STROKE))]
        (.drawRect canvas
                   (Rect/makeXYWH (float x) (float y) (float width) (float height))
                   border)))
    (with-open [^Paint text-paint (doto (Paint.)
                                    (.setColor (unchecked-int final-text)))]
      (with-open [^Font font (make-font size)]
        (.drawString canvas text
                     (float text-x)
                     (float baseline)
                     font
                     text-paint)))))

(defn- draw-slider
  [^Canvas canvas node]
  (let [{:keys [background-color track-color handle-color]} (:props node)
        {:keys [x y width height]} (layout/bounds node)
        {:keys [track handle]} (get-in node [:layout :slider])
        bg-color (or background-color 0x00111111)
        t-color (or track-color 0xFF3C3F4A)
        h-color (or handle-color 0xFFF0F0F0)
        hovered? (pointer-over-node? node)
        active? (active-slider? node)
        track-color (cond active? (adjust-color t-color 0.9)
                          hovered? (adjust-color t-color 1.1)
                          :else t-color)
        handle-color (cond active? (adjust-color h-color 0.9)
                           hovered? (adjust-color h-color 1.05)
                           :else h-color)]
    (when background-color
      (with-open [^Paint bg (doto (Paint.)
                               (.setColor (unchecked-int bg-color)))]
        (.drawRect canvas
                   (Rect/makeXYWH (float x) (float y) (float width) (float height))
                   bg)))
    (when (pos? (:width track))
      (with-open [^Paint track-paint (doto (Paint.)
                                       (.setColor (unchecked-int track-color)))]
        (.drawRect canvas
                   (Rect/makeXYWH (float (:x track))
                                  (float (:y track))
                                  (float (:width track))
                                  (float (:height track)))
                   track-paint))
      (with-open [^Paint handle-paint (doto (Paint.)
                                         (.setColor (unchecked-int handle-color)))]
        (.drawCircle canvas
                     (float (:x handle))
                     (float (:y handle))
                     (float (:radius handle))
                     handle-paint)))))

(defn- draw-dropdown
  [^Canvas canvas node]
  (let [{:keys [selected
                background-color
                text-color
                option-background
                option-selected-background
                option-selected-text-color
                border-color]} (:props node)
        {:keys [header options expanded? all-options]} (get-in node [:layout :dropdown])
        header-bg (or background-color 0xFF2D2F38)
        option-bg (or option-background 0xFF1E2230)
        selected-bg (or option-selected-background 0xFF3C4456)
        txt-color (or text-color 0xFFCBCBCB)
        selected-txt-color (or option-selected-text-color 0xFF0F111A)
        caret (if expanded? "▲" "▼")
        caret-color txt-color
        header-rect (Rect/makeXYWH (float (:x header))
                                   (float (:y header))
                                   (float (:width header))
                                   (float (:height header)))
        padding 10.0
        font-size 16.0
        text-x (+ (:x header) padding)
        text-baseline (+ (:y header) (/ (:height header) 2.0) 5.0)
        caret-x (- (+ (:x header) (:width header)) (+ padding 6.0))
        caret-baseline text-baseline
        label (selected-label (or all-options []) selected)
        header-hovered? (pointer-in-bounds? header)
        header-active? (active-dropdown-header? node)
        header-color (cond header-active? (adjust-color header-bg 0.9)
                           header-hovered? (adjust-color header-bg 1.1)
                           :else header-bg)
        header-border (cond header-active? (adjust-color header-bg 0.8)
                             header-hovered? (adjust-color header-bg 1.2)
                             :else border-color)
        header-text-color (if header-active?
                            (adjust-color txt-color 0.95)
                            txt-color)]
    (with-open [^Paint header-paint (doto (Paint.)
                                      (.setColor (unchecked-int header-color)))]
      (.drawRect canvas header-rect header-paint))
    (when header-border
      (with-open [^Paint border-paint (doto (Paint.)
                                         (.setColor (unchecked-int header-border))
                                         (.setStrokeWidth 1.0)
                                         (.setMode PaintMode/STROKE))]
        (.drawRect canvas header-rect border-paint)))
    (with-open [^Paint text-paint (doto (Paint.)
                                     (.setColor (unchecked-int header-text-color)))]
      (with-open [^Font font (make-font font-size)]
        (.drawString canvas label
                     (float text-x)
                     (float text-baseline)
                     font
                     text-paint)
        (.drawString canvas caret
                     (float caret-x)
                     (float caret-baseline)
                     font
                     text-paint)))
    (when (and expanded? (seq options))
      (queue-overlay! {:type :dropdown
                       :node node
                       :selected selected
                       :options options
                       :padding padding
                       :option-bg option-bg
                       :selected-bg selected-bg
                       :text-color txt-color
                       :selected-text-color selected-txt-color}))))

(defn- draw-bar-chart
  [^Canvas canvas node]
  (let [{:keys [x y width height]} (layout/bounds node)
        {:keys [values min max bar-gap]} (get-in node [:layout :bar-chart])
        {:keys [background-color bar-color grid-color baseline-value]} (:props node)
        background-color (unchecked-int (or background-color 0x3310131C))
        bar-color (unchecked-int (or bar-color 0xFF9CDCFE))
        grid-color (unchecked-int (or grid-color 0x33FFFFFF))
        bars (vec values)
        count (count bars)
        safe-width (double (clojure.core/max width 0.0))
        gap (double (or bar-gap 2.0))
        total-gap (* gap (clojure.core/max 0 (dec count)))
        bar-width (if (pos? count)
                    (clojure.core/max 1.0 (/ (clojure.core/max 0.0 (- safe-width total-gap)) (double count)))
                    safe-width)
        min-value min
        max-value max
        span (clojure.core/max (- max-value min-value) 1e-6)
        baseline (double (or baseline-value min-value))
        baseline-ratio (clamp01 (/ (- baseline min-value) span))
        baseline-y (+ y (* (- 1.0 baseline-ratio) height))]
    (with-open [^Paint bg (doto (Paint.)
                             (.setColor background-color))]
      (let [rect (Rect/makeXYWH (float x) (float y) (float width) (float height))]
        (.drawRect canvas rect bg)))
    (when grid-color
      (with-open [^Paint grid (doto (Paint.)
                                (.setColor grid-color)
                                (.setStrokeWidth 1.0))]
        (.drawLine canvas (float x)
                    (float baseline-y)
                    (float (+ x width))
                    (float baseline-y)
                    grid)))
    (when (pos? count)
      (with-open [^Paint paint (doto (Paint.)
                                  (.setColor bar-color))]
        (doseq [[idx value] (map-indexed vector bars)]
          (let [ratio (clamp01 (/ (- (double value) min-value) span))
                bar-height (* ratio height)
                bar-x (+ x (* idx (+ bar-width gap)))
                bar-y (+ y (- height bar-height))
                rect (Rect/makeXYWH (float bar-x)
                                    (float bar-y)
                                    (float bar-width)
                                    (float bar-height))]
            (.drawRect canvas rect paint)))))
    )
  )

(defmethod draw-node :label
  [canvas node]
  (draw-label canvas node))

(defmethod draw-node :button
  [canvas node]
  (draw-button canvas node))

(defmethod draw-node :slider
  [canvas node]
  (draw-slider canvas node))

(defmethod draw-node :vstack
  [canvas node]
  (draw-stack canvas node))

(defmethod draw-node :hstack
  [canvas node]
  (draw-stack canvas node))

(defmethod draw-node :dropdown
  [canvas node]
  (draw-dropdown canvas node))

(defmethod draw-node :bar-chart
  [canvas node]
  (draw-bar-chart canvas node))

(defmethod draw-overlay :dropdown
  [^Canvas canvas {:keys [node selected options padding option-bg selected-bg text-color selected-text-color]}]
  (doseq [option options]
    (let [bounds (:bounds option)
          selected? (= (:value option) selected)
          hovered? (pointer-in-bounds? bounds)
          active? (active-dropdown-option? node option)
          base-bg (cond active? (adjust-color option-bg 0.85)
                        hovered? (adjust-color option-bg 1.1)
                        :else option-bg)
          bg (if selected?
               (cond active? (adjust-color selected-bg 0.9)
                     hovered? (adjust-color selected-bg 1.05)
                     :else selected-bg)
               base-bg)
          option-text-color (if (or selected? active?)
                              selected-text-color
                              text-color)
          rect (Rect/makeXYWH (float (:x bounds))
                               (float (:y bounds))
                               (float (:width bounds))
                               (float (:height bounds)))]
      (with-open [^Paint option-paint (doto (Paint.)
                                        (.setColor (unchecked-int bg)))]
        (.drawRect canvas rect option-paint))
      (with-open [^Paint text-paint (doto (Paint.)
                                       (.setColor (unchecked-int option-text-color)))]
        (with-open [^Font font (make-font 15.0)]
          (.drawString canvas (:label option)
                       (float (+ (:x bounds) padding))
                       (float (+ (:y bounds) (/ (:height bounds) 2.0) 5.0))
                       font
                       text-paint))))))

(defmethod draw-node :default
  [_ _]
  nil)

(defn draw-tree
  "Render a laid-out tree."
  [canvas node context]
  (when canvas
    (let [overlays (atom [])
          ctx (or context {})]
      (binding [*overlay-collector* overlays
                *render-context* ctx]
        (draw-node canvas node)
        (doseq [overlay @overlays]
          (draw-overlay canvas overlay))))))
