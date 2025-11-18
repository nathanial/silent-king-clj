(ns silent-king.reactui.components.hyperlane-settings
  "Pure components for the hyperlane settings panel.")

(set! *warn-on-reflection* true)

(def ^:const panel-bounds {:x 368 :y 24 :width 360})
(def ^:const slider-width (- (:width panel-bounds) 24.0))

(def ^:const accent-color 0xFF9CDCFE)
(def ^:const text-color 0xFFCBCBCB)
(def ^:const muted-color 0xFFB3B3B3)
(def ^:const panel-background 0xCC171B25)
(def ^:const section-background 0xFF2D2F38)

(def color-options
  [{:value :blue :label "Blue"}
   {:value :red :label "Red"}
   {:value :green :label "Green"}
   {:value :rainbow :label "Rainbow"}])

(def ^:const color-dropdown-id :hyperlane-color)

(defn- header-row
  [expanded?]
  [:hstack {:gap 8
            :padding {:bottom 2}}
   [:label {:text "Hyperlane Settings"
            :color accent-color
            :font-size 16.0
            :bounds {:width 200.0}}]
   [:button {:label (if expanded? "Hide" "Show")
             :on-click [:ui/toggle-hyperlane-panel]
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
               :on-change event
               :bounds {:width slider-width}}]]))

(defn- color-row
  [selected expanded?]
  [:vstack {:gap 4}
   [:label {:text "Color Scheme"
            :color muted-color}]
   [:dropdown {:id color-dropdown-id
               :bounds {:width slider-width}
               :options color-options
               :selected selected
               :expanded? expanded?
               :background-color section-background
               :option-background 0xFF1F2330
               :option-selected-background accent-color
               :option-selected-text-color 0xFF0F111A
               :text-color text-color
               :on-toggle [:ui.dropdown/toggle color-dropdown-id]
               :on-select [:hyperlanes/set-color-scheme]
               :on-close [:ui.dropdown/close color-dropdown-id]}]])

(defn- animation-row
  [animation?]
  [:hstack {:gap 8}
   [:label {:text "Animation"
            :color muted-color
            :bounds {:width 180.0}}]
   (toggle-button (if animation? "Disable" "Enable")
                  [:hyperlanes/set-animation? (not animation?)]
                  animation?)])

(defn- visibility-row
  [enabled?]
  [:vstack {:gap 6}
   [:label {:text "Visibility"
            :color muted-color}]
   (toggle-button (if enabled? "Disable Hyperlanes" "Enable Hyperlanes")
                  [:hyperlanes/set-enabled? (not enabled?)]
                  enabled?)])

(defn- settings-content
  [{:keys [enabled? opacity color-scheme animation? animation-speed line-width]}
   {:keys [color-dropdown-expanded?]}]
  [:vstack {:gap 10}
   (visibility-row enabled?)
   (color-row color-scheme color-dropdown-expanded?)
   (slider-row {:label "Opacity"
                :value opacity
                :min 0.05
                :max 1.0
                :step 0.05
                :event [:hyperlanes/set-opacity]
                :formatter (fn [v]
                             (format "%d%%" (int (Math/round (* 100.0 (double v))))))})
   (animation-row animation?)
   (slider-row {:label "Animation Speed"
                :value animation-speed
                :min 0.1
                :max 3.0
                :step 0.1
                :event [:hyperlanes/set-animation-speed]
                :formatter (fn [v]
                             (format "%.1fx" (double v)))})
   (slider-row {:label "Line Width"
                :value line-width
                :min 0.4
                :max 3.0
                :step 0.1
                :event [:hyperlanes/set-line-width]
                :formatter (fn [v]
                             (format "%.1fpx" (double v)))})
   [:button {:label "Reset to Defaults"
             :on-click [:hyperlanes/reset]
             :background-color accent-color
             :text-color 0xFF0F111A
             :bounds {:height 34.0}}]])

(defn hyperlane-settings-panel
  [{:keys [settings expanded? color-dropdown-expanded?]}]
  (let [content (when expanded?
                  (settings-content settings {:color-dropdown-expanded? color-dropdown-expanded?}))]
    [:vstack {:key :hyperlane-settings-panel
              :bounds panel-bounds
              :padding {:all 12}
              :gap 12
              :background-color panel-background}
     (header-row expanded?)
     content]))
