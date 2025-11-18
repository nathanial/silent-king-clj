# Reactified UI Plan (First Draft)

This is a **first‑pass design** for a new React‑style, unidirectional‑dataflow UI layer that will **replace** the previous entity‑based widget system. The legacy widget/UI implementation has already been removed from the codebase; what remains are Markdown reference documents that describe what the old components did so we can recreate them cleanly in the new system.

The goal is to let us write Clojure components that return **pure data trees**, and to have a **separate renderer** that turns those trees into draw calls (likely via multimethods). The new layer should integrate with the existing game ECS (stars, hyperlanes, camera) but does **not** need to coexist with the old `silent-king.widgets` implementation beyond using its behavior as a design reference.

---

## 1. Where We Are Today (Very Brief)

### 1.1 Runtime situation

- The game still uses an ECS for **stars, hyperlanes, camera, and core state** (`silent-king.state`, `silent-king.galaxy`, `silent-king.hyperlanes`, etc.).
- The **old widget/UI implementation has been removed**:
  - `src/silent_king/ui/*.clj` (controls, star inspector, hyperlane settings, performance dashboard, specs, theme) are gone.
  - `src/silent_king/widgets/*.clj` (core, layout, render, interaction, draw_order, animation, minimap, config) are gone.
- Only the **rendering of stars and hyperlanes** remains hooked up to the main loop; there is currently no in-game UI overlay.

### 1.2 Legacy UI reference docs

The behavior and intent of the old UI have been captured in Markdown documents:

- High-level UI panels:
  - `src/silent_king/ui/controls.md` – control panel + minimap.
  - `src/silent_king/ui/star_inspector.md` – star selection and inspector panel.
  - `src/silent_king/ui/hyperlane_settings.md` – hyperlane settings panel.
  - `src/silent_king/ui/performance_dashboard.md` – performance dashboard overlay.
- UI support and design system:
  - `src/silent_king/ui/specs.md` – UI state specs/helpers.
  - `src/silent_king/ui/theme.md` – colors, typography, spacing, panel/widget dimensions.
- Widget system architecture:
  - `src/silent_king/widgets/core.md` – widget ECS integration and constructors.
  - `src/silent_king/widgets/layout.md` – layout invalidation and stacking semantics.
  - `src/silent_king/widgets/render.md` – Skija rendering for each widget type.
  - `src/silent_king/widgets/interaction.md` – hit testing and interaction behaviors.
  - `src/silent_king/widgets/draw_order.md` – z-index and render ordering.
  - `src/silent_king/widgets/animation.md` – camera animation helpers.
  - `src/silent_king/widgets/minimap.md` – minimap math and transforms.
  - `src/silent_king/widgets/config.md` – `ui-scale` and global UI configuration.

These files are now the **authoritative description of what the old UI did**, and they should be treated as functional requirements and design inspiration for the Reactified rewrite.

### 1.3 Gaps the Reactified UI must fill

The legacy system gave us **data‑driven widgets** and **multimethod rendering**, but it lacked:
- a **pure “virtual UI tree”** abstraction,
- **prop‑based, composable components** that just return data,
- a clear **unidirectional flow** from “model → UI tree → rendering/interaction”.

The Reactified UI will reintroduce the useful behaviors documented above, but with a cleaner architecture that avoids mutating ECS widget entities directly.

---

## 2. High‑Level Target: React‑Style Data Flow

### 2.1 Desired Properties

- **Pure components**:
  - Components are plain Clojure functions: `(fn [props] ui-tree)`.
  - No internal mutation or direct `state/update-entity!` calls inside components.
- **Unidirectional data flow**:
  - Game/core state → derive **view model** → feed into UI component tree → produce pure primitive tree → renderer → draw calls.
  - Events travel “up” via callbacks that produce **state transition functions**, not direct mutations.
- **Separation of concerns**:
  - **Component layer**: pure Clojure data for UI.
  - **Renderer layer**: consumes a tree of primitives and calls drawing multimethods.
  - **Integration layer**: glues game state, layout, interaction, and rendering together.
- **Legacy-free implementation**:
  - We no longer need to preserve the old widget implementation; instead we need to **recreate its functionality** (as described in the Markdown docs) in a React-style system.
  - We can mine the legacy docs and Git history for algorithms and behaviors, but the new code should be architecturally independent.

### 2.2 Core Idea

