(ns silent-king.widgets.layout
  "Layout invalidation and processing system"
  (:require [silent-king.state :as state]))

(set! *warn-on-reflection* true)

(defmulti perform-layout
  "Apply layout for a widget entity that has been marked dirty."
  (fn [_game-state _entity-id widget-entity]
    (get-in (state/get-component widget-entity :widget) [:type])))

(defmethod perform-layout :default
  [_ _ _]
  nil)

(defn mark-dirty!
  "Mark a widget entity as needing a layout pass."
  [game-state entity-id]
  (swap! game-state update-in [:widgets :layout-dirty] (fnil conj #{}) entity-id))

(defn process-layouts!
  "Process all widgets that have been marked dirty."
  [game-state]
  (let [dirty (seq (get-in @game-state [:widgets :layout-dirty]))]
    (when dirty
      (swap! game-state assoc-in [:widgets :layout-dirty] #{})
      (doseq [entity-id dirty
              :let [entity (state/get-entity game-state entity-id)]
              :when entity]
        (perform-layout game-state entity-id entity)))))
