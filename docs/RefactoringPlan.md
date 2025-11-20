# Reactified UI Primitive Refactor Plan

This document describes how to reorganize the Reactified UI **primitive elements** (e.g. `:label`, `:button`, `:slider`, `:dropdown`, `:vstack`, `:hstack`, `:bar-chart`, `:window`, `:minimap`) into their own files and namespaces, while keeping behavior, tests, and public APIs stable.

The goal is to:
- Make it easy to add or evolve primitives without editing large monolithic namespaces.
- Keep the data‑flow, layout, rendering, and interaction model consistent with the current Reactified design.
- Avoid churn in higher‑level components (`reactui.components.*`) and callers (`reactui.app`, tests).

This is a **plan only**; no code has been moved yet.

---

## 1. Current State (Inventory)

### 1.1 Primitive vocabulary

The Reactified UI layer currently has the following **primitive element types**:

- Text / display:
  - `:label`
- Interaction:
  - `:button`
  - `:slider`
  - `:dropdown`
- Layout / containers:
  - `:vstack`
  - `:hstack`
  - `:window`
- Visualization:
  - `:bar-chart`
  - `:minimap`

These types appear both:
- In **component Hiccup** (`reactui.components.*`), and
- In the **normalized tree**, where each node is:
  ```clojure
  {:type   keyword
   :props  map
   :children [node ...]}
  ```

### 1.2 Where the implementations live today

The implementation for each primitive is **spread across four core namespaces**:

1. `silent-king.reactui.core`
   - Tree normalization and tag handling:
     - `normalize-tree`, `normalize-element`.
     - `defmulti normalize-tag` and `defmethod` implementations for:
       - `:label`, `:button`, `:slider`, `:dropdown`, `:bar-chart`, `:minimap`, `:window`.
     - Helper fns:
       - `text-fragment?`, `coerce-text`, `collect-text`.
       - `leaf-normalizer`, `branch-normalizer`.
       - `label-props`, `button-props`, `bar-chart-props`.
   - Pointer state and interactions:
     - Pointer capture (`capture-node!`, `release-capture!`, `captured-node`).
     - Active interaction tracking (`active-interaction`, `set-active-interaction!`, `clear-active-interaction!`).
     - Minimap helpers: `minimap-interaction-value`, `handle-minimap-pan!`.
     - Window helpers: `containing-window`, `start-window-move!`, `delegate-minimap-drag-to-window!`, `dispatch-window-bounds!`, `dispatch-window-toggle!`, `handle-window-pointer-*`.
   - Pointer event multiplexer:
     - `defmulti pointer-down!` with methods for `:slider`, `:button`, `:dropdown`, `:minimap`, `:window`.
     - `defmulti pointer-up!` with methods for `:button`, `:slider`, `:dropdown`, `:minimap`, `:window`.
     - `defmulti pointer-drag!` with methods for `:slider`, `:minimap`, `:window`.
     - Public entry points: `handle-pointer-down!`, `handle-pointer-up!`, `handle-pointer-drag!`.

2. `silent-king.reactui.layout`
   - Layout driver:
     - `defmulti layout-node` and `compute-layout`.
   - Shared helpers:
     - `bounds`, `normalize-bounds`, `clamp`, `positive-step`, `snap-to-step`.
     - `dropdown-option`, `clean-viewport`, `resolve-bounds`, `expand-padding`, `estimate-text-width`.
   - Primitive layout methods:
     - `defmethod layout-node :label` – text size, height/line‑height.
     - `defmethod layout-node :button` – default height, reuse bounds.
     - `defmethod layout-node :slider` – track, handle, clamps value.
     - `defmethod layout-node :bar-chart` – value normalization, bounds and chart metadata.
     - `defmethod layout-node :vstack` – vertical stacking, padding, gap.
     - `defmethod layout-node :hstack` – horizontal stacking, padding, gap.
     - `defmethod layout-node :dropdown` – header and options layout.
     - `defmethod layout-node :window` – header, content region, minimize/resize handles.
     - `defmethod layout-node :minimap` – default size and bounds.

