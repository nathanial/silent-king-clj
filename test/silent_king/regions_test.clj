(ns silent-king.regions-test
  (:require [clojure.test :refer [deftest is testing]]
            [silent-king.regions :as regions]
            [silent-king.state :as state]
            [silent-king.color :as color]))

(deftest plan-regions-test
  (let [game-state (atom (state/create-game-state))
        region {:id :region-1
                :name "Test Region"
                :center {:x 100.0 :y 100.0}
                :color (color/hex 0xFF00FF00)
                :sectors {:sector-1 {:name "Sector Alpha"
                                     :center {:x 120.0 :y 120.0}}}}]
    (state/set-regions! game-state {:region-1 region})

    (testing "generates text commands for region name"
      (let [zoom 1.0
            pan-x 0.0
            pan-y 0.0
            plan (regions/plan-regions zoom pan-x pan-y game-state)
            commands (:commands plan)]
        ;; Should have at least 2 commands: shadow text and main text for region
        (is (>= (count commands) 2))
        (let [shadow (first commands)
              main (second commands)]
          (is (= :text (:op shadow)))
          (is (= "Test Region" (:text shadow)))
          (is (= (color/hex 0x80000000) (:color shadow))) ;; Shadow color

          (is (= :text (:op main)))
          (is (= "Test Region" (:text main)))
          (is (= (color/hex 0xFF00FF00) (:color main))))))

    (testing "generates sector labels at high zoom"
      (let [zoom 2.0 ;; High zoom
            pan-x 0.0
            pan-y 0.0
            plan (regions/plan-regions zoom pan-x pan-y game-state)
            commands (:commands plan)]
        ;; Region shadow + main, Sector shadow + main = 4 commands
        (is (= 4 (count commands)))
        (let [sector-cmds (drop 2 commands)
              shadow (first sector-cmds)
              main (second sector-cmds)]
          (is (= :text (:op shadow)))
          (is (= "Sector Alpha" (:text shadow)))

          (is (= :text (:op main)))
          (is (= "Sector Alpha" (:text main))))))

    (testing "hides sector labels at low zoom"
      (let [zoom 0.5 ;; Low zoom
            pan-x 0.0
            pan-y 0.0
            plan (regions/plan-regions zoom pan-x pan-y game-state)
            commands (:commands plan)]
        ;; Only region labels
        (is (= 2 (count commands)))))))
