(ns silent-king.widgets.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [silent-king.state :as state]
            [silent-king.widgets.core :as wcore]
            [silent-king.widgets.layout :as wlayout]))

(deftest compute-vstack-layout-respects-padding-gap-align
  (testing "VStack layout honors padding, gap, and alignment"
    (let [parent-bounds {:x 5 :y 10 :width 200 :height 400}
          layout {:padding {:top 15 :left 20 :right 10}
                  :gap 5
                  :align :center}
          child-a (state/create-entity :bounds {:width 60 :height 20})
          child-b (state/create-entity :bounds {:width 80 :height 30})
          [first-child second-child] (wcore/compute-vstack-layout parent-bounds layout [child-a child-b])
          first-bounds (second first-child)
          second-bounds (second second-child)]
      (is (= {:x 80 :y 25 :width 60 :height 20} first-bounds))
      (is (= {:x 70 :y 50 :width 80 :height 30} second-bounds)))))

(deftest update-vstack-layout-applies-stretch
  (testing "Child bounds are updated and stretched to the parent content width"
    (state/reset-entity-ids!)
    (let [game-state (atom (state/create-game-state))
          panel (wcore/panel :id :panel
                             :bounds {:x 50 :y 30 :width 300 :height 200})
          child-a (wcore/label "First" :id :child-a :bounds {:width 40 :height 10})
          child-b (wcore/label "Second" :id :child-b :bounds {:width 60 :height 20})
          panel-id (wcore/add-widget-tree! game-state panel [child-a child-b])]
      (state/update-entity! game-state panel-id
                            #(assoc-in % [:components :layout]
                                       {:padding {:top 8 :left 10 :right 10}
                                        :gap 4
                                        :align :stretch}))
      (wcore/update-vstack-layout! game-state panel-id)
      (let [[child-a-id _] (wcore/get-widget-by-id game-state :child-a)
            [child-b-id _] (wcore/get-widget-by-id game-state :child-b)
            child-a-bounds (state/get-component (state/get-entity game-state child-a-id) :bounds)
            child-b-bounds (state/get-component (state/get-entity game-state child-b-id) :bounds)]
        (is (= {:x 60 :y 38 :width 280 :height 10} child-a-bounds))
        (is (= {:x 60 :y 52 :width 280 :height 20} child-b-bounds))))))

(deftest layout-queue-processes-dirty-widgets
  (testing "Queued layout roots recompute bounds before rendering"
    (state/reset-entity-ids!)
    (let [game-state (atom (state/create-game-state))
          panel (wcore/panel :id :panel
                             :bounds {:x 0 :y 0 :width 200 :height 100}
                             :layout {:padding {:left 0 :right 0 :top 0 :bottom 0}
                                      :gap 0
                                      :align :stretch})
          child (wcore/label "Child" :id :child :bounds {:width 40 :height 10})
          panel-id (wcore/add-widget-tree! game-state panel [child])]
      ;; No manual layout run yet; child keeps original width
      (let [[child-id _] (wcore/get-widget-by-id game-state :child)
            child-bounds (state/get-component (state/get-entity game-state child-id) :bounds)]
        (is (= 40 (:width child-bounds))))
      ;; Process dirty layouts and verify child was stretched
      (wlayout/process-layouts! game-state)
      (let [[child-id _] (wcore/get-widget-by-id game-state :child)
            child-bounds (state/get-component (state/get-entity game-state child-id) :bounds)]
        (is (= 200 (:width child-bounds))))
      ;; Mark the panel dirty again and ensure reprocessing works
      (wcore/request-layout! game-state panel-id)
      (wlayout/process-layouts! game-state)
      (let [[child-id _] (wcore/get-widget-by-id game-state :child)
            child-bounds (state/get-component (state/get-entity game-state child-id) :bounds)]
        (is (= 200 (:width child-bounds)))))))
