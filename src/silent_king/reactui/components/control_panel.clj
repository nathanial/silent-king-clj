(ns silent-king.reactui.components.control-panel
  "Pure components for the top-left control panel.")

(set! *warn-on-reflection* true)

(def ^:const default-panel-bounds {:width 320.0
                                   :height 420.0})

(def ^:const accent-color 0xFF9CDCFE)
(def ^:const text-color 0xFFCBCBCB)
(def ^:const muted-color 0xFFB3B3B3)

(defn- format-zoom
  [zoom]
  (format "Zoom: %.2fx" (double zoom)))

(defn- stat-label
  [label value]
  [:label {:text (str label ": " value)
           :color muted-color
           :font-size 14.0}])

(defn control-panel
  [{:keys [zoom hyperlanes-enabled? voronoi-enabled? stars-and-planets-enabled? metrics ui-scale]}]
  (let [hyperlane-label (if hyperlanes-enabled?
                          "Disable Hyperlanes"
                          "Enable Hyperlanes")
        voronoi-label (if voronoi-enabled?
                         "Disable Voronoi"
                         "Enable Voronoi")
        stars-label (if stars-and-planets-enabled?
                      "Disable Stars & Planets"
                      "Enable Stars & Planets")]
    [:vstack {:key :control-panel
              :padding {:all 12}
              :gap 8
              :background-color 0xCC171B25}
     [:label {:text "Controls"
              :color accent-color
              :font-size 16.0}]
     [:button {:label stars-label
               :on-click [:ui/toggle-stars-and-planets]
               :background-color 0xFF343844
               :text-color text-color}]
     [:button {:label hyperlane-label
               :on-click [:ui/toggle-hyperlanes]
               :background-color 0xFF2D2F38
               :text-color text-color}]
     [:button {:label voronoi-label
               :on-click [:ui/toggle-voronoi]
               :background-color 0xFF242734
               :text-color text-color}]
     [:label {:text (format-zoom zoom)
              :color text-color}]
     [:slider {:value (double zoom)
               :min 0.4
               :max 4.0
               :step 0.1
               :on-change [:ui/set-zoom]}]
     [:label {:text (format "UI Scale: %.1f×" (double ui-scale))
              :color text-color}]
     [:slider {:value (double ui-scale)
               :min 1.0
               :max 3.0
               :step 0.1
               :on-change [:ui/set-scale]}]
     [:vstack {:gap 2}
      [:label {:text "Performance"
               :color accent-color
               :font-size 15.0}]
      (stat-label "FPS" (format "%.1f" (double (get metrics :fps 0.0))))
      (stat-label "Visible Stars" (long (get metrics :visible-stars 0)))
      (stat-label "Draw Calls" (long (get metrics :draw-calls 0)))]]))
