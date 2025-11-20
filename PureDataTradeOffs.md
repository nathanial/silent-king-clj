# ECS vs Pure Data + Reactified Rendering

This document summarizes the tradeoffs of:

1. Keeping the current lightweight ECS for stars/hyperlanes and drawing them directly from `silent-king.core` / `silent-king.hyperlanes`, with Reactified UI layered on top.
2. Moving star + hyperlane rendering into the Reactified UI layer.
3. Going further and **removing the ECS**, representing the galaxy as pure data structures, and rendering everything (world + UI) via the Reactified pipeline.

The goal is not to mandate a decision, but to make the tradeoffs explicit so future refactors are deliberate rather than piecemeal.

> Status (November 20, 2025): Option 3 has been implemented. The ECS layer and `:entities` map are gone; stars and hyperlanes now live as pure data on `game-state` (`:stars`, `:hyperlanes`, `:neighbors-by-star-id`) with monotonic ID counters. The rest of this document remains for historical context and for evaluating future refactors.

---

## 1. Where We Are Today

### 1.1 World representation (current)

- `silent-king.state/create-game-state` returns a single `game-state` map wrapped in an atom with **domain keys**, not ECS entities:
  - `:stars {id {:id ... :x ... :y ... :size ... :density ... :sprite-path ... :rotation-speed ...}}`
  - `:hyperlanes [{:id ... :from-id ... :to-id ... :base-width ... :color-start ... :color-end ... :glow-color ... :animation-offset ...} ...]`
  - `:neighbors-by-star-id {star-id [{:neighbor-id ... :hyperlane hyperlane-map} ...]}`
  - Monotonic counters `:next-star-id` / `:next-hyperlane-id` for deterministic IDs.
  - Existing UI/camera/metrics keys remain unchanged (`:camera`, `:input`, `:time`, `:assets`, `:selection`, `:ui`, `:features`, `:hyperlane-settings`, `:metrics`, `:debug`).
- World helpers in `silent-king.state` are thin accessors/mutators: `stars`, `hyperlanes`, `star-by-id`, `set-world!`, `add-star!`, `add-hyperlane!`, `reset-world-ids!`, etc.
- Stars (in `silent-king.galaxy`):
  - `generate-galaxy` is pure: returns `{ :stars {...} :next-star-id n }` built from spiral arm + noise sampling.
  - `generate-galaxy!` writes these stars into `:stars`, clears hyperlanes, and resets hyperlane IDs.
- Hyperlanes (in `silent-king.hyperlanes`):
  - `generate-hyperlanes` takes `(vals (:stars ...))`, runs JTS Delaunay, and returns `{ :hyperlanes [...] :neighbors-by-star-id {...} :next-hyperlane-id n }`.
  - `generate-hyperlanes!` persists the vector and adjacency map to `game-state`.

### 1.2 Rendering and interaction

- **Stars** are rendered in `silent-king.core/draw-frame`:
  - Fetches camera + assets.
  - Uses `state/filter-entities-with game-state [:position :renderable :transform :physics]` to get star entities.
  - Applies `star-visible?` for frustum culling with non-linear zoom scaling (`silent-king.camera`).
  - For each visible star:
    - Computes screen position and size via `camera/transform-position` / `camera/transform-size`.
    - Computes rotation from `:physics` and `:time`.
    - Draws via `draw-star-from-atlas` or `draw-full-res-star` based on LOD.
    - Draws selection halo for the selected star.
- **Hyperlanes** are rendered in `silent-king.hyperlanes/draw-all-hyperlanes`:
  - Uses `state/filter-entities-with game-state [:hyperlane]` to get hyperlane entities.
  - Looks up `from`/`to` stars in `@game-state`’s `:entities`.
  - Transforms endpoints with camera functions, does line-segment frustum culling, and uses a zoom-based LOD scheme:
    - `:far` – simple thin line, no glow.
    - `:medium` – thicker rounded line.
    - `:close` – animated glowing gradient line with Skija shaders.
  - Respects `:hyperlane-settings` (`state/hyperlane-settings`): `:opacity`, `:color-scheme`, `:animation?`, `:animation-speed`, `:line-width`.
