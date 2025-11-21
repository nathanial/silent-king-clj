(ns silent-king.render.galaxy
  (:require [silent-king.camera :as camera]
            [silent-king.render.commands :as commands])
  (:import [io.github.humbleui.skija Image]))

(set! *warn-on-reflection* true)

(defn plan-star-from-atlas
  "Plan drawing a star from the texture atlas at screen coordinates (x, y)"
  [^Image atlas-image atlas-metadata path screen-x screen-y size angle _atlas-size]
  (when-let [coords (get atlas-metadata path)]
    (let [tile-size (:size coords)
          scale (/ size tile-size)
          scaled-size (* tile-size scale)
          dst {:x (- screen-x (/ scaled-size 2))
               :y (- screen-y (/ scaled-size 2))
               :width scaled-size
               :height scaled-size}
          src {:x (:x coords) :y (:y coords) :width tile-size :height tile-size}]
      [(commands/image-rect atlas-image src dst {:rotation-deg angle
                                                 :anchor :center})])))

(defn plan-full-res-star
  "Plan drawing a star from a full-resolution image at screen coordinates (x, y)"
  [star-images path screen-x screen-y size angle]
  (when-let [star-data (first (filter #(= (:path %) path) star-images))]
    (let [^Image image (:image star-data)
          img-width (.getWidth image)
          img-height (.getHeight image)
          scale (/ size (max img-width img-height))
          scaled-width (* img-width scale)
          scaled-height (* img-height scale)
          dst {:x (- screen-x (/ scaled-width 2))
               :y (- screen-y (/ scaled-height 2))
               :width scaled-width
               :height scaled-height}
          src {:x 0.0 :y 0.0 :width img-width :height img-height}]
      [(commands/image-rect image src dst {:rotation-deg angle
                                           :anchor :center})])))

(defn plan-planet-from-atlas
  "Plan drawing a planet from the planet atlas at screen coordinates (x, y)"
  [^Image atlas-image atlas-metadata path screen-x screen-y size]
  (when (and atlas-image atlas-metadata)
    (when-let [coords (get atlas-metadata path)]
      (let [tile-size (:size coords)
            scale (/ size tile-size)
            scaled-size (* tile-size scale)
            dst {:x (- screen-x (/ scaled-size 2))
                 :y (- screen-y (/ scaled-size 2))
                 :width scaled-size
                 :height scaled-size}
            src {:x (:x coords) :y (:y coords) :width tile-size :height tile-size}]
        [(commands/image-rect atlas-image src dst nil)]))))

(defn plan-orbit-ring
  "Plan a thin orbit ring for a planet around its parent star."
  [screen-star-x screen-star-y screen-radius]
  [(commands/circle {:x screen-star-x :y screen-star-y}
                    screen-radius
                    {:stroke-color 0x33FFFFFF
                     :stroke-width 1.2})])

(defn plan-selection-highlight
  "Plan a glowing selection ring around the focused star."
  [screen-x screen-y screen-size time]
  (let [pulse (+ 1.0 (* 0.15 (Math/sin (* time 3.0))))
        glow-radius (* screen-size 0.65 pulse)]
    [(commands/circle {:x screen-x :y screen-y}
                      glow-radius
                      {:fill-color 0x44FFD966})
     (commands/circle {:x screen-x :y screen-y}
                      (* screen-size 0.45 pulse)
                      {:stroke-color 0xFFFFE680
                       :stroke-width 3.0})]))

(defn star-visible?
  "Check if a star is visible in the current viewport using non-linear transform"
  [star-world-x star-world-y star-base-size pan-x pan-y viewport-width viewport-height zoom]
  (let [;; Transform world position to screen position
        screen-x (camera/transform-position star-world-x zoom pan-x)
        screen-y (camera/transform-position star-world-y zoom pan-y)
        ;; Transform size
        screen-size (camera/transform-size star-base-size zoom)
        ;; Extra margin to avoid pop-in
        margin (* screen-size 2)]
    (and (> (+ screen-x margin) 0)
         (< (- screen-x margin) viewport-width)
         (> (+ screen-y margin) 0)
         (< (- screen-y margin) viewport-height))))

(defn planet-visible?
  "Check if a planet is visible in the current viewport."
  [screen-x screen-y screen-size viewport-width viewport-height]
  (let [margin (max 8.0 (* screen-size 1.5))]
    (and (> (+ screen-x margin) 0)
         (< (- screen-x margin) viewport-width)
         (> (+ screen-y margin) 0)
         (< (- screen-y margin) viewport-height))))

(defn orbit-visible?
  "Check if an orbit ring is visible given star screen position and ring radius."
  [screen-star-x screen-star-y screen-radius viewport-width viewport-height]
  (let [r (+ (double screen-radius) 4.0)
        min-x (- screen-star-x r)
        max-x (+ screen-star-x r)
        min-y (- screen-star-y r)
        max-y (+ screen-star-y r)]
    (and (< min-x viewport-width)
         (> max-x 0.0)
         (< min-y viewport-height)
         (> max-y 0.0))))
