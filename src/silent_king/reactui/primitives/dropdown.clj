(ns silent-king.reactui.primitives.dropdown
  "Dropdown primitive: normalization, layout, rendering, overlays, and pointer handling."
  (:require [silent-king.reactui.core :as core]
            [silent-king.reactui.interaction :as interaction]
            [silent-king.reactui.layout :as layout]
            [silent-king.reactui.render :as render]
            [silent-king.render.commands :as commands]
            [silent-king.color :as color]))

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

(defn plan-dropdown
  [node]
  (let [{:keys [selected
                background-color
                text-color
                option-background
                option-selected-background
                option-selected-text-color
                border-color]} (:props node)
        {:keys [header options expanded? all-options]} (get-in node [:layout :dropdown])
        header-bg (or (color/ensure background-color) (color/hsv 229.1 19.6 22.0))
        option-bg (or (color/ensure option-background) (color/hsv 226.7 37.5 18.8))
        selected-bg (or (color/ensure option-selected-background) (color/hsv 221.5 30.2 33.7))
        txt-color (or (color/ensure text-color) (color/hsv 0 0 79.6))
        selected-txt-color (or (color/ensure option-selected-text-color) (color/hsv 229.1 42.3 10.2))
        caret (if expanded? "▼" "▲")
        caret-color txt-color
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
                            txt-color)
        header-bounds {:x (:x header)
                       :y (:y header)
                       :width (:width header)
                       :height (:height header)}]
    (when (and expanded? (seq options))
      (render/queue-overlay! {:type :dropdown
                              :node node
                              :selected selected
                              :options options
                              :padding padding
                              :option-bg option-bg
                              :selected-bg selected-bg
                              :text-color txt-color
                              :selected-text-color selected-txt-color}))
    (cond-> [(commands/rect header-bounds {:fill-color header-color})
             (commands/text {:text label
                             :position {:x text-x :y text-baseline}
                             :font {:size font-size}
                             :color header-text-color})
             (commands/text {:text caret
                             :position {:x caret-x :y caret-baseline}
                             :font {:size font-size}
                             :color caret-color})]
      header-border (into [(commands/rect header-bounds {:stroke-color header-border
                                                         :stroke-width 1.0})]))))

(defmethod render/plan-node :dropdown
  [_ node]
  (plan-dropdown node))

(defmethod render/plan-overlay :dropdown
  [_ {:keys [node selected options padding option-bg selected-bg text-color selected-text-color]}]
  (mapcat (fn [option]
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
                  text-y (+ (:y bounds) (/ (:height bounds) 2.0) 5.0)]
              [(commands/rect bounds {:fill-color bg})
               (commands/text {:text (:label option)
                               :position {:x (+ (:x bounds) padding)
                                          :y text-y}
                               :font {:size 15.0}
                               :color option-text-color})]))
          options))

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
