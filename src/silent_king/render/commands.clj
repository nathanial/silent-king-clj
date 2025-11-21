(ns silent-king.render.commands
  "Data-only draw command helpers used by render planners."
  (:refer-clojure :exclude [repeat]))

(set! *warn-on-reflection* true)

(defn clear
  [color]
  {:op :clear
   :color color})

(defn rect
  ([rect]
   {:op :rect
    :rect rect})
  ([rect style]
   {:op :rect
    :rect rect
    :style style}))

(defn circle
  ([center radius]
   {:op :circle
    :center center
    :radius radius})
  ([center radius style]
   {:op :circle
    :center center
    :radius radius
    :style style}))

(defn line
  [from to style]
  {:op :line
   :from from
   :to to
   :style style})

(defn polygon-fill
  [points style]
  {:op :polygon-fill
   :points (vec points)
   :style style})

(defn polygon-stroke
  [points style]
  {:op :polygon-stroke
   :points (vec points)
   :style style})

(defn image-rect
  ([image src dst]
   {:op :image-rect
    :image image
    :src src
    :dst dst})
  ([image src dst transform]
   (cond-> {:op :image-rect
            :image image
            :src src
            :dst dst}
     transform (assoc :transform transform))))

(defn text
  [{:keys [text position font color]}]
  {:op :text
   :text (or text "")
   :position position
   :font font
   :color color})

(defn save
  []
  {:op :save})

(defn restore
  []
  {:op :restore})

(defn translate
  [dx dy]
  {:op :translate
   :dx dx
   :dy dy})

(defn rotate
  [angle-deg]
  {:op :rotate
   :angle-deg angle-deg})

(defn scale
  [sx sy]
  {:op :scale
   :sx sx
   :sy sy})

(defn clip-rect
  [rect]
  {:op :clip-rect
   :rect rect})

(defn annotate
  "Attach arbitrary metadata to a command for debugging."
  [command tag]
  (cond-> (assoc command :tag tag)
    (map? tag) (update :meta merge tag)))
