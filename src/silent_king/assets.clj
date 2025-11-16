(ns silent-king.assets
  (:import [io.github.humbleui.skija Image Data]
           [java.io File]))

(set! *warn-on-reflection* true)

(defn load-star-images []
  (println "Loading star images...")
  (let [star-files (sort (.listFiles (File. "assets/stars")))]
    (vec (for [^File file star-files
               :when (.endsWith (.getName file) ".png")]
           (let [data (Data/makeFromFileName (.getPath file))
                 image (Image/makeFromEncoded (.getBytes data))]
             (.close data)
             {:image image
              :path (.getName file)})))))
