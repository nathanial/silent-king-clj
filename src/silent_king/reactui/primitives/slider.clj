(ns silent-king.reactui.primitives.slider
  "Slider primitive: normalization, layout, rendering, and interaction helpers."
  (:require [silent-king.reactui.core :as core]
            [silent-king.reactui.events :as ui-events]
            [silent-king.reactui.interaction :as interaction]
            [silent-king.reactui.layout :as layout]
            [silent-king.reactui.render :as render])
  (:import [io.github.humbleui.skija Canvas Paint]
           [io.github.humbleui.types Rect]))

(set! *warn-on-reflection* true)

(defmethod core/normalize-tag :slider
  [_ props child-forms]
  ((core/leaf-normalizer :slider) props child-forms))

(defmethod layout/layout-node :slider
  [node context]
  (let [props (:props node)
        bounds* (layout/resolve-bounds node context)
        height (double (if (pos? (:height bounds*))
                         (:height bounds*)
                         32.0))
        final-bounds (assoc bounds* :height height)
        raw-min (double (or (:min props) 0.0))
        raw-max (double (or (:max props) 1.0))
        [min-value max-value] (if (> raw-min raw-max)
                                [raw-max raw-min]
                                [raw-min raw-max])
        step (:step props)
        value (double (or (:value props) min-value))
        snapped-value (-> value
                          (layout/snap-to-step min-value step)
                          (layout/clamp min-value max-value))
        track-padding (double (max 0.0 (or (:track-padding props) 12.0)))
        track-height (double (or (:track-height props) 4.0))
        track-width (max 0.0 (- (:width final-bounds) (* 2 track-padding)))
        track-x (+ (:x final-bounds) track-padding)
        track-y (+ (:y final-bounds)
                   (/ (- height track-height) 2.0))
        range-span (max (- max-value min-value) 1e-9)
        ratio (/ (- snapped-value min-value) range-span)
        clamped-ratio (layout/clamp ratio 0.0 1.0)
        handle-radius (double (or (:handle-radius props) 8.0))
        handle-x (+ track-x (* track-width clamped-ratio))
        handle-y (+ (:y final-bounds) (/ height 2.0))]
    (assoc node
           :layout {:bounds final-bounds
                    :slider {:track {:x track-x
                                     :y track-y
                                     :width track-width
                                     :height track-height}
                             :handle {:x handle-x
                                      :y handle-y
                                      :radius handle-radius}
                             :range {:min min-value
                                     :max max-value
                                     :step (layout/positive-step step)}
                             :value snapped-value}}
           :children [])))

(defn- active-slider?
  [node]
  (let [active (render/active-interaction)]
    (and (= :slider (:type active))
         (= (:bounds active) (layout/bounds node)))))

(defn draw-slider
  [^Canvas canvas node]
  (let [{:keys [background-color track-color handle-color]} (:props node)
        {:keys [x y width height]} (layout/bounds node)
        {:keys [track handle]} (get-in node [:layout :slider])
        bg-color (or background-color 0x00111111)
        t-color (or track-color 0xFF3C3F4A)
        h-color (or handle-color 0xFFF0F0F0)
        hovered? (render/pointer-over-node? node)
        active? (active-slider? node)
        track-color (cond active? (render/adjust-color t-color 0.9)
                          hovered? (render/adjust-color t-color 1.1)
                          :else t-color)
        handle-color (cond active? (render/adjust-color h-color 0.9)
                           hovered? (render/adjust-color h-color 1.05)
                           :else h-color)]
    (when background-color
      (with-open [^Paint bg (doto (Paint.)
                              (.setColor (unchecked-int bg-color)))]
        (.drawRect canvas
                   (Rect/makeXYWH (float x) (float y) (float width) (float height))
                   bg)))
    (when (pos? (:width track))
      (with-open [^Paint track-paint (doto (Paint.)
                                       (.setColor (unchecked-int track-color)))]
        (.drawRect canvas
                   (Rect/makeXYWH (float (:x track))
                                  (float (:y track))
                                  (float (:width track))
                                  (float (:height track)))
                   track-paint))
      (with-open [^Paint handle-paint (doto (Paint.)
                                        (.setColor (unchecked-int handle-color)))]
        (.drawCircle canvas
                     (float (:x handle))
                     (float (:y handle))
                     (float (:radius handle))
                     handle-paint)))))

(defmethod render/draw-node :slider
  [canvas node]
  (draw-slider canvas node))

(defn slider-drag!
  [node game-state px]
  (when-let [event (-> node :props :on-change)]
    (when (vector? event)
      (let [value (interaction/slider-value-from-point node px)]
        (ui-events/dispatch-event! game-state (conj event value))))))

(defmethod core/pointer-down! :slider
  [node game-state x _y]
  (core/capture-node! node)
  (core/set-active-interaction! node :slider)
  (slider-drag! node game-state x)
  true)

(defmethod core/pointer-up! :slider
  [node game-state x _y]
  (slider-drag! node game-state x))

(defmethod core/pointer-drag! :slider
  [node game-state x _y]
  (slider-drag! node game-state x)
  true)