1. Define a shared **primitive UI vocabulary**: panels, stacks, labels, buttons, sliders, etc., expressed as **pure data** (no entity IDs, no atoms).
2. Let components **compose** these primitives into trees.
3. Build a **renderer** which walks a primitive tree and:
   - either issues drawing commands directly (immediate‑mode), or
   - uses an internal ECS-style representation as an implementation detail, guided by the legacy widget docs (not by the old code).
4. Events (clicks, sliders, etc.) are mapped back into **pure event values** which the app can turn into **state updates**.

---

## 3. Primitive Element Representation

We will use **Hiccup‑like vectors** as the canonical primitive representation.

**Shape**
```clojure
[:panel {:key :control-panel
         :bounds {:x 16 :y 16 :width 320 :height 260}
         :style {:background-color 0xCC222222
                 :border-radius 12.0}}
  [:vstack {:gap 8 :padding {:all 12}}
    [:button {:on-click [:ui/toggle-hyperlanes]}
     "Toggle Hyperlanes"]
    [:hstack {:gap 8}
      [:label {:text "Zoom"}]
      [:slider {:value zoom
                :min 0.4
                :max 10.0
                :step 0.1
                :on-change [:ui/set-zoom]}]]]]
```

**Rationale**
- Hiccup is idiomatic in Clojure and reads cleanly at the REPL.
- It matches the desired “components return trees” style.
- It keeps syntax light for authoring panels like the hyperlane toggle + zoom slider.

---

## 4. Component Model (Pure Functions)

### 4.1 Basic Component Shape

We define components as **pure functions from props → Hiccup tree**.

Example: a minimal control panel with a hyperlane toggle and zoom slider:
```clojure
(defn control-panel
  [{:keys [zoom hyperlanes-enabled]}]
  [:panel {:key :control-panel
           :bounds {:x 16 :y 16 :width 320 :height 120}
           :style {:background-color 0xCC222222
                   :border-radius 12.0}}
   [:vstack {:gap 8 :padding {:all 12}}
    [:button {:on-click [:ui/toggle-hyperlanes]}
     (if hyperlanes-enabled "Disable Hyperlanes" "Enable Hyperlanes")]
    [:hstack {:gap 8}
     [:label {:text "Zoom"}]
     [:slider {:value zoom
               :min 0.4
               :max 10.0
               :step 0.1
               :on-change [:ui/set-zoom]}]]]])
```

### 4.2 Event Props as Data (Event Vectors)

Instead of passing raw fns that close over `game-state`, components emit **event descriptors**:

```clojure
[:button {:on-click [:ui/reset-camera]} "Reset Camera"]
[:slider {:value zoom
          :min 0.4
          :max 10.0
          :step 0.1
          :on-change [:ui/set-zoom]}]
```

The runtime interprets `:on-click` / `:on-change` as **messages** that the app turns into **state updates**. All callbacks use **event vectors**, and we will prefer namespaced event keywords (e.g. `:ui.controls/reset-camera`).

---

## 5. Rendering Architecture

We will use an **immediate‑mode renderer** as the core and only rendering path for the Reactified UI.

**Idea**
- Treat the primitive tree as a **virtual UI** that we render **directly** every frame.
- The renderer walks the tree, computes layout for each node, and calls Skija drawing primitives without creating a persistent UI entity graph.

**Rough flow**
```clojure
(defn render-ui-tree
  [^Canvas canvas root-element viewport game-state]
  (let [layout-tree (layout/compute-layout root-element viewport)]
    (render/paint-tree canvas layout-tree game-state)))
```

**Pros**
- Pure, React‑like, easy to reason about.
- No `:entities` or layout-dirty tracking; layout is recomputed from scratch each frame.
- Keeps the new UI layer small and focused, which is ideal for starting with panels, buttons, and sliders.

**Cons**
- We must re‑implement hit testing, scroll math, dropdown behavior, etc. for the new tree shape.
- Requires careful attention to performance as UI grows (though early panels will be small).

---

## 6. Layout in the Reactified World

We will design a **fresh, simple layout system** tailored to the immediate‑mode renderer, rather than porting legacy layout semantics.

Initial scope:
- Vertical stacks (`:vstack`) for column layout.
- Horizontal stacks (`:hstack`) for row layout.
- Fixed‑size panels (`:panel` with explicit `:bounds`).
- Simple padding/gap support.

