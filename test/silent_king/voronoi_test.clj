(ns silent-king.voronoi-test
  (:require [clojure.test :refer [deftest is testing]]
            [silent-king.state :as state]
            [silent-king.voronoi :as voronoi]))

(defn- distance
  [{x1 :x y1 :y} {x2 :x y2 :y}]
  (Math/sqrt (+ (Math/pow (- (double x1) (double x2)) 2.0)
                (Math/pow (- (double y1) (double y2)) 2.0))))

(deftest generate-voronoi-basic
  (let [stars [{:id 1 :x 0.0 :y 0.0}
               {:id 2 :x 120.0 :y 0.0}
               {:id 3 :x 60.0 :y 80.0}]
        {:keys [voronoi-cells elapsed-ms]} (voronoi/generate-voronoi stars)]
    (is (= (set (map :id stars)) (set (keys voronoi-cells))))
    (is (<= 0 elapsed-ms))
    (doseq [[id cell] voronoi-cells]
      (is (= id (:star-id cell)))
      (is (seq (:vertices cell)))
      (is (>= (count (:vertices cell)) 2))
      (is (map? (:bbox cell)))
      (is (map? (:centroid cell)))
      (let [star (first (filter #(= (:id %) id) stars))
            centroid (:centroid cell)]
        (is (< (Math/abs (- (double (:x centroid)) (double (:x star)))) 400.0))
        (is (< (Math/abs (- (double (:y centroid)) (double (:y star)))) 400.0))))))

(deftest generate-voronoi-insufficient-stars
  (let [{:keys [voronoi-cells]} (voronoi/generate-voronoi [{:id 1 :x 0.0 :y 0.0}])]
    (is (empty? voronoi-cells))))

(deftest voronoi-settings-defaults
  (let [game-state (atom (state/create-game-state))]
    (is (= state/default-voronoi-settings (state/voronoi-settings game-state)))
    (is (true? (state/voronoi-enabled? game-state)))
    (state/set-voronoi-setting! game-state :enabled? true)
    (is (true? (state/voronoi-enabled? game-state)))
    (state/reset-voronoi-settings! game-state)
    (is (= state/default-voronoi-settings (:voronoi-settings @game-state)))))

(deftest voronoi-cells-flag-envelope
  (let [stars [{:id 1 :x 0.0 :y 0.0}
               {:id 2 :x 200.0 :y 0.0}
               {:id 3 :x 100.0 :y 150.0}]
        {:keys [voronoi-cells]} (voronoi/generate-voronoi stars)]
    (is (seq voronoi-cells))
    (doseq [[_ cell] voronoi-cells]
      (is (contains? cell :on-envelope?)))))

(deftest relax-sites-once-moves-toward-centroid
  (let [stars [{:id 1 :x 0.0 :y 0.0}
               {:id 2 :x 150.0 :y 20.0}
               {:id 3 :x 70.0 :y 140.0}]
        {:keys [stars-relaxed voronoi-cells stats]} (voronoi/relax-sites-once stars {:step-factor 1.0})
        by-id (into {} (map (juxt :id identity) stars))
        relaxed-by-id (into {} (map (juxt :id identity) stars-relaxed))]
    (is (= (set (keys by-id)) (set (keys relaxed-by-id))))
    (doseq [[sid relaxed] relaxed-by-id]
      (let [original (get by-id sid)
            centroid (get-in voronoi-cells [sid :centroid])]
        (is centroid)
        (is (< (distance relaxed centroid) (distance original centroid)))))
    (is (pos? (:max-displacement stats)))))

(deftest relax-sites-once-respects-max-displacement
  (let [stars [{:id 1 :x -800.0 :y -800.0}
               {:id 2 :x 800.0 :y -800.0}
               {:id 3 :x -800.0 :y 800.0}
               {:id 4 :x 800.0 :y 800.0}
               {:id 5 :x 0.0 :y 0.0}]
        unclamped (voronoi/relax-sites-once stars {:step-factor 1.0})
        unclamped-max (get-in unclamped [:stats :max-displacement])
        max-d 25.0
        clamped (voronoi/relax-sites-once stars {:step-factor 1.0
                                                 :max-displacement max-d})
        clamped-max (get-in clamped [:stats :max-displacement])
        moves (map (fn [[before after]] (distance before after))
                   (map vector stars (:stars-relaxed clamped)))]
    (is (<= clamped-max (+ max-d 1.0e-6)))
    (is (every? #(<= % (+ max-d 1.0e-6)) moves))
    (when (> unclamped-max max-d)
      (is (< clamped-max unclamped-max)))))

(deftest generate-relaxed-voronoi-zero-iteration-matches-base
  (let [stars [{:id 10 :x -50.0 :y -20.0}
               {:id 11 :x 60.0 :y -10.0}
               {:id 12 :x 10.0 :y 90.0}
               {:id 13 :x -40.0 :y 120.0}]
        base (voronoi/generate-voronoi stars)
        relaxed (voronoi/generate-relaxed-voronoi stars {:iterations 0})
        relaxed-nil (voronoi/generate-relaxed-voronoi stars nil)]
    (is (= (:voronoi-cells base) (:voronoi-cells relaxed)))
    (is (= (:voronoi-cells base) (:voronoi-cells relaxed-nil)))
    (is (= 0 (get-in relaxed [:relax-meta :iterations-used])))
    (is (= 0 (get-in relaxed-nil [:relax-meta :iterations-used])))))

(deftest plan-voronoi-cells-test
  (let [game-state (atom (state/create-game-state))
        star1 {:id 1 :x 0.0 :y 0.0}
        star2 {:id 2 :x 100.0 :y 0.0}
        star3 {:id 3 :x 50.0 :y 86.6}] ;; Equilateral triangle
    (state/add-star! game-state star1)
    (state/add-star! game-state star2)
    (state/add-star! game-state star3)
    (voronoi/generate-voronoi! game-state)

    (testing "generates polygon commands"
      (let [width 800
            height 600
            zoom 1.0
            pan-x 0.0
            pan-y 0.0
            time 0.0
            plan (voronoi/plan-voronoi-cells width height zoom pan-x pan-y game-state time)
            commands (:commands plan)]
        (is (seq commands))
        (let [fills (filter #(= :polygon-fill (:op %)) commands)
              strokes (filter #(= :polygon-stroke (:op %)) commands)]
          (is (seq fills))
          (is (seq strokes))
          (is (= (count fills) (count strokes))))))

    (testing "generates centroids when enabled"
      (state/set-voronoi-setting! game-state :show-centroids? true)
      (let [width 800
            height 600
            zoom 1.0
            pan-x 0.0
            pan-y 0.0
            time 0.0
            plan (voronoi/plan-voronoi-cells width height zoom pan-x pan-y game-state time)
            commands (:commands plan)
            circles (filter #(= :circle (:op %)) commands)]
        (is (seq circles))))))
