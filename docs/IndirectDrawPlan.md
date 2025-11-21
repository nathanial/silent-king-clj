# Indirect Draw Plan

This document describes how to move **all** rendering in Silent King from
“draw directly to a Skija `Canvas`” to an **indirect draw system**:

- Gameplay and UI code **produce vectors of draw commands** (pure data).
- A dedicated Skija backend module **interprets those commands and mutates
  the `Canvas`**.

The end-state goal is:

- No game/UI namespace calls `.draw*`, `.clip*`, `.save`, `.restore`,
  `.translate`, `.rotate`, or `.scale` on a `Canvas`.
- All Skija-specific drawing lives in a single module (or a very small
  cluster) under a `rendering/drawing/skia`-style namespace.
- Rendering logic is testable as pure transformations: inputs
  (game state + UI tree + camera) → **draw command vectors**.

The plan is written to support an incremental migration but targets *full*
conversion of the existing codebase.

---

## 1. Current Rendering Overview

### 1.1 World / Galaxy Rendering

Key namespaces:

- `silent-king.core`
  - `draw-frame` orchestrates all world and UI drawing.
  - Holds camera/zoom/pan logic and dispatches to world renderers and the
    React UI (`react-app/render!`).
- `silent-king.render.galaxy`
  - Draws stars from atlases and full-res images.
  - Draws orbit rings and selection highlights.
  - Uses `Canvas`, `Image`, `Rect`, and `Paint`.
- `silent-king.hyperlanes`
  - Generates hyperlane topology (pure).
  - Renders hyperlanes with LOD, animation, glows, and gradients.
  - Uses `Canvas`, `Paint`, `PaintMode`, `PaintStrokeCap`, and `Shader`.
- `silent-king.voronoi`
  - Generates Voronoi cells (mostly pure geometry).
  - Renders cells with fill + stroke passes and optional centroids.
  - Uses `Canvas`, `Paint`, `PaintMode`, `PaintStrokeCap`, and `Path`.
- `silent-king.regions`
  - Generates regions/sectors (pure-ish graph operations).
  - Renders region / sector labels (text) using a shared `Typeface`/`Font`
    and `Paint`.

Characteristics:

- Most game rendering functions:
  - Accept a `^Canvas` first.
  - Perform coordinate transforms (via `camera` helpers).
  - Allocate `Paint`/`Path`/`Rect`/`Shader`/`Font` objects and call `.draw*`
    methods directly.
- Debug metrics (`:hyperlanes-rendered`, `:voronoi-rendered`, etc.) are
  updated in the same functions that draw to the `Canvas`.

### 1.2 Reactified UI Rendering

Key namespaces:

- `silent-king.reactui.core`
  - `render-ui-tree` normalizes a Hiccup tree, computes layout, and uses
    `reactui.render/draw-tree` to draw.
- `silent-king.reactui.render`
  - Skija-based renderer for the layout tree.
  - Has multimethods:
    - `draw-node` (per node type).
    - `draw-overlay` (for overlay drawing passes, e.g., dropdown menus).
  - Implements utilities like `make-font`, `blend-colors`, and pointer
    helpers (`pointer-in-bounds?`, `active-interaction`).
  - `draw-tree` currently takes a `Canvas` and recursively calls
    `draw-node` + `draw-overlay` with direct Skija calls.
- UI primitives:
  - `silent-king.reactui.primitives.label`
  - `silent-king.reactui.primitives.slider`
  - `silent-king.reactui.primitives.dropdown`
  - `silent-king.reactui.primitives.window`
  - `silent-king.reactui.primitives.minimap`
  - `silent-king.reactui.primitives.stack`
  - Each:
    - Implements `layout/layout-node` (pure).
    - Implements a `draw-*` function that takes a `^Canvas`.
    - Registers `render/draw-node` or `render/draw-overlay` methods which
      call Skija APIs directly.
- `silent-king.reactui.app`
  - Builds the root UI tree from game state.
  - `render!`:
    - Accepts `^Canvas`, viewport, and game state.
    - Sets the UI viewport in state.
    - Applies a Skija scale (`.save`, `.scale`, `.restore`) on the canvas.
    - Calls `reactui.core/render-ui-tree` with the `canvas`.

