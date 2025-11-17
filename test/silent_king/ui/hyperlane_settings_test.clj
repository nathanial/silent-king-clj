(ns silent-king.ui.hyperlane-settings-test
  (:require [clojure.test :refer :all]
            [silent-king.state :as state]
            [silent-king.widgets.core :as wcore]
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
