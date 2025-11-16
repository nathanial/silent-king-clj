(ns silent-king.assets
  (:require [clojure.data.json :as json])
  (:import [io.github.humbleui.skija Image Data]
           [java.io File ByteArrayOutputStream]
           [javax.imageio ImageIO]
           [java.awt.image BufferedImage]))

(set! *warn-on-reflection* true)

(def ^:const black-threshold 20)

(defn remove-black-background [^File image-file]
  "Load image as BufferedImage and make near-black pixels transparent"
  (let [^BufferedImage img (ImageIO/read image-file)
        width (.getWidth img)
        height (.getHeight img)
        ;; Create a new ARGB image to support transparency
        ^BufferedImage result (BufferedImage. width height BufferedImage/TYPE_INT_ARGB)]

    ;; Copy pixels, making near-black ones transparent
    (dotimes [y height]
      (dotimes [x width]
        (let [rgb (.getRGB img x y)
              ;; Extract color components
              a (bit-and (unsigned-bit-shift-right rgb 24) 0xFF)
              r (bit-and (unsigned-bit-shift-right rgb 16) 0xFF)
              g (bit-and (unsigned-bit-shift-right rgb 8) 0xFF)
              b (bit-and rgb 0xFF)]

          ;; If pixel is near-black, make it transparent
          (if (and (< r black-threshold)
                   (< g black-threshold)
                   (< b black-threshold))
            ;; Set to fully transparent
            (.setRGB result x y (unchecked-int 0x00000000))
            ;; Keep original color with full opacity
            (.setRGB result x y (bit-or (unchecked-int 0xFF000000) rgb))))))

    result))

(defn buffered-image-to-skija [^BufferedImage buffered-image]
  "Convert BufferedImage to Skija Image by encoding as PNG"
  (let [baos (ByteArrayOutputStream.)]
    ;; Write BufferedImage as PNG to byte array
    (ImageIO/write buffered-image "PNG" baos)
    (let [bytes (.toByteArray baos)
          ;; Create Skija Data from byte array
          data (Data/makeFromBytes bytes)
          ;; Create Skija Image from Data
          image (Image/makeFromEncoded (.getBytes data))]
      (.close data)
      image)))

(defn load-star-images []
  (println "Loading preprocessed star images...")
  (let [star-files (sort (.listFiles (File. "assets/stars-processed")))]
    (vec (for [^File file star-files
               :when (.endsWith (.getName file) ".png")]
           (let [;; Load preprocessed image directly (no runtime processing needed)
                 skija-image (Image/makeFromEncoded (.getBytes (Data/makeFromFileName (.getPath file))))]
             {:image skija-image
              :path (.getName file)})))))

(defn load-atlas-metadata []
  "Load atlas metadata from JSON file"
  (println "Loading atlas metadata...")
  (let [metadata-file (File. "assets/star-atlas.json")
        json-data (json/read-str (slurp metadata-file) :key-fn keyword)]
    (into {} (map (fn [entry]
                    [(:name entry)
                     {:x (:x entry)
                      :y (:y entry)
                      :size (:size entry)}])
                  json-data))))

(defn load-atlas-image []
  "Load the atlas texture"
  (println "Loading atlas texture...")
  (let [atlas-file (File. "assets/star-atlas.png")]
    (Image/makeFromEncoded (.getBytes (Data/makeFromFileName (.getPath atlas-file))))))

(defn load-all-assets []
  "Load both individual star images and atlas for LOD rendering"
  (println "Loading all assets...")
  {:individual-images (load-star-images)
   :atlas-image (load-atlas-image)
   :atlas-metadata (load-atlas-metadata)
   :atlas-size 4096})
