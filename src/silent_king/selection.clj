(ns silent-king.selection
  "Helpers for selecting stars in the world and building inspector data."
  (:require [silent-king.camera :as camera]
            [silent-king.state :as state]))

(set! *warn-on-reflection* true)

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
  (let [star (state/star-by-id game-state star-id)
        sx (:x star)
        sy (:y star)
        neighbors (or (get (state/neighbors-by-star-id game-state) star-id)
                      (map (fn [{:keys [from-id to-id] :as hyperlane}]
                             (cond
                               (= from-id star-id) {:neighbor-id to-id :hyperlane hyperlane}
                               (= to-id star-id) {:neighbor-id from-id :hyperlane hyperlane}
                               :else nil))
                           (state/hyperlanes game-state)))]
    (->> neighbors
         (keep (fn [{:keys [neighbor-id] :as conn}]
                 (when-let [neighbor (state/star-by-id game-state neighbor-id)]
                   (let [nx (:x neighbor)
                         ny (:y neighbor)
                         dist (when (and (number? sx) (number? sy)
                                         (number? nx) (number? ny))
                                (let [dx (- (double nx) (double sx))
                                      dy (- (double ny) (double sy))]
                                  (Math/sqrt (+ (* dx dx) (* dy dy)))))]
                     {:neighbor-id neighbor-id
                      :label (star-label neighbor-id)
                      :distance dist
                      :hyperlane (:hyperlane conn)}))))
         (sort-by (fn [conn]
                    (or (:distance conn) Double/POSITIVE_INFINITY)))
         (take 8)
         vec)))

(defn star-details
  "Build a detail map for the supplied star-id."
  [game-state star-id]
  (when-let [{:keys [x y size density rotation-speed sprite-path]} (state/star-by-id game-state star-id)]
    {:star-id star-id
     :name (star-label star-id)
     :position {:x x :y y}
     :size size
     :density density
     :rotation-speed rotation-speed
     :sprite sprite-path
     :connections (hyperlane-connections game-state star-id)}))

(defn selected-view
  "Return the current selection view model (freshly computed)."
  [game-state]
  (when-let [star-id (state/selected-star-id game-state)]
    (star-details game-state star-id)))

(defn- candidate-hit
  [camera screen-x screen-y {:keys [id x y size]}]
  (let [zoom (:zoom camera)
        pan-x (:pan-x camera)
        pan-y (:pan-y camera)
        world-x (double (or x 0.0))
        world-y (double (or y 0.0))
        base-size (double (or size 30.0))
        screen-star-x (camera/transform-position world-x zoom pan-x)
        screen-star-y (camera/transform-position world-y zoom pan-y)
        dx (- (double screen-x) screen-star-x)
        dy (- (double screen-y) screen-star-y)
        distance (Math/sqrt (+ (* dx dx) (* dy dy)))
        screen-size (camera/transform-size base-size zoom)
        hit-radius (max min-hit-radius (* hit-scale screen-size))]
    (when (<= distance hit-radius)
      {:star-id id
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
            (state/star-seq game-state))))

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