- **Selection** (`silent-king.selection`):
  - Hit testing uses `state/filter-entities-with game-state [:position :renderable :transform :star]` and camera transforms.
  - Stores selection in `[:selection]` and exposes pure views like `selected-view` (used by the Reactified star inspector).
- **Minimap**:
  - The minimap is already represented as a **ReactUI primitive** (`:minimap`).
  - `reactui.components.minimap/minimap-props` derives:
    - `:stars` from `state/filter-entities-with game-state [:position]`.
    - `:world-bounds`, `:viewport-rect` via `silent-king.minimap.math`.
  - Rendering + interaction happen in `reactui.primitives.minimap`:
    - Heatmap rendering from star positions.
    - Viewport rectangle rendering based on camera.
    - Click/drag events dispatch `[:camera/pan-to-world ...]` via `reactui.events`.
- **Reactified UI overlay**:
  - `silent-king.reactui.app/root-tree` builds a Hiccup tree of windows:
    - Control panel, hyperlane settings, performance overlay, star inspector, minimap.
  - `reactui.core` normalizes + lays out the tree; `reactui.render/draw-tree` does Skija drawing.
  - Mouse events:
    - GLFW callbacks in `silent-king.core/setup-mouse-callbacks` pass positions into `reactui.core`:
      - `handle-pointer-down!`, `handle-pointer-drag!`, `handle-pointer-up!`.
    - If the UI doesn’t consume the click (no widget hit), `selection/handle-screen-click!` gets a chance to pick a star.

So we already have:

- World (stars + hyperlanes + camera) in a lightweight ECS.
- UI in a React-style immediate-mode layer with its own primitive vocabulary.
- A small amount of glue:
  - UI events mutate `game-state` (e.g. hyperlane settings, camera zoom).
  - The UI reads derived views from `game-state` (minimap, star inspector, performance metrics).

---

## 2. What “Bring Stars/Hyperlanes Into ReactUI” Could Mean

There are two qualitatively different interpretations:

1. **ReactUI hosts the world as a coarse primitive**, but the world data stays in ECS:
   - Add one or a few primitives, e.g. `:galaxy` / `:star-field` / `:hyperlane-layer`.
   - Their layout node simply owns a world-space viewport; `render/draw-node` for `:galaxy` would call the existing star/hyperlane rendering logic (or a refactored, shared version).
   - Pointer events on that primitive would handle star selection and camera gestures, emitting event vectors (e.g. `[:selection/click-star id]`, `[:camera/drag ...]`).
   - The React tree remains reasonably small; the world is still rendered via tight loops and Skija calls inside the primitive.

2. **World entities become first-class React nodes**:
   - Represent each star/hyperlane as a separate node in the React tree, e.g. thousands of `[:star {...}]` elements.
   - The renderer walks the entire tree, computes layout/bounds per node, and draws star/hyperlane shapes based on those props.

Option (1) is essentially “unify rendering orchestration” (one renderer has both UI and world primitives), while (2) is “make world entities behave like UI widgets.”

When you say “rendering everything through the reactui,” you could choose (1) and still keep a small tree, or choose (2) and push the entire galaxy into React. The tradeoffs are very different.

---

## 3. What “Pure Data” Would Change

Today, “ECS” mainly means:

- A single `:entities` map with numeric IDs and `{ :components {...} }` maps.
- Queries like `filter-entities-with` that walk the map and filter by component keys.
- Some `!` helpers that mutate the `game-state` atom.

We do **not** have:

- Multiple disjoint systems ticking entities.
- Time-based component updates (rotation is computed on the fly from `:physics` + `:time` and never written back).
- A complex component graph or inheritance.

