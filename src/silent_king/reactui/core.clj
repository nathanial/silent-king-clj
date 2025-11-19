(ns silent-king.reactui.core
  "Entry points for the Reactified immediate-mode UI layer."
  (:require [silent-king.minimap.math :as minimap-math]
            [silent-king.reactui.events :as ui-events]
            [silent-king.reactui.interaction :as interaction]
            [silent-king.reactui.layout :as layout]
            [silent-king.reactui.render :as render]
            [silent-king.state :as state]))

(set! *warn-on-reflection* true)

(defonce ^:private last-layout (atom nil))
(defonce ^:private pointer-capture (atom nil))
(defonce ^:private active-interaction* (atom nil))
(def ^:const ^:private minimap-window-drag-threshold 5.0)

(defn- scale-factor
  [game-state]
  (state/ui-scale game-state))

(defn- text-fragment?
  [value]
  (or (string? value)
      (number? value)
      (keyword? value)))

(defn- coerce-text
  [value]
  (cond
    (string? value) value
    (number? value) (str value)
    (keyword? value) (name value)
    :else ""))

(declare normalize-element)

(defn- collect-text
  [children]
  (not-empty
   (apply str
          (map coerce-text
               (filter text-fragment? children)))))
(defn- normalize-children
  [children]
  (->> children
       (map normalize-element)
       (remove nil?)
       vec))

(defn- leaf-normalizer
  "Build a normalizer for elements that do not keep children."
  ([type]
   (leaf-normalizer type (fn [props _] props)))
  ([type props-fn]
   (fn [props raw-children]
     {:type type
      :props (props-fn (or props {}) raw-children)
      :children []})))

(defn- branch-normalizer
  "Build a normalizer for elements that keep normalized children."
  ([type]
   (branch-normalizer type (fn [props _] props)))
  ([type props-fn]
   (fn [props raw-children]
     {:type type
      :props (props-fn (or props {}) raw-children)
      :children (normalize-children raw-children)})))

(defn- label-props
  [props raw-children]
  (let [text (or (:text props)
                 (collect-text raw-children)
                 "")]
    (assoc props :text text)))

(defn- button-props
  [props raw-children]
  (let [label (or (:label props)
                  (collect-text raw-children)
                  "Button")]
    (assoc props :label label)))

