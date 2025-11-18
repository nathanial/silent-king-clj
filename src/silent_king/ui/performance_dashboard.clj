(ns silent-king.ui.performance-dashboard
  "Phase 5 performance dashboard overlay with draggable header, collapsible body, and metrics."
  (:require [silent-king.state :as state]
            [silent-king.widgets.core :as wcore]
            [silent-king.widgets.config :as wconfig]
            [silent-king.widgets.minimap :as wminimap]))

(set! *warn-on-reflection* true)

;; =============================================================================
;; Widget IDs
;; =============================================================================

(def ^:private panel-id :performance-dashboard-panel)
(def ^:private header-id :performance-dashboard-header)
(def ^:private body-id :performance-dashboard-body)
(def ^:private title-label-id :performance-dashboard-title)
(def ^:private fps-label-id :performance-dashboard-fps)
(def ^:private pin-button-id :performance-dashboard-pin)
(def ^:private expand-button-id :performance-dashboard-expand)
(def ^:private fps-chart-id :performance-dashboard-fps-chart)
(def ^:private frame-time-chart-id :performance-dashboard-frame-time-chart)
(def ^:private stats-stack-id :performance-dashboard-stats-stack)
(def ^:private stars-label-id :performance-dashboard-stars)
(def ^:private hyperlanes-label-id :performance-dashboard-hyperlanes)
(def ^:private widgets-label-id :performance-dashboard-widgets)
(def ^:private draw-calls-label-id :performance-dashboard-draw-calls)
(def ^:private memory-label-id :performance-dashboard-memory)

;; =============================================================================
;; Layout Constants
;; =============================================================================

(def ^:private panel-width 320.0)
(def ^:private header-height 48.0)
(def ^:private default-collapsed-height 60.0)
(def ^:private default-expanded-height 320.0)
(def ^:private margin 12.0)
(def ^:private viewport-margin 16.0)  ;; Minimum margin from viewport edge
(def ^:private gap 8.0)
(def ^:private chart-height 80.0)
(def ^:private label-height 24.0)

;; =============================================================================
;; Helper Functions - State Access
;; =============================================================================

(defn- dashboard-state
  "Get the performance dashboard state from game-state."
  [game-state]
  (get-in @game-state [:ui :performance-dashboard]))

