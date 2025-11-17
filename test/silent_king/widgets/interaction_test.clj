(ns silent-king.widgets.interaction-test
  (:require [clojure.test :refer [deftest is testing]]
            [silent-king.state :as state]
            [silent-king.widgets.config :as wconfig]
            [silent-king.widgets.core :as wcore]
            [silent-king.widgets.interaction :as winteraction]))

(deftest find-widget-prefers-topmost-enabled
  (testing "Hit testing returns the highest z-index enabled widget"
    (state/reset-entity-ids!)
    (let [game-state (atom (state/create-game-state))
          base-bounds {:x 0 :y 0 :width 50 :height 50}
          bottom (wcore/panel :id :bottom :bounds base-bounds :layout {:z-index 1})
          top (wcore/panel :id :top :bounds base-bounds :layout {:z-index 10})
          bottom-id (wcore/add-widget! game-state bottom)
          top-id (wcore/add-widget! game-state top)]
      (is (= top-id (first (winteraction/find-widget-at-point game-state 10 10))))
      (state/update-entity! game-state top-id
                            #(assoc-in % [:components :interaction :enabled] false))
      (is (= bottom-id (first (winteraction/find-widget-at-point game-state 10 10)))))))

(deftest slider-stops-dragging-when-mouse-up-away
  (testing "Slider drag state resets even if mouse-up happens on another widget"
    (state/reset-entity-ids!)
    (let [game-state (atom (state/create-game-state))
          slider (wcore/slider 0.0 10.0 5.0 (fn [_])
                               :id :slider
                               :bounds {:x 0 :y 0 :width 100 :height 20})
          button (wcore/button "Other" (fn [])
                               :id :other-button
                               :bounds {:x 200 :y 200 :width 80 :height 30})
          slider-id (wcore/add-widget! game-state slider)
          button-id (wcore/add-widget! game-state button)]
      (is (true? (winteraction/handle-mouse-click game-state 10 10 true)))
      (is (true? (get-in (state/get-entity game-state slider-id)
                         [:components :interaction :dragging])))
      (is (true? (winteraction/handle-mouse-click game-state 210 210 false)))
      (is (false? (get-in (state/get-entity game-state slider-id)
                          [:components :interaction :dragging])))
      (is (false? (get-in (state/get-entity game-state button-id)
                          [:components :interaction :pressed]))))))

(deftest button-release-outside-clears-state
  (testing "Button press cancels without callback when mouse-up occurs off the button"
    (state/reset-entity-ids!)
    (let [game-state (atom (state/create-game-state))
          clicks (atom 0)
          button (wcore/button "Reset" #(swap! clicks inc)
                               :id :reset-button
                               :bounds {:x 0 :y 0 :width 100 :height 30})
          button-id (wcore/add-widget! game-state button)]
      (is (true? (winteraction/handle-mouse-click game-state 10 10 true)))
      (is (true? (get-in (state/get-entity game-state button-id)
                         [:components :interaction :pressed])))
      (is (true? (winteraction/handle-mouse-click game-state 500 500 false)))
      (is (false? (get-in (state/get-entity game-state button-id)
                          [:components :interaction :pressed])))
      (is (zero? @clicks)))))

(deftest scroll-view-consumes-wheel-events
  (testing "Scroll views update their internal offset when the wheel moves"
    (state/reset-entity-ids!)
    (let [game-state (atom (state/create-game-state))
          scroll (wcore/scroll-view :id :hyperlane-list
                                    :bounds {:x 0 :y 0 :width 200 :height 100})
          scroll-id (wcore/add-widget! game-state scroll)
          screen-x (* 10 wconfig/ui-scale)
          screen-y (* 10 wconfig/ui-scale)]
      (state/update-entity! game-state scroll-id
                            #(assoc-in % [:components :value :items]
                                       (vec (repeat 12 {:primary "Row" :secondary ""}))))
      (is (true? (winteraction/handle-scroll game-state screen-x screen-y -1.0)))
      (is (> (get-in (state/get-entity game-state scroll-id)
                     [:components :value :scroll-offset])
             0.0)))))

(deftest dropdown-raises-z-index-while-expanded
  (testing "Dropdown temporarily renders over its siblings when expanded"
    (state/reset-entity-ids!)
    (let [game-state (atom (state/create-game-state))
          dropdown (wcore/dropdown [{:value :blue :label "Blue"}
                                    {:value :red :label "Red"}]
                                   :blue
                                   (fn [_])
                                   :id :color-dropdown
                                   :bounds {:x 0 :y 0 :width 200 :height 36})
          slider (wcore/slider 0.0 1.0 0.5 (fn [_])
                               :id :opacity-slider
                               :bounds {:x 0 :y 50 :width 200 :height 24})
          dropdown-id (wcore/add-widget! game-state dropdown)
          slider-id (wcore/add-widget! game-state slider)
          base-z (get-in (state/get-entity game-state dropdown-id)
                         [:components :layout :z-index])]
      (#'winteraction/set-dropdown-expanded! game-state dropdown-id true)
      (let [expanded-z (get-in (state/get-entity game-state dropdown-id)
                               [:components :layout :z-index])
            slider-z (get-in (state/get-entity game-state slider-id)
                             [:components :layout :z-index])]
        (is (> expanded-z slider-z))
        (is (> expanded-z base-z)))
      (#'winteraction/set-dropdown-expanded! game-state dropdown-id false)
      (is (= base-z (get-in (state/get-entity game-state dropdown-id)
                            [:components :layout :z-index]))))))

(deftest dropdown-hit-area-includes-expanded-menu
  (testing "Expanded dropdown captures clicks below its base height"
    (state/reset-entity-ids!)
    (let [game-state (atom (state/create-game-state))
          dropdown (wcore/dropdown [{:value :blue :label "Blue"}
                                    {:value :red :label "Red"}]
                                   :blue
                                   (fn [_])
                                   :id :color-dropdown
                                   :bounds {:x 10 :y 10 :width 200 :height 36})
          dropdown-id (wcore/add-widget! game-state dropdown)]
      (#'winteraction/set-dropdown-expanded! game-state dropdown-id true)
      (let [menu-y (+ 10 50)  ;; below base-height
            hit (winteraction/find-widget-at-point game-state
                                                   (* wconfig/ui-scale 20)
                                                   (* wconfig/ui-scale menu-y))]
        (is (= dropdown-id (first hit)))))))
