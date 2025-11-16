(ns silent-king.core
  (:require [silent-king.assets :as assets]
            [silent-king.state :as state])
  (:import [org.lwjgl.glfw GLFW GLFWErrorCallback GLFWCursorPosCallbackI GLFWMouseButtonCallbackI GLFWScrollCallbackI]
           [org.lwjgl.opengl GL GL11 GL30]
           [org.lwjgl.system MemoryUtil]
           [io.github.humbleui.skija Canvas Color4f Paint Surface DirectContext BackendRenderTarget FramebufferFormat ColorSpace SurfaceOrigin SurfaceColorFormat Font Typeface Image Data]
           [io.github.humbleui.types Rect]
           [java.io File]))

(set! *warn-on-reflection* true)

(defn generate-star-entities! [game-state base-images num-stars]
  "Generate star entities with components and add them to game state"
  (println "Generating" num-stars "star entities...")
  (let [canvas-width 4000
        canvas-height 4000]
    (doseq [i (range num-stars)]
      (let [base-image (rand-nth base-images)
            entity (state/create-entity
                    :position {:x (rand canvas-width)
                              :y (rand canvas-height)}
                    :renderable {:image (:image base-image)
                                :path (:path base-image)}
                    :transform {:size (+ 80 (rand 80))
                               :rotation 0.0}
                    :physics {:rotation-speed (+ 0.5 (rand 1.5))})]
        (state/add-entity! game-state entity)))))

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
  ;; Mouse cursor position callback
  (GLFW/glfwSetCursorPosCallback window
    (reify GLFWCursorPosCallbackI
      (invoke [this win xpos ypos]
        ;; Convert mouse coordinates from window space to framebuffer space
        (let [win-width-arr (int-array 1)
              win-height-arr (int-array 1)
              fb-width-arr (int-array 1)
              fb-height-arr (int-array 1)
              _ (GLFW/glfwGetWindowSize window win-width-arr win-height-arr)
              _ (GLFW/glfwGetFramebufferSize window fb-width-arr fb-height-arr)
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
  (GLFW/glfwSetMouseButtonCallback window
    (reify GLFWMouseButtonCallbackI
      (invoke [this win button action mods]
        (when (= button GLFW/GLFW_MOUSE_BUTTON_LEFT)
          (state/update-input! game-state assoc :dragging (= action GLFW/GLFW_PRESS))))))

  ;; Scroll callback for zoom
  (GLFW/glfwSetScrollCallback window
    (reify GLFWScrollCallbackI
      (invoke [this win xoffset yoffset]
        (let [input (state/get-input game-state)
              camera (state/get-camera game-state)
              mouse-x (:mouse-x input)
              mouse-y (:mouse-y input)
              old-zoom (:zoom camera)
              zoom-factor (Math/pow 1.1 yoffset)
              new-zoom (max 0.1 (min 10.0 (* old-zoom zoom-factor)))

              ;; Calculate world position before zoom
              old-pan-x (:pan-x camera)
              old-pan-y (:pan-y camera)
              world-x (/ (- mouse-x old-pan-x) old-zoom)
              world-y (/ (- mouse-y old-pan-y) old-zoom)

              ;; Calculate new pan to keep world position under cursor
              new-pan-x (- mouse-x (* world-x new-zoom))
              new-pan-y (- mouse-y (* world-y new-zoom))]

          (state/update-camera! game-state assoc
                               :zoom new-zoom
                               :pan-x new-pan-x
                               :pan-y new-pan-y))))))

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

(defn draw-rotating-star [^Canvas canvas ^Image image x y size angle]
  (.save canvas)
  (.translate canvas x y)
  (.rotate canvas angle)
  (let [img-width (.getWidth image)
        img-height (.getHeight image)
        scale (/ size (max img-width img-height))
        scaled-width (* img-width scale)
        scaled-height (* img-height scale)]
    (.drawImageRect canvas image
                    (Rect/makeXYWH 0 0 img-width img-height)
                    (Rect/makeXYWH (- (/ scaled-width 2))
                                   (- (/ scaled-height 2))
                                   scaled-width
                                   scaled-height)))
  (.restore canvas))

(defn draw-star-from-atlas [^Canvas canvas ^Image atlas-image atlas-metadata path x y size angle atlas-size]
  "Draw a star from the texture atlas"
  (when-let [coords (get atlas-metadata path)]
    (.save canvas)
    (.translate canvas x y)
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

(defn star-visible? [star-x star-y star-size viewport-x viewport-y viewport-width viewport-height zoom]
  "Check if a star is visible in the current viewport (with margin for safety)"
  (let [margin (* star-size 2)  ;; Extra margin to avoid pop-in
        world-min-x (/ (- viewport-x) zoom)
        world-max-x (/ (+ (- viewport-x) viewport-width) zoom)
        world-min-y (/ (- viewport-y) zoom)
        world-max-y (/ (+ (- viewport-y) viewport-height) zoom)]
    (and (> (+ star-x margin) world-min-x)
         (< (- star-x margin) world-max-x)
         (> (+ star-y margin) world-min-y)
         (< (- star-y margin) world-max-y))))

(defn draw-frame [^Canvas canvas width height time game-state]
  ;; Clear background
  (.clear canvas (unchecked-int 0xFF000000))

  (let [camera (state/get-camera game-state)
        assets (state/get-assets game-state)
        zoom (:zoom camera)
        pan-x (:pan-x camera)
        pan-y (:pan-y camera)

        ;; LOD: Use atlas when zoomed out for performance
        use-atlas? (< zoom 2.5)
        atlas-image (:atlas-image assets)
        atlas-metadata (:atlas-metadata assets)
        atlas-size (:atlas-size assets)

        ;; Get all star entities (those with position, renderable, and transform components)
        star-entities (state/filter-entities-with game-state [:position :renderable :transform :physics])

        ;; Frustum culling: filter visible stars
        visible-stars (filter (fn [[_ entity]]
                                (let [pos (state/get-component entity :position)
                                      transform (state/get-component entity :transform)]
                                  (star-visible? (:x pos) (:y pos) (:size transform)
                                               pan-x pan-y width height zoom)))
                             star-entities)
        visible-count (count visible-stars)
        total-count (count star-entities)]

    ;; Save canvas state and apply camera transform
    (.save canvas)
    (.translate canvas (float pan-x) (float pan-y))
    (.scale canvas (float zoom) (float zoom))

    ;; Draw visible stars using LOD
    (if use-atlas?
      ;; Low-detail mode: Use atlas for better batching
      (doseq [[_ entity] visible-stars]
        (let [pos (state/get-component entity :position)
              renderable (state/get-component entity :renderable)
              transform (state/get-component entity :transform)
              physics (state/get-component entity :physics)
              path (:path renderable)
              x (:x pos)
              y (:y pos)
              size (:size transform)
              rotation-speed (:rotation-speed physics)
              rotation (* time 30 rotation-speed)]
          (draw-star-from-atlas canvas atlas-image atlas-metadata path x y size rotation atlas-size)))

      ;; High-detail mode: Use individual full-res images
      (doseq [[_ entity] visible-stars]
        (let [pos (state/get-component entity :position)
              renderable (state/get-component entity :renderable)
              transform (state/get-component entity :transform)
              physics (state/get-component entity :physics)
              star-image (:image renderable)
              x (:x pos)
              y (:y pos)
              size (:size transform)
              rotation-speed (:rotation-speed physics)
              rotation (* time 30 rotation-speed)]
          (draw-rotating-star canvas star-image x y size rotation))))

    ;; Restore canvas state
    (.restore canvas)

    ;; Draw UI overlay (not affected by camera)
    (let [paint (doto (Paint.)
                  (.setColor (unchecked-int 0xFFffffff)))
          font (Font. (Typeface/makeDefault) (float 24))]
      (.drawString canvas "Silent King - Star Gallery" (float 20) (float 30) font paint)
      (.drawString canvas (str "Stars: " total-count " (visible: " visible-count ")") (float 20) (float 60) font paint)
      (.drawString canvas (str "Zoom: " (format "%.2f" zoom) "x") (float 20) (float 90) font paint)
      (.drawString canvas (str "LOD: " (if use-atlas? "Atlas (low-detail)" "Individual (high-detail)")) (float 20) (float 120) font paint)
      (.drawString canvas "Controls: Click-Drag=Pan, Scroll=Zoom" (float 20) (float 150) font paint)
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
        assets (state/get-assets game-state)
        entities (state/get-all-entities game-state)]

    ;; Close surface
    (when surface
      (.close ^Surface surface))

    ;; Close DirectContext
    (when context
      (.close ^DirectContext context))

    ;; Close all star images from entities
    (doseq [[_ entity] entities]
      (when-let [renderable (state/get-component entity :renderable)]
        (when-let [image (:image renderable)]
          (.close ^Image image))))

    ;; Close atlas image
    (when-let [atlas-image (:atlas-image assets)]
      (.close ^Image atlas-image))

    ;; Destroy window
    (when window
      (GLFW/glfwDestroyWindow window))

    ;; Terminate GLFW
    (GLFW/glfwTerminate)))

(defn -main [& args]
  (println "Starting Silent King...")

  ;; Initialize state
  (let [game-state (atom (state/create-game-state))
        render-state (atom (state/create-render-state))]
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
              base-images (:individual-images loaded-assets)]
          (println "Loaded" (count base-images) "base star images")
          (state/set-assets! game-state loaded-assets)

          ;; Generate star entities
          (generate-star-entities! game-state base-images 1000)
          (println "Generated" (count (state/get-all-entities game-state)) "star entities"))

        ;; Run render loop
        (render-loop game-state render-state))

      (catch Exception e
        (println "Error:" (.getMessage e))
        (.printStackTrace e))

      (finally
        (cleanup game-state render-state))))

  (println "Goodbye!")
  (System/exit 0))