Tests:

- `test/silent_king/reactui/core_test.clj` already passes `:canvas nil`
  into `render-ui-tree`, proving that layout and tree normalization are
  usable without a real `Canvas`. This fits well with a plan-based renderer.

---

## 2. Target Architecture

### 2.1 Core Concepts

**Draw commands** are small Clojure maps describing *what* to draw, not
*how it is drawn by Skija*.

Example shapes:

- Clear:
  ```clojure
  {:op :clear
   :color 0xFF000000}
  ```

- Rect:
  ```clojure
  {:op :rect
   :rect {:x 10.0 :y 20.0 :width 100.0 :height 40.0}
   :style {:fill-color 0xFF2D2F38
           :stroke-color 0x80444444
           :stroke-width 1.0}}
  ```

- Circle:
  ```clojure
  {:op :circle
   :center {:x 200.0 :y 120.0}
   :radius 16.0
   :style {:fill-color 0x44FFD966
           :stroke-color 0xFFFFE680
           :stroke-width 3.0}}
  ```

- Line:
  ```clojure
  {:op :line
   :from {:x 10.0 :y 10.0}
   :to   {:x 240.0 :y 180.0}
   :style {:stroke-color 0xFF6699FF
           :stroke-width 2.0
           :stroke-cap :round}}
  ```

- Polygon fill/stroke (Voronoi):
  ```clojure
  {:op :polygon-fill
   :points [{:x 0.0 :y 0.0}
            {:x 50.0 :y 10.0}
            {:x 40.0 :y 60.0}]
   :style {:fill-color 0x33FFFFFF}}

  {:op :polygon-stroke
   :points [...]
   :style {:stroke-color 0xFF7FA5FF
           :stroke-width 1.4}}
  ```

- Text:
  ```clojure
  {:op :text
   :text "Andromeda Expanse"
   :position {:x 512.0 :y 300.0}
   :font {:size 18.0
          :family :default      ;; high-level; backend maps this to Skija Typeface
          :weight :normal}
   :color 0xFFFFFFFF}
  ```

- Image (star/planet atlas):
  ```clojure
  {:op :image-rect
   :image (:atlas-image-xs assets)          ;; opaque handle; still a Skija Image
   :src {:x 128.0 :y 64.0 :width 64.0 :height 64.0}
   :dst {:x 400.0 :y 250.0 :width 48.0 :height 48.0}
   :transform {:rotation-deg 30.0
               :anchor :center}}
  ```

- Canvas state / transforms:
  ```clojure
  {:op :save}
  {:op :restore}
  {:op :translate :dx 100.0 :dy 50.0}
  {:op :rotate :angle-deg 45.0}
  {:op :scale :sx 2.0 :sy 2.0}
  {:op :clip-rect
   :rect {:x 0.0 :y 0.0 :width 300.0 :height 200.0}}
  ```

- Hyperlane-specific gradient line (convenience op):
  ```clojure
  {:op :hyperlane-line
   :from {:x ... :y ...}
   :to   {:x ... :y ...}
   :lod  :close                         ;; :far, :medium, :close
   :width-base 2.0
   :colors {:start 0xFF6699FF
            :end   0xFF3366CC
            :glow  0x406699FF}
   :animation {:enabled? true
               :phase animation-phase
               :pulse-amplitude 0.3}}
  ```

The **exact shape can be tuned while implementing**, but all commands
should follow this pattern:

- `:op` keyword names an operation.
- Remaining keys are scalars/maps of numbers and keywords.
- No Skija classes (`Canvas`, `Paint`, `Font`, `Path`) appear in the
  command payload.

### 2.2 New Namespaces

Introduce two new, Skija-focused namespaces and one neutral helper:

