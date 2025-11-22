(ns silent-king.reactui.primitives.dock
  "Dock container primitive: renders a tabbed dock area with a resizable splitter."
  (:require [silent-king.reactui.core :as core]
            [silent-king.reactui.events :as ui-events]
            [silent-king.reactui.layout :as layout]
            [silent-king.reactui.render :as render]
            [silent-king.reactui.interaction :as interaction]
            [silent-king.render.commands :as commands]))

(set! *warn-on-reflection* true)

;; =============================================================================
;; Constants & Styles
;; =============================================================================

(def ^:const tab-height 30.0)
(def ^:const splitter-size 6.0)
(def ^:const tab-padding 8.0)

(def active-tab-color 0xFF303030)
(def inactive-tab-color 0xFF202020)
(def bg-color 0xFF181818)
(def border-color 0xFF404040)
(def splitter-color 0xFF505050)
(def splitter-hover-color 0xFF707070)
(def text-color 0xFFCCCCCC)
(def active-text-color 0xFFFFFFFF)

;; =============================================================================
;; Normalization & Layout
;; =============================================================================

(defmethod core/normalize-tag :dock-container
  [[_ props & children]]
  ;; We expect children to be the *content* of the active window.
  ;; Props should contain :tabs [{:id ... :title ...}]
  ((core/branch-normalizer :dock-container) props children))

