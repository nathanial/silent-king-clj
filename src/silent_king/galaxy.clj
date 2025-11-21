(ns silent-king.galaxy
  "Galaxy generation using spiral arms blended with simplex noise for organic structure"
  (:require [silent-king.state :as state])
  (:import [silentking.noise FastNoiseLite FastNoiseLite$NoiseType]
           [java.util Random]))

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
   :arm-edge-tightening 0.35 ; 0..1 - lower keeps outer arms tighter
   :radial-bias 1.18        ; >1.0 concentrates more mass near the core
   :radial-jitter 0.05      ; fraction of max radius used as jitter
   :core-density-power 0.45
   :noise-density-weight 0.08
   :density-jitter 0.025
   ;; Bulge/core tuning
   :core-radius-fraction 0.28
   :core-star-probability 0.2
   :core-density-base 0.78
   :core-density-noise-weight 0.2
   :core-density-jitter 0.08
   :core-density-center-weight 0.75
   :core-density-falloff 3.0
   :core-density-edge 0.22
   :core-radius-distribution 1.35})

(def ^:private planet-config
  {:min-radius 18.0
   :max-radius 80.0
   :min-gap 12.0
   :gap-jitter 10.0
   :min-size 7.0
   :max-size 17.0
   :max-planets 5
   :base-period 80.0
   :period-radius-ref 60.0
   :period-exponent 1.3})

(declare clamp)

(defn- star->rng
  ^Random [{:keys [id]}]
  (let [base (long (or id 0))
        seed (-> (unchecked-multiply 6364136223846793005 base)
                 (unchecked-add 1442695040888963407))]
    (Random. seed)))

(defn- density->planet-count
  ^long [^Random rng density]
  (let [d (clamp (double (or density 0.0)) 0.0 1.0)
        base (cond
               (< d 0.20) 0
               (< d 0.35) 1
               (< d 0.55) 2
               (< d 0.75) 3
               :else 4)
        bonus (if (< (.nextDouble rng) d) 1 0)
        max-planets (:max-planets planet-config)]
    (-> (+ base bonus)
        (min max-planets)
        (max 0))))

(defn- next-orbital-radius
  ^double [^Random rng last-radius]
  (let [{:keys [min-gap gap-jitter max-radius]} planet-config
        gap (+ (double min-gap) (* (double gap-jitter) (.nextDouble rng)))
        proposed (+ (double last-radius) gap)]
    (min (double max-radius) proposed)))

(defn- orbital-period
  ^double [radius]
  (let [{:keys [base-period period-radius-ref period-exponent]} planet-config
        safe-radius (max 1.0 (double radius))
        scale (/ safe-radius (double (max 1.0 (or period-radius-ref 1.0))))]
    (max 2.0 (* (double base-period) (Math/pow scale (double period-exponent))))))

(defn- random-phase
  ^double [^Random rng]
  (* 2.0 Math/PI (.nextDouble rng)))

(defn- random-planet-size
  ^double [^Random rng]
  (let [{:keys [min-size max-size]} planet-config
        span (max 0.0 (- (double max-size) (double min-size)))]
    (+ (double min-size) (* span (.nextDouble rng)))))

(defn- pick-planet-sprite
  [^Random rng planet-sprites]
  (when (seq planet-sprites)
    (nth planet-sprites (.nextInt rng (count planet-sprites)))))

(defn generate-planets-for-star
  "Generate a vector of planet maps orbiting the supplied star."
  [planet-sprites {:keys [id density] :as star}]
  (let [^Random rng (star->rng star)
        planet-count (density->planet-count rng density)
        start-radius (+ (double (:min-radius planet-config))
                        (* (double (:gap-jitter planet-config)) (.nextDouble rng)))]
    (loop [i 0
           radius start-radius
           planets []]
      (if (or (= i planet-count)
              (> radius (:max-radius planet-config)))
        planets
        (let [planet {:star-id id
                      :radius radius
                      :orbital-period (orbital-period radius)
                      :phase (random-phase rng)
                      :size (random-planet-size rng)
                      :sprite-path (pick-planet-sprite rng planet-sprites)
                      :eccentricity 0.0
                      :inclination 0.0}
              next-radius (next-orbital-radius rng radius)
              next-radius (if (>= next-radius (:max-radius planet-config))
                            (inc (double (:max-radius planet-config)))
                            next-radius)]
          (recur (inc i)
                 next-radius
                 (conj planets planet)))))))

(defn planet-position
  "Compute the world-space position of a planet relative to its parent star at time t (seconds).
  Returns {:x :y :angle} where :angle is the orbital angle in radians."
  [{:keys [radius orbital-period phase]} {:keys [x y]} t]
  (let [safe-period (max 0.001 (double (or orbital-period 1.0)))
        omega (/ (* 2.0 Math/PI) safe-period)
        angle (+ (double (or phase 0.0))
                 (* omega (double (or t 0.0))))
        px (+ (double (or x 0.0))
              (* (double (or radius 0.0)) (Math/cos angle)))
        py (+ (double (or y 0.0))
              (* (double (or radius 0.0)) (Math/sin angle)))]
    {:x px
     :y py
     :angle angle}))

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
                arm-randomness arm-randomness-core arm-edge-tightening
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
        tightening (clamp (or arm-edge-tightening 1.0) 0.0 1.0)
        edge-factor (+ (* (- 1.0 normalized-radius) (- 1.0 tightening))
                       tightening)
        arm-index (rand-int arm-count)
        spiral-angle (* normalized-radius arm-turns (* 2.0 Math/PI))
        base-angle (+ (* arm-index angle-step) spiral-angle)
        gaussian-offset (rand-normal 0.0 (* 0.7 effective-spread edge-factor))
        jitter-offset (* randomness-scale edge-factor (- (rand) 0.5))
        offset (+ gaussian-offset jitter-offset)
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

