(ns silent-king.reactui.layout
  "Pure layout pass for the Reactified UI tree."
  (:refer-clojure :exclude [bounds]))

(set! *warn-on-reflection* true)

(defn bounds
  "Helper to read the bounds map attached to a laid-out node."
  [node]
  (get-in node [:layout :bounds]))

(defn normalize-bounds
  [bounds]
  (-> {:x 0.0 :y 0.0 :width 0.0 :height 0.0}
      (merge bounds)
      (update :x #(double (or % 0.0)))
      (update :y #(double (or % 0.0)))
      (update :width #(double (or % 0.0)))
      (update :height #(double (or % 0.0)))))

(defn clamp
  [value min-value max-value]
  (-> value
      (max min-value)
      (min max-value)))

(defn positive-step
  [step]
  (when (some? step)
    (let [s (double step)]
      (when (pos? s) s))))

(defn snap-to-step
  [value min-value step]
  (if-let [s (positive-step step)]
    (let [steps (/ (- value min-value) s)
          snapped (+ min-value (* (Math/round (double steps)) s))]
      (double snapped))
    (double value)))

(defn clean-viewport
  [viewport]
  (normalize-bounds viewport))

(defn resolve-bounds
  [node context]
  (let [viewport (:viewport context)
        fallback (:bounds context)
        explicit (get-in node [:props :bounds])
        base (or fallback viewport)]
    (normalize-bounds (merge base (or explicit {})))))

(defn expand-padding
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

(defn estimate-text-width
  [text font-size]
  (* (count (or text ""))
     font-size
     0.55))

;; =============================================================================
;; Docking Layout
;; =============================================================================

(defn calculate-dock-layout
  "Calculate bounds for dock areas and the remaining center area.
   Precedence: Top/Bottom take full width. Left/Right take remaining height.
   Returns map with :center, :left, :right, :top, :bottom bounds."
  [viewport docking-state]
  (let [{:keys [width height x y] :as base} (normalize-bounds viewport)
        x (or x 0.0)
        y (or y 0.0)
        
        ;; Extract dock configs
        top-dock (:top docking-state)
        bottom-dock (:bottom docking-state)
        left-dock (:left docking-state)
        right-dock (:right docking-state)

        ;; Determine active sizes (0 if no windows)
        get-size (fn [dock]
                   (if (seq (:windows dock))
                     (double (:size dock))
                     0.0))

        top-h (get-size top-dock)
        bottom-h (get-size bottom-dock)
        left-w (get-size left-dock)
        right-w (get-size right-dock)

        ;; Calculate Top Dock
        top-bounds {:x x
                    :y y
                    :width width
                    :height top-h}

        ;; Calculate Bottom Dock
        bottom-bounds {:x x
                       :y (+ y height (- bottom-h))
                       :width width
                       :height bottom-h}

        ;; Remaining vertical space
        center-y (+ y top-h)
        center-h (max 0.0 (- height top-h bottom-h))

        ;; Calculate Left Dock (positioned below top, above bottom)
        left-bounds {:x x
                     :y center-y
                     :width left-w
                     :height center-h}

        ;; Calculate Right Dock (positioned below top, above bottom)
        right-bounds {:x (+ x width (- right-w))
                      :y center-y
                      :width right-w
                      :height center-h}
        
        ;; Remaining center space
        center-x (+ x left-w)
        center-w (max 0.0 (- width left-w right-w))
        center-bounds {:x center-x
                       :y center-y
                       :width center-w
                       :height center-h}]

    {:top top-bounds
     :bottom bottom-bounds
     :left left-bounds
     :right right-bounds
     :center center-bounds}))

;; =============================================================================
;; Node Layout
;; =============================================================================

(defmulti layout-node
  (fn [node _]
    (:type node)))

(defn compute-layout
  "Attach bounds to every node in the tree."
  [node viewport]
  (layout-node node {:viewport (clean-viewport viewport)
                     :bounds (clean-viewport viewport)}))

(defmethod layout-node :minimap
  [node context]
  (let [bounds* (resolve-bounds node context)
        width (double (if (pos? (:width bounds*))
                        (:width bounds*)
                        200.0))
        height (double (if (pos? (:height bounds*))
                         (:height bounds*)
                         200.0))
        final-bounds (assoc bounds* :width width :height height)]
    (assoc node
           :layout {:bounds final-bounds}
           :children [])))

(defmethod layout-node :default
  [node _context]
  (throw (ex-info "Unknown primitive type" {:type (:type node)})))