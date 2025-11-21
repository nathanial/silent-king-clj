(ns silent-king.state
  "Game state management and world data helpers"
  (:require [silent-king.camera :as camera]))

(set! *warn-on-reflection* true)

(def ^:const ^long relax-iterations-limit 5)

(defn- clamp
  [value min-value max-value]
  (-> value double (max min-value) (min max-value)))

;; =============================================================================
;; World ID Generation
;; =============================================================================

(defn next-star-id!
  "Generate and store the next star id. Starts at 1."
  [game-state]
  (let [next-id (inc (long (get @game-state :next-star-id 0)))]
    (swap! game-state assoc :next-star-id next-id)
    next-id))

(def ^:private control-panel-window-id :ui/control-panel)
(def ^:private performance-window-id :ui/performance-overlay)
(def ^:private star-inspector-window-id :ui/star-inspector)
(def ^:private minimap-window-id :ui/minimap)
(def ^:private galaxy-window-id :ui/galaxy)

(def default-window-order
  [galaxy-window-id
   minimap-window-id
   control-panel-window-id
   performance-window-id
   star-inspector-window-id])

(defn next-planet-id!
  "Generate and store the next planet id. Starts at 1."
  [game-state]
  (let [next-id (inc (long (get @game-state :next-planet-id 0)))]
    (swap! game-state assoc :next-planet-id next-id)
    next-id))

(defn next-hyperlane-id!
  "Generate and store the next hyperlane id. Starts at 1."
  [game-state]
  (let [next-id (inc (long (get @game-state :next-hyperlane-id 0)))]
    (swap! game-state assoc :next-hyperlane-id next-id)
    next-id))

(defn reset-world-ids!
  "Reset star and hyperlane id counters (testing convenience)."
  [game-state]
  (swap! game-state assoc
         :next-star-id 0
         :next-planet-id 0
         :next-hyperlane-id 0))

(def default-hyperlane-settings
  {:enabled? true
   :opacity 0.9
   :color-scheme :blue
   :animation? true
   :animation-speed 1.0
   :line-width 1.0})

(def default-voronoi-settings
  {:enabled? true
   :opacity 0.5
   :line-width 2.0
   :color-scheme :by-region
   :show-centroids? false
   :hide-border-cells? false
   :relax-iterations 0
   :relax-step 1.0
   :relax-max-displacement 250.0
   :relax-clip-to-envelope? true})

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

(defn create-game-state
  "Create initial game state structure."
  []
  {:stars {}
   :planets {}
   :hyperlanes []
   :voronoi-cells {}
   :regions {}
   :neighbors-by-star-id {}
   :next-star-id 0
   :next-planet-id 0
   :next-hyperlane-id 0
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
            :atlas-size-lg 8192
            :planet-atlas-image-medium nil
            :planet-atlas-metadata-medium {}
            :planet-atlas-size-medium 4096
            :star-images []}
   :widgets {:layout-dirty #{}}
   :selection default-selection
   :ui {:scale 2.0
        :star-inspector {:visible? false
                         :pinned? false}
        :hyperlane-panel {:expanded? true}
        :voronoi-panel {:expanded? true}
        :viewport {:width 0.0
                   :height 0.0}
        :windows {}
        :window-order default-window-order
        :performance-overlay {:visible? true
                              :expanded? true}
        :dropdowns {}}
   :features {:stars-and-planets? true
              :hyperlanes? true
              :voronoi? (:enabled? default-voronoi-settings)
              :minimap? true}
   :hyperlane-settings default-hyperlane-settings
   :voronoi-settings default-voronoi-settings
   :voronoi-generated? false
   :metrics {:performance default-performance-metrics}})

(defn create-render-state
  "Create initial render state structure"
  []
  {:window nil
   :context nil
   :surface nil})

;; =============================================================================
;; World Accessors & Mutators
;; =============================================================================