1. **`silent-king.render.commands`** (neutral)
   - Defines the *data model* and helpers for draw commands.
   - Exposes small constructors like:
     - `clear`, `rect`, `circle`, `line`, `polygon-fill`,
       `polygon-stroke`, `image-rect`, `text`, `save`, `restore`,
       `translate`, `rotate`, `scale`, `clip-rect`.
     - Helper for annotation / debugging, e.g.
       `annotate` that adds `:tag`/`:meta` for testing.
   - No Skija imports.

2. **`silent-king.render.skia`** (or `silent-king.render.skia-driver`)
   - Contains all Skija-specific drawing logic.
   - Public API:
     - `(draw-commands! ^Canvas canvas commands & [opts])`
       - `commands`: seq/vector of draw command maps.
       - `opts`: optional map, e.g. `{::assets ... ::font-cache ...}`.
   - Implements a dispatch on `:op` that:
     - Allocates `Paint`/`Font`/`Path`/`Rect`/`Shader` objects.
     - Applies transforms (`.save`, `.restore`, `.translate`, etc.).
     - Calls `.draw*` on the `Canvas`.
     - Ensures all Skija resources are closed via `with-open` or `try/finally`.
   - May include an internal font/paint cache keyed by simple style maps
     if performance is a concern.

3. **Optional: `silent-king.render.world-plan` / `silent-king.render.ui-plan`**
   - Pure, high-level planners for:
     - World frame rendering (stars, hyperlanes, voronoi, regions, planets).
     - UI tree rendering.
   - These can be separate or simply live in existing namespaces as
     `*-plan` functions instead of new files; see migration strategy below.

---

## 3. High-Level Migration Strategy

We want an **incremental migration** that keeps the game runnable while
eventually removing all direct Skija drawing from domain code.

### 3.1 General Pattern for Each Renderer

For any existing `draw-*` function:

1. **Introduce a plan-producing function**:
   - New function (or renamed old one):
     ```clojure
     (defn plan-selection-highlight
       [screen-x screen-y screen-size time]
       ;; returns a vector of draw commands
       [...])
     ```
   - Uses only data and helper fns, **not** `Canvas`.
2. **Add a thin adapter that calls the Skia backend**:
   - For the transition:
     ```clojure
     (defn draw-selection-highlight
       [^Canvas canvas screen-x screen-y screen-size time]
       (let [commands (plan-selection-highlight screen-x screen-y screen-size time)]
         (skia/draw-commands! canvas commands)))
     ```
3. **Update callers** to use the adapter.
   - The call sites stay the same signature-wise during migration.
4. **Once everything is migrated**, collapse the adapter layer:
   - Domain code (e.g. `core/draw-frame`) should:
     - Build **a single frame plan** by composing all `*-plan` functions.
     - Call `skia/draw-commands!` once per frame with the combined plan.

The adapter step lets us convert one module at a time without breaking the
rest of the pipeline.

### 3.2 Where Draw Commands Live and Flow

Target draw pipeline per frame:

1. **World plan**:
   - New function, e.g.:
     ```clojure
     (defn frame-world-plan
       [width height time game-state]
       ;; uses camera, assets, hyperlanes, voronoi, regions
       ;; returns {:commands [...] :metrics {...}}
       ...)
     ```
   - Returns:
     - `:commands` – vector of draw commands for galaxy, hyperlanes,
       Voronoi, regions, planets, etc.
     - `:metrics` – derived values (`visible-stars`, counts of rendered
       hyperlanes cells, etc.) previously written during drawing.

2. **UI plan**:
   - New function, e.g.:
     ```clojure
     (defn ui-plan
       [viewport game-state]
       ;; returns {:commands [...] :layout-tree ...}
       ...)
     ```
   - Leverages the existing normalization/layout machinery in
     `reactui.core` and `reactui.layout`.
   - Returns:
     - `:commands` – vector of UI draw commands.
     - `:layout-tree` – same structure as today (for hit-testing).

