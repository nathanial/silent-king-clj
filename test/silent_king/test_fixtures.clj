(ns silent-king.test-fixtures
  (:require [silent-king.color :as color]
            [silent-king.schemas :as schemas]
            [silent-king.state :as state]))

(defn reset-world!
  "Clear stars/hyperlanes and reset id counters on the supplied game-state atom."
  [game-state]
  (state/set-world! game-state {:stars {}
                                :hyperlanes []
                                :voronoi-cells {}
                                :voronoi-generated? false
                                :neighbors-by-star-id {}
                                :next-star-id 0
                                :next-hyperlane-id 0}))

(defn add-test-star!
  "Insert a test star into game-state; returns the assigned id."
  ([game-state x y]
   (add-test-star! game-state x y {}))
  ([game-state x y {:keys [density size rotation-speed sprite-path id]}]
   (state/add-star! game-state {:id id
                                :x (double x)
                                :y (double y)
                                :size (double (or size 40.0))
                                :density (double (or density 0.5))
                                :rotation-speed (double (or rotation-speed 1.0))
                                :sprite-path (or sprite-path "stars/bright.png")})))

(defn add-test-hyperlane!
  "Insert a hyperlane between the supplied stars; returns the assigned id."
  [game-state from-id to-id]
  (state/add-hyperlane! game-state {:from-id from-id
                                    :to-id to-id
                                    :base-width 2.0
                                    :color-start (color/hsv 220 60 100)
                                    :color-end (color/hsv 220 75 80)
                                    :glow-color (color/hsv 220 60 100 0.25)
                                    :animation-offset 0.0}))

(defn recompute-neighbors!
  "Derive neighbors-by-star-id from the current hyperlanes and store it."
  [game-state]
  (let [neighbors (reduce (fn [acc {:keys [from-id to-id] :as hyperlane}]
                            (-> acc
                                (update from-id (fnil conj []) {:neighbor-id to-id
                                                                :hyperlane hyperlane})
                                (update to-id (fnil conj []) {:neighbor-id from-id
                                                              :hyperlane hyperlane})))
                          {}
                          (state/hyperlanes game-state))]
    (schemas/validate-if-enabled! schemas/NeighborsByStarId neighbors "neighbors-by-star-id")
    (state/set-neighbors! game-state neighbors)
    neighbors))
