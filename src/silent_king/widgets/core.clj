(ns silent-king.widgets.core
  "Core widget system - entity creation and component management"
  (:require [silent-king.state :as state]
            [silent-king.widgets.layout :as wlayout]))

(set! *warn-on-reflection* true)

;; =============================================================================
;; Widget Component Helpers
;; =============================================================================

(defn create-widget
  "Create a widget entity with given components. Widgets default to visible."
  [widget-type & {:keys [id bounds layout visual interaction value visible?]}]
  (state/create-entity
   :widget {:type widget-type
            :id (or id (keyword (str (name widget-type) "-" (rand-int 999999))))
            :parent-id nil
            :children []
            :visible? (if (nil? visible?) true visible?)}
   :bounds (or bounds {:x 0 :y 0 :width 100 :height 40})
   :layout (or layout {:padding {:top 0 :right 0 :bottom 0 :left 0}
                       :margin {:top 0 :right 0 :bottom 0 :left 0}
                       :anchor :top-left
                       :z-index 0})
   :visual (or visual {})
   :interaction (or interaction {:enabled true
                                 :hovered false
                                 :pressed false
                                 :focused false
                                 :hover-cursor :pointer
                                 :on-click nil
                                 :on-hover nil
                                 :on-drag nil})
   :value value))

;; =============================================================================
;; Basic Widget Constructors
;; =============================================================================

(defn panel
  "Create a panel (container widget)"
  [& {:keys [id bounds layout visual]}]
  (create-widget :panel
                 :id id
                 :bounds bounds
                 :layout layout
                 :visual (merge {:background-color 0xCC222222
                                :border-radius 12.0
                                :shadow {:offset-x 0 :offset-y 4 :blur 12 :color 0x80000000}}
                               visual)))

(defn label
  "Create a label (text display widget)"
  [text & {:keys [id bounds visual]}]
  (create-widget :label
                 :id id
                 :bounds bounds
                 :visual (merge {:text text
                                :text-color 0xFFFFFFFF
                                :font-size 14
                                :font-weight :normal
                                :text-align :left}
                               visual)))

(defn button
  "Create a button widget"
  [text on-click & {:keys [id bounds visual]}]
  (create-widget :button
                 :id id
                 :bounds bounds
                 :visual (merge {:text text
                                :background-color 0xFF3366CC
                                :text-color 0xFFFFFFFF
                                :border-radius 6.0
                                :font-size 14
                                :font-weight :normal}
                               visual)
                 :interaction {:enabled true
                              :hovered false
                              :pressed false
                              :focused false
                              :hover-cursor :pointer
                              :on-click on-click}))

(defn slider
  "Create a slider widget"
  [min-val max-val current-val on-change & {:keys [id bounds visual step]}]
  (create-widget :slider
                 :id id
                 :bounds (or bounds {:x 0 :y 0 :width 200 :height 20})
                 :value {:min min-val
                        :max max-val
                        :current current-val
                        :step (or step 0.1)}
                 :visual (merge {:track-color 0xFF444444
                                :track-active-color 0xFF6699FF
                                :thumb-color 0xFFFFFFFF
                                :thumb-radius 10.0}
                               visual)
                 :interaction {:enabled true
                              :hovered false
                              :pressed false
                              :focused false
                              :hover-cursor :pointer
                              :on-change on-change
                              :dragging false}))

(defn minimap
  "Create a minimap widget for galaxy navigation"
  [& {:keys [id bounds layout visual]}]
  (create-widget :minimap
                 :id id
                 :bounds (or bounds {:x 0 :y 0 :width 250 :height 250})
                 :layout (merge {:anchor :bottom-right
                                :margin {:all 20}}
                               layout)
                 :visual (merge {:background-color 0xCC1A1A1A
                                :border-color 0xFF444444
                                :border-width 2.0
                                :border-radius 8.0
                                :viewport-color 0xFFFF0000
                                :star-color 0xFFFFFFFF}
                               visual)
                 :interaction {:enabled true
                              :hovered false
                              :pressed false
                              :focused false
                              :hover-cursor :pointer
                              :on-click nil}))