3. **Frame composition** in `silent-king.core/draw-frame`:
   - Replace direct drawing with:
     ```clojure
     (defn draw-frame
       [^Canvas canvas width height time game-state]
       (let [{:keys [commands metrics]} (frame-world-plan width height time game-state)
             {:keys [ui-commands layout-tree]} (ui-plan {:x 0.0 :y 0.0 :width width :height height}
                                                        game-state)
             frame-commands (into [{:op :clear :color 0xFF000000}]
                                  (concat commands ui-commands))]
         ;; Update metrics derived from plans
         (state/update-performance-metrics! game-state metrics)
         ;; Draw once
         (skia/draw-commands! canvas frame-commands)))
     ```

4. **Tests**:
   - `frame-world-plan` and `ui-plan` become **pure test targets**:
     - Given a snapshot of `game-state`/props → assert on the command
       sequence (e.g. number of commands, layering, colors, etc.).
   - No `Canvas` required in tests.

---

## 4. Command Vocabulary vs. Existing Code

This section ensures the planned command set covers all current drawing.

### 4.1 World Rendering

#### 4.1.1 `silent-king.render.galaxy`

Current operations:

- `draw-star-from-atlas`
  - `.save`, `.translate`, `.rotate`, `.drawImageRect`, `.restore`.
- `draw-full-res-star`
  - Same pattern with different `Image` dimensions.
- `draw-planet-from-atlas`
  - Similar to atlas star without rotation.
- `draw-orbit-ring`
  - `Paint` with stroke mode and `drawCircle`.
- `draw-selection-highlight`
  - Two `drawCircle` calls with fill and stroke paints.

Required commands:

- `:save`, `:restore`, `:translate`, `:rotate`.
- `:image-rect` with src/dst rectangles and optional rotation/anchor.
- `:circle` with fill-only or stroke-only styles.

Design notes:

- We can keep using real Skija `Image` values as `:image` payloads in
  commands; tests can treat them as opaque.
- Rotation can be represented either as:
  - Explicit `:rotate` commands around a `:save`/`:restore` block, or
  - A `:transform` sub-map on `:image-rect` that the Skia backend
    implements by wrapping the operation with `save/translate/rotate`.
  - For simplicity, start with explicit `:save`/`translate`/`rotate`/
    `:image-rect` commands; they map directly to existing logic.

#### 4.1.2 `silent-king.hyperlanes`

Current operations:

- LOD-based rendering:
  - `:far` – simple line with minimal width.
  - `:medium` – thicker line, round caps.
  - `:close` – glowing gradient:
    - A wide glow stroke (glow color).
    - A shader-based linear gradient stroke between `start` / `end`.
    - All using Skija `Shader/makeLinearGradient`.

Required commands:

- `:line` with style map:
  - `:stroke-color`, `:stroke-width`, `:stroke-cap`.
- Optional specialized `:hyperlane-line` command:
  - Encodes LOD, base width, colors and animation configuration.
  - Skia backend interprets and builds the appropriate `Paint`/`Shader`.

Design notes:

- To keep the command vocabulary reasonably general:
  - Define a `:line` command with optional `:gradient` and `:glow` keys:
    ```clojure
    {:op :line
     :from {:x ... :y ...}
     :to   {:x ... :y ...}
     :style {:stroke-width ...
             :stroke-cap :round
             :color 0xFF6699FF
             :gradient {:start 0xFF6699FF
                        :end   0xFF3366CC}
             :glow {:color 0x406699FF
                    :multiplier 3.0}
             :animation {:phase animation-phase
                         :pulse-amplitude 0.3
                         :enabled? true}}}
    ```
  - In the Skia backend, when `:gradient` is present, construct a
    `Shader` and attach it to the `Paint`.
  - When `:glow` is present, draw a wider underlying line with the glow
    color before the main line.
  - LOD choice is already computed in `hyperlanes` functions; they can
    select which subset of style fields to include.

#### 4.1.3 `silent-king.voronoi`

Current operations (rendering section):

- For each visible cell:
  - Fills:
    - Build a `Path` from screen-space vertices.
    - `drawPath` (fill).
    - Special cases: `n = 2` draws a small circle at the segment midpoint.
    - `n <= 1` draws a circle at the centroid.
  - Strokes:
    - `drawPath` (stroke) or line segment or centroid circle.
  - Optional centroid markers.

Required commands:

