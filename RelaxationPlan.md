# Voronoi Relaxation Plan

Status: November 20, 2025 — Voronoi cells are generated once from the current star field using JTS and stored in `:voronoi-cells`. This plan outlines how to add centroid-based relaxation (Lloyd-style) to make cells less edgy while fitting into the existing Silent King pipeline.

---

## 1. Goals & Constraints

- Make Voronoi cells visually smoother and less jagged without breaking:
  - Star → cell ownership (`:star-id`).
  - Existing adjacency data (`:neighbors-by-star-id`) or hyperlane logic.
  - Current UI and settings in `:voronoi-settings`.
- Keep the system deterministic and testable:
  - Same galaxy seed + relaxation config → same relaxed cells.
  - Pure functions at the core with explicit side-effect wrappers.
- Preserve performance:
  - Relaxation should be optional and bounded (small number of iterations).
  - Handle thousands of stars without tanking startup time.
- Integrate cleanly with existing docs:
  - Complement `Voronoi.md` and `OrganicShapes.md` rather than replacing them.

---

## 2. High-Level Approach (Lloyd Relaxation over JTS)

We will apply a small number of centroid-relaxation iterations to the star sites, recompute the Voronoi diagram using JTS, and use the relaxed cells for rendering:

1. Start from the current star positions (or optionally a relaxed copy).
2. Generate Voronoi cells with `generate-voronoi` as we do now.
3. For each cell, compute its (JTS) polygon centroid in world space.
4. Move the corresponding star’s site slightly toward that centroid.
5. Repeat steps 2–4 a fixed number of times (e.g., 1–3 iterations).
6. Store only the final relaxed cells (and optionally the relaxed site positions for debug), keeping the rest of the world model intact.

We’ll implement this as a pure helper in `silent-king.voronoi` layered on top of the existing generator, with a small configuration map for tuning.

---

## 3. Data Model & Configuration

### 3.1 Relaxation Settings

Add a focused configuration map (not necessarily surfaced in UI immediately):

- `:iterations` — integer, `0–5`, default `0` (no relaxation).
- `:step-factor` — `0.0–1.0`, default `1.0`:
  - `1.0`: standard Lloyd step (move site to centroid).
  - `< 1.0`: partial step (move partway toward centroid) for softer relaxation.
- `:max-displacement` — optional world-space cap per iteration (e.g. `200.0`):
  - Clamp how far a star can move toward its centroid in a single iteration.
- `:clip-to-envelope?` — boolean, default `true`:
  - Keep relaxed sites inside the same expanded star envelope used by `generate-voronoi`.

Initially this config can be passed programmatically from `silent-king.core` or testing; later we can expose a subset (e.g. `:iterations` and a simple “softness” slider) through the Voronoi settings panel.

### 3.2 State Considerations

To avoid touching the canonical star positions used by the rest of the game, treat relaxed sites as an internal Voronoi concept:

- Keep `:stars` and hyperlanes based on the original star positions.
- Only Voronoi generation sees the relaxed site coordinates.
- Optionally expose a debug flag to store the relaxed positions alongside cells (e.g. `:voronoi-relaxed-sites {id {:x :y}}`) for inspection and tests, but they don’t feed back into `:stars`.

---

## 4. API Surface in `silent-king.voronoi`

### 4.1 Pure Relaxation Helpers

Add a pure helper that operates on a sequence of star-like maps:

- `relax-sites-once`
  - Input:
    - `stars`: vector of `{:id :x :y}`.
    - `config`: relaxation config (or subset).
  - Steps:
    1. Call an internal, pure variant of `generate-voronoi` that accepts a star collection and returns:
       - `{:voronoi-cells {id cell} :envelope envelope :elapsed-ms ...}`.
       - Envelope can be reused instead of recomputing for each iteration.
    2. For each `{:star-id id :centroid {:x cx :y cy}}` in `:voronoi-cells`:
       - Find the corresponding input star `{x0 y0}`.
       - Compute displacement:
         - `dx = cx - x0`, `dy = cy - y0`.
         - Optional clamp by `:max-displacement`.
       - New position:
         - `x' = x0 + step-factor * dx`
         - `y' = y0 + step-factor * dy`.
       - If the cell is degenerate (no vertices / centroid), keep the star in place.
    3. If `:clip-to-envelope?` is true, clamp `(x', y')` to the expanded envelope bounds.
    4. Return the new relaxed star vector, preserving ids and any additional fields.

