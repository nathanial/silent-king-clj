(ns silent-king.reactui.primitives.galaxy
  "Galaxy primitive: renders the main game view within a UI component."
  (:require             [silent-king.camera :as camera]
            [silent-king.color :as color]
            [silent-king.galaxy :as galaxy]
            [silent-king.hyperlanes :as hyperlanes]
            [silent-king.reactui.core :as core]
            [silent-king.reactui.events :as ui-events]
            [silent-king.reactui.layout :as layout]
            [silent-king.reactui.render :as render]
            [silent-king.regions :as regions]
            [silent-king.render.commands :as commands]
            [silent-king.render.galaxy :as render-galaxy]
            [silent-king.selection :as selection]
            [silent-king.state :as state]
            [silent-king.voronoi :as voronoi]))

(set! *warn-on-reflection* true)

(defmethod core/normalize-tag :galaxy
  [_ props child-forms]
  ((core/leaf-normalizer :galaxy) props child-forms))

(defmethod layout/layout-node :galaxy
  [node context]
  (let [bounds* (layout/resolve-bounds node context)
        ;; Default to a reasonable size if not specified, but allow full flex
        width (double (if (pos? (:width bounds*)) (:width bounds*) 400.0))
        height (double (if (pos? (:height bounds*)) (:height bounds*) 400.0))
        final-bounds (assoc bounds* :width width :height height)]
    (assoc node
           :layout {:bounds final-bounds}
           :children [])))

(defn- galaxy-interaction-value
  [node px py]
  (let [{:keys [game-state]} (:props node)
        {:keys [x y]} (layout/bounds node)
        local-x (- px x)
        local-y (- py y)
        camera (state/get-camera game-state)]
    {:start-pointer {:x px :y py}
     :start-pan {:x (:pan-x camera) :y (:pan-y camera)}
     :local-pos {:x local-x :y local-y}}))

(defn plan-galaxy
  [node]
  (let [{:keys [game-state]} (:props node)
        {:keys [x y width height]} (layout/bounds node)]
    (if-not game-state
      [(commands/rect {:x x :y y :width width :height height}
                      {:fill-color (color/hsv 0 0 20)}) ;; Placeholder if no state
       (commands/text {:text "No Game State" 
                       :position {:x (+ x 10) :y (+ y 20)} 
                       :font nil 
                       :color (color/hsv 0 0 100)})]
      (let [camera (state/get-camera game-state)
            zoom (:zoom camera)
            pan-x (:pan-x camera)
            pan-y (:pan-y camera)
            time-state (state/get-time game-state)
            time (:current-time time-state)
            
            assets (state/get-assets game-state)
            star-images (:star-images assets)
            planet-atlas-image (:planet-atlas-image-medium assets)
            planet-atlas-metadata (:planet-atlas-metadata-medium assets)
            atlas-image (:atlas-image-medium assets)
            atlas-metadata (:atlas-metadata-medium assets)
            atlas-size (:atlas-size-medium assets)
            
            stars (state/star-seq game-state)
            planets (state/planet-seq game-state)
            selected-star-id (state/selected-star-id game-state)
            
            hyperlanes-enabled (state/hyperlanes-enabled? game-state)
            voronoi-enabled (state/voronoi-enabled? game-state)
            render-planets? (state/stars-and-planets-enabled? game-state)
            
            ;; Culling - use widget dimensions
            visible-stars (filter #(render-galaxy/star-visible? (:x %) (:y %) (:size %)
                                                              pan-x pan-y width height zoom)
                                  stars)
            
            visible-planets (if render-planets?
                              (map (fn [{:keys [star-id radius size] :as planet}]
                                     (if-let [star (state/star-by-id game-state star-id)]
                                       (let [{:keys [x y]} (galaxy/planet-position planet star time)
                                             planet-x x
                                             planet-y y
                                             star-x (:x star)
                                             star-y (:y star)
                                             
                                             screen-star-x (camera/transform-position star-x zoom pan-x)
                                             screen-star-y (camera/transform-position star-y zoom pan-y)
                                             screen-x (camera/transform-position planet-x zoom pan-x)
                                             screen-y (camera/transform-position planet-y zoom pan-y)
                                             screen-size (camera/transform-size (double (or size 8.0)) zoom)
                                             screen-radius (* (double (or radius 0.0)) (camera/zoom->position-scale zoom))]
                                         (assoc planet
                                                :screen-x screen-x
                                                :screen-y screen-y
                                                :screen-size screen-size
                                                :star-screen-x screen-star-x
                                                :star-screen-y screen-star-y
                                                :screen-radius screen-radius
                                                :ring-visible? (render-galaxy/orbit-visible? screen-star-x screen-star-y screen-radius width height)
                                                :planet-visible? (render-galaxy/planet-visible? screen-x screen-y screen-size width height)))
                                       planet))
                                   planets)
                              [])
            
            ;; 1. Background
            base-commands [(commands/save)
                           (commands/translate x y)
                           (commands/clip-rect {:x 0 :y 0 :width width :height height})
                           (commands/rect {:x 0 :y 0 :width width :height height} 
                                          {:fill-color (color/rgb 0 0 0)})]
            
            ;; 2. Hyperlanes
            hyper-plan (if hyperlanes-enabled
                         (hyperlanes/plan-all-hyperlanes width height zoom pan-x pan-y game-state time)
                         {:commands []})
            
            ;; 3. Voronoi
            voronoi-plan (if (and voronoi-enabled (seq (state/voronoi-cells game-state)))
                           (voronoi/plan-voronoi-cells width height zoom pan-x pan-y game-state time)
                           {:commands []})
            
            ;; 4. Regions (Void Carver)
            region-plan (regions/plan-regions zoom pan-x pan-y game-state)
            
            ;; 5. Stars
            ;; Note: using loop/recur or reduce for performance
            star-commands (reduce (fn [cmds {:keys [id x y size rotation-speed sprite-path]}]
                                    (let [screen-x (camera/transform-position x zoom pan-x)
                                          screen-y (camera/transform-position y zoom pan-y)
                                          screen-size (camera/transform-size size zoom)
                                          rotation (* time 30 (double (or rotation-speed 0.0)))
                                          ;; Determine LOD (simplified logic from core)
                                          lod-level (if (> screen-size 128) :full :atlas)
                                          
                                          star-cmds (cond
                                                      (= lod-level :full) 
                                                      (render-galaxy/plan-full-res-star star-images sprite-path screen-x screen-y screen-size rotation)
                                                      :else 
                                                      (render-galaxy/plan-star-from-atlas atlas-image atlas-metadata sprite-path screen-x screen-y screen-size rotation atlas-size))
                                          
                                          star-cmds (cond-> (or star-cmds [])
                                                      (= id selected-star-id) (into (render-galaxy/plan-selection-highlight screen-x screen-y screen-size time)))]
                                      (into cmds star-cmds)))
                                  []
                                  visible-stars)

            ;; 6. Planet Rings
            ring-commands (reduce (fn [cmds {:keys [screen-radius star-screen-x star-screen-y ring-visible?]}]
                                    (if (and render-planets? ring-visible?)
                                      (into cmds (render-galaxy/plan-orbit-ring star-screen-x star-screen-y screen-radius))
                                      cmds))
                                  []
                                  visible-planets)

            ;; 7. Planets
            planet-commands (reduce (fn [cmds {:keys [screen-x screen-y screen-size planet-visible? sprite-path]}]
                                      (if (and render-planets? planet-visible? (some? sprite-path))
                                        (into cmds (render-galaxy/plan-planet-from-atlas planet-atlas-image planet-atlas-metadata sprite-path screen-x screen-y screen-size))
                                        cmds))
                                    []
                                    visible-planets)
            
            all-commands (-> base-commands
                             (into (:commands voronoi-plan)) ;; Voronoi first (background layer usually)
                             (into (:commands region-plan))
                             (into (:commands hyper-plan))
                             (into ring-commands)
                             (into star-commands)
                             (into planet-commands)
                             (conj (commands/restore)))]
        all-commands))))

