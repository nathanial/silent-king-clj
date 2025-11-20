# Organic Voronoi Shapes

Status: November 20, 2025 — Voronoi cells are generated with JTS and rendered as straight‑edge polygons; this note explores options for softer, more organic edges.

---

## 1. Current Pipeline (Where the Hard Lines Come From)

- Geometry:
  - `silent-king.voronoi/generate-voronoi` uses `org.locationtech.jts.triangulate.VoronoiDiagramBuilder` to build a diagram from `{ :id :x :y }` stars.
  - Each JTS `Polygon` is converted to a cell via `polygon->cell`, which:
    - Reads the exterior ring coordinates.
    - Strips the duplicated closing vertex.
    - Converts them to `{ :x :y }` maps.
    - Sorts vertices counter‑clockwise around the site (`sort-ccw`).
    - Caches `:bbox` and `:centroid`.
  - Cells are stored on `game-state` as `{:voronoi-cells {star-id cell} :voronoi-generated? true}`.
- Rendering:
  - `draw-voronoi-cells` is responsible for all on‑screen Voronoi work:
    - Computes a world‑space viewport via `world-viewport` and culled `:bbox` checks (`cell-visible?`).
    - Transforms vertices to screen space with `transform-vertices` and `camera/transform-position`.
    - Builds a Skija `Path` per visible cell and draws:
      - A filled polygon (`PaintMode/FILL`, low opacity, per‑cell palette).
      - A stroke pass (`PaintMode/STROKE`, `PaintStrokeCap/ROUND`, configurable `:line-width`).
    - Optional centroid markers use circles at `:centroid`.
  - Result: crisp, straight‑edged polygons with anti‑aliased strokes, but no curvature or “wiggle” in the edges.

This means all “hardness” lives in two places:

1. The cell vertex data (`:vertices`) coming out of JTS.
2. The path construction in `draw-voronoi-cells`, which connects vertices with straight line segments.

We can get more organic shapes by changing the geometry, the drawing, or both.

---

## 2. Design Goals for Organic Edges

- Preserve clarity:
  - Cells should still read as “regions of influence” around stars, not abstract noise clouds.
  - Avoid large overlaps or gaps that would undermine adjacency intuition.
- Keep performance and determinism:
  - Work with thousands of stars at 60fps on typical hardware.
  - Changes should be deterministic for a fixed seed (good for tests and replays).
- Respect existing systems:
  - Avoid breaking hyperlane adjacency and `neighbors-by-star-id`.
  - Keep `:voronoi-cells` as a pure, testable data structure.
- Allow tunability:
  - Provide a way to dial in “wiggle amount” and “softness” via settings, including an option to keep the current crisp style.

---

## 3. Option A – Screen‑Space Edge Wiggle (Rendering Only)

**Idea:** Leave world‑space Voronoi cells untouched and add small, deterministic wiggles along each edge when drawing the Skija `Path`. Geometry stays simple, and we treat organic edges as a purely visual effect.

### 3.1 Implementation Sketch

- New settings (on `:voronoi-settings`):
  - `:edge-style` – `:straight` (current), `:wavy`.
  - `:wiggle-amplitude` – max perpendicular offset in screen pixels (e.g. 0–8).
  - `:wiggle-frequency` – how many “waves” per 100px of edge length.
- Rendering changes in `draw-voronoi-cells`:
  - Before constructing each `Path`, replace `screen-verts` with a subdivided, wavy polyline when `:edge-style = :wavy`:
    - For each segment `p0 → p1` in `screen-verts`:
      - Compute segment length `len` and direction `(dx, dy)`.
      - Derive a unit normal `(nx, ny)` for perpendicular offset.
      - Choose a subdivision count `n` based on `len` (e.g. 1 sample per 12–16px).
      - For each subdivision `t ∈ [0, 1]`:
        - Base point `b(t) = p0 + t * (p1 - p0)`.
        - Sample a deterministic scalar `w(t)` in `[-1, 1]` (sinusoid or noise).
        - Offset point `p(t) = b(t) + w(t) * amplitude * (nx, ny)`.
    - Emit `p(0) ... p(1)` as the new vertices for both fill and stroke paths.
  - Use deterministic wiggle:
    - Seed comes from `star-id`, segment index, and a global Voronoi noise seed (or just a hash function).
    - Optional time parameter (from `current-time`) can be added for subtle animation (breathing edges), but can be zero for static shapes.
