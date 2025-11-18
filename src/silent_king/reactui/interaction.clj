(ns silent-king.reactui.interaction
  "Hit testing and pointer interaction helpers for Reactified primitives."
  (:require [silent-king.reactui.events :as ui-events]
            [silent-king.reactui.layout :as layout]))

(set! *warn-on-reflection* true)

(def ^:private interactive-types
  #{:button :slider :dropdown})

(declare dropdown-region)

(defn- contains-point?
  [{:keys [x y width height]} px py]
  (and (number? x) (number? y)
       (number? width) (number? height)
       (>= px x)
       (<= px (+ x width))
       (>= py y)
       (<= py (+ y height))))

(defn- dropdown-overlay-hit
  [node px py]
  (when node
    (or (when (= (:type node) :dropdown)
          (let [region (dropdown-region node px py)]
            (when (= (:type region) :option)
              node)))
        (some #(dropdown-overlay-hit % px py) (:children node)))))

(defn node-at
  [node px py]
  (when node
    (or (dropdown-overlay-hit node px py)
        (let [bounds (layout/bounds node)
              type (:type node)]
          (when (contains-point? bounds px py)
            (or (some #(node-at % px py) (reverse (:children node)))
                (when (= type :dropdown)
                  (let [region (dropdown-region node px py)]
                    (when region
                      node)))
                (when (interactive-types type)
                  node)))))))

(defn dropdown-region
  [node px py]
  (let [{:keys [header options expanded?]} (get-in node [:layout :dropdown])]
    (cond
      (contains-point? header px py)
      {:type :header}

      (and expanded?)
      (some (fn [option]
              (when (contains-point? (:bounds option) px py)
                {:type :option
                 :value (:value option)}))
            options)

      :else
      nil)))

(defn- slider-value-from-point
  [node px]
  (let [track (get-in node [:layout :slider :track])
        {:keys [min max step]} (get-in node [:layout :slider :range])
        min-value (double min)
        max-value (double max)
        width (double (or (:width track) 0.0))
        relative (if (pos? width)
                   (/ (- px (double (:x track))) width)
                   0.0)
        ratio (-> relative
                  (clojure.core/max 0.0)
                  (clojure.core/min 1.0))
        span (- max-value min-value)
        unclamped (+ min-value (* ratio span))
        stepped (if (and step (pos? step))
                  (let [steps (/ (- unclamped min-value) step)]
                    (+ min-value (* (Math/round (double steps)) step)))
                  unclamped)
        value (-> stepped
                  (clojure.core/max min-value)
                  (clojure.core/min max-value))]
    value))

(defn click->events
  "Return any event vectors produced by clicking at px/py."
  [layout-tree px py]
  (if-let [node (node-at layout-tree px py)]
    (case (:type node)
      :button (if-let [event (-> node :props :on-click)]
                (if (vector? event) [event] [])
                [])
      :slider (if-let [event (-> node :props :on-change)]
                (if (vector? event)
                  [(conj event (slider-value-from-point node px))]
                  [])
                [])
      :dropdown (if-let [region (dropdown-region node px py)]
                  (case (:type region)
                    :header (let [event (-> node :props :on-toggle)]
                              (if (vector? event) [event] []))
                    :option (let [select (-> node :props :on-select)
                                  close (-> node :props :on-close)
                                  value (:value region)
                                  events []
                                  events (if (vector? select)
                                           (conj events (conj select value))
                                           events)
                                  events (if (vector? close)
                                           (conj events close)
                                           events)]
                              events)
                    [])
                  [])
      [])
    []))

(defn dropdown-click!
  [node game-state px py]
  (when-let [region (dropdown-region node px py)]
    (case (:type region)
      :header (when-let [event (-> node :props :on-toggle)]
                (when (vector? event)
                  (ui-events/dispatch-event! game-state event))
                true)
      :option (let [value (:value region)]
                (when-let [event (-> node :props :on-select)]
                  (when (vector? event)
                    (ui-events/dispatch-event! game-state (conj event value))))
                (when-let [close-event (-> node :props :on-close)]
                  (when (vector? close-event)
                    (ui-events/dispatch-event! game-state close-event)))
                true)
      false)))

(defn slider-drag!
  [node game-state px]
  (when-let [event (-> node :props :on-change)]
    (when (vector? event)
      (let [value (slider-value-from-point node px)]
        (ui-events/dispatch-event! game-state (conj event value))))))

(defn activate-button!
  [node game-state px py]
  (let [bounds (layout/bounds node)]
    (when (contains-point? bounds px py)
      (when-let [event (-> node :props :on-click)]
        (when (vector? event)
          (ui-events/dispatch-event! game-state event))))))
