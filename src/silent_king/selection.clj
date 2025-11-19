(ns silent-king.selection
  "Helpers for selecting stars in the world and building inspector data."
  (:require [silent-king.camera :as camera]
            [silent-king.state :as state]))

(set! *warn-on-reflection* true)

(def ^:const pick-components [:position :renderable :transform :star])
(def ^:const min-hit-radius 10.0)
(def ^:const hit-scale 0.55)

(defn star-label
  [star-id]
  (if (number? star-id)
    (format "Star #%d" (long star-id))
    "Star"))

(defn hyperlane-connections
  "Return a sorted vector of up to 8 neighboring hyperlane connections."
  [game-state star-id]
  (let [entities (state/filter-entities-with game-state [:hyperlane])
        [sx sy] (if-let [star (state/get-entity game-state star-id)]
                  (let [pos (state/get-component star :position)]
                    [(:x pos) (:y pos)])
                  [nil nil])]
    (->> entities
         (keep (fn [[_ entity]]
                 (let [{:keys [from-id to-id]} (state/get-component entity :hyperlane)
                       neighbor (cond
                                  (= from-id star-id) to-id
                                  (= to-id star-id) from-id
                                  :else nil)]
                   (when neighbor
                     (let [neighbor-entity (state/get-entity game-state neighbor)
                           neighbor-pos (state/get-component neighbor-entity :position)
                           nx (:x neighbor-pos)
                           ny (:y neighbor-pos)
                           dist (when (and (number? sx) (number? sy)
                                           (number? nx) (number? ny))
                                  (let [dx (- (double nx) (double sx))
                                        dy (- (double ny) (double sy))]
                                    (Math/sqrt (+ (* dx dx) (* dy dy)))))]
                       {:neighbor-id neighbor
                        :label (star-label neighbor)
                        :distance dist})))))
         (sort-by (fn [conn]
                    (or (:distance conn) Double/POSITIVE_INFINITY)))
         (take 8)
         vec)))

(defn star-details
  "Build a detail map for the supplied star-id."
  [game-state star-id]
  (when-let [entity (state/get-entity game-state star-id)]
    (let [position (state/get-component entity :position)
          transform (state/get-component entity :transform)
          star (state/get-component entity :star)
          physics (state/get-component entity :physics)
          renderable (state/get-component entity :renderable)]
      {:star-id star-id
       :name (star-label star-id)
       :position position
       :size (:size transform)
       :density (:density star)
       :rotation-speed (:rotation-speed physics)
       :sprite (:path renderable)
       :connections (hyperlane-connections game-state star-id)})))

(defn selected-view
  "Return the current selection view model (freshly computed)."
  [game-state]
  (when-let [star-id (state/selected-star-id game-state)]
    (star-details game-state star-id)))

(defn- candidate-hit
  [camera screen-x screen-y [entity-id entity]]
  (let [pos (state/get-component entity :position)
        transform (state/get-component entity :transform)
        zoom (:zoom camera)
        pan-x (:pan-x camera)
        pan-y (:pan-y camera)
        world-x (double (or (:x pos) 0.0))
        world-y (double (or (:y pos) 0.0))
        base-size (double (or (:size transform) 30.0))
        screen-star-x (camera/transform-position world-x zoom pan-x)
        screen-star-y (camera/transform-position world-y zoom pan-y)
        dx (- (double screen-x) screen-star-x)
        dy (- (double screen-y) screen-star-y)
        distance (Math/sqrt (+ (* dx dx) (* dy dy)))
        screen-size (camera/transform-size base-size zoom)
        hit-radius (max min-hit-radius (* hit-scale screen-size))]
    (when (<= distance hit-radius)
      {:star-id entity-id
       :distance distance
       :world {:x world-x :y world-y}})))

(defn pick-star
  "Return the closest star under the supplied screen coordinate, if any."
  [game-state screen-x screen-y]
  (let [camera (state/get-camera game-state)]
    (reduce (fn [best candidate]
              (let [hit (candidate-hit camera screen-x screen-y candidate)]
                (if (and hit
                         (or (nil? best)
                             (< (:distance hit) (:distance best))))
                  hit
                  best)))
            nil
            (state/filter-entities-with game-state pick-components))))

(defn- screen->world
  [camera screen-x screen-y]
  {:x (camera/inverse-transform-position screen-x (:zoom camera) (:pan-x camera))
   :y (camera/inverse-transform-position screen-y (:zoom camera) (:pan-y camera))})

(defn handle-screen-click!
  "Select the star under the given screen coordinate, or clear selection.
   Returns true if a star was selected."
  [game-state screen-x screen-y]
  (let [camera (state/get-camera game-state)
        world-click (screen->world camera screen-x screen-y)]
    (if-let [hit (pick-star game-state screen-x screen-y)]
      (do
        (state/set-selection! game-state {:star-id (:star-id hit)
                                          :last-world-click world-click})
        (state/show-star-inspector! game-state)
        true)
      (do
        (state/clear-selection! game-state)
        (state/hide-star-inspector! game-state)
        false))))
