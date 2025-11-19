(ns silent-king.reactui.primitives.button
  "Button primitive: normalization, layout, rendering, and pointer handling."
  (:require [silent-king.reactui.core :as core]
            [silent-king.reactui.events :as ui-events]
            [silent-king.reactui.interaction :as interaction]
            [silent-king.reactui.layout :as layout]
            [silent-king.reactui.render :as render])
  (:import [io.github.humbleui.skija Canvas Font Paint PaintMode]
           [io.github.humbleui.types Rect]))

(set! *warn-on-reflection* true)

(defn button-props
  "Derive a button label from props or children."
  [props raw-children]
  (let [label (or (:label props)
                  (core/collect-text raw-children)
                  "Button")]
    (assoc props :label label)))

(defmethod core/normalize-tag :button
  [_ props child-forms]
  ((core/leaf-normalizer :button button-props) props child-forms))

(defmethod layout/layout-node :button
  [node context]
  (let [bounds* (layout/resolve-bounds node context)
        height (double (if (pos? (:height bounds*))
                         (:height bounds*)
                         36.0))
        final-bounds (assoc bounds* :height height)]
    (assoc node
           :layout {:bounds final-bounds}
           :children [])))

(defn- active-button?
  [node]
  (let [active (render/active-interaction)]
    (and (= :button (:type active))
         (= (:bounds active) (layout/bounds node)))))

(defn draw-button
  [^Canvas canvas node]
  (let [{:keys [label background-color text-color font-size]} (:props node)
        {:keys [x y width height]} (layout/bounds node)
        bg-color (or background-color 0xFF2D2F38)
        txt-color (or text-color 0xFFFFFFFF)
        hovered? (render/pointer-over-node? node)
        active? (active-button? node)
        shade (cond active? 0.9
                    hovered? 1.1
                    :else 1.0)
        final-bg (if (= shade 1.0) bg-color (render/adjust-color bg-color shade))
        final-text (if active?
                     (render/adjust-color txt-color 0.95)
                     txt-color)
        size (double (or font-size 16.0))
        text (or label "")
        text-width (render/approx-text-width text size)
        text-x (+ x (/ (- width text-width) 2.0))
        baseline (+ y (/ height 2.0) (/ size 2.5))
        border-color (cond active? (render/adjust-color bg-color 0.7)
                           hovered? (render/adjust-color bg-color 1.2)
                           :else nil)]
    (with-open [^Paint paint (doto (Paint.)
                               (.setColor (unchecked-int final-bg)))]
      (.drawRect canvas
                 (Rect/makeXYWH (float x) (float y) (float width) (float height))
                 paint))
    (when border-color
      (with-open [^Paint border (doto (Paint.)
                                   (.setColor (unchecked-int border-color))
                                   (.setStrokeWidth 1.0)
                                   (.setMode PaintMode/STROKE))]
        (.drawRect canvas
                   (Rect/makeXYWH (float x) (float y) (float width) (float height))
                   border)))
    (with-open [^Paint text-paint (doto (Paint.)
                                    (.setColor (unchecked-int final-text)))]
      (with-open [^Font font (render/make-font size)]
        (.drawString canvas text
                     (float text-x)
                     (float baseline)
                     font
                     text-paint)))))

(defmethod render/draw-node :button
  [canvas node]
  (draw-button canvas node))

(defn- contains-point?
  [{:keys [x y width height]} px py]
  (and (number? x) (number? y)
       (number? width) (number? height)
       (>= px x)
       (<= px (+ x width))
       (>= py y)
       (<= py (+ y height))))

(defn activate-button!
  [node game-state px py]
  (let [bounds (layout/bounds node)]
    (when (contains-point? bounds px py)
      (when-let [event (-> node :props :on-click)]
        (when (vector? event)
          (ui-events/dispatch-event! game-state event))))))

(defmethod core/pointer-down! :button
  [node _game-state _x _y]
  (core/capture-node! node)
  (core/set-active-interaction! node :button)
  true)

(defmethod core/pointer-up! :button
  [node game-state x y]
  (activate-button! node game-state x y))
