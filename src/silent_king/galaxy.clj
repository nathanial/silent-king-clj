(ns silent-king.galaxy
  "Galaxy generation using spiral arms blended with simplex noise for organic structure"
  (:require [silent-king.state :as state])
  (:import [silentking.noise FastNoiseLite FastNoiseLite$NoiseType]))

;; Galaxy generation parameters
(def ^:private galaxy-config
  {:center-x 2000.0
   :center-y 2000.0
   :max-radius 10000.0
   :noise-seed 42
   :noise-frequency 0.0003
   :num-octaves 3
   :octave-gain 0.5
   :octave-lacunarity 2.0
   ;; Spiral arm specific tuning
   :arm-count 4
   :arm-turns 0.85          ; revolutions from core to edge
   :core-arm-spread 0.35    ; radians near the core before arms widen
   :arm-spread 0.85         ; radians at the edge, controls arm thickness
   :arm-flare-power 1.35    ; higher = arms widen more slowly with radius
   :arm-alignment-weight 0.82
   :arm-alignment-sharpness 1.8
   :arm-randomness-core 0.05
   :arm-randomness 0.18
   :radial-bias 1.18        ; >1.0 concentrates more mass near the core
   :radial-jitter 0.05      ; fraction of max radius used as jitter
   :core-density-power 0.45
   :noise-density-weight 0.08
   :density-jitter 0.025})

(defn create-noise-generator
  "Create a FastNoiseLite instance configured for OpenSimplex2 noise"
  ([] (create-noise-generator (:noise-seed galaxy-config)))
  ([seed]
   (let [noise (FastNoiseLite. seed)]
     (.SetNoiseType noise FastNoiseLite$NoiseType/OpenSimplex2)
     (.SetFrequency noise (float (:noise-frequency galaxy-config)))
     noise)))

(defn sample-density
  "Sample multi-octave noise at position (x, y) to get density value.
  Returns value between -1 and 1, but typically normalized to 0-1 range."
  [noise-gen x y]
  (let [num-octaves (:num-octaves galaxy-config)
        gain (:octave-gain galaxy-config)
        lacunarity (:octave-lacunarity galaxy-config)]
    (loop [octave 0
           amplitude 1.0
           frequency 1.0
           total 0.0
           max-value 0.0]
      (if (>= octave num-octaves)
        ;; Normalize to 0-1 range
        (/ (+ total 1.0) 2.0)
        (let [sample (.GetNoise noise-gen
                                (* x frequency)
                                (* y frequency))
              contribution (* sample amplitude)]
          (recur (inc octave)
                 (* amplitude gain)
                 (* frequency lacunarity)
                 (+ total contribution)
                 (+ max-value amplitude)))))))

(defn- clamp
  [value min-value max-value]
  (-> value
      (max min-value)
      (min max-value)))

(defn- rand-normal
  "Box-Muller transform for gaussian random values"
  ([] (rand-normal 0.0 1.0))
  ([mean stddev]
   (let [u1 (max Double/MIN_VALUE (rand))
         u2 (rand)
         magnitude (Math/sqrt (* -2.0 (Math/log u1)))
         theta (* 2.0 Math/PI u2)
         z (* magnitude (Math/cos theta))]
     (+ mean (* stddev z)))))

