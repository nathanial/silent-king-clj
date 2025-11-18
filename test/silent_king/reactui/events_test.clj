(ns silent-king.reactui.events-test
  (:require [clojure.test :refer [deftest is testing]]
            [silent-king.reactui.events :as events]
            [silent-king.state :as state]))

(deftest dispatch-toggle-hyperlanes
  (let [game-state (atom (state/create-game-state))]
    (is (true? (state/hyperlanes-enabled? game-state)))
    (events/dispatch-event! game-state [:ui/toggle-hyperlanes])
    (is (false? (state/hyperlanes-enabled? game-state)))))

(deftest dispatch-set-zoom
  (let [game-state (atom (state/create-game-state))]
    (events/dispatch-event! game-state [:ui/set-zoom 2.5])
    (is (= 2.5 (get-in @game-state [:camera :zoom])))
    (testing "values are clamped"
      (events/dispatch-event! game-state [:ui/set-zoom 40.0])
      (is (= 10.0 (get-in @game-state [:camera :zoom])))
      (events/dispatch-event! game-state [:ui/set-zoom 0.1])
      (is (= 0.4 (get-in @game-state [:camera :zoom]))))))

(deftest dispatch-set-scale
  (let [game-state (atom (state/create-game-state))]
    (events/dispatch-event! game-state [:ui/set-scale 2.5])
    (is (= 2.5 (state/ui-scale game-state)))
    (events/dispatch-event! game-state [:ui/set-scale 10.0])
    (is (= 3.0 (state/ui-scale game-state)))))
