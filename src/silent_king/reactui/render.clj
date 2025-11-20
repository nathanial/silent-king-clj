(ns silent-king.reactui.render
  "Skija renderer for the Reactified UI tree."
  (:require [silent-king.minimap.math :as minimap-math]
            [silent-king.reactui.layout :as layout])
  (:import [io.github.humbleui.skija Canvas Font Typeface Paint PaintMode]
           [io.github.humbleui.types Rect]))

(set! *warn-on-reflection* true)

(defonce ^Typeface default-typeface
  (Typeface/makeDefault))

(def ^:dynamic *overlay-collector* nil)
(def ^:dynamic *render-context* nil)

(defn queue-overlay!
  [overlay]
  (when *overlay-collector*
    (swap! *overlay-collector* conj overlay)))

(defmulti draw-node
  (fn [_ node]
    (:type node)))

(defmulti draw-overlay
  (fn [_ overlay]
    (:type overlay)))

(defn make-font
  [font-size]
  (Font. default-typeface (float font-size)))

(defn approx-text-width
  [text font-size]
  (* (count (or text ""))
     font-size
     0.55))

(defn clamp01
  [value]
  (-> value
      (max 0.0)
      (min 1.0)))

(defn- adjust-channel
  [value factor]
  (-> (* (double value) factor)
      (double)
      (Math/round)
      (max 0)
      (min 255)))

(defn adjust-color
  [color factor]
  (let [a (bit-and (bit-shift-right color 24) 0xFF)
        r (bit-and (bit-shift-right color 16) 0xFF)
        g (bit-and (bit-shift-right color 8) 0xFF)
        b (bit-and color 0xFF)
        nr (adjust-channel r factor)
        ng (adjust-channel g factor)
        nb (adjust-channel b factor)]
    (unchecked-int (bit-or (bit-shift-left a 24)
                           (bit-shift-left nr 16)
                           (bit-shift-left ng 8)
                           nb))))

(defn ->color-int
  "Convert the supplied value (which may be a long ARGB literal) into a signed 32-bit int.
  Ensures we never trigger Math/toIntExact overflow even if the high bit is set."
  [value default]
  (let [raw (long (or value default))
        signed (if (> raw 0x7FFFFFFF)
                 (- raw 0x100000000)
                 raw)]
    (int signed)))

(defn- lerp
  [a b t]
  (+ a (* t (- b a))))

(defn- round-channel
  [value]
  (int (long (Math/round (double value)))))

(defn blend-colors
  [color-a color-b t]
  (let [t (clamp01 t)
        ca (->color-int color-a color-a)
        cb (->color-int color-b color-b)
        a1 (bit-and (bit-shift-right ca 24) 0xFF)
        r1 (bit-and (bit-shift-right ca 16) 0xFF)
        g1 (bit-and (bit-shift-right ca 8) 0xFF)
        b1 (bit-and ca 0xFF)
        a2 (bit-and (bit-shift-right cb 24) 0xFF)
        r2 (bit-and (bit-shift-right cb 16) 0xFF)
        g2 (bit-and (bit-shift-right cb 8) 0xFF)
        b2 (bit-and cb 0xFF)
        a (round-channel (lerp a1 a2 t))
        r (round-channel (lerp r1 r2 t))
        g (round-channel (lerp g1 g2 t))
        b (round-channel (lerp b1 b2 t))]
    (->color-int (bit-or (bit-shift-left a 24)
                         (bit-shift-left r 16)
                         (bit-shift-left g 8)
                         b)
                 color-a)))

(def ^:const heatmap-low-color 0x1A08244A)
(def ^:const heatmap-high-color 0xFFF1C232)

(defn heatmap-cell-size
  [width height]
  (let [w (double (or width 0.0))
        h (double (or height 0.0))
        largest (max 1.0 (max w h))
        target (/ largest 48.0)]
    (-> target
        (max 3.0)
        (min 18.0))))

(defn bucket-stars-into-grid
  [stars world-bounds widget-bounds cell-size]
  (let [cols (max 1 (int (Math/ceil (/ (:width widget-bounds) cell-size))))
        rows (max 1 (int (Math/ceil (/ (:height widget-bounds) cell-size))))
        total (* rows cols)
        counts (int-array total)]
    (loop [remaining stars
           max-density 0]
      (if-let [star (first remaining)]
        (let [pos (minimap-math/world->minimap star world-bounds widget-bounds)
              local-x (- (:x pos) (:x widget-bounds))
              local-y (- (:y pos) (:y widget-bounds))
              col (int (Math/floor (/ local-x cell-size)))
              row (int (Math/floor (/ local-y cell-size)))]
          (if (and (>= col 0) (< col cols)
                   (>= row 0) (< row rows))
            (let [idx (+ (* row cols) col)
                  new-count (unchecked-inc-int (aget ^ints counts idx))]
              (aset-int counts idx new-count)
              (recur (rest remaining)
                     (max max-density new-count)))
            (recur (rest remaining) max-density)))
        {:counts counts
         :cols cols
         :rows rows
         :cell-size cell-size
         :max-density max-density}))))

(defn density->color
  [count max-count]
  (if (pos? max-count)
    (let [ratio (double (/ count max-count))
          eased (Math/pow ratio 0.6)]
      (blend-colors heatmap-low-color heatmap-high-color eased))
    (->color-int heatmap-low-color heatmap-low-color)))

(defn pointer-position
  []
  (:pointer *render-context*))

(defn pointer-in-bounds?
  [{:keys [x y width height]}]
  (when-let [{px :x py :y} (pointer-position)]
    (let [px* (double px)
          py* (double py)]
      (and (>= px* (double x))
           (<= px* (+ (double x) width))
           (>= py* (double y))
           (<= py* (+ (double y) height))))))

(defn pointer-over-node?
  [node]
  (pointer-in-bounds? (layout/bounds node)))

(defn active-interaction
  []
  (:active-interaction *render-context*))

(defmethod draw-node :default
  [_ _]
  nil)

(defn draw-tree
  "Render a laid-out tree."
  [canvas node context]
  (when canvas
    (let [overlays (atom [])
          ctx (or context {})]
      (binding [*overlay-collector* overlays
                *render-context* ctx]
        (draw-node canvas node)
        (doseq [overlay @overlays]
          (draw-overlay canvas overlay))))))
