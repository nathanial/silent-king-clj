(ns silent-king.widgets.interaction
  "Widget interaction system - hit testing and event handling"
  (:require [silent-king.state :as state]
            [silent-king.widgets.core :as wcore]
            [silent-king.widgets.draw-order :as draw-order]
            [silent-king.widgets.animation :as wanim]
            [silent-king.widgets.minimap :as wminimap]
            [silent-king.widgets.config :as wconfig]))

(set! *warn-on-reflection* true)

;; =============================================================================
;; Hit Testing
;; =============================================================================

(defn screen->widget-coords
  "Transform screen coordinates to widget coordinate space (accounting for UI scale)"
  [x y]
  [(/ x wconfig/ui-scale) (/ y wconfig/ui-scale)])

(defn point-in-bounds?
  "Check if point (x, y) is inside bounds"
  [x y bounds]
  (and bounds
       (:x bounds) (:y bounds) (:width bounds) (:height bounds)
       (>= x (:x bounds))
       (>= y (:y bounds))
       (<= x (+ (:x bounds) (:width bounds)))
       (<= y (+ (:y bounds) (:height bounds)))))

(defn- scroll-content-height
  [item-count item-height gap]
  (if (pos? item-count)
    (+ (* item-count item-height)
       (* (max 0 (dec item-count)) gap))
    0.0))

(defn- dropdown-total-height
  [{:keys [base-height option-height options]} expanded?]
  (if expanded?
    (+ base-height (* option-height (count options)))
    base-height))

(defn- set-dropdown-expanded!
  [game-state entity-id expanded?]
  (state/update-entity! game-state entity-id
                        (fn [entity]
                          (let [value (state/get-component entity :value)
                                base-height (double (:base-height value))
                                option-height (double (:option-height value))
                                options (:options value)
                                new-height (dropdown-total-height {:base-height base-height
                                                                   :option-height option-height
                                                                   :options options}
                                                                  expanded?)]
                            (-> entity
                                (assoc-in [:components :value :expanded?] expanded?)
                                (assoc-in [:components :value :hover-index] nil)
                                (assoc-in [:components :bounds :height] new-height)))))
  (wcore/request-layout! game-state entity-id)
  (wcore/request-parent-layout! game-state entity-id))

(defn- collapse-all-dropdowns!
  [game-state except-entity-id]
  (doseq [[entity-id widget] (wcore/get-all-widgets game-state)]
    (let [widget-data (state/get-component widget :widget)]
      (when (and (= (:type widget-data) :dropdown)
                 (not= entity-id except-entity-id))
        (let [value (state/get-component widget :value)]
          (when (:expanded? value)
            (set-dropdown-expanded! game-state entity-id false)))))))

(defn- widget-at-point
  "Return the first widget matching the screen coordinate from a cached list.
  Screen coordinates are automatically transformed to widget space."
  [widgets x y]
  (let [sorted-widgets (reverse widgets)
        [widget-x widget-y] (screen->widget-coords x y)]
    (some (fn [[entity-id widget]]
            (let [bounds (state/get-component widget :bounds)
                  interaction (state/get-component widget :interaction)]
              (when (and (:enabled interaction)
                         (point-in-bounds? widget-x widget-y bounds))
                [entity-id widget])))
          sorted-widgets)))

(defn find-widget-at-point
  "Find topmost widget at screen coordinates (x, y).
  Returns [entity-id widget-entity] or nil if no widget found."
  [game-state x y]
  (let [widgets (draw-order/sort-for-render game-state (wcore/get-all-widgets game-state))]
    (widget-at-point widgets x y)))

;; =============================================================================
;; Mouse Event Handlers
;; =============================================================================

