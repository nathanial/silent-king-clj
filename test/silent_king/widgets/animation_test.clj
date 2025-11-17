(ns silent-king.widgets.animation-test
  (:require [clojure.test :refer :all]
            [silent-king.widgets.animation :as wanim]))

(deftest test-easing-functions
  (testing "linear easing"
    (is (= 0.0 (wanim/linear 0.0)))
    (is (= 0.5 (wanim/linear 0.5)))
    (is (= 1.0 (wanim/linear 1.0))))

  (testing "ease-in at boundaries"
    (is (= 0.0 (wanim/ease-in 0.0)))
    (is (= 1.0 (wanim/ease-in 1.0))))

  (testing "ease-in is slower at start"
    ;; At t=0.5, cubic ease-in should be 0.125 (0.5^3)
    (is (< (wanim/ease-in 0.5) 0.5)))

  (testing "ease-out at boundaries"
    (is (= 0.0 (wanim/ease-out 0.0)))
    (is (= 1.0 (wanim/ease-out 1.0))))

  (testing "ease-out is faster at start"
    ;; At t=0.5, cubic ease-out should be 0.875
    (is (> (wanim/ease-out 0.5) 0.5)))

  (testing "ease-in-out at boundaries"
    (is (= 0.0 (wanim/ease-in-out 0.0)))
    (is (= 1.0 (wanim/ease-in-out 1.0))))

  (testing "ease-in-out is slower at start and end"
    ;; At t=0.25, should be slower than linear
    (is (< (wanim/ease-in-out 0.25) 0.25))
    ;; At t=0.75, should be slower than linear
    (is (< (- 1.0 (wanim/ease-in-out 0.75)) (- 1.0 0.75))))

  (testing "ease-in-out is symmetric"
    (is (< (Math/abs (- (wanim/ease-in-out 0.3)
                       (- 1.0 (wanim/ease-in-out 0.7))))
           0.001))))

(deftest test-interpolate
  (testing "interpolate at boundaries"
    (is (= 100.0 (wanim/interpolate 100.0 200.0 0.0)))
    (is (= 200.0 (wanim/interpolate 100.0 200.0 1.0))))

  (testing "interpolate at midpoint"
    (is (= 150.0 (wanim/interpolate 100.0 200.0 0.5))))

  (testing "interpolate with negative values"
    (is (= -50.0 (wanim/interpolate -100.0 0.0 0.5))))

  (testing "interpolate with fractional progress"
    (is (= 125.0 (wanim/interpolate 100.0 200.0 0.25)))
    (is (= 175.0 (wanim/interpolate 100.0 200.0 0.75)))))

(deftest test-start-camera-pan
  (testing "start camera pan creates animation state"
    (let [game-state (atom {:camera {:pan-x 0.0 :pan-y 0.0}
                            :time {:current-time 10.0}})
          _ (wanim/start-camera-pan! game-state 100.0 200.0 0.5)
          anim (:camera-animation @game-state)]
      (is (true? (:active anim)))
      (is (= 10.0 (:start-time anim)))
      (is (= 0.5 (:duration anim)))
      (is (= 0.0 (:from-pan-x anim)))
      (is (= 0.0 (:from-pan-y anim)))
      (is (= 100.0 (:to-pan-x anim)))
      (is (= 200.0 (:to-pan-y anim)))))

  (testing "start camera pan with custom easing"
    (let [game-state (atom {:camera {:pan-x 50.0 :pan-y 75.0}
                            :time {:current-time 20.0}})
          custom-easing :ease-out
          _ (wanim/start-camera-pan! game-state 150.0 250.0 1.0 custom-easing)
          anim (:camera-animation @game-state)]
      (is (= custom-easing (:easing anim)))
      (is (= 50.0 (:from-pan-x anim)))
      (is (= 75.0 (:from-pan-y anim))))))

(deftest test-update-camera-animation
  (testing "update camera animation when not active"
    (let [game-state (atom {:camera {:pan-x 0.0 :pan-y 0.0}
                            :time {:current-time 10.0}
                            :camera-animation {:active false}})
          _ (wanim/update-camera-animation! game-state)
          camera (:camera @game-state)]
      (is (= 0.0 (:pan-x camera)))
      (is (= 0.0 (:pan-y camera)))))

  (testing "update camera animation at start"
    (let [game-state (atom {:camera {:pan-x 0.0 :pan-y 0.0}
                            :time {:current-time 10.0}})
          _ (wanim/start-camera-pan! game-state 100.0 200.0 1.0 :linear)
          _ (wanim/update-camera-animation! game-state)
          camera (:camera @game-state)]
      ;; At t=0, should be at starting position
      (is (= 0.0 (:pan-x camera)))
      (is (= 0.0 (:pan-y camera)))))

  (testing "update camera animation at midpoint"
    (let [game-state (atom {:camera {:pan-x 0.0 :pan-y 0.0}
                            :time {:current-time 10.0}})
          _ (wanim/start-camera-pan! game-state 100.0 200.0 1.0 :linear)
          ;; Advance time to midpoint (10.0 + 0.5 = 10.5)
          _ (swap! game-state assoc-in [:time :current-time] 10.5)
          _ (wanim/update-camera-animation! game-state)
          camera (:camera @game-state)]
      ;; At t=0.5 with linear easing, should be at midpoint
      (is (= 50.0 (:pan-x camera)))
      (is (= 100.0 (:pan-y camera)))))

  (testing "update camera animation at completion"
    (let [game-state (atom {:camera {:pan-x 0.0 :pan-y 0.0}
                            :time {:current-time 10.0}})
          _ (wanim/start-camera-pan! game-state 100.0 200.0 1.0 :linear)
          ;; Advance time past completion (10.0 + 1.0 = 11.0)
          _ (swap! game-state assoc-in [:time :current-time] 11.0)
          _ (wanim/update-camera-animation! game-state)
          camera (:camera @game-state)
          anim (:camera-animation @game-state)]
      ;; Should be at target position
      (is (= 100.0 (:pan-x camera)))
      (is (= 200.0 (:pan-y camera)))
      ;; Animation should be inactive
      (is (false? (:active anim)))))

  (testing "update camera animation beyond completion"
    (let [game-state (atom {:camera {:pan-x 0.0 :pan-y 0.0}
                            :time {:current-time 10.0}})
          _ (wanim/start-camera-pan! game-state 100.0 200.0 1.0 :linear)
          ;; Advance time far beyond completion (10.0 + 3.0 = 13.0)
          _ (swap! game-state assoc-in [:time :current-time] 13.0)
          _ (wanim/update-camera-animation! game-state)
          camera (:camera @game-state)]
      ;; Should still be at target position (not beyond)
      (is (= 100.0 (:pan-x camera)))
      (is (= 200.0 (:pan-y camera))))))
