(ns silent-king.widgets.minimap
  "Pure math helpers for minimap layout, interaction, and density coloring."
  (:require [silent-king.camera :as camera]
            [silent-king.state :as state]))

(set! *warn-on-reflection* true)

(def ^:private min-span
  "Minimum world span so the minimap remains well-defined even when all points overlap."
  1.0)

(def ^:private default-world-span 10000.0)

(def default-world-bounds
  {:min-x (- (/ default-world-span 2.0))
   :max-x (/ default-world-span 2.0)
   :min-y (- (/ default-world-span 2.0))
   :max-y (/ default-world-span 2.0)
   :span-x default-world-span
   :span-y default-world-span})

(def ^:const default-density-resolution 48)
(def ^:const default-screen-width 1280.0)
(def ^:const default-screen-height 800.0)

(def default-viewport-size
  {:width default-screen-width
   :height default-screen-height})

(defn get-viewport-size
  "Return the most recent viewport size saved in game-state, or defaults."
  [game-state]
  (or (get-in @game-state [:widgets :viewport-size])
      default-viewport-size))

(defn collect-star-positions
  "Return a vector of all star positions with {:x :y} data."
  [game-state]
  (->> (state/filter-entities-with game-state [:position])
       (map (fn [[_ entity]] (state/get-component entity :position)))
       (remove nil?)
       (into [])))

(defn- expand-range
  "Ensure the provided min/max span has at least min-span width centered on the data."
  [min-val max-val]
  (let [span (- max-val min-val)]
    (if (< span min-span)
      (let [center (/ (+ min-val max-val) 2.0)
            half (/ min-span 2.0)]
        {:min (- center half)
         :max (+ center half)
         :span min-span})
      {:min min-val
       :max max-val
       :span span})))

(defn compute-world-bounds
  "Compute render-space bounds for the minimap using all known star positions.
  The returned map always has :min-x/:max-x/:span-x and :min-y/:max-y/:span-y keys."
  [positions]
  (if (seq positions)
    (let [xs (map :x positions)
          ys (map :y positions)
          expanded-x (expand-range (apply min xs) (apply max xs))
          expanded-y (expand-range (apply min ys) (apply max ys))]
      {:min-x (:min expanded-x)
       :max-x (:max expanded-x)
       :min-y (:min expanded-y)
       :max-y (:max expanded-y)
       :span-x (:span expanded-x)
       :span-y (:span expanded-y)})
    default-world-bounds))

(defn compute-transform
  "Return the data needed to convert between world coordinates and minimap pixels."
  [bounds world-bounds]
  (let [min-x (double (:min-x world-bounds))
        min-y (double (:min-y world-bounds))
        span-x (double (:span-x world-bounds))
        span-y (double (:span-y world-bounds))
        width (double (or (:width bounds) 0.0))
        height (double (or (:height bounds) 0.0))
        x (double (or (:x bounds) 0.0))
        y (double (or (:y bounds) 0.0))
        scale (if (pos? (min span-x span-y))
                (min (/ width span-x) (/ height span-y))
                1.0)
        scaled-width (* span-x scale)
        scaled-height (* span-y scale)
        offset-x (+ x (* 0.5 (- width scaled-width)))
        offset-y (+ y (* 0.5 (- height scaled-height)))]
    {:scale scale
     :offset-x offset-x
     :offset-y offset-y
     :min-x min-x
     :min-y min-y
     :span-x span-x
     :span-y span-y}))

(defn world->minimap
  "Convert a world coordinate into minimap pixel coordinates."
  [transform world-x world-y]
  (let [{:keys [scale offset-x offset-y min-x min-y]} transform
        rel-x (- world-x min-x)
        rel-y (- world-y min-y)]
    [(+ offset-x (* rel-x scale))
     (+ offset-y (* rel-y scale))]))