3. `silent-king.reactui.render`
   - Render driver:
     - `defmulti draw-node` and `draw-tree`.
     - `defmulti draw-overlay` for dropdown overlays.
   - Shared helpers:
     - Color utilities: `->color-int`, `adjust-color`, `blend-colors`, etc.
     - Text helpers: `make-font`, `approx-text-width`.
     - Pointer helpers: `pointer-position`, `pointer-in-bounds?`, `pointer-over-node?`, `active-interaction` (from render context).
   - Primitive drawing:
     - `draw-label` and `defmethod draw-node :label`.
     - Button drawing (`draw-button`) and `defmethod draw-node :button`.
     - Slider drawing (`draw-slider`) and `defmethod draw-node :slider`.
     - Stack drawing (`draw-stack`) and `defmethod draw-node :vstack` / `:hstack`.
     - Dropdown drawing (`draw-dropdown`, `defmethod draw-node :dropdown`; `defmethod draw-overlay :dropdown`).
     - Bar chart drawing (`draw-bar-chart`, `defmethod draw-node :bar-chart`).
     - Window drawing (`draw-window`, `defmethod draw-node :window`).
     - Minimap drawing (`draw-minimap`, `defmethod draw-node :minimap`).

4. `silent-king.reactui.interaction`
   - Hit testing and click → events:
     - `interactive-types` set: `#{:button :slider :dropdown :minimap :window}`.
     - `contains-point?` (local to interaction).
     - `dropdown-overlay-hit`, `dropdown-region`.
     - `window-region`, `window-overlay-hit`.
     - `node-at` – resolves which node is under the pointer, including overlay regions.
   - Higher‑level interaction helpers:
     - `slider-value-from-point` – shared slider math.
     - `click->events` – converts a click to event vectors based on node type:
       - `:button` → `:on-click`.
       - `:slider` → `:on-change` with calculated value.
       - `:dropdown` → `:on-toggle`, `:on-select`, `:on-close` depending on region.
     - `dropdown-click!`, `slider-drag!`, `activate-button!` – bridge to `reactui.events/dispatch-event!`.

### 1.3 Higher‑level components and tests

Higher‑level components (`reactui.components.*`) treat primitives as **Hiccup tags**:

- `control-panel` uses `:vstack`, `:label`, `:button`, `:slider`.
- `hyperlane-settings` uses `:vstack`, `:hstack`, `:label`, `:button`, `:slider`, `:dropdown`.
- `performance-overlay` uses `:vstack`, `:hstack`, `:label`, `:button`, `:bar-chart`.
- `star-inspector` uses `:vstack`, `:hstack`, `:label`, `:button`.
- `minimap` returns an explicit `{:type :minimap ...}` node.

Tests already exercise primitives indirectly:

- `reactui.core-test`:
  - Uses `:vstack` + `:label` in a smoke test.
  - Tests slider pointer capture and minimap/window interaction.
- `reactui.layout-test`:
  - Directly tests `:vstack`, `:hstack`, `:slider`, `:dropdown`, `:bar-chart` layout behavior.
- `reactui.interaction-test`:
  - Tests `click->events` for `:button`, `:slider`, and `:dropdown`.
- `reactui.app-test`:
  - Uses helper fns to find nodes of type `:button`, `:slider`, `:dropdown`, `:bar-chart`, and `:window` in the app tree.

This means any refactor must:
- Preserve primitive type keywords.
- Preserve `:props` shape and layout/render semantics.
- Keep `reactui.core/layout/render/interaction` public entry points and tests working.

---

## 2. Design Goals for the New Layout

### 2.1 What we want

- **Per‑primitive namespaces** so each element is defined in one place:
  - Normalization (`normalize-tag` defmethods or helpers).
  - Layout math.
  - Render code.
  - Interaction helpers and pointer handling.
- **Stable APIs** for:
  - `silent-king.reactui.core/normalize-tree`, `render-ui-tree`.
  - `silent-king.reactui.layout/compute-layout`.
  - `silent-king.reactui.render/draw-tree`.
  - `silent-king.reactui.interaction/click->events` and `reactui.core/*pointer*` fns.
- **Low friction for adding primitives**:
  - No need to touch several monolithic files to add a new type.
  - Clear conventions for where to put new behavior.

### 2.2 What we will not change (for this refactor)

