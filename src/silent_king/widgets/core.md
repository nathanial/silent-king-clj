# silent-king.widgets.core (legacy)

This namespace previously implemented the **core widget ECS integration**.

## Responsibilities (old system)

- Defined `create-widget` and higher-level widget constructors:
  - Containers: `panel`, `vstack`, `hstack`, `scroll-view`, `minimap`.
  - Controls: `label`, `button`, `slider`, `toggle`, `dropdown`.
  - Visualization: `line-chart`, `star-preview`.
- Defined the standard widget component layout:
  - `:widget` (type, id, parent/children, visibility).
  - `:bounds` (x, y, width, height).
  - `:layout` (padding, margin, anchor, z-index).
  - `:visual` (colors, text, radii, shadows, etc.).
  - `:interaction` (hover/pressed/focused, callbacks, drag state).
  - `:value` (widget-specific data: slider value, dropdown options, list items, etc.).
- Provided helpers for managing widget entities:
  - `add-widget!`, `add-widget-tree!`, `get-all-widgets`, `get-widget-by-id`.
  - Layout helpers for vertical/horizontal stacks, including `compute-vstack-layout` and `compute-hstack-layout`.
  - `request-layout!`, `request-parent-layout!`, `set-visibility!`.

## Notes for Reactified rewrite

In the Reactified UI, this functionality will be replaced by:

- A **pure primitive vocabulary** (panel, stack, label, button, etc.) expressed as data.
- A renderer that either:
  - Directly draws primitives (immediate mode), or
  - Internally uses an ECS-like structure as an implementation detail.

This file’s structure is a useful reference for:

- The fields we expect each primitive to support.
- The behaviors that container and control widgets should expose.

