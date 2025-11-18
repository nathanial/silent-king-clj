(ns silent-king.test-runner
  (:require [clojure.test :as t]
            [silent-king.reactui.app-test]
            [silent-king.reactui.core-test]
            [silent-king.reactui.events-test]
            [silent-king.reactui.interaction-test]
            [silent-king.reactui.layout-test]))

(defn -main
  "Run all Reactified UI unit tests."
  [& _]
  (let [result (apply t/run-tests '[silent-king.reactui.app-test
                                    silent-king.reactui.core-test
                                    silent-king.reactui.events-test
                                    silent-king.reactui.interaction-test
                                    silent-king.reactui.layout-test])
        failures (+ (:fail result 0) (:error result 0))]
    (when (pos? failures)
      (System/exit 1))))
