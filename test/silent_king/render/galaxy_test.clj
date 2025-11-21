(ns silent-king.render.galaxy-test
  (:require [clojure.test :refer [deftest is testing]]
            [silent-king.render.galaxy :as galaxy]
            [silent-king.render.commands :as commands]))

(deftest plan-star-from-atlas-test
  (testing "generates correct image-rect command"
    (let [atlas-image :mock-atlas-image ;; Mock image as keyword
          metadata {"star1.png" {:x 0 :y 0 :size 64}}
          path "star1.png"
          screen-x 100.0
          screen-y 200.0
          size 32.0
          angle 45.0
          atlas-size 1024
          commands (galaxy/plan-star-from-atlas atlas-image metadata path screen-x screen-y size angle atlas-size)]
      (is (= 1 (count commands)))
      (let [cmd (first commands)]
        (is (= :image-rect (:op cmd)))
        (is (= atlas-image (:image cmd)))
        (is (= {:x 0 :y 0 :width 64 :height 64} (:src cmd)))
        ;; Destination should be centered on screen-x/y
        ;; Scale = 32/64 = 0.5. Scaled size = 64 * 0.5 = 32.
        ;; x = 100 - 16 = 84, y = 200 - 16 = 184.
        (is (= {:x 84.0 :y 184.0 :width 32.0 :height 32.0} (:dst cmd)))
        (is (= {:rotation-deg 45.0 :anchor :center} (:transform cmd)))))))

(deftest plan-selection-highlight-test
  (testing "generates glow and ring commands"
    (let [screen-x 100.0
          screen-y 100.0
          screen-size 20.0
          time 0.0
          commands (galaxy/plan-selection-highlight screen-x screen-y screen-size time)]
      (is (= 2 (count commands)))
      (let [glow (first commands)
            ring (second commands)]
        (is (= :circle (:op glow)))
        (is (= {:x 100.0 :y 100.0} (:center glow)))
        (is (:fill-color (:style glow)))
        
        (is (= :circle (:op ring)))
        (is (= {:x 100.0 :y 100.0} (:center ring)))
        (is (:stroke-color (:style ring)))))))

(deftest plan-orbit-ring-test
  (testing "generates orbit ring command"
    (let [star-x 200.0
          star-y 200.0
          radius 50.0
          commands (galaxy/plan-orbit-ring star-x star-y radius)]
      (is (= 1 (count commands)))
      (let [cmd (first commands)]
        (is (= :circle (:op cmd)))
        (is (= {:x 200.0 :y 200.0} (:center cmd)))
        (is (= 50.0 (:radius cmd)))
        (is (:stroke-color (:style cmd)))
        (is (= 1.2 (:stroke-width (:style cmd))))))))
