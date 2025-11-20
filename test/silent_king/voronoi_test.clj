(ns silent-king.voronoi-test
  (:require [clojure.test :refer [deftest is testing]]
            [silent-king.state :as state]
            [silent-king.voronoi :as voronoi]))

(deftest generate-voronoi-basic
  (let [stars [{:id 1 :x 0.0 :y 0.0}
               {:id 2 :x 120.0 :y 0.0}
               {:id 3 :x 60.0 :y 80.0}]
        {:keys [voronoi-cells elapsed-ms]} (voronoi/generate-voronoi stars)]
    (is (= (set (map :id stars)) (set (keys voronoi-cells))))
    (is (<= 0 elapsed-ms))
    (doseq [[id cell] voronoi-cells]
      (is (= id (:star-id cell)))
      (is (seq (:vertices cell)))
      (is (>= (count (:vertices cell)) 2))
      (is (map? (:bbox cell)))
      (is (map? (:centroid cell)))
      (let [star (first (filter #(= (:id %) id) stars))
            centroid (:centroid cell)]
        (is (< (Math/abs (- (double (:x centroid)) (double (:x star)))) 400.0))
        (is (< (Math/abs (- (double (:y centroid)) (double (:y star)))) 400.0))))))

(deftest generate-voronoi-insufficient-stars
  (let [{:keys [voronoi-cells]} (voronoi/generate-voronoi [{:id 1 :x 0.0 :y 0.0}])]
    (is (empty? voronoi-cells))))

(deftest voronoi-settings-defaults
  (let [game-state (atom (state/create-game-state))]
    (is (= state/default-voronoi-settings (state/voronoi-settings game-state)))
    (is (true? (state/voronoi-enabled? game-state)))
    (state/set-voronoi-setting! game-state :enabled? true)
    (is (true? (state/voronoi-enabled? game-state)))
    (state/reset-voronoi-settings! game-state)
    (is (= state/default-voronoi-settings (:voronoi-settings @game-state)))))
