# Remove ECS and Use Pure Data for the World

This document is a concrete implementation plan for removing the current ECS layer
(`:entities` + `create-entity` / `add-entity!` / `filter-entities-with`, etc.)
and moving to a plain, domain-specific world model for stars and hyperlanes.

It focuses on the **galaxy/world model**; the Reactified UI remains a separate,
pure-data UI layer that consumes whatever representation we choose here.

---

## 1. Goals and Non‑Goals

### 1.1 Goals

- Represent the galaxy as **plain data**, not generic entities:
  - Stars and hyperlanes live in clearly named collections (e.g. `:stars`, `:hyperlanes`).
  - Callers use ordinary Clojure data operations (map, filter, group‑by) rather than component queries.
- Remove ECS helpers from runtime code:
  - Eliminate `:entities` from `game-state`.
  - Delete `create-entity`, `add-entity!`, `get-entity`, `update-entity!`, `remove-entity!`,
    `filter-entities-with`, and `find-entities-with-all`.
- Keep **behavior intact**:
  - Galaxy generation (spiral arms + noise) produces effectively the same spatial distributions.
  - Hyperlane generation via Delaunay is unchanged, just wired to the pure star list.
  - Selection, minimap, star inspector, hyperlane settings, and performance metrics still work.
  - Reactified UI tests remain green with updated helpers.

### 1.2 Non‑Goals

- Do **not** change the core camera math (`silent-king.camera`) beyond what’s required for type changes.
- Do **not** redesign the Reactified UI architecture in this pass:
  - We’re only changing how the world is stored and read.
  - Rendering can continue to be orchestrated from `silent-king.core/draw-frame`.
- Do **not** introduce a new ECS for other future entities (fleets, stations, etc.) yet.
  - If we need that later, we can design it with the new world model in mind.

---

## 2. Current ECS Usage (Runtime‑Critical Only)

Runtime namespaces currently using ECS helpers:

- `src/silent_king/state.clj`
  - Defines the ECS surface:
    - `:entities` in `create-game-state`.
    - Entity helpers: `create-entity`, `add-entity!`, `get-entity`, `update-entity!`, `remove-entity!`,
      `get-all-entities`.
    - Component helpers: `get-component`, `add-component`, `remove-component`, `has-component?`,
      `has-all-components?`.
    - Query helpers: `filter-entities-with`, `find-entities-with-all`.
- `src/silent_king/galaxy.clj`
  - `generate-galaxy-entities!`:
    - Creates stars via `state/create-entity` with components
      `:position`, `:renderable`, `:transform`, `:physics`, `:star`.
    - Inserts into `game-state` via `state/add-entity!`.
- `src/silent_king/hyperlanes.clj`
  - `generate-delaunay-hyperlanes!`:
    - Reads stars via `state/filter-entities-with game-state [:position]`.
    - Creates hyperlane entities (components `:hyperlane` + `:visual`) via `state/create-entity`.
    - Inserts via `state/add-entity!`.
  - `draw-all-hyperlanes`:
    - Uses `state/filter-entities-with game-state [:hyperlane]` and `state/get-component` for the line endpoints.
- `src/silent_king/core.clj`
  - `draw-frame`:
    - Reads star entities via `state/filter-entities-with game-state [:position :renderable :transform :physics]`.
    - Counts hyperlane entities via `state/filter-entities-with game-state [:hyperlane]`.
    - Counts widget entities via `state/filter-entities-with game-state [:widget]` (currently zero in practice).
- `src/silent_king/selection.clj`
  - `hyperlane-connections`, `star-details`, `pick-star`:
    - Use `state/filter-entities-with` to find hyperlanes and pickable stars.
    - Use `state/get-entity` + `state/get-component` to navigate from star IDs and hyperlane endpoints.
- `src/silent_king/reactui/components/minimap.clj`
  - `minimap-props`:
    - Builds `:stars` from `(state/filter-entities-with game-state [:position])`.

Runtime tests that depend on the ECS:

- `test/silent_king/selection_test.clj`
  - Uses `state/create-entity` and `state/add-entity!` to build test stars and hyperlanes.
- `test/silent_king/reactui/app_test.clj`
  - `create-test-star` builds a star via `state/create-entity`.
  - Adds it via `state/add-entity!` to test star inspector behavior.
- `test/silent_king/reactui/events_test.clj`
  - `star-entity` helper uses `state/create-entity` and `state/add-entity!` for zoom-to-selection tests.

Notes:

