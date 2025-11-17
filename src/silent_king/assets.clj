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


(defn load-star-filenames []
  "Load just the filenames of preprocessed star images (not the actual images)"
  (println "Loading star filenames...")
  (let [star-files (sort (.listFiles (File. "assets/stars-processed")))]
    (vec (for [^File file star-files
               :when (.endsWith (.getName file) ".png")]
           {:path (.getName file)}))))

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
  "Load texture atlases (xs, small, medium) for 3-level LOD rendering"
  (println "Loading all assets...")
  {:atlas-image-xs (load-atlas-image "assets/star-atlas-xs.png")
   :atlas-metadata-xs (load-atlas-metadata "assets/star-atlas-xs.json")
   :atlas-size-xs 4096
   :atlas-image-small (load-atlas-image "assets/star-atlas-small.png")
   :atlas-metadata-small (load-atlas-metadata "assets/star-atlas-small.json")
   :atlas-size-small 4096
   :atlas-image-medium (load-atlas-image "assets/star-atlas-medium.png")
   :atlas-metadata-medium (load-atlas-metadata "assets/star-atlas-medium.json")
   :atlas-size-medium 4096})