- Noise source:
  - Simple option: `Math/sin` / `Math/cos` based on `t` and segment/stage seeds.
  - Richer option: reuse the `FastNoiseLite` wrapper (`silent-king.galaxy/create-noise-generator`) for 1D/2D noise in screen space.

### 3.2 Pros

- Isolated to rendering:
  - No changes to `generate-voronoi`, tests, or stored `:voronoi-cells`.
  - Easy to gate behind a setting and zoom thresholds.
- Tunable and cheap:
  - Subdivision counts can be tied to zoom to keep the number of path points bounded.
  - We can fall back to straight edges when zoomed far out.
- Highly expressive:
  - Supports static wiggle, time‑based motion, or different “materials” (e.g. noisy edges for dense regions only).

### 3.3 Cons / Risks

- Neighbor consistency:
  - Each cell is drawn independently, so shared borders won’t be perfectly synchronized.
  - With small amplitudes and similar colors, this likely reads as intentional softness rather than an error, but we should verify visually.
- Extra path points:
  - Subdividing edges increases vertex count per cell.
  - Needs profiling on large galaxies and possibly a cap on total points per frame.

---

## 4. Option B – World‑Space Vertex Jitter (Soft but Still Polygonal)

**Idea:** Perturb Voronoi polygon vertices once, in world space, so the cells themselves become slightly irregular. Edges remain straight between vertices, but shapes pick up organic asymmetry and less “perfect” geometry.

### 4.1 Implementation Sketch

- New helper in `silent-king.voronoi`:
  - `jitter-vertices [vertices site-id] -> jittered-vertices`:
    - For each `{ :x :y }`:
      - Sample deterministic noise in world space (e.g. using `FastNoiseLite` with seed based on `:id`).
      - Compute a small offset vector `(dx, dy)` in world units.
      - Apply a clamped offset with max magnitude proportional to local cell size (e.g. a few percent of the bbox diagonal).
  - Apply inside `polygon->cell`:
    - After `vertex-maps` / `sort-ccw`, but before `vertices->bbox`, pass through `jitter-vertices`.
- Settings:
  - `:vertex-jitter-amount` (0–1), default 0 (current behavior).
  - Jitter can be tied to zoom indirectly via rendering (we always store full jitter, but culling and LOD remain unchanged).

### 4.2 Pros

- Simple mental model:
  - Cells are still polygons, just not as “perfect”.
  - No per‑frame subdivision; jitter is applied once at generation time.
- Topology‑safe:
  - Shared JTS `Coordinate` instances will be perturbed identically, so adjacent cells remain tightly aligned.
- Testable:
  - `generate-voronoi` remains pure and deterministic for a fixed seed, allowing snapshot tests on the jittered geometry.

### 4.3 Cons / Risks

- Limited “wiggle”:
  - Edges stay straight between jittered vertices; the effect is more about lumpy shapes than truly wavy borders.
- Geometry footprint:
  - Jittering too aggressively can create very skinny triangles or self‑intersections; we need conservative bounds and maybe a sanity filter.
- Regeneration:
  - Any change to jitter parameters requires regenerating Voronoi cells (`generate-voronoi!`) to take effect.

---

## 5. Option C – Rounded / Buffered Cells via JTS

**Idea:** Use JTS operations (e.g. `Polygon.buffer`) to round corners and soften sharp angles, producing smoother silhouettes. This leans on the geometry library instead of manual vertex tweaks.

### 5.1 Implementation Sketch

- Generation step:
  - For each `Polygon` from JTS:
    - Apply a small buffer operation with round joins (positive or negative radius depending on whether we want inflated or slightly inset shapes).
    - Convert the buffered result back through `polygon->cell` or a parallel helper.
  - Cache both the original and buffered polygons if we need them for other systems.
