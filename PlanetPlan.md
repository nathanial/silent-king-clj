# PlanetPlan.md

High-level plan for adding orbiting planets around stars in Silent King. Plan is broken into phases so we can ship incremental value and keep performance predictable.

---

## 1. Data Model & World Generation

- **1.1 Introduce planet entity structure**
  - Represent planets as pure data maps stored on `game-state`, mirroring stars.
  - Shape (per planet):
    - `:id` – unique planet id (separate counter from stars).
    - `:star-id` – id of the parent star this planet orbits.
    - `:radius` – orbital radius (world units) from star center.
    - `:orbital-period` – seconds for a full revolution (or derived from angular speed).
    - `:phase` – initial angle offset (radians) at `t=0` for deterministic layout.
    - `:size` – base visual size in pixels (before zoom transform).
    - `:sprite-path` – filename pointing into planet atlas metadata (UUID `.png` name).
    - `:eccentricity` (optional) – later: allow slightly elliptical orbits, initial value 0.0.
    - `:inclination` (optional) – for future 3D-ish projection tricks; start at 0.0.

- **1.2 Extend game-state for planets and ids**
  - In `silent-king.state/create-game-state`:
    - Add `:planets {}` (map of id → planet).
    - Add `:next-planet-id 0`.
  - Add helpers in `silent-king.state`:
    - `next-planet-id!` – increments and returns `:next-planet-id`.
    - `planets` – returns planet map from `game-state`.
    - `planet-seq` – returns `(vals (planets game-state))`.
    - `planets-by-star-id` – computed view (or precomputed map later for perf) mapping `star-id` → vector of planets.
    - `add-planet!` – inserts a planet with assigned id.
  - Update `reset-world-ids!` to also reset `:next-planet-id`.
  - Update `set-world!` to accept and store `:planets` and `:next-planet-id`.

- **1.3 Add planet generation alongside stars**
  - In `silent-king.galaxy`:
    - Add a new function `generate-planets-for-star` that takes:
      - `star` (includes `:x`, `:y`, `:density`, maybe `:id`).
      - RNG inputs (density, radial position, maybe noise) to make orbits interesting but deterministic.
    - Heuristics for number of planets:
      - Use star `:density` (or another derived property) to decide number of planets (e.g., 0–5).
      - Example: dwarfs may have 0–1 planets; dense/core stars more likely to have 3–5.
    - Orbital parameters:
      - `:radius` sampled from increasing bands (e.g., 200–800 world units from star), with min separation so orbits don’t overlap visually.
      - `:orbital-period` scaled with radius (Kepler-ish: `period ∝ radius^(3/2)` or a simple monotonic mapping).
      - `:phase` uniformly random in `[0, 2π)`.
      - `:size` small, e.g., 6–16 pixels base size.

- **1.4 Connect planet generation into world creation**
  - Extend `generate-galaxy` / `generate-galaxy!` to:
    - Generate stars as today.
    - For each generated star, call `generate-planets-for-star`.
    - Accumulate planets into a `planets` map and a `next-planet-id`.
    - Return `{ :stars stars :planets planets :next-star-id n :next-planet-id m :hyperlanes [] :neighbors-by-star-id {} }`.
  - In `generate-galaxy!`, update the call to `state/set-world!` to include planets and `:next-planet-id`.

---

## 2. Planet Assets & Loading

- **2.1 Use planet atlas built by the existing pipeline**
  - We already generate `assets/planet-atlas-medium.png` and `assets/planet-atlas-medium.json` from `assets/planets-processed`.
  - Planets are visible only at relatively close zoom, so a single “medium” resolution atlas is sufficient as a first pass.

- **2.2 Extend asset loading**
  - In `silent-king.assets`:
    - Add `load-planet-atlas-image` and `load-planet-atlas-metadata` helpers or reuse `load-atlas-image` / `load-atlas-metadata` with different paths.
    - Extend `load-all-assets` to load:
      - `:planet-atlas-image-medium` from `assets/planet-atlas-medium.png`.
      - `:planet-atlas-metadata-medium` from `assets/planet-atlas-medium.json`.
      - Optionally `:planet-atlas-size-medium` (4096) for consistency.
  - Update `silent-king.state/create-game-state` default `:assets` map to include the new planet atlas keys initialized to `nil` / `{}`.

- **2.3 Asset cleanup**
  - In `silent-king.core/cleanup`, close the planet atlas image similarly to star atlases:
    - Check `(:planet-atlas-image-medium assets)` and `.close` it if present.

---

## 3. Orbit Simulation (World-Space)

- **3.1 Time-based angular position**
  - Use existing time state: `(:current-time (state/get-time game-state))`.
  - For each planet:
    - Angular speed `ω` = `2π / orbital-period`.
    - Angle at time `t`: `θ(t) = phase + ω * t`.
  - World-space planet position:
    - Look up parent star position `(star-x, star-y)` by `:star-id`.
    - Planet world coordinates:
      - `x = star-x + radius * cos θ(t)`
      - `y = star-y + radius * sin θ(t)`
  - Keep the orbit calculation purely functional (no per-frame mutation of planet data).