(defmethod layout/layout-node :dock-container
  [node context]
  ;; The node already has bounds assigned by the parent (calculate-dock-layout).
  ;; We just need to layout the children into the content area.
  (let [bounds (layout/resolve-bounds node context)
        {:keys [width height]} bounds
        
        ;; Content area is full bounds minus tab bar
        ;; We assume tabs are at the bottom for Top/Bottom docks? 
        ;; Or always at top? Let's stick to TOP of the dock for now.
        content-y (+ (:y bounds) tab-height)
        content-h (max 0.0 (- height tab-height))
        
        content-bounds {:x (:x bounds)
                        :y content-y
                        :width width
                        :height content-h}
        
        child-context (assoc context :viewport content-bounds :bounds content-bounds)
        
        children (mapv #(layout/layout-node % child-context) (:children node))]
    
    (assoc node
           :layout {:bounds bounds
                    :content-bounds content-bounds}
           :children children)))

;; =============================================================================
;; Interaction Helpers
;; =============================================================================

(defn- get-splitter-rect
  "Calculate splitter rect based on dock side."
  [bounds side]
  (let [{:keys [x y width height]} bounds]
    (case side
      :left   {:x (+ x width (- (/ splitter-size 2))) :y y :width splitter-size :height height}
      :right  {:x (- x (/ splitter-size 2)) :y y :width splitter-size :height height}
      :top    {:x x :y (+ y height (- (/ splitter-size 2))) :width width :height splitter-size}
      :bottom {:x x :y (- y (/ splitter-size 2)) :width width :height splitter-size}
      {:x 0 :y 0 :width 0 :height 0})))

(defn- hit-test-splitter?
  [bounds side px py]
  (let [rect (get-splitter-rect bounds side)
        expansion 4.0 ;; Make hit area slightly larger
        l (- (:x rect) expansion)
        r (+ (:x rect) (:width rect) expansion)
        t (- (:y rect) expansion)
        b (+ (:y rect) (:height rect) expansion)]
    (and (>= px l) (<= px r) (>= py t) (<= py b))))

(defn- get-tab-rects
  "Calculate rects for each tab. Returns list of {:id ... :rect ...}"
  [bounds tabs]
  (let [start-x (:x bounds)
        y (:y bounds)]
    (loop [current-x start-x
           remaining tabs
           acc []]
      (if (empty? remaining)
        acc
        (let [tab (first remaining)
              ;; Estimate width roughly for now
              text-w (layout/estimate-text-width (:title tab) 14.0)
              w (+ text-w (* tab-padding 2))
              rect {:x current-x :y y :width w :height tab-height}]
          (recur (+ current-x w 1.0) ;; 1px gap
                 (rest remaining)
                 (conj acc (assoc tab :rect rect))))))))

(defn- hit-test-tab
  [bounds tabs px py]
  (let [rects (get-tab-rects bounds tabs)]
    (some (fn [{:keys [rect id]}]
            (when (and (>= px (:x rect))
                       (< px (+ (:x rect) (:width rect)))
                       (>= py (:y rect))
                       (< py (+ (:y rect) (:height rect))))
              id))
          rects)))

;; =============================================================================
;; Rendering
;; =============================================================================

(defn plan-dock-container
  [context node]
  (let [{:keys [bounds]} (:layout node)
        {:keys [side tabs active-id]} (:props node)
        
        ;; Background
        cmds [(commands/rect {:rect bounds :color bg-color})
              (commands/rect {:rect bounds :color border-color :style :stroke :width 1.0})]
        
        ;; Tabs
        tab-cmds (mapcat (fn [{:keys [id title rect]}]
                           (let [active? (= id active-id)
                                 bg (if active? active-tab-color inactive-tab-color)
                                 fg (if active? active-text-color text-color)]
                             [(commands/rect {:rect rect :color bg})
                              (commands/text {:text (str title)
                                              :x (+ (:x rect) tab-padding)
                                              :y (+ (:y rect) 20.0) ;; Baseline approx
                                              :size 14.0
                                              :color fg})]))
                         (get-tab-rects bounds tabs))
        
        ;; Splitter Visual
        splitter-rect (get-splitter-rect bounds side)
        splitter-cmd (commands/rect {:rect splitter-rect :color splitter-color})]
    
    (into (conj cmds splitter-cmd) tab-cmds)))

(defmethod render/plan-node :dock-container
  [context node]
  (plan-dock-container context node))

;; =============================================================================
;; Interaction Handling
;; =============================================================================

(defn handle-dock-pointer-down!
  [node game-state x y]
  (let [bounds (layout/bounds node)
        {:keys [side tabs on-resize on-select-tab]} (:props node)]
    
    ;; Check Splitter
    (if (hit-test-splitter? bounds side x y)
      (do
        (core/set-active-interaction! node :dock-resize {:start-x x :start-y y :start-bounds bounds})
        (core/capture-node! node)
        true)
      
      ;; Check Tabs
      (if-let [tab-id (hit-test-tab bounds tabs x y)]
        (do
          ;; Select the tab immediately
          (when on-select-tab
            (ui-events/dispatch-event! game-state (conj on-select-tab tab-id)))
          
          ;; Start potential drag
          (core/set-active-interaction! node :dock-tab-drag {:start-x x :start-y y :tab-id tab-id})
          (core/capture-node! node)
          true)
        false))))

(defn handle-dock-pointer-drag!
  [node game-state x y]
  (let [interaction (core/active-interaction)
        kind (:type interaction)
        {:keys [side on-resize]} (:props node)]
    
    (cond
      (= kind :dock-resize)
      (let [{:keys [width height]} (:start-bounds (:value interaction))
            dx (- x (:start-x (:value interaction)))
            dy (- y (:start-y (:value interaction)))
            
            new-size (case side
                       :left  (+ width dx)
                       :right (+ width (- dx)) ;; Drag left increases width
                       :top   (+ height dy)
                       :bottom (+ height (- dy)) ;; Drag up increases height
                       0.0)]
        
        (when on-resize
          (ui-events/dispatch-event! game-state (conj on-resize new-size)))
        true)
      
      (= kind :dock-tab-drag)
      (let [start-x (:start-x (:value interaction))
            start-y (:start-y (:value interaction))
            dist (Math/hypot (- x start-x) (- y start-y))]
        (when (> dist 5.0)
          ;; Threshold exceeded -> UNDOCK
          (let [tab-id (:tab-id (:value interaction))]
            ;; We place the window slightly offset from mouse so it feels like we are holding the tab/header
            (ui-events/dispatch-event! game-state [:ui.window/undock tab-id {:x (- x 20.0) :y (- y 15.0)}])
            (core/clear-active-interaction!)
            (core/release-capture!)))
        true)
      
      :else false)))

(defmethod core/pointer-down! :dock-container
  [node game-state x y]
  (handle-dock-pointer-down! node game-state x y))

(defmethod core/pointer-drag! :dock-container
  [node game-state x y]
  (handle-dock-pointer-drag! node game-state x y))

(defmethod core/pointer-up! :dock-container
  [node game-state x y]
  (core/clear-active-interaction!)
  (core/release-capture!))