Then add:

- `relax-sites`
  - Input: `stars`, `config`.
  - Behavior:
    - Loop `:iterations` times, feeding the output stars into the next call to `relax-sites-once`.
    - Early exit if:
      - `:iterations <= 0`, or
      - All displacements in an iteration fall below a small epsilon.
    - Return:
      - `{:stars-relaxed stars-relaxed
         :iterations-used n}`.

### 4.2 Relaxed Voronoi Generation

Expose a higher-level generator that plugs into the existing `generate-voronoi!` call sites:

- `generate-relaxed-voronoi`
  - Input:
    - `stars` (original).
    - `relax-config` (may be `nil` or `{:iterations 0}` for current behavior).
  - Steps:
    1. If no config or `:iterations` is `0`, delegate to `generate-voronoi` directly.
    2. Otherwise:
       - Run `relax-sites` to obtain `stars-relaxed`.
       - Call `generate-voronoi` on `stars-relaxed`.
       - Optionally annotate each cell with a `:relaxed? true` flag or embed the relaxed position for debugging:
         - `:relaxed-site {:x x' :y y'}`.
    3. Return `{ :voronoi-cells ... :elapsed-ms-total ... :relax-meta {...}}`:
       - `:elapsed-ms-total` can include both relaxation and final generation costs.
       - `:relax-meta` contains iteration count, average displacement, etc.

- `generate-voronoi!` updates:
  - Wrap the existing body to optionally call `generate-relaxed-voronoi` based on game-state settings (see §5).
  - Preserve the public contract:
    - Still returns a map with `:voronoi-cells` and `:elapsed-ms`.
  - Log a slightly richer summary:
    - Number of cells.
    - Total elapsed ms.
    - Number of relaxation iterations used.

All of the above functions should remain pure except for `generate-voronoi!`, which mutates `game-state` as it does today.

---

## 5. Wiring Relaxation into Game State & Settings

### 5.1 Voronoi Settings Extensions

Extend `default-voronoi-settings` in `silent-king.state` with non-invasive defaults:

- `:relax-iterations` — `0` (preserves current behavior).
- `:relax-step` — `1.0`.
- `:relax-max-displacement` — a conservative value (e.g. `250.0`) or `nil`.

Add simple accessors in `state.clj`:

- `voronoi-relax-config`:
  - Reads `:voronoi-settings` and extracts a small config map for the Voronoi namespace to consume.

### 5.2 UI / ReactUI Hooks (Optional First Pass)

To keep initial scope small, we can start with config-only (no UI) and wire in UI once we like the results:

- Phase 1:
  - Hard-code `:relax-iterations` and friends in `default-voronoi-settings` or dev-only feature flags.
  - Use REPL or tests to iterate on good defaults.
- Phase 2:
  - Extend `silent-king.reactui.events` with:
    - `:voronoi/set-relax-iterations`.
    - `:voronoi/set-relax-step`.
  - Add sliders / dropdowns in `reactui.components.voronoi-settings` (e.g. “Lloyd relaxation iterations”).
  - Keep ranges small and labeled as “experimental” or “smoothing”.

This mirrors how other Voronoi options are already exposed and tested.

---

## 6. Integration with the Existing Pipeline

### 6.1 Generation Path in `silent-king.core`

Current flow in `-main`:

1. `galaxy/generate-galaxy!` builds stars/planets.
2. `hyperlanes/generate-hyperlanes!` builds connections and `:neighbors-by-star-id`.
3. `voronoi/generate-voronoi!` builds cells once.

