(ns silent-king.reactui.app-test
  (:require [clojure.test :refer [deftest is]]
            [silent-king.reactui.app :as app]
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

(defn- find-node-by
  [node pred]
  (when node
    (or (when (pred node)
          node)
        (some #(find-node-by % pred) (:children node)))))

(defn- find-button-with-event
  [tree event]
  (find-node-by tree (fn [node]
                       (and (= :button (:type node))
                            (= event (get-in node [:props :on-click]))))))

(defn- find-slider-with-event
  [tree event]
  (find-node-by tree (fn [node]
                       (and (= :slider (:type node))
                            (= event (get-in node [:props :on-change]))))))

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
      (is (= 123 (get-in props [:metrics :visible-stars])))
      (is (= 2.0 (:ui-scale props))))))

(deftest control-panel-events-update-game-state
  (let [game-state (atom (state/create-game-state))
        tree (build-layout (app/root-tree game-state))
        button-node (find-button-with-event tree [:ui/toggle-hyperlanes])
        button-bounds (layout/bounds button-node)
        button-x (+ (:x button-bounds) (/ (:width button-bounds) 2.0))
        button-y (+ (:y button-bounds) (/ (:height button-bounds) 2.0))
        button-events (interaction/click->events tree button-x button-y)]
    (doseq [event button-events]
      (events/dispatch-event! game-state event))
    (is (false? (state/hyperlanes-enabled? game-state)))
    (let [zoom-slider (find-slider-with-event tree [:ui/set-zoom])
          scale-slider (find-slider-with-event tree [:ui/set-scale])
          zoom-track (get-in zoom-slider [:layout :slider :track])
          zoom-x (+ (:x zoom-track) (:width zoom-track))
          zoom-y (+ (:y zoom-track) (/ (:height zoom-track) 2.0))
          zoom-events (interaction/click->events tree zoom-x zoom-y)
          scale-track (get-in scale-slider [:layout :slider :track])
          scale-x (+ (:x scale-track) (:width scale-track))
          scale-y (+ (:y scale-track) (/ (:height scale-track) 2.0))
          scale-events (interaction/click->events tree scale-x scale-y)]
      (doseq [event zoom-events]
        (events/dispatch-event! game-state event))
      (doseq [event scale-events]
        (events/dispatch-event! game-state event)))
    (is (= 4.0 (get-in @game-state [:camera :zoom])))
    (is (= 3.0 (state/ui-scale game-state)))))

(deftest hyperlane-settings-slider-updates-state
  (let [game-state (atom (state/create-game-state))
        tree (build-layout (app/root-tree game-state))
        opacity-slider (find-slider-with-event tree [:hyperlanes/set-opacity])
        track (get-in opacity-slider [:layout :slider :track])
        click-x (+ (:x track) (:width track))
        click-y (+ (:y track) (/ (:height track) 2.0))
        events (interaction/click->events tree click-x click-y)]
    (doseq [event events]
      (events/dispatch-event! game-state event))
    (is (= 1.0 (:opacity (state/hyperlane-settings game-state))))))
