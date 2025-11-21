(ns silent-king.schemas-test
  (:require [clojure.test :refer [deftest is testing]]
            [malli.core :as m]
            [silent-king.galaxy :as galaxy]
            [silent-king.hyperlanes :as hyperlanes]
            [silent-king.schemas :as schemas]
            [silent-king.state :as state]))

(deftest create-game-state-conforms
  (is (m/validate schemas/GameState (state/create-game-state))))

(deftest generators-return-schema-checked-maps
  (let [star-images [{:path "stars/a.png"} {:path "stars/b.png"}]
        planet-sprites ["planets/a.png"]
        generated-galaxy (galaxy/generate-galaxy star-images planet-sprites 12)
        hyperlane-result (hyperlanes/generate-hyperlanes (vals (:stars generated-galaxy)))]
    (testing "galaxy generation wires ids and entities correctly"
      (is (m/validate schemas/GeneratedGalaxy generated-galaxy)))
    (testing "hyperlane generation yields structured neighbors"
      (is (m/validate schemas/GeneratedHyperlanes hyperlane-result)))))

(deftest boundary-validation-can-be-enabled
  (let [game-state (atom (state/create-game-state))
        base-star {:x 0.0
                   :y 0.0
                   :size 30.0
                   :density 0.5
                   :sprite-path "stars/base.png"
                   :rotation-speed 1.0}
        invalid-star (assoc base-star :size -2.0)]
    (schemas/with-boundary-validation
      (is (pos-int? (state/add-star! game-state base-star)))
      (is (thrown? clojure.lang.ExceptionInfo
                   (state/add-star! game-state invalid-star))))))
