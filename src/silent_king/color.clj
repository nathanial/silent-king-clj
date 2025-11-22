(ns silent-king.color
  "Color structures and manipulation helpers.
   All colors are represented as maps with a :type key.
   Supported types: :rgb, :hsl, :hsv."
  (:refer-clojure :exclude [byte ensure]))

(set! *warn-on-reflection* true)

(defn- clamp01
  ^double [val]
  (-> val double (max 0.0) (min 1.0)))

(defn- clamp255
  ^long [val]
  (-> val long (max 0) (min 255)))

;; -- Constructors --

(defn rgb
  "Create an RGB color.
   r, g, b: 0-255
   a: 0-255 (default 255)"
  ([r g b]
   (rgb r g b 255))
  ([r g b a]
   {:type :rgb
    :r (clamp255 r)
    :g (clamp255 g)
    :b (clamp255 b)
    :a (clamp255 a)}))

(defn rgba
  "Alias for rgb with explicit alpha."
  [r g b a]
  (rgb r g b a))

(defn hsl
  "Create an HSL color.
   h: 0-360 (degrees)
   s: 0-100 (percent)
   l: 0-100 (percent)
   a: 0-1.0 (opacity, default 1.0)"
  ([h s l]
   (hsl h s l 1.0))
  ([h s l a]
   {:type :hsl
    :h (mod (double h) 360.0)
    :s (clamp01 (/ (double s) 100.0))
    :l (clamp01 (/ (double l) 100.0))
    :a (clamp01 (double a))}))

(defn hsv
  "Create an HSV color.
   h: 0-360
   s: 0-100
   v: 0-100
   a: 0-1.0"
  ([h s v]
   (hsv h s v 1.0))
  ([h s v a]
   {:type :hsv
    :h (mod (double h) 360.0)
    :s (clamp01 (/ (double s) 100.0))
    :v (clamp01 (/ (double v) 100.0))
    :a (clamp01 (double a))}))

(defn hex
  "Parse a hex string or integer into an RGB color.
   Formats: \"#RRGGBB\", \"#AARRGGBB\", \"RRGGBB\", or integer 0xAARRGGBB / 0xRRGGBB."
  [v]
  (cond
    (string? v)
    (let [clean (if (.startsWith ^String v "#") (subs v 1) v)
          len (count clean)]
      (case len
        6 (let [r (Integer/parseInt (subs clean 0 2) 16)
                g (Integer/parseInt (subs clean 2 4) 16)
                b (Integer/parseInt (subs clean 4 6) 16)]
            (rgb r g b))
        8 (let [a (Integer/parseInt (subs clean 0 2) 16)
                r (Integer/parseInt (subs clean 2 4) 16)
                g (Integer/parseInt (subs clean 4 6) 16)
                b (Integer/parseInt (subs clean 6 8) 16)]
            (rgb r g b a))
        (throw (ex-info "Invalid hex string length" {:value v}))))

    (integer? v)
    (let [a (bit-and (bit-shift-right v 24) 0xFF)
          r (bit-and (bit-shift-right v 16) 0xFF)
          g (bit-and (bit-shift-right v 8) 0xFF)
          b (bit-and v 0xFF)]
      ;; If alpha is 0, assume it was omitted in 0xRRGGBB format unless explicitly intended.
      ;; However, standard 0xRRGGBB is actually 0x00RRGGBB which is transparent.
      ;; Usually people mean 0xFFRRGGBB.
      ;; For safety in this refactor, if v <= 0xFFFFFF, we assume full opacity.
      (if (<= v 0xFFFFFF)
        (rgb r g b 255)
        (rgb r g b a)))

    :else
    (throw (ex-info "Invalid hex value type" {:value v :type (type v)}))))

;; -- Conversions --

(defmulti to-rgb :type)

(defmethod to-rgb :rgb [c] c)

(defn- hue->rgb [p q t]
  (let [t (cond
            (< t 0.0) (+ t 1.0)
            (> t 1.0) (- t 1.0)
            :else t)]
    (cond
      (< t (/ 1.0 6.0)) (+ p (* (- q p) 6.0 t))
      (< t (/ 1.0 2.0)) q
      (< t (/ 2.0 3.0)) (+ p (* (- q p) (- (/ 2.0 3.0) t) 6.0))
      :else p)))

(defmethod to-rgb :hsl
  [{:keys [h s l a]}]
  (let [h (/ h 360.0)]
    (if (zero? s)
      (let [grey (long (* l 255.0))]
        (rgb grey grey grey (long (* a 255.0))))
      (let [q (if (< l 0.5)
                (* l (+ 1.0 s))
                (+ l s (* (- l) s)))
            p (- (* 2.0 l) q)
            r (hue->rgb p q (+ h (/ 1.0 3.0)))
            g (hue->rgb p q h)
            b (hue->rgb p q (- h (/ 1.0 3.0)))]
        (rgb (long (* r 255.0))
             (long (* g 255.0))
             (long (* b 255.0))
             (long (* a 255.0)))))))

