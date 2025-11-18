# silent-king.widgets.layout (legacy)

This namespace previously implemented **layout invalidation and processing** for the widget ECS.

## Responsibilities (old system)

- Defined `perform-layout` as a multimethod on widget `:type`.
- Managed a `[:widgets :layout-dirty]` set on `game-state` to track which widgets need recomputation.
- Implemented anchor positioning via `apply-anchor-position` to support:
  - :top-left, :top-right, :bottom-left, :bottom-right, :center, and margin-based positioning.
- Implemented:
  - `process-layouts!` to traverse dirty widgets in parent-before-child order.
  - `widget-depth` helpers to enforce correct layout order.
- Worked together with `silent-king.widgets.core` to compute child `:bounds` for `:vstack`, `:hstack`, and other containers.

## Notes for Reactified rewrite

In the new system, we likely want a **pure layout phase** that:

- Walks a tree of primitives and returns a new tree annotated with `:bounds`.
- Supports anchors, padding, margin, and stacking similar to this module, but without relying on mutable `layout-dirty` sets.

This legacy layout code is a good guide for the semantics and algorithms we may want to port to a functional layout engine.