(defn minimap->world
  "Convert a minimap pixel coordinate into world space."
  [transform map-x map-y]
  (let [{:keys [scale offset-x offset-y min-x min-y span-x span-y]} transform
        inv-scale (if (pos? scale) (/ 1.0 scale) 1.0)
        rel-x (* (- map-x offset-x) inv-scale)
        rel-y (* (- map-y offset-y) inv-scale)
        clamped-x (-> rel-x (max 0.0) (min span-x))
        clamped-y (-> rel-y (max 0.0) (min span-y))]
    {:x (+ min-x clamped-x)
     :y (+ min-y clamped-y)}))

(defn build-density-grid
  "Bucket every star position into a grid for density-based coloring."
  ([positions world-bounds]
   (build-density-grid positions world-bounds default-density-resolution))
  ([positions world-bounds bucket-count]
   (let [bucket-count (int (max 1 bucket-count))
         span-x (:span-x world-bounds)
         span-y (:span-y world-bounds)
         cell-width (/ span-x bucket-count)
         cell-height (/ span-y bucket-count)
         min-x (:min-x world-bounds)
         min-y (:min-y world-bounds)]
     {:bucket-count bucket-count
      :cell-width cell-width
      :cell-height cell-height
      :cells (reduce
              (fn [grid {:keys [x y]}]
                (let [norm-x (if (zero? span-x) 0.5 (/ (- x min-x) span-x))
                      norm-y (if (zero? span-y) 0.5 (/ (- y min-y) span-y))
                      ix (-> (* norm-x bucket-count) Math/floor int (max 0) (min (dec bucket-count)))
                      iy (-> (* norm-y bucket-count) Math/floor int (max 0) (min (dec bucket-count)))]
                  (update grid [ix iy] (fnil inc 0))))
              {}
              positions)})))

(defn- lerp-channel [start end t]
  (int (+ start (* (- end start) t))))

(def ^:private low-density-color 0x55203A5F)
(def ^:private high-density-color 0xFFF1E77A)

(defn density-color
  "Return an ARGB color representing the supplied density value (0-1)."
  [density]
  (let [t (-> density (max 0.0) (min 1.0))
        a (lerp-channel (bit-and (bit-shift-right low-density-color 24) 0xFF)
                        (bit-and (bit-shift-right high-density-color 24) 0xFF)
                        t)
        r (lerp-channel (bit-and (bit-shift-right low-density-color 16) 0xFF)
                        (bit-and (bit-shift-right high-density-color 16) 0xFF)
                        t)
        g (lerp-channel (bit-and (bit-shift-right low-density-color 8) 0xFF)
                        (bit-and (bit-shift-right high-density-color 8) 0xFF)
                        t)
        b (lerp-channel (bit-and low-density-color 0xFF)
                        (bit-and high-density-color 0xFF)
                        t)]
    (bit-or (bit-shift-left a 24)
            (bit-shift-left r 16)
            (bit-shift-left g 8)
            b)))

(defn camera->viewport
  "Return the visible world rectangle for the current camera/zoom, accounting for the non-linear transform."
  ([camera]
   (camera->viewport camera default-viewport-size))
  ([camera {:keys [width height] :or {width default-screen-width
                                     height default-screen-height}}]
   (let [zoom (double (max 0.0001 (:zoom camera)))
         pan-x (:pan-x camera)
         pan-y (:pan-y camera)
         left (camera/inverse-transform-position 0.0 zoom pan-x)
         right (camera/inverse-transform-position (double width) zoom pan-x)
         bottom (camera/inverse-transform-position 0.0 zoom pan-y)
         top (camera/inverse-transform-position (double height) zoom pan-y)]
     {:min-x (min left right)
      :max-x (max left right)
      :min-y (min bottom top)
      :max-y (max bottom top)})))

(defn target-pan
  "Compute the pan values needed to center a world coordinate inside the viewport."
  [world zoom {:keys [width height] :or {width default-screen-width
                                         height default-screen-height}}]
  {:pan-x (camera/center-pan (:x world) zoom width)
   :pan-y (camera/center-pan (:y world) zoom height)})
