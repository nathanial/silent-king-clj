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

(defn- normalize-label
  "Normalize a label element so that its text always lives in :props/:text."
  [props raw-children]
  (let [text (or (:text props)
                 (collect-text raw-children))
        cleaned-props (-> props
                          (assoc :text (or text "")))]
    {:type :label
     :props cleaned-props
     :children []}))

(defn- normalize-button
  [props raw-children]
  (let [label (or (:label props)
                  (collect-text raw-children)
                  "Button")]
    {:type :button
     :props (-> props
                (assoc :label label))
     :children []}))

(defn- normalize-slider
  [props _raw-children]
  {:type :slider
   :props (or props {})
   :children []})

(defn- normalize-dropdown
  [props _raw-children]
  {:type :dropdown
   :props (or props {})
   :children []})

(defn- normalize-bar-chart
  [props _raw-children]
  {:type :bar-chart
   :props (-> (or props {})
              (update :values #(vec (or % []))))
   :children []})

(defn- normalize-window
  [props raw-children]
  {:type :window
   :props (or props {})
   :children (->> raw-children
                  (map normalize-element)
                  (remove nil?)
                  vec)})

(defn- normalize-minimap
  [props _raw-children]
  {:type :minimap
   :props (or props {})
   :children []})

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
      (case tag*
        :label (normalize-label props child-forms)
        :button (normalize-button props child-forms)
        :slider (normalize-slider props child-forms)
        :dropdown (normalize-dropdown props child-forms)
        :bar-chart (normalize-bar-chart props child-forms)
        :minimap (normalize-minimap props child-forms)
        :window (normalize-window props child-forms)
        {:type tag*
         :props (or props {})
         :children (->> child-forms
                        (map normalize-element)
                        (remove nil?)
                        vec)}))
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

(defn- handle-minimap-pan!
  [node game-state x y]
  (let [{:keys [world-bounds]} (:props node)
        widget-bounds (layout/bounds node)
        world-pos (minimap-math/minimap->world {:x x :y y} world-bounds widget-bounds)]
    (ui-events/dispatch-event! game-state [:camera/pan-to-world world-pos])))

(defn- within-bounds?
  [{:keys [x y width height]} px py]
  (and (number? x) (number? y) (number? width) (number? height)
       (>= px x)
       (<= px (+ x width))
       (>= py y)
       (<= py (+ y height))))

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
        :move (do
                (capture-node! node)
                (set-active-interaction! node :window-move
                                         {:value {:start-pointer {:x px :y py}
                                                  :start-bounds enriched-bounds}})
                true)
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
          :minimap (do
                     (capture-node! node)
                     (handle-minimap-pan! node game-state (/ (double x) scale) (/ (double y) scale))
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
        :minimap (do
                   (handle-minimap-pan! node game-state (/ (double x) scale) (/ (double y) scale))
                   true)
        :window (handle-window-pointer-drag! node game-state (/ (double x) scale) (/ (double y) scale))
        false))))
