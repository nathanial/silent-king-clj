(ns silent-king.test-runner
  (:require [clojure.test :as t]
            [silent-king.reactui.app-test]
            [silent-king.reactui.core-test]
            [silent-king.reactui.events-test]
            [silent-king.reactui.interaction-test]
            [silent-king.reactui.layout-test]
            [silent-king.planet-test]
            [silent-king.selection-test]
            [silent-king.voronoi-test]
            [silent-king.render.galaxy-test]
            [silent-king.hyperlanes-test]
            [silent-king.regions-test]))

(defn -main
  "Run all Reactified UI unit tests."
  [& _]
  (let [result (apply t/run-tests '[silent-king.reactui.app-test
                                    silent-king.reactui.core-test
                                    silent-king.reactui.events-test
                                    silent-king.reactui.interaction-test
                                    silent-king.reactui.layout-test
                                    silent-king.planet-test
                                    silent-king.selection-test
                                    silent-king.voronoi-test
                                    silent-king.render.galaxy-test
                                    silent-king.hyperlanes-test
                                    silent-king.regions-test])
        failures (+ (:fail result 0) (:error result 0))]
    (when (pos? failures)
      (System/exit 1))))
