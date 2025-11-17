(ns silent-king.ui.hyperlane-settings-test
  (:require [clojure.test :refer :all]
            [silent-king.state :as state]
            [silent-king.widgets.core :as wcore]
            [silent-king.widgets.layout :as wlayout]
            [silent-king.ui.hyperlane-settings :as hsettings]))

(defn- setup-state []
  (state/reset-entity-ids!)
  (atom (state/create-game-state)))

(deftest create-panel-registers-state
  (let [game-state (setup-state)]
    (hsettings/create-hyperlane-settings! game-state)
    (is (some? (get-in @game-state [:ui :hyperlane-settings :panel-entity])))
    (is (false? (get-in @game-state [:ui :hyperlane-settings :body-visible?])))))

(deftest hyperlane-sliders-update-settings
  (let [game-state (setup-state)]
    (hsettings/create-hyperlane-settings! game-state)
    (let [[_ slider-entity] (wcore/get-widget-by-id game-state :hyperlane-opacity-slider)
          handler (get-in slider-entity [:components :interaction :on-change])]
      (is handler)
      (handler 0.45)
      (is (= 0.45 (double (:opacity (state/hyperlane-settings game-state))))))))

(deftest reset-button-restores-defaults
  (let [game-state (setup-state)]
    (hsettings/create-hyperlane-settings! game-state)
    (state/set-hyperlane-setting! game-state :line-width 1.75)
    (let [[_ reset-entity] (wcore/get-widget-by-id game-state :hyperlane-reset-button)
          handler (get-in reset-entity [:components :interaction :on-click])]
      (is handler)
      (handler)
      (is (= state/default-hyperlane-settings
             (state/hyperlane-settings game-state)))))) 

(deftest panel-toggle-makes-body-visible
  (let [game-state (setup-state)]
    (hsettings/create-hyperlane-settings! game-state)
    (hsettings/toggle-panel! game-state)
    (state/update-time! game-state assoc :current-time 1.0)
    (hsettings/update! game-state)
    (is (true? (get-in @game-state [:ui :hyperlane-settings :body-visible?])))))

(deftest sliders-align-after-layout-processing
  (let [game-state (setup-state)]
    (hsettings/create-hyperlane-settings! game-state)
    ;; Force a deterministic dirty order that would previously misplace sliders
    (let [[row-id _] (wcore/get-widget-by-id game-state :hyperlane-opacity-row)
          [panel-id panel-entity] (wcore/get-widget-by-id game-state :hyperlane-settings-panel)]
      (swap! game-state assoc-in [:widgets :layout-dirty] [row-id panel-id])
      (wlayout/process-layouts! game-state 1280 800)
      (let [[_ slider-entity] (wcore/get-widget-by-id game-state :hyperlane-opacity-slider)
            slider-bounds (get-in slider-entity [:components :bounds])
            panel-bounds (get-in panel-entity [:components :bounds])]
        (is (> (:y slider-bounds 0) (:y panel-bounds 0)))
        (is (> (:height panel-bounds 0) 0.0))
        (is (= 150.0 (:width slider-bounds)))))))

(deftest sliders-share-row-bounds
  (let [game-state (setup-state)]
    (hsettings/create-hyperlane-settings! game-state)
    (wlayout/process-layouts! game-state 1280 800)
    (let [[_ row-entity] (wcore/get-widget-by-id game-state :hyperlane-opacity-row)
          [_ slider-entity] (wcore/get-widget-by-id game-state :hyperlane-opacity-slider)
          row-bounds (get-in row-entity [:components :bounds])
          slider-bounds (get-in slider-entity [:components :bounds])]
      (is (<= (:y row-bounds) (:y slider-bounds)))
      (is (<= (:y slider-bounds) (+ (:y row-bounds) (:height row-bounds))))
      (is (= (:height slider-bounds) 24.0)))))
