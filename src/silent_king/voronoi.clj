(ns silent-king.voronoi
  "Voronoi cell generation and rendering over the star field."
  (:require [silent-king.camera :as camera]
            [silent-king.state :as state])
  (:import [org.locationtech.jts.geom Coordinate Envelope GeometryFactory Polygon]
           [org.locationtech.jts.triangulate VoronoiDiagramBuilder]
           [io.github.humbleui.skija Canvas Paint PaintMode PaintStrokeCap Path]))

(set! *warn-on-reflection* true)

(def ^:const ^double clip-padding-min 200.0)
(def ^:const ^double clip-padding-scale 0.18)
(def ^:const ^double epsilon 1.0e-6)

(def ^:private color-schemes
  {:monochrome {:stroke 0xFFFFFFFF
                :fill 0x99FFFFFF}
   :by-density {:stroke 0xFF66FFCC
                :fill 0x5566FFCC}
   :by-degree {:stroke 0xFFFFC36F
               :fill 0x55FFC36F}})

(defn- clamp
  [value min-value max-value]
  (-> value double (max min-value) (min max-value)))

(defn- valid-vertex?
  [{:keys [x y]}]
  (and (number? x) (number? y) (not (Double/isNaN (double x))) (not (Double/isNaN (double y)))))

(defn- apply-opacity
  [color opacity]
  (let [alpha (int (Math/round (* 255.0 (clamp opacity 0.0 1.0))))
        rgb (bit-and color 0x00FFFFFF)]
    (unchecked-int (bit-or (bit-shift-left alpha 24) rgb))))

(defn- coord->map
  [^Coordinate coord]
  {:x (.x coord)
   :y (.y coord)})

(defn- same-coord?
  [^Coordinate a ^Coordinate b]
  (and a b
       (< (Math/abs ^double (- (.x a) (.x b))) epsilon)
       (< (Math/abs ^double (- (.y a) (.y b))) epsilon)))

(defn- strip-duplicate-last
  [coords]
  (let [c (count coords)]
    (if (and (> c 1)
             (same-coord? (first coords) (last coords)))
      (subvec coords 0 (dec c))
      coords)))

(defn- sort-ccw
  [vertices {:keys [x y]}]
  (let [cx (double (or x 0.0))
        cy (double (or y 0.0))]
    (->> vertices
         (sort-by (fn [{:keys [x y]}]
                    (Math/atan2 (- (double (or y 0.0)) cy)
                                (- (double (or x 0.0)) cx))))
         vec)))

