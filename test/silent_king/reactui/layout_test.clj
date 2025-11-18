(ns silent-king.reactui.layout-test
  (:require [clojure.test :refer [deftest is testing]]
            [silent-king.reactui.core :as reactui]
            [silent-king.reactui.layout :as layout]))

(deftest vstack-computes-child-bounds
  (let [tree (reactui/normalize-tree
              [:vstack {:bounds {:x 10 :y 20 :width 200}
                        :padding {:left 8 :right 8 :top 10 :bottom 6}
                        :gap 4}
               [:label {:text "Alpha"}]
               [:label {:text "Beta"}]])
        laid-out (layout/compute-layout tree {:x 0 :y 0 :width 400 :height 300})
        root-bounds (layout/bounds laid-out)
        first-child (layout/bounds (first (:children laid-out)))
        second-child (layout/bounds (second (:children laid-out)))]
    (testing "root bounds"
      (is (= 10.0 (:x root-bounds)))
      (is (= 20.0 (:y root-bounds)))
      (is (= 200.0 (:width root-bounds)))
      (is (= 60.0 (:height root-bounds))))
    (testing "first label placement"
      (is (= 18.0 (:x first-child)))
      (is (= 30.0 (:y first-child)))
      (is (= 184.0 (:width first-child)))
      (is (= 20.0 (:height first-child))))
    (testing "second label placement respects gap"
      (is (= 18.0 (:x second-child)))
      (is (= 54.0 (:y second-child)))
      (is (= 184.0 (:width second-child)))
      (is (= 20.0 (:height second-child))))))

(deftest vstack-respects-explicit-height
  (let [tree (reactui/normalize-tree
              [:vstack {:bounds {:x 0 :y 0 :width 120 :height 250}
                        :padding {:all 4}}
               [:label {:text "Solo"}]])
        laid-out (layout/compute-layout tree {:x 0 :y 0 :width 120 :height 120})
        root-bounds (layout/bounds laid-out)
        child-bounds (layout/bounds (first (:children laid-out)))]
    (is (= 250 (:height root-bounds)))
    (is (= 4.0 (:y child-bounds)))
    (is (= 112.0 (:width child-bounds)))))

(deftest slider-layout-clamps-value
  (let [tree (reactui/normalize-tree
              [:slider {:bounds {:x 10 :y 10 :width 200 :height 30}
                        :min 0.0
                        :max 10.0
                        :step 1.0
                        :value 12.0}])
        laid-out (layout/compute-layout tree {:x 0 :y 0 :width 400 :height 200})
        slider-data (get-in laid-out [:layout :slider])]
    (is (= 10.0 (get-in slider-data [:range :max])))
    (is (= 10.0 (:value slider-data)))
    (is (= 176.0 (get-in slider-data [:track :width])))
    (is (= (+ (get-in slider-data [:track :x])
              (get-in slider-data [:track :width]))
           (get-in slider-data [:handle :x])))))
