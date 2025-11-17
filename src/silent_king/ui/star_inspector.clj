(ns silent-king.ui.star-inspector
  "Star selection and inspector panel helpers."
  (:require [silent-king.state :as state]
            [silent-king.widgets.animation :as wanim]
            [silent-king.widgets.config :as wconfig]
            [silent-king.widgets.core :as wcore]
            [silent-king.widgets.minimap :as wminimap]))

(set! *warn-on-reflection* true)

(declare zoom-to-selected-star!)

(def ^:private panel-id :star-inspector-panel)
(def ^:private preview-id :star-inspector-preview)
(def ^:private title-id :star-inspector-title)
(def ^:private subtitle-id :star-inspector-subtitle)
(def ^:private position-id :star-inspector-position)
(def ^:private size-id :star-inspector-size)
(def ^:private density-id :star-inspector-density)
(def ^:private rotation-id :star-inspector-rotation)
(def ^:private hyperlane-label-id :star-inspector-hyperlane-label)
(def ^:private hyperlane-list-id :star-inspector-hyperlane-list)

(declare update-panel!)

;; =============================================================================
;; Internal Helpers
;; =============================================================================

(defn- inspector-state
  [game-state]
  (get-in @game-state [:ui :star-inspector]))

(defn- widget-viewport-width
  [game-state]
  (let [{:keys [width]} (wminimap/get-viewport-size game-state)
        fallback (:width wminimap/default-viewport-size)
        px-width (double (or width fallback))]
    (/ px-width wconfig/ui-scale)))

(defn- visible-position
  [game-state]
  (let [{:keys [panel-width margin]} (inspector-state game-state)
        viewport (widget-viewport-width game-state)
        margin (or margin 20.0)]
    (- viewport panel-width margin)))

(defn- hidden-position
  [game-state]
  (let [{:keys [margin]} (inspector-state game-state)
        viewport (widget-viewport-width game-state)
        margin (or margin 20.0)]
    (+ viewport margin)))

(defn- desired-target-x
  [game-state]
  (if (:visible? (inspector-state game-state))
    (visible-position game-state)
    (hidden-position game-state)))

(defn- set-visibility!
  [game-state visible?]
  (let [target (if visible?
                 (visible-position game-state)
                 (hidden-position game-state))]
    (swap! game-state (fn [gs]
                        (-> gs
                            (assoc-in [:ui :star-inspector :visible?] visible?)
                            (assoc-in [:ui :star-inspector :target-x] target))))))

