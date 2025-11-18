(ns silent-king.reactui.app
  "Bridges game state to the Reactified UI tree."
  (:require [silent-king.reactui.components.control-panel :as control-panel]
            [silent-king.reactui.core :as ui-core]
            [silent-king.state :as state]))

(set! *warn-on-reflection* true)

(defn control-panel-props
  [game-state]
  (let [camera (state/get-camera game-state)
        metrics (get-in @game-state [:metrics :performance :latest])]
    {:zoom (double (or (:zoom camera) 1.0))
     :hyperlanes-enabled? (state/hyperlanes-enabled? game-state)
     :metrics {:fps (double (or (:fps metrics) 0.0))
               :visible-stars (long (or (:visible-stars metrics) 0))
               :draw-calls (long (or (:draw-calls metrics) 0))}}))

(defn root-tree
  [game-state]
  (control-panel/control-panel (control-panel-props game-state)))

(defn render!
  "Render the full Reactified UI."
  [canvas viewport game-state]
  (ui-core/render-ui-tree {:canvas canvas
                           :tree (root-tree game-state)
                           :viewport viewport}))
