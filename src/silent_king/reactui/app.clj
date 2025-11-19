(ns silent-king.reactui.app
  "Bridges game state to the Reactified UI tree."
  (:require [silent-king.reactui.components.control-panel :as control-panel]
            [silent-king.reactui.components.hyperlane-settings :as hyperlane-settings]
            [silent-king.reactui.components.minimap :as minimap]
            [silent-king.reactui.components.performance-overlay :as performance-overlay]
            [silent-king.reactui.components.star-inspector :as star-inspector]
            [silent-king.reactui.core :as ui-core]
            [silent-king.selection :as selection]
            [silent-king.state :as state])
  (:import [io.github.humbleui.skija Canvas]))

(set! *warn-on-reflection* true)

(def ^:const inspector-margin 24.0)
(def ^:const inspector-hidden-gap 32.0)

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

(defn star-inspector-props
  [game-state]
  (let [selection (selection/selected-view game-state)
        viewport (state/ui-viewport game-state)
        scale (state/ui-scale game-state)
        panel-width (:width star-inspector/default-panel-bounds)
        physical-width (double (or (:width viewport) panel-width))
        logical-width (if (pos? scale)
                        (/ physical-width scale)
                        physical-width)
        base-x (max inspector-margin
                    (- logical-width panel-width inspector-margin))
        base-bounds (-> star-inspector/default-panel-bounds
                        (assoc :x base-x
                               :y inspector-margin))
        visible? (or (state/star-inspector-visible? game-state)
                     (some? selection))
        bounds (if visible?
                 base-bounds
                 (assoc base-bounds
                        :x (+ base-x panel-width inspector-hidden-gap)))]
    {:selection selection
     :visible? visible?
     :bounds bounds}))

(defn minimap-props
  [game-state]
  (let [base-props (minimap/minimap-props game-state)
        viewport (state/ui-viewport game-state)
        scale (state/ui-scale game-state)
        margin 24.0
        panel-width (:width minimap/default-panel-bounds)
        panel-height (:height minimap/default-panel-bounds)
        physical-width (double (or (:width viewport) panel-width))
        physical-height (double (or (:height viewport) panel-height))
        logical-width (if (pos? scale)
                        (/ physical-width scale)
                        physical-width)
        logical-height (if (pos? scale)
                         (/ physical-height scale)
                         physical-height)
        x (- logical-width panel-width margin)
        y (- logical-height panel-height margin)
        bounds {:x x :y y :width panel-width :height panel-height}]
    (assoc base-props :bounds bounds)))

(defn root-tree
  [game-state]
  [:vstack {:key :ui-root}
   (control-panel/control-panel (control-panel-props game-state))
   (hyperlane-settings/hyperlane-settings-panel (hyperlane-settings-props game-state))
   (performance-overlay/performance-overlay (performance-overlay-props game-state))
   ;; Render last so it stacks on the right side without overlapping other panels.
   (star-inspector/star-inspector (star-inspector-props game-state))
   (minimap/minimap (minimap-props game-state))])

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