(defn stars
  "Return the map of stars keyed by id."
  [game-state]
  (:stars @game-state))

(defn planets
  "Return the map of planets keyed by id."
  [game-state]
  (:planets @game-state))

(defn star-seq
  "Return a sequence of star maps."
  [game-state]
  (vals (stars game-state)))

(defn planet-seq
  "Return a sequence of planet maps."
  [game-state]
  (vals (planets game-state)))

(defn star-by-id
  "Lookup a star by id."
  [game-state star-id]
  (get (:stars @game-state) star-id))

(defn planets-by-star-id
  "Return a map of star-id -> vector of planets orbiting that star."
  [game-state]
  (reduce (fn [acc {:keys [star-id] :as planet}]
            (if star-id
              (update acc star-id (fnil conj []) planet)
              acc))
          {}
          (planet-seq game-state)))

(defn hyperlanes
  "Return the vector of hyperlanes."
  [game-state]
  (:hyperlanes @game-state))

(defn voronoi-cells
  "Return the map of voronoi cells keyed by star id."
  [game-state]
  (:voronoi-cells @game-state))

(defn regions
  "Return the map of regions keyed by region id."
  [game-state]
  (:regions @game-state))

(defn neighbors-by-star-id
  "Return adjacency map of star id -> neighbors."
  [game-state]
  (:neighbors-by-star-id @game-state))

(defn set-stars!
  "Replace stars map on game-state."
  [game-state stars-map]
  (swap! game-state assoc :stars (or stars-map {})))

(defn set-planets!
  "Replace planets map on game-state."
  [game-state planets-map]
  (swap! game-state assoc :planets (or planets-map {})))

(defn set-hyperlanes!
  "Replace hyperlanes vector on game-state."
  [game-state hyperlanes-vector]
  (swap! game-state assoc :hyperlanes (vec (or hyperlanes-vector []))))

(defn set-voronoi-cells!
  "Replace voronoi cells map on game-state."
  [game-state cells]
  (swap! game-state assoc :voronoi-cells (or cells {})))

(defn set-regions!
  "Replace regions map on game-state."
  [game-state regions]
  (swap! game-state assoc :regions (or regions {})))

(defn set-neighbors!
  "Replace neighbors-by-star-id map on game-state."
  [game-state neighbors]
  (swap! game-state assoc :neighbors-by-star-id (or neighbors {})))

(defn set-world!
  "Replace world data on game-state with supplied values."
  [game-state {:keys [stars planets hyperlanes voronoi-cells neighbors-by-star-id next-star-id next-planet-id next-hyperlane-id voronoi-generated?]}]
  (swap! game-state
         (fn [state]
           (-> state
               (assoc :stars (or stars {}))
               (assoc :planets (or planets {}))
               (assoc :hyperlanes (vec (or hyperlanes [])))
               (assoc :voronoi-cells (or voronoi-cells {}))
               (assoc :regions {})
               (assoc :voronoi-generated? (boolean voronoi-generated?))
               (assoc :neighbors-by-star-id (or neighbors-by-star-id {}))
               (assoc :next-star-id (long (or next-star-id 0)))
               (assoc :next-planet-id (long (or next-planet-id 0)))
               (assoc :next-hyperlane-id (long (or next-hyperlane-id 0)))))))

(defn add-star!
  "Insert a star map, assigning an id if missing. Returns id."
  [game-state star]
  (let [id (or (:id star) (next-star-id! game-state))
        star* (assoc star :id id)]
    (swap! game-state assoc-in [:stars id] star*)
    id))

(defn add-planet!
  "Insert a planet map, assigning an id if missing. Returns id."
  [game-state planet]
  (let [id (or (:id planet) (next-planet-id! game-state))
        planet* (assoc planet :id id)]
    (swap! game-state assoc-in [:planets id] planet*)
    id))

