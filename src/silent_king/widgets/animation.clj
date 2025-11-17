(ns silent-king.widgets.animation
  "Animation system for smooth transitions"
  (:require [silent-king.state :as state]))

(set! *warn-on-reflection* true)

;; =============================================================================
;; Easing Functions
;; =============================================================================

(defn ease-in-out
  "Ease-in-out cubic easing function for smooth transitions"
  [t]
  (if (< t 0.5)
    (* 4 t t t)
    (+ 1 (* 4 (dec t) (dec t) (dec t)))))

(defn ease-out
  "Ease-out cubic easing function"
  [t]
  (+ 1 (* (dec t) (dec t) (dec t))))

(defn ease-in
  "Ease-in cubic easing function"
  [t]
  (* t t t))

(defn linear
  "Linear interpolation (no easing)"
  [t]
  t)

(defn apply-easing
  "Apply an easing function to a progress value (0.0 to 1.0)"
  [progress easing]
  (case easing
    :linear (linear progress)
    :ease-in (ease-in progress)
    :ease-out (ease-out progress)
    :ease-in-out (ease-in-out progress)
    (linear progress)))

;; =============================================================================
;; Interpolation
;; =============================================================================

(defn interpolate
  "Interpolate between two values based on progress (0.0 to 1.0)"
  [from to progress]
  (+ from (* (- to from) progress)))

;; =============================================================================
;; Camera Animation
;; =============================================================================

(defn start-camera-pan!
  "Start a smooth camera pan animation to target position.
  Duration is in seconds, easing defaults to :ease-in-out"
  ([game-state target-pan-x target-pan-y duration]
   (start-camera-pan! game-state target-pan-x target-pan-y duration :ease-in-out))
  ([game-state target-pan-x target-pan-y duration easing]
   (let [camera (state/get-camera game-state)
         time-state (state/get-time game-state)
         current-time (:current-time time-state)]
     (swap! game-state assoc :camera-animation
            {:active true
             :start-time current-time
             :duration duration
             :easing easing
             :from-pan-x (:pan-x camera)
             :from-pan-y (:pan-y camera)
             :to-pan-x target-pan-x
             :to-pan-y target-pan-y}))))

(defn update-camera-animation!
  "Update camera animation if one is active.
  Should be called each frame before rendering."
  [game-state]
  (when-let [anim (:camera-animation @game-state)]
    (when (:active anim)
      (let [time-state (state/get-time game-state)
            current-time (:current-time time-state)
            elapsed (- current-time (:start-time anim))
            progress (min 1.0 (/ elapsed (:duration anim)))
            eased-progress (apply-easing progress (:easing anim))
            new-pan-x (interpolate (:from-pan-x anim) (:to-pan-x anim) eased-progress)
            new-pan-y (interpolate (:from-pan-y anim) (:to-pan-y anim) eased-progress)]

        ;; Update camera position
        (state/update-camera! game-state assoc
                             :pan-x new-pan-x
                             :pan-y new-pan-y)

        ;; Remove animation when complete
        (when (>= progress 1.0)
          (swap! game-state assoc-in [:camera-animation :active] false))))))

(defn camera-animation-active?
  "Check if a camera animation is currently running"
  [game-state]
  (get-in @game-state [:camera-animation :active] false))
