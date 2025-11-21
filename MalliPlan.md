# Malli Integration Plan

High-level plan for adopting [malli](https://github.com/metosin/malli) in Silent King. Focus is on **documenting and enforcing the world model and UI state** without hurting frame-time performance.

---

## 1. Goals & Constraints

- **Goals**
  - Make the implicit data contracts in `silent-king.state` and friends explicit, executable, and testable.
  - Catch invalid world or UI data **at boundaries** (generation, asset loading, event handling, tests) instead of deep in rendering.
  - Provide a shared vocabulary (`Star`, `Planet`, `GameState`, `RenderState`, etc.) that docs, tests, and code all agree on.
  - Enable schema-driven tooling later (generators, data migrations, REPL inspection).
- **Non-Goals**
  - No per-frame validation in hot render loops.
  - Don’t fully spec every transient or optional key on day one; start with critical structures (world model, core UI, metrics).

---

## 2. Wiring malli into the Project

### 2.1 Dependency

- Add malli as a dependency in `deps.edn`:

  ```clojure
  :deps { ;; existing deps ...
         metosin/malli {:mvn/version "X.Y.Z"}}
  ```

  - Use the latest stable `X.Y.Z` from malli’s README/CHANGELOG.
  - No new aliases are required; `:test` and `:lint` can reuse it.

### 2.2 Namespace Layout

- Introduce a dedicated schema namespace to keep concerns separate:
  - `silent-king.malli` **or** `silent-king.schemas` (preferred: `silent-king.schemas`).
- Guideline:
  - Keep schemas for the core runtime model in this namespace:
    - `Star`, `Planet`, `Hyperlane`, `Neighbor`, `VoronoiCell`, `Region`.
    - `Camera`, `Input`, `Time`, `Assets`, `Selection`, `UI`, `Metrics`, `PerformanceMetrics`.
    - `GameState`, `RenderState`.
  - For feature-specific schemas (React UI tree, tools), consider:
    - `silent-king.reactui.schemas`
    - `silent-king.tools.schemas`

### 2.3 Common Helpers

- In `silent-king.schemas` define reusable type aliases and helpers:

  ```clojure
  (ns silent-king.schemas
    (:require [malli.core :as m]
              [malli.util :as mu]))

  (def PositiveId [:and int? [:> 0]])
  (def NonNegDouble [:and double? [:>= 0.0]])
  (def WorldCoord [:map [:x double?] [:y double?]])
  ```

- Prefer descriptive aliases over repeating raw predicates.

---

## 3. Core World Model Schemas

The world model today lives primarily in `silent-king.state`, `silent-king.galaxy`, `silent-king.hyperlanes`, `silent-king.voronoi`, and `silent-king.regions`. The Markdown docs (`docs/GalaxyModel.md`, `docs/PlanetPlan.md`, `docs/Voronoi.md`, `docs/RelaxationPlan.md`) are helpful but **not always up to date** (e.g., hyperlane colors, Voronoi cell shape, galaxy generator output). For malli we will treat the **code** (especially `state.clj`, `galaxy.clj`, `hyperlanes.clj`, `voronoi.clj`, `regions.clj`) as the source of truth and update docs over time to match schemas.

### 3.1 Stars

- From `docs/GalaxyModel.md` and usages in `silent-king.galaxy`:

  ```clojure
  (def Star
    [:map
     [:id       PositiveId]
     [:x        double?]
     [:y        double?]
     [:size     NonNegDouble]
     [:density  NonNegDouble]
     [:sprite-path string?]
     [:rotation-speed double?]])
  ```

- Optional extensions (future):
  - Allow extra keys via `[:map {:closed false} ...]` during migration.

### 3.2 Planets

- From `docs/PlanetPlan.md` and the actual implementation in `silent-king.galaxy/generate-planets-for-star` / `generate-galaxy`:

  ```clojure
  (def Planet
    [:map
     [:id            PositiveId]
     [:star-id       PositiveId]
     [:radius        NonNegDouble]
     [:orbital-period NonNegDouble]
     [:phase         double?]
     [:size          NonNegDouble]
     [:sprite-path   string?]
     [:eccentricity  {:optional true} NonNegDouble]
     [:inclination   {:optional true} double?]])
  ```

- Invariants we may check in phase 2+:
  - `:orbital-period` > 0.
  - Reasonable radius bounds relative to galaxy scale.

### 3.3 Hyperlanes & Neighbors

- From `silent-king.hyperlanes` and `silent-king.color`:
  - `docs/GalaxyModel.md` still describes hyperlane colors as ARGB `long`s, but the code now uses rich color maps (HSV/RGB) via `silent-king.color`. Schemas should follow the **current color map representation**, not the older integer form.

  ```clojure
  (def HyperlaneColor
    [:map
     [:h double?]
     [:s double?]
     [:v double?]
     [:a {:optional true} double?]])

  (def Hyperlane
    [:map
     [:id               PositiveId]
     [:from-id          PositiveId]
     [:to-id            PositiveId]
     [:base-width       NonNegDouble]
     [:color-start      HyperlaneColor]
     [:color-end        HyperlaneColor]
     [:glow-color       HyperlaneColor]
     [:animation-offset double?]])

  (def Neighbor
    [:map
     [:neighbor-id PositiveId]
     [:hyperlane   Hyperlane]])
  ```

- Adjacency map shape (used by `:neighbors-by-star-id`):

  ```clojure
  (def NeighborsByStarId
    [:map-of PositiveId [:sequential Neighbor]])
  ```

### 3.4 Voronoi Cells & Regions

- Based on `silent-king.voronoi` (which supersedes some of the earlier notes in `docs/Voronoi.md`):
  - Cells are stored in `:voronoi-cells` as `{star-id cell-map}`.
  - Each cell currently has:
    - `:star-id` – the owner star id.
    - `:vertices` – vector of `{ :x :y }` world coordinates for the polygon.
    - `:bbox` – `{ :min-x :min-y :max-x :max-y }` for culling.
    - `:centroid` – `{ :x :y }` centroid.
    - `:on-envelope?` (optional) – if it touches the expanded envelope.
    - `:star` (optional) – cached star map.
    - `:relaxed?` / `:relaxed-site` (optional) – when Lloyd relaxation is enabled.

  ```clojure
  (def BBox
    [:map
     [:min-x double?]
     [:min-y double?]
     [:max-x double?]
     [:max-y double?]])

  (def VoronoiCell
    [:map
     [:star-id PositiveId]
     [:vertices [:sequential WorldCoord]]
     [:bbox BBox]
     [:centroid WorldCoord]
     [:on-envelope? {:optional true} boolean?]
     [:star {:optional true} Star]
     [:relaxed? {:optional true} boolean?]
     [:relaxed-site {:optional true} WorldCoord]])
  ```

- Based on `silent-king.regions`:
  - Regions live in `:regions` as `{region-id region-map}` where `region-id` is a keyword like `:region-0`.
  - Each region has:
    - `:id` keyword, `:name` string, `:color` (color map), `:star-ids` set of star ids, `:center` `{ :x :y }`, and `:sectors` map of sector-id → sector.
  - Each sector has:
    - `:id` keyword, `:name` string, `:color` (color map), `:star-ids` set of star ids, `:center` `{ :x :y }`, `:capital-id` star id.

  ```clojure
  (def RegionId keyword?)

  (def Sector
    [:map
     [:id keyword?]
     [:name string?]
     [:color any?]          ;; color map from silent-king.color
     [:star-ids [:set PositiveId]]
     [:center WorldCoord]
     [:capital-id PositiveId]])

  (def Region
    [:map
     [:id RegionId]
     [:name string?]
     [:color any?]
     [:star-ids [:set PositiveId]]
     [:center WorldCoord]
     [:sectors [:map-of keyword? Sector]]])
  ```

### 3.5 Game State Top Level

- `silent-king.state/create-game-state` defines the authoritative structure. We’ll mirror it:

  ```clojure
  (def GameState
    [:map
     [:stars                [:map-of PositiveId Star]]
     [:planets              [:map-of PositiveId Planet]]
     [:hyperlanes           [:sequential Hyperlane]]
     [:voronoi-cells        [:map-of PositiveId VoronoiCell]]
     [:regions              [:map-of keyword? Region]]
     [:neighbors-by-star-id NeighborsByStarId]

     [:next-star-id         int?]
     [:next-planet-id       int?]
     [:next-hyperlane-id    int?]

     [:camera   Camera]
     [:input    Input]
     [:time     Time]
     [:assets   Assets]
     [:widgets  Widgets]
     [:selection Selection]
     [:ui       UI]
     [:features Features]
     [:hyperlane-settings HyperlaneSettings]
     [:voronoi-settings   VoronoiSettings]
     [:voronoi-generated? boolean?]
     [:metrics Metrics]])
  ```

- Strategy:
  - Start with strict required keys for the **world model** and more permissive `{:closed false}` maps for UI/settings so evolution is cheap.
  - Once stable, tighten UI/metrics maps.

### 3.6 Render State

- From `silent-king.state/create-render-state`:

  ```clojure
  (def RenderState
    [:map
     [:window any?]
     [:context any?]
     [:surface any?]])
  ```

- We intentionally keep these opaque; their validity is controlled by LWJGL/Skija, not malli.

---

## 4. Validation Strategy: Where to Use malli

Key idea: **validate at boundaries**, not inside tight loops. This keeps malli’s overhead small while still catching structural bugs.

### 4.1 World Creation & Mutations

- Add boundary validation helpers in `silent-king.schemas`:

  ```clojure
  (defn assert-valid!
    [schema value context]
    (when-not (m/validate schema value)
      (throw (ex-info (str "Invalid " context)
                      {:context context
                       :schema  schema
                       :value   value
                       :errors  (m/explain schema value)}))))
  ```

- Use them in **debug/dev paths**, not deep in render:
  - `silent-king.state/create-game-state`
    - Optionally validate the initial map against `GameState` in dev/test.
  - `silent-king.state/set-world!`
    - Before swapping, validate the provided world map against a `WorldSnapshot` schema that includes only world keys + id counters.
  - `silent-king.state/add-star!`, `add-planet!`, `add-hyperlane!`
    - In dev/test, validate each inserted entity against `Star` / `Planet` / `Hyperlane`.

### 4.2 World Generators

- `silent-king.galaxy/generate-galaxy`
  - Actual signature (per `galaxy.clj`): returns only stars/planets and their next ids:

    ```clojure
    (def GeneratedGalaxy
      [:map
       [:stars          [:map-of PositiveId Star]]
       [:planets        [:map-of PositiveId Planet]]
       [:next-star-id   int?]
       [:next-planet-id int?]])
    ```

  - Older docs suggested bundling hyperlanes and adjacency into this structure; the current code splits that responsibility into `generate-galaxy` and `hyperlanes/generate-hyperlanes`, so schemas should mirror that separation.
  - Validate `GeneratedGalaxy` in tests and optionally in a `:dev` alias, not in production builds.

- `silent-king.hyperlanes/generate-hyperlanes`
  - Validate the pure return map against a `GeneratedHyperlanes` schema:

    ```clojure
    (def GeneratedHyperlanes
      [:map
       [:hyperlanes           [:sequential Hyperlane]]
       [:neighbors-by-star-id NeighborsByStarId]
       [:next-hyperlane-id    int?]
       [:elapsed-ms           int?]])
    ```

- `silent-king.voronoi` and region builders
  - Validate Voronoi and region maps after generation, when they’re relatively small and infrequent compared to render ticks.

### 4.3 UI & Reactified Layer

- For the React-style UI (`src/silent_king/reactui`):
  - Define schemas for the **UI element tree** (e.g. `[:enum :vstack :hstack :panel :button :label :slider :dropdown]` plus props).
  - Validate **root trees** produced by top-level components in dev/test:
    - Example: `silent-king.reactui.app/root` returns `UiTree`.
    - Validate `UiTree` before handing it to the renderer in tests.
  - Validate **event payloads**:
    - Schemas for events like `[:ui/set-zoom double?]`, `[:ui/toggle-hyperlanes]`, etc.
    - Dispatcher can `assert-valid!` before applying state transitions in dev builds.

### 4.4 Tests & Fixtures

- In `test/silent_king/test_fixtures.clj`:
  - Use malli schemas to assert that test helpers build valid worlds:
    - For example, `recompute-neighbors!` can validate the resulting `NeighborsByStarId` against `NeighborsByStarId` schema in tests.

- Add **schema-based regression tests**:
  - When a bug involves malformed data, add a test that:
    - Constructs the smallest failing `game-state`.
    - Asserts that `m/validate GameState` fails with a helpful reason.

---

## 5. Instrumentation & Tooling

### 5.1 Function Instrumentation

- Use `malli.instrument` to instrument selected functions in dev/test:

  ```clojure
  (ns silent-king.dev
    (:require [malli.instrument :as mi]
              [silent-king.schemas :as schemas]))

  (defn instrument!
    []
    (mi/instrument!))
  ```

- Attach `:malli/schema` metadata to functions we care about (e.g. world generators, state setters).
  - Example: annotate `generate-galaxy` and `generate-hyperlanes` with input/output schemas.

### 5.2 REPL Utilities

- Add REPL helpers in `silent-king.schemas` or a dev-only namespace:
  - `check-game-state!` – validate the global `game-state` atom against `GameState`.
  - `explain-game-state` – pretty-print `m/explain` errors with a focus on top-level keys and IDs.
  - `sample-star`, `sample-planet` – use `malli.generator` (optional dependency) to generate sample entities for debugging or prototyping.

### 5.3 CLI Hooks (Optional)

- Add a lightweight script or alias to validate a serialized world:
  - For example, if a future tool writes world snapshots to JSON/EDN, a `clojure -M:validate-world path.edn` command can read the file and validate it via malli.

---

## 6. Phased Rollout

### Phase 1 – Foundations (Schemas + Dev Helpers)

- Add `metosin/malli` to `deps.edn`.
- Implement `silent-king.schemas` with:
  - Core type aliases (`PositiveId`, `NonNegDouble`, etc.).
  - `Star`, `Planet`, `Hyperlane`, `Neighbor`, `NeighborsByStarId`.
  - `GameState` and `RenderState` with conservative, slightly open maps for UI/settings.
- Add `assert-valid!` and REPL helpers (`check-game-state!`, etc.).
- Add a few tests that:
  - Validate `(state/create-game-state)` against `GameState`.
  - Validate outputs of `generate-galaxy` against `GeneratedGalaxy` and `generate-hyperlanes` against `GeneratedHyperlanes` in test runs.

### Phase 2 – World Generators & Mutators

- Annotate and instrument:
  - `silent-king.galaxy/generate-galaxy`.
  - `silent-king.hyperlanes/generate-hyperlanes`.
  - `silent-king.state/set-world!`, `add-star!`, `add-planet!`, `add-hyperlane!`.
- Run `malli.instrument/instrument!` in `:test` and optionally `:dev` only.
- Turn on validation for:
  - World snapshots flowing into `set-world!`.
  - Entity insertion via `add-*` helpers when running tests.

### Phase 3 – Voronoi, Regions, UI & Events

- Add schemas for:
  - `VoronoiCell`, `Region`, and any associated config maps.
  - React UI element tree and event descriptors.
- Validate:
  - Voronoi and region generation outputs.
  - Root React UI trees in tests.
  - Event payloads before dispatch in dev/test.

### Phase 4 – Tightening & Tooling

- Gradually move from open maps (`{:closed false}`) to closed maps where appropriate:
  - Especially for world entities and performance metrics.
- Add targeted validations where bugs have historically occurred (e.g. missing `:sprite-path`, negative radii).
- Enhance REPL tooling:
  - Quick commands to diff `game-state` against `GameState` and highlight unexpected keys or type mismatches.

---

## 7. How This Improves the Codebase

- **Stronger contracts for world data**
  - Stars, planets, hyperlanes, Voronoi cells, and regions get executable schemas that match the current implementations in `state.clj` / generator namespaces; docs can be updated to follow these schemas.
  - `set-world!` and world generators can’t silently introduce corrupt structures.
- **Safer refactors**
  - Changes to `silent-king.state` or generators are checked against schemas in tests.
  - React UI and event handlers can rely on stable shapes for props and events.
- **Better debugging and tooling**
  - A broken world or UI state becomes a clear `ex-info` with a tree of malli errors, not a random NPE deep in rendering.
  - REPL helpers make it easy to inspect and validate `game-state` and test fixtures.
- **Incremental adoption**
  - We can start with world model + generators, then layer in Voronoi, regions, UI, and events as they stabilize, without blocking day‑to‑day development.
