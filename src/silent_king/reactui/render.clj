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

(defn- approx-text-width
  [text font-size]
  (* (count (or text ""))
     font-size
     0.55))

(defn- draw-label
  [^Canvas canvas node]
  (let [{:keys [text color font-size]} (:props node)
        {:keys [x y]} (layout/bounds node)
        size (double (or font-size 16.0))
        baseline (+ y size)]
    (with-open [^Paint paint (doto (Paint.)
                               (.setColor (unchecked-int (or color 0xFFFFFFFF))))]
      (with-open [^Font font (make-font size)]
        (.drawString canvas (or text "")
                     (float x)
                     (float baseline)
                     font
                     paint)))))

(defn- draw-vstack
  [^Canvas canvas node]
  (let [{:keys [background-color]} (:props node)
        {:keys [x y width height]} (layout/bounds node)]
    (when background-color
      (with-open [^Paint paint (doto (Paint.)
                                  (.setColor (unchecked-int background-color)))]
        (.drawRect canvas
                   (Rect/makeXYWH (float x) (float y) (float width) (float height))
                   paint)))
    (doseq [child (:children node)]
      (draw-node canvas child))))

(defn- draw-button
  [^Canvas canvas node]
  (let [{:keys [label background-color text-color font-size]} (:props node)
        {:keys [x y width height]} (layout/bounds node)
        bg-color (or background-color 0xFF2D2F38)
        txt-color (or text-color 0xFFFFFFFF)
        size (double (or font-size 16.0))
        text (or label "")
        text-width (approx-text-width text size)
        text-x (+ x (/ (- width text-width) 2.0))
        baseline (+ y (/ height 2.0) (/ size 2.5))]
    (with-open [^Paint paint (doto (Paint.)
                               (.setColor (unchecked-int bg-color)))]
      (.drawRect canvas
                 (Rect/makeXYWH (float x) (float y) (float width) (float height))
                 paint))
    (with-open [^Paint text-paint (doto (Paint.)
                                    (.setColor (unchecked-int txt-color)))]
      (with-open [^Font font (make-font size)]
        (.drawString canvas text
                     (float text-x)
                     (float baseline)
                     font
                     text-paint)))))

(defn- draw-slider
  [^Canvas canvas node]
  (let [{:keys [background-color track-color handle-color]} (:props node)
        {:keys [x y width height]} (layout/bounds node)
        {:keys [track handle]} (get-in node [:layout :slider])
        bg-color (or background-color 0x00111111)
        t-color (or track-color 0xFF3C3F4A)
        h-color (or handle-color 0xFFF0F0F0)]
    (when background-color
      (with-open [^Paint bg (doto (Paint.)
                               (.setColor (unchecked-int bg-color)))]
        (.drawRect canvas
                   (Rect/makeXYWH (float x) (float y) (float width) (float height))
                   bg)))
    (when (pos? (:width track))
      (with-open [^Paint track-paint (doto (Paint.)
                                       (.setColor (unchecked-int t-color)))]
        (.drawRect canvas
                   (Rect/makeXYWH (float (:x track))
                                  (float (:y track))
                                  (float (:width track))
                                  (float (:height track)))
                   track-paint))
      (with-open [^Paint handle-paint (doto (Paint.)
                                         (.setColor (unchecked-int h-color)))]
        (.drawCircle canvas
                     (float (:x handle))
                     (float (:y handle))
                     (float (:radius handle))
                     handle-paint)))))

(defmethod draw-node :label
  [canvas node]
  (draw-label canvas node))

(defmethod draw-node :button
  [canvas node]
  (draw-button canvas node))

(defmethod draw-node :slider
  [canvas node]
  (draw-slider canvas node))

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
