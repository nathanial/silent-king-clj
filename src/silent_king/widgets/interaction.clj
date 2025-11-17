(ns silent-king.widgets.interaction
  "Widget interaction system - hit testing and event handling"
  (:require [silent-king.state :as state]
            [silent-king.widgets.core :as wcore]))

(set! *warn-on-reflection* true)

;; =============================================================================
;; Hit Testing
;; =============================================================================

(defn point-in-bounds?
  "Check if point (x, y) is inside bounds"
  [x y bounds]
  (and bounds
       (:x bounds) (:y bounds) (:width bounds) (:height bounds)
       (>= x (:x bounds))
       (>= y (:y bounds))
       (<= x (+ (:x bounds) (:width bounds)))
       (<= y (+ (:y bounds) (:height bounds)))))

(defn- widget-at-point
  "Return the first widget matching the screen coordinate from a cached list."
  [widgets x y]
  (let [sorted-widgets (sort-by
                        (fn [[_ w]]
                          (get-in (state/get-component w :layout) [:z-index] 0))
                        >
                        widgets)]
    (some (fn [[entity-id widget]]
            (let [bounds (state/get-component widget :bounds)
                  interaction (state/get-component widget :interaction)]
              (when (and (:enabled interaction)
                         (point-in-bounds? x y bounds))
                [entity-id widget])))
          sorted-widgets)))

(defn find-widget-at-point
  "Find topmost widget at screen coordinates (x, y).
  Returns [entity-id widget-entity] or nil if no widget found."
  [game-state x y]
  (widget-at-point (wcore/get-all-widgets game-state) x y))

;; =============================================================================
;; Mouse Event Handlers
;; =============================================================================

(defn handle-mouse-move
  "Handle mouse move event. Returns true if a widget handled the event."
  [game-state x y]
  (let [widgets (wcore/get-all-widgets game-state)
        hovered-widget (widget-at-point widgets x y)]

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
                ;; Calculate new value based on mouse position
                normalized (/ (- x (:x bounds)) (:width bounds))
                clamped (max 0.0 (min 1.0 normalized))
                raw-value (+ min-val (* clamped (- max-val min-val)))
                ;; Round to step
                stepped-value (if step
                               (* step (Math/round (/ raw-value step)))
                               raw-value)
                new-value (max min-val (min max-val stepped-value))]

            ;; Update slider value
            (state/update-entity! game-state entity-id
                                 #(assoc-in % [:components :value :current] new-value))

            ;; Call on-change callback
            (when-let [on-change (:on-change interaction)]
              (on-change new-value))))))

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
  (let [widgets (wcore/get-all-widgets game-state)
        target-widget (widget-at-point widgets x y)
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

                       :else false))
                   false)]
    (if pressed?
      handled?
      (let [had-active? (any-active-interactions? widgets)]
        (reset-pressed-and-dragging! game-state widgets)
        (or handled? had-active?)))))

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