(defmethod to-rgb :hsv
  [{:keys [h s v a]}]
  (let [h (/ h 360.0)
        i (long (* h 6.0))
        f (- (* h 6.0) i)
        p (* v (- 1.0 s))
        q (* v (- 1.0 (* f s)))
        t (* v (- 1.0 (* (- 1.0 f) s)))
        mod-i (mod i 6)
        r (cond
            (= mod-i 0) v
            (= mod-i 1) q
            (= mod-i 2) p
            (= mod-i 3) p
            (= mod-i 4) t
            :else v)
        g (cond
            (= mod-i 0) t
            (= mod-i 1) v
            (= mod-i 2) v
            (= mod-i 3) q
            (= mod-i 4) p
            :else p)
        b (cond
            (= mod-i 0) p
            (= mod-i 1) p
            (= mod-i 2) t
            (= mod-i 3) v
            (= mod-i 4) v
            :else q)]
    (rgb (long (* r 255.0))
         (long (* g 255.0))
         (long (* b 255.0))
         (long (* a 255.0)))))

(defn ->int
  "Convert any color structure to ARGB integer."
  [color]
  (let [{:keys [r g b a]} (to-rgb color)]
    (unchecked-int
     (bit-or (bit-shift-left a 24)
             (bit-shift-left r 16)
             (bit-shift-left g 8)
             b))))

;; -- Manipulation --

(defn with-alpha
  "Set alpha channel (0-255 for RGB, 0.0-1.0 for others)."
  [color alpha]
  (case (:type color)
    :rgb (assoc color :a (clamp255 (long alpha)))
    (assoc color :a (clamp01 (double alpha)))))

(defn set-opacity
  "Set opacity from 0.0 to 1.0 for any color type."
  [color opacity]
  (case (:type color)
    :rgb (assoc color :a (clamp255 (long (* opacity 255.0))))
    (assoc color :a (clamp01 (double opacity)))))

(defn ensure
  "Ensure value is a color map. Handles hex strings, integers, normalized RGBA vectors, or existing maps.
   Returns nil if value is nil."
  [v]
  (cond
    (nil? v) nil
    (map? v) v
    (vector? v) (let [[r g b a] v]
                  (rgb (long (* (double (or r 0.0)) 255.0))
                       (long (* (double (or g 0.0)) 255.0))
                       (long (* (double (or b 0.0)) 255.0))
                       (long (* (double (or a 1.0)) 255.0))))
    :else (hex v)))

(defn lighten
  "Lighten a color by a factor (0.0-1.0). Works best in HSL."
  [color factor]
  (let [base (if (= (:type color) :hsl) color (let [_ (to-rgb color)]
                                                (hsl 0 0 0) ;; TODO: full conversion RGB->HSL if needed, 
                                                ;; but for now let's just operate on RGB simply or implement full chain.
                                                ;; Implementing RGB->HSL is tedious, let's stick to simple RGB lerp to white if not HSL.
                                                nil))] 
    (if base
      (update base :l #(min 1.0 (+ % (* (- 1.0 %) factor))))
      ;; Fallback for RGB: mix with white
      (let [{:keys [r g b a]} (to-rgb color)
            f (clamp01 factor)]
        (rgb (long (+ r (* (- 255 r) f)))
             (long (+ g (* (- 255 g) f)))
             (long (+ b (* (- 255 b) f)))
             a)))))

(defn lerp
  [start end t]
  (let [s (to-rgb start)
        e (to-rgb end)
        t (clamp01 t)]
    (rgb (long (+ (:r s) (* (- (:r e) (:r s)) t)))
         (long (+ (:g s) (* (- (:g e) (:g s)) t)))
         (long (+ (:b s) (* (- (:b e) (:b s)) t)))
         (long (+ (:a s) (* (- (:a e) (:a s)) t))))))

(defn multiply
  "Multiply RGB channels by a factor, preserving alpha."
  [color factor]
  (let [{:keys [r g b a]} (to-rgb color)
        f (double factor)]
    (rgb (long (* r f))
         (long (* g f))
         (long (* b f))
         a)))

(defn multiply-alpha
  "Multiply alpha channel by a factor."
  [color factor]
  (let [a (case (:type color)
            :rgb (:a color)
            (long (* (:a color) 255.0)))
        new-a (clamp255 (long (* a (double factor))))]
    (case (:type color)
      :rgb (assoc color :a new-a)
      (assoc color :a (/ (double new-a) 255.0)))))
