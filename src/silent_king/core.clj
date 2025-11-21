(ns silent-king.core
  (:require [silent-king.assets :as assets]
            [silent-king.camera :as camera]
            [silent-king.state :as state]
            [silent-king.reactui.app :as react-app]
            [silent-king.reactui.core :as reactui]
            [silent-king.selection :as selection]
            [silent-king.galaxy :as galaxy]
            [silent-king.hyperlanes :as hyperlanes]
            [silent-king.voronoi :as voronoi]
            [silent-king.regions :as regions]
            [silent-king.render.galaxy :as render-galaxy]
            [silent-king.render.commands :as commands]
            [silent-king.render.skia :as skia]
            [nrepl.server :as nrepl]
            [cider.nrepl :refer [cider-nrepl-handler]]
            [silent-king.color :as color])
  (:import [org.lwjgl.glfw GLFW GLFWErrorCallback GLFWCursorPosCallbackI GLFWMouseButtonCallbackI GLFWScrollCallbackI GLFWKeyCallbackI]
           [org.lwjgl.opengl GL GL11 GL30]
           [org.lwjgl.system MemoryUtil]
           [io.github.humbleui.skija Canvas Surface DirectContext BackendRenderTarget FramebufferFormat ColorSpace SurfaceOrigin SurfaceColorFormat Image]))
(defonce game-state (atom (state/create-game-state)))
(defonce render-state (atom (state/create-render-state)))
(def ^:const world-click-threshold 6.0)
(def ^:const planet-visibility-zoom 1.5)

;; Backwards-compatibility aliases for widely-referenced camera helpers
(def zoom->position-scale camera/zoom->position-scale)
(def zoom->size-scale camera/zoom->size-scale)
(def transform-position camera/transform-position)
(def transform-size camera/transform-size)
(def inverse-transform-position camera/inverse-transform-position)

;; =============================================================================
;; GLFW Initialization
;; =============================================================================

(defn init-glfw []
  (println "Initializing GLFW...")
  (GLFWErrorCallback/createPrint System/err)
  (when-not (GLFW/glfwInit)
    (throw (Exception. "Unable to initialize GLFW")))

  ;; Configure GLFW
  (GLFW/glfwDefaultWindowHints)
  (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE GLFW/GLFW_FALSE)
  (GLFW/glfwWindowHint GLFW/GLFW_RESIZABLE GLFW/GLFW_TRUE)
  (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MAJOR 3)
  (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MINOR 3)
  (GLFW/glfwWindowHint GLFW/GLFW_OPENGL_PROFILE GLFW/GLFW_OPENGL_CORE_PROFILE)
  (GLFW/glfwWindowHint GLFW/GLFW_OPENGL_FORWARD_COMPAT GLFW/GLFW_TRUE))

(defn create-window [width height ^CharSequence title]
  (println "Creating window...")
  (let [window (GLFW/glfwCreateWindow (int width) (int height) title MemoryUtil/NULL MemoryUtil/NULL)]
    (when (= window MemoryUtil/NULL)
      (throw (Exception. "Failed to create GLFW window")))

    ;; Center window
    (let [vidmode (GLFW/glfwGetVideoMode (GLFW/glfwGetPrimaryMonitor))]
      (GLFW/glfwSetWindowPos window
                             (/ (- (.width vidmode) width) 2)
                             (/ (- (.height vidmode) height) 2)))

    ;; Make OpenGL context current
    (GLFW/glfwMakeContextCurrent window)
    (GLFW/glfwSwapInterval 1) ;; Enable vsync
    (GLFW/glfwShowWindow window)

    ;; Initialize OpenGL bindings
    (GL/createCapabilities)

    window))

