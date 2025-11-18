(ns silent-king.reactui.interaction
  "Hit testing and pointer interaction helpers for Reactified primitives."
  (:require [silent-king.reactui.layout :as layout]))

(set! *warn-on-reflection* true)

(def ^:private interactive-types
  #{:button :slider})

(defn- contains-point?
  [{:keys [x y width height]} px py]
  (and (number? x) (number? y)
       (number? width) (number? height)
       (>= px x)
       (<= px (+ x width))
       (>= py y)
       (<= py (+ y height))))

(defn- hit-node
  [node px py]
  (when node
    (let [bounds (layout/bounds node)]
      (when (contains-point? bounds px py)
        (or (some (fn [child]
                    (hit-node child px py))
                  (reverse (:children node)))
            (when (interactive-types (:type node))
              node))))))

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
  (if-let [node (hit-node layout-tree px py)]
    (case (:type node)
      :button (if-let [event (-> node :props :on-click)]
                (if (vector? event) [event] [])
                [])
      :slider (if-let [event (-> node :props :on-change)]
                (if (vector? event)
                  [(conj event (slider-value-from-point node px))]
                  [])
                [])
      [])
    []))