- The top‑level module structure under `src/silent_king/reactui`:
  - `core.clj`, `layout.clj`, `render.clj`, `interaction.clj`, `app.clj`, `events.clj`, `components/*`.
- Primitive **type keywords** and `:props` contracts:
  - Callers still write `[:label {:text "…"}]`, `[:vstack {...}]`, `[:bar-chart {...}]`, etc.
- Event dispatch surface:
  - Primitives still expose `:on-click`, `:on-change`, `:on-select`, `:on-toggle`, `:on-close` as event vectors.
  - `reactui.events/dispatch-event!` retains its current API.
- The immediate‑mode rendering flow (normalize → layout → render).

### 2.3 Dependency graph and circular‑dependency rules

To keep the refactor safe, we explicitly constrain how namespaces may depend on each other:

- **Base layer (no deps on primitives)**:
  - `silent-king.reactui.core`, `.layout`, `.render`, `.interaction`, plus `state`, `minimap.math`, `events`, etc.
  - These define `defmulti`s, shared helpers, and top‑level entry points only.
- **Primitive layer**:
  - `silent-king.reactui.primitives.*` namespaces **may depend on the base layer only**.
  - They add `defmethod` implementations on the multimethods declared in the base layer and may call shared helpers there.
  - They **must not** require other primitive namespaces, to avoid cycles.
- **Composition layer**:
  - `silent-king.reactui.components.*`, `reactui.app`, and tests.
  - These may require:
    - Base layer namespaces.
    - Primitive namespaces (directly or via a single aggregator ns).
  - The composition layer sits “on top” and is never required by primitives or the base layer.

Loader strategy:

- Provide a lightweight aggregator namespace, e.g. `silent-king.reactui.primitives` that simply `:require`s all per‑primitive namespaces for their side‑effect of registering defmethods.
- `reactui.app` (and optionally the test runner) should `:require` this aggregator once.
- **Do not** `:require` primitive namespaces from `core`, `layout`, `render`, or `interaction`; those base namespaces remain cycle‑free and know nothing about primitive files beyond the multimethods they expose.

---

## 3. Target Namespace & File Layout

### 3.1 New primitive namespaces

Under `src/silent_king/reactui`, introduce a `primitives` folder with one namespace per primitive family:

- `src/silent_king/reactui/primitives/label.clj`
- `src/silent_king/reactui/primitives/button.clj`
- `src/silent_king/reactui/primitives/slider.clj`
- `src/silent_king/reactui/primitives/dropdown.clj`
- `src/silent_king/reactui/primitives/stack.clj` (for both `:vstack` and `:hstack`)
- `src/silent_king/reactui/primitives/bar_chart.clj`
- `src/silent_king/reactui/primitives/window.clj`
- `src/silent_king/reactui/primitives/minimap.clj`

Each namespace will own the **full behavior** for its primitive(s) but will integrate with existing multimethods defined in the core files.

### 3.2 Multimethod ownership

We will keep the **multimethod definitions** in the existing core modules, and move only the **methods and helpers** into per‑primitive namespaces.

Concretely:

- In `silent-king.reactui.core`:
  - Keep:
    - `defmulti normalize-tag`.
    - `defmethod normalize-tag :default`.
    - Pointer capture state.
    - `defmulti pointer-down!`, `pointer-up!`, `pointer-drag!` and default methods.
  - Remove per‑primitive helpers and defmethods (e.g. `label-props`, `button-props`, `bar-chart-props`, and `defmethod normalize-tag :label` etc.) after they are re‑homed.

- In `silent-king.reactui.layout`:
  - Keep:
    - `defmulti layout-node`.
    - `defmethod layout-node :default`.
    - Shared helpers (`bounds`, `normalize-bounds`, `expand-padding`, etc.).
  - Move per‑primitive `defmethod` bodies into primitive namespaces.

- In `silent-king.reactui.render`:
  - Keep:
    - `defmulti draw-node` and `defmethod draw-node :default`.
    - `defmulti draw-overlay` and its default.
    - Shared render helpers (fonts, colors, pointer helpers).
  - Move per‑primitive `draw-*` functions and `defmethod draw-node :type` definitions into primitive namespaces.

