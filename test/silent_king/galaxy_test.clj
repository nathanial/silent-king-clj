(ns silent-king.galaxy-test
  (:require [clojure.test :refer [deftest is testing]]
            [silent-king.galaxy :as galaxy]))

(deftest generate-galaxy-test
  (let [star-images [{:path :mock-star-1} {:path :mock-star-2}]
        planet-sprites [:mock-planet-1]
        num-stars 100
        result (galaxy/generate-galaxy star-images planet-sprites num-stars)]
    
    (testing "generates correct number of stars"
      (is (= num-stars (count (:stars result)))))
    
    (testing "stars have valid properties"
      (let [star (first (vals (:stars result)))
            valid-paths (set (map :path star-images))]
        (is (number? (:x star)))
        (is (number? (:y star)))
        (is (<= 0.0 (:density star) 1.0))
        (is (contains? valid-paths (:sprite-path star)))))
    
    (testing "generates planets"
      (is (seq (:planets result)))
      (let [planet (first (vals (:planets result)))]
        (is (number? (:radius planet)))
        (is (number? (:orbital-period planet)))
        (is (contains? (set planet-sprites) (:sprite-path planet)))))))

(deftest planet-position-test
  (testing "calculates orbital position"
    (let [planet {:radius 100.0
                  :orbital-period 10.0
                  :phase 0.0}
          star {:x 0.0 :y 0.0}
          t 0.0
          pos-0 (galaxy/planet-position planet star t)
          
          t-half 5.0 ;; Half period = pi radians = 180 degrees
          pos-half (galaxy/planet-position planet star t-half)]
      
      ;; At t=0, angle=0, x=100, y=0
      (is (= 100.0 (:x pos-0)))
      (is (= 0.0 (:y pos-0)))
      
      ;; At t=5 (half period), angle=pi, x=-100, y=0 (approx)
      (is (< (Math/abs (- -100.0 (:x pos-half))) 0.001))
      (is (< (Math/abs (:y pos-half)) 0.001)))))
