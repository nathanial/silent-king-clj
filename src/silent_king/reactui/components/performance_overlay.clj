(ns silent-king.reactui.components.performance-overlay
  "Performance overlay panel rendered in the Reactified UI.")

(set! *warn-on-reflection* true)

(def ^:const default-panel-bounds {:x 752 :y 24 :width 360})
(def ^:const panel-background 0xCC10131C)
(def ^:const section-background 0xFF1F2330)
(def ^:const accent-color 0xFF9CDCFE)
(def ^:const text-color 0xFFCBCBCB)
(def ^:const muted-color 0xFF84889A)

(defn- format-number
  [value {:keys [precision suffix default]}]
  (let [v (when (number? value)
            (double value))
        fmt (str "%." (int (or precision 1)) "f")]
    (cond
      v (str (format fmt v) (or suffix ""))
      :else (or default "--"))))

(defn- stat-row
  [{:keys [label value precision suffix default]}]
  [:hstack {:gap 8
            :padding {:vertical 2}}
   [:label {:text label
            :color muted-color
            :bounds {:width 150.0}}]
   [:label {:text (format-number value {:precision precision
                                        :suffix suffix
                                        :default default})
            :color text-color
            :font-size 14.0}]])

(defn- section
  [title & rows]
  [:vstack {:gap 4
            :padding {:all 8}
            :background-color section-background}
   [:label {:text title
            :color accent-color
            :font-size 15.0
            :bounds {:height 20.0}}]
   (into [:vstack {:gap 2}] rows)])

(defn- summary-content
  [metrics]
  (let [{:keys [fps frame-time-ms visible-stars visible-hyperlanes draw-calls
                hyperlane-count widget-count memory-mb total-stars]} metrics]
    [:vstack {:gap 8}
     (section "Performance"
              (stat-row {:label "FPS"
                         :value fps
                         :precision 1})
              (stat-row {:label "Frame Time"
                         :value frame-time-ms
                         :precision 1
                         :suffix " ms"}))
     (section "Rendering"
              (stat-row {:label "Visible Stars"
                         :value visible-stars
                         :precision 0})
              (stat-row {:label "Visible Hyperlanes"
                         :value visible-hyperlanes
                         :precision 0})
              (stat-row {:label "Draw Calls"
                         :value draw-calls
                         :precision 0}))
     (section "Entities"
              (stat-row {:label "Total Stars"
                         :value total-stars
                         :precision 0})
              (stat-row {:label "Hyperlane Entities"
                         :value hyperlane-count
                         :precision 0})
              (stat-row {:label "Widgets"
                         :value widget-count
                         :precision 0})
              (stat-row {:label "Memory"
                         :value memory-mb
                         :precision 1
                         :suffix " MB"}))]))

(defn- hidden-panel
  [bounds]
  [:vstack {:key :performance-overlay
            :bounds bounds
            :padding {:all 12}
            :gap 8
            :background-color panel-background}
   [:label {:text "Performance Overlay"
            :color accent-color
            :font-size 16.0}]
   [:label {:text "Hidden"
            :color muted-color
            :font-size 14.0}]
   [:button {:label "Show Overlay"
             :on-click [:ui/perf-toggle-visible]
             :background-color section-background
             :text-color text-color}]])

(defn performance-overlay
  [{:keys [metrics visible? expanded? bounds]}]
  (let [panel-bounds (or bounds default-panel-bounds)]
    (if-not visible?
      (hidden-panel panel-bounds)
      [:vstack {:key :performance-overlay
                :bounds panel-bounds
                :padding {:all 12}
                :gap 10
                :background-color panel-background}
       [:hstack {:gap 8}
        [:label {:text "Performance Overlay"
                 :color accent-color
                 :font-size 16.0
                 :bounds {:width 180.0}}]
        [:button {:label (if expanded? "Collapse" "Expand")
                  :on-click [:ui/perf-toggle-expanded]
                  :background-color section-background
                  :text-color text-color
                  :bounds {:width 80.0 :height 32.0}}]
        [:button {:label "Hide"
                  :on-click [:ui/perf-toggle-visible]
                  :background-color 0xFF3C4456
                  :text-color text-color
                  :bounds {:width 60.0 :height 32.0}}]]
       (if expanded?
         (summary-content metrics)
         (section "Summary"
                  (stat-row {:label "FPS"
                             :value (:fps metrics)
                             :precision 1})
                  (stat-row {:label "Frame Time"
                             :value (:frame-time-ms metrics)
                             :precision 1
                             :suffix " ms"})
                  (stat-row {:label "Draw Calls"
                             :value (:draw-calls metrics)
                             :precision 0})))
       [:button {:label "Reset Metrics"
                 :on-click [:metrics/reset-performance]
                 :background-color accent-color
                 :text-color 0xFF0F111A
                 :bounds {:height 32.0}}]])))