- In `silent-king.reactui.interaction`:
  - Keep:
    - `node-at`, `dropdown-overlay-hit`, `window-overlay-hit` and other structural hit‑testing helpers that need access to the whole tree.
    - The notion of overlay‑first hit‑testing.
  - Move pure per‑primitive helpers that don’t need global knowledge (e.g. `slider-value-from-point`) into the relevant primitive namespace.

### 3.3 Example: label primitive

Target organization for `:label`:

- `silent-king.reactui.primitives.label`:
  - Exports:
    - `defn label-props [...]` – previously private in `reactui.core`.
    - `(defmethod core/normalize-tag :label [...])` – uses `label-props` and `core/leaf-normalizer`.
    - `(defmethod layout/layout-node :label [...])` – uses `layout/resolve-bounds`, etc.
    - `(defn draw-label [...])` plus `(defmethod render/draw-node :label [...])`.

- `silent-king.reactui.core`:
  - Provides `normalize-tag`, `leaf-normalizer`, `branch-normalizer`, and `collect-text` as public helpers (or as utilities in a small `reactui.primitives.shared` ns).

### 3.4 Example: vstack and hstack primitives

Stacks share drawing and some layout semantics:

- `silent-king.reactui.primitives.stack`:
  - Layout:
    - `(defmethod layout/layout-node :vstack [...])`
    - `(defmethod layout/layout-node :hstack [...])`
  - Render:
    - `draw-stack` function (moved from `reactui.render`).
    - `(defmethod render/draw-node :vstack [...])`
    - `(defmethod render/draw-node :hstack [...])`
  - No `normalize-tag` defmethods are needed today for stacks (they use default). If we later add stack‑specific props preprocessing, it will live here.

### 3.5 Example: window and minimap primitives

`window` and `minimap` are more complex because they participate in pointer capture and delegate events:

- `silent-king.reactui.primitives.window`:
  - `normalize-tag` method for `:window`.
  - `layout-node :window` defmethod.
  - `draw-window` and `defmethod draw-node :window`.
  - Window‑specific pointer helpers (currently in `core`), e.g.:
    - `containing-window`, `start-window-move!`, `dispatch-window-bounds!`, `dispatch-window-toggle!`.
    - `(defmethod core/pointer-down! :window [...])`, `pointer-drag!`, `pointer-up!`.
  - Window‑specific overlay region logic may remain in `reactui.interaction` (it has to inspect `:layout :window` across the tree), but private helpers can be moved or split if it improves cohesion.

- `silent-king.reactui.primitives.minimap`:
  - `normalize-tag` defmethod for `:minimap` (currently a leaf).
  - `layout-node :minimap` defmethod.
  - `draw-minimap` and `defmethod draw-node :minimap`.
  - Minimap pointer helpers:
    - `minimap-interaction-value`, `handle-minimap-pan!`.
    - `(defmethod core/pointer-down! :minimap [...])`, `pointer-drag!`, `pointer-up!`.

---

## 4. Shared Utility Extraction

Several helpers are currently private (`defn-`) in `reactui.core`, `layout`, and `render` but will need to be reused across primitive namespaces.

### 4.1 Promote or centralize shared helpers

Refactor in two steps:

1. **Promote key helpers to public fns** in their current namespaces:
   - In `reactui.core`:
     - `leaf-normalizer`, `branch-normalizer`.
     - `collect-text`, `coerce-text`, `text-fragment?` (if needed outside).
   - In `reactui.layout`:
     - `resolve-bounds`, `expand-padding`, `estimate-text-width`, `clamp`, `positive-step`, `snap-to-step`.
   - In `reactui.render`:
     - Color helpers: `->color-int`, `adjust-color`, `blend-colors`.
     - Text helpers: `make-font`.
     - Pointer helpers: `pointer-over-node?` and `pointer-in-bounds?` may stay private if only used inside render.

2. Optionally introduce a small `silent-king.reactui.primitives.shared` namespace if helper surface grows:
   - Begin with direct reuse from existing namespaces.
   - Only factor out into `primitives.shared` once duplication appears.

### 4.2 Interaction utilities

`slider-value-from-point` is a pure helper that only depends on the node’s `:layout :slider` data:

- Move it into `primitives.slider` as a public helper.
- Update:
  - `reactui.interaction/click->events` to call `primitives.slider/slider-value-from-point`.
  - `reactui.interaction/slider-drag!` and the `pointer-*` defmethods to use the new location.

