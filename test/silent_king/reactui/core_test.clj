(ns silent-king.reactui.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [silent-king.reactui.core :as reactui]
            [silent-king.reactui.layout :as layout]))

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
