(ns silent-king.reactui.primitives.slider
  "Slider primitive: normalization, layout, rendering, and interaction helpers."
  (:require [silent-king.reactui.core :as core]
            [silent-king.reactui.events :as ui-events]
            [silent-king.reactui.interaction :as interaction]
            [silent-king.reactui.layout :as layout]
            [silent-king.reactui.render :as render]
            [silent-king.render.commands :as commands]
            [silent-king.color :as color]))

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

(defn plan-slider
  [node]
  (let [{:keys [background-color track-color handle-color]} (:props node)
        {:keys [x y width height]} (layout/bounds node)
        {:keys [track handle]} (get-in node [:layout :slider])
        bg-color (when background-color (color/ensure background-color))
        t-color (or (color/ensure track-color) (color/hsv 227.1 18.9 29.0))
        h-color (or (color/ensure handle-color) (color/hsv 0 0 94.1))
        hovered? (render/pointer-over-node? node)
        active? (active-slider? node)
        track-color (cond active? (render/adjust-color t-color 0.9)
                          hovered? (render/adjust-color t-color 1.1)
                          :else t-color)
        handle-color (cond active? (render/adjust-color h-color 0.9)
                           hovered? (render/adjust-color h-color 1.05)
                           :else h-color)]
    (cond-> []
      bg-color (conj (commands/rect {:x x :y y :width width :height height}
                                    {:fill-color bg-color}))
      (pos? (:width track)) (conj (commands/rect {:x (:x track)
                                                  :y (:y track)
                                                  :width (:width track)
                                                  :height (:height track)}
                                                 {:fill-color track-color}))
      (pos? (:width track)) (conj (commands/circle {:x (:x handle)
                                                    :y (:y handle)}
                                                   (:radius handle)
                                                   {:fill-color handle-color})))))

(defmethod render/plan-node :slider
  [_context node]
  (plan-slider node))

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
