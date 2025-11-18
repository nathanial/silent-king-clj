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

(defmethod dispatch-event! :default
  [_ event]
  (println "Unhandled UI event" event)
  nil)
