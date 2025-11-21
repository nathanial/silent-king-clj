(ns silent-king.reactui.core
  "Entry points for the Reactified immediate-mode UI layer."
  (:require [silent-king.reactui.interaction :as interaction]
            [silent-king.reactui.layout :as layout]
            [silent-king.reactui.render :as render]
            [silent-king.render.skia :as skia]
            [silent-king.state :as state]))

(set! *warn-on-reflection* true)

(defonce ^:private last-layout (atom nil))
(defonce ^:private pointer-capture (atom nil))
(defonce ^:private active-interaction* (atom nil))

(defn scale-factor
  [game-state]
  (state/ui-scale game-state))

(defn- text-fragment?
  [value]
  (or (string? value)
      (number? value)
      (keyword? value)))

(defn coerce-text
  [value]
  (cond
    (string? value) value
    (number? value) (str value)
    (keyword? value) (name value)
    :else ""))

(declare normalize-element)

(defn collect-text
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

(defn leaf-normalizer
  "Build a normalizer for elements that do not keep children."
  ([type]
   (leaf-normalizer type (fn [props _] props)))
  ([type props-fn]
   (fn [props raw-children]
     {:type type
      :props (props-fn (or props {}) raw-children)
      :children []})))

(defn branch-normalizer
  "Build a normalizer for elements that keep normalized children."
  ([type]
   (branch-normalizer type (fn [props _] props)))
  ([type props-fn]
   (fn [props raw-children]
     {:type type
      :props (props-fn (or props {}) raw-children)
      :children (normalize-children raw-children)})))

(defmulti normalize-tag
  "Normalize a Hiccup tag keyword. Extend via `defmethod` in feature namespaces."
  (fn [tag _props _children] tag))

(defmethod normalize-tag :default
  [tag props child-forms]
  {:type tag
   :props (or props {})
   :children (normalize-children child-forms)})

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

   Returns {:layout-tree .. :commands ..}"
  [{:keys [canvas tree viewport context]}]
  (let [normalized (normalize-tree tree)
        layout-tree (layout/compute-layout normalized viewport)
        {:keys [commands overlays]} (render/plan-tree layout-tree context)]
    (reset! last-layout layout-tree)
    (when canvas
      (skia/draw-commands! canvas commands))
    {:layout-tree layout-tree
     :commands commands
     :overlays overlays}))

(defn current-layout
  []
  @last-layout)

(defn capture-node!
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

(defn set-active-interaction!
  ([node kind]
   (set-active-interaction! node kind nil))
  ([node kind {:keys [bounds value]}]
   (reset! active-interaction*
           (cond-> {:type kind}
             node (assoc :node node)
             (or bounds node) (assoc :bounds (or bounds (layout/bounds node)))
             value (assoc :value value)))))

(defn clear-active-interaction!
  []
  (reset! active-interaction* nil))

(defmulti pointer-down!
  "Handle pointer-down per node type. Coordinates are already in logical UI space."
  (fn [node _game-state _x _y] (:type node)))

(defmethod pointer-down! :default
  [_ _ _ _]
  false)

(defmulti pointer-up!
  "Handle pointer-up for a captured node type. Coordinates are in logical UI space."
  (fn [node _game-state _x _y] (:type node)))

(defmethod pointer-up! :default
  [_ _ _ _]
  nil)

(defmulti pointer-drag!
  "Handle pointer-drag for a captured node type. Coordinates are in logical UI space."
  (fn [node _game-state _x _y] (:type node)))

(defmethod pointer-drag! :default
  [_ _ _ _]
  false)

(defmulti scroll!
  "Handle scroll events per node type. Coordinates are in logical UI space."
  (fn [node _game-state _x _y _dx _dy] (:type node)))

(defmethod scroll! :default
  [_ _ _ _ _ _]
  false)

(defn handle-pointer-down!
  [game-state x y]
  (let [scale (scale-factor game-state)
        logical-x (/ (double x) scale)
        logical-y (/ (double y) scale)]
    (clear-active-interaction!)
    (when-let [layout-tree (current-layout)]
      (when-let [node (interaction/node-at layout-tree logical-x logical-y)]
        (pointer-down! node game-state logical-x logical-y)))))

(defn handle-pointer-up!
  [game-state x y]
  (let [scale (scale-factor game-state)
        logical-x (/ (double x) scale)
        logical-y (/ (double y) scale)]
    (when-let [node (captured-node)]
      (pointer-up! node game-state logical-x logical-y)))
  (release-capture!)
  (clear-active-interaction!)
  nil)

(defn handle-pointer-drag!
  [game-state x y]
  (let [scale (scale-factor game-state)
        logical-x (/ (double x) scale)
        logical-y (/ (double y) scale)]
    (when-let [node (captured-node)]
      (pointer-drag! node game-state logical-x logical-y))))

(defn handle-scroll!
  [game-state x y dx dy]
  (let [scale (scale-factor game-state)
        logical-x (/ (double x) scale)
        logical-y (/ (double y) scale)]
    (when-let [layout-tree (current-layout)]
      (when-let [node (interaction/node-at layout-tree logical-x logical-y)]
        (scroll! node game-state logical-x logical-y dx dy)))))
