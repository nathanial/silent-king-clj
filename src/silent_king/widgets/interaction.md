# silent-king.widgets.interaction (legacy)

This namespace previously implemented **hit testing and interaction** for widgets.

## Responsibilities (old system)

- Converted screen coordinates into widget coordinates using `ui-scale`.
- Performed hit testing across widgets with correct z-order and hierarchy depth.
- Implemented interaction behaviors for:
  - Hover and pressed states on generic widgets.
  - Button clicks, toggle flips, slider drags, and dropdown open/close and selection.
  - Scroll-view scroll wheel handling and scrollbar rendering.
  - Generic draggable widgets via a `:draggable?` flag and `:drag-offset`.
  - Minimap click-to-pan using math from `silent-king.widgets.minimap` and camera animations from `silent-king.widgets.animation`.
- Exposed helpers to:
  - Reset interaction state across all widgets.
  - Handle mouse move/press/release and scroll events from GLFW callbacks.

## Notes for Reactified rewrite

The Reactified UI will need a new **event and interaction layer** that:

- Performs hit testing over a primitive/layout tree instead of ECS entities.
- Produces **event descriptors** (e.g. `[:ui/toggle-hyperlanes]`) rather than calling callbacks that close over `game-state`.

This file is a good guide to the expected behaviors of controls like dropdowns, sliders, scroll-views, and minimaps.

