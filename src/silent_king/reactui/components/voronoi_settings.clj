(ns silent-king.reactui.components.voronoi-settings
  "Pure components for the Voronoi settings panel.")

(set! *warn-on-reflection* true)

(def ^:const default-panel-bounds {:width 340.0
                                   :height 460.0})

(def ^:const accent-color 0xFF9CDCFE)
(def ^:const text-color 0xFFCBCBCB)
(def ^:const muted-color 0xFFB3B3B3)
(def ^:const panel-background 0xCC171B25)
(def ^:const section-background 0xFF2D2F38)

(def color-options
  [{:value :monochrome :label "Monochrome"}
   {:value :by-density :label "By Density"}
   {:value :by-degree :label "By Degree"}])

(def ^:const color-dropdown-id :voronoi-color)

(defn- header-row
  [expanded?]
  [:hstack {:gap 8
            :padding {:bottom 2}}
   [:label {:text "Voronoi Settings"
            :color accent-color
            :font-size 16.0
            :bounds {:width 200.0}}]
   [:button {:label (if expanded? "Hide" "Show")
             :on-click [:ui/toggle-voronoi-panel]
             :background-color section-background
             :text-color text-color
             :bounds {:width 96.0 :height 32.0}}]])

(defn- toggle-button
  [label event enabled?]
  [:button {:label label
            :on-click event
            :background-color (if enabled?
                                0xFF3C4456
                                section-background)
            :text-color text-color
            :bounds {:height 34.0}}])

(defn- slider-row
  [{:keys [label value min max step event formatter]}]
  (let [formatted (if formatter
                    (formatter value)
                    (format "%.2f" (double value)))]
    [:vstack {:gap 4}
     [:hstack {:gap 8}
      [:label {:text label
               :color muted-color
               :bounds {:width 180.0}}]
      [:label {:text formatted
               :color text-color
               :font-size 14.0}]]
     [:slider {:value (double value)
               :min min
               :max max
               :step step
               :on-change event}]]))

(defn- color-row
  [selected expanded?]
  [:vstack {:gap 4}
   [:label {:text "Color Scheme"
            :color muted-color}]
   [:dropdown {:id color-dropdown-id
               :options color-options
               :selected selected
               :expanded? expanded?
               :background-color section-background
               :option-background 0xFF1F2330
               :option-selected-background accent-color
               :option-selected-text-color 0xFF0F111A
               :text-color text-color
               :on-toggle [:ui.dropdown/toggle color-dropdown-id]
               :on-select [:voronoi/set-color-scheme]
               :on-close [:ui.dropdown/close color-dropdown-id]}]])

(defn- settings-content
  [{:keys [enabled? opacity line-width color-scheme show-centroids?
           relax-iterations relax-step relax-max-displacement relax-clip-to-envelope?
           hide-border-cells?]}
   {:keys [color-dropdown-expanded?]}]
  [:vstack {:gap 10}
   [:label {:text "Visibility"
            :color muted-color}]
   (toggle-button (if enabled? "Disable Voronoi" "Enable Voronoi")
                  [:voronoi/set-enabled? (not enabled?)]
                  enabled?)
   (color-row color-scheme color-dropdown-expanded?)
   (slider-row {:label "Opacity"
                :value opacity
                :min 0.05
                :max 1.0
                :step 0.05
                :event [:voronoi/set-opacity]
                :formatter (fn [v]
                             (format "%d%%" (int (Math/round (* 100.0 (double v))))))})
   (slider-row {:label "Line Width"
                :value line-width
                :min 0.5
                :max 4.0
                :step 0.1
                :event [:voronoi/set-line-width]
                :formatter (fn [v]
                             (format "%.1fpx" (double v)))})
   (toggle-button (if show-centroids? "Hide Centroids" "Show Centroids")
                  [:voronoi/set-show-centroids? (not show-centroids?)]
                  show-centroids?)
   (toggle-button (if hide-border-cells?
                    "Show Border Cells"
                    "Hide Border Cells")
                  [:voronoi/set-hide-border-cells? (not hide-border-cells?)]
                  hide-border-cells?)
   [:label {:text "Relaxation (Lloyd)"
            :color muted-color
            :padding {:top 4}}]
   (slider-row {:label "Iterations"
                :value relax-iterations
                :min 0
                :max 5
                :step 1
                :event [:voronoi/set-relax-iterations]
                :formatter (fn [v] (format "%d" (int (Math/round (double v)))))})
   (slider-row {:label "Step Factor"
                :value relax-step
                :min 0.0
                :max 1.0
                :step 0.05
                :event [:voronoi/set-relax-step]
                :formatter (fn [v] (format "%.2f" (double v)))})
   (slider-row {:label "Max Displacement"
                :value relax-max-displacement
                :min 0.0
                :max 500.0
                :step 10.0
                :event [:voronoi/set-relax-max-displacement]
                :formatter (fn [v]
                             (if (<= (double v) 0.0)
                               "Unlimited"
                               (format "%.0f" (double v))))})
   (toggle-button (if relax-clip-to-envelope?
                    "Clip to Envelope"
                    "Do Not Clip")
                  [:voronoi/set-relax-clip? (not relax-clip-to-envelope?)]
                  relax-clip-to-envelope?)
   [:button {:label "Reset to Defaults"
             :on-click [:voronoi/reset]
             :background-color accent-color
             :text-color 0xFF0F111A
             :bounds {:height 34.0}}]])

(defn voronoi-settings-panel
  [{:keys [settings expanded? color-dropdown-expanded?]}]
  (let [content (when expanded?
                  (settings-content settings {:color-dropdown-expanded? color-dropdown-expanded?}))]
    [:vstack {:key :voronoi-settings-panel
              :padding {:all 12}
              :gap 12
              :background-color panel-background}
     (header-row expanded?)
     content]))
