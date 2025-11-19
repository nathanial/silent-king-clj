(ns silent-king.reactui.interaction-test
  (:require [clojure.test :refer [deftest is testing]]
            [silent-king.reactui.core :as reactui]
            [silent-king.reactui.interaction :as interaction]
            [silent-king.reactui.layout :as layout]
            [silent-king.reactui.primitives]))

(defn- layout-tree
  [tree]
  (-> tree
      reactui/normalize-tree
      (layout/compute-layout {:x 0 :y 0 :width 400 :height 400})))

(deftest button-click-emits-event
  (let [tree (layout-tree [:button {:bounds {:x 10 :y 10 :width 120 :height 40}
                                    :on-click [:ui/toggle-hyperlanes]
                                    :label "Toggle"}])
        events (interaction/click->events tree 20 20)]
    (is (= [[:ui/toggle-hyperlanes]] events))))

(deftest slider-click-calculates-value
  (let [tree (layout-tree [:slider {:bounds {:x 0 :y 0 :width 200 :height 24}
                                    :min 0.4
                                    :max 2.0
                                    :step 0.1
                                    :value 0.4
                                    :on-change [:ui/set-zoom]}])
        track (get-in tree [:layout :slider :track])
        mid-x (+ (:x track) (/ (:width track) 2.0))
        events (interaction/click->events tree mid-x (+ (:y track) 2))]
    (is (< (Math/abs (- 1.2 (double (-> events first second)))) 1e-6))
    ;; Clicking beyond the right edge clamps to max
    (let [edge-x (min (+ (:x track) (:width track) 1.0)
                      (+ (:x (layout/bounds tree)) (:width (layout/bounds tree))))
          clamped (interaction/click->events tree edge-x (:y track))]
      (is (= 2.0 (-> clamped first second))))))

(deftest dropdown-click-emits-events
  (let [collapsed (layout-tree [:dropdown {:bounds {:x 0 :y 0 :width 200}
                                           :id :colors
                                           :options [{:value :blue :label "Blue"}
                                                     {:value :red :label "Red"}]
                                           :selected :blue
                                           :on-toggle [:ui.dropdown/toggle :colors]
                                           :on-select [:hyperlanes/set-color-scheme]
                                           :on-close [:ui.dropdown/close :colors]}])
        header (get-in collapsed [:layout :dropdown :header])
        header-events (interaction/click->events collapsed
                                                 (+ (:x header) 4)
                                                 (+ (:y header) 4))]
    (is (= [[:ui.dropdown/toggle :colors]] header-events))
    (let [expanded (layout-tree [:dropdown {:bounds {:x 0 :y 0 :width 200}
                                            :id :colors
                                            :options [{:value :blue :label "Blue"}
                                                      {:value :red :label "Red"}]
                                            :selected :blue
                                            :expanded? true
                                            :on-toggle [:ui.dropdown/toggle :colors]
                                            :on-select [:hyperlanes/set-color-scheme]
                                            :on-close [:ui.dropdown/close :colors]}])
          red-option (some #(when (= :red (:value %)) %)
                           (get-in expanded [:layout :dropdown :options]))
          red-bounds (:bounds red-option)
          option-events (interaction/click->events expanded
                                                   (+ (:x red-bounds) 4)
                                                   (+ (:y red-bounds) 4))]
      (is (= [[:hyperlanes/set-color-scheme :red]
              [:ui.dropdown/close :colors]]
             option-events)))))