- `:polygon-fill` (>= 3 vertices).
- `:polygon-stroke` (>= 3 vertices).
- `:line` (2 vertices).
- `:circle` (centroid and debug markers).

Design notes:

- Replace Skija `Path` usage with data-only `:points` vectors in commands.
- The Skia backend will:
  - Allocate a `Path`, write vertices, and draw it, then close the path.
- Visibility, color palettes, opacity, and LOD logic remain in
  `voronoi` and only influence the command sequences.

#### 4.1.4 `silent-king.regions`

Current operations:

- Uses a shared `Typeface` + `Font`.
- For each region:
  - Computes screen position from world center.
  - Adjusts text size based on zoom.
  - Draws a shadow string (offset) and the main string.
- For sectors (if zoomed in enough):
  - Similar text rendering for sector names.

Required commands:

- `:text` with:
  - `:text`, `:position`, `:font`, `:color`.
- Possibly `:shadow` convenience:
  - Alternatively, just two `:text` commands with slightly different
    positions and colors.

Design notes:

- Keep `Typeface` held inside the Skia backend; `regions` should not
  import Skija once migrated.
- Commands only specify:
  - `:font` map – at minimum `:size`, plus optional `:family`/`weight`.
  - `:color` – ARGB integer.
- Skia backend maps `:font` → `Font` instances (with caching).

### 4.2 Reactified UI

#### 4.2.1 `silent-king.reactui.render`

Current behavior:

- `draw-tree`:
  - Creates an overlay accumulator atom.
  - Binds `*overlay-collector*` and `*render-context*`.
  - Calls `draw-node` and then `draw-overlay` per collected overlay.
  - Directly draws to `Canvas`.

Target behavior:

- `draw-tree` becomes purely plan-based:
  ```clojure
  (defn plan-tree
    [node context]
    ;; returns {:commands [...] :overlays [...]}
    ...)
  ```

- Suggested split:
  - `plan-node` multimethod: same dispatch as `draw-node` today.
    - Each implementation returns a vector of commands for that node.
  - `plan-overlay` multimethod: same as `draw-overlay` but returns
    commands.
  - `queue-overlay!` remains, but collects **overlay data**, not direct
    drawing.

- The top-level API for other code:
  ```clojure
  (defn layout-and-plan-tree
    [{:keys [tree viewport context]}]
    ;; 1. normalize -> layout (as today)
    ;; 2. build commands for the laid out tree + overlays
    ;; 3. return {:layout-tree layout-tree
    ;;            :commands commands}
    )
  ```

- The existing `render-ui-tree` in `reactui.core` can be reworked to:
  ```clojure
  (defn render-ui-tree
    [{:keys [canvas tree viewport context]}]
    (let [{:keys [layout-tree commands]}
          (render/layout-and-plan-tree {:tree tree
                                        :viewport viewport
                                        :context context})]
      (when canvas
        (skia/draw-commands! canvas commands))
      layout-tree))
  ```

This keeps the current caller contract (UI tests that pass `:canvas nil`
keep working) while routing drawing through the command system.

#### 4.2.2 UI Primitives

Each primitive currently:

- Implements `layout/layout-node` (no changes needed).
- Implements a `draw-*` function that takes a `Canvas`.
- Registers `render/draw-node` (and sometimes `render/draw-overlay`)
  methods that call Skija APIs.

For each primitive:

1. **Create a plan function**:
   - Example for `label`:
     ```clojure
     (defn plan-label
       [node]
       (let [{:keys [text color font-size]} (:props node)
             {:keys [x y]} (layout/bounds node)
             size (double (or font-size 16.0))
             baseline (+ y size)]
         [(commands/text {:text (or text "")
                          :position {:x x :y baseline}
                          :font {:size size}
                          :color (or color 0xFFFFFFFF)})]))
     ```
2. **Change the render multimethods to return commands**:
   - Instead of:
     ```clojure
     (defmethod render/draw-node :label
       [canvas node]
       (draw-label canvas node))
     ```
   - Use:
     ```clojure
     (defmethod render/plan-node :label
       [_ node]
       (plan-label node))
     ```
