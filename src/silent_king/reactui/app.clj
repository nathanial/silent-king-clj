(ns silent-king.reactui.app
  "Bridges game state to the Reactified UI tree."
  (:require [silent-king.reactui.components.control-panel :as control-panel]
            [silent-king.reactui.components.hyperlane-settings :as hyperlane-settings]
            [silent-king.reactui.components.minimap :as minimap]
            [silent-king.reactui.components.performance-overlay :as performance-overlay]
            [silent-king.reactui.components.star-inspector :as star-inspector]
            [silent-king.reactui.core :as ui-core]
            [silent-king.reactui.primitives]
            [silent-king.selection :as selection]
            [silent-king.state :as state])
  (:import [io.github.humbleui.skija Canvas]))

(set! *warn-on-reflection* true)

(def ^:const inspector-margin 24.0)

(def ^:private control-panel-window-id :ui/control-panel)
(def ^:private hyperlane-window-id :ui/hyperlane-settings)
(def ^:private performance-window-id :ui/performance-overlay)
(def ^:private star-inspector-window-id :ui/star-inspector)
(def ^:private minimap-window-id :ui/minimap)

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
        default-bounds (-> performance-overlay/default-panel-bounds
                           (assoc :x x
                                  :y margin))]
    {:metrics metrics
     :fps-history fps-history
     :default-bounds default-bounds
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
                     (some? selection))]
    {:selection selection
     :visible? visible?
     :default-bounds base-bounds}))

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
                         physical-height)]
    (assoc base-props
           :default-bounds {:x (- logical-width panel-width margin)
                            :y (- logical-height panel-height margin)
                            :width panel-width
                            :height panel-height})))

(defn- control-panel-default-bounds
  []
  {:x 24.0
   :y 24.0
   :width (:width control-panel/default-panel-bounds)
   :height (:height control-panel/default-panel-bounds)})

(defn- hyperlane-default-bounds
  []
  (let [margin 24.0
        control-width (:width control-panel/default-panel-bounds)]
    {:x (+ margin control-width margin)
     :y margin
     :width (:width hyperlane-settings/default-panel-bounds)
     :height (:height hyperlane-settings/default-panel-bounds)}))

(defn control-panel-window
  [game-state]
  (let [props (control-panel-props game-state)
        default-bounds (control-panel-default-bounds)
        bounds (state/window-bounds game-state control-panel-window-id default-bounds)
        minimized? (state/window-minimized? game-state control-panel-window-id)
        content (when-not minimized?
                  (control-panel/control-panel props))]
    {:type :window
     :props {:title "Controls"
             :bounds bounds
             :minimized? minimized?
             :resizable? true
             :min-width (:width control-panel/default-panel-bounds)
             :min-height (:height control-panel/default-panel-bounds)
             :on-change-bounds [:ui.window/set-bounds control-panel-window-id]
             :on-toggle-minimized [:ui.window/toggle-minimized control-panel-window-id]}
     :children (cond-> []
                 content (conj content))}))

(defn hyperlane-settings-window
  [game-state]
  (let [props (hyperlane-settings-props game-state)
        default-bounds (hyperlane-default-bounds)
        bounds (state/window-bounds game-state hyperlane-window-id default-bounds)
        minimized? (state/window-minimized? game-state hyperlane-window-id)
        content (when-not minimized?
                  (hyperlane-settings/hyperlane-settings-panel props))]
    {:type :window
     :props {:title "Hyperlane Settings"
             :bounds bounds
             :minimized? minimized?
             :resizable? true
             :min-width (:width hyperlane-settings/default-panel-bounds)
             :min-height (:height hyperlane-settings/default-panel-bounds)
             :on-change-bounds [:ui.window/set-bounds hyperlane-window-id]
             :on-toggle-minimized [:ui.window/toggle-minimized hyperlane-window-id]}
     :children (cond-> []
                 content (conj content))}))

(defn performance-overlay-window
  [game-state]
  (let [{:keys [default-bounds] :as props} (performance-overlay-props game-state)
        bounds (state/window-bounds game-state performance-window-id default-bounds)
        minimized? (state/window-minimized? game-state performance-window-id)
        content (when-not minimized?
                  (performance-overlay/performance-overlay (dissoc props :default-bounds)))]
    {:type :window
     :props {:title "Performance Overlay"
             :bounds bounds
             :minimized? minimized?
             :resizable? true
             :min-width (:width performance-overlay/default-panel-bounds)
             :min-height (:height performance-overlay/default-panel-bounds)
             :on-change-bounds [:ui.window/set-bounds performance-window-id]
             :on-toggle-minimized [:ui.window/toggle-minimized performance-window-id]}
     :children (cond-> []
                 content (conj content))}))

(defn star-inspector-window
  [game-state]
  (let [{:keys [visible? default-bounds] :as props} (star-inspector-props game-state)]
    (when visible?
      (let [bounds (state/window-bounds game-state star-inspector-window-id default-bounds)
            minimized? (state/window-minimized? game-state star-inspector-window-id)
            content (when-not minimized?
                      (star-inspector/star-inspector (dissoc props :default-bounds)))]
        {:type :window
         :props {:title "Star Inspector"
                 :bounds bounds
                 :minimized? minimized?
                 :resizable? true
                 :min-width (:width star-inspector/default-panel-bounds)
                 :min-height (:height star-inspector/default-panel-bounds)
                 :on-change-bounds [:ui.window/set-bounds star-inspector-window-id]
                 :on-toggle-minimized [:ui.window/toggle-minimized star-inspector-window-id]}
         :children (cond-> []
                     content (conj content))}))))

(defn minimap-window
  [game-state]
  (let [props (minimap-props game-state)
        visible? (:visible? props)
        default-bounds (:default-bounds props)
        minimap-props (dissoc props :default-bounds)
        bounds (state/window-bounds game-state minimap-window-id default-bounds)
        minimized? (state/window-minimized? game-state minimap-window-id)
        content (when-not minimized?
                  (minimap/minimap minimap-props))]
    (when visible?
      {:type :window
       :props {:title "Minimap"
               :bounds bounds
               :minimized? minimized?
               :resizable? true
               :min-width (:width minimap/default-panel-bounds)
               :min-height (:height minimap/default-panel-bounds)
               :content-padding {:all 12.0}
               :on-change-bounds [:ui.window/set-bounds minimap-window-id]
               :on-toggle-minimized [:ui.window/toggle-minimized minimap-window-id]}
       :children (cond-> []
                   content (conj content))})))

(defn root-tree
  [game-state]
  [:vstack {:key :ui-root}
   (control-panel-window game-state)
   (hyperlane-settings-window game-state)
   (performance-overlay-window game-state)
   ;; Render last so it stacks on the right side without overlapping other panels.
   (when-let [window (star-inspector-window game-state)]
     window)
   (when-let [window (minimap-window game-state)]
     window)])

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
