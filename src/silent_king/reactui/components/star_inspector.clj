(ns silent-king.reactui.components.star-inspector
  "Right-side panel that shows the currently selected star."
  (:require [clojure.string :as str]
            [silent-king.reactui.theme :as theme]))

(set! *warn-on-reflection* true)

(def ^:const default-panel-bounds {:width 360.0
                                   :height 520.0})
(def panel-background (:panel-bg-star theme/colors))
(def section-background (:section-bg-alt theme/colors))
(def accent-color (:accent theme/colors))
(def text-color (:text theme/colors))
(def muted-color (:text-muted-alt theme/colors))
(def ^:const zoom-target 2.4)

(defn- format-position
  [{:keys [x y]}]
  (if (and (number? x) (number? y))
    (format "X %.0f · Y %.0f" (double x) (double y))
    "--"))

(defn- format-size
  [size]
  (if (number? size)
    (format "%.0f px" (double size))
    "--"))

(defn- format-density
  [density]
  (if (number? density)
    (format "%.0f%%" (double (* 100.0 density)))
    "--"))

(defn- format-rotation
  [rotation-speed]
  (if (number? rotation-speed)
    (format "%.2fx" (double rotation-speed))
    "--"))

(defn- format-distance
  [distance]
  (if (number? distance)
    (format "%.0f px" (double distance))
    "--"))

(defn- stat-row
  [label value]
  [:hstack {:gap 8
            :padding {:vertical 2}}
   [:label {:text label
            :color muted-color
            :bounds {:width 120.0}}]
   [:label {:text value
            :color text-color
            :font-size 14.0}]])

(defn- preview-block
  [{:keys [sprite]}]
  (let [label (if (and sprite (not (str/blank? sprite)))
                sprite
                "No sprite metadata")]
    [:vstack {:gap 4
              :padding {:all 10}
              :background-color section-background}
     [:label {:text "Preview"
              :color accent-color
              :font-size 14.0}]
     [:label {:text label
              :color text-color
              :font-size 13.0
              :bounds {:height 20.0}}]]))

(defn- stats-section
  [{:keys [position size density rotation-speed]}]
  [:vstack {:gap 6
            :padding {:all 10}
            :background-color section-background}
   [:label {:text "Details"
            :color accent-color
            :font-size 14.0}]
   (stat-row "Position" (format-position position))
   (stat-row "Size" (format-size size))
   (stat-row "Density" (format-density density))
   (stat-row "Rotation" (format-rotation rotation-speed))])

(defn- connection-row
  [{:keys [label distance]}]
  [:hstack {:gap 8
            :padding {:vertical 4
                      :horizontal 6}
            :background-color section-background}
   [:label {:text label
            :color text-color
            :bounds {:width 180.0}}]
   [:label {:text (format-distance distance)
            :color muted-color
            :font-size 13.0}]])

(defn- connections-section
  [connections]
  [:vstack {:gap 6}
   [:label {:text "Hyperlanes"
            :color accent-color
            :font-size 14.0}]
   (if (seq connections)
     (into [:vstack {:gap 4}]
           (map connection-row connections))
     [:label {:text "No direct hyperlane connections."
              :color muted-color
              :font-size 13.0}])])

(defn- header-block
  [{:keys [selection visible?]}]
  (let [{:keys [name star-id position]} selection
        subtitle (cond
                   selection (format "ID %s • %s"
                                     (or star-id "--")
                                     (format-position position))
                   visible? "Ready for selection"
                   :else "Inspector hidden")]
    [:vstack {:gap 4}
     [:label {:text (if selection
                      name
                      "Star Inspector")
              :color accent-color
              :font-size 16.0}]
     [:label {:text subtitle
              :color text-color
              :font-size 14.0}]]))

(defn- empty-state
  []
  [:vstack {:gap 4
            :padding {:vertical 8}}
   [:label {:text "No star selected"
            :color text-color
            :font-size 15.0}]
   [:label {:text "Click a star in the galaxy to see its details."
            :color muted-color
            :font-size 13.0}]])

(defn- action-row
  [has-selection?]
  [:hstack {:gap 8}
   [:button {:label "Zoom to Star"
             :on-click [:ui/zoom-to-selected-star {:zoom zoom-target}]
             :background-color (if has-selection?
                                 accent-color
                                 (:btn-bg-enabled theme/colors))
             :text-color (if has-selection?
                           (:option-selected-text theme/colors)
                           text-color)
             :bounds {:height 34.0}}]
   [:button {:label "Clear Selection"
             :on-click [:ui/clear-selection]
             :background-color (:btn-bg-hyperlanes theme/colors)
             :text-color text-color
             :bounds {:height 34.0}}]])

(defn star-inspector
  [{:keys [selection visible?]}]
  (let [has-selection? (boolean selection)]
    [:vstack {:key :star-inspector
              :padding {:all 14}
              :gap 12
              :background-color panel-background}
     (header-block {:selection selection
                    :visible? visible?})
     (if has-selection?
       [:vstack {:gap 10}
        (preview-block selection)
        (stats-section selection)
        (connections-section (:connections selection))]
       (empty-state))
     (action-row has-selection?)]))
