(ns silent-king.widgets.minimap-test
  (:require [clojure.test :refer :all]
            [silent-king.state :as state]
            [silent-king.widgets.animation :as wanim]
            [silent-king.widgets.core :as wcore]
            [silent-king.widgets.interaction :as winteraction]))

(defn- add-star!
  [game-state x y]
  (state/add-entity! game-state
                     (state/create-entity
                      :position {:x x :y y})))

(deftest minimap-click-centers-wide-galaxy
  (testing "Clicking that hits the minimap center recenters even when the galaxy is wider than tall"
    (state/reset-entity-ids!)
    (let [game-state (atom (state/create-game-state))
          _ (add-star! game-state -200.0 -100.0)
          _ (add-star! game-state 200.0 100.0)
          _ (wcore/add-widget! game-state (wcore/minimap :id :wide-minimap
                                                         :bounds {:x 0 :y 0 :width 200 :height 200}))
          pan-calls (atom [])]
      (with-redefs [wanim/start-camera-pan!
                    (fn [& args]
                      (swap! pan-calls conj args))]
        (is (true? (winteraction/handle-mouse-click game-state 100 100 false)))
        (is (= [[game-state 0.0 0.0 0.5]] @pan-calls)
            "Expected camera to target the world center")))))

(deftest minimap-click-centers-tall-galaxy
  (testing "Clicking the minimap center recenters even when the galaxy is taller than it is wide"
    (state/reset-entity-ids!)
    (let [game-state (atom (state/create-game-state))
          _ (add-star! game-state -100.0 -200.0)
          _ (add-star! game-state 100.0 200.0)
          _ (wcore/add-widget! game-state (wcore/minimap :id :tall-minimap
                                                         :bounds {:x 0 :y 0 :width 200 :height 200}))
          pan-calls (atom [])]
      (with-redefs [wanim/start-camera-pan!
                    (fn [& args]
                      (swap! pan-calls conj args))]
        (is (true? (winteraction/handle-mouse-click game-state 100 100 false)))
        (is (= [[game-state 0.0 0.0 0.5]] @pan-calls))))))

(deftest minimap-ignores-click-when-hidden
  (testing "Minimap clicks are ignored when the feature flag is disabled"
    (state/reset-entity-ids!)
    (let [game-state (atom (assoc (state/create-game-state) :features {:minimap? false}))
          _ (add-star! game-state -200.0 -200.0)
          _ (add-star! game-state 200.0 200.0)
          _ (wcore/add-widget! game-state (wcore/minimap :id :hidden-minimap
                                                         :bounds {:x 0 :y 0 :width 200 :height 200}))
          pan-calls (atom [])]
      (with-redefs [wanim/start-camera-pan!
                    (fn [& args]
                      (swap! pan-calls conj args))]
        (is (false? (winteraction/handle-mouse-click game-state 100 100 false)))
        (is (empty? @pan-calls))))))
