(ns silent-king.reactui.events-test
  (:require [clojure.test :refer [deftest is testing]]
            [silent-king.camera :as camera]
            [silent-king.reactui.events :as events]
            [silent-king.state :as state]
            [silent-king.test-fixtures :as fixtures]))

(deftest dispatch-toggle-hyperlanes
  (let [game-state (atom (state/create-game-state))]
    (is (true? (state/hyperlanes-enabled? game-state)))
    (events/dispatch-event! game-state [:ui/toggle-hyperlanes])
    (is (false? (state/hyperlanes-enabled? game-state)))))

(deftest dispatch-toggle-voronoi
  (let [game-state (atom (state/create-game-state))]
    (is (true? (state/voronoi-enabled? game-state)))
    (events/dispatch-event! game-state [:ui/toggle-voronoi])
    (is (false? (state/voronoi-enabled? game-state)))))

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

(deftest dispatch-voronoi-setting-events
  (let [game-state (atom (state/create-game-state))]
    (events/dispatch-event! game-state [:voronoi/set-enabled? true])
    (is (true? (state/voronoi-enabled? game-state)))
    (events/dispatch-event! game-state [:voronoi/set-opacity 2.0])
    (is (= 1.0 (:opacity (state/voronoi-settings game-state))))
    (events/dispatch-event! game-state [:voronoi/set-line-width 5.0])
    (is (= 4.0 (:line-width (state/voronoi-settings game-state))))
    (events/dispatch-event! game-state [:voronoi/set-color-scheme :by-degree])
    (is (= :by-degree (:color-scheme (state/voronoi-settings game-state))))
    (events/dispatch-event! game-state [:voronoi/set-show-centroids? true])
    (is (true? (:show-centroids? (state/voronoi-settings game-state))))
    (events/dispatch-event! game-state [:voronoi/set-hide-border-cells? true])
    (is (true? (:hide-border-cells? (state/voronoi-settings game-state))))
    (events/dispatch-event! game-state [:voronoi/set-relax-iterations 10])
    (is (= state/relax-iterations-limit (:relax-iterations (state/voronoi-settings game-state))))
    (events/dispatch-event! game-state [:voronoi/set-relax-step -1.0])
    (is (= 0.0 (:relax-step (state/voronoi-settings game-state))))
    (events/dispatch-event! game-state [:voronoi/set-relax-max-displacement 800.0])
    (is (= 500.0 (:relax-max-displacement (state/voronoi-settings game-state))))
    (events/dispatch-event! game-state [:voronoi/set-relax-clip? false])
    (is (false? (:relax-clip-to-envelope? (state/voronoi-settings game-state))))
    (events/dispatch-event! game-state [:voronoi/set-jaggedness 1.2])
    (is (= 1.2 (:jaggedness (state/voronoi-settings game-state))))
    (swap! game-state assoc-in [:voronoi-settings :opacity] 0.2)
    (events/dispatch-event! game-state [:voronoi/reset])
    (is (= state/default-voronoi-settings (:voronoi-settings @game-state)))))

(deftest dispatch-dropdown-events
  (let [game-state (atom (state/create-game-state))]
    (events/dispatch-event! game-state [:ui.dropdown/toggle :colors])
    (is (true? (state/dropdown-open? game-state :colors)))
    (events/dispatch-event! game-state [:ui.dropdown/close :colors])
    (is (false? (state/dropdown-open? game-state :colors)))))

(defn- add-test-star!
  [game-state x y]
  (fixtures/add-test-star! game-state x y))

(deftest dispatch-clear-selection
  (let [game-state (atom (state/create-game-state))]
    (state/set-selection! game-state {:star-id 1})
    (state/show-star-inspector! game-state)
    (events/dispatch-event! game-state [:ui/clear-selection])
    (is (nil? (state/selected-star-id game-state)))
    (is (false? (state/star-inspector-visible? game-state)))))

(deftest dispatch-zoom-to-selected-star
  (let [game-state (atom (state/create-game-state))
        _ (state/reset-world-ids! game-state)
        star-id (add-test-star! game-state 120.0 80.0)]
    (state/set-selection! game-state {:star-id star-id})
    (state/set-ui-viewport! game-state {:width 800.0 :height 600.0})
    (events/dispatch-event! game-state [:ui/zoom-to-selected-star {:zoom 3.0}])
    (let [camera-state (state/get-camera game-state)
          expected-pan-x (camera/center-pan 120.0 3.0 800.0)
          expected-pan-y (camera/center-pan 80.0 3.0 600.0)]
      (is (= 3.0 (:zoom camera-state)))
      (is (= expected-pan-x (:pan-x camera-state)))
      (is (= expected-pan-y (:pan-y camera-state))))))