(defn- vertices->bbox
  [vertices]
  (let [valid (filter #(and (number? (:x %)) (number? (:y %))) vertices)]
    (if (seq valid)
      (reduce (fn [{:keys [min-x min-y max-x max-y]} {:keys [x y]}]
                {:min-x (min (double min-x) (double x))
                 :min-y (min (double min-y) (double y))
                 :max-x (max (double max-x) (double x))
                 :max-y (max (double max-y) (double y))})
              {:min-x Double/POSITIVE_INFINITY
               :min-y Double/POSITIVE_INFINITY
               :max-x Double/NEGATIVE_INFINITY
               :max-y Double/NEGATIVE_INFINITY}
              valid)
      {:min-x 0.0 :min-y 0.0 :max-x 0.0 :max-y 0.0})))

(defn- expand-envelope
  [^Envelope env]
  (when (and env (not (.isNull env)))
    (let [width (.getWidth env)
          height (.getHeight env)
          span (max width height)
          padding (+ clip-padding-min (* clip-padding-scale span))
          padded (Envelope. env)]
      (.expandBy padded padding padding)
      padded)))

(defn- star-envelope
  [stars]
  (reduce (fn [^Envelope env {:keys [x y]}]
            (.expandToInclude env (double (or x 0.0)) (double (or y 0.0)))
            env)
          (Envelope.)
          stars))

(defn- nearest-site
  [sites {:keys [x y]}]
  (when (and (seq sites) (number? x) (number? y))
    (reduce (fn [{:keys [dist] :as best} {:keys [coord] :as site}]
              (let [dx (- (.x ^Coordinate coord) (double x))
                    dy (- (.y ^Coordinate coord) (double y))
                    d (+ (* dx dx) (* dy dy))]
                (if (< d (double (or dist Double/POSITIVE_INFINITY)))
                  (assoc site :dist d)
                  best)))
            nil
            sites)))

(defn- polygon->cell
  [^Polygon polygon {:keys [coord star] :as site}]
  (let [raw-ring (strip-duplicate-last (vec (.getCoordinates (.getExteriorRing polygon))))
        ring (keep identity raw-ring)
        center (or (some-> site :coord coord->map)
                    (let [centroid (.getCentroid polygon)]
                      {:x (.getX centroid) :y (.getY centroid)}))
        vertices (->> ring
                      (keep (fn [^Coordinate c]
                              (when c (coord->map c))))
                      (remove #(or (nil? (:x %)) (nil? (:y %))))
                      (sort-ccw center))
        bbox (vertices->bbox vertices)
        centroid-geom (.getCentroid polygon)
        centroid {:x (.getX centroid-geom)
                  :y (.getY centroid-geom)}]
    (cond-> {:star-id (:id site)
             :vertices vertices
             :bbox bbox
             :centroid centroid}
      star (assoc :star star))))

(defn generate-voronoi
  "Pure Voronoi generator. Accepts a sequence of star maps with :id/:x/:y and
  returns {:voronoi-cells {id cell} :elapsed-ms n}."
  [stars]
  (let [start (System/currentTimeMillis)
        sites (mapv (fn [{:keys [id x y] :as star}]
                      {:id id
                       :coord (Coordinate. (double x) (double y))
                       :star star})
                    stars)
        site-count (count sites)]
    (if (< site-count 2)
      {:voronoi-cells {}
       :elapsed-ms (- (System/currentTimeMillis) start)}
      (let [envelope (-> (star-envelope stars)
                         (expand-envelope))
            builder (VoronoiDiagramBuilder.)
            _ (.setSites builder ^java.util.Collection (mapv :coord sites))
            _ (when envelope
                (.setClipEnvelope builder envelope))
            diagram (.getDiagram builder (GeometryFactory.))
            num-polys (.getNumGeometries diagram)]
        (loop [i 0
               acc {}]
          (if (= i num-polys)
            {:voronoi-cells acc
             :elapsed-ms (- (System/currentTimeMillis) start)}
            (let [^Polygon poly (.getGeometryN diagram i)
                  centroid (.getCentroid poly)
                  centroid-map {:x (.getX centroid)
                                :y (.getY centroid)}
                  user-data (.getUserData poly)
                  site (cond
                         (instance? Coordinate user-data)
                         (nearest-site sites (coord->map ^Coordinate user-data))

                         :else
                         (nearest-site sites centroid-map))
                  cell (when site (polygon->cell poly site))
                  acc* (if (and cell (:star-id cell))
                         (assoc acc (:star-id cell) cell)
                         acc)]
              (recur (inc i) acc*))))))))

(defn generate-voronoi!
  "Generate Voronoi cells from the current world's stars and persist them."
  [game-state]
  (let [stars (state/star-seq game-state)
        {:keys [voronoi-cells elapsed-ms] :as result} (generate-voronoi stars)]
    (state/set-voronoi-cells! game-state voronoi-cells)
    (swap! game-state assoc :voronoi-generated? true)
    (println "Generated" (count voronoi-cells) "Voronoi cells in" (or elapsed-ms 0) "ms")
    result))

(defn world-viewport
  "Return {:min-x :min-y :max-x :max-y} for the visible screen in world space."
  [width height zoom pan-x pan-y]
  (let [scale (camera/zoom->position-scale zoom)
        min-x (/ (- 0.0 pan-x) scale)
        max-x (/ (- (double width) pan-x) scale)
        min-y (/ (- 0.0 pan-y) scale)
        max-y (/ (- (double height) pan-y) scale)
        [min-x max-x] (if (< max-x min-x) [max-x min-x] [min-x max-x])
        [min-y max-y] (if (< max-y min-y) [max-y min-y] [min-y max-y])]
    {:min-x min-x
     :min-y min-y
     :max-x max-x
     :max-y max-y}))

(defn cell-visible?
  "Return true if the cell bbox intersects the expanded viewport."
  [{:keys [min-x min-y max-x max-y]} viewport margin]
  (let [mx (double (or margin 0.0))
        vx-min (double (get viewport :min-x 0.0))
        vx-max (double (get viewport :max-x 0.0))
        vy-min (double (get viewport :min-y 0.0))
        vy-max (double (get viewport :max-y 0.0))]
    (and (< (- (double min-x) mx) vx-max)
         (> (+ (double max-x) mx) vx-min)
         (< (- (double min-y) mx) vy-max)
         (> (+ (double max-y) mx) vy-min))))

(defn- transform-vertices
  [vertices zoom pan-x pan-y]
  (->> vertices
       (filter valid-vertex?)
       (mapv (fn [{:keys [x y]}]
               {:x (camera/transform-position (double x) zoom pan-x)
                :y (camera/transform-position (double y) zoom pan-y)}))))

(defn draw-voronoi-cells
  "Draw Voronoi overlay with LOD and culling. Returns count of rendered cells."
  [^Canvas canvas width height zoom pan-x pan-y game-state current-time]
  (let [settings (state/voronoi-settings game-state)
        enabled? (state/voronoi-enabled? game-state)
        cells (state/voronoi-cells game-state)
        opacity (clamp (:opacity settings 0.35) 0.05 1.0)
        line-width (clamp (:line-width settings 1.4) 0.5 4.0)
        show-centroids? (boolean (:show-centroids? settings))
        palette (get color-schemes (:color-scheme settings) (:monochrome color-schemes))
        stroke-color (apply-opacity (:stroke palette) (max 0.8 opacity))
        fill-color (apply-opacity (:fill palette) 1.0)
        centroid-color (apply-opacity (:stroke palette) 1.0)
        stroke-paint (doto (Paint.)
                       (.setColor (int stroke-color))
                       (.setMode PaintMode/STROKE)
                       (.setStrokeCap PaintStrokeCap/ROUND)
                       (.setAntiAlias true))
        fill-paint (doto (Paint.)
                     (.setColor (int fill-color))
                     (.setMode PaintMode/FILL)
                     (.setAntiAlias true))
        centroid-paint (doto (Paint.)
                         (.setColor (int centroid-color))
                         (.setMode PaintMode/FILL)
                         (.setAntiAlias true))
        rendered (atom 0)
        stroke-width-screen (fn []
                              (max 1.5 (camera/transform-size line-width zoom)))]
    (try
      (if (and canvas (seq cells))
        (doseq [[_ {:keys [vertices centroid]}] cells]
          (when (seq vertices)
            (let [screen-verts (->> (transform-vertices vertices zoom pan-x pan-y)
                                    (filter valid-vertex?)
                                    vec)
                  lw (stroke-width-screen)
                  draw-fill? true
                  stroke? true]
              (.setStrokeWidth stroke-paint (float lw))
              (let [n (count screen-verts)]
                (cond
                  (>= n 3)
                  (when-let [{:keys [x y]} (first screen-verts)]
                    (let [path (Path.)]
                      (.moveTo path (float x) (float y))
                      (doseq [{:keys [x y]} (rest screen-verts)]
                        (.lineTo path (float x) (float y)))
                      (.closePath path)
                      (when draw-fill?
                        (.drawPath canvas path fill-paint))
                      (when stroke?
                        (.drawPath canvas path stroke-paint))
                      (.close path)
                      (swap! rendered inc)))

                  (= n 2)
                  (let [[p0 p1] screen-verts]
                    (.drawLine canvas (float (:x p0)) (float (:y p0))
                               (float (:x p1)) (float (:y p1)) stroke-paint)
                    (when draw-fill?
                      (let [mx (/ (+ (:x p0) (:x p1)) 2.0)
                            my (/ (+ (:y p0) (:y p1)) 2.0)]
                        (.drawCircle canvas (float mx) (float my) 4.5 fill-paint)))
                    (swap! rendered inc))

                  :else
                  (when centroid
                    (let [{cx :x cy :y} centroid
                          sx (camera/transform-position (double cx) zoom pan-x)
                          sy (camera/transform-position (double cy) zoom pan-y)]
                      (.drawCircle canvas (float sx) (float sy) 6.0 fill-paint)
                      (.drawCircle canvas (float sx) (float sy) 7.0 stroke-paint)
                      (swap! rendered inc)))))

              (when (and show-centroids? centroid)
                (let [{cx :x cy :y} centroid
                      sx (camera/transform-position (double cx) zoom pan-x)
                      sy (camera/transform-position (double cy) zoom pan-y)]
                  (.drawCircle canvas (float sx) (float sy) 2.5 centroid-paint))))))
        (when (and enabled? (seq cells) (= 0 @rendered))
          (println "Voronoi draw: 0 cells rendered out of" (count cells))))
      (finally
        (.close stroke-paint)
        (.close fill-paint)
        (.close centroid-paint)))
    @rendered))
