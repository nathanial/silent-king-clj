(ns silent-king.color-test
  (:require [clojure.test :refer :all]
            [silent-king.color :as color]))

(deftest ensure-handles-vectors
  (testing "Regression: ensure should handle normalized RGBA vectors"
    (let [input [0.2 0.6 1.0 0.3]
          result (color/ensure input)]
      (is (= :rgb (:type result)))
      (is (= 51 (:r result)))   ;; 0.2 * 255
      (is (= 153 (:g result)))  ;; 0.6 * 255
      (is (= 255 (:b result)))  ;; 1.0 * 255
      (is (= 76 (:a result)))))) ;; 0.3 * 255
