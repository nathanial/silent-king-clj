(ns silent-king.ui.controls
  "High-level UI construction functions for Silent King"
  (:require [silent-king.state :as state]
            [silent-king.widgets.core :as wcore]
            [silent-king.ui.theme :as theme]))

(set! *warn-on-reflection* true)

;; =============================================================================
;; Camera Control Functions
;; =============================================================================

(defn reset-camera!
  "Reset camera to default position and zoom"
  [game-state]
  (state/update-camera! game-state
                        (fn [cam]
                          (assoc cam
                                :zoom 1.0
                                :pan-x 0.0
                                :pan-y 0.0)))
  (println "Camera reset to default"))

(defn set-camera-zoom!
  "Set camera zoom to a specific value"
  [game-state zoom]
  (state/update-camera! game-state
                        (fn [cam]
                          (assoc cam :zoom zoom))))

;; =============================================================================
;; Control Panel Creation
;; =============================================================================

(defn create-control-panel!
  "Create the main control panel UI with zoom slider, reset button, hyperlane toggle, minimap toggle, and stats"
  [game-state]
  (let [;; Panel background
        panel (wcore/panel
               :id :control-panel
               :bounds {:x (theme/get-panel-dimension :control :margin)
                       :y (theme/get-panel-dimension :control :margin)
                       :width (theme/get-panel-dimension :control :width)
                       :height (theme/get-panel-dimension :control :height)}
               :visual {:background-color (theme/get-color :background :panel-secondary)
                       :border-radius (theme/get-border-radius :xl)
                       :shadow (theme/get-shadow :md)})

        ;; Title
        title (wcore/label "Silent King Controls"
                          :bounds {:width (:width (theme/get-widget-size :button :standard))
                                  :height (:height (theme/get-widget-size :label :large))}
                          :visual {:text-color (theme/get-color :text :primary)
                                  :font-size (theme/get-font-size :heading)
                                  :font-weight :bold
                                  :text-align :left})

        ;; Zoom label
        zoom-label (wcore/label "Zoom"
                               :bounds {:width (:width (theme/get-widget-size :button :standard))
                                       :height (:height (theme/get-widget-size :label :small))}
                               :visual {:text-color (theme/get-color :text :tertiary)
                                       :font-size (theme/get-font-size :body)})

        ;; Zoom slider
        zoom-slider (wcore/slider (:min (:zoom theme/ranges))
                                 (:max (:zoom theme/ranges))
                                 (:initial (:zoom theme/ranges))
                                 #(set-camera-zoom! game-state %)
                                 :id :zoom-slider
                                 :bounds {:width (:width (theme/get-widget-size :button :standard))
                                         :height (:height (theme/get-widget-size :label :large))}
                                 :step (:step (:zoom theme/ranges)))

        ;; Hyperlane toggle button
        hyperlane-button (wcore/button "Toggle Hyperlanes"
                                       #(state/toggle-hyperlanes! game-state)
                                       :id :hyperlane-toggle
                                       :bounds (theme/get-widget-size :button :standard)
                                       :visual {:background-color (theme/get-color :interactive :secondary)
                                               :border-radius (theme/get-border-radius :md)})

        ;; Minimap toggle button
        minimap-button (wcore/button "Toggle Minimap"
                                     #(state/toggle-minimap! game-state)
                                     :id :minimap-toggle
                                     :bounds (theme/get-widget-size :button :standard)
                                     :visual {:background-color (theme/get-color :interactive :tertiary)
                                             :border-radius (theme/get-border-radius :md)})

        ;; Reset camera button
        reset-button (wcore/button "Reset Camera"
                                  #(reset-camera! game-state)
                                  :id :reset-camera
                                  :bounds (theme/get-widget-size :button :standard)
                                  :visual {:background-color (theme/get-color :interactive :primary)
                                          :border-radius (theme/get-border-radius :md)})

        ;; Stats label (will be updated each frame)
        stats-label (wcore/label "FPS: -- | Stars: --"
                                :id :stats-label
                                :bounds {:width (:width (theme/get-widget-size :button :standard))
                                        :height (:height (theme/get-widget-size :label :small))}
                                :visual {:text-color (theme/get-color :text :tertiary)
                                        :font-size (theme/get-font-size :tiny)})

        ;; Create VStack with all children
        children [title zoom-label zoom-slider hyperlane-button minimap-button reset-button stats-label]]

    ;; Add panel and children to game state (layout will run automatically)
    (wcore/add-widget-tree! game-state panel children)

    (println "Control panel created")))

;; =============================================================================
;; Stats Update
;; =============================================================================

(defn update-stats-label!
  "Update the stats label with current FPS and entity counts"
  [game-state fps total-stars visible-stars hyperlanes-rendered hyperlanes-enabled]
  (when-let [[entity-id _] (wcore/get-widget-by-id game-state :stats-label)]
    (let [hyperlane-text (if hyperlanes-enabled
                          (str "H-lanes: " hyperlanes-rendered)
                          "H-lanes: OFF")
          stats-text (str "FPS: " (format "%.0f" fps)
                         " | Stars: " total-stars "/" visible-stars
                         " | " hyperlane-text)]
      (state/update-entity! game-state entity-id
                           #(assoc-in % [:components :visual :text] stats-text)))))

(defn update-zoom-slider!
  "Update the zoom slider to reflect current camera zoom"
  [game-state current-zoom]
  (when-let [[entity-id _] (wcore/get-widget-by-id game-state :zoom-slider)]
    (state/update-entity! game-state entity-id
                         #(assoc-in % [:components :value :current] current-zoom))))

;; =============================================================================
;; Minimap Creation
;; =============================================================================

(defn create-minimap!
  "Create the minimap widget for galaxy navigation.
  The minimap appears in the bottom-right corner and shows the entire galaxy.
  Click the minimap to smoothly pan the camera to that location."
  [game-state]
  (let [minimap (wcore/minimap
                 :id :galaxy-minimap
                 :bounds (:minimap theme/widget-sizes))]
    (wcore/add-widget! game-state minimap)
    (println "Minimap created")))
