# silent-king.widgets.draw-order (legacy)

This namespace previously handled **render ordering** for widgets.

## Responsibilities (old system)

- Computed widget depth based on parent relationships so that children rendered above parents by default.
- Implemented `sort-for-render`, which:
  - Filtered out invisible widgets.
  - Sorted by `:layout :z-index`, then hierarchy depth, then entity id for determinism.

## Notes for Reactified rewrite

In the new system we will still need a **stable draw order**. This module’s rules (z-index, parent/child depth, deterministic tie-breaking) are a sensible baseline for a Reactified renderer walking a primitive tree.