Window and minimap pointer helpers should live next to their pointer defmethods in `primitives.window` and `primitives.minimap`.

---

## 5. Incremental Refactor Steps

This section lays out a **step‑by‑step migration** that keeps the game runnable and tests passing at every checkpoint.

### 5.1 Foundation: helper visibility and namespaces

1. **Promote shared helpers**:
   - In `reactui.core`:
     - Convert `leaf-normalizer`, `branch-normalizer`, `collect-text`, and `coerce-text` from `defn-` to `defn`.
   - In `reactui.layout`:
     - Convert `resolve-bounds`, `expand-padding`, `estimate-text-width`, `clamp`, `positive-step`, and `snap-to-step` from `defn-` to `defn`.
   - In `reactui.render`:
     - Convert `make-font`, `->color-int`, `adjust-color`, and `blend-colors` from `defn-` to `defn`.

2. **Add the `primitives` directory**:
   - Create empty namespaces:
     - `silent-king.reactui.primitives.label`
     - `silent-king.reactui.primitives.button`
     - `silent-king.reactui.primitives.slider`
     - `silent-king.reactui.primitives.dropdown`
     - `silent-king.reactui.primitives.stack`
     - `silent-king.reactui.primitives.bar-chart`
     - `silent-king.reactui.primitives.window`
     - `silent-king.reactui.primitives.minimap`
   - Give each ns a short docstring and `set! *warn-on-reflection* true`.

3. **Wire primitives into the load path without cycles** (no behavior change yet):
   - Create an aggregator namespace `silent-king.reactui.primitives` that `:require`s each per‑primitive ns:
     - `primitives.label`, `primitives.button`, `primitives.slider`, etc.
   - Update `silent-king.reactui.app` (and, if desired, `silent-king.test-runner`) to `:require` this aggregator so that all `defmethod`s are loaded at startup.
   - **Do not** add `:require` edges from `reactui.core`, `layout`, `render`, or `interaction` to any `primitives.*` namespaces; the dependency direction is always:
     - base layer → (export multimethods/helpers),
     - primitive layer → base layer,
     - composition layer → both base and primitive layers.

### 5.2 Move label implementation

1. **Extract normalization**:
   - Move `label-props` and the `defmethod normalize-tag :label` implementation from `reactui.core` into `primitives.label`.
   - In `primitives.label`:
     - Require `silent-king.reactui.core` as `core`.
     - Implement:
       ```clojure
       (defn label-props [props raw-children] ...)

       (defmethod core/normalize-tag :label
         [_ props child-forms]
         ((core/leaf-normalizer :label label-props) props child-forms))
       ```
   - Remove the old `defmethod` and helper from `reactui.core`.

2. **Extract layout**:
   - Move the body of `defmethod layout-node :label` from `reactui.layout` into `primitives.label`.
   - Implement:
     ```clojure
     (defmethod layout/layout-node :label
       [node context]
       ;; same body, using layout/resolve-bounds, layout/estimate-text-width, etc.
       )
     ```
   - Remove the original defmethod from `reactui.layout`.

3. **Extract rendering**:
   - Move `draw-label` and `defmethod draw-node :label` from `reactui.render` into `primitives.label`.
   - Implement:
     ```clojure
     (defn draw-label [^Canvas canvas node] ...)

     (defmethod render/draw-node :label
       [canvas node]
       (draw-label canvas node))
     ```
   - Remove the original `draw-label` and `defmethod draw-node :label` from `reactui.render`.

4. **Run tests for regressions**:
   - `./run-tests.sh`.
   - If anything fails, adjust requires/imports in `primitives.label` or helper visibility.

5. **Run paren balance check**:
   - `python3 scripts/check_parens.py src/silent_king/reactui/core.clj`
   - `python3 scripts/check_parens.py src/silent_king/reactui/layout.clj`
   - `python3 scripts/check_parens.py src/silent_king/reactui/render.clj`
   - `python3 scripts/check_parens.py src/silent_king/reactui/primitives/label.clj`

### 5.3 Move button implementation

Repeat the same pattern for `:button`:

