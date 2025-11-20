# Performance Overlay Implementation Plan

## Goals
- Recreate the legacy performance overlay (FPS, frame time, draw calls, entity counts, etc.) in the new Reactified UI layer.
- Keep the component pure (props → Hiccup tree) and reuse existing metrics data from `game-state`.
- Demonstrate hover/press styles already available in the renderer.

## Milestones

### 1. Requirements & Data Flow
- Re-read `src/silent_king/ui/performance_dashboard.md` for legacy behavior (metrics tracked, layout, panel placement).
- Inventory metrics already available under `[:metrics :performance]` (FPS history, latest stats) and determine any gaps (e.g., memory, GC). Decide whether to stub missing metrics or leave placeholders.
- Define a `performance-overlay-props` helper in `reactui.app` to gather values (FPS, frame time, draw calls, entities rendered, etc.).

### 2. Component Structure
- Create `src/silent_king/reactui/components/performance_overlay.clj` with:
  - Root `:panel` or `:vstack` anchored to top-right with padding/background.
  - Sections for "Performance" (FPS, frame time), "Rendering" (draw calls, visible stars/lanes), and optional charts/placeholders.
  - Optional controls (e.g., toggle detail, reset stats) referencing event vectors.
- Keep layout compact enough not to overlap control/hyperlane panels; follow existing spacing/typography.

### 3. Integration
- Update `reactui.app/root-tree` to include the overlay (likely stacked in a `:hstack` or positioned panel). Consider z-ordering so overlay doesn’t cover control panel.
- Ensure scaler & pointer handling already work without additional hooks.

### 4. Rendering/Hover Enhancements (if needed)
- Reuse label styles; add icons or colored badges if helpful (via existing primitives).
- If new primitives are needed (progress bars, mini charts), scope them explicitly and add layout/render coverage.

### 5. Interaction/Event Wiring
- Expose events for potential controls (e.g., `:metrics/reset-peaks`, `:metrics/toggle-overlay`). Stub dispatchers in `reactui.events` even if they no-op initially.
- Persist overlay visibility state under `[:ui :performance-overlay :visible?]` if toggling is supported.

### 6. Testing
- Unit tests for the new component: verify generated tree structure, metric formatting, and event vectors.
- Integration test in `reactui.app-test` ensuring the overlay renders when metrics exist and events dispatch correctly.
- Update `python3 scripts/check_parens.py` targets and run `./run-tests.sh`.

### 7. Follow-ups
- Hook up richer metrics (CPU %, memory) when available.
- Consider history sparklines using additional primitives.
- Add config to hide overlay or change opacity.

## Deliverables
- New component file + integration into app tree.
- Updated events/state for overlay visibility or actions.
- Automated tests covering props, layout, and events.
- Documentation snippet (optional) referencing PerformanceOverlayPlan in README/Reactified plan.
