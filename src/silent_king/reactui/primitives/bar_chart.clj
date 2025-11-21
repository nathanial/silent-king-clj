(ns silent-king.reactui.primitives.bar-chart
  "Bar chart primitive: normalization, layout, and rendering."
  (:require [silent-king.reactui.core :as core]
            [silent-king.reactui.layout :as layout]
            [silent-king.reactui.render :as render]
            [silent-king.render.commands :as commands]
            [silent-king.color :as color]))

(set! *warn-on-reflection* true)

(defn bar-chart-props
  [props _raw-children]
  (update props :values #(vec (or % []))))

(defmethod core/normalize-tag :bar-chart
  [_ props child-forms]
  ((core/leaf-normalizer :bar-chart bar-chart-props) props child-forms))

(defmethod layout/layout-node :bar-chart
  [node context]
  (let [props (:props node)
        bounds* (layout/resolve-bounds node context)
        width (double (if (pos? (:width bounds*))
                        (:width bounds*)
                        (:width (:bounds context))))
        height (double (if (pos? (:height bounds*))
                         (:height bounds*)
                         80.0))
        values (mapv (fn [v]
                       (if (number? v)
                         (double v)
                         0.0))
                     (or (:values props) []))
        explicit-min (:min props)
        explicit-max (:max props)
        auto-min (when (seq values) (apply min values))
        auto-max (when (seq values) (apply max values))
        min-value (double (or explicit-min auto-min 0.0))
        raw-max (double (or explicit-max auto-max min-value))
        max-value (if (= min-value raw-max)
                    (+ min-value 1.0)
                    raw-max)
        bar-gap (double (max 0.0 (or (:bar-gap props) 2.0)))
        final-bounds (-> bounds*
                         (assoc :width width)
                         (assoc :height height))]
    (assoc node
           :layout {:bounds final-bounds
                    :bar-chart {:values values
                                :min min-value
                                :max max-value
                                :bar-gap bar-gap}}
           :children [])))

(defn plan-bar-chart
  [node]
  (let [{:keys [x y width height]} (layout/bounds node)
        {:keys [values min max bar-gap]} (get-in node [:layout :bar-chart])
        {:keys [background-color bar-color grid-color baseline-value]} (:props node)
        background-color (or (color/ensure background-color) (color/hex 0x3310131C))
        bar-color (or (color/ensure bar-color) (color/hex 0xFF9CDCFE))
        grid-color (or (color/ensure grid-color) (color/hex 0x33FFFFFF))
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
        baseline-ratio (render/clamp01 (/ (- baseline min-value) span))
        baseline-y (+ y (* (- 1.0 baseline-ratio) height))]
    (cond-> [(commands/rect {:x x :y y :width width :height height}
                            {:fill-color background-color})
             (commands/line {:x x :y baseline-y}
                            {:x (+ x width) :y baseline-y}
                            {:stroke-color grid-color
                             :stroke-width 1.0})]
      (pos? count) (into (map (fn [[idx value]]
                                (let [ratio (render/clamp01 (/ (- (double value) min-value) span))
                                      bar-height (* ratio height)
                                      bar-x (+ x (* idx (+ bar-width gap)))
                                      bar-y (+ y (- height bar-height))]
                                  (commands/rect {:x bar-x
                                                  :y bar-y
                                                  :width bar-width
                                                  :height bar-height}
                                                 {:fill-color bar-color})))
                              (map-indexed vector bars))))))

(defmethod render/plan-node :bar-chart
  [_context node]
  (plan-bar-chart node))
