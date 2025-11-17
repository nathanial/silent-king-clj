(ns silent-king.widgets.layout-test
  (:require [clojure.test :refer :all]
            [silent-king.widgets.layout :as wlayout]))

(deftest test-apply-anchor-position
  (testing "top-left anchor with no margin"
    (let [bounds {:x 0 :y 0 :width 100 :height 50}
          layout {:anchor :top-left}
          result (wlayout/apply-anchor-position bounds layout 1280 800)]
      (is (= 0 (:x result)))
      (is (= 0 (:y result)))
      (is (= 100 (:width result)))
      (is (= 50 (:height result)))))

  (testing "top-left anchor with margin"
    (let [bounds {:x 0 :y 0 :width 100 :height 50}
          layout {:anchor :top-left :margin {:all 20}}
          result (wlayout/apply-anchor-position bounds layout 1280 800)]
      (is (= 20 (:x result)))
      (is (= 20 (:y result)))
      (is (= 100 (:width result)))
      (is (= 50 (:height result)))))

  (testing "top-right anchor with no margin"
    (let [bounds {:x 0 :y 0 :width 100 :height 50}
          layout {:anchor :top-right}
          result (wlayout/apply-anchor-position bounds layout 1280 800)]
      (is (= 1180 (:x result)))  ; 1280 - 100
      (is (= 0 (:y result)))
      (is (= 100 (:width result)))
      (is (= 50 (:height result)))))

  (testing "top-right anchor with margin"
    (let [bounds {:x 0 :y 0 :width 100 :height 50}
          layout {:anchor :top-right :margin {:all 20}}
          result (wlayout/apply-anchor-position bounds layout 1280 800)]
      (is (= 1160 (:x result)))  ; 1280 - 100 - 20
      (is (= 20 (:y result)))
      (is (= 100 (:width result)))
      (is (= 50 (:height result)))))

  (testing "bottom-left anchor with no margin"
    (let [bounds {:x 0 :y 0 :width 100 :height 50}
          layout {:anchor :bottom-left}
          result (wlayout/apply-anchor-position bounds layout 1280 800)]
      (is (= 0 (:x result)))
      (is (= 750 (:y result)))  ; 800 - 50
      (is (= 100 (:width result)))
      (is (= 50 (:height result)))))

  (testing "bottom-left anchor with margin"
    (let [bounds {:x 0 :y 0 :width 100 :height 50}
          layout {:anchor :bottom-left :margin {:all 20}}
          result (wlayout/apply-anchor-position bounds layout 1280 800)]
      (is (= 20 (:x result)))
      (is (= 730 (:y result)))  ; 800 - 50 - 20
      (is (= 100 (:width result)))
      (is (= 50 (:height result)))))

  (testing "bottom-right anchor with no margin"
    (let [bounds {:x 0 :y 0 :width 100 :height 50}
          layout {:anchor :bottom-right}
          result (wlayout/apply-anchor-position bounds layout 1280 800)]
      (is (= 1180 (:x result)))  ; 1280 - 100
      (is (= 750 (:y result)))   ; 800 - 50
      (is (= 100 (:width result)))
      (is (= 50 (:height result)))))

  (testing "bottom-right anchor with margin"
    (let [bounds {:x 0 :y 0 :width 100 :height 50}
          layout {:anchor :bottom-right :margin {:all 20}}
          result (wlayout/apply-anchor-position bounds layout 1280 800)]
      (is (= 1160 (:x result)))  ; 1280 - 100 - 20
      (is (= 730 (:y result)))   ; 800 - 50 - 20
      (is (= 100 (:width result)))
      (is (= 50 (:height result)))))

  (testing "center anchor with no margin"
    (let [bounds {:x 0 :y 0 :width 100 :height 50}
          layout {:anchor :center}
          result (wlayout/apply-anchor-position bounds layout 1280 800)]
      (is (= 590 (:x result)))   ; (1280 - 100) / 2
      (is (= 375 (:y result)))   ; (800 - 50) / 2
      (is (= 100 (:width result)))
      (is (= 50 (:height result)))))

  (testing "center anchor with margin (margin ignored for center)"
    (let [bounds {:x 0 :y 0 :width 100 :height 50}
          layout {:anchor :center :margin {:all 20}}
          result (wlayout/apply-anchor-position bounds layout 1280 800)]
      (is (= 590 (:x result)))   ; (1280 - 100) / 2
      (is (= 375 (:y result)))   ; (800 - 50) / 2
      (is (= 100 (:width result)))
      (is (= 50 (:height result)))))

  (testing "individual margin values"
    (let [bounds {:x 0 :y 0 :width 100 :height 50}
          layout {:anchor :bottom-right :margin {:top 10 :right 20 :bottom 30 :left 40}}
          result (wlayout/apply-anchor-position bounds layout 1280 800)]
      (is (= 1160 (:x result)))  ; 1280 - 100 - 20 (uses right margin)
      (is (= 720 (:y result)))   ; 800 - 50 - 30 (uses bottom margin)
      (is (= 100 (:width result)))
      (is (= 50 (:height result)))))

  (testing "default anchor is top-left"
    (let [bounds {:x 0 :y 0 :width 100 :height 50}
          layout {}
          result (wlayout/apply-anchor-position bounds layout 1280 800)]
      (is (= 0 (:x result)))
      (is (= 0 (:y result)))
      (is (= 100 (:width result)))
      (is (= 50 (:height result))))))