A “pure data” world, as you’re describing it, would likely look like:

- Stars and hyperlanes stored under domain-specific keys instead of `:entities`:
  - `:stars` – vector or map of `{:id ... :x ... :y ... :size ... :density ... :sprite ...}`.
  - `:hyperlanes` – vector of `{:from-id ... :to-id ... :visual {...}}`.
- No `:components` nesting, no component-level query API; callers just use ordinary Clojure operations over strongly-typed collections.
- Updates performed by pure functions `(world -> world')`, with mutation only at the outer atom boundary (if at all).

ECS vs “pure data” here is mostly about:

- **Where and how we store the galaxy** (`:entities` map with component queries vs `:stars` / `:hyperlanes` collections).
- **Whether systems think in terms of whole-world transformations** or per-entity/component updates.

The Reactified UI is already **pure data** in the sense of “Hiccup tree derived from props,” so this question is specifically about the model for the galaxy, not about the UI layer.

---

## 4. Pros/Cons: Keep ECS + Direct Rendering (Status Quo)

### Pros

- **Straightforward, low-overhead rendering path**
  - `draw-frame` currently draws stars and hyperlanes in tight loops directly against Skija:
    - Frustum culling (`star-visible?`), per-star LOD, per-hyperlane LOD, selection rings, etc.
  - There’s no extra tree-walking or intermediate representation beyond:
    - “Fetch entities with components X.”
    - “For each, compute screen coords and draw.”
  - For thousands of stars and hyperlanes, this is simple and performant.

- **ECS already works as a world model**
  - All world-space consumers (selection, minimap, hyperlanes, star inspector, metrics) already speak in terms of entities + components.
  - ECS provides one place to hang shared state:
    - Position is used by star rendering, hyperlane generation, minimap, selection, and camera focus helpers.
    - Hyperlane data is used by selection (`hyperlane-connections`) and performance metrics.
  - The cost of this ECS is small: it’s just a map-of-maps; no complex system orchestration.

- **Reactified UI is decoupled from world rendering details**
  - UI only needs derived views (selection info, metrics, minimap props); it doesn’t care how the galaxy is drawn.
  - You can iterate on UI layout, visual design, and interaction without touching galaxy rendering code.

- **Tests and tooling align with ECS**
  - Tests in `test/silent_king/reactui` and `test/silent_king/selection_test.clj` use `state/create-entity`, `state/add-entity!`, and component accessors.
  - The performance overlay uses entity counts (`:total-stars`, `:hyperlane-count`, `:widget-count`) derived directly from ECS queries.

### Cons

- **Two rendering worlds**
  - World rendering and UI rendering live in different subsystems:
    - World: `core.draw-frame` + `hyperlanes.draw-all-hyperlanes`.
    - UI: Reactified pipeline (`reactui.core`, `.layout`, `.render`).
  - Behaviors that conceptually span both (e.g. “click in minimap to pan camera and highlight a star”) must bridge across:
    - Minimap click → ReactUI event → camera update.
    - Galaxy click → selection namespace → ReactUI star inspector showing updated details.

- **ECS adds mental overhead without giving full ECS benefits**
  - We have IDs and components, but:
    - No generalized “systems” abstraction.
    - No sophisticated ECS engine (e.g. archetypes, chunked storage).
  - The ECS API can make world code slightly noisier:
    - `state/filter-entities-with` + `state/get-component` vs iterating a domain-specific `:stars` collection.
  - For a single world type (stars + hyperlanes), the generic abstraction may not be buying much.

- **Duplication and divergence risk**
  - Camera math is used in several places (star rendering, hyperlane rendering, selection, minimap); any change must stay consistent.
  - If we later add a ReactUI primitive for the galaxy without removing the old rendering path, we risk:
    - Two different code paths for drawing the same stars/hyperlanes.
    - Harder debugging when behavior or performance differs between views.

