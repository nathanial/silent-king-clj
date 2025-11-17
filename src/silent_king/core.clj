(ns silent-king.core
  (:require [silent-king.assets :as assets]
            [silent-king.state :as state]
            [silent-king.galaxy :as galaxy]
            [silent-king.hyperlanes :as hyperlanes]
            [nrepl.server :as nrepl]
            [cider.nrepl :refer [cider-nrepl-handler]])
  (:import [org.lwjgl.glfw GLFW GLFWErrorCallback GLFWCursorPosCallbackI GLFWMouseButtonCallbackI GLFWScrollCallbackI GLFWKeyCallbackI]
           [org.lwjgl.opengl GL GL11 GL30]
           [org.lwjgl.system MemoryUtil]
           [io.github.humbleui.skija Canvas Color4f Paint Surface DirectContext BackendRenderTarget FramebufferFormat ColorSpace SurfaceOrigin SurfaceColorFormat Font Typeface Image Data]
           [io.github.humbleui.types Rect]
           [java.io File]))

(set! *warn-on-reflection* true)

;; =============================================================================
;; Global State
;; =============================================================================

(defonce game-state (atom (state/create-game-state)))
(defonce render-state (atom (state/create-render-state)))

;; =============================================================================
;; Non-Linear Transform Functions
;; =============================================================================

(def ^:const position-exponent 2.5)  ;; How fast positions spread apart
(def ^:const size-exponent 1.3)      ;; How fast star sizes grow

(defn zoom->position-scale
  "Convert zoom to position scale factor using a power function.
  This makes stars spread apart more dramatically when zooming in."
  [zoom]
  (Math/pow zoom position-exponent))

(defn zoom->size-scale
  "Convert zoom to size scale factor.
  Kept linear (exponent 1.0) so stars don't grow as fast as they spread."
  [zoom]
  (Math/pow zoom size-exponent))

(defn transform-position
  "Transform world position to screen position using non-linear scale"
  [world-pos zoom pan]
  (+ (* world-pos (zoom->position-scale zoom)) pan))

(defn transform-size
  "Transform base size to rendered size using size scale"
  [base-size zoom]
  (* base-size (zoom->size-scale zoom)))

(defn inverse-transform-position
  "Convert screen position back to world position (inverse of transform-position)"
  [screen-pos zoom pan]
  (/ (- screen-pos pan) (zoom->position-scale zoom)))

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

