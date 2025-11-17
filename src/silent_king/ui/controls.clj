(ns silent-king.ui.controls
  "High-level UI construction functions for Silent King"
  (:require [silent-king.state :as state]
            [silent-king.widgets.core :as wcore]))

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
  "Create the main control panel UI with zoom slider, reset button, hyperlane toggle, and stats"
  [game-state]
  (let [;; Panel background
        panel (wcore/panel
               :id :control-panel
               :bounds {:x 20 :y 20 :width 280 :height 220}
               :visual {:background-color 0xCC222222
                       :border-radius 12.0
                       :shadow {:offset-x 0 :offset-y 4 :blur 12 :color 0x80000000}})

        ;; Title
        title (wcore/label "Silent King Controls"
                          :bounds {:width 260 :height 30}
                          :visual {:text-color 0xFFFFFFFF
                                  :font-size 18
                                  :font-weight :bold
                                  :text-align :left})

        ;; Zoom label
        zoom-label (wcore/label "Zoom"
                               :bounds {:width 260 :height 20}
                               :visual {:text-color 0xFFAAAAAA
                                       :font-size 14})

        ;; Zoom slider
        zoom-slider (wcore/slider 0.4 10.0 1.0
                                 #(set-camera-zoom! game-state %)
                                 :id :zoom-slider
                                 :bounds {:width 260 :height 30}
                                 :step 0.1)

        ;; Hyperlane toggle button
        hyperlane-button (wcore/button "Toggle Hyperlanes"
                                       #(state/toggle-hyperlanes! game-state)
                                       :id :hyperlane-toggle
                                       :bounds {:width 260 :height 36}
                                       :visual {:background-color 0xFF6699FF
                                               :border-radius 6.0})

        ;; Reset camera button
        reset-button (wcore/button "Reset Camera"
                                  #(reset-camera! game-state)
                                  :id :reset-camera
                                  :bounds {:width 260 :height 36}
                                  :visual {:background-color 0xFF3366CC
                                          :border-radius 6.0})

        ;; Stats label (will be updated each frame)
        stats-label (wcore/label "FPS: -- | Stars: --"
                                :id :stats-label
                                :bounds {:width 260 :height 20}
                                :visual {:text-color 0xFFAAAAAA
                                        :font-size 12})

        ;; Create VStack with all children
        children [title zoom-label zoom-slider hyperlane-button reset-button stats-label]]

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