(defn setup-mouse-callbacks [window game-state]
  (GLFW/glfwSetCursorPosCallback
   window
   (reify GLFWCursorPosCallbackI
     (invoke [_ win xpos ypos]
       (let [win-width-arr (int-array 1)
             win-height-arr (int-array 1)
             fb-width-arr (int-array 1)
             fb-height-arr (int-array 1)
             _ (GLFW/glfwGetWindowSize win win-width-arr win-height-arr)
             _ (GLFW/glfwGetFramebufferSize win fb-width-arr fb-height-arr)
             scale-x (/ (aget fb-width-arr 0) (double (aget win-width-arr 0)))
             scale-y (/ (aget fb-height-arr 0) (double (aget win-height-arr 0)))
             fb-xpos (* xpos scale-x)
             fb-ypos (* ypos scale-y)
             input (state/get-input game-state)
             old-x (:mouse-x input)
             old-y (:mouse-y input)
             dragging (:dragging input)
             slider-handled? (boolean (reactui/handle-pointer-drag! game-state fb-xpos fb-ypos))]
         (when (and dragging (not slider-handled?))
           (let [dx (- fb-xpos old-x)
                 dy (- fb-ypos old-y)]
             (state/update-camera! game-state
                                   (fn [cam]
                                     (-> cam
                                         (update :pan-x #(+ % dx))
                                         (update :pan-y #(+ % dy)))))))
         (state/update-input! game-state assoc
                              :mouse-x fb-xpos
                              :mouse-y fb-ypos
                              :mouse-initialized? true)))))

  (GLFW/glfwSetMouseButtonCallback
   window
   (reify GLFWMouseButtonCallbackI
     (invoke [_ _win button action _mods]
       (when (= button GLFW/GLFW_MOUSE_BUTTON_LEFT)
         (let [input (state/get-input game-state)
               x (:mouse-x input)
               y (:mouse-y input)
               pressed? (= action GLFW/GLFW_PRESS)]
           (if pressed?
             (let [handled? (reactui/handle-pointer-down! game-state x y)]
               (state/update-input! game-state assoc
                                    :dragging (not handled?)
                                    :ui-active? (boolean handled?)
                                    :mouse-down-x x
                                    :mouse-down-y y))
             (do
               (reactui/handle-pointer-up! game-state x y)
               (let [ui-active? (boolean (:ui-active? input))
                     dx (- (double x) (double (:mouse-down-x input)))
                     dy (- (double y) (double (:mouse-down-y input)))
                     movement (Math/sqrt (+ (* dx dx) (* dy dy)))
                     click? (and (not ui-active?)
                                 (<= movement world-click-threshold))]
                 (when click?
                   (selection/handle-screen-click! game-state x y)))
               (state/update-input! game-state assoc
                                    :dragging false
                                    :ui-active? false))))))))

  (GLFW/glfwSetScrollCallback
   window
   (reify GLFWScrollCallbackI
     (invoke [_ _win _xoffset yoffset]
       (let [input (state/get-input game-state)
             mouse-x (:mouse-x input)
             mouse-y (:mouse-y input)
             camera (state/get-camera game-state)
             old-zoom (:zoom camera)
             zoom-factor (Math/pow 1.1 yoffset)
             new-zoom (max 0.4 (min 10.0 (* old-zoom zoom-factor)))
             old-pan-x (:pan-x camera)
             old-pan-y (:pan-y camera)
             world-x (inverse-transform-position mouse-x old-zoom old-pan-x)
             world-y (inverse-transform-position mouse-y old-zoom old-pan-y)
             new-pan-x (- mouse-x (* world-x (zoom->position-scale new-zoom)))
             new-pan-y (- mouse-y (* world-y (zoom->position-scale new-zoom)))]
         (state/update-camera! game-state assoc
                               :zoom new-zoom
                               :pan-x new-pan-x
                               :pan-y new-pan-y)))))

  (GLFW/glfwSetKeyCallback
   window
   (reify GLFWKeyCallbackI
     (invoke [_ _win key _scancode action _mods]
       (when (and (= action GLFW/GLFW_PRESS)
                  (= key GLFW/GLFW_KEY_H))
         (state/toggle-hyperlanes! game-state))))))

(defn create-skija-context []
  (println "Creating Skija DirectContext...")
  (DirectContext/makeGL))

(defn create-skija-surface [^DirectContext context width height]
  (let [fb-id (GL11/glGetInteger GL30/GL_FRAMEBUFFER_BINDING)
        render-target (BackendRenderTarget/makeGL
                       width
                       height
                       0    ;; sample count
                       8    ;; stencil bits
                       fb-id
                       FramebufferFormat/GR_GL_RGBA8)]
    (Surface/makeFromBackendRenderTarget
     context
     render-target
     SurfaceOrigin/BOTTOM_LEFT
     SurfaceColorFormat/RGBA_8888
     (ColorSpace/getSRGB))))

(defn frame-world-plan
  "Build world draw commands and metrics for a frame."
  [width height time game-state]
  (let [camera (state/get-camera game-state)
        assets (state/get-assets game-state)
        zoom (:zoom camera)
        pan-x (:pan-x camera)
        pan-y (:pan-y camera)
        hyperlanes-enabled (state/hyperlanes-enabled? game-state)
        voronoi-enabled (state/voronoi-enabled? game-state)
        stars-and-planets-enabled (state/stars-and-planets-enabled? game-state)
        selected-star-id (state/selected-star-id game-state)
        lod-level (cond
                    (< zoom 0.5) :xs
                    (< zoom 1.5) :small
                    (< zoom 4.0) :medium
                    :else :full)
        atlas-image (case lod-level
                      :xs (:atlas-image-xs assets)
                      :small (:atlas-image-small assets)
                      :medium (:atlas-image-medium assets)
                      :full nil)
        atlas-metadata (case lod-level
                         :xs (:atlas-metadata-xs assets)
                         :small (:atlas-metadata-small assets)
                         :medium (:atlas-metadata-medium assets)
                         :full nil)
        atlas-size (case lod-level
                     :xs (:atlas-size-xs assets)
                     :small (:atlas-size-small assets)
                     :medium (:atlas-size-medium assets)
                     :full nil)
        star-images (:star-images assets)
        planet-atlas-image (:planet-atlas-image-medium assets)
        planet-atlas-metadata (:planet-atlas-metadata-medium assets)
        stars-by-id (state/stars game-state)
        stars (vals stars-by-id)
        planets (state/planet-seq game-state)
        visible-stars (if stars-and-planets-enabled
                        (filter (fn [{:keys [x y size]}]
                                  (render-galaxy/star-visible? x y size pan-x pan-y width height zoom))
                                stars)
                        [])
        visible-star-count (if stars-and-planets-enabled (count visible-stars) 0)
        total-star-count (count stars)
        render-planets? (and stars-and-planets-enabled
                             (>= zoom planet-visibility-zoom)
                             planet-atlas-image
                             (seq planet-atlas-metadata))
        visible-planets (when render-planets?
                          (keep (fn [{:keys [star-id radius size] :as planet}]
                                  (when-let [star (get stars-by-id star-id)]
                                    (let [{:keys [x y]} (galaxy/planet-position planet star time)
                                          screen-x (transform-position x zoom pan-x)
                                          screen-y (transform-position y zoom pan-y)
                                          screen-size (transform-size (double (or size 8.0)) zoom)
                                          screen-radius (* (double (or radius 0.0)) (camera/zoom->position-scale zoom))
                                          star-screen-x (transform-position (:x star) zoom pan-x)
                                          star-screen-y (transform-position (:y star) zoom pan-y)
                                          ring-visible? (render-galaxy/orbit-visible? star-screen-x star-screen-y screen-radius width height)
                                          planet-visible? (render-galaxy/planet-visible? screen-x screen-y screen-size width height)]
                                      (when (or ring-visible? planet-visible?)
                                        {:planet planet
                                         :star-screen-x star-screen-x
                                         :star-screen-y star-screen-y
                                         :screen-radius screen-radius
                                         :ring-visible? ring-visible?
                                         :planet-visible? planet-visible?
                                         :screen-x screen-x
                                         :screen-y screen-y
                                         :screen-size screen-size}))))
                                planets))
        visible-planet-count (count visible-planets)
        total-planet-count (count planets)
        hyper-plan (if hyperlanes-enabled
                     (hyperlanes/plan-all-hyperlanes width height zoom pan-x pan-y game-state time)
                     {:commands [] :rendered 0})
        base-commands (vec (:commands hyper-plan))
        world-commands (transient base-commands)
        world-commands (reduce (fn [cmds {:keys [id x y size rotation-speed sprite-path]}]
                                 (let [screen-x (transform-position x zoom pan-x)
                                       screen-y (transform-position y zoom pan-y)
                                       screen-size (transform-size size zoom)
                                       rotation (* time 30 (double (or rotation-speed 0.0)))
                                       star-cmds (cond
                                                   (= lod-level :full) (render-galaxy/plan-full-res-star star-images sprite-path screen-x screen-y screen-size rotation)
                                                   :else (render-galaxy/plan-star-from-atlas atlas-image atlas-metadata sprite-path screen-x screen-y screen-size rotation atlas-size))
                                       star-cmds (cond-> (or star-cmds [])
                                                   (= id selected-star-id) (into (render-galaxy/plan-selection-highlight screen-x screen-y screen-size time)))]
                                   (reduce conj! cmds star-cmds)))
                               world-commands
                               visible-stars)
        voronoi-plan (if (and voronoi-enabled (seq (state/voronoi-cells game-state)))
                       (voronoi/plan-voronoi-cells width height zoom pan-x pan-y game-state time)
                       {:commands [] :rendered 0})
        world-commands (reduce conj! world-commands (:commands voronoi-plan))
        region-plan (regions/plan-regions zoom pan-x pan-y game-state)
        world-commands (reduce conj! world-commands (:commands region-plan))
        world-commands (reduce (fn [cmds {:keys [screen-radius star-screen-x star-screen-y ring-visible?]}]
                                 (if (and render-planets? ring-visible?)
                                   (reduce conj! cmds (render-galaxy/plan-orbit-ring star-screen-x star-screen-y screen-radius))
                                   cmds))
                               world-commands
                               visible-planets)
        {:keys [commands planets-rendered]}
        (reduce (fn [{:keys [commands planets-rendered]} {:keys [planet screen-x screen-y screen-size planet-visible?]}]
                  (if (and render-planets? planet-visible? (some? (:sprite-path planet)))
                    (let [planet-cmds (render-galaxy/plan-planet-from-atlas planet-atlas-image planet-atlas-metadata (:sprite-path planet) screen-x screen-y screen-size)]
                      {:commands (into commands planet-cmds)
                       :planets-rendered (inc planets-rendered)})
                    {:commands commands :planets-rendered planets-rendered}))
                {:commands (persistent! world-commands) :planets-rendered 0}
                visible-planets)
        metrics {:total-stars total-star-count
                 :visible-stars visible-star-count
                 :total-planets total-planet-count
                 :visible-planets visible-planet-count
                 :hyperlane-count (count (state/hyperlanes game-state))
                 :visible-hyperlanes (:rendered hyper-plan)
                 :voronoi-cells (count (state/voronoi-cells game-state))
                 :visible-voronoi (:rendered voronoi-plan)
                 :planets-rendered planets-rendered}]
    {:commands commands
     :metrics metrics}))

(defn draw-frame [^Canvas canvas width height _time game-state]
  (let [ui-result (react-app/render! nil {:x 0.0
                                          :y 0.0
                                          :width width
                                          :height height}
                                     game-state)
        ui-commands (:commands ui-result)
        ;; Just clear to black and then draw the UI
        frame-commands (into [(commands/clear (color/hsv 0 0 0))]
                             ui-commands)]
    (skia/draw-commands! canvas frame-commands)
    (let [time-state (state/get-time game-state)
          frame-count (:frame-count time-state)
          current-time (:current-time time-state)
          fps (if (pos? current-time) (/ frame-count current-time) 0.0)
          frame-time-ms (/ 1000.0 (max fps 0.0001))
          runtime (Runtime/getRuntime)
          used-memory (- (.totalMemory runtime) (.freeMemory runtime))
          memory-mb (/ used-memory 1048576.0)
          ;; Create a basic metrics map since we aren't calculating world metrics here anymore
          metrics {:fps fps
                   :frame-time-ms frame-time-ms
                   :memory-mb memory-mb
                   :current-time current-time}]
      (state/record-performance-metrics! game-state metrics))))

(defn render-loop [game-state render-state]
  (println "Starting render loop...")
  (let [window (state/get-window render-state)
        context (state/get-context render-state)]
    (loop []
      (if (GLFW/glfwWindowShouldClose window)
        nil
        (let [time (state/get-time game-state)
              current-time-ns (System/nanoTime)
              current-time (/ (- current-time-ns (:start-time time)) 1e9)
              fb-width-arr (int-array 1)
              fb-height-arr (int-array 1)
              _ (GLFW/glfwGetFramebufferSize window fb-width-arr fb-height-arr)
              fb-width (aget fb-width-arr 0)
              fb-height (aget fb-height-arr 0)
              current-surface (state/get-surface render-state)]

          ;; Update time in game state
          (state/update-time! game-state assoc
                              :current-time current-time
                              :frame-count (inc (:frame-count time)))

          ;; Update surface if needed
          (when (or (nil? current-surface)
                    (not= fb-width (.getWidth ^Surface current-surface))
                    (not= fb-height (.getHeight ^Surface current-surface)))
            (when current-surface
              (.close ^Surface current-surface))
            (state/set-surface! render-state (create-skija-surface context fb-width fb-height)))

          ;; Set viewport
          (GL11/glViewport 0 0 fb-width fb-height)

          ;; Render with Skija
          (let [^Surface surface (state/get-surface render-state)
                ^Canvas canvas (.getCanvas surface)]
            (draw-frame canvas fb-width fb-height current-time game-state)
            (.flush surface))

          ;; Swap buffers and poll events
          (GLFW/glfwSwapBuffers window)
          (GLFW/glfwPollEvents)

          (recur))))))

(defn cleanup [game-state render-state]
  (println "Cleaning up...")
  (let [surface (state/get-surface render-state)
        context (state/get-context render-state)
        window (state/get-window render-state)
        assets (state/get-assets game-state)]

    ;; Close surface
    (when surface
      (.close ^Surface surface))

    ;; Close DirectContext
    (when context
      (.close ^DirectContext context))

    ;; Close all atlas images
    (when-let [atlas-image-xs (:atlas-image-xs assets)]
      (.close ^Image atlas-image-xs))

    (when-let [atlas-image-small (:atlas-image-small assets)]
      (.close ^Image atlas-image-small))

    (when-let [atlas-image-medium (:atlas-image-medium assets)]
      (.close ^Image atlas-image-medium))

    (when-let [planet-atlas-image-medium (:planet-atlas-image-medium assets)]
      (.close ^Image planet-atlas-image-medium))

    ;; Close all full-res star images
    (when-let [star-images (:star-images assets)]
      (doseq [star-data star-images]
        (when-let [^Image image (:image star-data)]
          (.close image))))

    ;; Destroy window
    (when window
      (GLFW/glfwDestroyWindow window))

    ;; Terminate GLFW
    (GLFW/glfwTerminate)))

(defn -main [& _args]
  (println "Starting Silent King...")

  ;; Start nREPL server for interactive development
  (let [nrepl-port 7888
        _nrepl-server (nrepl/start-server :port nrepl-port :handler cider-nrepl-handler)]
    (println (format "nREPL server started on port %d" nrepl-port))
    (spit ".nrepl-port" nrepl-port))

  ;; Reset global state atoms
  (reset! game-state (state/create-game-state))
  (reset! render-state (state/create-render-state))

  (try
    ;; Initialize GLFW
    (init-glfw)

    ;; Create window and context
    (let [window (create-window 1280 800 "Silent King - Star Gallery")
          context (create-skija-context)]
      (state/set-window! render-state window)
      (state/set-context! render-state context)

      ;; Setup input callbacks
      (setup-mouse-callbacks window game-state)

      ;; Load assets
      (let [loaded-assets (assets/load-all-assets)
            star-images (:star-images loaded-assets)
            planet-sprites (keys (:planet-atlas-metadata-medium loaded-assets))]
        (when (empty? planet-sprites)
          (throw (ex-info "Planet atlas metadata missing entries; cannot generate planets"
                          {:atlas "assets/planet-atlas-medium.json"})))
        (println "Loaded" (count star-images) "star images and" (count planet-sprites) "planet sprites")
        (state/set-assets! game-state loaded-assets)

        ;; Generate world data with noise-based clustering
        (galaxy/generate-galaxy! game-state star-images planet-sprites 2000)

        ;; Generate hyperlane connections using Delaunay triangulation
        (hyperlanes/generate-hyperlanes! game-state)

        ;; Generate regions using "Void Carver" on hyperlanes
        (regions/generate-regions! game-state)

        ;; Generate Voronoi cells overlay from the same star set
        (voronoi/generate-voronoi! game-state))

      ;; Run render loop
      (render-loop game-state render-state))

    (catch Exception e
      (println "Error:" (.getMessage e))
      (.printStackTrace e))

    (finally
      (cleanup game-state render-state)))

  (println "Goodbye!")
  (System/exit 0))
