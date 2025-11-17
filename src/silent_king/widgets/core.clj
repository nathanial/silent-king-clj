(ns silent-king.widgets.core
  "Core widget system - entity creation and component management"
  (:require [silent-king.state :as state]))

(set! *warn-on-reflection* true)

;; =============================================================================
;; Widget Component Helpers
;; =============================================================================

(defn create-widget
  "Create a widget entity with given components"
  [widget-type & {:keys [id bounds layout visual interaction value]}]
  (state/create-entity
   :widget {:type widget-type
            :id (or id (keyword (str (name widget-type) "-" (rand-int 999999))))
            :parent-id nil
            :children []}
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

;; =============================================================================
;; Layout Containers
;; =============================================================================

(defn vstack
  "Create a vertical stack container"
  [children & {:keys [id bounds layout visual]}]
  (create-widget :vstack
                 :id id
                 :bounds bounds
                 :layout (merge {:padding {:top 12 :right 12 :bottom 12 :left 12}
                                :gap 8
                                :align :stretch}
                               layout)
                 :visual (or visual {})
                 :value {:children children}))

;; =============================================================================
;; Widget Management
;; =============================================================================

(defn add-widget!
  "Add a widget entity to the game state and return the entity ID"
  [game-state widget-entity]
  (state/add-entity! game-state widget-entity))

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
