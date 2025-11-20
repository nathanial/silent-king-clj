#!/usr/bin/env clojure

(ns preprocess-planets
  (:import [java.io File]
           [javax.imageio ImageIO]
           [java.awt.image BufferedImage]))

(set! *warn-on-reflection* true)

(def ^:const black-threshold 20)

(defn remove-black-background [^BufferedImage img]
  "Make near-black pixels transparent in a BufferedImage"
  (let [width (.getWidth img)
        height (.getHeight img)
        ^BufferedImage result (BufferedImage. width height BufferedImage/TYPE_INT_ARGB)]

    ;; Copy pixels, making near-black ones transparent
    (dotimes [y height]
      (dotimes [x width]
        (let [rgb (.getRGB img x y)
              r (bit-and (unsigned-bit-shift-right rgb 16) 0xFF)
              g (bit-and (unsigned-bit-shift-right rgb 8) 0xFF)
              b (bit-and rgb 0xFF)]

          (if (and (< r black-threshold)
                   (< g black-threshold)
                   (< b black-threshold))
            (.setRGB result x y (unchecked-int 0x00000000))
            (.setRGB result x y (bit-or (unchecked-int 0xFF000000) rgb))))))

    result))

(defn process-image [^File input-file ^File output-file]
  "Load image, remove black background, and save"
  (let [^BufferedImage original (ImageIO/read input-file)
        ^BufferedImage processed (remove-black-background original)]
    (ImageIO/write processed "PNG" output-file)))

(defn -main []
  (println "Planet Image Preprocessor")
  (println "=========================")

  (let [input-dir (File. "assets/planets")
        output-dir (File. "assets/planets-processed")]

    ;; Create output directory
    (when-not (.exists output-dir)
      (.mkdir output-dir)
      (println "Created output directory: assets/planets-processed"))

    ;; Get all PNG files
    (let [planet-files (sort (.listFiles input-dir))
          png-files (filter #(.endsWith (.getName ^File %) ".png") planet-files)]

      (println (format "\nProcessing %d planet images..." (count png-files)))
      (println "Removing black backgrounds and saving to assets/planets-processed/")

      (doseq [[idx ^File file] (map-indexed vector png-files)]
        (when (zero? (mod idx 10))
          (println (format "  Processing %d/%d..." (inc idx) (count png-files))))

        (let [output-file (File. output-dir (.getName file))]
          (process-image file output-file)))

      (println (format "\nDone! Processed %d images" (count png-files)))
      (println "Preprocessed images saved to: assets/planets-processed/"))))

(-main)

