# silent-king.widgets.animation (legacy)

This namespace previously implemented a small **camera animation system** used by the UI.

## Responsibilities (old system)

- Defined cubic easing functions (`ease-in`, `ease-out`, `ease-in-out`) and linear interpolation helpers.
- Implemented `start-camera-pan!` and `update-camera-animation!` to:
  - Interpolate camera `:pan-x` and `:pan-y` over a configurable duration.
  - Support different easing modes.
  - Use `[:time :current-time]` from `game-state`.
- Provided `camera-animation-active?` to query whether an animation was in progress.

## Notes for Reactified rewrite

While the current `draw-frame` no longer calls this module, the behavior is still valuable as:

- A blueprint for a **generic animation helper** that could be reused by Reactified UI components (e.g. animated panel transitions, minimap pan-to-click).

We may reintroduce a refined version of these helpers in a new, UI-agnostic animation module.