1. `primitives.button`:
   - Move `button-props` + `defmethod normalize-tag :button` from `reactui.core`.
   - Add `defmethod layout-node :button` (moved from `reactui.layout`).
   - Add `draw-button` and `defmethod draw-node :button` (moved from `reactui.render`).

2. Interaction:
   - Move `interaction/activate-button!` into `primitives.button` if desired, or keep it where it is and simply call back into that function.
   - Keep `click->events` logic in `reactui.interaction` (it already knows how to build events for `:button`).

3. Update pointer handling:
   - Keep `(defmethod pointer-down! :button ...)` and `(defmethod pointer-up! :button ...)` in `reactui.core` if we prefer a single entry point for capture logic.
   - Alternatively, move these defmethods into `primitives.button`, still targeting `core/pointer-down!` and `core/pointer-up!`.
   - This plan recommends **moving them** into `primitives.button` for better cohesion.

4. Run tests + paren check as in 5.2.

### 5.4 Move slider implementation

1. `primitives.slider`:
   - Move `defmethod normalize-tag :slider` from `reactui.core` (if we need any special handling; today it just uses `leaf-normalizer`, so we may leave it in core or move it for consistency).
   - Move `defmethod layout-node :slider` from `reactui.layout`.
   - Move `draw-slider` and `defmethod draw-node :slider` from `reactui.render`.

2. Interaction:
   - Move `slider-value-from-point` and `slider-drag!` from `reactui.interaction` into `primitives.slider`.
   - Update:
     - `click->events` to call `primitives.slider/slider-value-from-point`.
     - `core/pointer-down!`, `pointer-up!`, and `pointer-drag!` implementations for `:slider` (wherever they live) to call `primitives.slider/slider-drag!`.

3. Tests:
   - `reactui.interaction-test/slider-click-calculates-value` and `reactui.core-test/slider-pointer-capture-updates-zoom` should remain green.
   - Run `./run-tests.sh` and paren checks.

### 5.5 Move dropdown implementation

1. `primitives.dropdown`:
   - Move `defmethod normalize-tag :dropdown` from `reactui.core` (currently a simple leaf normalizer; can be kept or moved for symmetry).
   - Move `defmethod layout-node :dropdown` from `reactui.layout`.
   - Move dropdown rendering and overlay:
     - `draw-dropdown` and `defmethod draw-node :dropdown`.
     - `defmethod draw-overlay :dropdown`.

2. Interaction:
   - Keep `dropdown-region` and high‑level `click->events` in `reactui.interaction` because they traverse `:layout :dropdown` and operate over the layout tree.
   - Optionally move pure helper pieces that only depend on a dropdown node into `primitives.dropdown`.
   - Pointer capture (`pointer-down! :dropdown`, `pointer-up! :dropdown`) can remain in `reactui.core` or move to `primitives.dropdown` mirroring the slider and button pattern.

3. Tests:
   - `reactui.layout-test/dropdown-layout-adjusts-height`.
   - `reactui.interaction-test/dropdown-click-emits-events`.
   - `reactui.app-test/hyperlane-color-dropdown-selects-scheme`.
   - Run tests + paren checks.

### 5.6 Move stack (vstack/hstack) implementation

1. `primitives.stack`:
   - Move:
     - `defmethod layout-node :vstack` and `:hstack` from `reactui.layout`.
     - `draw-stack` and the `defmethod draw-node :vstack` + `:hstack` implementations from `reactui.render`.
   - If we later add shared props normalization for stacks, add it here as well:
     - E.g. `stack-props` to clamp gaps and padding.

2. Tests:
   - `reactui.layout-test/vstack-*` and `hstack-lays-out-children-horizontally`.
   - `reactui.core-test/render-ui-tree-smoke` uses `:vstack`.
   - Run tests + paren checks.

### 5.7 Move bar-chart implementation

1. `primitives.bar-chart`:
   - Move `bar-chart-props` and `defmethod normalize-tag :bar-chart` from `reactui.core`.
   - Move `defmethod layout-node :bar-chart` from `reactui.layout`.
   - Move `draw-bar-chart` and `defmethod draw-node :bar-chart` from `reactui.render`.

