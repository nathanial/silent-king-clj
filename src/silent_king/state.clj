(ns silent-king.state
  "Game state management with entity-component system"
  (:require [silent-king.camera :as camera]))

(set! *warn-on-reflection* true)

;; =============================================================================
;; Entity ID Generation
;; =============================================================================

(def ^:private entity-id-counter (atom 0))

(defn next-entity-id []
  "Generate a unique entity ID"
  (swap! entity-id-counter inc))

(defn reset-entity-ids! []
  "Reset entity ID counter (useful for testing)"
  (reset! entity-id-counter 0))

(def default-hyperlane-settings
  {:enabled? true
   :opacity 0.9
   :color-scheme :blue
   :animation? true
   :animation-speed 1.0
   :line-width 1.0})

(def default-performance-metrics
  {:fps-history []
   :frame-time-history []
   :last-sample-time 0.0
   :latest {}})

(def default-selection
  {:star-id nil
   :last-world-click nil
   :details nil})

(def ^:const max-performance-samples 120)

(defn- append-sample
  [values sample]
  (if (number? sample)
    (let [values (vec (or values []))
          next (conj values (double sample))
          excess (- (count next) max-performance-samples)]
      (if (pos? excess)
        (vec (subvec next excess))
        next))
    (vec (or values []))))

;; =============================================================================
;; Game State Structure
;; =============================================================================

