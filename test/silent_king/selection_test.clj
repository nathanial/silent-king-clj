(ns silent-king.selection-test
  (:require [clojure.test :refer [deftest is]]
            [silent-king.selection :as selection]
            [silent-king.state :as state]
            [silent-king.test-fixtures :as fixtures]))

(deftest handle-screen-click-selects-star
  (let [game-state (atom (state/create-game-state))
        _ (state/reset-world-ids! game-state)
        _ (fixtures/reset-world! game-state)
        star-id (fixtures/add-test-star! game-state 120.0 80.0)
        neighbor-id (fixtures/add-test-star! game-state 200.0 80.0)
        _ (fixtures/add-test-hyperlane! game-state star-id neighbor-id)
        _ (fixtures/recompute-neighbors! game-state)]
    (is (true? (selection/handle-screen-click! game-state 120.0 80.0)))
    (is (= star-id (state/selected-star-id game-state)))
    (is (true? (state/star-inspector-visible? game-state)))
    (let [view (selection/selected-view game-state)
          connection (first (:connections view))]
      (is (= "Star #1" (:name view)))
      (is (= neighbor-id (:neighbor-id connection))))))

(deftest handle-screen-click-clears-when-empty
  (let [game-state (atom (state/create-game-state))
        _ (state/reset-world-ids! game-state)
        _ (fixtures/reset-world! game-state)
        _ (fixtures/add-test-star! game-state 50.0 40.0)]
    (selection/handle-screen-click! game-state 50.0 40.0)
    (is (some? (state/selected-star-id game-state)))
    (is (false? (selection/handle-screen-click! game-state 800.0 800.0)))
    (is (nil? (state/selected-star-id game-state)))
    (is (false? (state/star-inspector-visible? game-state)))))