(defn- update-label-text!
  [game-state widget-id text]
  (when-let [[entity-id _] (wcore/get-widget-by-id game-state widget-id)]
    (state/update-entity! game-state entity-id
                          #(assoc-in % [:components :visual :text] text))))

(defn- update-preview!
  [game-state {:keys [star-id density size]}]
  (when-let [[entity-id _] (wcore/get-widget-by-id game-state preview-id)]
    (state/update-entity! game-state entity-id
                          #(assoc-in % [:components :value]
                                     {:star-id star-id
                                      :density (double (or density 0.0))
                                      :size (double (or size 0.0))}))))

(defn- format-distance
  [dist]
  (format "%.0f ly" dist))

(defn- connections->items
  [connections]
  (mapv (fn [{:keys [neighbor-id distance]}]
          {:primary (str "Star #" neighbor-id)
           :secondary (format-distance distance)})
        connections))

(defn- update-hyperlane-list!
  [game-state connections]
  (when-let [[entity-id _] (wcore/get-widget-by-id game-state hyperlane-list-id)]
    (let [items (connections->items connections)]
      (state/update-entity! game-state entity-id
                            #(-> %
                                 (assoc-in [:components :value :items] items)
                                 (assoc-in [:components :value :scroll-offset] 0.0))))))

(defn- hyperlane-count-text
  [count]
  (str "Hyperlanes (" count ")"))

(defn- update-inspector-content!
  [game-state selection]
  (if selection
    (let [{:keys [star-id position size density rotation-speed connections]} selection
          {:keys [x y]} position
          connection-count (count connections)]
      (update-label-text! game-state title-id (str "Star #" star-id))
      (update-label-text! game-state subtitle-id "Detailed properties")
      (update-label-text! game-state position-id (format "Position: %.0f, %.0f" x y))
      (update-label-text! game-state size-id (format "Size: %.1f px" size))
      (update-label-text! game-state density-id (format "Density: %.2f" density))
      (update-label-text! game-state rotation-id (format "Rotation: %.2f rad/s" rotation-speed))
      (update-label-text! game-state hyperlane-label-id (hyperlane-count-text connection-count))
      (update-hyperlane-list! game-state connections)
      (update-preview! game-state selection))
    (do
      (update-label-text! game-state title-id "Star Inspector")
      (update-label-text! game-state subtitle-id "Select a star to view details")
      (update-label-text! game-state position-id "Position: --")
      (update-label-text! game-state size-id "Size: --")
      (update-label-text! game-state density-id "Density: --")
      (update-label-text! game-state rotation-id "Rotation: --")
      (update-label-text! game-state hyperlane-label-id "Hyperlanes (0)")
      (update-hyperlane-list! game-state [])
      (update-preview! game-state {:star-id nil :density 0.0 :size 0.0}))))

(defn- hit-star?
  [world-x world-y entity]
  (let [pos (state/get-component entity :position)
        transform (state/get-component entity :transform)
        base-size (double (or (:size transform) 0.0))
        radius (max 30.0 (* 0.6 base-size))
        dx (- world-x (:x pos))
        dy (- world-y (:y pos))
        dist (Math/sqrt (+ (* dx dx) (* dy dy)))]
    (when (<= dist radius)
      dist)))

(defn- find-star-at
  [game-state world-x world-y]
  (let [stars (state/filter-entities-with game-state [:position :transform])]
    (reduce (fn [best [entity-id entity]]
              (if-let [dist (hit-star? world-x world-y entity)]
                (if (or (nil? best) (< dist (:distance best)))
                  {:id entity-id :entity entity :distance dist}
                  best)
                best))
            nil
            stars)))

(defn- hyperlane-connections
  [game-state star-id]
  (let [hyperlanes (state/filter-entities-with game-state [:hyperlane])
        star-entity (state/get-entity game-state star-id)
        pos-a (state/get-component star-entity :position)]
    (->> hyperlanes
         (keep (fn [[_ hyperlane-entity]]
                 (let [data (state/get-component hyperlane-entity :hyperlane)
                       {:keys [from-id to-id]} data]
                   (cond
                     (= star-id from-id) {:neighbor-id to-id}
                     (= star-id to-id) {:neighbor-id from-id}
                     :else nil))))
         (keep (fn [{:keys [neighbor-id]}]
                 (when (and pos-a neighbor-id)
                   (when-let [neighbor (state/get-entity game-state neighbor-id)]
                     (when-let [pos-b (state/get-component neighbor :position)]
                       (let [dx (- (:x pos-b) (:x pos-a))
                             dy (- (:y pos-b) (:y pos-a))
                             dist (Math/sqrt (+ (* dx dx) (* dy dy)))]
                         {:neighbor-id neighbor-id
                          :distance dist}))))))
         (sort-by :distance)
         vec)))

(defn- build-selection
  [game-state star-id star-entity last-click]
  (let [pos (state/get-component star-entity :position)
        transform (state/get-component star-entity :transform)
        physics (state/get-component star-entity :physics)
        star-data (state/get-component star-entity :star)
        renderable (state/get-component star-entity :renderable)]
    {:star-id star-id
     :position pos
     :size (:size transform)
     :density (:density star-data 0.0)
     :rotation-speed (:rotation-speed physics 0.0)
     :path (:path renderable)
     :connections (hyperlane-connections game-state star-id)
     :last-click last-click}))

(defn- set-selection!
  [game-state selection]
  (swap! game-state assoc :selection {:star-id (:star-id selection)
                                      :details selection
                                      :last-world-click (:last-click selection)}))

(defn- clear-selection-state!
  [game-state]
  (swap! game-state assoc :selection {:star-id nil
                                      :details nil
                                      :last-world-click nil}))

;; =============================================================================
;; Public API
;; =============================================================================

(defn create-star-inspector!
  [game-state]
  (let [{:keys [panel-width panel-height]} (inspector-state game-state)
        hidden-x (hidden-position game-state)
        panel (wcore/panel
               :id panel-id
               :bounds {:x hidden-x
                        :y 80.0
                        :width panel-width
                        :height panel-height}
               :layout {:padding {:top 16 :right 16 :bottom 16 :left 16}
                        :gap 12
                        :align :stretch}
               :visual {:background-color 0xE61A1A1A
                        :border-radius 14.0
                        :shadow {:offset-x 0 :offset-y 8 :blur 20 :color 0x80000000}})
        title (wcore/label "Star Inspector"
                           :id title-id
                           :bounds {:width (- panel-width 32) :height 28}
                           :visual {:text-color 0xFFFFFFFF
                                    :font-size 20
                                    :font-weight :bold})
        subtitle (wcore/label "Select a star to view details"
                              :id subtitle-id
                              :bounds {:width (- panel-width 32) :height 22}
                              :visual {:text-color 0xFFAAAAAA
                                       :font-size 14})
        preview (wcore/star-preview
                 :id preview-id
                 :bounds {:width (- panel-width 32) :height 110})
        position (wcore/label "Position: --"
                              :id position-id
                              :bounds {:width (- panel-width 32) :height 22}
                              :visual {:text-color 0xFFEEEEEE
                                       :font-size 14})
        size-label (wcore/label "Size: --"
                                :id size-id
                                :bounds {:width (- panel-width 32) :height 22}
                                :visual {:text-color 0xFFEEEEEE
                                         :font-size 14})
        density-label (wcore/label "Density: --"
                                   :id density-id
                                   :bounds {:width (- panel-width 32) :height 22}
                                   :visual {:text-color 0xFFEEEEEE
                                            :font-size 14})
        rotation-label (wcore/label "Rotation: --"
                                    :id rotation-id
                                    :bounds {:width (- panel-width 32) :height 22}
                                    :visual {:text-color 0xFFEEEEEE
                                             :font-size 14})
        hyperlane-label (wcore/label "Hyperlanes (0)"
                                     :id hyperlane-label-id
                                     :bounds {:width (- panel-width 32) :height 22}
                                     :visual {:text-color 0xFFCCCCCC
                                              :font-size 13
                                              :font-weight :bold})
        hyperlane-list (wcore/scroll-view
                        :id hyperlane-list-id
                        :bounds {:width (- panel-width 32) :height 200})
        zoom-button (wcore/button "Zoom to Star"
                                  #(zoom-to-selected-star! game-state)
                                  :id :star-inspector-zoom
                                  :bounds {:width (- panel-width 32) :height 40}
                                  :visual {:background-color 0xFF3366CC
                                           :border-radius 8.0})]
    (let [panel-entity (wcore/add-widget-tree! game-state panel
                                               [title
                                                subtitle
                                                preview
                                                position
                                                size-label
                                                density-label
                                                rotation-label
                                                hyperlane-label
                                                hyperlane-list
                                                zoom-button])]
      (swap! game-state (fn [gs]
                          (-> gs
                              (assoc-in [:ui :star-inspector :panel-entity] panel-entity)
                              (assoc-in [:ui :star-inspector :current-x] hidden-x)
                              (assoc-in [:ui :star-inspector :target-x] hidden-x))))
      (update-inspector-content! game-state nil)
      panel-entity)))

(defn selected-star-id
  [game-state]
  (get-in @game-state [:selection :star-id]))

(defn handle-star-click!
  [game-state world-x world-y]
  (swap! game-state assoc-in [:selection :last-world-click] {:x world-x :y world-y})
  (if-let [{:keys [id entity]} (find-star-at game-state world-x world-y)]
    (let [selection (build-selection game-state id entity {:x world-x :y world-y})]
      (set-selection! game-state selection)
      (set-visibility! game-state true)
      (update-inspector-content! game-state selection)
      true)
    (do
      (clear-selection-state! game-state)
      (set-visibility! game-state false)
      (update-inspector-content! game-state nil)
      false)))

(defn clear-star-selection!
  [game-state]
  (clear-selection-state! game-state)
  (set-visibility! game-state false)
  (update-inspector-content! game-state nil))

(defn update-panel!
  [game-state]
  (when-let [panel-entity (get-in @game-state [:ui :star-inspector :panel-entity])]
    (let [{:keys [current-x target-x]} (inspector-state game-state)
          desired (desired-target-x game-state)
          target-x (if (not= desired target-x)
                     (do
                       (swap! game-state assoc-in [:ui :star-inspector :target-x] desired)
                       desired)
                     target-x)
          current (double (or current-x target-x 0.0))
          target (double (or target-x current-x 0.0))
          new-x (if (<= (Math/abs (- current target)) 0.5)
                  target
                  (+ current (* 0.2 (- target current))))]
      (when (not= new-x current-x)
        (let [panel-entity-data (state/get-entity game-state panel-entity)
              bounds (state/get-component panel-entity-data :bounds)
              new-bounds (assoc bounds :x new-x)]
          (state/update-entity! game-state panel-entity
                                #(state/add-component % :bounds new-bounds))
          (wcore/request-layout! game-state panel-entity)
          (swap! game-state assoc-in [:ui :star-inspector :current-x] new-x))))))

(defn zoom-to-selected-star!
  [game-state]
  (when-let [selection (get-in @game-state [:selection :details])]
    (let [camera (state/get-camera game-state)
          viewport (wminimap/get-viewport-size game-state)
          world (:position selection)
          {:keys [pan-x pan-y]} (wminimap/target-pan world (:zoom camera) viewport)]
      (wanim/start-camera-pan! game-state pan-x pan-y 0.6))))