(defn- bar-chart-props
  [props _raw-children]
  (update props :values #(vec (or % []))))

(defmulti normalize-tag
  "Normalize a Hiccup tag keyword. Extend via `defmethod` in feature namespaces."
  (fn [tag _props _children] tag))

(defmethod normalize-tag :default
  [tag props child-forms]
  {:type tag
   :props (or props {})
   :children (normalize-children child-forms)})

(defmethod normalize-tag :label
  [_ props child-forms]
  ((leaf-normalizer :label label-props) props child-forms))

(defmethod normalize-tag :button
  [_ props child-forms]
  ((leaf-normalizer :button button-props) props child-forms))

(defmethod normalize-tag :slider
  [_ props child-forms]
  ((leaf-normalizer :slider) props child-forms))

(defmethod normalize-tag :dropdown
  [_ props child-forms]
  ((leaf-normalizer :dropdown) props child-forms))

(defmethod normalize-tag :bar-chart
  [_ props child-forms]
  ((leaf-normalizer :bar-chart bar-chart-props) props child-forms))

(defmethod normalize-tag :minimap
  [_ props child-forms]
  ((leaf-normalizer :minimap) props child-forms))

(defmethod normalize-tag :window
  [_ props child-forms]
  ((branch-normalizer :window) props child-forms))

(defn normalize-element
  "Normalize a single Hiccup element (vector, string, or number) into the
   internal tree representation {:type keyword :props map :children [...]}."
  [element]
  (cond
    (nil? element) nil
    (map? element)
    (let [{:keys [type props children]} element]
      (when-not type
        (throw (ex-info "Map elements must include :type" {:element element})))
      {:type type
       :props (or props {})
       :children (->> children
                      (map normalize-element)
                      (remove nil?)
                      vec)})
    (vector? element)
    (let [[tag maybe-props & remaining] element
          tag* (keyword tag)
          has-props? (map? maybe-props)
          props (if has-props? maybe-props {})
          child-forms (if has-props? remaining (cons maybe-props remaining))]
      (normalize-tag tag* props child-forms))
    (text-fragment? element)
    {:type :label
     :props {:text (coerce-text element)}
     :children []}
    :else
    (throw (ex-info "Unsupported element type" {:element element}))))

(defn normalize-tree
  "Normalize a root UI element into the internal map form."
  [element]
  (or (normalize-element element)
      (throw (ex-info "UI tree cannot be nil" {}))))


(defn render-ui-tree
  "Normalize, lay out, and render a Hiccup tree.

   Accepts a map with:
   - :canvas   – optional Skija Canvas (if nil, layout still runs for testing)
   - :tree     – the root Hiccup element
   - :viewport – {:x ... :y ... :width ... :height ...}

   Returns the laid out tree."
  [{:keys [canvas tree viewport context]}]
  (let [normalized (normalize-tree tree)
        layout-tree (layout/compute-layout normalized viewport)]
    (reset! last-layout layout-tree)
    (when canvas
      (render/draw-tree canvas layout-tree context))
    layout-tree))

(defn current-layout
  []
  @last-layout)

(defn- capture-node!
  [node]
  (reset! pointer-capture node))

(defn release-capture!
  []
  (reset! pointer-capture nil))

(defn captured-node
  []
  @pointer-capture)

(defn active-interaction
  []
  @active-interaction*)

(defn- pointer-distance
  [{:keys [x y]} {x2 :x y2 :y}]
  (let [dx (- (double x2) (double x))
        dy (- (double y2) (double y))]
    (Math/sqrt (+ (* dx dx) (* dy dy)))))

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

(defn- containing-window
  [target]
  (when-let [root (current-layout)]
    (when-let [path (find-node-path root target)]
      (->> path
           butlast
           (filter #(= :window (:type %)))
           last))))

(defn- set-active-interaction!
  ([node kind]
   (set-active-interaction! node kind nil))
  ([node kind {:keys [bounds value]}]
   (reset! active-interaction*
           (cond-> {:type kind}
             node (assoc :node node)
             (or bounds node) (assoc :bounds (or bounds (layout/bounds node)))
             value (assoc :value value)))))

(defn- clear-active-interaction!
  []
  (reset! active-interaction* nil))

(defn- within-bounds?
  [{:keys [x y width height]} px py]
  (and (number? x) (number? y) (number? width) (number? height)
       (>= px x)
       (<= px (+ x width))
       (>= py y)
       (<= py (+ y height))))

(defn- viewport-center
  [{:keys [x y width height]}]
  (when (and (number? x) (number? y) (number? width) (number? height))
    {:x (+ (double x) (/ (double width) 2.0))
     :y (+ (double y) (/ (double height) 2.0))}))

(defn- minimap-interaction-value
  [node px py]
  (let [{:keys [world-bounds viewport-rect]} (:props node)
        widget-bounds (layout/bounds node)
        viewport-bounds (when (and viewport-rect world-bounds widget-bounds)
                          (minimap-math/viewport->minimap-rect viewport-rect world-bounds widget-bounds))
        viewport-hit? (and viewport-bounds (within-bounds? viewport-bounds px py))
        center (viewport-center viewport-rect)
        pointer-world (when (and world-bounds widget-bounds)
                        (minimap-math/minimap->world {:x px :y py} world-bounds widget-bounds))
        offset (when (and viewport-hit? pointer-world center)
                 {:dx (- (:x pointer-world) (:x center))
                  :dy (- (:y pointer-world) (:y center))})]
    {:mode (if viewport-hit? :viewport-drag :click)
     :offset offset
     :viewport-bounds viewport-bounds}))

(defn- handle-minimap-pan!
  ([node game-state x y]
   (handle-minimap-pan! node game-state x y nil))
  ([node game-state x y interaction]
   (let [{:keys [world-bounds]} (:props node)
         widget-bounds (layout/bounds node)
         world-pos (minimap-math/minimap->world {:x x :y y} world-bounds widget-bounds)
         {:keys [mode offset]} (or interaction {})
         adjusted (if (and (= mode :viewport-drag) offset)
                    {:x (- (:x world-pos) (double (:dx offset)))
                     :y (- (:y world-pos) (double (:dy offset)))}
                    world-pos)]
     (ui-events/dispatch-event! game-state [:camera/pan-to-world adjusted]))))

(defn- start-window-move!
  [node px py]
  (let [bounds (layout/bounds node)
        window-layout (get-in node [:layout :window])
        stored-height (or (:stored-height window-layout) (:height bounds))
        enriched-bounds (assoc bounds :stored-height stored-height)]
    (capture-node! node)
    (set-active-interaction! node :window-move
                             {:value {:start-pointer {:x px :y py}
                                      :start-bounds enriched-bounds}})
    true))

(defn- delegate-minimap-drag-to-window!
  [node px py]
  (when-let [window (containing-window node)]
    (start-window-move! window px py)))

(defn- dispatch-window-bounds!
  [node game-state bounds]
  (when-let [event (-> node :props :on-change-bounds)]
    (when (vector? event)
      (ui-events/dispatch-event! game-state (conj event bounds)))))

(defn- dispatch-window-toggle!
  [node game-state]
  (when-let [event (-> node :props :on-toggle-minimized)]
    (when (vector? event)
      (ui-events/dispatch-event! game-state event))))

(defn- handle-window-pointer-down!
  [node game-state px py]
  (when-let [region (interaction/window-region node px py)]
    (let [bounds (layout/bounds node)
          window-layout (get-in node [:layout :window])
          constraints (:constraints window-layout)
          stored-height (or (:stored-height window-layout) (:height bounds))
          enriched-bounds (assoc bounds :stored-height stored-height)]
      (case (:kind region)
        :move (start-window-move! node px py)
        :resize (do
                  (capture-node! node)
                  (set-active-interaction! node :window-resize
                                           {:value {:start-pointer {:x px :y py}
                                                    :start-bounds enriched-bounds
                                                    :constraints constraints}})
                  true)
        :minimize (do
                    (capture-node! node)
                    (set-active-interaction! node :window-minimize
                                             {:value {:button-bounds (:bounds region)}})
                    true)
        false))))

(defn- handle-window-pointer-drag!
  [node game-state px py]
  (when-let [active (active-interaction)]
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

(defn- handle-window-pointer-up!
  [node game-state px py]
  (when-let [active (active-interaction)]
    (when (= (:node active) node)
      (case (:type active)
        :window-minimize
        (let [{:keys [button-bounds]} (:value active)]
          (when (within-bounds? button-bounds px py)
            (dispatch-window-toggle! node game-state))
          true)
        :window-move true
        :window-resize true
        false))))

(defn handle-pointer-down!
  [game-state x y]
  (let [scale (scale-factor game-state)]
    (clear-active-interaction!)
    (when-let [layout-tree (current-layout)]
      (when-let [node (interaction/node-at layout-tree
                                           (/ (double x) scale)
                                           (/ (double y) scale))]
        (case (:type node)
          :slider (do
                    (capture-node! node)
                    (set-active-interaction! node :slider)
                    (interaction/slider-drag! node game-state (/ (double x) scale))
                    true)
          :button (do
                    (capture-node! node)
                    (set-active-interaction! node :button)
                    true)
          :dropdown (when-let [region (interaction/dropdown-region node (/ (double x) scale) (/ (double y) scale))]
                      (capture-node! node)
                      (case (:type region)
                        :option (set-active-interaction! node :dropdown-option {:bounds (:bounds region)
                                                                              :value (:value region)})
                        :header (set-active-interaction! node :dropdown {:bounds (:bounds region)}))
                      true)
          :minimap (let [logical-x (/ (double x) scale)
                         logical-y (/ (double y) scale)
                         interaction (assoc (minimap-interaction-value node logical-x logical-y)
                                            :start-pointer {:x logical-x :y logical-y})]
                     (capture-node! node)
                     (set-active-interaction! node :minimap {:value interaction})
                     (when (= :viewport-drag (:mode interaction))
                       (handle-minimap-pan! node game-state logical-x logical-y interaction))
                     true)
          :window (handle-window-pointer-down! node game-state (/ (double x) scale) (/ (double y) scale))
          false)))))

(defn handle-pointer-up!
  [game-state x y]
  (let [scale (scale-factor game-state)]
    (when-let [node (captured-node)]
      (case (:type node)
        :button (interaction/activate-button! node game-state (/ (double x) scale) (/ (double y) scale))
        :slider (interaction/slider-drag! node game-state (/ (double x) scale))
        :dropdown (interaction/dropdown-click! node game-state (/ (double x) scale) (/ (double y) scale))
        :minimap (let [logical-x (/ (double x) scale)
                        logical-y (/ (double y) scale)
                        interaction (some-> (active-interaction) :value)]
                    (when (= :click (:mode interaction))
                      (handle-minimap-pan! node game-state logical-x logical-y interaction))
                    true)
        :window (handle-window-pointer-up! node game-state (/ (double x) scale) (/ (double y) scale))
        nil)))
  (release-capture!)
  (clear-active-interaction!)
  nil)

(defn handle-pointer-drag!
  [game-state x y]
  (let [scale (scale-factor game-state)]
    (when-let [node (captured-node)]
      (case (:type node)
        :slider (do
                  (interaction/slider-drag! node game-state (/ (double x) scale))
                  true)
        :minimap (let [logical-x (/ (double x) scale)
                        logical-y (/ (double y) scale)
                        interaction (some-> (active-interaction) :value)
                        {:keys [mode start-pointer]} interaction]
                    (case mode
                      :viewport-drag (do
                                       (handle-minimap-pan! node game-state logical-x logical-y interaction)
                                       true)
                      :click (do
                               (when (and start-pointer
                                          (>= (pointer-distance start-pointer {:x logical-x :y logical-y})
                                              minimap-window-drag-threshold))
                                 (delegate-minimap-drag-to-window! node logical-x logical-y))
                               true)
                      true))
        :window (handle-window-pointer-drag! node game-state (/ (double x) scale) (/ (double y) scale))
        false))))
