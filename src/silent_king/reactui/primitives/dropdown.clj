(ns silent-king.reactui.primitives.dropdown
  "Dropdown primitive: normalization, layout, rendering, overlays, and pointer handling."
  (:require [silent-king.reactui.core :as core]
            [silent-king.reactui.interaction :as interaction]
            [silent-king.reactui.layout :as layout]
            [silent-king.reactui.render :as render])
  (:import [io.github.humbleui.skija Canvas Font Paint PaintMode]
           [io.github.humbleui.types Rect]))

(set! *warn-on-reflection* true)

(defmethod core/normalize-tag :dropdown
  [_ props child-forms]
  ((core/leaf-normalizer :dropdown) props child-forms))

(defn dropdown-option
  [option]
  (cond
    (map? option)
    (let [value (or (:value option)
                    (:scheme option)
                    (:id option)
                    (:key option))]
      (when (nil? value)
        (throw (ex-info "Dropdown option missing :value" {:option option})))
      {:value value
       :label (or (:label option)
                  (if (keyword? value)
                    (name value)
                    (str value)))})
    :else
    {:value option
     :label (str option)}))

(defmethod layout/layout-node :dropdown
  [node context]
  (let [props (:props node)
        bounds* (layout/resolve-bounds node context)
        width (let [w (:width bounds*)]
                (if (pos? w) w 200.0))
        header-height (double (or (:header-height props) 36.0))
        option-height (double (or (:option-height props) 30.0))
        option-gap (double (or (:option-gap props) 4.0))
        expanded? (boolean (:expanded? props))
        options (mapv dropdown-option (:options props))
        header-bounds (assoc bounds*
                             :width width
                             :height header-height)
        options-layout (when (and expanded? (seq options))
                         (loop [remaining options
                                acc []
                                cursor-y (+ (:y header-bounds) header-height option-gap)]
                           (if (empty? remaining)
                             acc
                             (let [opt (first remaining)
                                   bounds {:x (:x header-bounds)
                                           :y cursor-y
                                           :width width
                                           :height option-height}
                                   next-y (+ cursor-y option-height option-gap)]
                               (recur (rest remaining)
                                      (conj acc (assoc opt :bounds bounds))
                                      next-y)))))]
    (assoc node
           :layout {:bounds header-bounds
                    :dropdown {:header header-bounds
                               :options options-layout
                               :all-options options
                               :expanded? expanded?}}
           :children [])))

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

(defn- active-dropdown-option?
  [node option]
  (let [active (render/active-interaction)]
    (and (= :dropdown-option (:type active))
         (= (:node active) node)
         (= (:value active) (:value option)))))

(defn- active-dropdown-header?
  [node]
  (let [active (render/active-interaction)]
    (and (= :dropdown (:type active))
         (= (:node active) node))))

(defn draw-dropdown
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
        header-hovered? (render/pointer-in-bounds? header)
        header-active? (active-dropdown-header? node)
        header-color (cond header-active? (render/adjust-color header-bg 0.9)
                           header-hovered? (render/adjust-color header-bg 1.1)
                           :else header-bg)
        header-border (cond header-active? (render/adjust-color header-bg 0.8)
                            header-hovered? (render/adjust-color header-bg 1.2)
                            :else border-color)
        header-text-color (if header-active?
                            (render/adjust-color txt-color 0.95)
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
      (with-open [^Font font (render/make-font font-size)]
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
      (render/queue-overlay! {:type :dropdown
                              :node node
                              :selected selected
                              :options options
                              :padding padding
                              :option-bg option-bg
                              :selected-bg selected-bg
                              :text-color txt-color
                              :selected-text-color selected-txt-color}))))

(defmethod render/draw-node :dropdown
  [canvas node]
  (draw-dropdown canvas node))

(defmethod render/draw-overlay :dropdown
  [^Canvas canvas {:keys [node selected options padding option-bg selected-bg text-color selected-text-color]}]
  (doseq [option options]
    (let [bounds (:bounds option)
          selected? (= (:value option) selected)
          hovered? (render/pointer-in-bounds? bounds)
          active? (active-dropdown-option? node option)
          base-bg (cond active? (render/adjust-color option-bg 0.85)
                        hovered? (render/adjust-color option-bg 1.1)
                        :else option-bg)
          bg (if selected?
               (cond active? (render/adjust-color selected-bg 0.9)
                     hovered? (render/adjust-color selected-bg 1.05)
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
        (with-open [^Font font (render/make-font 15.0)]
          (.drawString canvas (:label option)
                       (float (+ (:x bounds) padding))
                       (float (+ (:y bounds) (/ (:height bounds) 2.0) 5.0))
                       font
                       text-paint))))))

(defmethod core/pointer-down! :dropdown
  [node _game-state x y]
  (when-let [region (interaction/dropdown-region node x y)]
    (core/capture-node! node)
    (case (:type region)
      :option (core/set-active-interaction! node :dropdown-option {:bounds (:bounds region)
                                                                   :value (:value region)})
      :header (core/set-active-interaction! node :dropdown {:bounds (:bounds region)}))
    true))

(defmethod core/pointer-up! :dropdown
  [node game-state x y]
  (interaction/dropdown-click! node game-state x y))
