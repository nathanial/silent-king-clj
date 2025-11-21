(ns silent-king.reactui.primitives.stack
  "Stack primitives: vstack and hstack layout plus rendering."
  (:require [silent-king.reactui.layout :as layout]
            [silent-king.reactui.render :as render]
            [silent-king.render.commands :as commands]
            [silent-king.color :as color]))

(set! *warn-on-reflection* true)

(defmethod layout/layout-node :vstack
  [node context]
  (let [props (:props node)
        explicit-bounds (or (get-in node [:props :bounds]) {})
        bounds* (layout/resolve-bounds node context)
        padding (layout/expand-padding (:padding props))
        gap (double (or (:gap props) 0.0))
        inner-x (+ (:x bounds*) (:left padding))
        inner-width (max 0.0 (- (:width bounds*) (:left padding) (:right padding)))
        start-y (+ (:y bounds*) (:top padding))
        viewport (:viewport context)
        children (:children node)]
    (loop [remaining children
           acc []
           cursor-y start-y]
      (if (empty? remaining)
        (let [content-height (- cursor-y start-y)
              padded-height (+ (max 0.0 content-height) (:top padding) (:bottom padding))
              height (if (contains? explicit-bounds :height)
                       (:height explicit-bounds)
                       padded-height)
              final-bounds (assoc bounds* :height height)]
          (assoc node
                 :layout {:bounds final-bounds
                          :padding padding}
                 :children acc))
        (let [child (first remaining)
              child-node (layout/layout-node child {:viewport viewport
                                                    :bounds {:x inner-x
                                                             :y cursor-y
                                                             :width inner-width}})
              child-height (double (or (:height (layout/bounds child-node)) 0.0))
              has-more? (seq (rest remaining))
              next-y (+ cursor-y child-height (if has-more? gap 0.0))]
          (recur (rest remaining)
                 (conj acc child-node)
                 next-y))))))

(defmethod layout/layout-node :hstack
  [node context]
  (let [props (:props node)
        explicit-bounds (or (get-in node [:props :bounds]) {})
        bounds* (layout/resolve-bounds node context)
        padding (layout/expand-padding (:padding props))
        gap (double (or (:gap props) 0.0))
        inner-y (+ (:y bounds*) (:top padding))
        inner-x (+ (:x bounds*) (:left padding))
        inner-width (max 0.0 (- (:width bounds*) (:left padding) (:right padding)))
        explicit-height (when (contains? explicit-bounds :height)
                          (double (:height explicit-bounds)))
        inner-height (if explicit-height
                       (max 0.0 (- explicit-height (:top padding) (:bottom padding)))
                       0.0)
        children (:children node)
        viewport (:viewport context)]
    (loop [remaining children
           acc []
           cursor-x inner-x
           max-height 0.0]
      (if (empty? remaining)
        (let [computed-height (+ max-height (:top padding) (:bottom padding))
              height (if explicit-height explicit-height computed-height)
              final-bounds (assoc bounds* :height height)]
          (assoc node
                 :layout {:bounds final-bounds
                          :padding padding}
                 :children acc))
        (let [child (first remaining)
              available-width (max 0.0 (- (+ inner-x inner-width) cursor-x))
              child-node (layout/layout-node child {:viewport viewport
                                                    :bounds {:x cursor-x
                                                             :y inner-y
                                                             :width available-width
                                                             :height inner-height}})
              child-bounds (layout/bounds child-node)
              child-width (double (or (:width child-bounds) 0.0))
              child-height (double (or (:height child-bounds) 0.0))
              has-more? (seq (rest remaining))
              next-x (+ cursor-x child-width (if has-more? gap 0.0))]
          (recur (rest remaining)
                 (conj acc child-node)
                 next-x
                 (max max-height child-height)))))))

(defn plan-stack
  [context node]
  (let [{:keys [background-color]} (:props node)
        bounds (layout/bounds node)
        child-commands (mapcat #(render/plan-node context %) (:children node))]
    (cond-> []
      background-color (conj (commands/rect bounds {:fill-color (color/ensure background-color)}))
      true (into child-commands))))

(defmethod render/plan-node :vstack
  [context node]
  (plan-stack context node))

(defmethod render/plan-node :hstack
  [context node]
  (plan-stack context node))