---

## 5. Pros/Cons: Bring World Rendering Into ReactUI (But Keep ECS)

Assume we pick the **coarse primitive** approach (e.g. `:galaxy` primitive), not one-node-per-star.

### Pros

- **Single orchestration pipeline**
  - `draw-frame` would become:
    - Clear background.
    - Render Reactified tree, which includes:
      - A `:galaxy` node for stars + hyperlanes.
      - UI windows for overlay panels.
  - One place to handle pointer routing, z-order, scaling, and render-time context (e.g. active interaction).

- **Cleaner layering and composition**
  - Galaxy rendering would look structurally similar to minimap:
    - `galaxy-props` derived from `game-state` (camera, assets, entities).
    - React component returns `[:galaxy {...}]`.
    - `reactui.primitives.galaxy` owns rendering + interaction.
  - UI features that need to talk to the world (selection, focus camera, toggling hyperlanes) now do so through the same event system (`reactui.events`) rather than through raw GLFW hooks.

- **More flexible integration with UI affordances**
  - Hover overlays, tooltips, or in-world UI markers could leverage ReactUI’s overlay mechanisms (`render/queue-overlay!`) while sharing the galaxy’s layout node.
  - You could add “in-world UI” (e.g. inline star labels) as children or overlays of the galaxy primitive instead of special-casing them in `core.clj`.

- **Gradual path toward pure-data world**
  - Once world rendering is a React primitive, you can refactor the backing store from ECS → pure domain collections without changing the primitive’s public surface.
  - Consumers of `game-state` (ReactUI + minimap + selection) would just switch from “entities + components” to whatever pure data representation you choose.

### Cons

- **More complexity in the ReactUI layer**
  - ReactUI is currently focused on UI scale and layout; it doesn’t know about:
    - Star LOD thresholds.
    - Hyperlane animation parameters.
    - Galaxy-level culling beyond what minimap needs.
  - A `:galaxy` primitive would import more Skija and camera logic into the ReactUI subsystem, making it heavier and more “engine-like.”

- **Risk of duplicating rendering logic unless carefully refactored**
  - If we just “wrap” existing drawing code, we need to:
    - Factor star/hyperlane rendering out of `core.clj`/`hyperlanes.clj` into shared functions.
    - Ensure we don’t accidentally retain an unused legacy path.
  - If we re-implement the galaxy renderer “React style” from scratch, we risk subtle regressions in LOD, culling, and visual polish.

- **Potential debugging friction**
  - Errors in the galaxy primitive’s layout or interaction logic would now go through the ReactUI stack (normalize → layout → render → pointer handling), which can be more complex to reason about than the current “draw everything in world coordinates” approach.

### On per-entity React nodes

Creating individual React nodes for every star / hyperlane (`:star`, `:hyperlane-edge` primitives) would:

- Greatly increase tree size (thousands of nodes).
- Require layout and hit-testing to deal with many more nodes.
- Provide little benefit for objects that:
  - Don’t participate in UI-style layout.
  - Mostly need tight world-space math and culling.

This path is unlikely to be worth it unless the galaxy becomes a UI-like scene (labels, per-star widgets), and even then, we’d probably prefer a batch-style primitive with its own internal structures over a huge public tree.

---

## 6. Pros/Cons: Remove ECS and Use Pure Data for the World

This is orthogonal to whether rendering is orchestrated by ReactUI, but in practice you’re considering them together: “pure data world + ReactUI renderer.”

### Potential target design

- `game-state` still an atom, but:
  - `:entities` goes away.
  - Instead, we have:
    - `:stars` – e.g. vector of `{ :id Long :position {:x :y} :density ... :sprite ... }`.
    - `:hyperlanes` – vector of `{ :from-id :to-id :visual {...} }`.
  - Optionally precomputed maps for fast lookups:
    - `:stars-by-id` map.
    - `:hyperlanes-by-star` (adjacency lists).
