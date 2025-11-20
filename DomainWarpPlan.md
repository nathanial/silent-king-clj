# DomainWarpPlan.md

Status: November 20, 2025 — design notes for adding domain‑warped Voronoi cells using our existing FastNoiseLite infrastructure. This builds on the high‑level ideas in `Voronoi.md` and `OrganicShapes.md`.

---

## 1. Goals & Mental Model

- Make Voronoi cells feel more organic by warping the *space* they are computed in, rather than only jittering edges after the fact.
- Keep the implementation:
  - Deterministic for a fixed world seed and Voronoi settings.
  - Pure at the geometry layer (`generate-voronoi` stays referentially transparent).
  - Optional and tunable via the Voronoi settings panel.
- Avoid breaking other systems:
  - Hyperlanes, star positions, and selection all continue to work off the original world coordinates.
  - Voronoi remains a visualization layer over the world model, not a new canonical distance metric.

The key design decision: **we warp the site coordinates that JTS sees when generating the Voronoi diagram, but we do *not* move the stars themselves**. Cells are drawn in the warped coordinate field, while star sprites stay at their original positions. We keep the warp amplitude small so the mismatch reads as organic variation rather than a bug.

---

## 2. High‑Level Approach

1. Add configurable, deterministic domain‑warp parameters to the Voronoi settings.
2. In `silent-king.voronoi`, construct a dedicated `FastNoiseLite` instance for the Voronoi overlay.
3. When building JTS sites in `generate-voronoi`, replace each star’s `{x, y}` with a warped `{x', y'}`:
   - `x' = x + warp-offset-x(x, y)`
   - `y' = y + warp-offset-y(x, y)`
4. Feed warped coordinates into `VoronoiDiagramBuilder`, but keep `:star-id` and original star maps attached.
5. Render cells using warped vertices, while still drawing stars at their original positions.

This gives us coherent, noise‑driven irregular cells without touching the star generation pipeline.

---

## 3. Configuration & Settings

### 3.1 New Voronoi Settings

Extend `default-voronoi-settings` in `src/silent_king/state.clj` to include:

- `:domain-warp?` — boolean, default `false`.
- `:warp-amplitude` — double, world‑space magnitude of the warp offset (e.g. 0–80). Acts as a global scale; we’ll clamp effective per‑star offsets later.
- `:warp-frequency` — double, frequency of the noise field (e.g. 0.0005–0.005 in world units).
- `:warp-octaves` (optional) — small integer (1–3) for richer structure; or we can bake this into the helper and omit from settings.
- `:warp-seed` (optional) — integer seed. If absent, derive from the galaxy/world seed.

Example updated default:

```clojure
(def default-voronoi-settings
  {:enabled?        true
   :opacity         0.8
   :line-width      2.0
   :color-scheme    :monochrome
   :show-centroids? false
   :domain-warp?    false
   :warp-amplitude  40.0
   :warp-frequency  0.0015})
```

Notes:

- `:domain-warp?` starts `false` so existing screenshots and tests remain valid until we opt in.
- We keep `:warp-seed` optional to avoid extra UI at first; the internal helper can pull a seed from the world or fall back to a fixed constant for deterministic results.

### 3.2 UI Integration (Later)

The Voronoi settings panel (`src/silent_king/reactui/components/voronoi_settings.clj`) can eventually expose:

- A toggle: “Domain‑warped cells”.
- A slider: “Warp strength” (maps to `:warp-amplitude`).
- Optionally, an “Advanced” dropdown with warp frequency presets.

This is UI work and does not block the core implementation.

---

## 4. Noise Setup for Domain Warping

We already ship `FastNoiseLite` under `src/java/silentking/noise/FastNoiseLite.java` and use it in `src/silent_king/galaxy.clj` for galaxy density.

For Voronoi domain warping, we want:

- A *separate* noise instance so we can tune frequency and amplitude independently from the galaxy arms.
- Determinism tied to the world seed, not to transient runtime state.

### 4.1 New Helper in `silent-king.voronoi`

