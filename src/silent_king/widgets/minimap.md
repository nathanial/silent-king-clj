# silent-king.widgets.minimap (legacy)

This namespace previously provided **pure math helpers for the minimap**.

## Responsibilities (old system)

- Computed world-space bounding boxes for stars and hyperlanes.
- Defined transforms between world coordinates and minimap widget coordinates.
- Provided helpers to:
  - Compute viewport size in widget space.
  - Collect star positions and density information for rendering.
  - Map minimap clicks to world-space positions and derive camera pan targets.
- Used by:
  - The minimap widget rendering and interaction logic.
  - The performance dashboard to convert viewport information into widget coordinates.

## Notes for Reactified rewrite

These math utilities are strong candidates to be **ported largely as-is** into the new system, perhaps under a more generic `silent-king.minimap.math` namespace, and then used by a Reactified minimap component and renderer.

