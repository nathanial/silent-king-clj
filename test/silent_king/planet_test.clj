(ns silent-king.planet-test
  (:require [clojure.test :refer [deftest is]]
            [silent-king.galaxy :as galaxy]))

(defn- close?
  [a b]
  (< (Math/abs (- (double a) (double b))) 1.0e-3))

(deftest planet-count-scales-with-density
  (let [sparse (galaxy/generate-planets-for-star ["p1"] {:id 42 :density 0.1})
        dense (galaxy/generate-planets-for-star ["p1"] {:id 42 :density 0.85})]
    (is (<= (count sparse) 1))
    (is (>= (count dense) 3))
    (is (<= (count dense) 5))
    (is (<= (count sparse) (count dense)))))

(deftest planet-radii-are-ascending
  (let [planets (galaxy/generate-planets-for-star ["p1"] {:id 7 :density 0.7})
        radii (map :radius planets)]
    (is (every? pos? (map - (rest radii) radii)))
    (is (every? #(>= % 15.0) radii))
    (is (every? #(<= % 90.0) radii))
    (is (every? #(>= % 10.0) (map - (rest radii) radii)))))

(deftest planet-position-reflects-orbit
  (let [planet {:radius 300.0
                :orbital-period 10.0
                :phase 0.0}
        star {:x 1000.0 :y 2000.0}
        position (galaxy/planet-position planet star 2.5)]
    (is (close? (:x position) 1000.0))
    (is (close? (:y position) 2300.0))))
