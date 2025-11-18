(ns silent-king.ui.performance-dashboard-test
  (:require [clojure.test :refer [deftest is testing]]
            [silent-king.state :as state]
            [silent-king.widgets.core :as wcore]
            [silent-king.ui.performance-dashboard :as pdash]))

(deftest dashboard-initializes-collapsed
  (testing "Performance dashboard starts collapsed with body hidden and :expanded? false"
    (state/reset-entity-ids!)
    (let [game-state (atom (state/create-game-state))]
      ;; Ensure :expanded? defaults to false
      (is (false? (get-in @game-state [:ui :performance-dashboard :expanded?])))

      ;; Create the dashboard
      (pdash/create-performance-dashboard! game-state)

      ;; Verify panel was registered
      (let [panel-id (get-in @game-state [:ui :performance-dashboard :panel-entity])]
        (is (some? panel-id))
        (is (some? (state/get-entity game-state panel-id))))

      ;; Verify body entity exists and is hidden
      (let [body-id (get-in @game-state [:ui :performance-dashboard :body-entity])]
        (is (some? body-id))
        (let [body-entity (state/get-entity game-state body-id)]
          (is (some? body-entity))
          (is (false? (get-in body-entity [:components :widget :visible?])))))

      ;; Verify :expanded? remains false
      (is (false? (get-in @game-state [:ui :performance-dashboard :expanded?])))

      ;; Verify panel height is collapsed
      (let [panel-id (get-in @game-state [:ui :performance-dashboard :panel-entity])
            panel-entity (state/get-entity game-state panel-id)
            panel-height (get-in panel-entity [:components :bounds :height])
            collapsed-height (get-in @game-state [:ui :performance-dashboard :collapsed-height])]
        (is (= collapsed-height panel-height))))))

