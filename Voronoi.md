# Voronoi.md

High-level plan for adding a Voronoi diagram visualization for the star field in Silent King. The diagram should be derived from the same Delaunay triangulation we already use for hyperlanes and exposed as a configurable overlay.

---

## 1. Goals & UX Overview

- Show a Voronoi partition of space where each star owns a cell region of influence.
- Make the overlay visually subtle so it enhances, not overwhelms, stars and hyperlanes.
- Keep the feature fully optional and controlled via a dedicated Voronoi settings panel and a quick toggle in the main control panel.
- Reuse Delaunay triangulation / JTS geometry to avoid duplicate math and keep performance predictable.

---

## 2. Data Model & Geometry

### 2.1 Voronoi cell representation

- Define a pure data shape for a Voronoi cell (world space):
  - `:star-id` – owner star id.
  - `:vertices` – ordered vector of `{ :x :y }` world coordinates for the cell polygon (counter-clockwise, no duplicates).
  - `:bbox` – cached axis-aligned bounding box `{ :min-x :min-y :max-x :max-y }` for culling.
  - `:centroid` – `{ :x :y }` average point (for debug labels or UI).
  - `:neighbors` (optional) – vector of neighboring `:star-id`s sharing an edge (can be derived from hyperlanes if needed).
- Store cells on `game-state`:
  - Add `:voronoi-cells {star-id cell}` and `:voronoi-generated?` (bool) so we can lazily generate and detect stale data.

### 2.2 Deriving cells from Delaunay / JTS

- Create a new namespace `silent-king.voronoi` responsible for pure Voronoi geometry:
  - Require `silent-king.state` and import JTS triangulation types, mirroring `silent-king.hyperlanes`.
- Input: sequence of star maps with `:id :x :y` (via `state/star-seq`).
- Reuse the coordinate preparation logic from `hyperlanes`:
  - Build `coords` as `[id Coordinate]` pairs to keep a mapping between JTS coordinates and star ids.
- Use JTS triangulation to derive Voronoi cells:
  - Option A (preferred): Use the subdivision produced by `DelaunayTriangulationBuilder` to obtain Voronoi cell polygons per site.
  - Option B: Use `VoronoiDiagramBuilder` with the same coordinates and then map output polygons back to the nearest input coordinate (within epsilon) to recover `:star-id`.
- For each site:
  - Collect the polygon vertices in world coordinates.
  - Ensure vertices are ordered consistently (e.g., by angle around the star).
  - Clamp / clip cells to a reasonable bounding box based on the star field extent so they don’t stretch to infinity.
- Return a pure map:
  - `{ :voronoi-cells {star-id cell, ...}
      :elapsed-ms ... }`.

### 2.3 World generation / lifecycle

- Add `generate-voronoi` and `generate-voronoi!`:
  - `generate-voronoi` (pure) in `silent-king.voronoi`:
    - Accepts a star sequence and returns the map above.
  - `generate-voronoi!`:
    - Grabs stars from `game-state`.
    - Calls `generate-voronoi`.
    - Writes `:voronoi-cells` (and `:voronoi-generated? true`) into `game-state`.
    - Logs a small summary: number of cells, elapsed time.
- Decide when to generate:
  - On world creation (after stars/hyperlanes) in `silent-king.core` so it’s ready for visualization.
  - Optionally expose a debug key / menu action to regenerate Voronoi after any future “rebuild world” flow.

---

## 3. Rendering the Voronoi Overlay

### 3.1 Camera transform & culling

- Introduce helper functions in `silent-king.voronoi` (or a small dedicated rendering helper namespace) to compute:
  - `cell-visible?` given `:bbox` and current viewport in world space.
  - Screen-space vertices by running each `{ :x :y }` through `camera/transform-position`.
- Only draw cells whose bounding boxes intersect the expanded viewport (similar to hyperlane line culling) to avoid per-frame overdraw.

### 3.2 Skija drawing primitives

