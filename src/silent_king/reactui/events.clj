(ns silent-king.reactui.events
  "Dispatch helpers that translate UI event vectors into game-state updates."
  (:require [silent-king.state :as state]))

(set! *warn-on-reflection* true)

(defn- clamp-zoom
  [value]
  (-> value
      (double)
      (max 0.4)
      (min 10.0)))

(defn- clamp-range
  [value min-value max-value]
  (-> value double (max min-value) (min max-value)))

(def ^:private color-schemes
  #{:blue :red :green :rainbow})

(defmulti dispatch-event!
  (fn [_game-state event]
    (first event)))

(defmethod dispatch-event! :ui/toggle-hyperlanes
  [game-state _]
  (state/toggle-hyperlanes! game-state)
  nil)

(defmethod dispatch-event! :ui/set-zoom
  [game-state [_ value]]
  (when (number? value)
    (state/update-camera! game-state assoc :zoom (clamp-zoom value)))
  nil)

(defmethod dispatch-event! :ui/set-scale
  [game-state [_ value]]
  (when (number? value)
    (state/set-ui-scale! game-state value))
  nil)

(defmethod dispatch-event! :ui/toggle-hyperlane-panel
  [game-state _]
  (state/toggle-hyperlane-panel! game-state)
  nil)

(defmethod dispatch-event! :hyperlanes/set-enabled?
  [game-state [_ value]]
  (state/set-hyperlane-setting! game-state :enabled? (boolean value))
  nil)

(defmethod dispatch-event! :hyperlanes/set-opacity
  [game-state [_ value]]
  (when (number? value)
    (state/set-hyperlane-setting! game-state :opacity (clamp-range value 0.05 1.0)))
  nil)

(defmethod dispatch-event! :hyperlanes/set-animation-speed
  [game-state [_ value]]
  (when (number? value)
    (state/set-hyperlane-setting! game-state :animation-speed (clamp-range value 0.1 3.0)))
  nil)

(defmethod dispatch-event! :hyperlanes/set-line-width
  [game-state [_ value]]
  (when (number? value)
    (state/set-hyperlane-setting! game-state :line-width (clamp-range value 0.4 3.0)))
  nil)

(defmethod dispatch-event! :hyperlanes/set-animation?
  [game-state [_ value]]
  (state/set-hyperlane-setting! game-state :animation? (boolean value))
  nil)

(defmethod dispatch-event! :hyperlanes/set-color-scheme
  [game-state [_ value]]
  (when (and (keyword? value)
             (color-schemes value))
    (state/set-hyperlane-setting! game-state :color-scheme value))
  nil)

(defmethod dispatch-event! :hyperlanes/reset
  [game-state _]
  (state/reset-hyperlane-settings! game-state)
  nil)

(defmethod dispatch-event! :default
  [_ event]
  (println "Unhandled UI event" event)
  nil)