(defn add-hyperlane!
  "Insert a hyperlane map, assigning an id if missing. Returns id."
  [game-state hyperlane]
  (let [id (or (:id hyperlane) (next-hyperlane-id! game-state))
        hyperlane* (assoc hyperlane :id id)]
    (swap! game-state update :hyperlanes
           (fnil conj [])
           hyperlane*)
    id))

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

(defn voronoi-settings
  "Return merged voronoi settings."
  [game-state]
  (merge default-voronoi-settings
         (:voronoi-settings @game-state)))

(defn voronoi-relax-config
  "Return a sanitized relaxation config map for voronoi generation."
  [game-state]
  (let [settings (voronoi-settings game-state)
        iterations (-> (long (or (:relax-iterations settings) 0))
                       (max 0)
                       (min relax-iterations-limit))
        step (clamp (:relax-step settings 1.0) 0.0 1.0)
        max-d (when (number? (:relax-max-displacement settings))
                (Math/abs ^double (:relax-max-displacement settings)))
        clip? (if (contains? settings :relax-clip-to-envelope?)
                (boolean (:relax-clip-to-envelope? settings))
                true)]
    {:iterations iterations
     :step-factor step
     :max-displacement max-d
     :clip-to-envelope? clip?}))

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

(defn set-voronoi-setting!
  "Set a single voronoi setting (e.g., :opacity, :line-width)."
  [game-state key value]
  (swap! game-state update :voronoi-settings
         (fn [settings]
           (assoc (merge default-voronoi-settings settings) key value)))
  (when (= key :enabled?)
    (swap! game-state assoc-in [:features :voronoi?] (boolean value))))

(defn reset-voronoi-settings!
  "Reset voronoi settings to defaults."
  [game-state]
  (swap! game-state assoc :voronoi-settings default-voronoi-settings)
  (swap! game-state assoc-in [:features :voronoi?] (:enabled? default-voronoi-settings)))

(defn hyperlanes-enabled?
  "Return true if hyperlanes should be rendered"
  [game-state]
  (get (hyperlane-settings game-state) :enabled? true))

(defn voronoi-enabled?
  "Return true if the voronoi overlay should be rendered"
  [game-state]
  (get (voronoi-settings game-state) :enabled? false))

(defn stars-and-planets-enabled?
  "Return true if stars and planets should be rendered"
  [game-state]
  (boolean (get-in @game-state [:features :stars-and-planets?] true)))

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

(defn voronoi-panel-expanded?
  [game-state]
  (boolean (get-in @game-state [:ui :voronoi-panel :expanded?] true)))

(defn toggle-voronoi-panel!
  [game-state]
  (swap! game-state update-in [:ui :voronoi-panel :expanded?]
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

(defn get-window-order
  [game-state]
  (or (get-in @game-state [:ui :window-order]) default-window-order))

(defn bring-window-to-front!
  [game-state window-id]
  (when window-id
    (swap! game-state update-in [:ui :window-order]
           (fn [order]
             (let [order (or order default-window-order)
                   others (filterv #(not= % window-id) order)]
               (conj others window-id))))))

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
   (when-let [star (star-by-id game-state star-id)]
     (let [camera (get-camera game-state)
           requested (if (number? zoom)
                       (double zoom)
                       (max 2.0 (double (or (:zoom camera) 1.0))))
           clamped (-> requested
                       (max 0.4)
                       (min 10.0))]
       (focus-camera-on-world! game-state {:x (:x star) :y (:y star)} {:zoom clamped})
       (update-camera! game-state assoc :zoom clamped)
       clamped))))

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

(defn toggle-voronoi!
  "Toggle voronoi overlay visibility flag"
  [game-state]
  (let [current (voronoi-enabled? game-state)]
    (set-voronoi-setting! game-state :enabled? (not current))))

(defn toggle-stars-and-planets!
  "Toggle rendering of both stars and planets"
  [game-state]
  (swap! game-state update-in [:features :stars-and-planets?] not))

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
