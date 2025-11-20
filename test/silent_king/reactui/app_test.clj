(ns silent-king.reactui.app-test
  (:require [clojure.test :refer [deftest is]]
            [silent-king.reactui.app :as app]
            [silent-king.reactui.components.star-inspector :as star-inspector]
            [silent-king.reactui.core :as ui-core]
            [silent-king.reactui.events :as events]
            [silent-king.reactui.interaction :as interaction]
            [silent-king.reactui.layout :as layout]
            [silent-king.state :as state]
            [silent-king.test-fixtures :as fixtures]))

(def ^:const test-viewport {:x 0 :y 0 :width 2560 :height 1440})

(defn- build-layout
  [game-state]
  (state/set-ui-viewport! game-state {:width (:width test-viewport)
                                      :height (:height test-viewport)})
  (-> (app/root-tree game-state)
      ui-core/normalize-tree
      (layout/compute-layout test-viewport)))

(defn- find-node-by
  [node pred]
  (when node
    (or (when (pred node)
          node)
        (some #(find-node-by % pred) (:children node)))))

(defn- find-button-with-event
  [tree event]
  (find-node-by tree (fn [node]
                       (and (= :button (:type node))
                            (= event (get-in node [:props :on-click]))))))

(defn- find-slider-with-event
  [tree event]
  (find-node-by tree (fn [node]
                       (and (= :slider (:type node))
                            (= event (get-in node [:props :on-change]))))))

(defn- find-dropdown
  [tree]
  (find-node-by tree (fn [node]
                       (= :dropdown (:type node)))))

(defn- find-dropdown-by-id
  [tree dropdown-id]
  (find-node-by tree (fn [node]
                       (and (= :dropdown (:type node))
                            (= dropdown-id (get-in node [:props :id]))))))

(defn- find-bar-chart
  [tree]
  (find-node-by tree (fn [node]
                       (= :bar-chart (:type node)))))

(defn- find-node-with-key
  [tree key]
  (find-node-by tree (fn [node]
                       (= key (get-in node [:props :key])))))

(defn- find-window-with-title
  [tree title]
  (find-node-by tree (fn [node]
                       (and (= :window (:type node))
                            (= title (get-in node [:props :title]))))))

(deftest control-panel-props-reflect-game-state
  (let [game-state (atom (state/create-game-state))]
    (state/update-camera! game-state assoc :zoom 2.5)
    (swap! game-state assoc-in [:metrics :performance :latest]
           {:fps 55.5 :visible-stars 123 :draw-calls 900})
    (state/toggle-hyperlanes! game-state)
    (state/set-voronoi-setting! game-state :enabled? true)
    (let [props (app/control-panel-props game-state)]
      (is (= 2.5 (:zoom props)))
      (is (false? (:hyperlanes-enabled? props)))
      (is (true? (:voronoi-enabled? props)))
      (is (= 55.5 (get-in props [:metrics :fps])))
      (is (= 123 (get-in props [:metrics :visible-stars])))
      (is (= 2.0 (:ui-scale props))))))

(deftest control-panel-events-update-game-state
  (let [game-state (atom (state/create-game-state))
        tree (build-layout game-state)
        button-node (find-button-with-event tree [:ui/toggle-hyperlanes])
        button-bounds (layout/bounds button-node)
        button-x (+ (:x button-bounds) (/ (:width button-bounds) 2.0))
        button-y (+ (:y button-bounds) (/ (:height button-bounds) 2.0))
        button-events (interaction/click->events tree button-x button-y)]
    (doseq [event button-events]
      (events/dispatch-event! game-state event))
    (is (false? (state/hyperlanes-enabled? game-state)))
    (let [voronoi-button (find-button-with-event tree [:ui/toggle-voronoi])
          voronoi-bounds (layout/bounds voronoi-button)
          vx (+ (:x voronoi-bounds) (/ (:width voronoi-bounds) 2.0))
          vy (+ (:y voronoi-bounds) (/ (:height voronoi-bounds) 2.0))
          voronoi-events (interaction/click->events tree vx vy)]
      (doseq [event voronoi-events]
        (events/dispatch-event! game-state event)))
    (is (false? (state/voronoi-enabled? game-state)))
    (let [zoom-slider (find-slider-with-event tree [:ui/set-zoom])
          scale-slider (find-slider-with-event tree [:ui/set-scale])
          zoom-track (get-in zoom-slider [:layout :slider :track])
          zoom-x (+ (:x zoom-track) (:width zoom-track))
          zoom-y (+ (:y zoom-track) (/ (:height zoom-track) 2.0))
          zoom-events (interaction/click->events tree zoom-x zoom-y)
          scale-track (get-in scale-slider [:layout :slider :track])
          scale-x (+ (:x scale-track) (:width scale-track))
          scale-y (+ (:y scale-track) (/ (:height scale-track) 2.0))
          scale-events (interaction/click->events tree scale-x scale-y)]
      (doseq [event zoom-events]
        (events/dispatch-event! game-state event))
      (doseq [event scale-events]
        (events/dispatch-event! game-state event)))
    (is (= 4.0 (get-in @game-state [:camera :zoom])))
    (is (= 3.0 (state/ui-scale game-state)))))

(deftest hyperlane-settings-slider-updates-state
  (let [game-state (atom (state/create-game-state))
        tree (build-layout game-state)
        opacity-slider (find-slider-with-event tree [:hyperlanes/set-opacity])
        track (get-in opacity-slider [:layout :slider :track])
        click-x (+ (:x track) (:width track))
        click-y (+ (:y track) (/ (:height track) 2.0))
        events (interaction/click->events tree click-x click-y)]
    (doseq [event events]
      (events/dispatch-event! game-state event))
    (is (= 1.0 (:opacity (state/hyperlane-settings game-state))))))

(deftest voronoi-settings-slider-updates-state
  (let [game-state (atom (state/create-game-state))
        tree (build-layout game-state)
        opacity-slider (find-slider-with-event tree [:voronoi/set-opacity])
        track (get-in opacity-slider [:layout :slider :track])
        click-x (+ (:x track) (:width track))
        click-y (+ (:y track) (/ (:height track) 2.0))
        events (interaction/click->events tree click-x click-y)]
    (doseq [event events]
      (events/dispatch-event! game-state event))
    (is (= 1.0 (:opacity (state/voronoi-settings game-state))))))

(deftest voronoi-relax-iterations-slider-updates-state
  (let [game-state (atom (state/create-game-state))
        tree (build-layout game-state)
        iter-slider (find-slider-with-event tree [:voronoi/set-relax-iterations])]
    (is iter-slider)
    (events/dispatch-event! game-state [:voronoi/set-relax-iterations state/relax-iterations-limit])
    (is (= state/relax-iterations-limit (:relax-iterations (state/voronoi-settings game-state))))))

(deftest hyperlane-color-dropdown-selects-scheme
  (let [game-state (atom (state/create-game-state))
        initial-tree (build-layout game-state)
        dropdown (find-dropdown-by-id initial-tree :hyperlane-color)
        header (get-in dropdown [:layout :dropdown :header])
        toggle-events (interaction/click->events initial-tree
                                                 (+ (:x header) 4)
                                                 (+ (:y header) 4))]
    (doseq [event toggle-events]
      (events/dispatch-event! game-state event))
    (let [expanded-tree (build-layout game-state)
          dropdown* (find-dropdown-by-id expanded-tree :hyperlane-color)
          red-option (some #(when (= :red (:value %)) %)
                           (get-in dropdown* [:layout :dropdown :options]))]
      (is red-option)
      (let [option-bounds (:bounds red-option)
            option-events (interaction/click->events expanded-tree
                                                     (+ (:x option-bounds) 4)
                                                     (+ (:y option-bounds) 4))]
        (doseq [event option-events]
          (events/dispatch-event! game-state event))
        (is (= :red (:color-scheme (state/hyperlane-settings game-state))))
        (is (false? (state/dropdown-open? game-state :hyperlane-color)))))))

(deftest voronoi-color-dropdown-selects-scheme
  (let [game-state (atom (state/create-game-state))
        initial-tree (build-layout game-state)
        dropdown (find-dropdown-by-id initial-tree :voronoi-color)
        header (get-in dropdown [:layout :dropdown :header])
        toggle-events (interaction/click->events initial-tree
                                                 (+ (:x header) 4)
                                                 (+ (:y header) 4))]
    (doseq [event toggle-events]
      (events/dispatch-event! game-state event))
    (let [expanded-tree (build-layout game-state)
          dropdown* (find-dropdown-by-id expanded-tree :voronoi-color)
          degree-option (some #(when (= :by-degree (:value %)) %)
                              (get-in dropdown* [:layout :dropdown :options]))]
      (is degree-option)
      (let [option-bounds (:bounds degree-option)
            option-events (interaction/click->events expanded-tree
                                                     (+ (:x option-bounds) 4)
                                                     (+ (:y option-bounds) 4))]
        (doseq [event option-events]
          (events/dispatch-event! game-state event))
        (is (= :by-degree (:color-scheme (state/voronoi-settings game-state))))
        (is (false? (state/dropdown-open? game-state :voronoi-color)))))))

(deftest performance-overlay-visibility-and-expanded-toggle
  (let [game-state (atom (state/create-game-state))
        tree (build-layout game-state)
        hide-button (find-button-with-event tree [:ui/perf-toggle-visible])]
    (is hide-button)
    (let [bounds (layout/bounds hide-button)
          click-x (+ (:x bounds) (/ (:width bounds) 2.0))
          click-y (+ (:y bounds) (/ (:height bounds) 2.0))
          click-events (interaction/click->events tree click-x click-y)]
      (is (seq click-events))
      (doseq [event click-events]
        (events/dispatch-event! game-state event)))
    (is (false? (state/performance-overlay-visible? game-state)))
    (let [hidden-tree (build-layout game-state)
          show-button (find-button-with-event hidden-tree [:ui/perf-toggle-visible])
          bounds (layout/bounds show-button)
          click-x (+ (:x bounds) (/ (:width bounds) 2.0))
          click-y (+ (:y bounds) (/ (:height bounds) 2.0))
          click-events (interaction/click->events hidden-tree click-x click-y)]
      (is (seq click-events))
      (doseq [event click-events]
        (events/dispatch-event! game-state event)))
    (is (true? (state/performance-overlay-visible? game-state)))
    (let [expanded-tree (build-layout game-state)
          collapse-button (find-button-with-event expanded-tree [:ui/perf-toggle-expanded])
          bounds (layout/bounds collapse-button)
          click-x (+ (:x bounds) (/ (:width bounds) 2.0))
          click-y (+ (:y bounds) (/ (:height bounds) 2.0))
          click-events (interaction/click->events expanded-tree click-x click-y)]
      (is (seq click-events))
      (doseq [event click-events]
        (events/dispatch-event! game-state event)))
    (is (false? (state/performance-overlay-expanded? game-state)))))

(deftest performance-overlay-reset-metrics
  (let [game-state (atom (state/create-game-state))]
    (swap! game-state assoc-in [:metrics :performance]
           {:fps-history [1.0]
            :frame-time-history [10.0]
            :last-sample-time 5.0
            :latest {:fps 120.0}})
    (let [tree (build-layout game-state)
          reset-button (find-button-with-event tree [:metrics/reset-performance])
          bounds (layout/bounds reset-button)
          click-x (+ (:x bounds) 4.0)
          click-y (+ (:y bounds) (/ (:height bounds) 2.0))
          click-events (interaction/click->events tree click-x click-y)]
      (is (seq click-events))
      (doseq [event click-events]
        (events/dispatch-event! game-state event)))
    (is (= state/default-performance-metrics
           (get-in @game-state [:metrics :performance])))))

(deftest performance-overlay-renders-fps-chart
  (let [game-state (atom (state/create-game-state))]
    (swap! game-state assoc-in [:metrics :performance :fps-history] [30.0 45.0 60.0])
    (let [tree (build-layout game-state)
          chart (find-bar-chart tree)
          data (get-in chart [:layout :bar-chart])]
      (is chart)
      (is (= [30.0 45.0 60.0] (:values data))))))

(deftest star-inspector-hidden-when-no-selection
  (let [game-state (atom (state/create-game-state))
        tree (build-layout game-state)
        window (find-window-with-title tree "Star Inspector")]
    (is (nil? window))))

(deftest star-inspector-active-with-selection
  (let [game-state (atom (state/create-game-state))
        _ (state/reset-world-ids! game-state)
        star-id (fixtures/add-test-star! game-state 200.0 300.0)]
    (state/set-selection! game-state {:star-id star-id})
    (let [tree (build-layout game-state)
          window (find-window-with-title tree "Star Inspector")
          bounds (layout/bounds window)
          panel-width (:width star-inspector/default-panel-bounds)
          scale (state/ui-scale game-state)
          logical-width (/ (:width test-viewport) scale)
          base-x (max 24.0 (- logical-width panel-width 24.0))
          zoom-button (find-button-with-event tree [:ui/zoom-to-selected-star {:zoom 2.4}])
          clear-button (find-button-with-event tree [:ui/clear-selection])]
      (is window)
      (is (= base-x (:x bounds)))
      (is zoom-button)
      (is clear-button))))