3. **Minimize Skija imports**:
   - After plan migration, primitives should only require:
     - `reactui.layout`
     - `reactui.render` (for pointer helpers, etc.)
     - `render.commands` (for constructors).
   - Remove `Canvas`, `Paint`, `Rect`, `Font`, `PaintMode` imports from
     primitives.

Special cases:

- `window`:
  - Uses `save`/`clipRect`/`restore` to clip children.
  - Must emit `:save`, `:clip-rect`, and `:restore` commands around its
    children’s commands.
  - The planning function should:
    - Compute background/border/header/controls as rect/text commands.
    - Wrap child commands with `:save`, `:clip-rect`, `:restore`.

- `stack` (`:vstack` / `:hstack`):
  - Emits an optional background rect.
  - Returns concatenated child commands.

- `dropdown` overlays:
  - `render/queue-overlay!` remains but now accumulates overlay *models*.
  - The `plan-overlay` multimethod creates option rect + text commands.

- `minimap`:
  - Uses heatmap helpers from `reactui.render`.
  - Should be expressed via `:rect` commands for cells, plus rect outlines
    for viewports and borders.

#### 4.2.3 `reactui.app/render!`

Current behavior:

- Applies Skija scaling:
  - `.save`, `.scale`, `.restore` directly on the `Canvas`.
- Calls `render-ui-tree` with `:canvas canvas`.

Target behavior:

- Replace direct Skija calls with draw commands:
  ```clojure
  (defn render!
    [^Canvas canvas viewport game-state]
    (state/set-ui-viewport! game-state viewport)
    (let [scale (state/ui-scale game-state)
          input (state/get-input game-state)
          pointer (when (:mouse-initialized? input)
                    {:x (/ (double (or (:mouse-x input) 0.0)) scale)
                     :y (/ (double (or (:mouse-y input) 0.0)) scale)})
          render-context {:pointer pointer
                          :active-interaction (ui-core/active-interaction)}
          {:keys [layout-tree commands]}
          (ui-core/render-ui-tree {:canvas nil          ;; no direct drawing here
                                   :tree (root-tree game-state)
                                   :viewport (logical-viewport scale viewport)
                                   :context render-context})
          scaled-commands (into [{:op :save}
                                 {:op :scale :sx scale :sy scale}]
                                (conj commands {:op :restore}))]
      (skia/draw-commands! canvas scaled-commands)
      layout-tree))
  ```

- Alternatively, the scaling commands can be pushed down into the world
  frame plan so that the top-level `draw-frame` composes **both** world
  and UI commands and calls `skia/draw-commands!` once.

---

## 5. Concrete Implementation Steps

This section outlines a practical order of work to fully migrate the
codebase.

### Step 1 – Introduce Command Model and Skia Backend

1. Add `silent-king.render.commands`:
   - Implement constructors for:
     - `clear`, `rect`, `circle`, `line`, `polygon-fill`,
       `polygon-stroke`, `image-rect`, `text`.
     - `save`, `restore`, `translate`, `rotate`, `scale`, `clip-rect`.
   - Keep functions small and pure, returning maps or small vectors.
2. Add `silent-king.render.skia`:
   - Public API:
     - `(draw-commands! ^Canvas canvas commands & [opts])`.
   - Implement an `execute-command!` multimethod or `case` on `:op` that:
     - Allocates and closes appropriate Skija objects.
     - Mirrors all current drawing behavior.
   - Start by supporting the subset of commands used by a single simple
     primitive (e.g. `label`) to keep initial implementation small.

### Step 2 – Migrate a Simple UI Primitive End-to-End

Use `label` as the pilot conversion:

1. Implement `plan-label` and `render/plan-node :label`.
2. Extend `reactui.render` with:
   - `plan-node` and `plan-overlay` multimethods.
   - `layout-and-plan-tree`.
3. Update `reactui.core/render-ui-tree` to:
   - Call `layout-and-plan-tree`.
   - If `:canvas` is non-nil, call `skia/draw-commands!`.
