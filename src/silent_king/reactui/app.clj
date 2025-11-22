(ns silent-king.reactui.app
  "Bridges game state to the Reactified UI tree."
  (:require [silent-king.reactui.components.control-panel :as control-panel]
            [silent-king.reactui.components.hyperlane-settings :as hyperlane-settings]
            [silent-king.reactui.components.voronoi-settings :as voronoi-settings]
            [silent-king.reactui.components.minimap :as minimap]
            [silent-king.reactui.components.performance-overlay :as performance-overlay]
            [silent-king.reactui.components.star-inspector :as star-inspector]
            [silent-king.reactui.core :as ui-core]
            [silent-king.reactui.primitives]
            [silent-king.render.commands :as commands]
            [silent-king.render.skia :as skia]
            [silent-king.selection :as selection]
            [silent-king.state :as state])
  (:import [io.github.humbleui.skija Canvas]))

(set! *warn-on-reflection* true)

(def ^:const inspector-margin 24.0)

(def ^:private control-panel-window-id :ui/control-panel)
(def ^:private performance-window-id :ui/performance-overlay)
(def ^:private star-inspector-window-id :ui/star-inspector)
(def ^:private minimap-window-id :ui/minimap)
(def ^:private galaxy-window-id :ui/galaxy)

(defn control-panel-props
  [game-state]
  (let [camera (state/get-camera game-state)
        metrics (get-in @game-state [:metrics :performance :latest])]
    {:zoom (double (or (:zoom camera) 1.0))
     :hyperlanes-enabled? (state/hyperlanes-enabled? game-state)
     :voronoi-enabled? (state/voronoi-enabled? game-state)
     :stars-and-planets-enabled? (state/stars-and-planets-enabled? game-state)
     :ui-scale (state/ui-scale game-state)
     :metrics {:fps (double (or (:fps metrics) 0.0))
               :visible-stars (long (or (:visible-stars metrics) 0))
               :draw-calls (long (or (:draw-calls metrics) 0))}
     :active-tab (or (:control-panel-tab @game-state) :main)}))

(defn hyperlane-settings-props
  [game-state]
  {:settings (state/hyperlane-settings game-state)
   :expanded? (state/hyperlane-panel-expanded? game-state)
   :color-dropdown-expanded? (state/dropdown-open? game-state :hyperlane-color)})

(defn voronoi-settings-props
  [game-state]
  {:settings (state/voronoi-settings game-state)
   :expanded? (state/voronoi-panel-expanded? game-state)
   :color-dropdown-expanded? (state/dropdown-open? game-state :voronoi-color)})

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



(defn control-panel-window
  [game-state]
  (let [props (control-panel-props game-state)
        hyperlane-props (hyperlane-settings-props game-state)
        voronoi-props (voronoi-settings-props game-state)
        default-bounds (control-panel-default-bounds)
        bounds (state/window-bounds game-state control-panel-window-id default-bounds)
        minimized? (state/window-minimized? game-state control-panel-window-id)
        content (when-not minimized?
                  (case (:active-tab props)
                    :main (control-panel/control-panel props)
                    :hyperlanes (hyperlane-settings/hyperlane-settings-panel hyperlane-props)
                    :voronoi (voronoi-settings/voronoi-settings-panel voronoi-props)
                    (control-panel/control-panel props)))]
    {:type :tabbed-window
     :props {:title "Controls"
             :bounds bounds
             :minimized? minimized?
             :resizable? true
             :min-width (:width control-panel/default-panel-bounds)
             :min-height (:height control-panel/default-panel-bounds)
             :on-change-bounds [:ui.window/set-bounds control-panel-window-id]
             :on-toggle-minimized [:ui.window/toggle-minimized control-panel-window-id]
             :on-bring-to-front [:ui.window/bring-to-front control-panel-window-id]
             :tabs [{:id :main :label "Main"}
                    {:id :hyperlanes :label "Hyperlanes"}
                    {:id :voronoi :label "Voronoi"}]
             :active-tab (:active-tab props)
             :on-change-tab [:ui.control-panel/set-tab]}
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
             :on-toggle-minimized [:ui.window/toggle-minimized performance-window-id]
             :on-bring-to-front [:ui.window/bring-to-front performance-window-id]}
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
                 :on-toggle-minimized [:ui.window/toggle-minimized star-inspector-window-id]
                 :on-bring-to-front [:ui.window/bring-to-front star-inspector-window-id]}
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
               :on-toggle-minimized [:ui.window/toggle-minimized minimap-window-id]
               :on-bring-to-front [:ui.window/bring-to-front minimap-window-id]}
       :children (cond-> []
                   content (conj content))})))

(defn galaxy-window
  [game-state]
  (let [default-bounds {:x 100.0 :y 100.0 :width 600.0 :height 400.0}
        bounds (state/window-bounds game-state galaxy-window-id default-bounds)
        minimized? (state/window-minimized? game-state galaxy-window-id)
        content (when-not minimized?
                  [:galaxy {:game-state game-state}])]
    {:type :window
     :props {:title "Galaxy View"
             :bounds bounds
             :minimized? minimized?
             :resizable? true
             :min-width 200.0
             :min-height 200.0
             :on-change-bounds [:ui.window/set-bounds galaxy-window-id]
             :on-toggle-minimized [:ui.window/toggle-minimized galaxy-window-id]
             :on-bring-to-front [:ui.window/bring-to-front galaxy-window-id]}
     :children (cond-> []
                 content (conj content))}))

