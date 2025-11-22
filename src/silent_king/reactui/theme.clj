(ns silent-king.reactui.theme
  "Centralized color theme for the ReactUI components.")

(def colors
  {;; Common
   :accent            0xFF9CDCFE
   :text              0xFFCBCBCB
   :text-muted        0xFFB3B3B3
   :text-muted-alt    0xFF84889A ;; Used in star-inspector, performance-overlay

   ;; Panel Backgrounds
   :panel-bg          0xCC171B25 ;; control, voronoi, hyperlane
   :panel-bg-star     0xCC12151E
   :panel-bg-perf     0xCC10131C
   
   ;; Section Backgrounds
   :section-bg        0xFF2D2F38
   :section-bg-alt    0xFF1F2330 ;; star section, perf section, options

   ;; Buttons
   :btn-bg-enabled    0xFF3C4456
   :btn-bg-stars      0xFF343844
   :btn-bg-hyperlanes 0xFF2D2F38
   :btn-bg-voronoi    0xFF242734
   
   ;; Dock
   :dock-bg           0xFF181818
   :dock-border       0xFF505050
   :dock-tab-active   0xFF454545
   :dock-tab-inactive 0xFF2A2A2A
   :dock-text         0xFFCCCCCC
   :dock-text-active  0xFFFFFFFF
   :dock-splitter     0xFF505050
   :dock-splitter-hover 0xFF707070

   ;; Misc
   :option-selected-text 0xFF0F111A
   :chart-bg          0x33171B25})