At the top of `src/silent_king/voronoi.clj`, extend the imports:

```clojure
(:import
  [org.locationtech.jts.geom Coordinate Envelope GeometryFactory Polygon]
  [org.locationtech.jts.triangulate VoronoiDiagramBuilder]
  [silentking.noise FastNoiseLite FastNoiseLite$NoiseType]
  [io.github.humbleui.skija Canvas Paint PaintMode PaintStrokeCap Path])
```

Add a constructor for our warp noise:

```clojure
(defn- create-domain-warp-noise
  "Create a FastNoiseLite instance for Voronoi domain warping."
  [^long seed ^double frequency]
  (let [noise (FastNoiseLite. (int seed))]
    (.SetNoiseType noise FastNoiseLite$NoiseType/OpenSimplex2)
    (.SetFrequency noise (float frequency))
    noise))
```

We keep the configuration deliberately simple: 2D OpenSimplex, single frequency, and an amplitude we control outside the noise generator.

### 4.2 Warp Offset Function

Define a pure helper to compute a warp offset for one point:

```clojure
(defn- warp-offset
  "Return {:dx :dy} world-space offset for domain warping at (x, y)."
  [^FastNoiseLite noise
   ^double amplitude
   ^double x
   ^double y]
  (if (or (nil? noise) (<= amplitude 0.0))
    {:dx 0.0 :dy 0.0}
    (let [;; Sample two decorrelated noise channels for x/y offsets.
          nx (.GetNoise noise (float x) (float y))
          ny (.GetNoise noise (float (+ x 1000.0)) (float (+ y 1000.0)))
          ;; FastNoiseLite GetNoise returns roughly [-1, 1].
          dx (* amplitude (double nx))
          dy (* amplitude (double ny))]
      {:dx dx :dy dy})))
```

For better control, we can later:

- Normalize or clamp `(dx, dy)` relative to the local cell size.
- Use a separate frequency for `ny` (e.g. scaled inputs) if we want more complex patterns.

---

## 5. Warped Sites in `generate-voronoi`

The core change is to build JTS `Coordinate` instances from warped positions when domain warping is enabled.

### 5.1 Extend `generate-voronoi` Signature

Current signature (simplified) in `src/silent_king/voronoi.clj`:

```clojure
(defn generate-voronoi
  "Pure Voronoi generator. Accepts a sequence of star maps with :id/:x/:y and
  returns {:voronoi-cells {id cell} :elapsed-ms n}."
  [stars]
  ...)
```

Update it to be multi‑arity and accept an optional warp config:

```clojure
(defn generate-voronoi
  "Pure Voronoi generator. Accepts a sequence of star maps with :id/:x/:y and
  optional warp-config:
   {:domain-warp? boolean
    :warp-amplitude double
    :warp-frequency double
    :warp-seed long}
  Returns {:voronoi-cells {id cell} :elapsed-ms n}."
  ([stars]
   (generate-voronoi stars nil))
  ([stars warp-config]
   ...))
```

- The existing tests call the 1‑arity version and will continue to work without changes.
- The 2‑arity path enables domain warping when provided with a non‑nil `warp-config`.

### 5.2 Building Warped Sites

Inside the 2‑arity implementation, replace the `sites` construction:

```clojure
  (let [start (System/currentTimeMillis)
        {:keys [domain-warp? warp-amplitude warp-frequency warp-seed]} warp-config
        warp-amplitude (double (or warp-amplitude 0.0))
        warp-frequency (double (or warp-frequency 0.0))
        ;; Clamp to safe ranges
        warp-amplitude (clamp warp-amplitude 0.0 200.0)
        warp-frequency (clamp warp-frequency 0.0 0.01)
        warp-enabled? (and domain-warp?
                           (> warp-amplitude 0.0)
                           (> warp-frequency 0.0))
        ;; Seed strategy: prefer explicit seed, otherwise derive from something
        ;; stable like the star count / hash of positions.
        derived-seed (long (hash (map (juxt :id :x :y) stars)))
        noise-seed (if (number? warp-seed) warp-seed derived-seed)
        noise (when warp-enabled?
                (create-domain-warp-noise noise-seed warp-frequency))
        sites (mapv (fn [{:keys [id x y] :as star}]
                      (let [x (double (or x 0.0))
                            y (double (or y 0.0))
                            {:keys [dx dy]} (warp-offset noise warp-amplitude x y)
                            wx (+ x dx)
                            wy (+ y dy)]
                        {:id    id
                         :coord (Coordinate. wx wy)
                         :star  star
                         ;; Optional: debug / potential UI use
                         :warp-position {:x wx :y wy}}))
                    stars)
        site-count (count sites)]
    ...))
```

