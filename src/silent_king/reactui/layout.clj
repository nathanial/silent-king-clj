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

(defn- clamp
  [value min-value max-value]
  (-> value
      (max min-value)
      (min max-value)))

(defn- positive-step
  [step]
  (when (some? step)
    (let [s (double step)]
      (when (pos? s) s))))

(defn- snap-to-step
  [value min-value step]
  (if-let [s (positive-step step)]
    (let [steps (/ (- value min-value) s)
          snapped (+ min-value (* (Math/round (double steps)) s))]
      (double snapped))
    (double value)))

(defn- dropdown-option
  [option]
  (cond
    (map? option)
    (let [value (or (:value option)
                    (:scheme option)
                    (:id option)
                    (:key option))]
      (when (nil? value)
        (throw (ex-info "Dropdown option missing :value" {:option option})))
      {:value value
       :label (or (:label option)
                  (if (keyword? value)
                    (name value)
                    (str value)))})
    :else
    {:value option
     :label (str option)}))

(defn- clean-viewport
  [viewport]
  (normalize-bounds viewport))

(defn- resolve-bounds
  [node context]
  (let [viewport (:viewport context)
        fallback (:bounds context)
        explicit (get-in node [:props :bounds])
        base (or fallback viewport)]
    (normalize-bounds (merge base (or explicit {})))))

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

(defmethod layout-node :button
  [node context]
  (let [bounds* (resolve-bounds node context)
        height (double (if (pos? (:height bounds*))
                         (:height bounds*)
                         36.0))
        final-bounds (assoc bounds* :height height)]
    (assoc node
           :layout {:bounds final-bounds}
           :children [])))

(defmethod layout-node :slider
  [node context]
  (let [props (:props node)
        bounds* (resolve-bounds node context)
        height (double (if (pos? (:height bounds*))
                         (:height bounds*)
                         32.0))
        final-bounds (assoc bounds* :height height)
        raw-min (double (or (:min props) 0.0))
        raw-max (double (or (:max props) 1.0))
        [min-value max-value] (if (> raw-min raw-max)
                                [raw-max raw-min]
                                [raw-min raw-max])
        step (:step props)
        value (double (or (:value props) min-value))
        snapped-value (-> value
                          (snap-to-step min-value step)
                          (clamp min-value max-value))
        track-padding (double (max 0.0 (or (:track-padding props) 12.0)))
        track-height (double (or (:track-height props) 4.0))
        track-width (max 0.0 (- (:width final-bounds) (* 2 track-padding)))
        track-x (+ (:x final-bounds) track-padding)
        track-y (+ (:y final-bounds)
                   (/ (- height track-height) 2.0))
        range-span (max (- max-value min-value) 1e-9)
        ratio (/ (- snapped-value min-value) range-span)
        clamped-ratio (clamp ratio 0.0 1.0)
        handle-radius (double (or (:handle-radius props) 8.0))
        handle-x (+ track-x (* track-width clamped-ratio))
        handle-y (+ (:y final-bounds) (/ height 2.0))]
    (assoc node
           :layout {:bounds final-bounds
                    :slider {:track {:x track-x
                                     :y track-y
                                     :width track-width
                                     :height track-height}
                             :handle {:x handle-x
                                      :y handle-y
                                      :radius handle-radius}
                             :range {:min min-value
                                     :max max-value
                                     :step (positive-step step)}
                             :value snapped-value}}
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

(defmethod layout-node :hstack
  [node context]
  (let [props (:props node)
        explicit-bounds (or (get-in node [:props :bounds]) {})
        bounds* (resolve-bounds node context)
        padding (expand-padding (:padding props))
        gap (double (or (:gap props) 0.0))
        inner-y (+ (:y bounds*) (:top padding))
        inner-x (+ (:x bounds*) (:left padding))
        inner-width (max 0.0 (- (:width bounds*) (:left padding) (:right padding)))
        explicit-height (when (contains? explicit-bounds :height)
                          (double (:height explicit-bounds)))
        inner-height (if explicit-height
                       (max 0.0 (- explicit-height (:top padding) (:bottom padding)))
                       0.0)
        children (:children node)
        viewport (:viewport context)]
    (loop [remaining children
           acc []
           cursor-x inner-x
           max-height 0.0]
      (if (empty? remaining)
        (let [computed-height (+ max-height (:top padding) (:bottom padding))
              height (if explicit-height explicit-height computed-height)
              final-bounds (assoc bounds* :height height)]
          (assoc node
                 :layout {:bounds final-bounds
                          :padding padding}
                 :children acc))
        (let [child (first remaining)
              available-width (max 0.0 (- (+ inner-x inner-width) cursor-x))
              child-node (layout-node child {:viewport viewport
                                             :bounds {:x cursor-x
                                                      :y inner-y
                                                      :width available-width
                                                      :height inner-height}})
              child-bounds (bounds child-node)
              child-width (double (or (:width child-bounds) 0.0))
              child-height (double (or (:height child-bounds) 0.0))
              has-more? (seq (rest remaining))
              next-x (+ cursor-x child-width (if has-more? gap 0.0))]
          (recur (rest remaining)
                 (conj acc child-node)
                 next-x
                 (max max-height child-height)))))))

(defmethod layout-node :dropdown
  [node context]
  (let [props (:props node)
        bounds* (resolve-bounds node context)
        width (let [w (:width bounds*)]
                (if (pos? w) w 200.0))
        header-height (double (or (:header-height props) 36.0))
        option-height (double (or (:option-height props) 30.0))
        option-gap (double (or (:option-gap props) 4.0))
        expanded? (boolean (:expanded? props))
        options (mapv dropdown-option (:options props))
        header-bounds (assoc bounds*
                             :width width
                             :height header-height)
        [options-layout total-height]
        (if (and expanded? (seq options))
          (loop [remaining options
                 acc []
                 cursor-y (+ (:y header-bounds) header-height option-gap)
                 height (+ header-height option-gap)]
            (if (empty? remaining)
              [acc height]
              (let [opt (first remaining)
                    bounds {:x (:x header-bounds)
                            :y cursor-y
                            :width width
                            :height option-height}
                    next-y (+ cursor-y option-height option-gap)
                    next-height (+ height option-height
                                    (if (seq (rest remaining)) option-gap 0.0))]
                (recur (rest remaining)
                       (conj acc (assoc opt :bounds bounds))
                       next-y
                       next-height))))
          [[] header-height])
        final-bounds (assoc header-bounds :height total-height)]
    (assoc node
           :layout {:bounds final-bounds
                    :dropdown {:header header-bounds
                               :options options-layout
                               :all-options options
                               :expanded? expanded?}}
           :children [])))

(defmethod layout-node :default
  [node context]
  (throw (ex-info "Unknown primitive type" {:type (:type node)})))