(defn- sample-core-star
  "Return {:x :y :density} sampled inside bright galactic core."
  [noise-gen]
  (let [{:keys [center-x center-y max-radius core-radius-fraction
                core-density-base core-density-noise-weight core-density-jitter
                core-density-center-weight core-density-falloff core-density-edge
                core-radius-distribution]}
        galaxy-config
        radius-fraction (clamp core-radius-fraction 0.02 0.5)
        edge-density (clamp (or core-density-edge 0.0) 0.0 1.0)
        center-weight (clamp (or core-density-center-weight 0.0) 0.0 1.0)
        falloff (max 0.1 (double (or core-density-falloff 1.0)))
        core-radius (* max-radius radius-fraction)
        radius-exponent (max 0.1 (double (or core-radius-distribution 2.0)))
        radius-factor (Math/pow (rand) (/ 1.0 radius-exponent))
        radius (* core-radius radius-factor)
        normalized-radius (if (pos? core-radius) (/ radius core-radius) 0.0)
        center-shape (Math/pow (- 1.0 normalized-radius) falloff)
        secondary-shape (- 1.0 normalized-radius)
        center-prominence (clamp (+ (* center-weight center-shape)
                                    (* (- 1.0 center-weight) secondary-shape))
                                 0.0
                                 1.0)
        structural-density (+ edge-density
                              (* center-prominence (- core-density-base edge-density)))
        angle (* 2.0 Math/PI (rand))
        x (+ center-x (* radius (Math/cos angle)))
        y (+ center-y (* radius (Math/sin angle)))
        noise-density (sample-density noise-gen x y)
        noise-weight (clamp core-density-noise-weight 0.0 1.0)
        structural-weight (- 1.0 noise-weight)
        raw-density (+ (* structural-weight structural-density)
                       (* noise-weight noise-density)
                       (* core-density-jitter (- (rand) 0.5)))
        density (clamp raw-density 0.0 1.0)]
    {:x x :y y :density density}))

(defn- density->size
  "Map density value (0-1) to star size in pixels"
  [density]
  (let [min-size 30
        max-size 40
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

(defn- star-map
  [id {:keys [path]} {:keys [x y density]}]
  (let [size (density->size density)
        rotation-speed (density->rotation-speed density)]
    {:id id
     :x (double x)
     :y (double y)
     :size size
     :density density
     :sprite-path path
     :rotation-speed rotation-speed}))

(defn generate-galaxy
  "Pure galaxy generator. Returns {:stars {...} :planets {...} :next-star-id n :next-planet-id m}."
  [star-images planet-sprites num-stars]
  (when (empty? planet-sprites)
    (throw (ex-info "Planet sprites required for galaxy generation" {:planet-sprites-count 0})))
  (let [noise-gen (create-noise-generator)
        {:keys [core-star-probability]} galaxy-config
        core-prob (clamp (or core-star-probability 0.0) 0.0 1.0)]
    (loop [i 0
           last-id 0
           stars {}]
      (if (= i num-stars)
        (let [{:keys [planets next-id]}
              (reduce (fn [{:keys [planets next-id]} star]
                        (let [generated (generate-planets-for-star planet-sprites star)]
                          (reduce (fn [{:keys [planets next-id]} planet]
                                    (let [id (inc (long next-id))]
                                      {:planets (assoc planets id (assoc planet :id id))
                                       :next-id id}))
                                  {:planets planets :next-id next-id}
                                  generated)))
                      {:planets {} :next-id 0}
                      (vals stars))]
          {:stars stars
           :planets planets
           :next-star-id last-id
           :next-planet-id next-id})
        (let [{:keys [density] :as sample}
              (if (< (rand) core-prob)
                (sample-core-star noise-gen)
                (sample-spiral-star noise-gen))
              base-image (density->star-image star-images density)
              id (inc last-id)
              star (star-map id base-image sample)]
          (recur (inc i)
                 id
                 (assoc stars id star)))))))

(defn generate-galaxy!
  "Generate stars (and their planets) and write them into the world model on game-state."
  [game-state star-images planet-sprites num-stars]
  (println "Generating" num-stars "stars with spiral arm distribution...")
  (let [start-time (System/currentTimeMillis)
        {:keys [stars planets next-star-id next-planet-id] :as result}
        (generate-galaxy star-images planet-sprites num-stars)
        elapsed (- (System/currentTimeMillis) start-time)]
    (state/set-world! game-state {:stars stars
                                  :planets planets
                                  :hyperlanes []
                                  :neighbors-by-star-id {}
                                  :next-star-id next-star-id
                                  :next-planet-id next-planet-id
                                  :next-hyperlane-id 0})
    (println "Generated" (count stars) "stars and" (count planets) "planets in" elapsed "ms")
    result))
