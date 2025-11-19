(ns silent-king.reactui.layout-test
  (:require [clojure.test :refer [deftest is testing]]
            [silent-king.reactui.core :as reactui]
            [silent-king.reactui.layout :as layout]
            [silent-king.reactui.primitives]))

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

(deftest hstack-lays-out-children-horizontally
  (let [tree (reactui/normalize-tree
              [:hstack {:bounds {:x 0 :y 0 :width 240}
                        :padding {:left 10 :right 10}
                        :gap 6}
               [:label {:text "Label"
                        :bounds {:width 60}}]
               [:button {:label "Action"
                         :bounds {:width 80 :height 32}}]])
        laid-out (layout/compute-layout tree {:x 0 :y 0 :width 300 :height 200})
        first-child (layout/bounds (first (:children laid-out)))
        second-child (layout/bounds (second (:children laid-out)))]
    (is (= 10.0 (:x first-child)))
    (is (= (+ 10.0 60.0 6.0) (:x second-child)))
    (is (= (:y first-child) (:y second-child)))))

(deftest dropdown-layout-adjusts-height
  (let [base-opts [{:value :blue :label "Blue"}
                   {:value :red :label "Red"}
                   {:value :green :label "Green"}]
        collapsed (reactui/normalize-tree
                   [:dropdown {:bounds {:x 0 :y 0 :width 220}
                               :options base-opts
                               :selected :blue}])
        collapsed-layout (layout/compute-layout collapsed {:x 0 :y 0 :width 220 :height 200})
        collapsed-bounds (layout/bounds collapsed-layout)
        header-height (:height (get-in collapsed-layout [:layout :dropdown :header]))]
    (is (= header-height (:height collapsed-bounds)))
    (let [expanded (reactui/normalize-tree
                    [:dropdown {:bounds {:x 0 :y 0 :width 220}
                                :options base-opts
                                :selected :blue
                                :expanded? true}])
          expanded-layout (layout/compute-layout expanded {:x 0 :y 0 :width 220 :height 400})
          expanded-bounds (layout/bounds expanded-layout)
          options (get-in expanded-layout [:layout :dropdown :options])
          header-bounds (get-in expanded-layout [:layout :dropdown :header])]
      (is (= header-height (:height expanded-bounds)))
      (is (= 3 (count options)))
      ;; Ensure options are stacked below header despite overlay rendering
      (let [first-option-bounds (:bounds (first options))]
        (is (> (:y first-option-bounds)
               (+ (:y header-bounds) (:height header-bounds))))))))

(deftest bar-chart-layout-derives-range
  (let [tree (reactui/normalize-tree
              [:bar-chart {:bounds {:x 8 :y 12 :width 300 :height 120}
                           :values [10 20 30]}])
        laid-out (layout/compute-layout tree {:x 0 :y 0 :width 400 :height 300})
        bounds (layout/bounds laid-out)
        chart-data (get-in laid-out [:layout :bar-chart])]
    (is (= 300.0 (:width bounds)))
    (is (= 120.0 (:height bounds)))
    (is (= [10.0 20.0 30.0] (:values chart-data)))
    (is (= 10.0 (:min chart-data)))
    (is (= 30.0 (:max chart-data)))))
