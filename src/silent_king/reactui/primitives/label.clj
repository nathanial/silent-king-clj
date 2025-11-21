(ns silent-king.reactui.primitives.label
  "Label primitive: normalization, layout, and rendering."
  (:require [silent-king.reactui.core :as core]
            [silent-king.reactui.layout :as layout]
            [silent-king.reactui.render :as render]
            [silent-king.render.commands :as commands]
            [silent-king.color :as color]))

(set! *warn-on-reflection* true)

(defn label-props
  "Derive label props from explicit props or textual children."
  [props raw-children]
  (let [text (or (:text props)
                 (core/collect-text raw-children)
                 "")]
    (assoc props :text text)))

(defmethod core/normalize-tag :label
  [_ props child-forms]
  ((core/leaf-normalizer :label label-props) props child-forms))

(defmethod layout/layout-node :label
  [node context]
  (let [props (:props node)
        text (or (:text props) "")
        font-size (double (or (:font-size props) 16.0))
        line-height (double (or (:line-height props) (+ font-size 4.0)))
        bounds* (layout/resolve-bounds node context)
        width-provided (double (or (:width bounds*) 0.0))
        measured-width (double (layout/estimate-text-width text font-size))
        width (double (if (pos? width-provided)
                        (max width-provided measured-width)
                        (max measured-width 0.0)))
        final-bounds (-> bounds*
                         (assoc :width width)
                         (assoc :height line-height))]
    (assoc node
           :layout {:bounds final-bounds}
           :children [])))

(defn plan-label
  [node]
  (let [{:keys [text color font-size]} (:props node)
        {:keys [x y]} (layout/bounds node)
        size (double (or font-size 16.0))
        baseline (+ y size)]
    [(commands/text {:text (or text "")
                     :position {:x x :y baseline}
                     :font {:size size}
                     :color (or (color/ensure color) (color/hex 0xFFFFFFFF))})]))

(defmethod render/plan-node :label
  [_ node]
  (plan-label node))