2. Tests:
   - `reactui.layout-test/bar-chart-layout-derives-range`.
   - `reactui.app-test/performance-overlay-renders-fps-chart`.
   - Run tests + paren checks.

### 5.8 Move window implementation

1. `primitives.window`:
   - Move `defmethod normalize-tag :window` from `reactui.core`.
   - Move `defmethod layout-node :window` from `reactui.layout`.
   - Move `draw-window` and `defmethod draw-node :window` from `reactui.render`.

2. Pointer & interaction helpers:
   - Move the following from `reactui.core` into `primitives.window`:
     - `containing-window`, `start-window-move!`, `delegate-minimap-drag-to-window!`.
     - `dispatch-window-bounds!`, `dispatch-window-toggle!`.
     - `handle-window-pointer-down!`, `handle-window-pointer-drag!`, `handle-window-pointer-up!`.
   - Move the `pointer-*` defmethods for `:window` into `primitives.window`:
     - `defmethod core/pointer-down! :window [...]`
     - `defmethod core/pointer-drag! :window [...]`
     - `defmethod core/pointer-up! :window [...]`

3. Hit testing:
   - Keep `window-region` and `window-overlay-hit` in `reactui.interaction` for now, since they operate on arbitrary nodes and are used by `node-at`.
   - If cohesion improves, we can later introduce `primitives.window/hit-region` and have `interaction` delegate to it.

4. Tests:
   - `reactui.core-test/minimap-window-header-drag-still-works`.
   - `reactui.core-test/minimap-background-drag-delegates-window`.
   - `reactui.app-test` window‑related assertions.
   - Run tests + paren checks.

### 5.9 Move minimap implementation

1. `primitives.minimap`:
   - Move `defmethod normalize-tag :minimap` from `reactui.core`.
   - Move `defmethod layout-node :minimap` from `reactui.layout`.
   - Move `draw-minimap` and `defmethod draw-node :minimap` from `reactui.render`.

2. Pointer & interaction helpers:
   - Move `minimap-interaction-value` and `handle-minimap-pan!` from `reactui.core` into `primitives.minimap`.
   - Move the `pointer-*` defmethods for `:minimap` into `primitives.minimap`:
     - `defmethod core/pointer-down! :minimap [...]`
     - `defmethod core/pointer-drag! :minimap [...]`
     - `defmethod core/pointer-up! :minimap [...]`

3. Tests:
   - `reactui.core-test/layout-minimap!` helpers and minimap pointer tests.
   - Ensure pointer capture behavior is unchanged.
   - Run tests + paren checks.

---

## 6. Clean‑up and Documentation

Once all primitives are migrated:

1. **Audit the core files for unused code**:
   - Remove any dead helpers or imports from:
     - `reactui.core`
     - `reactui.layout`
     - `reactui.render`
     - `reactui.interaction`
   - Keep only:
     - Multimethod definitions and default methods.
     - Cross‑cutting helpers that truly belong at the core level.

2. **Update internal documentation**:
   - Add a short section to `Reactified.md` describing the primitive layout:
     - Mention `silent-king.reactui.primitives.*` and how new primitives are added.
   - Optionally add a lightweight overview comment at the top of each primitive ns explaining:
     - What the primitive does.
     - Which aspects (normalize/layout/render/interaction) it implements.

3. **Verify tests and paren checks**:
   - Final run:
     - `./run-tests.sh`
     - `python3 scripts/check_parens.py src/silent_king/reactui`
   - Treat any failures as blockers.

4. **Future work** (post‑refactor):
   - Consider adding new primitives (e.g. progress bars, sparklines) into `reactui.primitives.*`, using this pattern.
   - If the primitive set grows, introduce a micro‑registry or doc table mapping `:type` keywords to their implementing namespaces for developer onboarding.

---

## 7. Summary

This plan:

- Keeps the Reactified UI public APIs and tests stable.
- Introduces a clear per‑primitive namespace layout under `silent-king.reactui.primitives`.
- Moves normalization, layout, rendering, and per‑primitive interaction logic into cohesive modules.
- Leaves `core`, `layout`, `render`, and `interaction` as **orchestrators** that define multimethods and shared infrastructure, rather than housing all implementations.

Following the incremental steps above, we can land the refactor in small, test‑backed slices without breaking the UI or game loop.
