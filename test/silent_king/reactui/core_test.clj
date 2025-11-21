(ns silent-king.reactui.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [silent-king.minimap.math :as minimap-math]
            [silent-king.reactui.core :as reactui]
            [silent-king.reactui.events :as ui-events]
            [silent-king.reactui.interaction :as interaction]
            [silent-king.reactui.layout :as layout]
            [silent-king.reactui.primitives]
            [silent-king.state :as state]))

(deftest render-ui-tree-smoke
  (let [tree [:vstack {:bounds {:x 5 :y 5 :width 180}
                       :padding {:all 6}
                       :gap 2}
              [:label {:text "Demo"}]
              [:label {:text "Pipeline"}]]
        {:keys [layout-tree]} (reactui/render-ui-tree
                               {:canvas nil
                                :tree tree
                                :viewport {:x 0 :y 0 :width 200 :height 200}})
        root-bounds (layout/bounds layout-tree)]
    (testing "layout succeeds"
      (is (= :vstack (:type layout-tree)))
      (is (= 2 (count (:children layout-tree))))
      (is (map? root-bounds))
      (is (= 5.0 (:x root-bounds)))
      (is (= 180.0 (:width root-bounds))))))

(defn- find-node
  [node type]
  (when node
    (if (= (:type node) type)
      node
      (some #(find-node % type) (:children node)))))

(deftest slider-pointer-capture-updates-zoom
  (reactui/release-capture!)
  (let [game-state (atom (state/create-game-state))
        tree [:slider {:bounds {:x 0 :y 0 :width 200 :height 32}
                       :min 0.4
                       :max 4.0
                       :step 0.1
                       :value 0.4
                       :on-change [:ui/set-zoom]}]
        {:keys [layout-tree]} (reactui/render-ui-tree {:canvas nil
                                                       :tree tree
                                                       :viewport {:x 0 :y 0 :width 200 :height 40}})
        slider-node (find-node layout-tree :slider)
        track (get-in slider-node [:layout :slider :track])
        scale (state/ui-scale game-state)
        down-x (* scale (+ (:x track) 2.0))
        down-y (+ (:y track) (/ (:height track) 2.0))
        drag-x (* scale (+ (:x track) (:width track)))]
    (is (true? (reactui/handle-pointer-down! game-state down-x down-y)))
    (is (= 0.4 (get-in @game-state [:camera :zoom])))
    (is (true? (reactui/handle-pointer-drag! game-state drag-x down-y)))
    (reactui/handle-pointer-up! game-state drag-x down-y)
    (is (= 4.0 (get-in @game-state [:camera :zoom])))))

(defn- approx=
  [a b]
  (< (Math/abs (- (double a) (double b))) 1e-6))

(defn- approx-point=
  [expected actual]
  (and (approx= (:x expected) (:x actual))
       (approx= (:y expected) (:y actual))))

(defn- contains-point?
  [{:keys [x y width height]} px py]
  (and (number? x) (number? y)
       (number? width) (number? height)
       (>= px x)
       (<= px (+ x width))
       (>= py y)
       (<= py (+ y height))))

(def ^:private minimap-world-bounds
  {:min-x -400.0
   :max-x 400.0
   :min-y -400.0
   :max-y 400.0})

(def ^:private minimap-viewport-rect
  {:x -80.0
   :y -80.0
   :width 160.0
   :height 160.0})

(defn- layout-minimap!
  ([] (layout-minimap! {}))
  ([overrides]
   (let [props (merge {:bounds {:x 0.0 :y 0.0 :width 200.0 :height 200.0}
                       :world-bounds minimap-world-bounds
                       :viewport-rect minimap-viewport-rect
                       :stars []}
                      overrides)]
     (let [{:keys [layout-tree]} (reactui/render-ui-tree {:canvas nil
                                                          :tree [:minimap props]
                                                          :viewport {:x 0 :y 0 :width 400 :height 400}})]
       layout-tree))))

(deftest minimap-window-header-drag-still-works
  (reactui/release-capture!)
  (let [game-state (atom (state/create-game-state))
        _ (state/set-ui-scale! game-state 1.0)
        tree [:window {:title "Mini"
                       :bounds {:x 100.0 :y 100.0 :width 220.0 :height 240.0}
                       :resizable? true
                       :on-change-bounds [:ui.window/set-bounds :mini-window]
                       :on-toggle-minimized [:noop]}
              [:minimap {:bounds {:width 200.0 :height 200.0}
                         :world-bounds minimap-world-bounds
                         :viewport-rect minimap-viewport-rect
                         :visible? true
                         :stars []}]]
        {:keys [layout-tree]} (reactui/render-ui-tree {:canvas nil
                                                       :tree tree
                                                       :viewport {:x 0 :y 0 :width 640 :height 480}})
        window-node (find-node layout-tree :window)
        header (get-in window-node [:layout :window :header])
        start-x (+ (:x header) (/ (:width header) 2.0))
        start-y (+ (:y header) (/ (:height header) 2.0))
        drag-x (+ start-x 30.0)
        drag-y (+ start-y 20.0)
        events (atom [])]
    (is (= :window (:type (interaction/node-at layout-tree start-x start-y)))
        "Header hit should resolve to window node for dragging")
    (let [orig-dispatch ui-events/dispatch-event!]
      (with-redefs [ui-events/dispatch-event! (fn [state event]
                                                (swap! events conj event)
                                                (orig-dispatch state event))]
        (is (true? (reactui/handle-pointer-down! game-state start-x start-y)))
        (is (true? (reactui/handle-pointer-drag! game-state drag-x drag-y)))
        (reactui/handle-pointer-up! game-state drag-x drag-y)))
    (is (some #(= :ui.window/set-bounds (first %)) @events))))

(deftest minimap-click-recenters-camera
  (reactui/release-capture!)
  (let [game-state (atom (state/create-game-state))
        _ (state/set-ui-scale! game-state 1.0)
        layout-tree (layout-minimap!)
        bounds (layout/bounds layout-tree)
        click-x (+ (:x bounds) 48.0)
        click-y (+ (:y bounds) 72.0)
        world-bounds (:world-bounds (:props layout-tree))
        expected (minimap-math/minimap->world {:x click-x :y click-y}
                                              world-bounds
                                              bounds)
        events (atom [])]
    (with-redefs [ui-events/dispatch-event! (fn [_ event]
                                              (swap! events conj event))]
      (is (true? (reactui/handle-pointer-down! game-state click-x click-y)))
      (reactui/handle-pointer-up! game-state click-x click-y))
    (is (= [[:camera/pan-to-world expected]] @events))))

(deftest minimap-viewport-drag-preserves-offset
  (reactui/release-capture!)
  (let [game-state (atom (state/create-game-state))
        _ (state/set-ui-scale! game-state 1.0)
        layout-tree (layout-minimap!)
        bounds (layout/bounds layout-tree)
        world-bounds (:world-bounds (:props layout-tree))
        viewport (:viewport-rect (:props layout-tree))
        viewport-bounds (minimap-math/viewport->minimap-rect viewport world-bounds bounds)
        down-x (+ (:x viewport-bounds) 6.0)
        down-y (+ (:y viewport-bounds) 5.0)
        drag-x (+ down-x 24.0)
        drag-y (+ down-y 12.0)
        world-down (minimap-math/minimap->world {:x down-x :y down-y} world-bounds bounds)
        world-drag (minimap-math/minimap->world {:x drag-x :y drag-y} world-bounds bounds)
        center {:x (+ (:x viewport) (/ (:width viewport) 2.0))
                :y (+ (:y viewport) (/ (:height viewport) 2.0))}
        delta {:x (- (:x world-drag) (:x world-down))
               :y (- (:y world-drag) (:y world-down))}
        expected-center center
        expected-drag {:x (+ (:x expected-center) (:x delta))
                       :y (+ (:y expected-center) (:y delta))}
        events (atom [])]
    (with-redefs [ui-events/dispatch-event! (fn [_ event]
                                              (swap! events conj event))]
      (reactui/handle-pointer-down! game-state down-x down-y)
      (reactui/handle-pointer-drag! game-state drag-x drag-y)
      (reactui/handle-pointer-up! game-state drag-x drag-y))
    (is (= 2 (count @events)) "Expected pointer down + drag to emit two pans")
    (let [[down-event drag-event] @events]
      (is (approx-point= expected-center (second down-event)))
      (is (approx-point= expected-drag (second drag-event))))))

(deftest minimap-background-drag-delegates-window
  (reactui/release-capture!)
  (let [game-state (atom (state/create-game-state))
        _ (state/set-ui-scale! game-state 1.0)
        tree [:window {:title "Mini"
                       :bounds {:x 80.0 :y 80.0 :width 220.0 :height 240.0}
                       :on-change-bounds [:ui.window/set-bounds :mini-window]
                       :on-toggle-minimized [:noop]}
              [:minimap {:bounds {:width 200.0 :height 200.0}
                         :world-bounds minimap-world-bounds
                         :viewport-rect minimap-viewport-rect
                         :stars []}]]
        {:keys [layout-tree]} (reactui/render-ui-tree {:canvas nil
                                                       :tree tree
                                                       :viewport {:x 0 :y 0 :width 640 :height 480}})
        window-node (find-node layout-tree :window)
        minimap-node (find-node layout-tree :minimap)
        widget-bounds (layout/bounds minimap-node)
        world-bounds (:world-bounds (:props minimap-node))
        viewport (:viewport-rect (:props minimap-node))
        viewport-bounds (minimap-math/viewport->minimap-rect viewport world-bounds widget-bounds)
        candidate-points [{:x (+ (:x widget-bounds) 12.0)
                           :y (+ (:y widget-bounds) 12.0)}
                          {:x (- (+ (:x widget-bounds) (:width widget-bounds)) 12.0)
                           :y (+ (:y widget-bounds) 12.0)}
                          {:x (+ (:x widget-bounds) 12.0)
                           :y (- (+ (:y widget-bounds) (:height widget-bounds)) 12.0)}
                          {:x (- (+ (:x widget-bounds) (:width widget-bounds)) 12.0)
                           :y (- (+ (:y widget-bounds) (:height widget-bounds)) 12.0)}]
        {:keys [x y]} (or (some (fn [{:keys [x y] :as point}]
                                  (when (or (nil? viewport-bounds)
                                            (not (contains-point? viewport-bounds x y)))
                                    point))
                                candidate-points)
                          {:x (+ (:x widget-bounds) 12.0)
                           :y (+ (:y widget-bounds) 12.0)})
        drag1-x (+ x 10.0)
        drag1-y y
        drag2-x (+ drag1-x 20.0)
        drag2-y (+ drag1-y 15.0)
        events (atom [])]
    (let [orig-dispatch ui-events/dispatch-event!]
      (with-redefs [ui-events/dispatch-event! (fn [state event]
                                                (swap! events conj event)
                                                (orig-dispatch state event))]
        (reactui/handle-pointer-down! game-state x y)
        (reactui/handle-pointer-drag! game-state drag1-x drag1-y)
        (reactui/handle-pointer-drag! game-state drag2-x drag2-y)
        (reactui/handle-pointer-up! game-state drag2-x drag2-y)))
    (is (some #(= :ui.window/set-bounds (first %)) @events))
    (let [updated-bounds (state/window-bounds game-state :mini-window {:x 0 :y 0 :width 220 :height 240})]
      (is (> (:x updated-bounds) (:x (layout/bounds window-node)))))))
