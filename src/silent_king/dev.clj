(ns silent-king.dev
  "Development helpers (instrumentation, etc.)."
  (:require [malli.instrument :as mi]
            [silent-king.galaxy :as galaxy]
            [silent-king.hyperlanes :as hyperlanes]))

(set! *warn-on-reflection* true)

(def instrumented-vars
  [#'galaxy/generate-galaxy
   #'hyperlanes/generate-hyperlanes])

(defn instrument!
  "Instrument selected functions using their :malli/schema metadata."
  []
  (mi/instrument! {:vars instrumented-vars}))

(defn uninstrument!
  []
  (mi/unstrument! {:vars instrumented-vars}))
