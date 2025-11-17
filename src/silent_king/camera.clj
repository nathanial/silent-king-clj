(ns silent-king.camera
  "Shared camera math helpers used across rendering, interaction, and minimap code.")

(set! *warn-on-reflection* true)

(def ^:const position-exponent 2.5)
(def ^:const size-exponent 1.3)

(defn zoom->position-scale
  "Convert zoom to the positional scale factor used in the non-linear transform."
  [zoom]
  (Math/pow zoom position-exponent))

(defn zoom->size-scale
  "Convert zoom to the size scaling factor."
  [zoom]
  (Math/pow zoom size-exponent))

(defn transform-position
  "Transform a world coordinate into a screen coordinate using the camera scale/pan."
  [world-pos zoom pan]
  (+ (* world-pos (zoom->position-scale zoom)) pan))

(defn transform-size
  "Transform a base size into the rendered size."
  [base-size zoom]
  (* base-size (zoom->size-scale zoom)))

(defn inverse-transform-position
  "Invert transform-position. Converts a screen coordinate back into world space."
  [screen-pos zoom pan]
  (/ (- screen-pos pan) (zoom->position-scale zoom)))

(defn center-pan
  "Return the pan offset required to center the supplied world coordinate on a screen axis.
  screen-span is the width/height (in pixels) for the axis being centered."
  [world zoom screen-span]
  (let [scale (zoom->position-scale zoom)
        screen-center (/ (double screen-span) 2.0)]
    (- screen-center (* world scale))))
