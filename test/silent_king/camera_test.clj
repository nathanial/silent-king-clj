(ns silent-king.camera-test
  (:require [clojure.test :refer [deftest is testing]]
            [silent-king.camera :as camera]))

(deftest transform-position-test
  (testing "transform-position and inverse-transform-position are inverses"
    (let [world-x 100.0
          zoom 2.0
          pan-x 50.0
          screen-x (camera/transform-position world-x zoom pan-x)
          inverted-x (camera/inverse-transform-position screen-x zoom pan-x)]
      (is (= 100.0 inverted-x))))
  
  (testing "transform-position applies zoom and pan"
    ;; scale = zoom^2.5 = 2.0^2.5 approx 5.656
    ;; pos = 10 * 5.656 + 20 = 76.56
    (let [world 10.0
          zoom 2.0
          pan 20.0
          scale (Math/pow 2.0 2.5)
          expected (+ (* 10.0 scale) 20.0)]
      (is (= expected (camera/transform-position world zoom pan))))))

(deftest center-pan-test
  (testing "calculates pan to center coordinate"
    (let [world 100.0
          zoom 1.0
          screen-span 800.0
          ;; center = 400
          ;; scale = 1^2.5 = 1
          ;; pan = 400 - (100 * 1) = 300
          pan (camera/center-pan world zoom screen-span)]
      (is (= 300.0 pan))
      ;; Verify by transforming back
      (is (= 400.0 (camera/transform-position world zoom pan))))))

(deftest zoom-scaling-test
  (testing "zoom scales non-linearly"
    (is (< (camera/zoom->position-scale 1.0) (camera/zoom->position-scale 2.0)))
    (is (= 1.0 (camera/zoom->position-scale 1.0)))))
