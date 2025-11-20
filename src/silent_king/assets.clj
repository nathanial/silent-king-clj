(ns silent-king.assets
  (:require [clojure.data.json :as json])
  (:import [io.github.humbleui.skija Image Data]
           [java.io File]
           ))

(set! *warn-on-reflection* true)

(defn load-star-images []
  "Load all preprocessed star images (with transparency) as Skija Images"
  (println "Loading star images...")
  (let [star-files (sort (.listFiles (File. "assets/stars-processed")))]
    (vec (for [^File file star-files
               :when (.endsWith (.getName file) ".png")]
           {:path (.getName file)
            :image (Image/makeFromEncoded (.getBytes (Data/makeFromFileName (.getPath file))))}))))

(defn load-atlas-metadata [metadata-path]
  "Load atlas metadata from JSON file"
  (println (format "Loading atlas metadata from %s..." metadata-path))
  (let [metadata-file (File. metadata-path)
        json-data (json/read-str (slurp metadata-file) :key-fn keyword)]
    (into {} (map (fn [entry]
                    [(:name entry)
                     {:x (:x entry)
                      :y (:y entry)
                      :size (:size entry)}])
                  json-data))))

(defn load-atlas-image [image-path]
  "Load the atlas texture"
  (println (format "Loading atlas texture from %s..." image-path))
  (let [atlas-file (File. image-path)]
    (Image/makeFromEncoded (.getBytes (Data/makeFromFileName (.getPath atlas-file))))))

(defn load-all-assets []
  "Load texture atlases (xs, small, medium) for LOD rendering and full-res images for high zoom"
  (println "Loading all assets...")
  {:atlas-image-xs (load-atlas-image "assets/star-atlas-xs.png")
   :atlas-metadata-xs (load-atlas-metadata "assets/star-atlas-xs.json")
   :atlas-size-xs 4096
   :atlas-image-small (load-atlas-image "assets/star-atlas-small.png")
   :atlas-metadata-small (load-atlas-metadata "assets/star-atlas-small.json")
   :atlas-size-small 4096
   :atlas-image-medium (load-atlas-image "assets/star-atlas-medium.png")
   :atlas-metadata-medium (load-atlas-metadata "assets/star-atlas-medium.json")
   :atlas-size-medium 4096
   :planet-atlas-image-medium (load-atlas-image "assets/planet-atlas-medium.png")
   :planet-atlas-metadata-medium (load-atlas-metadata "assets/planet-atlas-medium.json")
   :planet-atlas-size-medium 4096
   :star-images (load-star-images)})