As we add more widgets, we can extend this with alignment options and responsive behavior, but we do **not** need to match the legacy `widgets/layout.md` exactly.

### Functional Layout in the Tree

Given our immediate‑mode choice, layout will be a pure function:
- We define pure layout functions that walk the element tree and compute final `:bounds` for each node.
- The layout tree is just data; rendering uses it directly.

Example sketch:
```clojure
(defn compute-layout
  [element viewport]
  ;; returns element tree annotated with :bounds for each node
  )
```

**Pros**
- Fully functional, easy to test.
- No layout‑dirty bookkeeping; each frame’s tree is self‑contained.

**Cons**
- We need to design and iterate on layout behavior as we add more component types (beyond the initial panels, buttons, and sliders).
- Are you open to a world where UI layout is recomputed every frame (like typical immediate‑mode UIs), given the relatively small widget counts?

---

## 7. Event & Interaction Model

We need a clear, React‑like story for events:

1. User input → hit testing over the rendered UI.
2. The interaction layer identifies a **primitive node** and event type.
3. The node’s props contain an **event handler descriptor** (e.g. `[:ui/reset-camera]`).
4. The app consumes the event descriptor and applies a **pure state transition** to `game-state`.

### 7.1 Functional Event Handling over the Layout Tree

Given the immediate‑mode choice, hit testing and interaction will operate directly on the **layout tree**:
- Define `handle-mouse-event` functions that walk the layout tree, find the target node, and produce zero or more **event vectors**.
- A central dispatcher (e.g. `dispatch-event!`) interprets those vectors and updates `game-state`.

Example sketch:
```clojure
(defn handle-mouse-click
  [layout-tree x y]
  ;; returns a sequence of event vectors, e.g. [[:ui/toggle-hyperlanes]]
  )

(defn dispatch-event!
  [game-state event]
  (match event
    [:ui/toggle-hyperlanes] (state/toggle-hyperlanes! game-state)
    [:ui/set-zoom value]    (state/update-camera! game-state assoc :zoom value)
    ;; ...
    ))
```

More complex widgets (dropdowns, scroll-views) can build on top of the same pattern once we add them.

---

## 8. State & Props: Mapping from Game State

We should treat `game-state` as the **single source of truth** and map it into props for Reactified components.

Example: control panel props:
```clojure
(defn control-panel-props
  [game-state]
  {:zoom (get-in @game-state [:camera :zoom])
   :stats {:fps (get-in @game-state [:metrics :performance :latest :fps] 0.0)
           :total-stars (get-in @game-state [:metrics :latest :total-stars] 0)
           ;; ...
           }
   :ranges theme/ranges
   :theme  theme/*current*}) ;; or whatever theme story we prefer
```

Then, on each frame (once a renderer exists):
```clojure
(defn render-ui-frame!
  [canvas game-state]
  (let [props (control-panel-props game-state)
        tree  (control-panel props)]
    ;; e.g., immediate-mode render:
    ;; (render-ui-tree canvas tree game-state)
    ))
```

## 9. Bootstrapping Strategy (No Legacy UI)

The legacy widget UI is already removed; the Reactified UI will be built **from scratch**, guided by the Markdown docs listed in section 1.2. We will start with **panels (vertical + horizontal), buttons, and sliders**, so we can implement a basic control panel with:
- A button to toggle hyperlane visibility.
- A slider to control zoom.

1. **Define primitive vocabulary and component model**
   - Decide between Hiccup vs map primitives.
   - (Done conceptually) Use Hiccup vectors as the public API.
   - Define `render-primitive` multimethod (for direct Skija drawing) and a small set of initial primitive types: `:panel`, `:vstack`, `:hstack`, `:button`, `:label`, `:slider`.
   - Add Spec (optional) for primitive nodes.

2. **Implement the immediate‑mode renderer**
   - Implement a prototype renderer that:
     - Walks a Hiccup tree.
     - Computes layout for `:panel`, `:vstack`, `:hstack`.
     - Issues Skija drawing calls for `:panel`, `:button`, `:label`, and `:slider`.
   - Add minimal hit testing and interaction for `:button` and `:slider`, using event vectors.

