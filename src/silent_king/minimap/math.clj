(ns silent-king.minimap.math
  "Pure math utilities for minimap coordinate transformations."
  (:require [silent-king.camera :as camera]))

(set! *warn-on-reflection* true)

(def ^:const default-padding 500.0)

(defn calculate-world-bounds
  "Find the bounding box of all stars in the galaxy."
  [stars]
  (if (empty? stars)
    {:min-x -1000.0 :max-x 1000.0 :min-y -1000.0 :max-y 1000.0}
    (reduce
     (fn [acc {:keys [x y]}]
       (-> acc
           (update :min-x min x)
           (update :max-x max x)
           (update :min-y min y)
           (update :max-y max y)))
     {:min-x Double/MAX_VALUE
      :max-x (- Double/MAX_VALUE)
      :min-y Double/MAX_VALUE
      :max-y (- Double/MAX_VALUE)}
     stars)))

(defn- normalize-bounds
  "Add padding to bounds and ensure width/height are positive."
  [{:keys [min-x max-x min-y max-y]}]
  (let [min-x (- min-x default-padding)
        max-x (+ max-x default-padding)
        min-y (- min-y default-padding)
        max-y (+ max-y default-padding)
        width (- max-x min-x)
        height (- max-y min-y)]
    {:min-x min-x
     :max-x max-x
     :min-y min-y
     :max-y max-y
     :width (max 1.0 width)
     :height (max 1.0 height)}))

(defn- scale-factor
  "Calculate the scale factor to fit world bounds into widget bounds."
  [world-bounds widget-bounds]
  (let [w-ratio (/ (:width widget-bounds) (:width world-bounds))
        h-ratio (/ (:height widget-bounds) (:height world-bounds))]
    (min w-ratio h-ratio)))

(defn world->minimap
  "Convert a world coordinate to a minimap widget coordinate."
  [world-pos world-bounds widget-bounds]
  (let [bounds (normalize-bounds world-bounds)
        scale (scale-factor bounds widget-bounds)
        offset-x (/ (- (:width widget-bounds) (* (:width bounds) scale)) 2.0)
        offset-y (/ (- (:height widget-bounds) (* (:height bounds) scale)) 2.0)
        ;; Center the minimap content within the widget
        start-x (+ (:x widget-bounds) offset-x)
        start-y (+ (:y widget-bounds) offset-y)]
    {:x (+ start-x (* (- (:x world-pos) (:min-x bounds)) scale))
     :y (+ start-y (* (- (:y world-pos) (:min-y bounds)) scale))}))

(defn minimap->world
  "Convert a minimap widget coordinate to a world coordinate."
  [minimap-pos world-bounds widget-bounds]
  (let [bounds (normalize-bounds world-bounds)
        scale (scale-factor bounds widget-bounds)
        offset-x (/ (- (:width widget-bounds) (* (:width bounds) scale)) 2.0)
        offset-y (/ (- (:height widget-bounds) (* (:height bounds) scale)) 2.0)
        start-x (+ (:x widget-bounds) offset-x)
        start-y (+ (:y widget-bounds) offset-y)]
    {:x (+ (:min-x bounds) (/ (- (:x minimap-pos) start-x) scale))
     :y (+ (:min-y bounds) (/ (- (:y minimap-pos) start-y) scale))}))

(defn viewport-rect
  "Calculate the visible world area based on camera state."
  [camera viewport]
  (let [{:keys [zoom pan-x pan-y]} camera
        {:keys [width height]} viewport
        tl-x (camera/inverse-transform-position 0.0 zoom pan-x)
        tl-y (camera/inverse-transform-position 0.0 zoom pan-y)
        br-x (camera/inverse-transform-position width zoom pan-x)
        br-y (camera/inverse-transform-position height zoom pan-y)]
    {:x tl-x
     :y tl-y
     :width (- br-x tl-x)
     :height (- br-y tl-y)}))