- Legacy widget/UI tests under `test/silent_king/widgets` and `test/silent_king/ui` also use ECS,
  but they are not wired into `test_runner` and can be treated as historical reference.
- Markdown docs (`WidgetToolkit.md`, etc.) mention ECS extensively; we plan to **remove** these
  once the migration is complete, replacing them with pure‑data documentation.

---

## 3. Target Pure Data World Model (Decided)

This section reflects the **finalized** world model we’ll implement, based on your choices.

### 3.1 World storage on `game-state`

The world will be stored as **top‑level keys** on `game-state`:

```clj
{:stars                {star-id star-map, ...}
 :hyperlanes           [hyperlane0 hyperlane1 ...]
 :neighbors-by-star-id {star-id [{:neighbor-id ... :hyperlane hyperlane-map} ...]}
 ;; existing keys:
 :camera {...}
 :selection {...}
 :ui {...}
 :metrics {...}
 ;; etc.
}
```

- `:stars` is a **map keyed by star id**.
- `:hyperlanes` is a **vector** of hyperlane maps.
- `:neighbors-by-star-id` is a **precomputed adjacency index** for selection and the star inspector.

### 3.2 Star shape

Stars currently live as entities with components:

- `:position {:x :y}`
- `:renderable {:path sprite-path}`
- `:transform {:size px-size :rotation 0.0}`
- `:physics {:rotation-speed rad/s}`
- `:star {:density 0..1}`

Pure star map (per star):

```clj
{:id              long
 :x               double     ; world-space position
 :y               double
 :size            double     ; base size in pixels (before zoom transform)
 :density         double
 :sprite-path     string     ; same as :renderable :path
 :rotation-speed  double}    ; rad/s, used to compute visual rotation
```

Collection shape (decided):

- `:stars` is a **map keyed by id**:

  ```clj
  :stars {id star-map, ...}
  ```

- We do **not** keep a separate vector of stars; iteration uses `(vals (:stars @game-state))`.

Star IDs:

- Allocated via a **monotonic counter** stored in `state` (e.g. `next-star-id`).
- We keep a helper to **reset IDs in tests** to preserve deterministic expectations like `"Star #1"`.

### 3.3 Hyperlane shape

Hyperlanes currently live as entities with components:

- `:hyperlane {:from-id star-id
               :to-id   star-id}`
- `:visual {:base-width double
           :color-start argb-long
           :color-end   argb-long
           :glow-color  argb-long
           :animation-offset double}`

Pure hyperlane map:

```clj
{:id               long          ; optional, may be implicit index
 :from-id          long          ; star id
 :to-id            long          ; star id
 :base-width       double
 :color-start      long
 :color-end        long
 :glow-color       long
 :animation-offset double}
```

Collection:

- `:hyperlanes [hyperlane0 hyperlane1 ...]`
- Adjacency index:
  - `:neighbors-by-star-id {star-id [{:neighbor-id ... :hyperlane hyperlane-map} ...]}`
  - Built after hyperlane generation and updated whenever hyperlanes change.

Hyperlane IDs:

- Also allocated via a monotonic counter, so they are stable and testable if needed.

### 3.4 Selection and camera

We keep existing selection & camera shapes:

- Selection:
  - `[:selection :star-id]` remains a star ID (not a star map).
  - Star details/inspector views are recomputed from `:stars` / `:neighbors-by-star-id`.
- Camera:
  - Unchanged: `:camera {:zoom ... :pan-x ... :pan-y ...}`.

This keeps a simple ID‑based contract while changing only how we **resolve** IDs to stars.

---

## 4. Migration Strategy (Phased)

We want to avoid a flag day refactor. The plan below proceeds in phases.

### Phase 0 – Lock Data Model Decisions

**Status:** Completed (this document already encodes the decisions).

- World lives under top‑level `:stars`, `:hyperlanes`, `:neighbors-by-star-id`.
- `:stars` is a map keyed by id; `:hyperlanes` is a vector.
- We precompute adjacency in `:neighbors-by-star-id`.
- IDs are assigned via monotonic counters with a reset helper for tests.
- Minimap colors are derived from star density, not a stored `:color` field.
- `:widget-count` metric will be removed from performance overlay.
- Old ECS design docs will be removed once migration is complete.

### Phase 1 – Introduce Pure World Representation (Alongside ECS)

**Objective:** Add pure `:stars` / `:hyperlanes` / `:neighbors-by-star-id` representation while leaving the existing ECS fully intact.

Steps:

1. Extend `silent-king.state/create-game-state`:
   - Add empty world keys:
     ```clj
     :stars                {}
     :hyperlanes           []
     :neighbors-by-star-id {}
     ```