(defmethod render/plan-node :galaxy
  [_context node]
  (plan-galaxy node))

(defmethod core/pointer-down! :galaxy
  [node _game-state x y]
  (let [interaction (galaxy-interaction-value node x y)]
    (core/capture-node! node)
    (core/set-active-interaction! node :galaxy {:value interaction})
    true))

(defmethod core/pointer-drag! :galaxy
  [_node game-state x y]
  (let [interaction (some-> (core/active-interaction) :value)
        {:keys [start-pointer start-pan]} interaction
        dx (- x (:x start-pointer))
        dy (- y (:y start-pointer))]
    (when (and start-pointer start-pan)
      ;; Pan = start-pan + delta
      ;; We want to update the camera pan.
      ;; Note: If we drag LEFT (dx < 0), we want to see what is on the RIGHT.
      ;; Moving camera PAN left (decreasing pan-x) moves the world LEFT.
      ;; So dragging mouse LEFT should move pan LEFT.
      ;; So pan-x = start-pan-x + dx.
      (ui-events/dispatch-event! game-state [:camera/set-pan 
                                             (+ (:x start-pan) dx)
                                             (+ (:y start-pan) dy)]))
    true))

(defmethod core/pointer-up! :galaxy
  [_node game-state x y]
  (let [interaction (some-> (core/active-interaction) :value)
        {:keys [start-pointer]} interaction
        dx (- x (:x start-pointer))
        dy (- y (:y start-pointer))
        dist (Math/sqrt (+ (* dx dx) (* dy dy)))
        click-threshold 5.0]
    (when (and start-pointer (< dist click-threshold))
      (let [local-x (:x (:local-pos interaction))
            local-y (:y (:local-pos interaction))]
        (selection/handle-screen-click! game-state local-x local-y)))
    true))

(defmethod core/scroll! :galaxy
  [node game-state x y _dx dy]
  (let [bounds (layout/bounds node)
        local-x (- x (:x bounds))
        local-y (- y (:y bounds))
        camera (state/get-camera game-state)
        old-zoom (:zoom camera)
        zoom-factor (Math/pow 1.1 dy)
        new-zoom (max 0.4 (min 10.0 (* old-zoom zoom-factor)))
        old-pan-x (:pan-x camera)
        old-pan-y (:pan-y camera)
        ;; Calculate world point under cursor using old zoom/pan
        world-x (camera/inverse-transform-position local-x old-zoom old-pan-x)
        world-y (camera/inverse-transform-position local-y old-zoom old-pan-y)
        ;; Calculate new pan so that world point projects to same cursor position
        new-pan-x (- local-x (* world-x (camera/zoom->position-scale new-zoom)))
        new-pan-y (- local-y (* world-y (camera/zoom->position-scale new-zoom)))]
    (state/update-camera! game-state assoc
                          :zoom new-zoom
                          :pan-x new-pan-x
                          :pan-y new-pan-y)
    true))

