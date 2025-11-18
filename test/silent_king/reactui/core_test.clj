(ns silent-king.reactui.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [silent-king.reactui.core :as reactui]
            [silent-king.reactui.layout :as layout]
            [silent-king.state :as state]))

(deftest render-ui-tree-smoke
  (let [tree [:vstack {:bounds {:x 5 :y 5 :width 180}
                       :padding {:all 6}
                       :gap 2}
              [:label {:text "Demo"}]
              [:label {:text "Pipeline"}]]
        result (reactui/render-ui-tree
                {:canvas nil
                 :tree tree
                 :viewport {:x 0 :y 0 :width 200 :height 200}})
        root-bounds (layout/bounds result)]
    (testing "layout succeeds"
      (is (= :vstack (:type result)))
      (is (= 2 (count (:children result))))
      (is (map? root-bounds))
      (is (= 5.0 (:x root-bounds)))
      (is (= 180.0 (:width root-bounds))))))

(defn- find-node
  [node type]
  (when node
    (if (= (:type node) type)
      node
      (some #(find-node % type) (:children node)))))

(deftest slider-pointer-capture-updates-zoom
  (reactui/release-capture!)
  (let [game-state (atom (state/create-game-state))
        tree [:slider {:bounds {:x 0 :y 0 :width 200 :height 32}
                       :min 0.4
                       :max 4.0
                       :step 0.1
                       :value 0.4
                       :on-change [:ui/set-zoom]}]
        layout-tree (reactui/render-ui-tree {:canvas nil
                                             :tree tree
                                             :viewport {:x 0 :y 0 :width 200 :height 40}})
        slider-node (find-node layout-tree :slider)
        track (get-in slider-node [:layout :slider :track])
        down-x (+ (:x track) 2.0)
        down-y (+ (:y track) (/ (:height track) 2.0))
        drag-x (+ (:x track) (:width track))]
    (is (true? (reactui/handle-pointer-down! game-state down-x down-y)))
    (is (= 0.4 (get-in @game-state [:camera :zoom])))
    (is (true? (reactui/handle-pointer-drag! game-state drag-x down-y)))
    (reactui/handle-pointer-up! game-state drag-x down-y)
    (is (= 4.0 (get-in @game-state [:camera :zoom])))))
