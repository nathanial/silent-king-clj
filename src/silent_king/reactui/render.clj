(ns silent-king.reactui.render
  "Rendering planner for the Reactified UI tree."
  (:require [silent-king.minimap.math :as minimap-math]
            [silent-king.reactui.layout :as layout]
            [silent-king.color :as color]))

(set! *warn-on-reflection* true)

(def ^:dynamic *overlay-collector* nil)
(def ^:dynamic *render-context* nil)

(defn queue-overlay!
  [overlay]
  (when *overlay-collector*
    (swap! *overlay-collector* conj overlay)))

(defn approx-text-width
  [text font-size]
  (* (count (or text ""))
     font-size
     0.55))

(defn clamp01
  "Clamp value between 0.0 and 1.0."
  [value]
  (-> value
      (max 0.0)
      (min 1.0)))

(defn adjust-color
  [color factor]
  (color/multiply color factor))

(defn blend-colors
  [color-a color-b t]
  (color/lerp color-a color-b t))

(def heatmap-low-color (color/hsv 214.5 89.2 29.0 0.1))
(def heatmap-high-color (color/hsv 45.2 79.3 94.5))

(defn heatmap-cell-size
  [width height]
  (let [w (double (or width 0.0))
        h (double (or height 0.0))
        largest (max 1.0 (max w h))
        target (/ largest 48.0)]
    (-> target
        (max 3.0)
        (min 18.0))))

(defn bucket-stars-into-grid
  [stars world-bounds widget-bounds cell-size]
  (let [cols (max 1 (int (Math/ceil (/ (:width widget-bounds) cell-size))))
        rows (max 1 (int (Math/ceil (/ (:height widget-bounds) cell-size))))
        total (* rows cols)
        counts (int-array total)]
    (loop [remaining stars
           max-density 0]
      (if-let [star (first remaining)]
        (let [pos (minimap-math/world->minimap star world-bounds widget-bounds)
              local-x (- (:x pos) (:x widget-bounds))
              local-y (- (:y pos) (:y widget-bounds))
              col (int (Math/floor (/ local-x cell-size)))
              row (int (Math/floor (/ local-y cell-size)))]
          (if (and (>= col 0) (< col cols)
                   (>= row 0) (< row rows))
            (let [idx (+ (* row cols) col)
                  new-count (unchecked-inc-int (aget ^ints counts idx))]
              (aset-int counts idx new-count)
              (recur (rest remaining)
                     (max max-density new-count)))
            (recur (rest remaining) max-density)))
        {:counts counts
         :cols cols
         :rows rows
         :cell-size cell-size
         :max-density max-density}))))

(defn density->color
  [count max-count]
  (if (pos? max-count)
    (let [ratio (double (/ count max-count))
          eased (Math/pow ratio 0.6)]
      (blend-colors heatmap-low-color heatmap-high-color eased))
    heatmap-low-color))

(defn pointer-position
  []
  (:pointer *render-context*))

(defn pointer-in-bounds?
  [{:keys [x y width height]}]
  (when-let [{px :x py :y} (pointer-position)]
    (let [px* (double px)
          py* (double py)]
      (and (>= px* (double x))
           (<= px* (+ (double x) width))
           (>= py* (double y))
           (<= py* (+ (double y) height))))))

(defn pointer-over-node?
  [node]
  (pointer-in-bounds? (layout/bounds node)))

(defn active-interaction
  []
  (:active-interaction *render-context*))

(defmulti plan-node
  (fn [_ node]
    (:type node)))

(defmulti plan-overlay
  (fn [_ overlay]
    (:type overlay)))

(defmethod plan-node :default
  [context node]
  (mapcat #(plan-node context %)
          (:children node)))

(defmethod plan-overlay :default
  [_ _]
  [])

(defn plan-tree
  "Plan draw commands for a laid-out UI tree, returning both node and overlay commands."
  [node context]
  (let [overlays (atom [])
        ctx (or context {})]
    (binding [*overlay-collector* overlays
              *render-context* ctx]
      (let [node-commands (vec (plan-node ctx node))
            overlay-commands (vec (mapcat #(plan-overlay ctx %) @overlays))]
        {:commands (into [] (concat node-commands overlay-commands))
         :overlays @overlays}))))
