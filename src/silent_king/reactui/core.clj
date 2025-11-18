(ns silent-king.reactui.core
  "Entry points for the Reactified immediate-mode UI layer."
  (:require [silent-king.reactui.layout :as layout]
            [silent-king.reactui.render :as render]))

(set! *warn-on-reflection* true)

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

(defn- normalize-label
  "Normalize a label element so that its text always lives in :props/:text."
  [props raw-children]
  (let [text (or (:text props)
                 (not-empty
                  (apply str
                         (map coerce-text
                              (filter text-fragment? raw-children)))))
        cleaned-props (-> props
                          (assoc :text (or text "")))]
    {:type :label
     :props cleaned-props
     :children []}))

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
      (if (= tag* :label)
        (normalize-label props child-forms)
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
  [{:keys [canvas tree viewport]}]
  (let [normalized (normalize-tree tree)
        layout-tree (layout/compute-layout normalized viewport)]
    (when canvas
      (render/draw-tree canvas layout-tree))
    layout-tree))

(defn demo-tree
  "Simple placeholder tree used until real panels are wired up."
  []
  [:vstack {:bounds {:x 24
                     :y 24
                     :width 300}
            :padding {:all 12}
            :gap 6
            :background-color 0xAA10131A}
   [:label {:text "Reactified UI scaffolding"
            :color 0xFFFFFFFF}]
   [:label {:text "Phase 1 – vstack demo"
            :color 0xFF9CDCFE}]
   [:label {:text "This overlay is static for now."
            :color 0xFFB3B3B3}]])

(defn render-demo!
  "Render the placeholder :vstack overlay."
  [canvas viewport]
  (render-ui-tree {:canvas canvas
                   :tree (demo-tree)
                   :viewport viewport}))
