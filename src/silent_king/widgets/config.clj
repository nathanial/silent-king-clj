(ns silent-king.widgets.config
  "Global widget configuration and constants")

;; =============================================================================
;; UI Scale
;; =============================================================================

(def ^:const ui-scale
  "Global UI scale factor. All widget rendering and hit testing is scaled by this amount.
  Uses Skia canvas scaling for efficient uniform scaling without modifying individual widget parameters.

  Values:
  - 1.0 = normal size (100%)
  - 1.5 = 150% larger
  - 2.0 = 200% larger (double size)

  This is useful for:
  - High DPI displays
  - User accessibility preferences
  - Different screen sizes"
  1.5)