4. Implement `draw-commands!` support for `:text`.
5. Run the app and confirm labels render as before.
6. Update tests (if any) to assert on label commands where helpful.

This validates the core architecture without touching world rendering.

### Step 3 – Convert Remaining UI Primitives

For each primitive (`slider`, `dropdown`, `window`, `minimap`, `stack`):

1. Replace `draw-*` and `render/draw-node` with `plan-*` and
   `render/plan-node` that return command vectors.
2. Ensure overlays are represented as data and `plan-overlay` produces
   commands.
3. Remove direct Skija imports from primitives.
4. Confirm UI still renders identically.
5. Add/extend tests in `test/silent_king/reactui`:
   - Assert that `render-ui-tree` returns expected `layout-tree`.
   - Optionally assert on the sequence of commands for simple scenes
     (e.g., slider handle position vs. value).

At the end of this step:

- Only `reactui.render` and `render.skia` should import Skija for UI.

### Step 4 – Introduce World Frame Plan

1. In `silent-king.core`, extract world rendering into a planner:
   - New function:
     ```clojure
     (defn frame-world-plan
       [width height time game-state]
       ;; roughly the body of current draw-frame,
       ;; but returning {:commands [...] :metrics {...}}
       )
     ```
   - Keep all camera, LOD, and visibility logic intact.
2. Initially, for minimal disruption:
   - Use `frame-world-plan` inside `draw-frame`:
     ```clojure
     (defn draw-frame
       [^Canvas canvas width height time game-state]
       (let [{:keys [commands metrics]} (frame-world-plan width height time game-state)]
         (skia/draw-commands! canvas commands)
         (update-metrics! game-state metrics)))
     ```
   - `frame-world-plan` can delegate to existing `draw-*` functions via
     adapters while they still accept `Canvas`.
   - This step focuses on centralizing the plan at the `core` level.

### Step 5 – Migrate World Modules to Plans

For each world rendering namespace:

#### 5.1 `silent-king.render.galaxy`

1. Introduce plan functions:
   - `plan-star-from-atlas`
   - `plan-full-res-star`
   - `plan-planet-from-atlas`
   - `plan-orbit-ring`
   - `plan-selection-highlight`
2. Each function:
   - Takes numeric coordinates/angles and asset handles.
   - Returns vectors of commands (no `Canvas`).
3. Replace `draw-*` bodies with calls to `skia/draw-commands!` over the
   corresponding plan functions during the transition.
4. Update `core/frame-world-plan` to use plan functions directly and
   remove adapter usage once stable.

#### 5.2 `silent-king.hyperlanes`

1. Extract the rendering part into `plan-hyperlanes`:
   - Signature similar to existing `draw-all-hyperlanes` but returning
     `{:commands [...] :count rendered-count}`.
2. Use `:line` commands with style maps encoding:
   - LOD-specific widths and caps.
   - Colors (including optional gradient + glow).
   - Animation phase (for pulse width).
3. Adapt `draw-all-hyperlanes` to call the planner and then
   `skia/draw-commands!` until callers are switched to use the plan.

#### 5.3 `silent-king.voronoi`

1. Extract rendering logic to `plan-voronoi-cells`:
   - Same parameters as `draw-voronoi-cells`.
   - Returns `{:commands [...] :rendered-count n}`.
2. Convert Path-building to:
   - `:polygon-fill` and `:polygon-stroke` commands with screen-space
     vertices.
   - `:circle` commands for 2-point and centroid cases.
3. Replace `draw-voronoi-cells` with a thin adapter that delegates to
   the plan + `skia/draw-commands!` until `core` is updated.

#### 5.4 `silent-king.regions`

1. Introduce `plan-regions`:
   - Takes zoom/pan/game-state.
   - Returns all region/sector label text commands.
2. Remove direct `Font`/`Paint` usage from `regions` once the Skia backend
   handles text.

At the end of Step 5:

- `frame-world-plan` is the single world renderer entry point and
  contains no Skija calls.
- `hyperlanes`, `voronoi`, `regions`, and `render.galaxy` are purely
  plan generators.
