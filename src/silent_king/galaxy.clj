(ns silent-king.galaxy
  "Galaxy generation using simplex noise for clustered star distribution"
  (:require [silent-king.state :as state])
  (:import [silentking.noise FastNoiseLite FastNoiseLite$NoiseType]))

;; Galaxy generation parameters
(def ^:private galaxy-config
  {:center-x 2000.0
   :center-y 2000.0
   :max-radius 10000.0
   :density-threshold 0.3  ; Only spawn stars where noise > this value
   :noise-seed 42
   :noise-frequency 0.0003  ; Lower = larger features
   :num-octaves 3
   :octave-gain 0.5
   :octave-lacunarity 2.0})

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

(defn- density->size
  "Map density value (0-1) to star size in pixels"
  [density]
  (let [min-size 60
        max-size 180
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
  "Generate star entities with noise-based clustered distribution.
  Uses rejection sampling to create dense clusters with sparse regions."
  [game-state base-images num-stars]
  (println "Generating" num-stars "star entities with clustered noise distribution...")
  (let [noise-gen (create-noise-generator)
        {:keys [center-x center-y max-radius density-threshold]} galaxy-config
        start-time (System/currentTimeMillis)]

    ;; Use rejection sampling to generate stars only in high-density regions
    (loop [generated 0
           attempts 0
           max-attempts (* num-stars 5)]  ; Safety limit
      (when (and (< generated num-stars)
                 (< attempts max-attempts))
        (let [;; Random position within disk
              angle (* (rand) 2.0 Math/PI)
              radius (* (Math/sqrt (rand)) max-radius)
              x (+ center-x (* radius (Math/cos angle)))
              y (+ center-y (* radius (Math/sin angle)))

              ;; Sample density at this position
              density (sample-density noise-gen x y)]

          (if (> density density-threshold)
            ;; Accept this position - create star entity
            (let [;; Normalize density to 0-1 range above threshold
                  normalized-density (/ (- density density-threshold)
                                       (- 1.0 density-threshold))
                  base-image (density->star-image base-images normalized-density)
                  size (density->size normalized-density)
                  rotation-speed (density->rotation-speed normalized-density)
                  entity (state/create-entity
                          :position {:x x :y y}
                          :renderable {:path (:path base-image)}
                          :transform {:size size
                                     :rotation 0.0}
                          :physics {:rotation-speed rotation-speed})]
              (state/add-entity! game-state entity)
              (recur (inc generated) (inc attempts) max-attempts))

            ;; Reject this position - try again
            (recur generated (inc attempts) max-attempts)))))

    (let [elapsed (- (System/currentTimeMillis) start-time)]
      (println "Generated" (count (state/get-all-entities game-state))
               "stars in" elapsed "ms"))))
