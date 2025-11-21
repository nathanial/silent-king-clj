(ns silent-king.reactui.primitives.window
  "Window primitive: normalization, layout, rendering, and pointer handling."
  (:require [silent-king.reactui.core :as core]
            [silent-king.reactui.events :as ui-events]
            [silent-king.reactui.interaction :as interaction]
            [silent-king.reactui.layout :as layout]
            [silent-king.reactui.render :as render]
            [silent-king.render.commands :as commands]
            [silent-king.color :as color]))

(set! *warn-on-reflection* true)

(defmethod core/normalize-tag :window
  [_ props child-forms]
  ((core/branch-normalizer :window) props child-forms))

(defmethod layout/layout-node :window
  [node context]
  (let [props (:props node)
        bounds* (layout/resolve-bounds node context)
        min-width (double (max 120.0 (or (:min-width props) 200.0)))
        min-height (double (max 80.0 (or (:min-height props) 200.0)))
        header-height (double (max 20.0 (or (:header-height props) 32.0)))
        resizable? (if (contains? props :resizable?)
                     (boolean (:resizable? props))
                     true)
        minimized? (boolean (:minimized? props))
        width (max min-width (double (or (:width bounds*) min-width)))
        stored-height (double (or (:height bounds*) min-height))
        desired-height (max min-height stored-height)
        final-height (if minimized?
                       header-height
                       desired-height)
        final-bounds (assoc bounds* :width width :height final-height)
        padding (layout/expand-padding (:content-padding props))
        content-width (max 0.0 (- width (:left padding) (:right padding)))
        content-height (max 0.0 (- final-height header-height (:top padding) (:bottom padding)))
        content-bounds {:x (+ (:x final-bounds) (:left padding))
                        :y (+ (:y final-bounds) header-height (:top padding))
                        :width content-width
                        :height content-height}
        header-bounds {:x (:x final-bounds)
                       :y (:y final-bounds)
                       :width width
                       :height header-height}
        control-size (double (max 12.0 (or (:control-size props) 18.0)))
        minimize-bounds {:x (- (+ (:x final-bounds) width) control-size 8.0)
                         :y (+ (:y final-bounds)
                               (/ (- header-height control-size) 2.0))
                         :width control-size
                         :height control-size}
        resize-size 16.0
        resize-bounds (when resizable?
                        {:x (- (+ (:x final-bounds) width) resize-size)
                         :y (- (+ (:y final-bounds) final-height) resize-size)
                         :width resize-size
                         :height resize-size})
        viewport (:viewport context)
        child-context {:viewport viewport
                       :bounds content-bounds}
        laid-out-children (if minimized?
                            []
                            (mapv #(layout/layout-node % child-context) (:children node)))]
    (assoc node
           :layout {:bounds final-bounds
                    :window {:header header-bounds
                             :content content-bounds
                             :minimize minimize-bounds
                             :resize resize-bounds
                             :resizable? resizable?
                             :minimized? minimized?
                             :header-height header-height
                             :stored-height desired-height
                             :constraints {:min-width min-width
                                           :min-height min-height}}}
           :children laid-out-children)))

(defn- active-window-kind?
  [node kind]
  (let [active (render/active-interaction)]
    (and (= kind (:type active))
         (= (:node active) node))))

(defn plan-window
  [context node]
  (let [{:keys [title background-color header-color border-color content-background-color title-color button-icon-color]} (:props node)
        {:keys [bounds window]} (:layout node)
        {:keys [header content minimize resize resizable? minimized?]} window
        body-color (or (color/ensure background-color) (color/hsv 222.9 29.8 18.4 0.94))
        header-base (or (color/ensure header-color) (color/hsv 228.8 36.4 17.3))
        border-color (or (color/ensure border-color) (color/hsv 225 26.7 23.5 0.5))
        content-color (or (color/ensure content-background-color)
                          (render/adjust-color body-color 1.07))
        text-color (or (color/ensure title-color) (color/hsv 0 0 100))
        icon-color (or (color/ensure button-icon-color) text-color)
        header-hover? (and header (render/pointer-in-bounds? header)
                           (not (and minimize (render/pointer-in-bounds? minimize))))
        header-active? (active-window-kind? node :window-move)
        header-fill (cond header-active? (render/adjust-color header-base 0.9)
                          header-hover? (render/adjust-color header-base 1.08)
                          :else header-base)
        minimize-hover? (and minimize (render/pointer-in-bounds? minimize))
        minimize-active? (active-window-kind? node :window-minimize)
        minimize-fill (cond minimize-active? (render/adjust-color header-base 0.85)
                            minimize-hover? (render/adjust-color header-base 1.18)
                            :else (render/adjust-color header-base 1.02))
        resize-hover? (and resize (render/pointer-in-bounds? resize))
        resize-active? (active-window-kind? node :window-resize)
        resize-color (cond resize-active? (render/adjust-color border-color 0.85)
                           resize-hover? (render/adjust-color border-color 1.2)
                           :else border-color)
        content-bounds (when (and content (pos? (:width content)) (pos? (:height content)))
                         {:x (:x content)
                          :y (:y content)
                          :width (:width content)
                          :height (:height content)})
        header-bounds (when header
                        {:x (:x header)
                         :y (:y header)
                         :width (:width header)
                         :height (:height header)})
        minimize-bounds (when minimize
                          {:x (:x minimize)
                           :y (:y minimize)
                           :width (:width minimize)
                           :height (:height minimize)})
        resize-bounds (when resize
                        {:x (:x resize)
                         :y (:y resize)
                         :width (:width resize)
                         :height (:height resize)})
        child-commands (mapcat #(render/plan-node context %) (:children node))]
    (cond-> [(commands/rect bounds {:fill-color body-color})]
      header-bounds (conj (commands/rect header-bounds {:fill-color header-fill}))
      (and content-bounds (not minimized?)) (conj (commands/rect content-bounds {:fill-color content-color}))
      minimize-bounds (into (let [padding 4.0
                                  mx (+ (:x minimize) padding)
                                  my (+ (:y minimize) padding)
                                  mw (- (:width minimize) (* 2.0 padding))
                                  mh (- (:height minimize) (* 2.0 padding))
                                  line-y (+ my (* mh 0.65))]
                              [(commands/rect minimize-bounds {:fill-color minimize-fill})
                               (if minimized?
                                 (commands/rect {:x mx :y my :width mw :height mh}
                                                {:stroke-color icon-color
                                                 :stroke-width 2.0})
                                 (commands/line {:x mx :y line-y}
                                                {:x (+ mx mw) :y line-y}
                                                {:stroke-color icon-color
                                                 :stroke-width 2.0}))]))
      (pos? (:width bounds)) (conj (commands/rect bounds {:stroke-color border-color
                                                          :stroke-width 1.0}))
      header-bounds (conj (commands/text {:text (or title "Window")
                                          :position {:x (+ (:x header) 12.0)
                                                     :y (+ (:y header) (/ (:height header) 2.0) 6.0)}
                                          :font {:size 16.0}
                                          :color text-color}))
      (and resizable? resize-bounds (not minimized?))
      (into (let [rx (+ (:x resize) (:width resize))
                  ry (+ (:y resize) (:height resize))]
              [(commands/line {:x (- rx 12.0) :y ry}
                              {:x rx :y (- ry 12.0)}
                              {:stroke-color resize-color
                               :stroke-width 1.5})
               (commands/line {:x (- rx 8.0) :y ry}
                              {:x rx :y (- ry 8.0)}
                              {:stroke-color resize-color
                               :stroke-width 1.5})]))
      (and (seq (:children node))
           content-bounds
           (pos? (:width content))
           (pos? (:height content))
           (not minimized?))
      (into (concat [(commands/save)
                     (commands/clip-rect content-bounds)]
                    child-commands
                    [(commands/restore)]))
      (and (seq (:children node))
           (or (not content-bounds) minimized?))
      (into child-commands))))

(defmethod render/plan-node :window
  [context node]
  (plan-window context node))

(defn- find-node-path
  [node target]
  (when node
    (cond
      (identical? node target) [node]
      (= node target) [node]
      :else (some (fn [child]
                    (when-let [path (find-node-path child target)]
                      (cons node path)))
                  (:children node)))))

(defn containing-window
  [target]
  (when-let [root (core/current-layout)]
    (when-let [path (find-node-path root target)]
      (->> path
           butlast
           (filter #(= :window (:type %)))
           last))))

(defn start-window-move!
  [node px py]
  (let [bounds (layout/bounds node)
        window-layout (get-in node [:layout :window])
        stored-height (or (:stored-height window-layout) (:height bounds))
        enriched-bounds (assoc bounds :stored-height stored-height)]
    (core/capture-node! node)
    (core/set-active-interaction! node :window-move
                                  {:value {:start-pointer {:x px :y py}
                                           :start-bounds enriched-bounds}})
    true))

(defn delegate-minimap-drag-to-window!
  [node px py]
  (when-let [window (containing-window node)]
    (start-window-move! window px py)))

(defn dispatch-window-bounds!
  [node game-state bounds]
  (when-let [event (-> node :props :on-change-bounds)]
    (when (vector? event)
      (ui-events/dispatch-event! game-state (conj event bounds)))))

(defn dispatch-window-toggle!
  [node game-state]
  (when-let [event (-> node :props :on-toggle-minimized)]
    (when (vector? event)
      (ui-events/dispatch-event! game-state event))))

(defn dispatch-bring-to-front!
  [node game-state]
  (when-let [event (-> node :props :on-bring-to-front)]
    (when (vector? event)
      (ui-events/dispatch-event! game-state event))))

(defn handle-window-pointer-down!
  [node game-state px py]
  (when-let [region (interaction/window-region node px py)]
    (let [bounds (layout/bounds node)
          window-layout (get-in node [:layout :window])
          constraints (:constraints window-layout)
          stored-height (or (:stored-height window-layout) (:height bounds))
          enriched-bounds (assoc bounds :stored-height stored-height)]
      (case (:kind region)
        :move (do
                (dispatch-bring-to-front! node game-state)
                (start-window-move! node px py))
        :resize (do
                  (dispatch-bring-to-front! node game-state)
                  (core/capture-node! node)
                  (core/set-active-interaction! node :window-resize
                                                {:value {:start-pointer {:x px :y py}
                                                         :start-bounds enriched-bounds
                                                         :constraints constraints}})
                  true)
        :minimize (do
                    (dispatch-bring-to-front! node game-state)
                    (core/capture-node! node)
                    (core/set-active-interaction! node :window-minimize
                                                  {:value {:button-bounds (:bounds region)}})
                    true)
        false))))

(defn handle-window-pointer-drag!
  [node game-state px py]
  (when-let [active (core/active-interaction)]
    (when (= (:node active) node)
      (case (:type active)
        :window-move
        (let [{:keys [start-pointer start-bounds]} (:value active)
              dx (- px (double (:x start-pointer)))
              dy (- py (double (:y start-pointer)))
              stored-height (double (or (:stored-height start-bounds) (:height start-bounds)))
              new-bounds {:x (+ (double (:x start-bounds)) dx)
                          :y (+ (double (:y start-bounds)) dy)
                          :width (double (:width start-bounds))
                          :height stored-height}]
          (dispatch-window-bounds! node game-state new-bounds)
          true)

        :window-resize
        (let [{:keys [start-pointer start-bounds constraints]} (:value active)
              dx (- px (double (:x start-pointer)))
              dy (- py (double (:y start-pointer)))
              min-width (double (max 80.0 (or (get constraints :min-width) 120.0)))
              min-height (double (max 60.0 (or (get constraints :min-height) 100.0)))
              base-height (double (or (:stored-height start-bounds) (:height start-bounds)))
              new-width (max min-width (+ (double (:width start-bounds)) dx))
              new-height (max min-height (+ base-height dy))
              new-bounds {:x (double (:x start-bounds))
                          :y (double (:y start-bounds))
                          :width new-width
                          :height new-height}]
          (dispatch-window-bounds! node game-state new-bounds)
          true)

        :window-minimize
        false

        false))))

(defn handle-window-pointer-up!
  [node game-state px py]
  (when-let [active (core/active-interaction)]
    (when (= (:node active) node)
      (case (:type active)
        :window-minimize
        (let [{:keys [button-bounds]} (:value active)]
          (when (interaction/contains-point? button-bounds px py)
            (dispatch-window-toggle! node game-state))
          true)
        :window-move true
        :window-resize true
        false))))

(defmethod core/pointer-down! :window
  [node game-state x y]
  (handle-window-pointer-down! node game-state x y))

(defmethod core/pointer-drag! :window
  [node game-state x y]
  (handle-window-pointer-drag! node game-state x y))

(defmethod core/pointer-up! :window
  [node game-state x y]
  (handle-window-pointer-up! node game-state x y))
