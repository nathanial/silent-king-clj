(ns silent-king.reactui.layout
  "Pure layout pass for the Reactified UI tree."
  (:refer-clojure :exclude [bounds]))

(set! *warn-on-reflection* true)

(defn bounds
  "Helper to read the bounds map attached to a laid-out node."
  [node]
  (get-in node [:layout :bounds]))

(defn- normalize-bounds
  [bounds]
  (-> {:x 0.0 :y 0.0 :width 0.0 :height 0.0}
      (merge bounds)
      (update :x #(double (or % 0.0)))
      (update :y #(double (or % 0.0)))
      (update :width #(double (or % 0.0)))
      (update :height #(double (or % 0.0)))))

(defn- clean-viewport
  [viewport]
  (normalize-bounds viewport))

(defn- resolve-bounds
  [node context]
  (let [viewport (:viewport context)
        fallback (:bounds context)
        explicit (get-in node [:props :bounds])]
    (normalize-bounds (merge viewport
                             fallback
                             (or explicit {})))))

(defn- expand-padding
  [padding]
  (let [padding (or padding {})
        all (double (or (:all padding) 0.0))
        horizontal (double (or (:horizontal padding) all))
        vertical (double (or (:vertical padding) all))
        left (double (or (:left padding) horizontal))
        right (double (or (:right padding) horizontal))
        top (double (or (:top padding) vertical))
        bottom (double (or (:bottom padding) vertical))]
    {:left left
     :right right
     :top top
     :bottom bottom}))

(defn- estimate-text-width
  [text font-size]
  (* (count (or text ""))
     font-size
     0.55))

(defmulti layout-node
  (fn [node _]
    (:type node)))

(defn compute-layout
  "Attach bounds to every node in the tree."
  [node viewport]
  (layout-node node {:viewport (clean-viewport viewport)
                     :bounds (clean-viewport viewport)}))

(defmethod layout-node :label
  [node context]
  (let [props (:props node)
        text (or (:text props) "")
        font-size (double (or (:font-size props) 16.0))
        line-height (double (or (:line-height props) (+ font-size 4.0)))
        bounds* (resolve-bounds node context)
        width-provided (double (or (:width bounds*) 0.0))
        measured-width (double (estimate-text-width text font-size))
        width (double (if (pos? (double width-provided))
                        (max width-provided measured-width)
                        (max measured-width 0.0)))
        final-bounds (-> bounds*
                         (assoc :width width)
                         (assoc :height line-height))]
    (assoc node
           :layout {:bounds final-bounds}
           :children [])))

(defmethod layout-node :vstack
  [node context]
  (let [props (:props node)
        explicit-bounds (or (get-in node [:props :bounds]) {})
        bounds* (resolve-bounds node context)
        padding (expand-padding (:padding props))
        gap (double (or (:gap props) 0.0))
        inner-x (+ (:x bounds*) (:left padding))
        inner-width (max 0.0 (- (:width bounds*) (:left padding) (:right padding)))
        start-y (+ (:y bounds*) (:top padding))
        viewport (:viewport context)
        children (:children node)]
    (loop [remaining children
           acc []
           cursor-y start-y]
      (if (empty? remaining)
        (let [content-height (- cursor-y start-y)
              padded-height (+ (max 0.0 content-height) (:top padding) (:bottom padding))
              height (if (contains? explicit-bounds :height)
                       (:height explicit-bounds)
                       padded-height)
              final-bounds (assoc bounds* :height height)]
          (assoc node
                 :layout {:bounds final-bounds
                          :padding padding}
                 :children acc))
        (let [child (first remaining)
              child-node (layout-node child {:viewport viewport
                                             :bounds {:x inner-x
                                                      :y cursor-y
                                                      :width inner-width}})
              child-height (double (or (:height (bounds child-node)) 0.0))
              has-more? (seq (rest remaining))
              next-y (+ cursor-y child-height (if has-more? gap 0.0))]
          (recur (rest remaining)
                 (conj acc child-node)
                 next-y))))))

(defmethod layout-node :default
  [node context]
  (throw (ex-info "Unknown primitive type" {:type (:type node)})))
