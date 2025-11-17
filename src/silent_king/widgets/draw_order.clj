(ns silent-king.widgets.draw-order
  "Helpers for determining stable widget render order"
  (:require [silent-king.state :as state]))

(set! *warn-on-reflection* true)

(defn widget-depth
  "Return how many ancestors a widget has so children can render above parents."
  [game-state widget-entity]
  (loop [depth 0
         current widget-entity]
    (if-let [parent-id (get-in current [:components :widget :parent-id])]
      (if-let [parent (state/get-entity game-state parent-id)]
        (recur (inc depth) parent)
        depth)
      depth)))

(defn sort-for-render
  "Sort widgets by z-index, hierarchy depth, then entity-id for determinism."
  [game-state widgets]
  (let [visible (filter (fn [[_ widget]]
                          (get-in (state/get-component widget :widget) [:visible?] true))
                        widgets)]
    (sort-by
     (fn [[entity-id widget]]
       [(get-in (state/get-component widget :layout) [:z-index] 0)
        (widget-depth game-state widget)
        entity-id])
     visible)))
