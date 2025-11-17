(ns silent-king.test-runner
  (:require [clojure.test :as t]
            [silent-king.widgets.core-test]
            [silent-king.widgets.interaction-test]
            [silent-king.widgets.draw-order-test]
            [silent-king.widgets.minimap-test]
            [silent-king.widgets.layout-test]
            [silent-king.widgets.animation-test]
            [silent-king.ui.star-inspector-test]
            [silent-king.ui.hyperlane-settings-test]))

(defn -main
  "Run all widget-related unit tests."
  [& _]
  (let [result (apply t/run-tests '[silent-king.widgets.core-test
                                    silent-king.widgets.interaction-test
                                    silent-king.widgets.draw-order-test
                                    silent-king.widgets.minimap-test
                                    silent-king.widgets.layout-test
                                    silent-king.widgets.animation-test
                                    silent-king.ui.star-inspector-test
                                    silent-king.ui.hyperlane-settings-test])
        failures (+ (:fail result 0) (:error result 0))]
    (when (pos? failures)
      (System/exit 1))))