(deftest toggle-expanded-updates-height
  (testing "Toggling expanded state changes panel height and body visibility"
    (state/reset-entity-ids!)
    (let [game-state (atom (state/create-game-state))]
      ;; Create dashboard
      (pdash/create-performance-dashboard! game-state)

      ;; Get initial state
      (let [panel-id (get-in @game-state [:ui :performance-dashboard :panel-entity])
            body-id (get-in @game-state [:ui :performance-dashboard :body-entity])
            collapsed-height (get-in @game-state [:ui :performance-dashboard :collapsed-height])
            expanded-height (get-in @game-state [:ui :performance-dashboard :expanded-height])]

        ;; Verify initial state (collapsed)
        (is (false? (get-in @game-state [:ui :performance-dashboard :expanded?])))
        (is (= collapsed-height (get-in (state/get-entity game-state panel-id)
                                        [:components :bounds :height])))
        (is (false? (get-in (state/get-entity game-state body-id)
                            [:components :widget :visible?])))

        ;; Toggle expanded using private function via var deref
        (#'pdash/toggle-expanded! game-state)

        ;; Verify expanded state
        (is (true? (get-in @game-state [:ui :performance-dashboard :expanded?])))
        (is (= expanded-height (get-in (state/get-entity game-state panel-id)
                                       [:components :bounds :height])))
        (is (true? (get-in (state/get-entity game-state body-id)
                           [:components :widget :visible?])))

        ;; Toggle back to collapsed
        (#'pdash/toggle-expanded! game-state)

        ;; Verify collapsed state
        (is (false? (get-in @game-state [:ui :performance-dashboard :expanded?])))
        (is (= collapsed-height (get-in (state/get-entity game-state panel-id)
                                        [:components :bounds :height])))
        (is (false? (get-in (state/get-entity game-state body-id)
                            [:components :widget :visible?])))))))

(deftest update-dashboard-refreshes-labels
  (testing "update-dashboard! updates FPS label and metrics history"
    (state/reset-entity-ids!)
    (let [game-state (atom (state/create-game-state))]
      ;; Create dashboard
      (pdash/create-performance-dashboard! game-state)

      ;; Sample metrics map
      (let [sample-metrics {:fps 60.5
                           :frame-time-ms 16.6
                           :total-stars 1000
                           :visible-stars 250
                           :hyperlane-count 50
                           :visible-hyperlanes 12
                           :widget-count 15
                           :draw-calls 8
                           :memory-mb 128.5
                           :current-time 1.0}]

        ;; Update dashboard with sample metrics
        (pdash/update-dashboard! game-state sample-metrics)

        ;; Verify FPS label text was updated
        (let [[fps-label-id fps-label-entity] (wcore/get-widget-by-id game-state :performance-dashboard-fps)
              fps-text (get-in fps-label-entity [:components :visual :text])]
          (is (some? fps-label-id))
          (is (= "60.5 FPS" fps-text)))

        ;; Verify :metrics :performance :fps-history was updated
        (let [fps-history (get-in @game-state [:metrics :performance :fps-history])]
          (is (some? fps-history))
          (is (= [60.5] fps-history)))

        ;; Verify :metrics :performance :frame-time-history was updated
        (let [frame-time-history (get-in @game-state [:metrics :performance :frame-time-history])]
          (is (some? frame-time-history))
          (is (= [16.6] frame-time-history)))

        ;; Verify stat labels were updated
        (let [[stars-label-id stars-entity] (wcore/get-widget-by-id game-state :performance-dashboard-stars)
              stars-text (get-in stars-entity [:components :visual :text])]
          (is (some? stars-label-id))
          (is (= "Stars: 1000 (visible 250)" stars-text)))

        (let [[hyperlanes-label-id hyperlanes-entity] (wcore/get-widget-by-id game-state :performance-dashboard-hyperlanes)
              hyperlanes-text (get-in hyperlanes-entity [:components :visual :text])]
          (is (some? hyperlanes-label-id))
          (is (= "Hyperlanes: 50 (visible 12)" hyperlanes-text)))

        (let [[widgets-label-id widgets-entity] (wcore/get-widget-by-id game-state :performance-dashboard-widgets)
              widgets-text (get-in widgets-entity [:components :visual :text])]
          (is (some? widgets-label-id))
          (is (= "Widgets: 15" widgets-text)))

        (let [[draw-calls-label-id draw-calls-entity] (wcore/get-widget-by-id game-state :performance-dashboard-draw-calls)
              draw-calls-text (get-in draw-calls-entity [:components :visual :text])]
          (is (some? draw-calls-label-id))
          (is (= "Draw calls: 8" draw-calls-text)))

        (let [[memory-label-id memory-entity] (wcore/get-widget-by-id game-state :performance-dashboard-memory)
              memory-text (get-in memory-entity [:components :visual :text])]
          (is (some? memory-label-id))
          (is (= "Memory: 128.5 MB" memory-text)))))))

(deftest update-dashboard-accumulates-history
  (testing "Multiple updates accumulate history up to the limit"
    (state/reset-entity-ids!)
    (let [game-state (atom (state/create-game-state))]
      ;; Create dashboard
      (pdash/create-performance-dashboard! game-state)

      ;; Update with three different FPS values
      (pdash/update-dashboard! game-state {:fps 60.0 :frame-time-ms 16.6 :current-time 1.0})
      (pdash/update-dashboard! game-state {:fps 55.0 :frame-time-ms 18.2 :current-time 2.0})
      (pdash/update-dashboard! game-state {:fps 62.0 :frame-time-ms 16.1 :current-time 3.0})

      ;; Verify history contains all three values
      (let [fps-history (get-in @game-state [:metrics :performance :fps-history])]
        (is (= [60.0 55.0 62.0] fps-history)))

      (let [frame-time-history (get-in @game-state [:metrics :performance :frame-time-history])]
        (is (= [16.6 18.2 16.1] frame-time-history))))))

(deftest toggle-pin-updates-state
  (testing "Toggling pin state updates :pinned? and disables dragging"
    (state/reset-entity-ids!)
    (let [game-state (atom (state/create-game-state))]
      ;; Create dashboard
      (pdash/create-performance-dashboard! game-state)

      ;; Verify initial state (not pinned)
      (is (false? (get-in @game-state [:ui :performance-dashboard :pinned?])))

      ;; Get header entity
      (let [header-id (get-in @game-state [:ui :performance-dashboard :header-entity])]
        ;; Verify header is draggable initially
        (is (true? (get-in (state/get-entity game-state header-id)
                           [:components :interaction :enabled])))

        ;; Toggle pin using private function
        (#'pdash/toggle-pin! game-state)

        ;; Verify pinned state
        (is (true? (get-in @game-state [:ui :performance-dashboard :pinned?])))

        ;; Verify header is no longer draggable
        (is (false? (get-in (state/get-entity game-state header-id)
                            [:components :interaction :enabled])))

        ;; Verify pin button text was updated
        (let [[pin-button-id pin-button-entity] (wcore/get-widget-by-id game-state :performance-dashboard-pin)
              pin-text (get-in pin-button-entity [:components :visual :text])]
          (is (some? pin-button-id))
          (is (= "UNPIN" pin-text)))

        ;; Toggle back to unpinned
        (#'pdash/toggle-pin! game-state)

        ;; Verify unpinned state
        (is (false? (get-in @game-state [:ui :performance-dashboard :pinned?])))

        ;; Verify header is draggable again
        (is (true? (get-in (state/get-entity game-state header-id)
                           [:components :interaction :enabled])))

        ;; Verify pin button text was updated back
        (let [[pin-button-id pin-button-entity] (wcore/get-widget-by-id game-state :performance-dashboard-pin)
              pin-text (get-in pin-button-entity [:components :visual :text])]
          (is (= "PIN" pin-text)))))))

(deftest expand-button-text-changes
  (testing "Expand button text reflects current expanded state"
    (state/reset-entity-ids!)
    (let [game-state (atom (state/create-game-state))]
      ;; Create dashboard (starts collapsed)
      (pdash/create-performance-dashboard! game-state)

      ;; Verify initial button text (collapsed state shows [+])
      (let [[expand-button-id expand-entity] (wcore/get-widget-by-id game-state :performance-dashboard-expand)
            initial-text (get-in expand-entity [:components :visual :text])]
        (is (some? expand-button-id))
        (is (= "[+]" initial-text)))

      ;; Toggle to expanded
      (#'pdash/toggle-expanded! game-state)

      ;; Verify button text changed to [-]
      (let [[_ expand-entity] (wcore/get-widget-by-id game-state :performance-dashboard-expand)
            expanded-text (get-in expand-entity [:components :visual :text])]
        (is (= "[-]" expanded-text)))

      ;; Toggle back to collapsed
      (#'pdash/toggle-expanded! game-state)

      ;; Verify button text changed back to [+]
      (let [[_ expand-entity] (wcore/get-widget-by-id game-state :performance-dashboard-expand)
            collapsed-text (get-in expand-entity [:components :visual :text])]
        (is (= "[+]" collapsed-text))))))

(deftest chart-values-updated
  (testing "Charts receive updated point data from metrics history"
    (state/reset-entity-ids!)
    (let [game-state (atom (state/create-game-state))]
      ;; Create dashboard
      (pdash/create-performance-dashboard! game-state)

      ;; Update with multiple data points
      (pdash/update-dashboard! game-state {:fps 60.0 :frame-time-ms 16.6})
      (pdash/update-dashboard! game-state {:fps 58.0 :frame-time-ms 17.2})
      (pdash/update-dashboard! game-state {:fps 62.0 :frame-time-ms 16.1})

      ;; Verify FPS chart has correct points
      (let [[fps-chart-id fps-chart-entity] (wcore/get-widget-by-id game-state :performance-dashboard-fps-chart)
            fps-points (get-in fps-chart-entity [:components :value :points])]
        (is (some? fps-chart-id))
        (is (= [60.0 58.0 62.0] fps-points)))

      ;; Verify frame time chart has correct points
      (let [[frame-chart-id frame-chart-entity] (wcore/get-widget-by-id game-state :performance-dashboard-frame-time-chart)
            frame-points (get-in frame-chart-entity [:components :value :points])]
        (is (some? frame-chart-id))
        (is (= [16.6 17.2 16.1] frame-points))))))

(deftest initial-expanded-state-respected
  (testing "Dashboard respects :expanded? true in initial state"
    (state/reset-entity-ids!)
    (let [game-state (atom (state/create-game-state))]
      ;; Set initial expanded state to true
      (swap! game-state assoc-in [:ui :performance-dashboard :expanded?] true)

      ;; Create dashboard
      (pdash/create-performance-dashboard! game-state)

      ;; Verify panel starts with expanded height
      (let [panel-id (get-in @game-state [:ui :performance-dashboard :panel-entity])
            panel-entity (state/get-entity game-state panel-id)
            panel-height (get-in panel-entity [:components :bounds :height])
            expanded-height (get-in @game-state [:ui :performance-dashboard :expanded-height])]
        (is (= expanded-height panel-height)))

      ;; Verify body is visible
      (let [body-id (get-in @game-state [:ui :performance-dashboard :body-entity])
            body-entity (state/get-entity game-state body-id)]
        (is (true? (get-in body-entity [:components :widget :visible?]))))

      ;; Verify expand button shows [-]
      (let [[_ expand-entity] (wcore/get-widget-by-id game-state :performance-dashboard-expand)
            button-text (get-in expand-entity [:components :visual :text])]
        (is (= "[-]" button-text))))))
