(ns silent-king.ui.star-inspector-test
  (:require [clojure.test :refer :all]
            [silent-king.state :as state]
            [silent-king.ui.star-inspector :as inspector]))

(defn- make-star
  [x y]
  (state/create-entity
   :position {:x x :y y}
   :renderable {:path "stars/bright.png"}
   :transform {:size 40.0
               :rotation 0.0}
   :physics {:rotation-speed 1.0}
   :star {:density 0.5}))

(deftest handle-star-click-selects-and-builds-connections
  (state/reset-entity-ids!)
  (let [game-state (atom (state/create-game-state))
        star-a (state/add-entity! game-state (make-star 0.0 0.0))
        star-b (state/add-entity! game-state (make-star 80.0 0.0))
        _ (state/add-entity! game-state
                             (state/create-entity
                              :hyperlane {:from-id star-a
                                          :to-id star-b}
                              :visual {:base-width 2.0}))
        _ (inspector/create-star-inspector! game-state)]
    (is (true? (inspector/handle-star-click! game-state 5.0 0.0)))
    (is (= star-a (get-in @game-state [:selection :star-id])))
    (is (true? (get-in @game-state [:ui :star-inspector :visible?])))
    (let [connections (get-in @game-state [:selection :details :connections])]
      (is (= 1 (count connections)))
      (is (= star-b (:neighbor-id (first connections))))
      (is (= 80 (Math/round (:distance (first connections))))))))

(deftest clicking-empty-space-clears-selection
  (state/reset-entity-ids!)
  (let [game-state (atom (state/create-game-state))
        _ (state/add-entity! game-state (make-star 0.0 0.0))
        _ (inspector/create-star-inspector! game-state)]
    (inspector/handle-star-click! game-state 0.0 0.0)
    (is (some? (get-in @game-state [:selection :star-id])))
    (is (true? (get-in @game-state [:ui :star-inspector :visible?])))
    (inspector/handle-star-click! game-state 400.0 400.0)
    (is (nil? (get-in @game-state [:selection :star-id])))
    (is (false? (get-in @game-state [:ui :star-inspector :visible?])))))