- Add a `draw-voronoi-cells` function, similar to `draw-all-hyperlanes`, taking:
  - `^Canvas canvas`, `width`, `height`, `zoom`, `pan-x`, `pan-y`, `game-state`, and current time.
- For each visible cell:
  - Build a Skija `Path` from the transformed vertices.
  - Draw:
    - A subtle fill (low opacity) controlled by settings.
    - An outline with configurable width and color.
- Respect zoom-based LOD:
  - Far zoom (`zoom < ~0.7`): disable Voronoi overlay entirely, or render only very faint outlines.
  - Mid zoom: draw outlines only.
  - Close zoom: draw outlines + fill and optional debug markers.
- Rendering order in `silent-king.core/draw-frame`:
  - Hyperlanes (background).
  - Voronoi cells (soft overlay).
  - Stars and planets (foreground) so cells never obscure star sprites.

### 3.3 Feature flag / toggle

- Extend `create-game-state` and `:features`:
  - Add `:voronoi? false` or `true` by default (we can start disabled and flip later).
- Provide a helper in `state.clj`:
  - `voronoi-enabled?` analogous to `hyperlanes-enabled?`.
- Guard both generation and drawing:
  - Only call `draw-voronoi-cells` when `voronoi-enabled?` is true and `:voronoi-cells` is populated.

---

## 4. Voronoi Control Panel & Customization

This section covers how players can control the Voronoi overlay via UI. It mirrors the existing hyperlane settings panel and plugs into the top-left control panel.

### 4.1 State: default settings and helpers

- In `silent-king.state`:
  - Add `default-voronoi-settings`, for example:
    - `:enabled?` – master on/off toggle.
    - `:opacity` – overall opacity applied to both fill and stroke.
    - `:fill-opacity` – relative fill opacity (fraction of `:opacity`).
    - `:line-width` – base Voronoi edge width in world pixels (scaled by zoom).
    - `:style` – keyword (`:wireframe`, `:filled`, `:heatmap`, etc.).
    - `:show-centroids?` – optional debug display of cell centroids.
    - `:color-scheme` – keyword for palette (`:monochrome`, `:by-density`, `:by-degree`).
  - Add accessors/mutators similar to hyperlanes:
    - `voronoi-settings` – returns merged settings.
    - `set-voronoi-setting!` – generic setter that also keeps `:features :voronoi?` in sync with `:enabled?`.
    - `reset-voronoi-settings!` – reset to defaults and update feature flag.
- Ensure `create-game-state` seeds:
  - `:voronoi-settings default-voronoi-settings`
  - `:features {:voronoi? (:enabled? default-voronoi-settings) ...}`.

### 4.2 Events: wiring UI to state

- In `silent-king.reactui.events`:
  - Add dispatch cases for Voronoi settings:
    - `:ui/toggle-voronoi-panel` – expand/collapse the dedicated Voronoi window.
    - `:voronoi/set-enabled?` – toggle overlay visibility.
    - `:voronoi/set-opacity` – clamp into `[0.05, 1.0]`.
    - `:voronoi/set-fill-opacity` – clamp into `[0.0, 1.0]`.
    - `:voronoi/set-line-width` – clamp into `[0.5, 4.0]`.
    - `:voronoi/set-style` – only accept known style keywords.
    - `:voronoi/set-color-scheme` – similar to hyperlane color handling.
    - `:voronoi/set-show-centroids?` – toggle debug markers.
    - `:voronoi/reset` – delegate to `state/reset-voronoi-settings!`.
- For quick access in the main control panel:
  - Add `:ui/toggle-voronoi` event that simply flips `:enabled?` via `set-voronoi-setting!`.

### 4.3 Voronoi settings panel component