(defn create-game-state []
  "Create initial game state structure"
  {:entities {}
   :camera {:zoom 1.0
            :pan-x 0.0
            :pan-y 0.0}
   :input {:mouse-x 0.0
           :mouse-y 0.0
           :mouse-down-x 0.0
           :mouse-down-y 0.0
           :dragging false
           :ui-active? false
           :mouse-initialized? false}
   :time {:start-time (System/nanoTime)
          :current-time 0.0
          :frame-count 0}
   :assets {:individual-images []
            :atlas-image-xs nil
            :atlas-metadata-xs {}
            :atlas-size-xs 4096
            :atlas-image-small nil
            :atlas-metadata-small {}
            :atlas-size-small 4096
            :atlas-image-medium nil
           :atlas-metadata-medium {}
           :atlas-size-medium 4096
           :atlas-image-lg nil
           :atlas-metadata-lg {}
           :atlas-size-lg 8192}
   :widgets {:layout-dirty #{}}
   :selection default-selection
   :ui {:scale 2.0
        :star-inspector {:visible? false
                         :pinned? false}
        :hyperlane-panel {:expanded? true}
        :viewport {:width 0.0
                   :height 0.0}
        :windows {}
        :performance-overlay {:visible? true
                               :expanded? true}
        :dropdowns {}}
   :features {:hyperlanes? true
              :minimap? true}
   :hyperlane-settings default-hyperlane-settings
   :metrics {:performance default-performance-metrics}})

(defn create-render-state []
  "Create initial render state structure"
  {:window nil
   :context nil
   :surface nil})

;; =============================================================================
;; Entity Management
;; =============================================================================

(defn create-entity
  "Create a new entity with given components"
  [& {:as components}]
  {:components components})

(defn add-entity!
  "Add an entity to the game state and return the entity ID"
  [game-state entity]
  (let [id (next-entity-id)]
    (swap! game-state assoc-in [:entities id] entity)
    id))

(defn get-entity
  "Get an entity by ID"
  [game-state entity-id]
  (get-in @game-state [:entities entity-id]))

(defn update-entity!
  "Update an entity by ID using function f"
  [game-state entity-id f & args]
  (swap! game-state update-in [:entities entity-id] #(apply f % args)))

(defn remove-entity!
  "Remove an entity by ID"
  [game-state entity-id]
  (swap! game-state update :entities dissoc entity-id))

(defn get-all-entities
  "Get all entities as a map of {id entity}"
  [game-state]
  (:entities @game-state))

;; =============================================================================
;; Component Management
;; =============================================================================

(defn add-component
  "Add a component to an entity (pure function, returns updated entity)"
  [entity component-key component-data]
  (assoc-in entity [:components component-key] component-data))

(defn remove-component
  "Remove a component from an entity (pure function, returns updated entity)"
  [entity component-key]
   (update entity :components dissoc component-key))

(defn get-component
  "Get a component from an entity"
  [entity component-key]
  (get-in entity [:components component-key]))

(defn has-component?
  "Check if an entity has a component"
  [entity component-key]
  (contains? (:components entity) component-key))

(defn has-all-components?
  "Check if an entity has all specified components"
  [entity component-keys]
  (every? #(has-component? entity %) component-keys))

;; =============================================================================
;; Entity Queries
;; =============================================================================

(defn filter-entities-with
  "Filter entities that have all specified components.
   Returns a sequence of [entity-id entity] pairs."
  [game-state component-keys]
  (filter (fn [[_ entity]]
            (has-all-components? entity component-keys))
          (get-all-entities game-state)))

(defn find-entities-with-all
  "Find all entity IDs that have all specified components"
  [game-state component-keys]
  (map first (filter-entities-with game-state component-keys)))

;; =============================================================================
;; State Accessors
;; =============================================================================

(defn get-camera
  "Get camera state"
  [game-state]
  (:camera @game-state))

(defn get-input
  "Get input state"
  [game-state]
  (:input @game-state))

(defn get-time
  "Get time state"
  [game-state]
  (:time @game-state))

;; =============================================================================
;; Selection Helpers
;; =============================================================================

(defn selection
  "Return the current selection map merged with defaults."
  [game-state]
  (merge default-selection
         (or (:selection @game-state) default-selection)))

(defn selected-star-id
  "Return the currently selected star id (or nil)."
  [game-state]
  (:star-id (selection game-state)))

(defn set-selection!
  "Replace the selection map with the supplied data merged onto defaults."
  [game-state selection-map]
  (swap! game-state assoc :selection
         (merge default-selection (or selection-map {}))))

(defn clear-selection!
  "Clear any active selection."
  [game-state]
  (swap! game-state assoc :selection default-selection))

(defn get-assets
  "Get assets"
  [game-state]
  (:assets @game-state))

(defn hyperlane-settings
  "Return merged hyperlane settings."
  [game-state]
  (merge default-hyperlane-settings
         (:hyperlane-settings @game-state)))

(defn set-hyperlane-setting!
  "Set a single hyperlane setting (e.g., :opacity, :line-width)."
  [game-state key value]
  (swap! game-state update :hyperlane-settings
         (fn [settings]
           (assoc (merge default-hyperlane-settings settings) key value)))
  (when (= key :enabled?)
    (swap! game-state assoc-in [:features :hyperlanes?] (boolean value))))

(defn reset-hyperlane-settings!
  "Reset hyperlane settings to defaults."
  [game-state]
  (swap! game-state assoc :hyperlane-settings default-hyperlane-settings)
  (swap! game-state assoc-in [:features :hyperlanes?] (:enabled? default-hyperlane-settings)))

(defn hyperlanes-enabled?
  "Return true if hyperlanes should be rendered"
  [game-state]
  (get (hyperlane-settings game-state) :enabled? true))

(defn minimap-visible?
  "Return true if minimap should be rendered"
  [game-state]
  (get-in @game-state [:features :minimap?] true))

(defn ui-scale
  [game-state]
  (double (get-in @game-state [:ui :scale] 2.0)))

(defn set-ui-scale!
  [game-state value]
  (swap! game-state assoc-in [:ui :scale]
         (-> value double (max 1.0) (min 3.0))))

(defn ui-viewport
  [game-state]
  (get-in @game-state [:ui :viewport]))

(defn set-ui-viewport!
  [game-state {:keys [width height]}]
  (swap! game-state assoc-in [:ui :viewport]
         {:width (double (or width 0.0))
          :height (double (or height 0.0))}))

(defn star-inspector-visible?
  [game-state]
  (boolean (get-in @game-state [:ui :star-inspector :visible?] false)))

(defn set-star-inspector-visible!
  [game-state value]
  (swap! game-state assoc-in [:ui :star-inspector :visible?] (boolean value)))

(defn show-star-inspector!
  [game-state]
  (set-star-inspector-visible! game-state true))

(defn hide-star-inspector!
  [game-state]
  (set-star-inspector-visible! game-state false))

(defn hyperlane-panel-expanded?
  [game-state]
  (boolean (get-in @game-state [:ui :hyperlane-panel :expanded?] true)))

(defn toggle-hyperlane-panel!
  [game-state]
  (swap! game-state update-in [:ui :hyperlane-panel :expanded?]
         (fnil not true)))

(defn dropdown-open?
  [game-state dropdown-id]
  (boolean (get-in @game-state [:ui :dropdowns dropdown-id])))

(defn toggle-dropdown!
  [game-state dropdown-id]
  (when dropdown-id
    (swap! game-state update-in [:ui :dropdowns dropdown-id]
           (fnil not false))))

(defn close-dropdown!
  [game-state dropdown-id]
  (when dropdown-id
    (swap! game-state assoc-in [:ui :dropdowns dropdown-id] false)))

(defn- normalize-window-bounds
  [bounds defaults]
  (let [base (merge {:x 0.0 :y 0.0 :width 200.0 :height 200.0}
                    (or defaults {})
                    (or bounds {}))]
    {:x (double (or (:x base) 0.0))
     :y (double (or (:y base) 0.0))
     :width (double (max 1.0 (or (:width base) 1.0)))
     :height (double (max 1.0 (or (:height base) 1.0)))}))

(defn window-bounds
  "Return the stored bounds for the given window-id merged with defaults."
  [game-state window-id default-bounds]
  (let [stored (get-in @game-state [:ui :windows window-id :bounds])]
    (normalize-window-bounds stored default-bounds)))

(defn set-window-bounds!
  "Persist bounds for a window."
  [game-state window-id bounds]
  (when (and window-id (map? bounds))
    (let [normalized (normalize-window-bounds bounds nil)]
      (swap! game-state update-in [:ui :windows window-id]
             (fn [state]
               (assoc (or state {})
                      :bounds normalized))))))

(defn window-minimized?
  [game-state window-id]
  (boolean (get-in @game-state [:ui :windows window-id :minimized?] false)))

(defn toggle-window-minimized!
  [game-state window-id]
  (when window-id
    (swap! game-state update-in [:ui :windows window-id :minimized?]
           (fnil not false))))

(defn set-window-minimized!
  [game-state window-id value]
  (when window-id
    (swap! game-state assoc-in [:ui :windows window-id :minimized?] (boolean value))))

(defn performance-overlay-visible?
  [game-state]
  (boolean (get-in @game-state [:ui :performance-overlay :visible?] true)))

(defn toggle-performance-overlay-visible!
  [game-state]
  (swap! game-state update-in [:ui :performance-overlay :visible?]
         (fnil not true)))

(defn performance-overlay-expanded?
  [game-state]
  (boolean (get-in @game-state [:ui :performance-overlay :expanded?] true)))

(defn toggle-performance-overlay-expanded!
  [game-state]
  (swap! game-state update-in [:ui :performance-overlay :expanded?]
         (fnil not true)))

(defn reset-performance-metrics!
  [game-state]
  (swap! game-state assoc-in [:metrics :performance] default-performance-metrics))

(defn record-performance-metrics!
  [game-state metrics]
  (swap! game-state update :metrics
         (fn [metrics-state]
           (let [metrics-state (or metrics-state {})
                 performance (merge default-performance-metrics
                                    (:performance metrics-state))
                 fps (:fps metrics)
                 frame-time (:frame-time-ms metrics)
                 updated (-> performance
                             (assoc :latest metrics)
                             (assoc :last-sample-time (double (or (:current-time metrics) 0.0)))
                             (update :fps-history append-sample fps)
                             (update :frame-time-history append-sample frame-time))]
             (assoc metrics-state :performance updated)))))

;; =============================================================================
;; State Updaters
;; =============================================================================

(defn update-camera!
  "Update camera state using function f"
  [game-state f & args]
  (swap! game-state update :camera #(apply f % args)))

(defn focus-camera-on-world!
  "Center the camera pan on the supplied world coordinate (map with :x/:y).
  Optionally accepts {:zoom value} to compute pan for the supplied zoom."
  ([game-state position]
   (focus-camera-on-world! game-state position {}))
  ([game-state {:keys [x y]} {:keys [zoom]}]
   (when (and (number? x) (number? y))
     (let [viewport (ui-viewport game-state)
           width (double (max 1.0 (or (:width viewport) 1280.0)))
           height (double (max 1.0 (or (:height viewport) 720.0)))
           camera (get-camera game-state)
           zoom-value (double (or zoom (:zoom camera) 1.0))
           pan-x (camera/center-pan (double x) zoom-value width)
           pan-y (camera/center-pan (double y) zoom-value height)]
       (update-camera! game-state assoc :pan-x pan-x :pan-y pan-y)
       {:pan-x pan-x
        :pan-y pan-y}))))

(defn zoom-to-star!
  "Focus the camera on the given star-id and optionally set zoom via {:zoom v}."
  ([game-state star-id]
   (zoom-to-star! game-state star-id {}))
  ([game-state star-id {:keys [zoom]}]
   (when-let [star (get-entity game-state star-id)]
     (when-let [pos (get-component star :position)]
       (let [camera (get-camera game-state)
             requested (if (number? zoom)
                         (double zoom)
                         (max 2.0 (double (or (:zoom camera) 1.0))))
             clamped (-> requested
                         (max 0.4)
                         (min 10.0))]
         (focus-camera-on-world! game-state pos {:zoom clamped})
         (update-camera! game-state assoc :zoom clamped)
         clamped)))))

(defn zoom-to-selection!
  "Zoom and pan the camera to the currently selected star."
  ([game-state]
   (zoom-to-selection! game-state {}))
  ([game-state opts]
   (when-let [star-id (selected-star-id game-state)]
     (zoom-to-star! game-state star-id opts))))

(defn update-input!
  "Update input state using function f"
  [game-state f & args]
  (swap! game-state update :input #(apply f % args)))

(defn update-time!
  "Update time state using function f"
  [game-state f & args]
  (swap! game-state update :time #(apply f % args)))

(defn set-assets!
  "Set the assets in game state"
  [game-state assets]
  (swap! game-state assoc :assets assets))

(defn toggle-hyperlanes!
  "Toggle hyperlane visibility flag"
  [game-state]
  (let [current (hyperlanes-enabled? game-state)]
    (set-hyperlane-setting! game-state :enabled? (not current))))

(defn toggle-minimap!
  "Toggle minimap visibility flag"
  [game-state]
  (swap! game-state update-in [:features :minimap?] not))

;; =============================================================================
;; Render State Accessors
;; =============================================================================

(defn get-window
  "Get window from render state"
  [render-state]
  (:window @render-state))

(defn get-context
  "Get DirectContext from render state"
  [render-state]
  (:context @render-state))

(defn get-surface
  "Get surface from render state"
  [render-state]
  (:surface @render-state))

;; =============================================================================
;; Render State Updaters
;; =============================================================================

(defn set-window!
  "Set window in render state"
  [render-state window]
  (swap! render-state assoc :window window))

(defn set-context!
  "Set DirectContext in render state"
  [render-state context]
  (swap! render-state assoc :context context))

(defn set-surface!
  "Set surface in render state"
  [render-state surface]
  (swap! render-state assoc :surface surface))