(defn create-window [width height title]
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
  (doto window
    ;; Mouse cursor position callback
    (GLFW/glfwSetCursorPosCallback
     (reify GLFWCursorPosCallbackI
       (invoke [_ win xpos ypos]
         ;; Convert mouse coordinates from window space to framebuffer space
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
               dragging (:dragging input)]
           (when dragging
             (let [dx (- fb-xpos old-x)
                   dy (- fb-ypos old-y)]
               (state/update-camera! game-state
                                     (fn [cam]
                                       (-> cam
                                           (update :pan-x #(+ % dx))
                                           (update :pan-y #(+ % dy)))))))
           (state/update-input! game-state assoc :mouse-x fb-xpos :mouse-y fb-ypos)))))

    ;; Mouse button callback
    (GLFW/glfwSetMouseButtonCallback
     (reify GLFWMouseButtonCallbackI
       (invoke [_ win button action mods]
         (when (= button GLFW/GLFW_MOUSE_BUTTON_LEFT)
           (state/update-input! game-state assoc :dragging (= action GLFW/GLFW_PRESS))))))

    ;; Scroll callback for zoom
    (GLFW/glfwSetScrollCallback
     (reify GLFWScrollCallbackI
       (invoke [_ win xoffset yoffset]
         (let [input (state/get-input game-state)
               camera (state/get-camera game-state)
               mouse-x (:mouse-x input)
               mouse-y (:mouse-y input)
               old-zoom (:zoom camera)
               zoom-factor (Math/pow 1.1 yoffset)
               new-zoom (max 0.4 (min 10.0 (* old-zoom zoom-factor)))

               ;; Calculate world position before zoom using non-linear transform
               old-pan-x (:pan-x camera)
               old-pan-y (:pan-y camera)
               world-x (inverse-transform-position mouse-x old-zoom old-pan-x)
               world-y (inverse-transform-position mouse-y old-zoom old-pan-y)

               ;; Calculate new pan to keep world position under cursor with non-linear transform
               new-pan-x (- mouse-x (* world-x (zoom->position-scale new-zoom)))
               new-pan-y (- mouse-y (* world-y (zoom->position-scale new-zoom)))]

           (state/update-camera! game-state assoc
                                 :zoom new-zoom
                                 :pan-x new-pan-x
                                 :pan-y new-pan-y)))))

    ;; Keyboard callback for simple feature toggles
    (GLFW/glfwSetKeyCallback
     (reify GLFWKeyCallbackI
       (invoke [_ win key scancode action mods]
         (when (and (= action GLFW/GLFW_PRESS)
                    (= key GLFW/GLFW_KEY_H))
           (state/toggle-hyperlanes! game-state)))))))

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

(defn draw-star-from-atlas
  "Draw a star from the texture atlas at screen coordinates (x, y)"
  [^Canvas canvas ^Image atlas-image atlas-metadata path screen-x screen-y size angle atlas-size]
  (when-let [coords (get atlas-metadata path)]
    (.save canvas)
    (.translate canvas screen-x screen-y)
    (.rotate canvas angle)
    (let [tile-size (:size coords)
          scale (/ size tile-size)
          scaled-size (* tile-size scale)]
      (.drawImageRect canvas atlas-image
                      (Rect/makeXYWH (:x coords) (:y coords) tile-size tile-size)
                      (Rect/makeXYWH (- (/ scaled-size 2))
                                     (- (/ scaled-size 2))
                                     scaled-size
                                     scaled-size)))
    (.restore canvas)))

(defn draw-full-res-star
  "Draw a star from a full-resolution image at screen coordinates (x, y)"
  [^Canvas canvas star-images path screen-x screen-y size angle]
  (when-let [star-data (first (filter #(= (:path %) path) star-images))]
    (let [^Image image (:image star-data)
          img-width (.getWidth image)
          img-height (.getHeight image)
          scale (/ size (max img-width img-height))
          scaled-width (* img-width scale)
          scaled-height (* img-height scale)]
      (.save canvas)
      (.translate canvas screen-x screen-y)
      (.rotate canvas angle)
      (.drawImageRect canvas image
                      (Rect/makeXYWH 0 0 img-width img-height)
                      (Rect/makeXYWH (- (/ scaled-width 2))
                                     (- (/ scaled-height 2))
                                     scaled-width
                                     scaled-height))
      (.restore canvas))))

(defn star-visible?
  "Check if a star is visible in the current viewport using non-linear transform"
  [star-world-x star-world-y star-base-size pan-x pan-y viewport-width viewport-height zoom]
  (let [;; Transform world position to screen position
        screen-x (transform-position star-world-x zoom pan-x)
        screen-y (transform-position star-world-y zoom pan-y)
        ;; Transform size
        screen-size (transform-size star-base-size zoom)
        ;; Extra margin to avoid pop-in
        margin (* screen-size 2)]
    (and (> (+ screen-x margin) 0)
         (< (- screen-x margin) viewport-width)
         (> (+ screen-y margin) 0)
         (< (- screen-y margin) viewport-height))))

(defn draw-frame [^Canvas canvas width height time game-state]
  ;; Clear background
  (.clear canvas (unchecked-int 0xFF000000))

  (let [camera (state/get-camera game-state)
        assets (state/get-assets game-state)
        zoom (:zoom camera)
        pan-x (:pan-x camera)
        pan-y (:pan-y camera)
        hyperlanes-enabled (state/hyperlanes-enabled? game-state)

        ;; 4-Level LOD system
        lod-level (cond
                    (< zoom 0.5) :xs       ;; Far zoom: use xs atlas (64x64)
                    (< zoom 1.5) :small    ;; Medium zoom: use small atlas (128x128)
                    (< zoom 4.0) :medium   ;; Close zoom: use medium atlas (256x256)
                    :else :full)           ;; Very close zoom: use full-resolution images

        ;; Select atlas based on LOD level (or nil for full-res)
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

        ;; Get star images for full-res rendering
        star-images (:star-images assets)

        ;; Get all star entities (those with position, renderable, and transform components)
        star-entities (state/filter-entities-with game-state [:position :renderable :transform :physics])

        ;; Frustum culling: filter visible stars using non-linear transform
        visible-stars (filter (fn [[_ entity]]
                                (let [pos (state/get-component entity :position)
                                      transform (state/get-component entity :transform)]
                                  (star-visible? (:x pos) (:y pos) (:size transform)
                                               pan-x pan-y width height zoom)))
                             star-entities)
        visible-count (count visible-stars)
        total-count (count star-entities)]

    ;; Note: No canvas transform needed - we calculate screen positions per-star with non-linear scaling

    ;; Draw hyperlanes BEFORE stars (so they appear as background connections)
    (if hyperlanes-enabled
      (let [hyperlanes-rendered (hyperlanes/draw-all-hyperlanes canvas width height zoom pan-x pan-y game-state time)]
        ;; Store hyperlane count for UI display
        (swap! game-state assoc-in [:debug :hyperlanes-rendered] hyperlanes-rendered))
      (swap! game-state assoc-in [:debug :hyperlanes-rendered] 0))

    ;; Draw visible stars using 4-level LOD
    (doseq [[_ entity] visible-stars]
      (let [pos (state/get-component entity :position)
            renderable (state/get-component entity :renderable)
            transform (state/get-component entity :transform)
            physics (state/get-component entity :physics)
            path (:path renderable)
            world-x (:x pos)
            world-y (:y pos)
            base-size (:size transform)
            ;; Transform to screen coordinates with non-linear scaling
            screen-x (transform-position world-x zoom pan-x)
            screen-y (transform-position world-y zoom pan-y)
            screen-size (transform-size base-size zoom)
            rotation-speed (:rotation-speed physics)
            rotation (* time 30 rotation-speed)]
        (if (= lod-level :full)
          ;; Draw from full-resolution image
          (draw-full-res-star canvas star-images path screen-x screen-y screen-size rotation)
          ;; Draw from texture atlas
          (draw-star-from-atlas canvas atlas-image atlas-metadata path screen-x screen-y screen-size rotation atlas-size))))

    ;; Draw UI overlay (not affected by camera)
    (let [paint (doto (Paint.)
                  (.setColor (unchecked-int 0xFFffffff)))
          font (Font. (Typeface/makeDefault) (float 24))
          lod-description (case lod-level
                            :xs "XS atlas (64x64, far zoom)"
                            :small "Small atlas (128x128, medium zoom)"
                            :medium "Medium atlas (256x256, close zoom)"
                            :full "Full resolution images (highest quality)")
          hyperlanes-count (get-in @game-state [:debug :hyperlanes-rendered] 0)
          hyperlane-text (if hyperlanes-enabled
                           (str "Hyperlanes: " hyperlanes-count " rendered (press H to toggle)")
                           "Hyperlanes: OFF (press H to toggle)")]
      (.drawString canvas "Silent King - Star Gallery" (float 20) (float 30) font paint)
      (.drawString canvas (str "Stars: " total-count " (visible: " visible-count ")") (float 20) (float 60) font paint)
      (.drawString canvas hyperlane-text (float 20) (float 90) font paint)
      (.drawString canvas (str "Zoom: " (format "%.2f" zoom) "x") (float 20) (float 120) font paint)
      (.drawString canvas (str "LOD: " lod-description) (float 20) (float 150) font paint)
      (.drawString canvas "Controls: Click-Drag=Pan, Scroll=Zoom, H=Toggle Hyperlanes" (float 20) (float 180) font paint)
      (.close font)
      (.close paint))))

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

(defn -main [& args]
  (println "Starting Silent King...")

  ;; Start nREPL server for interactive development
  (let [nrepl-port 7888
        nrepl-server (nrepl/start-server :port nrepl-port :handler cider-nrepl-handler)]
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
            star-images (:star-images loaded-assets)]
        (println "Loaded" (count star-images) "star images")
        (state/set-assets! game-state loaded-assets)

        ;; Generate star entities with noise-based clustering
        (galaxy/generate-galaxy-entities! game-state star-images 10000)

        ;; Generate hyperlane connections using Delaunay triangulation
        (hyperlanes/generate-delaunay-hyperlanes! game-state))

      ;; Run render loop
      (render-loop game-state render-state))

    (catch Exception e
      (println "Error:" (.getMessage e))
      (.printStackTrace e))

    (finally
      (cleanup game-state render-state)))

  (println "Goodbye!")
  (System/exit 0))