- **3.2 Optional eccentricity (future enhancement)**
  - To support elliptical orbits later:
    - Use `:eccentricity` and a simple ellipse `(radius-x, radius-y)` instead of a single scalar radius.
    - For now, keep `eccentricity = 0.0` and treat radius as circular.

---

## 4. Rendering Planets with Zoom-Based Visibility

- **4.1 Decide zoom thresholds**
  - Reuse camera zoom from `state/get-camera`.
  - Current LOD thresholds for stars:
    - `< 0.5` → xs atlas.
    - `< 1.5` → small atlas.
    - `< 4.0` → medium atlas.
    - `≥ 4.0` → full-resolution images.
  - Planet visibility rule:
    - Planets should be invisible at far zoom levels.
    - Start rendering planets once `zoom` is “close” to the star:
      - For example:
        - `zoom < 1.5`: planets hidden.
        - `zoom ≥ 1.5`: planets visible.
    - Fine-tune thresholds after live testing; ensure they appear only when stars are clearly discernible.

- **4.2 Integrate with `draw-frame`**
  - In `silent-king.core/draw-frame`:
    - After computing `zoom`, `pan-x`, `pan-y`, `lod-level`, and visible stars:
      - Add a step to fetch planets: `(state/planet-seq game-state)` or a prefiltered structure.
    - Rendering order:
      - Hyperlanes (already drawn first).
      - Stars (current implementation).
      - Planets (draw after stars so they sit “on top” and are easy to see).
  - Planet visibility / frustum culling:
    - For each planet:
      - Compute world position from its orbit (using time and parent star).
      - Compute screen position using `camera/transform-position`.
      - Compute screen size using `camera/transform-size`.
      - Reuse `star-visible?` or a generalized `sprite-visible?` to cull off-screen planets.

- **4.3 Drawing planets from atlas**
  - Implement `draw-planet-from-atlas` in `silent-king.core` (or a shared helper):
    - Similar to `draw-star-from-atlas` but pulling from planet atlas metadata/image:
      - Inputs: canvas, planet atlas image, planet atlas metadata, `sprite-path` (planet UUID filename), screen position, size, rotation (optional).
      - For now, keep planet rotation independent of orbit (or a small spin if desired).
    - Use `:planet-atlas-image-medium` and `:planet-atlas-metadata-medium` from assets; no LOD switching needed initially.
  - In `draw-frame`:
    - When `zoom >= planet-visibility-threshold`:
      - For each visible planet:
        - Compute `(screen-x, screen-y, screen-size)` based on world position & zoom.
        - Look up metadata entry by `sprite-path`.
        - Draw using `draw-planet-from-atlas`.

---

## 5. Selection, UI, and Interaction (Optional Phase)

- **5.1 Basic selection support**
  - Extend `silent-king.selection` and selection state to optionally hold a `:planet-id`.
  - Update click-hit tests:
    - When clicking near a star at close zoom, also test proximity to planets:
      - Compute planet screen positions (same as rendering).
      - If cursor is within a planet’s radius, set selection to `{:planet-id … :star-id …}`.
    - Define priority (e.g., planet selection overrides star when overlapping).

- **5.2 UI representation**
  - Extend React UI (under `src/silent_king/reactui`) to display basic planet info:
    - Parent star name/id.
    - Orbital radius, orbital period, maybe “zone” (inner/outer belt).
  - This phase is optional; core goal is visual orbiting planets.

---

## 6. Performance & Testing Strategy

- **6.1 Performance considerations**
  - Keep planet counts modest (e.g., default 0–5 per star, with a cap on total planets).
  - Use simple math per-frame:
    - Avoid heavy trig inside tight loops where possible; but initial implementation can use `Math/cos` / `Math/sin` directly.
  - If needed, introduce spatial filtering:
    - Only consider planets for stars already in `visible-stars` to reduce work.
    - Option: derive planets from `visible-stars` only, instead of iterating over all planets.

- **6.2 Testing**
  - Unit tests:
    - Add tests in `test/silent_king` for:
      - Planet generation counts and radius ordering.
      - Orbit position correctness given `radius`, `phase`, `orbital-period`, and time.
    - Keep tests deterministic by using fixed seeds / mocked time.
  - Manual validation:
    - Zoom out fully: verify planets are not drawn.
    - Zoom in gradually:
      - Planets appear around stars only at close zoom.
      - Orbits look smooth and continuous as time progresses.
    - Verify no major FPS regression after enabling planets.

---

## 7. Implementation Phases (Suggested Order)

1. **Phase 1 – Data model & generation**
   - Add planet structures to `state`.
   - Implement planet generation per star in `galaxy`.
   - Ensure planets are present in `game-state` but not yet rendered.

2. **Phase 2 – Assets & loading**
   - Wire `planet-atlas-medium` loading into `assets` and `state`.
   - Confirm build + load-all-assets succeed and assets are closed on cleanup.

3. **Phase 3 – Orbit math & rendering**
   - Implement orbit position calculation based on time, star position, and planet parameters.
   - Add planet rendering path in `draw-frame` using planet atlas.
   - Enforce zoom thresholds so planets only appear close in.

4. **Phase 4 – Polish & perf**
   - Tune orbit radii, periods, and counts.
   - Profile with many stars + planets; adjust thresholds or limits if needed.
   - Optionally add basic planet selection and UI details.