- Settings:
  - `:corner-radius` – world‑space radius passed into `buffer`.
  - `:rounding-style` – `:none`, `:rounded`, `:soft-lobed` (different buffer parameters).

### 5.2 Pros

- High‑level:
  - JTS handles the heavy geometry math and can avoid many invalid polygon edge cases.
- “Soft” look:
  - Rounding corners removes the sharp, high‑valence joints that feel most artificial.

### 5.3 Cons / Risks

- Complexity and cost:
  - Buffering every polygon can be significantly more expensive than our current generation.
  - The buffer output may be multi‑polygon in pathological cases, which our cell representation doesn’t currently model.
- Less control over style:
  - JTS rounding is smooth but not necessarily “wiggly”; it may feel more like blobby blobs than procedural patterns.

---

## 6. Option D – Domain‑Warped Voronoi (Organic at the Metric Level)

**Idea:** Instead of modifying edges after the fact, change the space the Voronoi diagram is computed in. We map stars into a warped coordinate system (using noise), compute Voronoi there, then map back. The resulting cells can have curved, irregular boundaries.

### 6.1 Implementation Sketch

- Domain warping:
  - Create a noise generator via `silent-king.galaxy/create-noise-generator` (or a dedicated Voronoi instance).
  - For each star `{ :x :y }`, compute a warped position:
    - `x' = x + f_x(noise, x, y)`
    - `y' = y + f_y(noise, x, y)`
    - Offsets are small compared to inter‑star spacing.
  - Run `VoronoiDiagramBuilder` on warped coordinates, but still associate cells back to real stars by ID.
- Rendering:
  - Cells store warped `:vertices` in world space; everything else (culling, transforms, drawing) stays the same.

### 6.2 Pros

- Deeply organic:
  - Cells become irregular in a cohesive way; the entire diagram reflects the same underlying noise field.
- Reuses existing infrastructure:
  - JTS + `generate-voronoi` shape remains; only input coordinates change.

### 6.3 Cons / Risks

- Global impact:
  - Star positions stay fixed, but their regions warp; this can subtly change perceived proximity between stars and boundaries.
- Tuning complexity:
  - Small changes in warp amplitude/frequency have large visual effects.
  - Harder to reason about extreme cases without careful bounds and tests.

---

## 7. Visual Softness Without Geometry Changes

Even without geometric wiggle, we can soften the perceived hard lines by tweaking how we draw them:

- Edge alpha and blending:
  - Lower stroke opacity at mid zooms; rely more on fills for the “cell feel”.
  - Use slight opacity gradients by drawing multiple strokes with decreasing alpha and increasing width.
- Color choices:
  - Favor closer lightness between neighboring cells to reduce the contrast of boundaries.
  - Tie color variation to density or degree so edges feel like part of a field, not hard partitions.
- Blur‑like effects:
  - Fake a soft edge by drawing a wide, faint stroke under the main stroke (already cheap with Skija).

These tweaks could be layered on top of any of the geometry options above.

---

## 8. Recommended Path Forward

1. **Prototype Option A (screen‑space wiggle):**
   - Quick to iterate in `draw-voronoi-cells`.
   - Easy to gate behind `:edge-style` and zoom thresholds.
   - Gives us immediate visual feedback on how much wiggle feels good.
2. **Evaluate Option B (world‑space jitter) for static organic shapes:**
   - If we like organic outlines but want to keep edges straight, this is a low‑maintenance alternative.
3. **Defer JTS buffering and domain warping (Options C/D):**
   - Keep them as follow‑up experiments if we want even softer, more “alien” cells and are willing to pay some geometry complexity.

Once we like a style, we can expand the Voronoi settings panel to include:

- `:edge-style` (`:straight`, `:wavy`).
- `:wiggle-amplitude` and `:wiggle-frequency`.
- Optional `:vertex-jitter-amount` for world‑space irregularity.

