(ns silent-king.hyperlanes-test
  (:require [clojure.test :refer [deftest is testing]]
            [silent-king.color :as color]
            [silent-king.hyperlanes :as hyperlanes]
            [silent-king.state :as state]))

(deftest plan-all-hyperlanes-test
  (let [game-state (atom (state/create-game-state))
        star1 {:id 1 :x 0.0 :y 0.0}
        star2 {:id 2 :x 100.0 :y 0.0}
        hyperlane {:id 1
                   :from-id 1
                   :to-id 2
                   :base-width 2.0
                   :color-start (color/hsv 220 60 100)
                   :color-end (color/hsv 220 75 80)
                   :glow-color (color/hsv 220 60 100 0.25)
                   :animation-offset 0.0}]

    (state/add-star! game-state star1)
    (state/add-star! game-state star2)
    (state/add-hyperlane! game-state hyperlane)

    (testing "generates line command for visible hyperlane"
      (let [width 800
            height 600
            zoom 1.0
            pan-x 0.0
            pan-y 0.0
            time 0.0
            plan (hyperlanes/plan-all-hyperlanes width height zoom pan-x pan-y game-state time)
            commands (:commands plan)]
        (is (= 1 (count commands)))
        (let [cmd (first commands)]
          (is (= :line (:op cmd)))
          (is (= {:x 0.0 :y 0.0} (:from cmd)))
          (is (= {:x 100.0 :y 0.0} (:to cmd)))
          (is (:stroke-color (:style cmd))))))

    (testing "culls off-screen hyperlane"
      (let [width 800
            height 600
            zoom 1.0
            pan-x -2000.0 ;; Panned far away
            pan-y 0.0
            time 0.0
            plan (hyperlanes/plan-all-hyperlanes width height zoom pan-x pan-y game-state time)]
        (is (empty? (:commands plan)))))

    (testing "LOD: far zoom uses simple lines"
      (let [width 800
            height 600
            zoom 0.5 ;; Far zoom
            pan-x 0.0
            pan-y 0.0
            time 0.0
            plan (hyperlanes/plan-all-hyperlanes width height zoom pan-x pan-y game-state time)
            cmd (first (:commands plan))]
        (is (= :line (:op cmd)))
        ;; Far LOD doesn't use gradient/glow
        (is (nil? (:gradient (:style cmd))))
        (is (nil? (:glow (:style cmd))))))

    (testing "LOD: close zoom uses gradient and glow"
      (let [width 800
            height 600
            zoom 3.0 ;; Close zoom
            pan-x 0.0
            pan-y 0.0
            time 0.0
            plan (hyperlanes/plan-all-hyperlanes width height zoom pan-x pan-y game-state time)
            cmd (first (:commands plan))]
        (is (= :line (:op cmd)))
        (is (:gradient (:style cmd)))
        (is (:glow (:style cmd)))))))