(defn- sample-spiral-star
  "Return {:x :y :density} sampled along spiral arms."
  [noise-gen]
  (let [{:keys [center-x center-y max-radius arm-count arm-turns arm-spread core-arm-spread
                arm-flare-power arm-alignment-weight arm-alignment-sharpness
                arm-randomness arm-randomness-core
                radial-bias radial-jitter core-density-power
                noise-density-weight density-jitter]}
        galaxy-config
        arm-count (max arm-count 1)
        angle-step (/ (* 2.0 Math/PI) arm-count)
        ;; Draw radius with slight preference for the core and add jitter to break symmetry
        radius-factor (Math/pow (Math/sqrt (rand)) radial-bias)
        base-radius (* max-radius radius-factor)
        radius (+ base-radius (* max-radius radial-jitter (- (rand) 0.5)))
        radius (clamp radius 0.0 max-radius)
        normalized-radius (if (pos? max-radius) (/ radius max-radius) 0.0)
        flare-power (max 0.1 (double arm-flare-power))
        spread-blend (Math/pow normalized-radius flare-power)
        base-spread (max 1.0e-3 (or core-arm-spread arm-spread))
        spread-range (max 0.0 (- arm-spread base-spread))
        effective-spread (+ base-spread (* spread-range spread-blend))
        randomness-core (max 0.0 (double (or arm-randomness-core 0.0)))
        randomness-edge (max randomness-core (double (or arm-randomness 0.0)))
        randomness-scale (+ randomness-core
                            (* (- randomness-edge randomness-core) spread-blend))
        arm-index (rand-int arm-count)
        spiral-angle (* normalized-radius arm-turns (* 2.0 Math/PI))
        base-angle (+ (* arm-index angle-step) spiral-angle)
        offset (+ (rand-normal 0.0 (* 0.7 effective-spread))
                  (* randomness-scale (- (rand) 0.5)))
        angle (+ base-angle offset)
        x (+ center-x (* radius (Math/cos angle)))
        y (+ center-y (* radius (Math/sin angle)))
        ;; Reward stars that stay close to the ideal arm centerline
        spread (max 1.0e-3 effective-spread)
        gaussian (Math/exp (* -0.5 (Math/pow (/ offset spread) 2.0)))
        sharpness (max 0.5 (double (or arm-alignment-sharpness 1.0)))
        arm-alignment (Math/pow gaussian sharpness)
        radial-density (Math/pow (- 1.0 normalized-radius) core-density-power)
        alignment-weight (clamp arm-alignment-weight 0.0 1.0)
        structure-density (+ (* alignment-weight arm-alignment)
                             (* (- 1.0 alignment-weight) radial-density))
        noise-density (sample-density noise-gen x y)
        noise-weight (clamp noise-density-weight 0.0 1.0)
        structural-weight (- 1.0 noise-weight)
        raw-density (+ (* structural-weight structure-density)
                       (* noise-weight noise-density)
                       (* density-jitter (- (rand) 0.5)))
        density (clamp raw-density 0.0 1.0)]
    {:x x :y y :density density}))

(defn- density->size
  "Map density value (0-1) to star size in pixels"
  [density]
  (let [min-size 40
        max-size 50
        size-range (- max-size min-size)]
    (+ min-size (* density density size-range))))

(defn- density->rotation-speed
  "Map density value (0-1) to rotation speed in rad/s"
  [density]
  (let [min-speed 0.3
        max-speed 2.5
        speed-range (- max-speed min-speed)]
    (+ min-speed (* density speed-range))))

(defn- density->star-image
  "Select star image based on density. Higher density = brighter/larger stars"
  [base-images density]
  (let [num-images (count base-images)
        ;; Bias toward brighter stars in dense regions
        brightness-bias (Math/pow density 0.7)
        index (int (* brightness-bias num-images))]
    (nth base-images (min index (dec num-images)))))

(defn generate-galaxy-entities!
  "Generate star entities using spiral arm distribution blended with noise for texture."
  [game-state base-images num-stars]
  (println "Generating" num-stars "star entities with spiral arm distribution...")
  (let [noise-gen (create-noise-generator)
        start-time (System/currentTimeMillis)]
    (dotimes [_ num-stars]
      (let [{:keys [x y density]} (sample-spiral-star noise-gen)
            base-image (density->star-image base-images density)
            size (density->size density)
            rotation-speed (density->rotation-speed density)
            entity (state/create-entity
                    :position {:x x :y y}
                    :renderable {:path (:path base-image)}
                    :transform {:size size
                               :rotation 0.0}
                    :physics {:rotation-speed rotation-speed})]
        (state/add-entity! game-state entity)))

    (let [elapsed (- (System/currentTimeMillis) start-time)]
      (println "Generated" (count (state/get-all-entities game-state))
               "stars in" elapsed "ms"))))