With relaxation:

- `generate-voronoi!` should:
  - Read `relax-config` from game-state via `state/voronoi-settings` or a helper.
  - Decide whether to run relaxed or straight generation.
  - Leave hyperlanes and `:neighbors-by-star-id` unchanged (they’re derived from original stars).

No changes are required in `galaxy` or `hyperlanes` namespaces unless we later decide to base adjacency on relaxed positions (out of scope for this first pass).

### 6.2 Rendering (`draw-voronoi-cells`)

- No structural changes are needed:
  - Cells still expose `:vertices`, `:bbox`, and `:centroid` in world space.
  - Culling and drawing stay as-is.
- Relaxation indirectly yields smoother silhouettes by making the underlying diagram more regular; we can layer this with the screen-space “wiggle” ideas in `OrganicShapes.md` if desired.

---

## 7. Performance & Safety Considerations

- Iteration count:
  - Cap `:iterations` to a small integer (≤ 5) to avoid runaway generation costs.
  - For large galaxies, start with 1–2 iterations and profile.
- Envelope reuse:
  - Reuse the same expanded envelope across iterations where possible to avoid repeated bounding-box scans.
- Degenerate cells:
  - If a star’s cell is missing or degenerate in some iteration:
    - Keep the star at its previous position.
    - Optionally count and log such cases for debugging.
- Movement bounds:
  - `:max-displacement` + envelope clipping prevent stars from drifting too far from their original region.
  - Use a small epsilon to avoid thrashing once positions stabilize.

---

## 8. Testing Strategy

Extend `test/silent_king/voronoi_test.clj`:

- Unit tests for `relax-sites-once`:
  - Simple configurations with 2–4 stars arranged in a line or triangle.
  - Assert:
    - Output stars preserve ids.
    - Positions move toward their corresponding cell centroids (or stay put when degenerate).
    - Displacement never exceeds `:max-displacement`.
- Convergence sanity:
  - Run `relax-sites` for a few iterations and assert:
    - Average movement per iteration decreases or falls below epsilon.
- Integration with `generate-relaxed-voronoi`:
  - Compare:
    - `generate-voronoi` vs. `generate-relaxed-voronoi` with `:iterations 0` (results equal).
    - With `:iterations > 0`, check:
      - Same star ids in `:voronoi-cells`.
      - Centroids move closer to their owning stars’ relaxed positions.
- State settings:
  - Tests in `reactui.events_test` / `app_test` to ensure:
    - Relaxation settings can be toggled and clamped.
    - `generate-voronoi!` respects the current settings (may be verified via logged relax-meta in a smaller harness or via REPL-driven tests).

---

## 9. Rollout Plan

1. **Phase 1 – Core helpers**
   - Implement `relax-sites-once`, `relax-sites`, and `generate-relaxed-voronoi` in `silent-king.voronoi`.
   - Add tests exercising pure relaxation behavior.
2. **Phase 2 – Integration**
   - Extend `generate-voronoi!` to call the relaxed generator when enabled by settings.
   - Add basic config fields to `default-voronoi-settings`.
3. **Phase 3 – Tuning & UX**
   - Experiment at the REPL with galaxies of different sizes.
   - Dial in default `:iterations` and `:step-factor` (likely 1–2 iterations with step < 1.0).
   - Optionally expose minimal UI controls in the Voronoi settings panel.
4. **Phase 4 – Polishing & Combination**
   - Combine Lloyd relaxation with chosen options from `OrganicShapes.md`:
     - Screen-space edge wiggle for subtle local irregularity.
     - Domain warping (see `DomainWarpPlan.md`) for broader organic variation.
   - Capture before/after screenshots and update `Voronoi.md` / `OrganicShapes.md` with examples and final defaults.

This plan adds a small, well-contained relaxation layer on top of the existing Voronoi implementation, giving the cells a less edgy, more aesthetically pleasing structure while keeping the rest of the game’s model and performance characteristics intact.

