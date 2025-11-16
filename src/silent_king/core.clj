(ns silent-king.core
  (:require [silent-king.assets :as assets])
  (:import [org.lwjgl.glfw GLFW GLFWErrorCallback GLFWCursorPosCallbackI GLFWMouseButtonCallbackI GLFWScrollCallbackI]
           [org.lwjgl.opengl GL GL11 GL30]
           [org.lwjgl.system MemoryUtil]
           [io.github.humbleui.skija Canvas Color4f Paint Surface DirectContext BackendRenderTarget FramebufferFormat ColorSpace SurfaceOrigin SurfaceColorFormat Font Typeface Image Data]
           [io.github.humbleui.types Rect]
           [java.io File]))

(set! *warn-on-reflection* true)

(defn generate-star-instances [base-images num-stars]
  (println "Generating" num-stars "star instances...")
  (let [canvas-width 4000
        canvas-height 4000]
    (vec (for [i (range num-stars)]
           (let [base-image (rand-nth base-images)]
             {:image (:image base-image)
              :x (rand canvas-width)
              :y (rand canvas-height)
              :size (+ 80 (rand 80))  ;; Random size between 80-160
              :rotation-speed (+ 0.5 (rand 1.5))})))))

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

(defn setup-mouse-callbacks [window mouse-state camera]
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
              old-x (:mouse-x @mouse-state)
              old-y (:mouse-y @mouse-state)
              dragging (:dragging @mouse-state)]
          (when dragging
            (let [dx (- fb-xpos old-x)
                  dy (- fb-ypos old-y)]
              (swap! camera update :pan-x #(+ % dx))
              (swap! camera update :pan-y #(+ % dy))))
          (swap! mouse-state assoc :mouse-x fb-xpos :mouse-y fb-ypos)))))

  ;; Mouse button callback
  (GLFW/glfwSetMouseButtonCallback window
    (reify GLFWMouseButtonCallbackI
      (invoke [this win button action mods]
        (when (= button GLFW/GLFW_MOUSE_BUTTON_LEFT)
          (swap! mouse-state assoc :dragging (= action GLFW/GLFW_PRESS))))))

  ;; Scroll callback for zoom
  (GLFW/glfwSetScrollCallback window
    (reify GLFWScrollCallbackI
      (invoke [this win xoffset yoffset]
        (let [mouse-x (:mouse-x @mouse-state)
              mouse-y (:mouse-y @mouse-state)
              old-zoom (:zoom @camera)
              zoom-factor (Math/pow 1.1 yoffset)
              new-zoom (max 0.1 (min 10.0 (* old-zoom zoom-factor)))

              ;; Calculate world position before zoom
              old-pan-x (:pan-x @camera)
              old-pan-y (:pan-y @camera)
              world-x (/ (- mouse-x old-pan-x) old-zoom)
              world-y (/ (- mouse-y old-pan-y) old-zoom)

              ;; Calculate new pan to keep world position under cursor
              new-pan-x (- mouse-x (* world-x new-zoom))
              new-pan-y (- mouse-y (* world-y new-zoom))]

          (swap! camera assoc
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

(defn draw-frame [^Canvas canvas width height time star-images camera]
  ;; Clear background
  (.clear canvas (unchecked-int 0xFF000000))

  (let [zoom (:zoom @camera)
        pan-x (:pan-x @camera)
        pan-y (:pan-y @camera)]

    ;; Save canvas state and apply camera transform
    (.save canvas)
    (.translate canvas (float pan-x) (float pan-y))
    (.scale canvas (float zoom) (float zoom))

    ;; Draw all stars at their random positions
    (doseq [star-data star-images]
      (let [star-image (:image star-data)
            x (:x star-data)
            y (:y star-data)
            size (:size star-data)
            rotation-speed (:rotation-speed star-data)
            rotation (* time 30 rotation-speed)]
        (draw-rotating-star canvas star-image x y size rotation)))

    ;; Restore canvas state
    (.restore canvas)

    ;; Draw UI overlay (not affected by camera)
    (let [paint (doto (Paint.)
                  (.setColor (unchecked-int 0xFFffffff)))
          font (Font. (Typeface/makeDefault) (float 24))]
      (.drawString canvas "Silent King - Star Gallery" (float 20) (float 30) font paint)
      (.drawString canvas (str "Stars: " (count star-images)) (float 20) (float 60) font paint)
      (.drawString canvas (str "Zoom: " (format "%.2f" zoom) "x") (float 20) (float 90) font paint)
      (.drawString canvas "Controls: Click-Drag=Pan, Scroll=Zoom" (float 20) (float 120) font paint)
      (.close font)
      (.close paint))))

(defn render-loop [window context surface-atom star-images camera]
  (println "Starting render loop...")
  (let [start-time (System/nanoTime)]
    (loop []
      (if (GLFW/glfwWindowShouldClose window)
        nil
        (let [current-time-ns (System/nanoTime)
              current-time (/ (- current-time-ns start-time) 1e9)
              fb-width-arr (int-array 1)
              fb-height-arr (int-array 1)
              _ (GLFW/glfwGetFramebufferSize window fb-width-arr fb-height-arr)
              fb-width (aget fb-width-arr 0)
              fb-height (aget fb-height-arr 0)]

          ;; Update surface if needed
          (when (or (nil? @surface-atom)
                    (not= fb-width (.getWidth ^Surface @surface-atom))
                    (not= fb-height (.getHeight ^Surface @surface-atom)))
            (when @surface-atom
              (.close ^Surface @surface-atom))
            (reset! surface-atom (create-skija-surface context fb-width fb-height)))

          ;; Set viewport
          (GL11/glViewport 0 0 fb-width fb-height)

          ;; Render with Skija
          (let [^Surface surface @surface-atom
                ^Canvas canvas (.getCanvas surface)]
            (draw-frame canvas fb-width fb-height current-time star-images camera)
            (.flush surface))

          ;; Swap buffers and poll events
          (GLFW/glfwSwapBuffers window)
          (GLFW/glfwPollEvents)

          (recur))))))

(defn cleanup [window context surface-atom star-images]
  (println "Cleaning up...")
  (when @surface-atom
    (.close ^Surface @surface-atom))
  (when context
    (.close ^DirectContext context))
  (doseq [star-data star-images]
    (.close ^Image (:image star-data)))
  (when window
    (GLFW/glfwDestroyWindow window))
  (GLFW/glfwTerminate))

(defn -main [& args]
  (println "Starting Silent King...")
  (let [window (atom nil)
        context (atom nil)
        surface (atom nil)
        star-images (atom [])
        camera (atom {:zoom 1.0 :pan-x 0.0 :pan-y 0.0})
        mouse-state (atom {:mouse-x 0.0 :mouse-y 0.0 :dragging false})]
    (try
      (init-glfw)
      (reset! window (create-window 1280 800 "Silent King - Star Gallery"))
      (setup-mouse-callbacks @window mouse-state camera)
      (reset! context (create-skija-context))
      (let [base-images (assets/load-star-images)]
        (println "Loaded" (count base-images) "base star images")
        (reset! star-images (generate-star-instances base-images 1000)))
      (println "Generated" (count @star-images) "star instances")
      (render-loop @window @context surface @star-images camera)
      (catch Exception e
        (println "Error:" (.getMessage e))
        (.printStackTrace e))
      (finally
        (cleanup @window @context surface @star-images))))
  (println "Goodbye!")
  (System/exit 0))
