(ns silent-king.ui.hyperlane-settings
  "Collapsible hyperlane settings panel with advanced controls."
  (:require [silent-king.state :as state]
            [silent-king.widgets.core :as wcore]
            [silent-king.ui.specs :as ui-specs]
            [clojure.spec.alpha :as s]))

(set! *warn-on-reflection* true)

;; =============================================================================
;; Widget Identifiers & Configuration
;; =============================================================================

(def ^:private panel-id :hyperlane-settings-panel)
(def ^:private header-id :hyperlane-settings-header)
(def ^:private master-toggle-id :hyperlane-master-toggle)
(def ^:private color-dropdown-id :hyperlane-color-dropdown)
(def ^:private animation-toggle-id :hyperlane-animation-toggle)
(def ^:private opacity-row-id :hyperlane-opacity-row)
(def ^:private opacity-slider-id :hyperlane-opacity-slider)
(def ^:private speed-row-id :hyperlane-speed-row)
(def ^:private speed-slider-id :hyperlane-speed-slider)
(def ^:private width-row-id :hyperlane-width-row)
(def ^:private width-slider-id :hyperlane-width-slider)
(def ^:private reset-button-id :hyperlane-reset-button)

(def ^:private color-options
  [{:value :blue :label "Blue"}
   {:value :red :label "Red"}
   {:value :green :label "Green"}
   {:value :rainbow :label "Rainbow"}])

(def ^:private opacity-range {:min 0.0 :max 1.0 :step 0.05})
(def ^:private speed-range {:min 0.2 :max 2.5 :step 0.1})
(def ^:private width-range {:min 0.5 :max 2.5 :step 0.1})

;; =============================================================================
;; Internal Helpers
;; =============================================================================

(defn- panel-state
  [game-state]
  (get-in @game-state [:ui :hyperlane-settings]))

(defn- update-ui!
  [game-state path value]
  (swap! game-state
         (fn [gs]
           (let [updated (assoc-in gs (into [:ui :hyperlane-settings] path) value)
                 new-state (get-in updated [:ui :hyperlane-settings])]
             (when-not (s/valid? ::ui-specs/hyperlane-settings-state new-state)
               (throw (ex-info "Invalid hyperlane settings state after update"
                               {:path path
                                :value value
                                :explain (s/explain-str ::ui-specs/hyperlane-settings-state new-state)
                                :state new-state})))
             updated))))

