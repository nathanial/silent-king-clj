(ns silent-king.ui.theme
  "Centralized design system for Silent King UI components.
   Provides color palette, typography, spacing, shadows, and component dimensions.")

;; =============================================================================
;; Color Palette
;; =============================================================================

(def colors
  "Color palette for the application. Uses ARGB hex format (0xAARRGGBB)."
  {:background
   {:panel-primary      0xCC1A1A1A  ; Semi-transparent dark gray (main panels)
    :panel-secondary    0xCC222222  ; Slightly lighter dark gray
    :panel-inspector    0xE61A1A1A  ; More opaque dark gray (inspector)
    :header             0xDD1A1A1A  ; Header background
    :body               0xDD222222  ; Body background
    :transparent        0x00000000  ; Fully transparent
    :scroll-view        0x22181818  ; Very subtle dark background
    :button-primary     0xFF2A2A2A  ; Dark button background
    :button-neutral     0xFF444444} ; Medium gray button

   :text
   {:primary   0xFFFFFFFF  ; White (primary text)
    :secondary 0xFFEEEEEE  ; Light gray (secondary text)
    :tertiary  0xFFAAAAAA  ; Medium gray (muted/tertiary text)
    :muted     0xFFCCCCCC  ; Light gray (labels)
    :accent    0xFF3DD598} ; Bright green (accent/success)

   :interactive
   {:primary   0xFF3366CC  ; Blue (primary action)
    :secondary 0xFF6699FF  ; Light blue (secondary action)
    :tertiary  0xFF66CCFF  ; Lighter blue (tertiary action)
    :track-on  0xFF3DD598  ; Green (active track)
    :track-off 0xFF555555  ; Gray (inactive track)
    :track     0xFF444444} ; Default track color

   :shadow
   {:dark-80 0x80000000  ; 50% opacity black
    :dark-66 0x66000000  ; 40% opacity black
    :dark-a0 0xA0000000} ; 62.5% opacity black

   :grid
   {:lines 0x33444444}}) ; Very subtle grid lines

;; =============================================================================
;; Typography
;; =============================================================================

(def typography
  "Font size scale for text elements."
  {:title      {:size 20}  ; Panel titles, large headings
   :heading    {:size 18}  ; Section headings
   :subheading {:size 16}  ; Subsection headings, button text
   :body       {:size 14}  ; Body text, labels
   :small      {:size 13}  ; Small text
   :tiny       {:size 12}}) ; Smallest text (captions, hints)

;; =============================================================================
;; Spacing
;; =============================================================================

(def spacing
  "Consistent spacing scale for margins, padding, and gaps."
  {:xs  4   ; Extra small spacing
   :sm  8   ; Small spacing
   :md  12  ; Medium spacing (most common)
   :lg  16  ; Large spacing
   :xl  20}) ; Extra large spacing

;; =============================================================================
;; Border Radius
;; =============================================================================

(def border-radius
  "Border radius values for rounded corners."
  {:sm   4.0   ; Small radius (buttons)
   :md   6.0   ; Medium radius (small buttons)
   :lg   8.0   ; Large radius (panels, cards)
   :xl   12.0  ; Extra large radius (main panels)
   :xxl  14.0}) ; Extra extra large radius (inspector)

;; =============================================================================
;; Shadows
;; =============================================================================

(def shadows
  "Pre-configured shadow styles for elevation."
  {:sm  {:offset-x 0 :offset-y 4 :blur 10 :color (:dark-66 (:shadow colors))}
   :md  {:offset-x 0 :offset-y 4 :blur 12 :color (:dark-80 (:shadow colors))}
   :lg  {:offset-x 0 :offset-y 4 :blur 16 :color (:dark-a0 (:shadow colors))}
   :xl  {:offset-x 0 :offset-y 8 :blur 20 :color (:dark-80 (:shadow colors))}})

;; =============================================================================
;; Component Dimensions
;; =============================================================================

(def panel-dimensions
  "Standard dimensions for UI panels."
  {:control
   {:width     280.0
    :height    264.0
    :margin    20.0}

   :settings
   {:width     320.0
    :collapsed 64.0
    :expanded  320.0
    :margin    20.0}

   :inspector
   {:width     320.0
    :height    520.0
    :collapsed 100.0
    :margin    20.0}

   :dashboard
   {:width     320.0
    :collapsed 60.0
    :expanded  320.0
    :margin    12.0
    :viewport-margin 16.0
    :header-height   48.0
    :chart-height    80.0}})

(def widget-sizes
  "Standard sizes for common widgets."
  {:button
   {:standard {:width 260 :height 36}
    :compact  {:width 60  :height 32}
    :small    {:width 40  :height 32}
    :medium   {:width 120 :height 32}
    :large    {:width 280 :height 36}}

   :label
   {:standard {:height 22}
    :title    {:height 28}
    :small    {:height 20}
    :medium   {:height 24}
    :large    {:height 30}}

   :slider
   {:height 24
    :width-sm 150
    :width-md 260
    :width-lg 280}

   :preview
   {:width 110
    :height 110}

   :scroll-view
   {:default-height 200}

   :minimap
   {:width 250
    :height 250}

   :text-field
   {:small  {:height 34 :width 280}
    :medium {:height 38 :width 280}
    :large  {:height 48 :width 280}}})

;; =============================================================================
;; Animation
;; =============================================================================

(def animation
  "Animation timing and speed values."
  {:slide-speed  10.0  ; Panel slide animation speed
   :fade-speed   8.0   ; Fade animation speed
   :history-limit 60}) ; Number of frames to keep in history

;; =============================================================================
;; Range Configurations
;; =============================================================================

(def ranges
  "Common range configurations for sliders and inputs."
  {:opacity {:min 0.0 :max 1.0 :step 0.05}
   :speed   {:min 0.2 :max 2.5 :step 0.1}
   :width   {:min 0.5 :max 2.5 :step 0.1}
   :zoom    {:min 0.4 :max 10.0 :step 0.1 :initial 1.0}})

;; =============================================================================
;; Helper Functions
;; =============================================================================

(defn get-color
  "Retrieve a color value from the color palette using a path.
   Example: (get-color :background :panel-primary) => 0xCC1A1A1A"
  [category key]
  (get-in colors [category key]))

(defn get-font-size
  "Retrieve a font size from the typography scale.
   Example: (get-font-size :body) => 14"
  [key]
  (get-in typography [key :size]))

(defn get-spacing
  "Retrieve a spacing value.
   Example: (get-spacing :md) => 12"
  [key]
  (get spacing key))

(defn get-border-radius
  "Retrieve a border radius value.
   Example: (get-border-radius :lg) => 8.0"
  [key]
  (get border-radius key))

(defn get-shadow
  "Retrieve a shadow configuration.
   Example: (get-shadow :md) => {:offset-x 0 :offset-y 4 :blur 12 :color 0x80000000}"
  [key]
  (get shadows key))

(defn get-panel-dimension
  "Retrieve a panel dimension.
   Example: (get-panel-dimension :control :width) => 280.0"
  [panel key]
  (get-in panel-dimensions [panel key]))

(defn get-widget-size
  "Retrieve a widget size configuration.
   Example: (get-widget-size :button :standard) => {:width 260 :height 36}"
  [widget key]
  (get-in widget-sizes [widget key]))

;; =============================================================================
;; Convenience Aliases
;; =============================================================================

(def c colors)           ; Short alias for colors
(def t typography)       ; Short alias for typography
(def s spacing)          ; Short alias for spacing
(def br border-radius)   ; Short alias for border-radius
(def sh shadows)         ; Short alias for shadows
(def pd panel-dimensions) ; Short alias for panel-dimensions
(def ws widget-sizes)    ; Short alias for widget-sizes