- Only the Skia backend applies commands to a `Canvas`.

### Step 6 – Compose World and UI Plans

1. Update `silent-king.core/draw-frame` to:
   - Call `frame-world-plan`.
   - Call a new `reactui.app/ui-plan` (or equivalent) that returns
     UI commands for the current frame.
   - Prepend a global `:clear` command.
   - Combine world and UI commands into one frame command vector in the
     correct Z-order (world first, UI last).
   - Call `skia/draw-commands!` once with the combined list.
2. Move any remaining Skija scaling logic for UI into plan commands
   (`:save`, `:scale`, `:restore` around UI commands).

After this step, there should be only two places that know about Skija:

- The Skia backend (`silent-king.render.skia`).
- The low-level window/surface creation and teardown code in
  `silent-king.core` (which owns the `Canvas`, `Surface`, and context).

### Step 7 – Cleanup and Enforcement

1. **Remove now-unneeded adapters**:
   - Delete the transitional `draw-*` functions that simply forwarded to
     plans + backend.
   - Ensure callers use plan functions or frame-level planners directly.
2. **Strip Skija imports**:
   - Run `rg "Canvas" src` and ensure:
     - Only `silent-king.core` and `silent-king.render.skia` import
       `Canvas`.
   - Run `rg "Paint\\b" src` and similar for `Font`, `Path`, `Shader` and
     confirm usage is isolated to the Skia backend (and asset loading for
     `Image`).
3. **Add a lightweight safeguard**:
   - Optionally add a small test that fails if any namespace under
     `src/silent_king` (except allowed render backend files) imports
     Skija `Canvas`.

---

## 6. Testing and Metrics

### 6.1 Unit Testing Plans

- **World tests**:
  - Create new tests under `test/silent_king` that:
    - Build a small synthetic `game-state` with known stars/hyperlanes/
      regions.
    - Call `frame-world-plan`, `plan-hyperlanes`, `plan-voronoi-cells`,
      etc.
    - Assert on:
      - Number of commands.
      - Existence and order of certain command types (e.g., Voronoi fill
        before stroke).
      - Presence of expected colors, coordinates, and op types.

- **UI tests**:
  - Extend `reactui/core_test.clj`:
    - Call `render-ui-tree` with `:canvas nil`.
    - Capture the returned commands via a test hook or by calling the
      planner directly.
    - Assert that layout and command sequence match expectations for
      common UI structures (sliders, dropdowns, minimap window).

### 6.2 Performance Metrics

- Replace direct counting inside draw functions with plan-based metrics:
  - E.g., `plan-hyperlanes` returns `:rendered-count`.
  - `plan-voronoi-cells` returns `:rendered-count`.
  - `frame-world-plan` aggregates these into a `:metrics` map.
- Use a new helper in `core` to write metrics back into `game-state`
  after planning, before executing commands.
- Optionally, track total `:draw-commands` as a cheap proxy for draw
  call count.

---

## 7. End-State Checklist

Use this as a final verification list once implementation is complete:

- [ ] `rg "Canvas" src` only shows:
  - `silent-king.core` (window/context/surface management).
  - `silent-king.render.skia` (the backend).
- [ ] `rg "drawRect" src`, `rg "drawCircle" src`, `rg "drawString" src`,
  `rg "drawLine" src`, `rg "drawPath" src`, `rg "clipRect" src` only
  match in `silent-king.render.skia`.
- [ ] All `render.*` / `reactui.*` domain namespaces:
  - Define plan functions that return vectors of draw commands.
  - Import no Skija classes except where asset types (`Image`) are
    unavoidable.
- [ ] `silent-king.core/draw-frame`:
  - Builds a frame-level plan from world and UI planners.
  - Calls `skia/draw-commands!` exactly once per frame.
- [ ] Core rendering logic is covered by tests that:
  - Run without a `Canvas`.
  - Assert on draw command sequences instead of pixel output.

Once all boxes are checked, Silent King will have a fully indirect,
plan-based rendering system with Skija usage cleanly isolated, making
rendering logic easier to reason about, refactor, and test.