(defn- populate-row!
  [game-state row-widget-id label-widget slider-widget]
  (when-let [[row-entity-id _] (wcore/get-widget-by-id game-state row-widget-id)]
    (let [child-ids (mapv #(wcore/add-widget! game-state %) [label-widget slider-widget])]
      (state/update-entity! game-state row-entity-id
                            #(assoc-in % [:components :widget :children] child-ids))
      (doseq [child-id child-ids]
        (state/update-entity! game-state child-id
                              #(assoc-in % [:components :widget :parent-id] row-entity-id)))
      (wcore/request-layout! game-state row-entity-id)
      row-entity-id)))

(defn- text-label
  [text]
  (wcore/label text
               :bounds {:width 120 :height 24}
               :visual {:text-color 0xFFAAAAAA
                        :font-size 14}))

(defn- update-header-label!
  [game-state expanded?]
  (when-let [[entity-id _] (wcore/get-widget-by-id game-state header-id)]
    (let [suffix (if expanded? "▾" "▸")
          text (str "Hyperlane Settings " suffix)]
      (state/update-entity! game-state entity-id
                            #(assoc-in % [:components :visual :text] text)))))

(defn- set-panel-expanded!
  [game-state expanded?]
  (let [time (:current-time (state/get-time game-state))
        target (if expanded? 1.0 0.0)]
    (update-ui! game-state [:expanded?] expanded?)
    (update-ui! game-state [:target] target)
    (update-ui! game-state [:last-update] time)
    (update-header-label! game-state expanded?)
    (when-not expanded?
      (when-let [[dropdown-id dropdown] (wcore/get-widget-by-id game-state color-dropdown-id)]
        (let [base-height (get-in dropdown [:components :value :base-height])]
          (state/update-entity! game-state dropdown-id
                                #(-> %
                                     (assoc-in [:components :value :expanded?] false)
                                     (assoc-in [:components :value :hover-index] nil)
                                     (assoc-in [:components :bounds :height] base-height)))
          (wcore/request-layout! game-state dropdown-id)
          (wcore/request-parent-layout! game-state dropdown-id))))))

(defn toggle-panel!
  "Toggle panel expansion state."
  [game-state]
  (let [expanded? (:expanded? (panel-state game-state))]
    (set-panel-expanded! game-state (not expanded?))))

(defn- update-toggle!
  [game-state widget-id value]
  (when-let [[entity-id _] (wcore/get-widget-by-id game-state widget-id)]
    (state/update-entity! game-state entity-id
                          #(assoc-in % [:components :value :checked?] (boolean value)))))

(defn- update-slider!
  [game-state widget-id value]
  (when-let [[entity-id _] (wcore/get-widget-by-id game-state widget-id)]
    (state/update-entity! game-state entity-id
                          #(assoc-in % [:components :value :current] (double value)))))

(defn- update-dropdown!
  [game-state widget-id value]
  (when-let [[entity-id _] (wcore/get-widget-by-id game-state widget-id)]
    (state/update-entity! game-state entity-id
                          #(assoc-in % [:components :value :selected] value))))

(defn- sync-controls!
  [game-state settings]
  (update-toggle! game-state master-toggle-id (:enabled? settings))
  (update-slider! game-state opacity-slider-id (:opacity settings))
  (update-dropdown! game-state color-dropdown-id (:color-scheme settings))
  (update-toggle! game-state animation-toggle-id (:animation? settings))
  (update-slider! game-state speed-slider-id (:animation-speed settings))
  (update-slider! game-state width-slider-id (:line-width settings)))

(defn- show-body!
  [game-state show?]
  (let [current (:body-visible? (panel-state game-state))]
    (when (not= current show?)
      (when-let [body-entities (:body-entities (panel-state game-state))]
        (doseq [entity-id body-entities]
          (wcore/set-visibility! game-state entity-id show?)))
      (update-ui! game-state [:body-visible?] show?))))

;; =============================================================================
;; Panel Creation
;; =============================================================================

(defn create-hyperlane-settings!
  "Create and mount the hyperlane settings UI panel."
  [game-state]
  (let [settings (state/hyperlane-settings game-state)
        ui-state (panel-state game-state)
        collapsed-height (:collapsed-height ui-state)
        expanded-height (:expanded-height ui-state)
        panel (wcore/panel
               :id panel-id
               :bounds {:x 20 :y 300 :width 320 :height collapsed-height}
               :visual {:background-color 0xCC1A1A1A
                        :border-radius 12.0
                        :shadow {:offset-x 0 :offset-y 4 :blur 10 :color 0x66000000}})
        header (wcore/button "Hyperlane Settings ▸"
                             #(toggle-panel! game-state)
                             :id header-id
                             :bounds {:width 280 :height 36}
                             :visual {:background-color 0xFF2A2A2A
                                      :border-radius 8.0
                                      :font-size 16
                                      :text-color 0xFFEEEEEE})
        master-toggle (wcore/toggle "Enable Hyperlanes"
                                    (:enabled? settings)
                                    #(state/set-hyperlane-setting! game-state :enabled? %)
                                    :id master-toggle-id
                                    :bounds {:width 280 :height 34})
        color-dropdown (wcore/dropdown color-options
                                       (:color-scheme settings)
                                       #(state/set-hyperlane-setting! game-state :color-scheme %)
                                       :id color-dropdown-id
                                       :bounds {:width 280 :height 38})
        animation-toggle (wcore/toggle "Enable Animation"
                                       (:animation? settings)
                                       #(state/set-hyperlane-setting! game-state :animation? %)
                                       :id animation-toggle-id
                                       :bounds {:width 280 :height 34})
        opacity-row (wcore/hstack
                     :id opacity-row-id
                     :bounds {:width 280 :height 48}
                     :layout {:padding {:top 4 :bottom 4 :left 4 :right 4}
                              :gap 12
                              :align :center})
        speed-row (wcore/hstack
                   :id speed-row-id
                   :bounds {:width 280 :height 48}
                   :layout {:padding {:top 4 :bottom 4 :left 4 :right 4}
                            :gap 12
                            :align :center})
        width-row (wcore/hstack
                   :id width-row-id
                   :bounds {:width 280 :height 48}
                   :layout {:padding {:top 4 :bottom 4 :left 4 :right 4}
                            :gap 12
                            :align :center})
        opacity-label (text-label "Opacity")
        opacity-slider (wcore/slider (:min opacity-range)
                                     (:max opacity-range)
                                     (:opacity settings)
                                     #(state/set-hyperlane-setting! game-state :opacity %)
                                     :id opacity-slider-id
                                     :bounds {:width 150 :height 24}
                                     :step (:step opacity-range))
        speed-label (text-label "Animation Speed")
        speed-slider (wcore/slider (:min speed-range)
                                   (:max speed-range)
                                   (:animation-speed settings)
                                   #(state/set-hyperlane-setting! game-state :animation-speed %)
                                   :id speed-slider-id
                                   :bounds {:width 150 :height 24}
                                   :step (:step speed-range))
        width-label (text-label "Line Width")
        width-slider (wcore/slider (:min width-range)
                                   (:max width-range)
                                   (:line-width settings)
                                   #(state/set-hyperlane-setting! game-state :line-width %)
                                   :id width-slider-id
                                   :bounds {:width 150 :height 24}
                                   :step (:step width-range))
        reset-button (wcore/button "Reset to Defaults"
                                   #(state/reset-hyperlane-settings! game-state)
                                   :id reset-button-id
                                   :bounds {:width 280 :height 32}
                                   :visual {:background-color 0xFF444444
                                            :border-radius 8.0
                                            :text-color 0xFFFFFFFF})]

    (let [panel-entity-id (wcore/add-widget-tree! game-state
                                                  panel
                                                  [header
                                                   master-toggle
                                                   color-dropdown
                                                   animation-toggle
                                                   opacity-row
                                                   speed-row
                                                   width-row
                                                   reset-button])]
      (populate-row! game-state opacity-row-id opacity-label opacity-slider)
      (populate-row! game-state speed-row-id speed-label speed-slider)
      (populate-row! game-state width-row-id width-label width-slider)

      (let [body-widget-ids [master-toggle-id color-dropdown-id animation-toggle-id
                             opacity-row-id speed-row-id width-row-id reset-button-id]
            body-entity-ids (->> body-widget-ids
                                 (keep #(wcore/get-widget-by-id game-state %))
                                 (map first)
                                 vec)
            header-entity (some-> (wcore/get-widget-by-id game-state header-id) first)]
        (swap! game-state update :ui
               (fn [ui]
                 (let [existing (:hyperlane-settings ui)]
                   (assoc ui :hyperlane-settings
                          (-> existing
                              (assoc :panel-entity panel-entity-id)
                              (assoc :header-entity header-entity)
                              (assoc :body-entities body-entity-ids)
                              (assoc :collapsed-height collapsed-height)
                              (assoc :expanded-height expanded-height)
                              (assoc :body-visible? false)
                              (assoc :progress 0.0)
                              (assoc :target 0.0)
                              (assoc :last-update (:current-time (state/get-time game-state)))
                              (assoc :last-settings settings))))))
        (doseq [entity-id body-entity-ids]
          (wcore/set-visibility! game-state entity-id false))
        (update-header-label! game-state false)
        (sync-controls! game-state settings)
        (println "Hyperlane settings panel created.")))))

;; =============================================================================
;; Per-frame Update
;; =============================================================================

(defn update!
  "Animate the panel and keep controls synced to game-state."
  [game-state]
  (let [ui (panel-state game-state)
        panel-entity (:panel-entity ui)]
    (when panel-entity
      (let [current-time (:current-time (state/get-time game-state))
            last-update (double (:last-update ui))
            progress (double (:progress ui))
            target (double (:target ui))
            delta (max 0.0 (- current-time last-update))
            speed (:animation-speed ui)
            step (* delta speed)
            new-progress (cond
                           (> target progress) (min target (+ progress step))
                           (< target progress) (max target (- progress step))
                           :else progress)]
        (when-not (= new-progress progress)
          (update-ui! game-state [:progress] new-progress)
          (update-ui! game-state [:last-update] current-time)
          (let [collapsed (:collapsed-height ui)
                expanded (:expanded-height ui)
                height (+ collapsed (* (- expanded collapsed) new-progress))]
            (state/update-entity! game-state panel-entity
                                  #(assoc-in % [:components :bounds :height] height))
            (wcore/request-layout! game-state panel-entity))
          (show-body! game-state (> new-progress 0.05))))
      (let [settings (state/hyperlane-settings game-state)
            last-settings (:last-settings ui)]
        (when (not= settings last-settings)
          (sync-controls! game-state settings)
          (update-ui! game-state [:last-settings] settings)))
      (update-header-label! game-state (:expanded? ui)))))
