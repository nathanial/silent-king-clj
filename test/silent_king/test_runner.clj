(ns silent-king.test-runner
  (:require [clojure.test :as t]
            [silent-king.reactui.layout-test]
            [silent-king.reactui.core-test]))

(defn -main
  "Run all reactui-related unit tests."
  [& _]
  (let [result (apply t/run-tests '[silent-king.reactui.layout-test
                                    silent-king.reactui.core-test])
        failures (+ (:fail result 0) (:error result 0))]
    (when (pos? failures)
      (System/exit 1))))
