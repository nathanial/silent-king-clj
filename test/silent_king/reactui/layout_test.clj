(ns silent-king.reactui.layout-test
  (:require [clojure.test :refer :all]
            [silent-king.reactui.layout :as layout]))

(deftest calculate-dock-layout-test
  (testing "All docks empty -> Center takes full viewport"
    (let [viewport {:x 0 :y 0 :width 1000 :height 800}
          docking {:left {:windows [] :size 200}
                   :right {:windows [] :size 200}
                   :top {:windows [] :size 100}
                   :bottom {:windows [] :size 100}}
          result (layout/calculate-dock-layout viewport docking)]
      (is (= 1000.0 (get-in result [:center :width])))
      (is (= 800.0 (get-in result [:center :height])))
      (is (= 0.0 (get-in result [:left :width])))
      (is (= 0.0 (get-in result [:top :height])))))

  (testing "Left active -> Center width reduces"
    (let [viewport {:x 0 :y 0 :width 1000 :height 800}
          docking {:left {:windows [:w1] :size 200}
                   :right {:windows [] :size 200}
                   :top {:windows [] :size 100}
                   :bottom {:windows [] :size 100}}
          result (layout/calculate-dock-layout viewport docking)]
      (is (= 200.0 (get-in result [:left :width])))
      (is (= 800.0 (get-in result [:left :height])))
      (is (= 800.0 (get-in result [:center :width]))) ;; 1000 - 200
      (is (= 200.0 (get-in result [:center :x])))))

  (testing "Top and Bottom active -> Center height reduces"
    (let [viewport {:x 0 :y 0 :width 1000 :height 800}
          docking {:left {:windows [] :size 200}
                   :right {:windows [] :size 200}
                   :top {:windows [:w1] :size 100}
                   :bottom {:windows [:w2] :size 150}}
          result (layout/calculate-dock-layout viewport docking)]
      (is (= 100.0 (get-in result [:top :height])))
      (is (= 150.0 (get-in result [:bottom :height])))
      (is (= 550.0 (get-in result [:center :height]))) ;; 800 - 100 - 150
      (is (= 100.0 (get-in result [:center :y])))))

  (testing "All sides active -> Center squeezed"
    (let [viewport {:x 0 :y 0 :width 1000 :height 800}
          docking {:left {:windows [:w1] :size 200}
                   :right {:windows [:w2] :size 200}
                   :top {:windows [:w3] :size 100}
                   :bottom {:windows [:w4] :size 100}}
          result (layout/calculate-dock-layout viewport docking)]
      ;; Top/Bottom full width
      (is (= 1000.0 (get-in result [:top :width])))
      (is (= 1000.0 (get-in result [:bottom :width])))
      
      ;; Left/Right height = Viewport - Top - Bottom
      (is (= 600.0 (get-in result [:left :height]))) ;; 800 - 100 - 100
      
      ;; Center dimensions
      (is (= 600.0 (get-in result [:center :width]))) ;; 1000 - 200 - 200
      (is (= 600.0 (get-in result [:center :height])))))

  (testing "Center calculation with offsets"
    (let [viewport {:x 50 :y 50 :width 1000 :height 800}
          docking {:left {:windows [:w1] :size 200}
                   :right {:windows [] :size 200}
                   :top {:windows [] :size 100}
                   :bottom {:windows [] :size 100}}
          result (layout/calculate-dock-layout viewport docking)]
      (is (= 50.0 (get-in result [:left :x])))
      (is (= 250.0 (get-in result [:center :x]))))))