(ns silent-king.reactui.docked-window-test
  (:require [clojure.test :refer :all]
            [silent-king.reactui.layout :as layout]
            [silent-king.reactui.primitives :as primitives]))

(deftest docked-window-header-visibility-test
  (testing "Window docked in bottom dock has visible header within viewport"
    (let [viewport {:x 0.0 :y 0.0 :width 1000.0 :height 800.0}
          dock-height 200.0
          dock-y (- 800.0 dock-height) ;; 600.0
          
          dock-node {:type :dock-container
                     :props {:bounds {:x 0.0 
                                      :y dock-y 
                                      :width 1000.0 
                                      :height dock-height}
                             :side :bottom
                             :tabs [{:id :w1 :title "Window 1"}]}
                     :children [{:type :window
                                 :props {:title "Window 1"}
                                 :children []}]}
          
          ;; Run layout pass
          layout-context {:viewport viewport
                          :bounds viewport}
          result-node (layout/layout-node dock-node layout-context)
          
          ;; Extract window layout
          window-node (first (:children result-node))
          window-layout (:layout window-node)
          header-bounds (get-in window-layout [:window :header])
          window-bounds (:bounds window-layout)]

      (is (some? window-node) "Window node should exist in layout result")
      (is (some? header-bounds) "Window should have header bounds")

      ;; Check Window Bounds
      ;; Dock tab height is 30.0 (defined in dock.clj)
      ;; Dock content starts at dock-y + 30.0 = 630.0
      (is (= 630.0 (:y window-bounds)) "Window should start below dock tabs")
      (is (= 1000.0 (:width window-bounds)) "Window should take full width of dock")
      
      ;; Check Header Bounds
      ;; Header should be at the top of the window (which is at 630.0)
      (is (= 630.0 (:y header-bounds)) "Header should be at top of window content")
      
      ;; Check Visibility
      (let [header-bottom (+ (:y header-bounds) (:height header-bounds))]
        (is (< header-bottom 800.0) "Header bottom should be within viewport height")
        (is (>= (:y header-bounds) 0.0) "Header top should be within viewport"))))

  (testing "Window docked in top dock ignores explicit bounds and aligns to content"
    (let [viewport {:x 0.0 :y 0.0 :width 1000.0 :height 800.0}
          dock-height 200.0
          
          ;; Simulate a window that was dropped at y=0 (explicit bounds)
          dock-node {:type :dock-container
                     :props {:bounds {:x 0.0 
                                      :y 0.0 
                                      :width 1000.0 
                                      :height dock-height}
                             :side :top
                             :tabs [{:id :w1 :title "Window 1"}]}
                     :children [{:type :window
                                 :props {:title "Window 1"
                                         :bounds {:x 0.0 :y 0.0 :width 400.0 :height 400.0}}
                                 :children []}]}
          
          layout-context {:viewport viewport
                          :bounds viewport}
          result-node (layout/layout-node dock-node layout-context)
          
          window-node (first (:children result-node))
          window-bounds (get-in window-node [:layout :bounds])]

      ;; Dock content should start at y = 30.0 (tab height)
      ;; The window should respect this, ignoring its explicit :y 0.0
      (is (= 30.0 (:y window-bounds)) "Window Y should align with dock content (30.0), not explicit prop (0.0)")
      (is (= 1000.0 (:width window-bounds)) "Window width should fill dock (1000.0), not explicit prop (400.0)"))))
