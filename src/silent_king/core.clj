(ns silent-king.core
  (:import [org.lwjgl.glfw GLFW GLFWErrorCallback]
           [org.lwjgl.opengl GL GL11 GL30]
           [org.lwjgl.system MemoryUtil]
           [io.github.humbleui.skija Canvas Color4f Paint Surface DirectContext BackendRenderTarget FramebufferFormat ColorSpace SurfaceOrigin SurfaceColorFormat Font Typeface]
           [io.github.humbleui.types Rect]))

(set! *warn-on-reflection* true)

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

(defn draw-rotating-square [^Canvas canvas x y size angle color]
  (let [paint (doto (Paint.)
                (.setColor (unchecked-int color)))]
    (.save canvas)
    (.translate canvas x y)
    (.rotate canvas angle)
    (.drawRect canvas (Rect/makeXYWH (- (/ size 2)) (- (/ size 2)) size size) paint)
    (.restore canvas)
    (.close paint)))

(defn draw-frame [^Canvas canvas width height time]
  ;; Clear background
  (.clear canvas (unchecked-int 0xFF1a1a2e))

  (let [center-x (/ width 2)
        center-y (/ height 2)
        rotation (* time 50) ;; degrees per second
        orbit-radius 150]

    ;; Draw multiple rotating squares in a circular pattern
    (doseq [i (range 6)]
      (let [angle (* i (/ 360 6))
            rad-angle (* angle (/ Math/PI 180))
            x (+ center-x (* orbit-radius (Math/cos rad-angle)))
            y (+ center-y (* orbit-radius (Math/sin rad-angle)))
            size 80
            color (case i
                    0 0xFF00d4ff ;; cyan
                    1 0xFFff006e ;; pink
                    2 0xFF8338ec ;; purple
                    3 0xFFfb5607 ;; orange
                    4 0xFFffbe0b ;; yellow
                    5 0xFF3a86ff ;; blue
                    0xFFffffff)]
        (draw-rotating-square canvas x y size (+ rotation (* i 15)) color)))

    ;; Draw center rotating square
    (draw-rotating-square canvas center-x center-y 120 rotation 0xFFffffff)

    ;; Draw title
    (let [paint (doto (Paint.)
                  (.setColor (unchecked-int 0xFFffffff)))
          font (Font. (Typeface/makeDefault) (float 32))]
      (.drawString canvas "Silent King - Skija Animation" (float 20) (float 40) font paint)
      (.close font)
      (.close paint))))

(defn render-loop [window context surface-atom]
  (println "Starting render loop...")
  (let [start-time (System/nanoTime)]
    (loop []
      (when-not (GLFW/glfwWindowShouldClose window)
        (let [current-time (/ (- (System/nanoTime) start-time) 1e9)
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
            (draw-frame canvas fb-width fb-height current-time)
            (.flush surface))

          ;; Swap buffers and poll events
          (GLFW/glfwSwapBuffers window)
          (GLFW/glfwPollEvents))

        (recur)))))

(defn cleanup [window context surface-atom]
  (println "Cleaning up...")
  (when @surface-atom
    (.close ^Surface @surface-atom))
  (when context
    (.close ^DirectContext context))
  (when window
    (GLFW/glfwDestroyWindow window))
  (GLFW/glfwTerminate))

(defn -main [& args]
  (println "Starting Silent King...")
  (let [window (atom nil)
        context (atom nil)
        surface (atom nil)]
    (try
      (init-glfw)
      (reset! window (create-window 1024 768 "Silent King - Skija Animation"))
      (reset! context (create-skija-context))
      (render-loop @window @context surface)
      (catch Exception e
        (println "Error:" (.getMessage e))
        (.printStackTrace e))
      (finally
        (cleanup @window @context surface))))
  (println "Goodbye!")
  (System/exit 0))