Key points:

- When warping is disabled (`warp-enabled?` false), `noise` is `nil` and `warp-offset` returns `(0, 0)`, so behavior matches today.
- When enabled, each star’s coordinate is shifted by a small, deterministic offset.
- We keep `:star` as the original star map and optionally stash the warped position under `:warp-position` for debugging or future UI overlays.

The rest of `generate-voronoi` (envelope computation, `VoronoiDiagramBuilder`, `polygon->cell`) can remain unchanged.

### 5.3 Clip Envelope Considerations

`star-envelope` currently computes an `Envelope` from the original star coordinates, then `expand-envelope` pads it out. With domain warp:

- Warped positions may lie slightly outside the unwarped envelope.
- Because we already add `clip-padding-min` and scale‑based padding, the existing logic likely suffices.
- If we see clipped cells at extreme warp amplitudes, we can:
  - Include warped positions when computing the envelope, or
  - Increase `clip-padding-min` slightly when domain warping is enabled.

---

## 6. Integrating With `generate-voronoi!`

`generate-voronoi!` in `src/silent_king/voronoi.clj` currently:

```clojure
(defn generate-voronoi!
  "Generate Voronoi cells from the current world's stars and persist them."
  [game-state]
  (let [stars (state/star-seq game-state)
        {:keys [voronoi-cells elapsed-ms] :as result} (generate-voronoi stars)]
    (state/set-voronoi-cells! game-state voronoi-cells)
    (swap! game-state assoc :voronoi-generated? true)
    (println "Generated" (count voronoi-cells) "Voronoi cells in" (or elapsed-ms 0) "ms")
    result))
```

We can now thread warp settings through:

```clojure
(defn- voronoi-warp-config
  "Extract warp config from game-state Voronoi settings."
  [game-state]
  (let [settings (state/voronoi-settings game-state)]
    {:domain-warp?   (boolean (:domain-warp? settings))
     :warp-amplitude (double (or (:warp-amplitude settings) 0.0))
     :warp-frequency (double (or (:warp-frequency settings) 0.0))
     ;; Optional: pull from a world-seed key on game-state instead.
     :warp-seed      (long (or (:warp-seed settings) 0))}))

(defn generate-voronoi!
  [game-state]
  (let [stars  (state/star-seq game-state)
        wcfg   (voronoi-warp-config game-state)
        {:keys [voronoi-cells elapsed-ms] :as result}
        (generate-voronoi stars wcfg)]
    (state/set-voronoi-cells! game-state voronoi-cells)
    (swap! game-state assoc :voronoi-generated? true)
    (println "Generated" (count voronoi-cells) "Voronoi cells in" (or elapsed-ms 0) "ms"
             (when (:domain-warp? wcfg) "(domain-warped)"))
    result))
```

This keeps domain warping a *view concern* driven entirely by settings on `game-state`.

---

## 7. Visual Behavior & Tuning

### 7.1 Expected Look

- With small warp amplitudes (e.g. 10–40 units on a galaxy spanning thousands of units):
  - Cell borders bend subtly around stars; larger regions pick up wavy edges.
  - Stars remain near the center of their cells but not perfectly centered.
- With larger amplitudes:
  - Cells become more irregular and can drift away from their owning stars.
  - At extremes, regions can look “melty” or tangled; we should avoid exposing such extremes in the UI.

### 7.2 Zoom Interaction

