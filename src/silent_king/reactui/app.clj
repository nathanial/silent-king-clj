(ns silent-king.reactui.app
  "Bridges game state to the Reactified UI tree."
  (:require [silent-king.reactui.components.control-panel :as control-panel]
            [silent-king.reactui.components.hyperlane-settings :as hyperlane-settings]
            [silent-king.reactui.components.performance-overlay :as performance-overlay]
            [silent-king.reactui.core :as ui-core]
            [silent-king.state :as state])
  (:import [io.github.humbleui.skija Canvas]))

(set! *warn-on-reflection* true)

(defn control-panel-props
  [game-state]
  (let [camera (state/get-camera game-state)
        metrics (get-in @game-state [:metrics :performance :latest])]
    {:zoom (double (or (:zoom camera) 1.0))
     :hyperlanes-enabled? (state/hyperlanes-enabled? game-state)
     :ui-scale (state/ui-scale game-state)
     :metrics {:fps (double (or (:fps metrics) 0.0))
               :visible-stars (long (or (:visible-stars metrics) 0))
               :draw-calls (long (or (:draw-calls metrics) 0))}}))

(defn hyperlane-settings-props
  [game-state]
  {:settings (state/hyperlane-settings game-state)
   :expanded? (state/hyperlane-panel-expanded? game-state)
   :color-dropdown-expanded? (state/dropdown-open? game-state :hyperlane-color)})

(defn performance-overlay-props
  [game-state]
  (let [metrics-state (get-in @game-state [:metrics :performance])
        metrics (or (:latest metrics-state) {})
        fps-history (vec (or (:fps-history metrics-state) []))
        viewport (state/ui-viewport game-state)
        margin 24.0
        panel-width (double (or (:width performance-overlay/default-panel-bounds) 320.0))
        scale (state/ui-scale game-state)
        physical-width (double (or (:width viewport) panel-width))
        logical-width (if (pos? scale)
                        (/ physical-width scale)
                        physical-width)
        x (max margin (- logical-width panel-width margin))
        bounds (-> performance-overlay/default-panel-bounds
                   (assoc :x x
                          :y margin))]
    {:metrics metrics
     :fps-history fps-history
     :bounds bounds
     :visible? (state/performance-overlay-visible? game-state)
     :expanded? (state/performance-overlay-expanded? game-state)}))

(defn root-tree
  [game-state]
  [:vstack {:key :ui-root}
   (control-panel/control-panel (control-panel-props game-state))
   (hyperlane-settings/hyperlane-settings-panel (hyperlane-settings-props game-state))
   (performance-overlay/performance-overlay (performance-overlay-props game-state))])

(defn logical-viewport
  [scale {:keys [x y width height]}]
  {:x (/ x scale)
   :y (/ y scale)
   :width (/ width scale)
   :height (/ height scale)})

(defn render!
  "Render the full Reactified UI."
  [^Canvas canvas viewport game-state]
  (state/set-ui-viewport! game-state viewport)
  (let [scale (state/ui-scale game-state)
        input (state/get-input game-state)
        pointer (when (:mouse-initialized? input)
                  {:x (/ (double (or (:mouse-x input) 0.0)) scale)
                   :y (/ (double (or (:mouse-y input) 0.0)) scale)})
        render-context {:pointer pointer
                        :active-interaction (ui-core/active-interaction)}]
    (when canvas
      (.save canvas)
      (.scale canvas (float scale) (float scale)))
    (let [result (ui-core/render-ui-tree {:canvas canvas
                                          :tree (root-tree game-state)
                                          :viewport (logical-viewport scale viewport)
                                          :context render-context})]
      (when canvas
        (.restore canvas))
      result)))
