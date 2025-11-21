(ns silent-king.reactui.primitives.minimap
  "Minimap primitive: normalization, layout, rendering, and interaction handling."
  (:require [silent-king.minimap.math :as minimap-math]
            [silent-king.reactui.core :as core]
            [silent-king.reactui.events :as ui-events]
            [silent-king.reactui.layout :as layout]
            [silent-king.reactui.render :as render]
            [silent-king.reactui.primitives.window :as window]
            [silent-king.render.commands :as commands]))

(set! *warn-on-reflection* true)

(def ^:const minimap-window-drag-threshold 5.0)

(defmethod core/normalize-tag :minimap
  [_ props child-forms]
  ((core/leaf-normalizer :minimap) props child-forms))

(defmethod layout/layout-node :minimap
  [node context]
  (let [bounds* (layout/resolve-bounds node context)
        width (double (if (pos? (:width bounds*))
                        (:width bounds*)
                        200.0))
        height (double (if (pos? (:height bounds*))
                         (:height bounds*)
                         200.0))
        final-bounds (assoc bounds* :width width :height height)]
    (assoc node
           :layout {:bounds final-bounds}
           :children [])))

(defn- within-bounds?
  [{:keys [x y width height]} px py]
  (and (number? x) (number? y) (number? width) (number? height)
       (>= px x)
       (<= px (+ x width))
       (>= py y)
       (<= py (+ y height))))

(defn- viewport-center
  [{:keys [x y width height]}]
  (when (and (number? x) (number? y) (number? width) (number? height))
    {:x (+ (double x) (/ (double width) 2.0))
     :y (+ (double y) (/ (double height) 2.0))}))

(defn minimap-interaction-value
  [node px py]
  (let [{:keys [world-bounds viewport-rect]} (:props node)
        widget-bounds (layout/bounds node)
        viewport-bounds (when (and viewport-rect world-bounds widget-bounds)
                          (minimap-math/viewport->minimap-rect viewport-rect world-bounds widget-bounds))
        viewport-hit? (and viewport-bounds (within-bounds? viewport-bounds px py))
        center (viewport-center viewport-rect)
        pointer-world (when (and world-bounds widget-bounds)
                        (minimap-math/minimap->world {:x px :y py} world-bounds widget-bounds))
        offset (when (and viewport-hit? pointer-world center)
                 {:dx (- (:x pointer-world) (:x center))
                  :dy (- (:y pointer-world) (:y center))})]
    {:mode (if viewport-hit? :viewport-drag :click)
     :offset offset
     :viewport-bounds viewport-bounds}))

(defn handle-minimap-pan!
  ([node game-state x y]
   (handle-minimap-pan! node game-state x y nil))
  ([node game-state x y interaction]
   (let [{:keys [world-bounds]} (:props node)
         widget-bounds (layout/bounds node)
         world-pos (minimap-math/minimap->world {:x x :y y} world-bounds widget-bounds)
         {:keys [mode offset]} (or interaction {})
         adjusted (if (and (= mode :viewport-drag) offset)
                    {:x (- (:x world-pos) (double (:dx offset)))
                     :y (- (:y world-pos) (double (:dy offset)))}
                    world-pos)]
     (ui-events/dispatch-event! game-state [:camera/pan-to-world adjusted]))))

(defn- pointer-distance
  [{:keys [x y]} {x2 :x y2 :y}]
  (let [dx (- (double x2) (double x))
        dy (- (double y2) (double y))]
    (Math/sqrt (+ (* dx dx) (* dy dy)))))

(defn plan-minimap
  [node]
  (let [{:keys [stars world-bounds viewport-rect background-color viewport-color]} (:props node)
        {:keys [x y width height]} (layout/bounds node)
        bg-color (render/->color-int background-color 0xFF000000)
        viewport-color (render/->color-int viewport-color 0xFF00FF00)
        widget-bounds {:x x :y y :width width :height height}
        cell-size (render/heatmap-cell-size width height)
        heatmap (when world-bounds
                  (render/bucket-stars-into-grid stars world-bounds widget-bounds cell-size))
        {:keys [counts cols rows max-density]} heatmap
        counts-array ^ints counts
        viewport-bounds (when world-bounds
                          (minimap-math/viewport->minimap-rect viewport-rect
                                                               world-bounds
                                                               widget-bounds))
        base-commands [(commands/rect widget-bounds {:fill-color bg-color})]]
    (cond-> base-commands
      (pos? (or max-density 0))
      (into (for [row (range rows)
                  col (range cols)
                  :let [idx (+ (* row cols) col)
                        count (aget counts-array idx)]
                  :when (pos? count)]
              (let [px (+ x (* col cell-size))
                    py (+ y (* row cell-size))
                    cell-w (max 0.0 (min cell-size (- (+ x width) px)))
                    cell-h (max 0.0 (min cell-size (- (+ y height) py)))]
                (commands/rect {:x px
                                :y py
                                :width cell-w
                                :height cell-h}
                               {:fill-color (render/density->color count max-density)}))))
      viewport-bounds
      (conj (commands/rect viewport-bounds {:stroke-color viewport-color
                                            :stroke-width 1.0}))
      true (conj (commands/rect widget-bounds {:stroke-color (render/->color-int nil 0xFF444444)
                                               :stroke-width 1.0})))))

(defmethod render/plan-node :minimap
  [context node]
  (plan-minimap node))

(defmethod core/pointer-down! :minimap
  [node game-state x y]
  (let [interaction (assoc (minimap-interaction-value node x y)
                           :start-pointer {:x x :y y})]
    (core/capture-node! node)
    (core/set-active-interaction! node :minimap {:value interaction})
    (when (= :viewport-drag (:mode interaction))
      (handle-minimap-pan! node game-state x y interaction))
    true))

(defmethod core/pointer-drag! :minimap
  [node game-state x y]
  (let [interaction (some-> (core/active-interaction) :value)
        {:keys [mode start-pointer]} interaction]
    (case mode
      :viewport-drag (do
                       (handle-minimap-pan! node game-state x y interaction)
                       true)
      :click (do
               (when (and start-pointer
                          (>= (pointer-distance start-pointer {:x x :y y})
                              minimap-window-drag-threshold))
                 (window/delegate-minimap-drag-to-window! node x y))
               true)
      true)))

(defmethod core/pointer-up! :minimap
  [node game-state x y]
  (let [interaction (some-> (core/active-interaction) :value)]
    (when (= :click (:mode interaction))
      (handle-minimap-pan! node game-state x y interaction))
    true))