(defn star-preview
  "Create a simple star preview widget used by the inspector panel."
  [& {:keys [id bounds visual]}]
  (create-widget :star-preview
                 :id id
                 :bounds (or bounds {:width 140 :height 120})
                 :visual (merge {:background-color 0x33111111
                                :border-radius 12.0}
                               visual)
                 :value {:star-id nil
                         :density 0.0
                         :size 0.0}))

(defn scroll-view
  "Create a vertical scroll view with inertial scrolling handled by the interaction layer."
  [& {:keys [id bounds visual value]}]
  (create-widget :scroll-view
                 :id id
                 :bounds (or bounds {:width 240 :height 200})
                 :visual (merge {:background-color 0x22181818
                                :border-radius 10.0
                                :scrollbar-color 0x55FFFFFF}
                               visual)
                 :interaction {:enabled true
                               :hovered false
                               :pressed false
                               :focused false
                               :hover-cursor :default}
                 :value (merge {:items []
                                :scroll-offset 0.0
                                :item-height 34.0
                                :gap 6.0}
                               value)))

(defn toggle
  "Create a toggle switch widget with a label."
  [label checked? on-toggle & {:keys [id bounds visual]}]
  (create-widget :toggle
                 :id id
                 :bounds (or bounds {:width 260 :height 32})
                 :visual (merge {:label label
                                 :label-color 0xFFEEEEEE
                                 :track-on-color 0xFF3DD598
                                 :track-off-color 0xFF555555
                                 :thumb-color 0xFFFFFFFF}
                                visual)
                 :value {:checked? (boolean checked?)}
                 :interaction {:enabled true
                               :hovered false
                               :pressed false
                               :focused false
                               :hover-cursor :pointer
                               :on-toggle on-toggle}))

(defn dropdown
  "Create a dropdown widget with labeled options. Options must be maps with :value and :label."
  [options selected on-select & {:keys [id bounds visual]}]
  (let [normalized (mapv (fn [opt]
                           (cond
                             (and (map? opt) (contains? opt :value)) opt
                             :else {:value opt :label (name opt)}))
                         options)]
    (create-widget :dropdown
                   :id id
                   :bounds (or bounds {:width 260 :height 36})
                   :visual (merge {:label-color 0xFFEEEEEE
                                   :background-color 0xFF1E1E1E
                                   :border-color 0xFF444444
                                   :text-color 0xFFEEEEEE
                                   :option-hover-color 0x33222222}
                                  visual)
                   :value {:options normalized
                           :selected selected
                           :expanded? false
                           :hover-index nil
                           :option-height 32.0
                           :base-height (double (or (:height bounds) 36.0))}
                   :interaction {:enabled true
                                 :hovered false
                                 :pressed false
                                 :focused false
                                 :hover-cursor :pointer
                                 :on-select on-select})))

(defn line-chart
  "Create a line chart widget for displaying time-series performance metrics."
  [& {:keys [id bounds visual value]}]
  (create-widget :line-chart
                 :id id
                 :bounds (or bounds {:width 280 :height 100})
                 :visual (merge {:background-color 0xCC1A1A1A
                                 :border-radius 8.0
                                 :line-color 0xFF3DD598
                                 :grid-color 0x33444444
                                 :text-color 0xFFCCCCCC}
                                visual)
                 :interaction {:enabled false
                               :hovered false
                               :pressed false
                               :focused false
                               :hover-cursor :default}
                 :value (merge {:points []
                                :max-points 60
                                :label ""
                                :unit ""
                                :grid-lines 4}
                               value)))

;; =============================================================================
;; Layout Containers
;; =============================================================================

(defn vstack
  "Create a vertical stack container"
  [& {:keys [id bounds layout visual]}]
  (create-widget :vstack
                 :id id
                 :bounds bounds
                 :layout (merge {:padding {:top 12 :right 12 :bottom 12 :left 12}
                                 :gap 8
                                 :align :stretch}
                                layout)
                 :visual (or visual {})))