(defn render-dock
  [game-state docking-state side bounds]
  (let [dock (get docking-state side)
        renderers {galaxy-window-id galaxy-window
                   minimap-window-id minimap-window
                   control-panel-window-id control-panel-window
                   performance-window-id performance-overlay-window
                   star-inspector-window-id star-inspector-window}
        windows (keep (fn [id]
                        (when-let [renderer (get renderers id)]
                          (let [component (renderer game-state)]
                            (when component
                              {:id id
                               :title (get-in component [:props :title] (str id))
                               :component component}))))
                      (:windows dock))]
    (when (seq windows)
      (let [active-id (:active dock)
            active-component (some #(when (= (:id %) active-id) (:component %)) windows)]
        {:type :dock-container
         :props {:bounds bounds
                 :side side
                 :tabs (mapv #(select-keys % [:id :title]) windows)
                 :active-id active-id
                 :on-resize [:ui.dock/resize side]
                 :on-select-tab [:ui.dock/select-tab side]}
         ;; Render the CONTENT of the window, stripping the window chrome
         :children (if active-component
                     (:children active-component)
                     [])}))))

(defn render-floating-windows
  [game-state]
  (let [window-order (state/get-window-order game-state)
        renderers {galaxy-window-id galaxy-window
                   minimap-window-id minimap-window
                   control-panel-window-id control-panel-window
                   performance-window-id performance-overlay-window
                   star-inspector-window-id star-inspector-window}]
    (for [window-id window-order
          :let [renderer (get renderers window-id)]
          :when renderer
          :let [component (renderer game-state)]
          :when component]
      component)))

(defn dock-preview-overlay
  [game-state]
  (let [{:keys [visible? rect]} (get-in @game-state [:ui :dock-preview])]
    (when visible?
      {:type :window ;; Using window as a rect container for now, or specific overlay
       :props {:bounds rect
               :background-color [0.2 0.6 1.0 0.3] ;; Transparent blue
               :border-color [0.2 0.6 1.0 0.8]
               :header-height 0
               :resizable? false
               :minimized? false}
       :children []})))

(defn root-tree
  [game-state viewport] ;; Note: viewport passed in
  (let [docking-state (state/get-docking-state game-state)
        layout (silent-king.reactui.layout/calculate-dock-layout viewport docking-state)
        
        ;; Dock Containers
        left-dock (render-dock game-state docking-state :left (:left layout))
        right-dock (render-dock game-state docking-state :right (:right layout))
        top-dock (render-dock game-state docking-state :top (:top layout))
        bottom-dock (render-dock game-state docking-state :bottom (:bottom layout))
        center-dock (render-dock game-state docking-state :center (:center layout))
        
        ;; Floating Windows
        floating (render-floating-windows game-state)
        
        ;; Preview
        preview (dock-preview-overlay game-state)]
    
    ;; We return a flat list of children effectively.
    ;; But `root-tree` is expected to be a single element.
    ;; We'll use a :vstack (or similar container) that takes up full screen?
    ;; Actually, the `layout/calculate-dock-layout` implies we are orchestrating layout.
    ;; But `ui-core/render-ui-tree` expects a single root.
    ;; Let's create a "root" container that just passes through its children but uses manual bounds?
    ;; Or just a fragment if our system supports it?
    ;; The system expects a single map.
    
    {:type :stack
     :props {:bounds viewport} ;; Fill viewport
     :children (cond-> []
                 ;; Background / Game View could go here in :center layout?
                 ;; For now, we treat "Game View" as just what's behind the UI.
                 ;; BUT, if we want the UI to squeeze the game view, we need to communicate that back to camera.
                 ;; For this step, let's just render UI.
                 
                 center-dock (conj center-dock) ;; Render center dock at bottom (or above game view if implemented)
                 top-dock (conj top-dock)
                 bottom-dock (conj bottom-dock)
                 left-dock (conj left-dock)
                 right-dock (conj right-dock)
                 
                 :always (into floating)
                 
                 preview (conj preview))}))

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
        logical-vp (logical-viewport scale viewport) ;; Calc once
        input (state/get-input game-state)
        pointer (when (:mouse-initialized? input)
                  {:x (/ (double (or (:mouse-x input) 0.0)) scale)
                   :y (/ (double (or (:mouse-y input) 0.0)) scale)})
        render-context {:pointer pointer
                        :active-interaction (ui-core/active-interaction)}
        
        ;; Pass logical-vp to root-tree for layout calc
        tree (root-tree game-state logical-vp)
        
        {:keys [layout-tree commands]} (ui-core/render-ui-tree {:canvas nil
                                                                :tree tree
                                                                :viewport logical-vp
                                                                :context render-context})
        scaled-commands (if (= 1.0 scale)
                          commands
                          (into [(commands/save)
                                 (commands/scale scale scale)]
                                (concat commands
                                        [(commands/restore)])))]
    (when canvas
      (skia/draw-commands! canvas scaled-commands))
    {:layout-tree layout-tree
     :commands scaled-commands}))
