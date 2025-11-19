(ns silent-king.reactui.components.minimap
  "Minimap component definition."
  (:require [silent-king.minimap.math :as minimap-math]
            [silent-king.state :as state]))

(set! *warn-on-reflection* true)

(def ^:const default-panel-bounds
  {:width 200.0
   :height 200.0})

(defn- get-stars
  [game-state]
  (->> (state/filter-entities-with game-state [:position])
       (map second)
       (map (fn [e]
              (let [pos (state/get-component e :position)]
                {:x (double (:x pos))
                 :y (double (:y pos))
                 :color (state/get-component e :color)})))))

(defn minimap-props
  [game-state]
  (let [stars (get-stars game-state)
        camera (state/get-camera game-state)
        viewport (state/ui-viewport game-state)
        world-bounds (minimap-math/calculate-world-bounds stars)
        viewport-rect (minimap-math/viewport-rect camera viewport)
        minimap-visible? (state/minimap-visible? game-state)]
    {:stars stars
     :world-bounds world-bounds
     :viewport-rect viewport-rect
     :visible? minimap-visible?}))

(defn minimap
  [{:keys [visible?] :as props}]
  (when visible?
    {:type :minimap
     :props props
     :children []}))

