(ns silent-king.reactui.events
  "Dispatch helpers that translate UI event vectors into game-state updates."
  (:require [silent-king.schemas :as schemas]
            [silent-king.state :as state]
            [silent-king.voronoi :as voronoi]))

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

(def ^:private voronoi-color-schemes
  #{:monochrome :by-density :by-degree})

(defn- validate-ui-event!
  [event]
  (schemas/validate-ui-event! event)
  event)

(defmulti dispatch-event!
  (fn [_game-state event]
    (validate-ui-event! event)
    (first event)))

(defmethod dispatch-event! :ui/toggle-hyperlanes
  [game-state _]
  (state/toggle-hyperlanes! game-state)
  nil)

(defmethod dispatch-event! :ui/toggle-voronoi
  [game-state _]
  (state/toggle-voronoi! game-state)
  nil)

(defmethod dispatch-event! :ui/toggle-stars-and-planets
  [game-state _]
  (state/toggle-stars-and-planets! game-state)
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

(defmethod dispatch-event! :ui/toggle-voronoi-panel
  [game-state _]
  (state/toggle-voronoi-panel! game-state)
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

(defmethod dispatch-event! :voronoi/set-enabled?
  [game-state [_ value]]
  (state/set-voronoi-setting! game-state :enabled? (boolean value))
  nil)

(defmethod dispatch-event! :voronoi/set-opacity
  [game-state [_ value]]
  (when (number? value)
    (state/set-voronoi-setting! game-state :opacity (clamp-range value 0.05 1.0)))
  nil)

(defmethod dispatch-event! :voronoi/set-line-width
  [game-state [_ value]]
  (when (number? value)
    (state/set-voronoi-setting! game-state :line-width (clamp-range value 0.5 4.0)))
  nil)

(defmethod dispatch-event! :voronoi/set-relax-iterations
  [game-state [_ value]]
  (when (number? value)
    (state/set-voronoi-setting! game-state :relax-iterations
                                (long (clamp-range value 0 state/relax-iterations-limit))))
  (voronoi/generate-voronoi! game-state)
  nil)

(defmethod dispatch-event! :voronoi/set-relax-step
  [game-state [_ value]]
  (when (number? value)
    (state/set-voronoi-setting! game-state :relax-step (clamp-range value 0.0 1.0)))
  (voronoi/generate-voronoi! game-state)
  nil)

(defmethod dispatch-event! :voronoi/set-relax-max-displacement
  [game-state [_ value]]
  (when (number? value)
    (state/set-voronoi-setting! game-state :relax-max-displacement (clamp-range value 0.0 500.0)))
  (voronoi/generate-voronoi! game-state)
  nil)

(defmethod dispatch-event! :voronoi/set-relax-clip?
  [game-state [_ value]]
  (state/set-voronoi-setting! game-state :relax-clip-to-envelope? (boolean value))
  (voronoi/generate-voronoi! game-state)
  nil)

(defmethod dispatch-event! :voronoi/set-color-scheme
  [game-state [_ value]]
  (when (and (keyword? value)
             (voronoi-color-schemes value))
    (state/set-voronoi-setting! game-state :color-scheme value))
  nil)

(defmethod dispatch-event! :voronoi/set-show-centroids?
  [game-state [_ value]]
  (state/set-voronoi-setting! game-state :show-centroids? (boolean value))
  nil)

(defmethod dispatch-event! :voronoi/set-hide-border-cells?
  [game-state [_ value]]
  (state/set-voronoi-setting! game-state :hide-border-cells? (boolean value))
  (voronoi/generate-voronoi! game-state)
  nil)

(defmethod dispatch-event! :voronoi/set-jaggedness
  [game-state [_ value]]
  (when (number? value)
    (state/set-voronoi-setting! game-state :jaggedness (clamp-range value 0.0 1.5)))
  (voronoi/generate-voronoi! game-state)
  nil)

(defmethod dispatch-event! :voronoi/reset
  [game-state _]
  (state/reset-voronoi-settings! game-state)
  nil)

(defmethod dispatch-event! :ui.dropdown/toggle
  [game-state [_ dropdown-id]]
  (state/toggle-dropdown! game-state dropdown-id)
  nil)

(defmethod dispatch-event! :ui.dropdown/close
  [game-state [_ dropdown-id]]
  (state/close-dropdown! game-state dropdown-id)
  nil)

(defmethod dispatch-event! :ui/clear-selection
  [game-state _]
  (state/clear-selection! game-state)
  (state/hide-star-inspector! game-state)
  nil)

(defmethod dispatch-event! :ui/zoom-to-selected-star
  [game-state [_ opts]]
  (let [opts* (when (map? opts) opts)]
    (state/zoom-to-selection! game-state opts*))
  nil)

(defmethod dispatch-event! :ui/perf-toggle-visible
  [game-state _]
  (state/toggle-performance-overlay-visible! game-state)
  nil)

(defmethod dispatch-event! :ui/perf-toggle-expanded
  [game-state _]
  (state/toggle-performance-overlay-expanded! game-state)
  nil)

(defmethod dispatch-event! :metrics/reset-performance
  [game-state _]
  (state/reset-performance-metrics! game-state)
  nil)

(defmethod dispatch-event! :ui.window/set-bounds
  [game-state [_ window-id bounds]]
  (when (and window-id (map? bounds))
    (state/set-window-bounds! game-state window-id bounds))
  nil)

(defmethod dispatch-event! :ui.window/toggle-minimized
  [game-state [_ window-id]]
  (when window-id
    (state/toggle-window-minimized! game-state window-id))
  nil)

(defmethod dispatch-event! :ui.window/bring-to-front
  [game-state [_ window-id]]
  (state/bring-window-to-front! game-state window-id)
  nil)

(defmethod dispatch-event! :camera/pan-to-world
  [game-state [_ pos]]
  (state/focus-camera-on-world! game-state pos)
  nil)

(defmethod dispatch-event! :camera/set-pan
  [game-state [_ x y]]
  (state/update-camera! game-state assoc :pan-x (double x) :pan-y (double y))
  nil)

(defmethod dispatch-event! :ui.control-panel/set-tab
  [game-state [_ tab-id]]
  (swap! game-state assoc :control-panel-tab tab-id)
  nil)

(defmethod dispatch-event! :default
  [_ event]
  (println "Unhandled UI event" event)
  nil)
