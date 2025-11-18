# silent-king.widgets.render (legacy)

This namespace previously implemented the **widget rendering system** on top of Skija.

## Responsibilities (old system)

- Maintained a small paint cache and color utilities (`lighten`, `darken`) to avoid reallocations.
- Defined low-level drawing helpers:
  - Rounded rectangles with shadows.
  - Text rendering and centered text.
  - Scrollbars, slider tracks/thumbs, toggle switches, dropdown menus, etc.
- Implemented `render-widget` as a multimethod on widget `:type` for:
  - `:panel`, `:vstack`, `:hstack`, `:button`, `:label`, `:slider`, `:toggle`, `:dropdown`, `:scroll-view`, `:line-chart`, `:minimap`, `:star-preview`, and others.
- Implemented `render-all-widgets`, which:
  - Sorted widgets using `silent-king.widgets.draw-order/sort-for-render`.
  - Applied global UI scaling from `silent-king.widgets.config/ui-scale`.
  - Walked all visible widgets and drew them onto a Skija `Canvas`.

## Notes for Reactified rewrite

In the Reactified system this functionality will likely reappear as:

- A set of **primitive renderers** (one per primitive type) that consume pure data and issue Skija calls.
- A tree walker that:
  - Applies UI scaling.
  - Honors z-index and hierarchy depth.

This legacy renderer is a rich source of examples for how to draw each widget type and may be mined for drawing helpers even after the ECS-specific pieces are gone.