2. Add world accessors to `silent-king.state`:
   - Examples:
     - `stars` → returns the `:stars` map.
     - `hyperlanes` → returns the `:hyperlanes` vector.
     - `neighbors-by-star-id` → returns the adjacency map.
     - `star-by-id` → convenience lookup `(get (:stars @game-state) id)`.
     - `reset-world-ids!` → test helper that resets star/hyperlane ID counters.
3. Update `silent-king.galaxy`:
   - Introduce a new pure function:
     ```clj
     (defn generate-galaxy
       [star-images num-stars]
       ;; returns {:stars {id star-map, ...}
       ;;          :next-star-id next-id}
       )
     ```
   - Change `generate-galaxy-entities!` into a thin wrapper that:
     - Calls `generate-galaxy`.
     - Writes the resulting star map into `:stars` on `game-state`.
     - For the duration of the transition, also writes ECS entities into `:entities`
       (dual write) so existing callers keep working.
4. Update `silent-king.hyperlanes`:
   - Introduce a pure function:
     ```clj
     (defn generate-hyperlanes
       [stars]
       ;; returns {:hyperlanes [...]
       ;;          :neighbors-by-star-id {...}
       ;;          :next-hyperlane-id next-id}
       )
     ```
   - Change `generate-delaunay-hyperlanes!` to:
     - Read stars from the new `:stars` map.
     - Write hyperlanes into `:hyperlanes` and `:neighbors-by-star-id`.
     - Optionally still create ECS hyperlane entities during the transition.
5. Wire Phase‑1 into startup (`silent-king.core/-main`):
   - After loading assets:
     - Call the new pure generation functions.
     - Store results into top‑level `:stars`, `:hyperlanes`, and `:neighbors-by-star-id`.
     - Optionally continue populating ECS for now (until Phase 2 is complete).

Outcome:

- `game-state` has first‑class `:stars`, `:hyperlanes`, and `:neighbors-by-star-id` in addition to ECS.
- All existing ECS consumers keep working unchanged.
- New code can start using the pure world model.

### Phase 2 – Migrate Read Sites to Pure World Model

**Objective:** Move all world consumers away from ECS helpers and onto the pure world API,
while leaving ECS writes in place temporarily.

Steps by namespace:

1. `silent-king.core/draw-frame`:
   - Replace:
     - `state/filter-entities-with game-state [:position :renderable :transform :physics]`
   - With:
     - `(vals (state/stars game-state))` (or a helper `state/star-seq`).
   - Replace entity component unpacking (`get-component`) with direct star fields:
     - `:x`, `:y`, `:size`, `:rotation-speed`, `:sprite-path`.
   - Replace hyperlane entity counting with:
     - `(count (state/hyperlanes game-state))`.
   - Remove the widget entity count (`filter-entities-with [:widget]`) and the corresponding
     `:widget-count` metric from the performance overlay.

2. `silent-king.hyperlanes/draw-all-hyperlanes`:
   - Replace hyperlane entity queries with:
     - `(state/hyperlanes game-state)`.
   - Replace star lookup via `get-in all-entities [:entities from-id]` with:
     - `(state/star-by-id game-state from-id)` and likewise for `to-id`.
   - Replace `state/get-component` calls with direct field access on star/hyperlane maps.

3. `silent-king.selection`:
   - `hyperlane-connections`:
     - Replace `state/filter-entities-with [:hyperlane]` and `state/get-entity` with:
       - Iteration over `(state/hyperlanes game-state)` or direct use of `:neighbors-by-star-id`,
         plus `state/star-by-id` lookups.
   - `star-details`:
     - Replace `state/get-entity` + `get-component` with `state/star-by-id` and direct fields.
   - `pick-star`:
     - Replace `state/filter-entities-with game-state pick-components` with:
       - Direct iteration over `(vals (state/stars game-state))`.
   - Keep the selection API unchanged (still storing `:star-id`).

4. `silent-king/reactui/components/minimap`:
   - Replace `state/filter-entities-with game-state [:position]` with:
     - `(vals (state/stars game-state))`, mapping each star into `{ :x :y }`.
   - Derive minimap colors **from density** (or counts) instead of reading a `:color` field:
     - Reuse the heatmap machinery in `reactui.render` that already maps densities/counts to colors.

