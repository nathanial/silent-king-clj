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

(deftest dispatch-toggle-hyperlane-panel
  (let [game-state (atom (state/create-game-state))]
    (is (true? (state/hyperlane-panel-expanded? game-state)))
    (events/dispatch-event! game-state [:ui/toggle-hyperlane-panel])
    (is (false? (state/hyperlane-panel-expanded? game-state)))))

(deftest dispatch-hyperlane-setting-events
  (let [game-state (atom (state/create-game-state))]
    (events/dispatch-event! game-state [:hyperlanes/set-enabled? false])
    (is (false? (state/hyperlanes-enabled? game-state)))
    (events/dispatch-event! game-state [:hyperlanes/set-opacity 2.0])
    (is (= 1.0 (:opacity (state/hyperlane-settings game-state))))
    (events/dispatch-event! game-state [:hyperlanes/set-animation-speed 0.05])
    (is (= 0.1 (:animation-speed (state/hyperlane-settings game-state))))
    (events/dispatch-event! game-state [:hyperlanes/set-line-width 5.0])
    (is (= 3.0 (:line-width (state/hyperlane-settings game-state))))
    (events/dispatch-event! game-state [:hyperlanes/set-animation? false])
    (is (false? (:animation? (state/hyperlane-settings game-state))))
    (events/dispatch-event! game-state [:hyperlanes/set-color-scheme :green])
    (is (= :green (:color-scheme (state/hyperlane-settings game-state))))
    (swap! game-state assoc-in [:hyperlane-settings :opacity] 0.8)
    (events/dispatch-event! game-state [:hyperlanes/reset])
    (is (= state/default-hyperlane-settings (:hyperlane-settings @game-state)))))
