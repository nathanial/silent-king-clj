# silent-king.ui.controls (legacy)

This namespace previously contained the main **control panel UI** built on top of the widget ECS.

## Responsibilities (old system)

- Defined `create-control-panel!`, which built a `:panel` widget containing:
  - A title label (`"Silent King Controls"`).
  - A `"Zoom"` label and a slider bound to the camera zoom range.
  - Buttons to toggle hyperlanes and minimap visibility.
  - A `"Reset Camera"` button that reset zoom and pan.
  - A stats label that was updated each frame with FPS, star counts, and hyperlane counts.
- Defined `create-minimap!`, which created a `:minimap` widget anchored in the bottom-right corner.
- Exposed helpers to keep UI in sync with core state:
  - `update-stats-label!` rebuilt the stats text from metrics.
  - `update-zoom-slider!` synchronized the slider thumb with current camera zoom.

## Notes for Reactified rewrite

When rebuilding this in the new React-style system, we will want:

- A **pure control-panel component** that:
  - Takes camera and metrics props.
  - Emits events like `:ui/reset-camera`, `:ui/toggle-hyperlanes`, `:ui/toggle-minimap`, and `:ui/set-zoom`.
- A **minimap toggle** and stats display that read directly from the new metrics and camera state, rather than mutating widget entities in-place.