To keep the overlay readable and performant:

- At far zoom:
  - Either disable Voronoi entirely (as already supported) or force `domain-warp? false`.
- At mid zoom:
  - Allow domain warping but keep `warp-amplitude` low, emphasizing subtle unevenness over obvious waves.
- At close zoom:
  - Higher amplitude is acceptable; players can appreciate the detail.

This can be implemented either by:

- Dynamically scaling the effective `warp-amplitude` based on `zoom` inside `voronoi-warp-config`, or
- Keeping warp amplitude fixed and relying on the existing LOD logic in `draw-voronoi-cells`.

---

## 8. Testing Strategy

### 8.1 Unit Tests for `generate-voronoi`

Extend `test/silent_king/voronoi_test.clj`:

- Preserve existing tests:
  - They exercise the 1‑arity `generate-voronoi` and should continue to pass with `domain-warp?` off by default.
- New tests:
  - `generate-voronoi-domain-warp-basic`:
    - Call `(generate-voronoi stars {:domain-warp? true :warp-amplitude 40.0 :warp-frequency 0.001 :warp-seed 12345})`.
    - Assert:
      - All stars still get cells.
      - Cells have non‑empty `:vertices` and valid `:bbox`/`:centroid`.
      - Centroids remain within a reasonable distance of their stars (slightly relaxed bounds vs. non‑warped case).
  - Determinism:
    - Call `generate-voronoi` twice with identical `stars` and `warp-config`; assert equal `:voronoi-cells` maps.
  - Off switch:
    - With `:domain-warp? false` or zero amplitude/frequency, `generate-voronoi` results should be equal (or extremely close) to the old implementation. For robustness, we can compare key invariants rather than full geometry if floating‑point differences are a concern.

### 8.2 Visual / Manual QA

- Capture before/after screenshots with:
  - Small test galaxies (tens of stars).
  - Typical production‑sized galaxies.
- Vary:
  - Warp amplitude and frequency.
  - Zoom level.
- Check:
  - Stars still visually “own” their cells (no egregious misalignments).
  - No obvious clipping, gaps, or self‑intersecting cells.

---

## 9. Performance Considerations

- Generation‑time cost:
  - For each star, we add a small number of noise samples and arithmetic ops.
  - Voronoi diagram construction remains the dominant cost; the added overhead should be negligible compared to JTS.
- Runtime cost:
  - Domain warping occurs only during generation; per‑frame rendering cost is unchanged (same number of vertices per cell).
- Memory:
  - Optionally storing `:warp-position` per site and/or per cell adds a modest overhead but can be omitted if we don’t need it.

If later we combine domain warping with screen‑space edge wiggle, the main budget pressure will come from increased vertex counts; we can mitigate this with LOD (fewer segments at low zoom) and hard caps on total path points per frame.

---

## 10. Migration & Rollout Plan

1. **Phase 1 – Core implementation**
   - Add domain‑warp settings and `create-domain-warp-noise` / `warp-offset`.
   - Extend `generate-voronoi` and `generate-voronoi!` to support `warp-config`.
   - Keep `:domain-warp?` default `false`.
2. **Phase 2 – Testing**
   - Update `voronoi_test.clj` with the new tests described above.
   - Run `./run-tests.sh` and play through typical galaxy sizes.
3. **Phase 3 – UI & UX**
   - Expose `:domain-warp?` and `:warp-amplitude` in the Voronoi settings panel.
   - Add a tooltip explaining that domain warping makes cells more organic and may move cell boundaries slightly away from perfect geometric centers.
4. **Phase 4 – Polish**
   - Tune default amplitudes and frequencies based on visual feedback.
   - Optionally, tie warp amplitude to `:density` or local graph degree so denser regions feel more distorted.

With this plan, we can add genuinely organic Voronoi cells as a layered enhancement, without destabilizing the core galaxy model or existing visuals. Once implemented and tuned, we can reference this document from `Voronoi.md` and `OrganicShapes.md` as the canonical guide to the domain‑warped overlay. 