- Galaxy generation:
  - `generate-galaxy!` returns a new world map `{ :stars [...], :hyperlanes [...] }`.
  - Called once at game startup (or on regenerate).
- Consumers:
  - Rendering, selection, minimap, star inspector, and metrics operate directly on `:stars` and `:hyperlanes`.

### Pros

- **Simpler, domain-specific data model**
  - Callers no longer need to think in terms of generic entities and components; they work directly with:
    - “stars” and “hyperlanes” as first-class concepts.
  - Fewer indirections:
    - No `:components` nesting.
    - No “has-all-components?” filtering; just filter `:stars` or join `:stars` and `:hyperlanes` explicitly.
  - Easier for new contributors to understand:
    - “Stars are here, hyperlanes are here, camera is here.”

- **Easier to evolve world-specific operations**
  - If you want richer per-star properties, you add keys to the star map; no need to coordinate component schemas or query keys.
  - Hyperlane adjacency data can be precomputed and stored alongside galaxy data:
    - `:neighbors-by-star-id` used by hyperlane inspector and selection.
  - World-level invariants (e.g. “hyperlanes connect existing stars”) can be checked in pure, total functions.

- **Better match for how we already use the ECS**
  - We don’t rely on dynamic component composition or frequent entity type changes.
  - Entities are created once at galaxy generation time and then treated as static.
  - Most uses are domain-specific:
    - “All stars with positions” – which is just *all stars*.
    - “All hyperlanes” – again a dedicated collection.

- **Closer alignment with Reactified philosophy**
  - Reactified UI is “pure props → tree”; a pure galaxy model would be “pure noise params → galaxy data.”
  - You can snapshot or serialize the entire galaxy easily for debugging, replay, or tests.

### Cons

- **Large refactor across multiple namespaces**
  - `silent-king.state`:
    - `:entities`, `create-entity`, `add-entity!`, `filter-entities-with`, etc., are widely used:
      - `galaxy`, `hyperlanes`, `selection`, `minimap`, `reactui.app-test` (via `create-test-star`).
    - Refactoring would touch core gameplay logic and tests.
  - `silent-king.galaxy`, `silent-king.hyperlanes`, `silent-king.selection`, `silent-king.minimap.math`, and ReactUI tests all assume an ECS-oriented API.
  - Migration would be invasive; you’d want it to be deliberate and well-tested.

- **Lose some generic flexibility**
  - If you later introduce other entity types (fleets, stations, effects) that *would* benefit from an ECS-style component model, you may reintroduce a second abstraction:
    - Pure galaxy data for stars/hyperlanes.
    - An ECS or similar for dynamic entities.
  - That’s not necessarily bad, but it’s more complex than leaving the current minimal ECS in place.

- **No immediate performance gain**
  - Current operations are already linear in entity count and dominated by:
    - Noise sampling for generation (one-time).
    - Per-frame draw loops for visible stars/hyperlanes.
  - Swapping `filter-entities-with` + `get-component` for direct iteration over `:stars` would tidy the code but is unlikely to materially change performance.
  - Without careful profiling, “simpler” here is mostly about **clarity** and not speed.

- **Transition complexity for tests**
  - Many tests construct entities via `state/create-entity` and `state/add-entity!`:
    - ReactUI app tests, selection tests, UI behavior tests.
  - Pure data would force rethinking the testing story:
    - New helpers like `test/create-star` returning a star map.
    - Different ways to set up `game-state` fixtures.

---

## 7. Combined Scenario: Pure Data World + ReactUI Galaxy Primitive

In the direction you’re leaning (“get rid of ECS and render everything through ReactUI”), the concrete target could be:

- **World representation**
  - `game-state` has:
    - `:galaxy {:stars [...], :hyperlanes [...] , ...}`.
    - `:camera`, `:selection`, `:metrics`, `:ui`, etc., as today (possibly tidied up).
  - `galaxy/generate!` returns a pure galaxy map; `state` orchestrates storing it.
