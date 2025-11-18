# silent-king.ui.specs (legacy)

This namespace previously defined **`clojure.spec` schemas and helpers** for UI-related state subtrees.

## Responsibilities (old system)

- Defined specs for:
  - `::star-inspector-state`
  - `::hyperlane-settings-state`
  - `::performance-dashboard-state`
- Provided constructors:
  - `make-star-inspector-state`
  - `make-hyperlane-settings-state`
  - `make-performance-dashboard-state`
- Provided validation helpers used by UI modules to guard state updates.

## Notes for Reactified rewrite

In the Reactified UI we may want:

- A **consolidated UI state spec** that covers all Reactified components, or
- Per-component specs living near each Reactified component module.

Either way, this legacy file is a useful reference for:

- Field names and semantics we previously considered important.
- Default values and invariants we may want to preserve or revisit.

