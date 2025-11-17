(ns silent-king.widgets.draw-order-test
  (:require [clojure.test :refer [deftest is testing]]
            [silent-king.state :as state]
            [silent-king.widgets.core :as wcore]
            [silent-king.widgets.draw-order :as draw-order]))

(deftest containers-render-before-children
  (testing "Parent widgets render before children at the same z-index"
    (state/reset-entity-ids!)
    (let [game-state (atom (state/create-game-state))
          panel (wcore/panel :id :panel)
          label (wcore/label "Child" :id :child)
          _ (wcore/add-widget-tree! game-state panel [label])
          sorted (draw-order/sort-for-render game-state
                                             (wcore/get-all-widgets game-state))
          order (map (fn [[_ entity]]
                       (get-in entity [:components :widget :id]))
                     sorted)]
      (is (= [:panel :child] order)))))
