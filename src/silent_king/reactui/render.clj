(ns silent-king.reactui.render
  "Skija renderer for the Reactified UI tree."
  (:require [silent-king.reactui.layout :as layout])
  (:import [io.github.humbleui.skija Canvas Font Typeface Paint]
           [io.github.humbleui.types Rect]))

(set! *warn-on-reflection* true)

(defonce ^Typeface default-typeface
  (Typeface/makeDefault))

(defmulti draw-node
  (fn [_ node]
    (:type node)))

(defn- make-font
  [font-size]
  (Font. default-typeface (float font-size)))

(defn- draw-label
  [^Canvas canvas node]
  (let [{:keys [text color font-size]} (:props node)
        {:keys [x y]} (layout/bounds node)
        ^Paint paint (doto (Paint.)
                       (.setColor (unchecked-int (or color 0xFFFFFFFF))))
        size (double (or font-size 16.0))
        baseline (+ y size)]
    (with-open [font (make-font size)]
      (.drawString canvas (or text "")
                   (float x)
                   (float baseline)
                   font
                   paint))
    (.close paint)))

(defn- draw-vstack
  [^Canvas canvas node]
  (let [{:keys [background-color]} (:props node)
        {:keys [x y width height]} (layout/bounds node)]
    (when background-color
      (let [paint (doto (Paint.)
                    (.setColor (unchecked-int background-color)))]
        (.drawRect canvas
                   (Rect/makeXYWH (float x) (float y) (float width) (float height))
                   paint)
        (.close paint)))
    (doseq [child (:children node)]
      (draw-node canvas child))))

(defmethod draw-node :label
  [canvas node]
  (draw-label canvas node))

(defmethod draw-node :vstack
  [canvas node]
  (draw-vstack canvas node))

(defmethod draw-node :default
  [_ _]
  nil)

(defn draw-tree
  "Render a laid-out tree."
  [canvas node]
  (when canvas
    (draw-node canvas node)))