- Create `src/silent_king/reactui/components/voronoi_settings.clj`:
  - Mirror structure of `hyperlane-settings`:
    - `default-panel-bounds` with a slightly smaller height.
    - Shared color constants (`accent-color`, `text-color`, etc.).
    - Small helpers:
      - `header-row` with title `"Voronoi Settings"` and a show/hide button (`:ui/toggle-voronoi-panel`).
      - `toggle-button` reused for multiple toggles.
      - `slider-row` for opacity/line-width.
      - `color-row` if we support color schemes.
  - Main `voronoi-settings-panel`:
    - Takes `{:keys [settings expanded? color-dropdown-expanded?]}` props.
    - Shows:
      - Visibility section (`Enable/Disable Voronoi`).
      - Opacity slider (0–100%).
      - Fill opacity slider (0–100%).
      - Line width slider (thin to thick).
      - Style selector (wireframe vs filled).
      - Optional toggles for centroids or debug modes.
      - “Reset to Defaults” button bound to `:voronoi/reset`.

### 4.4 Integrating the panel into the UI

- In `silent-king.reactui.app`:
  - Add a `voronoi-window-id` (e.g., `:ui/voronoi-settings`).
  - Define `voronoi-settings-props` analogous to `hyperlane-settings-props`:
    - Pulls `state/voronoi-settings`.
    - Checks `state/dropdown-open?` for any dropdowns in the panel.
  - Add `voronoi-settings-window`:
    - Computes bounds using `state/window-bounds` with `voronoi-settings/default-panel-bounds`.
    - Provides title `"Voronoi Settings"`, resizable window, and minimization handling.
  - Mount it in `root-tree` near the hyperlane settings window so they appear together:
    - `[:vstack ... (hyperlane-settings-window game-state) (voronoi-settings-window game-state) ...]`.

### 4.5 Quick toggles in the main control panel

- In `control-panel-props`:
  - Add `:voronoi-enabled?` derived from `state/voronoi-enabled?`.
- In `silent-king.reactui.components.control-panel`:
  - Add a second toggle button under the hyperlane button:
    - Label `"Enable Voronoi"` / `"Disable Voronoi"` depending on `:voronoi-enabled?`.
    - `:on-click [:ui/toggle-voronoi]`.
- This gives a fast entry point to turn the overlay on/off without opening the settings window.

---

## 5. Performance & Testing

### 5.1 Performance considerations

- Generation:
  - Measure Voronoi generation time for existing star counts and log the result.
  - If expensive, consider generating only when the feature is first enabled or when zoomed in past a threshold.
- Rendering:
  - Use bounding box culling plus LOD rules to avoid drawing cells when zoomed far out.
  - Avoid allocating new Skija `Path` objects every frame for all cells:
    - Option: precompute immutable vertex arrays and reuse `Path` instances with `reset`/`addPoly` patterns where possible.
  - Watch overdraw: prefer strokes only at mid zooms, reserve fills for closer views.

### 5.2 Testing strategy

- Geometry tests:
  - Add unit tests for `generate-voronoi`:
    - Simple input patterns (e.g., 2, 3, 4 stars in known arrangements).
    - Assert: each star gets exactly one cell, no empty cells, and polygons are non-empty.
    - Validate that cell centroids are reasonably close to their owning star coordinates.
- State / UI tests:
  - Mirror existing hyperlane tests:
    - Verify `reset-voronoi-settings!` restores defaults and sets `:features :voronoi?` appropriately.
    - Verify sliders/buttons in the Voronoi settings panel dispatch the expected events and change state.
    - Ensure the control panel toggle updates `:voronoi-enabled?`.

---

## 6. Implementation Phases

1. **Phase 1 – Data & geometry**
   - Implement `silent-king.voronoi` with `generate-voronoi` / `generate-voronoi!` and cell data structures.
2. **Phase 2 – Rendering**
   - Add `draw-voronoi-cells` and integrate it into `silent-king.core/draw-frame` with LOD and culling.
3. **Phase 3 – Control panel & state**
   - Add Voronoi settings to `silent-king.state`, `reactui.events`, the dedicated Voronoi settings panel, and the main control-panel toggle.
4. **Phase 4 – Polish & perf**
   - Tune colors, opacity defaults, zoom thresholds, and profile generation + rendering with large star counts.
   - Iterate on UX (e.g., default disabled vs enabled, control panel layout) based on live testing.

