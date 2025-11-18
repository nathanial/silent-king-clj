# silent-king.ui.performance-dashboard (legacy)

This namespace previously implemented the **performance dashboard overlay**: a draggable, pinnable panel showing FPS, frame-time, entity counts, and memory usage.

## Responsibilities (old system)

- Managed `[:ui :performance-dashboard]` state (position, pinned/expanded flags, history length, etc.) with `clojure.spec`.
- Built a widget tree with:
  - A header containing:
    - A title label.
    - A live FPS label.
    - Buttons for pin/unpin and expand/collapse.
  - A body containing:
    - Two line charts (FPS and frame time).
    - A vertical stack of labels summarizing stars, hyperlanes, widgets, draw calls, and memory usage.
- Provided helpers to:
  - Clamp and persist panel position in widget space.
  - Enable/disable header dragging based on pin state.
  - Animate expanding/collapsing by adjusting widget bounds and visibility.
  - Maintain `:metrics :performance` history (`:fps-history`, `:frame-time-history`, `:last-sample-time`, `:latest`).
- `update-dashboard!` consumed a metrics map from `silent-king.core` and:
  - Updated histories in `[:metrics :performance]`.
  - Updated chart widgets and stat labels.
  - Ensured the panel stayed on-screen after resizes.

## Notes for Reactified rewrite

For the Reactified UI, we will want:

- A `performance-dashboard` component that:
  - Takes metrics, history, and panel UX state as props.
  - Emits events like `:ui/perf-toggle-pinned`, `:ui/perf-toggle-expanded`, `:ui/perf-move-panel`.
- A dedicated **metrics collector** (likely in or near `silent-king.core`) that:
  - Produces a canonical metrics stream for the new dashboard.
  - Maintains history independently of the rendering technology.

