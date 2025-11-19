(ns silent-king.selection-test
  (:require [clojure.test :refer [deftest is testing]]
            [silent-king.selection :as selection]
            [silent-king.state :as state]))

(defn- add-test-star
  [game-state x y]
  (state/add-entity! game-state
                     (state/create-entity
                      :position {:x x :y y}
                      :renderable {:path "stars/bright.png"}
                      :transform {:size 40.0}
                      :physics {:rotation-speed 1.0}
                      :star {:density 0.5})))

(deftest handle-screen-click-selects-star
  (state/reset-entity-ids!)
  (let [game-state (atom (state/create-game-state))
        star-id (add-test-star game-state 120.0 80.0)
        neighbor-id (add-test-star game-state 200.0 80.0)
        _ (state/add-entity! game-state
                             (state/create-entity
                              :hyperlane {:from-id star-id
                                          :to-id neighbor-id}))]
    (is (true? (selection/handle-screen-click! game-state 120.0 80.0)))
    (is (= star-id (state/selected-star-id game-state)))
    (is (true? (state/star-inspector-visible? game-state)))
    (let [view (selection/selected-view game-state)
          connection (first (:connections view))]
      (is (= "Star #1" (:name view)))
      (is (= neighbor-id (:neighbor-id connection))))))

(deftest handle-screen-click-clears-when-empty
  (state/reset-entity-ids!)
  (let [game-state (atom (state/create-game-state))
        _ (add-test-star game-state 50.0 40.0)]
    (selection/handle-screen-click! game-state 50.0 40.0)
    (is (some? (state/selected-star-id game-state)))
    (is (false? (selection/handle-screen-click! game-state 800.0 800.0)))
    (is (nil? (state/selected-star-id game-state)))
    (is (false? (state/star-inspector-visible? game-state)))))
