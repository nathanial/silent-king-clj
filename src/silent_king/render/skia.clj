(ns silent-king.render.skia
  "Interpret draw command data and apply it to a Skija Canvas."
  (:import [io.github.humbleui.skija Canvas FilterTileMode Font Image Paint PaintMode PaintStrokeCap Path Shader Typeface]
           [io.github.humbleui.types Rect]))

(set! *warn-on-reflection* true)

(defonce ^Typeface default-typeface
  (Typeface/makeDefault))

(defn- ->int-color
  "Convert long ARGB to signed int to avoid overflow in Skija."
  [value default]
  (let [raw (long (or value default 0))
        signed (if (> raw 0x7FFFFFFF)
                 (- raw 0x100000000)
                 raw)]
    (int signed)))

(defn- ->float
  [value]
  (float (double (or value 0.0))))

(defn- ^Rect rect->Rect
  [{:keys [x y width height]}]
  (Rect/makeXYWH (->float x) (->float y) (->float width) (->float height)))

(defn- stroke-cap
  [cap]
  (case cap
    :round PaintStrokeCap/ROUND
    :square PaintStrokeCap/SQUARE
    PaintStrokeCap/BUTT))

(defn- ^Paint with-fill
  [color]
  (doto ^Paint (Paint.)
    (.setColor (->int-color color color))))

(defn- ^Paint with-stroke
  [color width cap]
  (doto ^Paint (Paint.)
    (.setColor (->int-color color color))
    (.setMode PaintMode/STROKE)
    (.setStrokeWidth (->float (or width 1.0)))
    (.setStrokeCap (stroke-cap cap))))

(defn- draw-rect!
  [^Canvas canvas {:keys [rect style]}]
  (let [{:keys [fill-color stroke-color stroke-width stroke-cap]} style
        rect* (rect->Rect rect)]
    (when fill-color
      (with-open [paint (with-fill fill-color)]
        (.drawRect canvas rect* paint)))
    (when stroke-color
      (with-open [paint (with-stroke stroke-color stroke-width stroke-cap)]
        (.drawRect canvas rect* paint)))))

(defn- draw-circle!
  [^Canvas canvas {:keys [center radius style]}]
  (let [{:keys [fill-color stroke-color stroke-width stroke-cap]} style
        cx (->float (:x center))
        cy (->float (:y center))
        r (->float radius)]
    (when fill-color
      (with-open [paint (with-fill fill-color)]
        (.drawCircle canvas cx cy r paint)))
    (when stroke-color
      (with-open [paint (with-stroke stroke-color stroke-width stroke-cap)]
        (.drawCircle canvas cx cy r paint)))))

(defn- gradient-shader
  [{:keys [from to start end]}]
  (when (and from to start end)
    (let [^floats positions (float-array [0.0 1.0])
          ^ints colors (int-array [(->int-color start start)
                                   (->int-color end end)])]
      (Shader/makeLinearGradient (->float (:x from))
                                 (->float (:y from))
                                 (->float (:x to))
                                 (->float (:y to))
                                 colors
                                 positions
                                 FilterTileMode/CLAMP))))

(defn- draw-line!
  [^Canvas canvas {:keys [from to style]}]
  (let [{:keys [stroke-color stroke-width stroke-cap gradient glow]} style
        fx (->float (:x from))
        fy (->float (:y from))
        tx (->float (:x to))
        ty (->float (:y to))]
    (when (and glow (:color glow))
      (with-open [paint (with-stroke (:color glow)
                                   (* (double (or (:multiplier glow) 1.0))
                                      (double (or stroke-width 1.0)))
                                   stroke-cap)]
        (.drawLine canvas fx fy tx ty paint)))
    (when (or stroke-color gradient)
      (with-open [paint (with-stroke (or stroke-color (:start gradient) 0xFFFFFFFF)
                                   stroke-width
                                   stroke-cap)]
        (when gradient
          (when-let [shader (gradient-shader {:from from
                                              :to to
                                              :start (:start gradient)
                                              :end (:end gradient)})]
            (.setShader paint shader)))
        (.drawLine canvas fx fy tx ty paint)))))

(defn- ^Path path-from-points
  [points]
  (let [path (Path.)]
    (when-let [first-point (first points)]
      (.moveTo path (->float (:x first-point)) (->float (:y first-point)))
      (doseq [pt (rest points)]
        (.lineTo path (->float (:x pt)) (->float (:y pt))))
      (.closePath path))
    path))

(defn- draw-polygon!
  [^Canvas canvas {:keys [points style]} mode]
  (let [{:keys [fill-color stroke-color stroke-width stroke-cap]} style]
    (with-open [path (path-from-points points)]
      (when (and (= mode :fill) fill-color)
        (with-open [paint (with-fill fill-color)]
          (.drawPath canvas path paint)))
      (when (and (= mode :stroke) stroke-color)
        (with-open [paint (with-stroke stroke-color stroke-width stroke-cap)]
          (.drawPath canvas path paint))))))

(defn- draw-text!
  [^Canvas canvas {:keys [text position font color]}]
  (let [size (double (or (:size font) 16.0))]
    (with-open [^Font font* (Font. default-typeface (float size))
                paint (with-fill color)]
      (.drawString canvas (or text "")
                   (->float (:x position))
                   (->float (:y position))
                   font*
                   paint))))

(defn- apply-transform!
  [^Canvas canvas {:keys [rotation-deg anchor]} rect]
  (when rotation-deg
    (let [{:keys [x y width height]} rect
          anchor-point (case anchor
                         :top-left {:x x :y y}
                         {:x (+ (double (or x 0.0)) (/ (double (or width 0.0)) 2.0))
                          :y (+ (double (or y 0.0)) (/ (double (or height 0.0)) 2.0))})]
      (.translate canvas (->float (:x anchor-point)) (->float (:y anchor-point)))
      (.rotate canvas (->float rotation-deg))
      (.translate canvas (->float (- (:x anchor-point))) (->float (- (:y anchor-point)))))))

(defn- draw-image-rect!
  [^Canvas canvas {:keys [^Image image src dst transform]}]
  (when (and image src dst)
    (let [^Rect src-rect (rect->Rect src)
          ^Rect dst-rect (rect->Rect dst)]
      (if transform
        (do
          (.save canvas)
          (apply-transform! canvas transform dst)
          (.drawImageRect canvas image src-rect dst-rect)
          (.restore canvas))
        (.drawImageRect canvas image src-rect dst-rect)))))

(defn execute-command!
  [^Canvas canvas command]
  (case (:op command)
    :clear (.clear canvas (->int-color (:color command) 0xFF000000))
    :save (.save canvas)
    :restore (.restore canvas)
    :translate (.translate canvas (->float (:dx command)) (->float (:dy command)))
    :scale (.scale canvas (->float (:sx command)) (->float (:sy command)))
    :rotate (.rotate canvas (->float (:angle-deg command)))
    :clip-rect (.clipRect canvas (rect->Rect (:rect command)))
    :rect (draw-rect! canvas command)
    :circle (draw-circle! canvas command)
    :line (draw-line! canvas command)
    :polygon-fill (draw-polygon! canvas command :fill)
    :polygon-stroke (draw-polygon! canvas command :stroke)
    :text (draw-text! canvas command)
    :image-rect (draw-image-rect! canvas command)
    nil))

(defn draw-commands!
  "Apply a sequence of draw commands to the provided Canvas."
  [^Canvas canvas commands & [_opts]]
  (doseq [cmd commands]
    (when cmd
      (execute-command! canvas cmd)))
  canvas)
