(ns silent-king.reactui.primitives.minimap
  "Minimap primitive: normalization, layout, rendering, and interaction handling."
  (:require [silent-king.minimap.math :as minimap-math]
            [silent-king.reactui.core :as core]
            [silent-king.reactui.events :as ui-events]
            [silent-king.reactui.layout :as layout]
            [silent-king.reactui.render :as render]
            [silent-king.reactui.primitives.window :as window])
  (:import [io.github.humbleui.skija Canvas Paint PaintMode]
           [io.github.humbleui.types Rect]))

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

(defmethod render/draw-node :minimap
  [^Canvas canvas node]
  (let [{:keys [stars world-bounds viewport-rect background-color viewport-color]} (:props node)
        {:keys [x y width height]} (layout/bounds node)
        bg-color (render/->color-int background-color 0xFF000000)
        viewport-color (render/->color-int viewport-color 0xFF00FF00)
        widget-bounds {:x x :y y :width width :height height}
        cell-size (render/heatmap-cell-size width height)
        {:keys [counts cols rows max-density]} (render/bucket-stars-into-grid stars world-bounds widget-bounds cell-size)
        counts-array ^ints counts]
    (with-open [^Paint bg (doto (Paint.)
                            (.setColor bg-color))]
      (try
        (.drawRect canvas (Rect/makeXYWH (float x) (float y) (float width) (float height)) bg)
        (catch ArithmeticException _ nil)
        (catch Exception _ nil)))

    (when (pos? max-density)
      (with-open [^Paint heatmap-paint (Paint.)]
        (dotimes [row rows]
          (dotimes [col cols]
            (let [idx (+ (* row cols) col)
                  count (aget counts-array idx)]
              (when (pos? count)
                (let [px (+ x (* col cell-size))
                      py (+ y (* row cell-size))
                      cell-w (max 0.0 (min cell-size (- (+ x width) px)))
                      cell-h (max 0.0 (min cell-size (- (+ y height) py)))]
                  (when (and (pos? cell-w) (pos? cell-h))
                    (.setColor heatmap-paint (render/density->color count max-density))
                    (try
                      (.drawRect canvas (Rect/makeXYWH (float px)
                                                       (float py)
                                                       (float cell-w)
                                                       (float cell-h))
                                 heatmap-paint)
                      (catch ArithmeticException _ nil)
                      (catch Exception _ nil))))))))))

    (when-let [{:keys [x y width height]} (minimap-math/viewport->minimap-rect viewport-rect
                                                                               world-bounds
                                                                               widget-bounds)]
      (with-open [^Paint viewport-paint (doto (Paint.)
                                          (.setColor viewport-color)
                                          (.setStrokeWidth 1.0)
                                          (.setMode PaintMode/STROKE))]
        (try
          (.drawRect canvas (Rect/makeXYWH (float x) (float y) (float width) (float height)) viewport-paint)
          (catch ArithmeticException _ nil)
          (catch Exception _ nil))))

    (with-open [^Paint border-paint (doto (Paint.)
                                      (.setColor (render/->color-int nil 0xFF444444))
                                      (.setStrokeWidth 1.0)
                                      (.setMode PaintMode/STROKE))]
      (try
        (.drawRect canvas (Rect/makeXYWH (float x) (float y) (float width) (float height)) border-paint)
        (catch ArithmeticException _ nil)
        (catch Exception _ nil)))))

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
