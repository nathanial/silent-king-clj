(ns silent-king.widgets.layout
  "Layout invalidation and processing system"
  (:require [silent-king.state :as state]))

(set! *warn-on-reflection* true)

;; =============================================================================
;; Anchor Positioning
;; =============================================================================

(defn apply-anchor-position
  "Apply anchor positioning to widget bounds based on viewport size.
  Anchors: :top-left, :top-right, :bottom-left, :bottom-right, :center"
  [bounds layout viewport-width viewport-height]
  (let [{:keys [anchor margin]} layout
        margin-top (or (:top margin) (:all margin) 0)
        margin-right (or (:right margin) (:all margin) 0)
        margin-bottom (or (:bottom margin) (:all margin) 0)
        margin-left (or (:left margin) (:all margin) 0)
        widget-width (:width bounds)
        widget-height (:height bounds)]

    (case (or anchor :top-left)
      :top-left
      (assoc bounds
             :x (+ margin-left (:x bounds 0))
             :y (+ margin-top (:y bounds 0)))

      :top-right
      (assoc bounds
             :x (- viewport-width widget-width margin-right)
             :y (+ margin-top (:y bounds 0)))

      :bottom-left
      (assoc bounds
             :x (+ margin-left (:x bounds 0))
             :y (- viewport-height widget-height margin-bottom))

      :bottom-right
      (assoc bounds
             :x (- viewport-width widget-width margin-right)
             :y (- viewport-height widget-height margin-bottom))

      :center
      (assoc bounds
             :x (/ (- viewport-width widget-width) 2)
             :y (/ (- viewport-height widget-height) 2))

      ;; Default: use provided x/y with margins
      (assoc bounds
             :x (+ margin-left (:x bounds 0))
             :y (+ margin-top (:y bounds 0))))))

;; =============================================================================
;; Layout Processing
;; =============================================================================

(defmulti perform-layout
  "Apply layout for a widget entity that has been marked dirty."
  (fn [_game-state _entity-id widget-entity _viewport-width _viewport-height]
    (get-in (state/get-component widget-entity :widget) [:type])))

(defmethod perform-layout :default
  [_ _ _ _ _]
  nil)

(defn mark-dirty!
  "Mark a widget entity as needing a layout pass."
  [game-state entity-id]
  (swap! game-state update-in [:widgets :layout-dirty] (fnil conj #{}) entity-id))

(defn process-layouts!
  "Process all widgets that have been marked dirty.
  Viewport dimensions are needed for anchor positioning."
  ([game-state]
   (process-layouts! game-state 1280 800))  ;; Default viewport size
  ([game-state viewport-width viewport-height]
   (let [dirty (seq (get-in @game-state [:widgets :layout-dirty]))]
     (when dirty
       (swap! game-state assoc-in [:widgets :layout-dirty] #{})
       (doseq [entity-id dirty
               :let [entity (state/get-entity game-state entity-id)]
               :when entity]
         (perform-layout game-state entity-id entity viewport-width viewport-height))))))