5. Tests:
   - `selection_test`:
     - Replace `add-test-star` to modify `:stars` and `:hyperlanes` directly, using the same
       star/hyperlane shapes as the runtime world model.
     - Use the ID reset helper so expectations like `"Star #1"` still hold.
   - `reactui/app_test` and `reactui/events_test`:
     - Replace helpers that call `state/create-entity` + `state/add-entity!` with:
       - New helpers that update `:stars` and `:hyperlanes` directly, or use a shared
         test utility namespace (e.g. `silent-king.test-fixtures`) to construct test worlds.
     - Ensure zoom‑to‑selection tests still pass using `state/star-by-id`.

During Phase 2, `state/create-entity` and friends still exist, but:

- Runtime code **no longer calls** `filter-entities-with` / `get-entity` / `get-component` for world objects.
- Only transitional helpers or unused legacy paths refer to ECS.

### Phase 3 – Remove ECS Data and API

**Objective:** Delete ECS storage and helpers once all meaningful call sites are migrated.

Steps:

1. `silent-king.state`:
   - Remove:
     - `:entities` from `create-game-state`.
     - `create-entity`, `add-entity!`, `get-entity`, `update-entity!`, `remove-entity!`,
       `get-all-entities`, `add-component`, `remove-component`, `get-component`,
       `has-component?`, `has-all-components?`, `filter-entities-with`, `find-entities-with-all`.
   - Repurpose `entity-id-counter` and `reset-entity-ids!` into:
     - `next-star-id`, `next-hyperlane-id`, and `reset-world-ids!` (or similar).

2. Runtime code:
   - Ensure `silent-king.core`, `silent-king.galaxy`, `silent-king.hyperlanes`, `silent-king.selection`,
     and `silent-king.reactui.components.minimap` no longer reference any removed ECS functions.

3. Tests:
   - Remove any lingering uses of ECS helper functions.
   - If needed, add new helper functions for constructing pure world fixtures:
     - E.g. `test/create-test-world`, `test/add-test-star`, etc.

4. Docs:
   - Update `Reactified.md` and `PureDataTradeOffs.md`:
     - Reflect the new pure world model and removal of ECS.
   - Remove ECS-specific design docs such as `WidgetToolkit.md` and any other Markdown that
     assumes widget entities; replace with a small `GalaxyModel.md` or equivalent if we
     want dedicated world-model documentation.

Outcome:

- `game-state` no longer has `:entities`.
- ECS helper functions are gone.
- Stars and hyperlanes are pure data on the top‑level `:stars` / `:hyperlanes` keys.

### Phase 4 – Follow‑Ups (Optional)

Potential follow‑up tasks (not required for the initial migration):

- Add a dedicated `silent-king.galaxy.model` namespace to house:
  - World data shape, invariants, and pure helpers.
  - Functions like `nearest-star`, `neighbors-of`, `galaxy-bounds`.
- Add serialization / deserialization helpers for the world (particularly `:stars`, `:hyperlanes`,
  `:neighbors-by-star-id`) for debugging and tooling.
- Consider integrating a ReactUI `:galaxy` primitive later, now that the model is pure.

---

## 5. Risks and Mitigation

### 5.1 Behavior drift

Risk:

- Small differences in how we compute or store positions / sizes / densities could change visuals subtly.

Mitigation:

- Keep the pure generators (`generate-galaxy`, `generate-hyperlanes`) as thin refactors of the existing ECS versions.
- Write focused tests where appropriate:
  - E.g. ensure the number of generated stars/hyperlanes stays the same for a given seed.

### 5.2 Selection and hyperlane adjacency

Risk:

- `selection.hyperlane-connections` currently relies on entity queries; a mistake in adjacency computation could break inspector connections.

Mitigation:

- Initially compute adjacency from the `:hyperlanes` vector using simple loops.
- Once correct, store it in `:neighbors-by-star-id` and keep a single implementation path.

### 5.3 Test churn

Risk:

- Tests that build ECS entities will need to be updated; mistakes could mask subtle regressions.

Mitigation:

- Add a small set of well‑named test helpers for constructing pure world fixtures and use them consistently.
- Keep `reset-world-ids!` (or similar) so test IDs remain deterministic.

---

## 6. Summary

This plan gives us a staged way to:

- Introduce a first‑class, pure world model (`:stars`, `:hyperlanes`, `:neighbors-by-star-id`).
- Gradually migrate all consumers (rendering, selection, minimap, tests) to that model.
- Finally delete the ECS storage and helpers without losing behavior.

With the data-model decisions already locked in, the next step is to start implementing Phase 1
and Phase 2 in small, well‑tested slices. Once that is stable, Phase 3 (removing ECS) becomes
mostly mechanical. 