- **Rendering**
  - `reactui.app/root-tree` includes a `:galaxy` window or root node:
    ```clj
    [:galaxy {:stars stars
              :hyperlanes hyperlanes
              :camera camera
              :assets assets}]
    ```
  - `reactui.primitives.galaxy`:
    - Layout: defines bounds, maybe uses the full viewport.
    - Rendering: performs essentially the same work as `core/draw-frame` + `hyperlanes/draw-all-hyperlanes`, but in `draw-node`.
    - Interaction:
      - Hit-testing and selection could re-use the existing math from `selection.pick-star`.
      - Emits event vectors like `[:selection/select-star star-id]` and `[:camera/drag-pan dx dy]`.
  - `silent-king.core/draw-frame` becomes:
    - Clear background.
    - Call `react-app/render!` with a tree that includes both galaxy and UI.

### Benefits of this combined approach

- One **pure data** model for the galaxy.
- One **rendering entry point** (ReactUI).
- One **event path** from pointer → ReactUI → `reactui.events` → `game-state`.
- Easier mental model:
  - “The entire frame is a React tree of primitives; the world is just a complex primitive with its own world-space math.”

### Costs and risks

- This is a **multi-step refactor**, not a single change:
  1. Introduce a `:galaxy` primitive that consumes the existing ECS.
  2. Move star/hyperlane rendering into that primitive, deleting the legacy path in `core.clj`.
  3. Refactor ECS-like world storage into pure `:galaxy` data (stars/hyperlanes collections).
  4. Update all consumers (selection, minimap, star inspector, metrics, tests).
- Each step needs tests:
  - Visual behaviors (LOD thresholds, hyperlane animation, selection) should remain consistent.
  - ReactUI tests must be expanded to cover the `:galaxy` primitive and its events.

---

## 8. Recommendation and Next Steps

Given the current codebase, a pragmatic path looks like:

1. **Do not immediately delete ECS.**
   - The current ECS is small and not causing performance issues.
   - It’s deeply wired into selection, minimap, hyperlanes, and tests.
   - Removing it outright would be a high-churn refactor for mostly conceptual benefit.

2. **Unify rendering orchestration first (low risk, high clarity).**
   - Introduce a **`reactui.primitives.galaxy`** primitive:
     - Initially, call existing star/hyperlane rendering helpers from its `draw-node` implementation.
     - Replace the direct calls in `core/draw-frame` with a call through ReactUI that includes the galaxy primitive as part of the tree.
   - This step:
     - Brings world rendering “into ReactUI” in the sense of orchestration and event routing.
     - Leaves the backing store (ECS) unchanged.

3. **Only then, evaluate pure data world representation.**
   - Once rendering is unified, consider a dedicated `:galaxy` data structure:
     - Start by adding `:galaxy` alongside `:entities`, keeping both in sync in generation code.
     - Migrate consumers (rendering, minimap, selection, inspector) one by one to use `:galaxy`.
     - When nothing important uses `:entities` anymore, remove ECS helpers.
   - This incremental approach allows:
     - Benchmarking and profiling to confirm there’s no regression.
     - Reusing existing tests while gradually updating them.

4. **Use “pure data” where it pulls its weight.**
   - For world-level operations (e.g. hyperlane adjacency, galaxy stats, serialization), introduce pure helper functions that operate on `:galaxy` data.
   - For highly dynamic or future entities (if they appear), you might still keep a small ECS or another flexible structure.

In short:

- **Bringing stars + hyperlanes into ReactUI as a primitive is a good idea** for architectural cleanliness and consistency, as long as we keep the primitive coarse-grained.
- **Deleting ECS in favor of a pure data galaxy model is more about clarity than performance** and should follow, not precede, that rendering unification. Doing it in stages will minimize risk and keep tests meaningful throughout. 
