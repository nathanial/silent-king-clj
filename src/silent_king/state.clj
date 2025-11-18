(ns silent-king.state
  "Game state management with entity-component system")

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
           :dragging false}
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
   :selection {:star-id nil
               :last-world-click nil
               :details nil}
   :ui {}
   :features {:hyperlanes? true
              :minimap? true}
   :hyperlane-settings default-hyperlane-settings
   :metrics {:performance {:fps-history []
                           :frame-time-history []
                           :last-sample-time 0.0
                           :latest {}}}})

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

;; =============================================================================
;; State Updaters
;; =============================================================================

(defn update-camera!
  "Update camera state using function f"
  [game-state f & args]
  (swap! game-state update :camera #(apply f % args)))

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