3. **Build the first Reactified control panel**
   - Implement a `control-panel` component (as sketched in section 4.1) that:
     - Shows a button bound to `[:ui/toggle-hyperlanes]`.
     - Shows a slider bound to `[:ui/set-zoom]`.
   - Wire the renderer into `silent-king.core/draw-frame` so this panel renders on top of the starfield.
   - Route events as data (e.g. `[:ui/reset-camera]`) into a central dispatcher that calls existing helpers (`reset-camera!`, `state/toggle-minimap!`, etc.), but keep the **component code** free of direct `update-entity!` usage.

4. **Extend vocabulary and infrastructure**
   - Add more primitives as needed (`:scroll-view`, `:dropdown`, `:toggle`, charts, etc.).
   - Generalize layout and interaction helpers, still within the immediate‑mode renderer.

5. **Use legacy docs as guidance**
   - For each Reactified screen, refer to the corresponding legacy Markdown doc(s) (e.g. `controls.md`, `hyperlane_settings.md`, `performance_dashboard.md`) as **design inspiration and behavioral guidance**, not as exact specs we must match.

---

## 10. Open Questions & Decisions To Make

Here’s a consolidated list of items that are **still open** and worth deciding before we start coding:

There are no remaining open design questions for this initial phase; the key decisions are summarized below.

---

## 11. Summary & Next Steps

With the following decisions made:

- Primitive representation: **Hiccup vectors**.
- Rendering: **clean immediate‑mode** renderer (no UI ECS).
- Events: **standardized event vectors** with a central dispatcher.
- Layout: **new, simple functional layout**, not legacy‑driven.
- State: a single `game-state` atom mapped into props; no separate UI atom initially.
- Legacy docs: treated as suggestions and reference, not fixed constraints.
- First primitive/panel target: **`:vstack`** (used to build a control panel with hyperlane toggle button and zoom slider).

The next step is to turn this plan into concrete namespaces and function signatures (e.g. `silent-king.reactui.core`, `layout`, `render`, `events`) and then implement the initial primitives plus the first `:vstack`-based control panel.

---

## 12. Phased Implementation Plan

To make the work concrete and incremental, we can break it into phases:

**Phase 1 – Core primitives and layout**
- Create a new `silent-king.reactui` (or similar) namespace group.
- Implement:
  - Hiccup primitive normalizer (if needed).
  - `:vstack` layout and rendering.
  - Minimal `render-ui-tree` that can draw a static `:vstack` of labels on top of the starfield.
- Testing:
  - Add clojure.test tests for `:vstack` layout (e.g. simple trees with known bounds).
  - Add a smoke test that builds a tiny Hiccup tree and passes it through `render-ui-tree` without exceptions.

**Phase 2 – Buttons and sliders**
- Add `:button` and `:slider` primitives:
  - Rendering (visual states can be simple at first).
  - Hit testing and interaction (clicks and value changes) that emit event vectors.
- Introduce a central `dispatch-event!` that handles:
  - `[:ui/toggle-hyperlanes]` → `state/toggle-hyperlanes!`
  - `[:ui/set-zoom value]`    → `state/update-camera!` with `:zoom`.
- Testing:
  - Unit tests for button hit testing and click handling (ensure correct event vectors are produced).
  - Unit tests for slider value mapping from mouse coordinates and event emission (`[:ui/set-zoom value]`).

**Phase 3 – Control panel integration**
- Implement the `control-panel` component using `:vstack`, `:button`, and `:slider`.
- Integrate `render-ui-frame!` into `silent-king.core/draw-frame`:
  - Build props from `game-state`.
  - Render the control panel over the existing starfield.
- Verify that:
  - The hyperlane toggle button affects rendering.
  - The zoom slider updates camera zoom and feels responsive.
- Testing:
  - Integration-style tests that:
    - Build a small `game-state`, render the control panel, and simulate button/slider events.
    - Assert that `state/toggle-hyperlanes!` and `state/update-camera!` effects are observed on `game-state`.

**Phase 4 – Additional primitives and panels**
- Add more layout and display primitives (`:hstack`, additional labels/text styling).
- Start designing the next Reactified panels (e.g. a simplified performance overlay or hyperlane settings UI), using the legacy Markdown docs as high-level guides.
- Testing:
  - For each new primitive, add unit tests for layout, rendering preconditions, and event behavior.
  - For each new panel, add focused tests that exercise its event vectors and basic state wiring.

Each phase should be small enough to validate at the REPL, and we can refine the layout, rendering quality, event model, and tests incrementally once the basic control panel is working.