(defn hstack
  "Create a horizontal stack container"
  [& {:keys [id bounds layout visual]}]
  (create-widget :hstack
                 :id id
                 :bounds bounds
                 :layout (merge {:padding {:top 12 :bottom 12 :left 12 :right 12}
                                 :gap 8
                                 :align :center}
                                layout)
                 :visual (or visual {})))

;; =============================================================================
;; Widget Management
;; =============================================================================

(defn add-widget!
  "Add a widget entity to the game state and return the entity ID"
  [game-state widget-entity]
  (let [entity-id (state/add-entity! game-state widget-entity)]
    (wlayout/mark-dirty! game-state entity-id)
    entity-id))

(defn add-widget-tree!
  "Add a widget and its children to the game state"
  [game-state parent-widget children]
  (let [parent-id (add-widget! game-state parent-widget)
        child-ids (mapv #(add-widget! game-state %) children)]
    ;; Update parent to reference children
    (state/update-entity! game-state parent-id
                         #(assoc-in % [:components :widget :children] child-ids))
    ;; Update children to reference parent
    (doseq [child-id child-ids]
      (state/update-entity! game-state child-id
                           #(assoc-in % [:components :widget :parent-id] parent-id)))
    (wlayout/mark-dirty! game-state parent-id)
    parent-id))

(defn get-all-widgets
  "Get all widget entities"
  [game-state]
  (state/filter-entities-with game-state [:widget]))

(defn get-widget-by-id
  "Find widget entity by widget ID"
  [game-state widget-id]
  (let [widgets (get-all-widgets game-state)]
    (some (fn [[entity-id entity]]
            (when (= widget-id (get-in (state/get-component entity :widget) [:id]))
              [entity-id entity]))
          widgets)))

;; =============================================================================
;; VStack Layout Computation
;; =============================================================================

(defn compute-vstack-layout
  "Compute the bounds for children in a VStack layout"
  [parent-bounds parent-layout children]
  (let [{:keys [gap padding align]} parent-layout
        gap-val (or gap 0)
        padding-top (or (:top padding) (:all padding) 0)
        padding-left (or (:left padding) (:all padding) 0)
        padding-right (or (:right padding) (:all padding) 0)
        content-width (- (:width parent-bounds) padding-left padding-right)
        y-offset (atom (+ (:y parent-bounds) padding-top))]

    (mapv (fn [child]
            (let [child-bounds (state/get-component child :bounds)
                  child-height (:height child-bounds)
                  child-width (case align
                               :stretch content-width
                               :fill content-width
                               (:width child-bounds))
                  child-x (case align
                           :start (+ (:x parent-bounds) padding-left)
                           :center (+ (:x parent-bounds) padding-left (/ (- content-width child-width) 2))
                           :end (+ (:x parent-bounds) (- (:width parent-bounds) padding-right child-width))
                           (+ (:x parent-bounds) padding-left))
                  child-y @y-offset
                  new-bounds {:x child-x
                             :y child-y
                             :width child-width
                             :height child-height}]
              (swap! y-offset + child-height gap-val)
              [child new-bounds]))
          children)))

(defn compute-hstack-layout
  "Compute bounds for children in an HStack layout."
  [parent-bounds parent-layout children]
  (let [{:keys [gap padding align]} parent-layout
        gap-val (or gap 0)
        padding-top (or (:top padding) (:all padding) 0)
        padding-bottom (or (:bottom padding) (:all padding) 0)
        padding-left (or (:left padding) (:all padding) 0)
        parent-x (double (or (:x parent-bounds) 0.0))
        parent-y (double (or (:y parent-bounds) 0.0))
        parent-height (double (or (:height parent-bounds) 0.0))
        x-offset (atom (+ parent-x padding-left))
        content-height (- parent-height padding-top padding-bottom)]
    (mapv (fn [child]
            (let [child-bounds (or (state/get-component child :bounds) {})
                  child-width (double (or (:width child-bounds) 0.0))
                  intrinsic-height (double (or (:height child-bounds) 0.0))
                  child-height (case align
                                 :stretch content-height
                                 :fill content-height
                                 intrinsic-height)
                  child-y (case align
                            :start (+ parent-y padding-top)
                            :center (+ parent-y padding-top (/ (- content-height child-height) 2))
                            :end (+ parent-y (- parent-height padding-bottom child-height))
                            (+ parent-y padding-top))
                  new-bounds {:x @x-offset
                              :y child-y
                              :width child-width
                              :height child-height}]
              (swap! x-offset + child-width gap-val)
              [child new-bounds]))
          children)))

(defn update-vstack-layout!
  "Update the layout of a VStack widget and its children"
  [game-state vstack-entity-id]
  (let [vstack-entity (state/get-entity game-state vstack-entity-id)
        vstack-bounds (state/get-component vstack-entity :bounds)
        vstack-layout (state/get-component vstack-entity :layout)
        vstack-widget (state/get-component vstack-entity :widget)
        child-ids (:children vstack-widget)
        children (mapv #(state/get-entity game-state %) child-ids)
        child-layouts (compute-vstack-layout vstack-bounds vstack-layout children)]

    ;; Update each child's bounds using associated child-id
    (doseq [[child-id [_child new-bounds]] (map vector child-ids child-layouts)]
      (state/update-entity! game-state child-id
                           #(state/add-component % :bounds new-bounds)))))

(defmethod wlayout/perform-layout :vstack
  [game-state entity-id _entity _viewport-width _viewport-height]
  (update-vstack-layout! game-state entity-id))

(defmethod wlayout/perform-layout :panel
  [game-state entity-id _entity _viewport-width _viewport-height]
  (update-vstack-layout! game-state entity-id))

(defn update-hstack-layout!
  [game-state hstack-entity-id]
  (let [hstack-entity (state/get-entity game-state hstack-entity-id)
        hstack-bounds (state/get-component hstack-entity :bounds)
        hstack-layout (state/get-component hstack-entity :layout)
        hstack-widget (state/get-component hstack-entity :widget)
        child-ids (:children hstack-widget)
        children (mapv #(state/get-entity game-state %) child-ids)
        child-layouts (compute-hstack-layout hstack-bounds hstack-layout children)]
    (doseq [[child-id [_child new-bounds]] (map vector child-ids child-layouts)]
      (state/update-entity! game-state child-id
                            #(state/add-component % :bounds new-bounds)))))

(defmethod wlayout/perform-layout :hstack
  [game-state entity-id _entity _viewport-width _viewport-height]
  (update-hstack-layout! game-state entity-id))

(defmethod wlayout/perform-layout :minimap
  [game-state entity-id entity viewport-width viewport-height]
  ;; Apply anchor positioning for minimap
  (let [bounds (state/get-component entity :bounds)
        layout (state/get-component entity :layout)
        anchored-bounds (wlayout/apply-anchor-position bounds layout viewport-width viewport-height)]
    (state/update-entity! game-state entity-id
                         #(state/add-component % :bounds anchored-bounds))))

(defn request-layout!
  "Mark a widget entity to recompute its layout before the next frame."
  [game-state entity-id]
  (wlayout/mark-dirty! game-state entity-id))

(defn request-parent-layout!
  "Mark a widget's parent as dirty so siblings reposition."
  [game-state entity-id]
  (when-let [entity (state/get-entity game-state entity-id)]
    (when-let [parent-id (get-in entity [:components :widget :parent-id])]
      (request-layout! game-state parent-id))))

(defn set-visibility!
  "Update widget visibility, optionally cascading to children."
  ([game-state entity-id visible?]
   (set-visibility! game-state entity-id visible? true))
  ([game-state entity-id visible? cascade?]
   (when entity-id
     (state/update-entity! game-state entity-id
                           #(assoc-in % [:components :widget :visible?] (boolean visible?)))
     (when cascade?
       (when-let [entity (state/get-entity game-state entity-id)]
         (doseq [child-id (get-in entity [:components :widget :children])]
           (set-visibility! game-state child-id visible? true)))))))
