(ns silent-king.reactui.app-test
  (:require [clojure.test :refer [deftest is testing]]
            [silent-king.reactui.app :as app]
            [silent-king.reactui.components.control-panel :as control-panel]
            [silent-king.reactui.core :as ui-core]
            [silent-king.reactui.events :as events]
            [silent-king.reactui.interaction :as interaction]
            [silent-king.reactui.layout :as layout]
            [silent-king.state :as state]))

(defn- build-layout
  [tree]
  (-> tree
      ui-core/normalize-tree
      (layout/compute-layout {:x 0 :y 0 :width 800 :height 600})))

(defn- find-node
  [node type]
  (if (= (:type node) type)
    node
    (some #(find-node % type) (:children node))))

(deftest control-panel-props-reflect-game-state
  (let [game-state (atom (state/create-game-state))]
    (state/update-camera! game-state assoc :zoom 2.5)
    (swap! game-state assoc-in [:metrics :performance :latest]
           {:fps 55.5 :visible-stars 123 :draw-calls 900})
    (state/toggle-hyperlanes! game-state)
    (let [props (app/control-panel-props game-state)]
      (is (= 2.5 (:zoom props)))
      (is (false? (:hyperlanes-enabled? props)))
      (is (= 55.5 (get-in props [:metrics :fps])))
      (is (= 123 (get-in props [:metrics :visible-stars]))))))

(deftest control-panel-events-update-game-state
  (let [game-state (atom (state/create-game-state))
        tree (build-layout (app/root-tree game-state))
        button-node (find-node tree :button)
        button-bounds (layout/bounds button-node)
        button-x (+ (:x button-bounds) (/ (:width button-bounds) 2.0))
        button-y (+ (:y button-bounds) (/ (:height button-bounds) 2.0))
        button-events (interaction/click->events tree button-x button-y)]
    (doseq [event button-events]
      (events/dispatch-event! game-state event))
    (is (false? (state/hyperlanes-enabled? game-state)))
    (let [slider-node (find-node tree :slider)
          track (get-in slider-node [:layout :slider :track])
          slider-x (+ (:x track) (:width track))
          slider-y (+ (:y track) (/ (:height track) 2.0))
          slider-events (interaction/click->events tree slider-x slider-y)]
      (doseq [event slider-events]
        (events/dispatch-event! game-state event)))
    (is (= 4.0 (get-in @game-state [:camera :zoom])))))