(defn- update-ui!
  "Update the performance dashboard UI state."
  [game-state f & args]
  (swap! game-state update-in [:ui :performance-dashboard] #(apply f % args)))

;; =============================================================================
;; Helper Functions - Widget Space
;; =============================================================================

(defn- widget-space-width
  "Compute viewport width in widget units."
  [game-state]
  (let [viewport-size (wminimap/get-viewport-size game-state)]
    (/ (:width viewport-size) wconfig/ui-scale)))

(defn- widget-space-height
  "Compute viewport height in widget units."
  [game-state]
  (let [viewport-size (wminimap/get-viewport-size game-state)]
    (/ (:height viewport-size) wconfig/ui-scale)))

;; =============================================================================
;; Helper Functions - Position Clamping
;; =============================================================================

(defn- initial-position
  "Compute the initial position for the dashboard (bottom-left corner)."
  [game-state]
  (let [state (dashboard-state game-state)
        collapsed-height (or (:collapsed-height state) default-collapsed-height)
        vw (widget-space-width game-state)
        vh (widget-space-height game-state)
        x viewport-margin
        y (- vh collapsed-height viewport-margin)]
    {:x x :y y}))

(defn- clamp-position
  "Clamp panel position to stay within viewport bounds, respecting margins even when viewport is smaller than panel."
  [game-state x y]
  (let [vw (widget-space-width game-state)
        vh (widget-space-height game-state)
        ;; Respect margins even if viewport is smaller than panel
        max-x (max viewport-margin (- vw panel-width viewport-margin))
        max-y (max viewport-margin (- vh header-height viewport-margin))
        clamped-x (-> x (max viewport-margin) (min max-x))
        clamped-y (-> y (max viewport-margin) (min max-y))]
    {:x clamped-x :y clamped-y}))

(defn- apply-panel-position!
  "Update panel bounds to the given position using stored entity ID."
  [game-state x y]
  (when-let [panel-entity-id (:panel-entity (dashboard-state game-state))]
    (let [clamped (clamp-position game-state x y)]
      (state/update-entity! game-state panel-entity-id
                           #(assoc-in % [:components :bounds :x] (:x clamped)))
      (state/update-entity! game-state panel-entity-id
                           #(assoc-in % [:components :bounds :y] (:y clamped)))
      (update-ui! game-state assoc :position clamped)
      (wcore/request-layout! game-state panel-entity-id))))

(defn- ensure-panel-in-viewport!
  "Ensure the panel stays within viewport bounds (useful after window resize)."
  [game-state]
  (let [position (:position (dashboard-state game-state))]
    (when position
      (apply-panel-position! game-state (:x position) (:y position)))))

(defn- drag-panel!
  "Handle panel dragging by updating position, ignoring drags when pinned."
  [game-state x y]
  (let [pinned? (:pinned? (dashboard-state game-state))]
    (when-not pinned?
      (apply-panel-position! game-state x y))))

;; =============================================================================
;; Helper Functions - Widget Tree
;; =============================================================================

(defn- set-children!
  "Wire up parent/child relationships for a widget and its children."
  [game-state parent-id child-ids]
  ;; Update parent to reference children
  (state/update-entity! game-state parent-id
                       #(assoc-in % [:components :widget :children] child-ids))
  ;; Update children to reference parent
  (doseq [child-id child-ids]
    (state/update-entity! game-state child-id
                         #(assoc-in % [:components :widget :parent-id] parent-id)))
  ;; Request layout recalculation
  (wcore/request-layout! game-state parent-id))

;; =============================================================================
;; Helper Functions - Update Widgets
;; =============================================================================

(defn- update-expand-button!
  "Update the expand button text based on expanded state."
  [game-state]
  (when-let [[button-entity-id _] (wcore/get-widget-by-id game-state expand-button-id)]
    (let [expanded? (:expanded? (dashboard-state game-state))
          text (if expanded? "[-]" "[+]")]
      (state/update-entity! game-state button-entity-id
                           #(assoc-in % [:components :visual :text] text)))))

(defn- update-pin-button!
  "Update the pin button text based on pinned state."
  [game-state]
  (when-let [[button-entity-id _] (wcore/get-widget-by-id game-state pin-button-id)]
    (let [pinned? (:pinned? (dashboard-state game-state))
          text (if pinned? "UNPIN" "PIN")]
      (state/update-entity! game-state button-entity-id
                           #(assoc-in % [:components :visual :text] text)))))

;; =============================================================================
;; Helper Functions - Header Interaction
;; =============================================================================

(defn- set-header-draggable!
  "Enable or disable header drag interaction."
  [game-state draggable?]
  (when-let [header-entity-id (:header-entity (dashboard-state game-state))]
    (state/update-entity! game-state header-entity-id
                         #(assoc-in % [:components :interaction :enabled] draggable?))))

;; =============================================================================
;; Helper Functions - Expand/Pin
;; =============================================================================

(defn- set-expanded!
  "Set expanded state of the dashboard, adjusting panel height and body visibility."
  [game-state expanded?]
  (update-ui! game-state assoc :expanded? expanded?)

  ;; Adjust panel height based on expanded state
  (let [state (dashboard-state game-state)]
    (when-let [panel-entity-id (:panel-entity state)]
      (let [collapsed-height (or (:collapsed-height state) default-collapsed-height)
            expanded-height (or (:expanded-height state) default-expanded-height)
            new-height (if expanded? expanded-height collapsed-height)]
        (state/update-entity! game-state panel-entity-id
                             #(assoc-in % [:components :bounds :height] new-height))))

    ;; Toggle body visibility
    (when-let [body-entity-id (:body-entity state)]
      (wcore/set-visibility! game-state body-entity-id expanded? true))

    ;; Request layout and update expand button
    (when-let [panel-entity-id (:panel-entity state)]
      (wcore/request-layout! game-state panel-entity-id)))

  (update-expand-button! game-state))

(defn- toggle-expanded!
  "Toggle the expanded state."
  [game-state]
  (let [current (:expanded? (dashboard-state game-state))]
    (set-expanded! game-state (not current))))

(defn- toggle-pin!
  "Toggle the pinned state and update header interaction."
  [game-state]
  (let [current (:pinned? (dashboard-state game-state))
        new-pinned? (not current)]
    ;; Update UI state
    (update-ui! game-state assoc :pinned? new-pinned?)

    ;; Disable dragging when pinned, enable when unpinned
    (set-header-draggable! game-state (not new-pinned?))

    ;; Update pin button label
    (update-pin-button! game-state)))

(defn- update-fps-label!
  "Update the FPS label in the header."
  [game-state fps]
  (when-let [[label-entity-id _] (wcore/get-widget-by-id game-state fps-label-id)]
    (let [text (format "%.1f FPS" (double fps))]
      (state/update-entity! game-state label-entity-id
                           #(assoc-in % [:components :visual :text] text)))))

(defn- update-stat-labels!
  "Update all stat labels with current metrics."
  [game-state metrics]
  ;; Stars label
  (when-let [[label-entity-id _] (wcore/get-widget-by-id game-state stars-label-id)]
    (let [total (:total-stars metrics 0)
          visible (:visible-stars metrics 0)
          text (format "Stars: %d (visible %d)" total visible)]
      (state/update-entity! game-state label-entity-id
                           #(assoc-in % [:components :visual :text] text))))

  ;; Hyperlanes label
  (when-let [[label-entity-id _] (wcore/get-widget-by-id game-state hyperlanes-label-id)]
    (let [total (:hyperlane-count metrics 0)
          visible (:visible-hyperlanes metrics 0)
          text (format "Hyperlanes: %d (visible %d)" total visible)]
      (state/update-entity! game-state label-entity-id
                           #(assoc-in % [:components :visual :text] text))))

  ;; Widgets label
  (when-let [[label-entity-id _] (wcore/get-widget-by-id game-state widgets-label-id)]
    (let [count (:widget-count metrics 0)
          text (format "Widgets: %d" count)]
      (state/update-entity! game-state label-entity-id
                           #(assoc-in % [:components :visual :text] text))))

  ;; Draw calls label
  (when-let [[label-entity-id _] (wcore/get-widget-by-id game-state draw-calls-label-id)]
    (let [count (:draw-calls metrics 0)
          text (format "Draw calls: %d" count)]
      (state/update-entity! game-state label-entity-id
                           #(assoc-in % [:components :visual :text] text))))

  ;; Memory label
  (when-let [[label-entity-id _] (wcore/get-widget-by-id game-state memory-label-id)]
    (let [mb (:memory-mb metrics 0.0)
          text (format "Memory: %.1f MB" (double mb))]
      (state/update-entity! game-state label-entity-id
                           #(assoc-in % [:components :visual :text] text)))))

;; =============================================================================
;; Helper Functions - History Management
;; =============================================================================

(defn- push-history
  "Push a value onto a history vector, maintaining the history limit."
  [history value limit]
  (let [updated (conj (vec history) value)]
    (if (> (count updated) limit)
      (subvec updated (- (count updated) limit))
      updated)))

(defn- update-chart-values!
  "Update chart widget values."
  [game-state chart-id points]
  (when-let [[chart-entity-id _] (wcore/get-widget-by-id game-state chart-id)]
    (state/update-entity! game-state chart-entity-id
                         #(assoc-in % [:components :value :points] (vec points)))))

;; =============================================================================
;; Dashboard Creation
;; =============================================================================

(defn create-performance-dashboard!
  "Build the performance dashboard widget tree."
  [game-state]
  (let [state (dashboard-state game-state)
        collapsed-height (or (:collapsed-height state) default-collapsed-height)
        expanded-height (or (:expanded-height state) default-expanded-height)
        initial-expanded? (:expanded? state)
        initial-height (if initial-expanded? expanded-height collapsed-height)
        ;; Compute starting position (use stored position or calculate initial position)
        stored-position (:position state)
        start-position (or stored-position (initial-position game-state))
        clamped-start (clamp-position game-state (:x start-position) (:y start-position))

        ;; Create header widgets
        title-label (wcore/label "Performance"
                                 :id title-label-id
                                 :bounds {:width 120 :height 32}
                                 :visual {:font-size 16
                                         :font-weight :bold
                                         :text-color 0xFFFFFFFF})

        fps-label (wcore/label "0.0 FPS"
                               :id fps-label-id
                               :bounds {:width 80 :height 32}
                               :visual {:font-size 14
                                       :text-color 0xFF3DD598
                                       :text-align :right})

        pin-button (wcore/button "PIN"
                                 #(do (toggle-pin! game-state)
                                      (update-pin-button! game-state))
                                 :id pin-button-id
                                 :bounds {:width 60 :height 32}
                                 :visual {:background-color 0xFF444444
                                         :text-color 0xFFFFFFFF
                                         :font-size 12
                                         :border-radius 4.0})

        expand-button (wcore/button (if initial-expanded? "[-]" "[+]")
                                    #(do (toggle-expanded! game-state)
                                         (update-expand-button! game-state))
                                    :id expand-button-id
                                    :bounds {:width 40 :height 32}
                                    :visual {:background-color 0xFF444444
                                            :text-color 0xFFFFFFFF
                                            :font-size 12
                                            :border-radius 4.0})

        ;; Create header stack
        header (wcore/hstack
                :id header-id
                :bounds {:width panel-width :height header-height}
                :layout {:padding {:all 8}
                        :gap 8
                        :align :center}
                :visual {:background-color 0xDD1A1A1A
                        :border-radius 8.0}
                :interaction {:draggable? true
                             :enabled true
                             :hover-cursor :move
                             :on-drag (fn [x y] (drag-panel! game-state x y))})

        ;; Create body widgets (charts and stats)
        fps-chart (wcore/line-chart
                   :id fps-chart-id
                   :bounds {:width (- panel-width (* 2 margin)) :height chart-height}
                   :value {:label "FPS"
                          :unit "fps"
                          :points []
                          :max-points 60
                          :grid-lines 4})

        frame-time-chart (wcore/line-chart
                          :id frame-time-chart-id
                          :bounds {:width (- panel-width (* 2 margin)) :height chart-height}
                          :value {:label "Frame Time"
                                 :unit "ms"
                                 :points []
                                 :max-points 60
                                 :grid-lines 4})

        ;; Stat labels
        stars-label (wcore/label "Stars: 0 (visible 0)"
                                 :id stars-label-id
                                 :bounds {:width (- panel-width (* 2 margin)) :height label-height}
                                 :visual {:font-size 12
                                         :text-color 0xFFCCCCCC})

        hyperlanes-label (wcore/label "Hyperlanes: 0 (visible 0)"
                                      :id hyperlanes-label-id
                                      :bounds {:width (- panel-width (* 2 margin)) :height label-height}
                                      :visual {:font-size 12
                                              :text-color 0xFFCCCCCC})

        widgets-label (wcore/label "Widgets: 0"
                                   :id widgets-label-id
                                   :bounds {:width (- panel-width (* 2 margin)) :height label-height}
                                   :visual {:font-size 12
                                           :text-color 0xFFCCCCCC})

        draw-calls-label (wcore/label "Draw calls: 0"
                                      :id draw-calls-label-id
                                      :bounds {:width (- panel-width (* 2 margin)) :height label-height}
                                      :visual {:font-size 12
                                              :text-color 0xFFCCCCCC})

        memory-label (wcore/label "Memory: 0.0 MB"
                                  :id memory-label-id
                                  :bounds {:width (- panel-width (* 2 margin)) :height label-height}
                                  :visual {:font-size 12
                                          :text-color 0xFFCCCCCC})

        ;; Stats stack
        stats-stack (wcore/vstack
                     :id stats-stack-id
                     :layout {:padding {:all 0}
                             :gap 4
                             :align :stretch})

        ;; Body container
        body (wcore/vstack
              :id body-id
              :bounds {:width panel-width :height (max 0 (- expanded-height header-height))}
              :layout {:padding {:all margin}
                      :gap gap
                      :align :stretch}
              :visual {:background-color 0xDD222222}
              :visible? initial-expanded?)

        ;; Main panel
        panel (wcore/panel
               :id panel-id
               :bounds {:x (:x clamped-start) :y (:y clamped-start) :width panel-width :height initial-height}
               :layout {:z-index 100}
               :visual {:background-color 0x00000000  ;; Transparent, children have backgrounds
                       :border-radius 8.0
                       :shadow {:offset-x 0 :offset-y 4 :blur 16 :color 0xA0000000}})]

    ;; Add widgets to game state
    (let [panel-entity-id (wcore/add-widget! game-state panel)
          header-entity-id (wcore/add-widget! game-state header)
          body-entity-id (wcore/add-widget! game-state body)

          ;; Header children
          title-entity-id (wcore/add-widget! game-state title-label)
          fps-label-entity-id (wcore/add-widget! game-state fps-label)
          pin-button-entity-id (wcore/add-widget! game-state pin-button)
          expand-button-entity-id (wcore/add-widget! game-state expand-button)

          ;; Body children
          fps-chart-entity-id (wcore/add-widget! game-state fps-chart)
          frame-time-chart-entity-id (wcore/add-widget! game-state frame-time-chart)
          stats-stack-entity-id (wcore/add-widget! game-state stats-stack)

          ;; Stats children
          stars-label-entity-id (wcore/add-widget! game-state stars-label)
          hyperlanes-label-entity-id (wcore/add-widget! game-state hyperlanes-label)
          widgets-label-entity-id (wcore/add-widget! game-state widgets-label)
          draw-calls-label-entity-id (wcore/add-widget! game-state draw-calls-label)
          memory-label-entity-id (wcore/add-widget! game-state memory-label)]

      ;; Wire up parent-child relationships
      (set-children! game-state panel-entity-id [header-entity-id body-entity-id])
      (set-children! game-state header-entity-id [title-entity-id fps-label-entity-id pin-button-entity-id expand-button-entity-id])
      (set-children! game-state body-entity-id [fps-chart-entity-id frame-time-chart-entity-id stats-stack-entity-id])
      (set-children! game-state stats-stack-entity-id [stars-label-entity-id hyperlanes-label-entity-id widgets-label-entity-id draw-calls-label-entity-id memory-label-entity-id])

      ;; Store entity references in UI state
      (update-ui! game-state assoc
                  :panel-entity panel-entity-id
                  :header-entity header-entity-id
                  :body-entity body-entity-id
                  :expand-button-entity expand-button-entity-id
                  :pin-button-entity pin-button-entity-id
                  :fps-label-entity fps-label-entity-id)

      ;; Update stored position to clamped start position
      (update-ui! game-state assoc :position clamped-start)

      ;; Set initial expanded state (ensures visibility matches :expanded?)
      (set-expanded! game-state initial-expanded?)

      ;; Initialize header draggability and pin button to match current state
      (let [current-state (dashboard-state game-state)
            pinned? (:pinned? current-state)]
        (set-header-draggable! game-state (not pinned?))
        (update-pin-button! game-state))

      (println "Performance dashboard created")
      panel-entity-id)))

;; =============================================================================
;; Dashboard Update
;; =============================================================================

(defn update-dashboard!
  "Update the dashboard with current metrics.

  Expected metrics map keys:
  - :fps - Current frames per second
  - :frame-time-ms - Current frame time in ms (fallback to previous history when missing)
  - :total-stars - Total number of stars
  - :visible-stars - Number of visible stars
  - :hyperlane-count - Total hyperlanes
  - :visible-hyperlanes - Visible hyperlanes
  - :widget-count - Number of widgets
  - :draw-calls - Number of draw calls
  - :memory-mb - Memory usage in MB
  - :current-time - Current time (for throttling)"
  [game-state metrics]
  ;; Exit early if panel hasn't been created
  (let [state (dashboard-state game-state)]
    (when (:panel-entity state)
      (let [fps (or (:fps metrics) 0.0)
            history-limit (max 1 (int (or (:history-limit state) 60)))

            ;; Get previous histories
            fps-history (get-in @game-state [:metrics :performance :fps-history] [])
            frame-time-history (get-in @game-state [:metrics :performance :frame-time-history] [])

            ;; Compute frame-time from :frame-time-ms metric, fall back to previous history when missing
            frame-time (double (or (:frame-time-ms metrics)
                                   (when (seq frame-time-history)
                                     (last frame-time-history))
                                   0.0))

            ;; Update histories
            new-fps-history (push-history fps-history fps history-limit)
            new-frame-time-history (push-history frame-time-history frame-time history-limit)
            current-time (double (or (:current-time metrics)
                                     (:current-time (state/get-time game-state))
                                     0.0))

            ;; Normalize metrics with computed frame-time
            normalized-metrics (assoc metrics :frame-time-ms frame-time)]

        ;; Refresh stored :metrics :performance map in one swap
        (swap! game-state update-in [:metrics :performance]
               (fn [perf-metrics]
                 (assoc perf-metrics
                        :fps-history new-fps-history
                        :frame-time-history new-frame-time-history
                        :last-sample-time current-time
                        :latest normalized-metrics)))

        ;; Update FPS label
        (update-fps-label! game-state fps)

        ;; Update charts
        (update-chart-values! game-state fps-chart-id new-fps-history)
        (update-chart-values! game-state frame-time-chart-id new-frame-time-history)

        ;; Update stat labels
        (update-stat-labels! game-state normalized-metrics)

        ;; Ensure panel stays in viewport after resizing
        (ensure-panel-in-viewport! game-state)))))
