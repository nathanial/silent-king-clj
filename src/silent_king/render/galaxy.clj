(ns silent-king.render.galaxy
  (:require [silent-king.camera :as camera])
  (:import [io.github.humbleui.skija Canvas Paint PaintMode Image]
           [io.github.humbleui.types Rect]))

(set! *warn-on-reflection* true)

(defn draw-star-from-atlas
  "Draw a star from the texture atlas at screen coordinates (x, y)"
  [^Canvas canvas ^Image atlas-image atlas-metadata path screen-x screen-y size angle atlas-size]
  (when-let [coords (get atlas-metadata path)]
    (.save canvas)
    (.translate canvas screen-x screen-y)
    (.rotate canvas angle)
    (let [tile-size (:size coords)
          scale (/ size tile-size)
          scaled-size (* tile-size scale)]
      (.drawImageRect canvas atlas-image
                      (Rect/makeXYWH (:x coords) (:y coords) tile-size tile-size)
                      (Rect/makeXYWH (- (/ scaled-size 2))
                                     (- (/ scaled-size 2))
                                     scaled-size
                                     scaled-size)))
    (.restore canvas)))

(defn draw-full-res-star
  "Draw a star from a full-resolution image at screen coordinates (x, y)"
  [^Canvas canvas star-images path screen-x screen-y size angle]
  (when-let [star-data (first (filter #(= (:path %) path) star-images))]
    (let [^Image image (:image star-data)
          img-width (.getWidth image)
          img-height (.getHeight image)
          scale (/ size (max img-width img-height))
          scaled-width (* img-width scale)
          scaled-height (* img-height scale)]
      (.save canvas)
      (.translate canvas screen-x screen-y)
      (.rotate canvas angle)
      (.drawImageRect canvas image
                      (Rect/makeXYWH 0 0 img-width img-height)
                      (Rect/makeXYWH (- (/ scaled-width 2))
                                     (- (/ scaled-height 2))
                                     scaled-width
                                     scaled-height))
      (.restore canvas))))

(defn draw-planet-from-atlas
  "Draw a planet from the planet atlas at screen coordinates (x, y)"
  [^Canvas canvas ^Image atlas-image atlas-metadata path screen-x screen-y size]
  (when (and atlas-image atlas-metadata)
    (when-let [coords (get atlas-metadata path)]
      (.save canvas)
      (.translate canvas screen-x screen-y)
      (let [tile-size (:size coords)
            scale (/ size tile-size)
            scaled-size (* tile-size scale)]
        (.drawImageRect canvas atlas-image
                        (Rect/makeXYWH (:x coords) (:y coords) tile-size tile-size)
                        (Rect/makeXYWH (- (/ scaled-size 2))
                                       (- (/ scaled-size 2))
                                       scaled-size
                                       scaled-size)))
      (.restore canvas))))

(defn draw-orbit-ring
  "Draw a thin orbit ring for a planet around its parent star."
  [^Canvas canvas screen-star-x screen-star-y screen-radius]
  (let [paint (doto (Paint.)
                (.setColor (unchecked-int 0x33FFFFFF))
                (.setMode PaintMode/STROKE)
                (.setStrokeWidth 1.2))]
    (.drawCircle canvas (float screen-star-x) (float screen-star-y) (float screen-radius) paint)
    (.close paint)))

(defn draw-selection-highlight
  "Draw a glowing selection ring around the focused star."
  [^Canvas canvas screen-x screen-y screen-size time]
  (let [pulse (+ 1.0 (* 0.15 (Math/sin (* time 3.0))))
        glow-radius (* screen-size 0.65 pulse)]
    (let [glow-paint (doto (Paint.)
                       (.setColor (unchecked-int 0x44FFD966)))]
      (.drawCircle canvas (float screen-x) (float screen-y) (float glow-radius) glow-paint)
      (.close glow-paint))
    (let [ring-paint (doto (Paint.)
                       (.setColor (unchecked-int 0xFFFFE680))
                       (.setMode PaintMode/STROKE)
                       (.setStrokeWidth 3.0))]
      (.drawCircle canvas (float screen-x) (float screen-y) (float (* screen-size 0.45 pulse)) ring-paint)
      (.close ring-paint))))

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