(defn handle-mouse-move
  "Handle mouse move event. Returns true if a widget handled the event."
  [game-state x y]
  (let [widgets (draw-order/sort-for-render game-state (wcore/get-all-widgets game-state))
        hovered-widget (widget-at-point widgets x y)
        ;; Transform screen coordinates to widget space for slider dragging
        [widget-x widget-y] (screen->widget-coords x y)]

    ;; Update hover state for all widgets
    (doseq [[entity-id widget] widgets]
      (let [interaction (state/get-component widget :interaction)
            is-hovered (and hovered-widget
                            (= entity-id (first hovered-widget)))]
        (when (not= (:hovered interaction) is-hovered)
          (state/update-entity! game-state entity-id
                               #(assoc-in % [:components :interaction :hovered] is-hovered)))))

    ;; Handle slider dragging
    (doseq [[entity-id widget] widgets]
      (let [widget-data (state/get-component widget :widget)
            interaction (state/get-component widget :interaction)]
        (when (and (= (:type widget-data) :slider)
                  (:dragging interaction))
          (let [bounds (state/get-component widget :bounds)
                value-data (state/get-component widget :value)
                min-val (:min value-data)
                max-val (:max value-data)
                step (:step value-data)
                ;; Calculate new value based on mouse position (using widget-space coordinates)
                normalized (/ (- widget-x (:x bounds)) (:width bounds))
                clamped (max 0.0 (min 1.0 normalized))
                raw-value (+ min-val (* clamped (- max-val min-val)))
                ;; Round to step
                stepped-value (if step
                               (* step (Math/round (double (/ raw-value step))))
                               raw-value)
                new-value (max min-val (min max-val stepped-value))]

            ;; Update slider value
            (state/update-entity! game-state entity-id
                                 #(assoc-in % [:components :value :current] new-value))

            ;; Call on-change callback
            (when-let [on-change (:on-change interaction)]
              (on-change new-value))))))

    ;; Update dropdown hover state if expanded
    (doseq [[entity-id widget] widgets]
      (let [widget-data (state/get-component widget :widget)]
        (when (= (:type widget-data) :dropdown)
          (let [value (state/get-component widget :value)
                bounds (state/get-component widget :bounds)
                expanded? (:expanded? value)
                base-height (double (:base-height value))
                option-height (double (:option-height value))
                relative-y (- widget-y (:y bounds))]
            (if (and expanded?
                     (>= relative-y base-height)
                     (< relative-y (+ base-height (* option-height (count (:options value))))))
              (let [hover-idx (int (/ (- relative-y base-height) option-height))
                    clamped (-> hover-idx (max 0) (min (dec (count (:options value)))))]
                (state/update-entity! game-state entity-id
                                      #(assoc-in % [:components :value :hover-index] clamped)))
              (when (:hover-index value)
                (state/update-entity! game-state entity-id
                                      #(assoc-in % [:components :value :hover-index] nil))))))))

    ;; Return true if a widget was hovered
    (boolean hovered-widget)))

(defn- any-active-interactions?
  "Return true if any widget currently has pressed or dragging state."
  [widgets]
  (some (fn [[_ widget]]
          (let [interaction (state/get-component widget :interaction)]
            (or (:pressed interaction) (:dragging interaction))))
        widgets))

(defn- reset-pressed-and-dragging!
  "Clear pressed/dragging state for all widgets."
  [game-state widgets]
  (doseq [[entity-id widget] widgets]
    (let [interaction (state/get-component widget :interaction)]
      (when (or (:pressed interaction) (:dragging interaction))
        (state/update-entity! game-state entity-id
                              #(-> %
                                   (assoc-in [:components :interaction :pressed] false)
                                   (assoc-in [:components :interaction :dragging] false)))))))

(defn handle-mouse-click
  "Handle mouse button press/release. Returns true if a widget handled the event."
  [game-state x y pressed?]
  (let [widgets (draw-order/sort-for-render game-state (wcore/get-all-widgets game-state))
        target-widget (widget-at-point widgets x y)
        ;; Transform screen coordinates to widget space
        [widget-x widget-y] (screen->widget-coords x y)
        target-type (when-let [[_ target-entity] target-widget]
                      (get-in (state/get-component target-entity :widget) [:type]))
        handled? (if-let [[entity-id widget] target-widget]
                   (let [widget-data (state/get-component widget :widget)
                         interaction (state/get-component widget :interaction)
                         widget-type (:type widget-data)]

                     (cond
                       ;; Handle button clicks
                       (and (= widget-type :button) (not pressed?))
                       (do
                         ;; Update pressed state
                         (state/update-entity! game-state entity-id
                                              #(assoc-in % [:components :interaction :pressed] false))
                         ;; Call on-click callback if mouse was released on button
                         (when-let [on-click (:on-click interaction)]
                           (on-click))
                         true)

                       ;; Handle button press
                       (and (= widget-type :button) pressed?)
                       (do
                         (state/update-entity! game-state entity-id
                                              #(assoc-in % [:components :interaction :pressed] true))
                         true)

                       ;; Toggle press
                       (and (= widget-type :toggle) pressed?)
                       (do
                         (state/update-entity! game-state entity-id
                                               #(assoc-in % [:components :interaction :pressed] true))
                         true)

                       ;; Toggle release
                       (and (= widget-type :toggle) (not pressed?))
                       (let [current (get-in widget [:components :value :checked?])
                             new-value (not (boolean current))]
                         (state/update-entity! game-state entity-id
                                               #(-> %
                                                    (assoc-in [:components :value :checked?] new-value)
                                                    (assoc-in [:components :interaction :pressed] false)))
                         (when-let [on-toggle (:on-toggle interaction)]
                           (on-toggle new-value))
                         true)

                       ;; Handle slider drag start
                       (and (= widget-type :slider) pressed?)
                       (do
                         (state/update-entity! game-state entity-id
                                              #(assoc-in % [:components :interaction :dragging] true))
                         ;; Immediately update slider value to mouse position
                         (handle-mouse-move game-state x y)
                         true)

                       ;; Handle slider drag end
                       (and (= widget-type :slider) (not pressed?))
                       (do
                         (state/update-entity! game-state entity-id
                                              #(assoc-in % [:components :interaction :dragging] false))
                         true)

                       ;; Dropdown press
                       (and (= widget-type :dropdown) pressed?)
                       (do
                         (state/update-entity! game-state entity-id
                                               #(assoc-in % [:components :interaction :pressed] true))
                         true)

                       ;; Dropdown release
                       (and (= widget-type :dropdown) (not pressed?))
                       (let [value (state/get-component widget :value)
                             bounds (state/get-component widget :bounds)
                             base-height (double (:base-height value))
                             option-height (double (:option-height value))
                             options (:options value)
                             expanded? (:expanded? value)
                             relative-y (- widget-y (:y bounds))
                             total-height (+ base-height (* option-height (count options)))]
                         (cond
                           ;; Selecting option
                           (and expanded?
                                (>= relative-y base-height)
                                (< relative-y total-height))
                           (let [option-index (-> (int (/ (- relative-y base-height) option-height))
                                                  (max 0)
                                                  (min (dec (count options))))
                                 option (nth options option-index nil)]
                             (when option
                               (state/update-entity! game-state entity-id
                                                     #(-> %
                                                          (assoc-in [:components :value :selected] (:value option))
                                                          (assoc-in [:components :interaction :pressed] false))))
                             (set-dropdown-expanded! game-state entity-id false)
                             (when-let [on-select (:on-select interaction)]
                               (when option
                                 (on-select (:value option))))
                             true)

                           ;; Toggle expanded/collapsed
                           :else
                           (do
                             (let [new-state (not expanded?)]
                               (when new-state
                                 (collapse-all-dropdowns! game-state entity-id))
                               (set-dropdown-expanded! game-state entity-id new-state)
                               (state/update-entity! game-state entity-id
                                                     #(assoc-in % [:components :interaction :pressed] false)))
                             true)))

                       ;; Handle minimap click-to-navigate (uses widget-space coordinates)
                       (and (= widget-type :minimap) (not pressed?) (state/minimap-visible? game-state))
                       (let [bounds (state/get-component widget :bounds)
                             positions (wminimap/collect-star-positions game-state)
                             world-bounds (wminimap/compute-world-bounds positions)
                             transform (wminimap/compute-transform bounds world-bounds)
                             world-pos (wminimap/minimap->world transform widget-x widget-y)
                             camera (state/get-camera game-state)
                             viewport-size (wminimap/get-viewport-size game-state)
                             {:keys [pan-x pan-y]} (wminimap/target-pan world-pos (:zoom camera) viewport-size)]
                         (wanim/start-camera-pan! game-state pan-x pan-y 0.5)
                         true)

                       :else false))
                   false)]
    (boolean
     (if pressed?
       handled?
       (let [had-active? (any-active-interactions? widgets)]
         (when (or (nil? target-widget)
                   (not= target-type :dropdown))
           (collapse-all-dropdowns! game-state nil))
         (reset-pressed-and-dragging! game-state widgets)
         (or handled? had-active?))))))

(defn handle-scroll
  "Handle scroll wheel movement. Returns true if a scroll-view consumed the event."
  [game-state x y delta-y]
  (let [widgets (draw-order/sort-for-render game-state (wcore/get-all-widgets game-state))
        target (widget-at-point widgets x y)]
    (if-let [[entity-id widget] target]
      (let [widget-data (state/get-component widget :widget)]
        (if (= (:type widget-data) :scroll-view)
          (let [value (state/get-component widget :value)
                bounds (state/get-component widget :bounds)
                items (:items value)
                item-height (double (or (:item-height value) 34.0))
                gap (double (or (:gap value) 6.0))
                content-height (scroll-content-height (count items) item-height gap)
                max-offset (max 0.0 (- content-height (:height bounds)))
                current-offset (double (or (:scroll-offset value) 0.0))]
            (if (pos? max-offset)
              (let [scroll-px (* (- delta-y) (max 24.0 (* 0.7 item-height)))
                    new-offset (-> (+ current-offset scroll-px)
                                   (max 0.0)
                                   (min max-offset))]
                (when (not= new-offset current-offset)
                  (state/update-entity! game-state entity-id
                                        #(assoc-in % [:components :value :scroll-offset] new-offset)))
                true)
              false))
          false))
      false)))

;; =============================================================================
;; Helper Functions
;; =============================================================================

(defn reset-all-interaction-states!
  "Reset all widget interaction states (useful for cleanup)"
  [game-state]
  (let [widgets (wcore/get-all-widgets game-state)]
    (doseq [[entity-id _] widgets]
      (state/update-entity! game-state entity-id
                           #(assoc-in % [:components :interaction]
                                     {:enabled true
                                      :hovered false
                                      :pressed false
                                      :focused false
                                      :hover-cursor :pointer})))))
