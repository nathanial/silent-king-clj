(ns silent-king.core
  (:import [org.lwjgl.glfw GLFW GLFWErrorCallback]
           [org.lwjgl.opengl GL GL11 GL30]
           [org.lwjgl.system MemoryUtil]
           [io.github.humbleui.skija Canvas Color4f Paint Surface DirectContext BackendRenderTarget FramebufferFormat ColorSpace SurfaceOrigin SurfaceColorFormat Font Typeface Image Data]
           [io.github.humbleui.types Rect]
           [java.io File]))

(set! *warn-on-reflection* true)

(defn load-star-images []
  (println "Loading star images...")
  (let [star-files (sort (.listFiles (File. "assets/stars")))
        ;; Load a selection of stars (every 8th one to get ~6 stars)
        selected-files (take 6 (take-nth 8 star-files))]
    (vec (for [^File file selected-files]
           (let [data (Data/makeFromFileName (.getPath file))
                 image (Image/makeFromEncoded (.getBytes data))]
             (.close data)
             {:image image
              :path (.getName file)})))))

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

(defn draw-frame [^Canvas canvas width height time star-images]
  ;; Clear background
  (.clear canvas (unchecked-int 0xFF0a0a1e))

  (let [center-x (/ width 2)
        center-y (/ height 2)
        rotation (* time 30) ;; degrees per second
        orbit-radius 200]

    ;; Draw multiple rotating stars in a circular pattern
    (doseq [i (range (count star-images))]
      (let [star-data (nth star-images i)
            star-image (:image star-data)
            angle (* i (/ 360 (count star-images)))
            rad-angle (* angle (/ Math/PI 180))
            x (+ center-x (* orbit-radius (Math/cos rad-angle)))
            y (+ center-y (* orbit-radius (Math/sin rad-angle)))
            size 120]
        (draw-rotating-star canvas star-image x y size (+ rotation (* i 20)))))

    ;; Draw center rotating star
    (when (seq star-images)
      (let [center-star (:image (first star-images))]
        (draw-rotating-star canvas center-star center-x center-y 180 rotation)))

    ;; Draw title
    (let [paint (doto (Paint.)
                  (.setColor (unchecked-int 0xFFffffff)))
          font (Font. (Typeface/makeDefault) (float 32))]
      (.drawString canvas "Silent King - Star Animation" (float 20) (float 40) font paint)
      (.close font)
      (.close paint))))

(defn render-loop [window context surface-atom star-images]
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
            (draw-frame canvas fb-width fb-height current-time star-images)
            (.flush surface))

          ;; Swap buffers and poll events
          (GLFW/glfwSwapBuffers window)
          (GLFW/glfwPollEvents))

        (recur)))))

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
        star-images (atom [])]
    (try
      (init-glfw)
      (reset! window (create-window 1024 768 "Silent King - Star Animation"))
      (reset! context (create-skija-context))
      (reset! star-images (load-star-images))
      (println "Loaded" (count @star-images) "star images")
      (render-loop @window @context surface @star-images)
      (catch Exception e
        (println "Error:" (.getMessage e))
        (.printStackTrace e))
      (finally
        (cleanup @window @context surface @star-images))))
  (println "Goodbye!")
  (System/exit 0))
