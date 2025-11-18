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

We need a canonical shape for the “Reactified” primitive elements. Here are two main options.

### Option A: Hiccup‑Like Vectors

**Shape**
```clojure
[:panel {:key :control-panel
         :bounds {:x 16 :y 16 :width 320 :height 260}
         :style {:background-color (theme/get-color :background :panel-secondary)
                 :border-radius (theme/get-border-radius :xl)
                 :shadow (theme/get-shadow :md)}}
  [:vstack {:gap 8 :padding {:all 12}}
    [:label {:text "Silent King Controls"
             :style {:color (theme/get-color :text :primary)
                     :font-size (theme/get-font-size :heading)
                     :font-weight :bold}}]
    [:label {:text "Zoom"
             :style {:color (theme/get-color :text :tertiary)}}]
    [:slider {:value zoom
              :range (:zoom theme/ranges)
              :on-change [:ui/zoom-changed]}]
    [:button {:on-click [:ui/toggle-hyperlanes]} "Toggle Hyperlanes"]
    [:button {:on-click [:ui/toggle-minimap]} "Toggle Minimap"]
    [:button {:on-click [:ui/reset-camera]} "Reset Camera"]
    [:label {:text stats-text
             :style {:color (theme/get-color :text :tertiary)
                     :font-size (theme/get-font-size :tiny)}}]]]
```

**Pros**
- Very idiomatic in Clojure (similar to Reagent/rum).
- Simple to read and compose.
- Easy to extend with context data, metadata, etc.

**Cons**
- We need conventions around map keys (e.g. `:props` vs `:attrs` vs `:style`).
- Slightly more “convention‑heavy” for nesting and children.

### Option B: Explicit Maps

**Shape**
```clojure
{:type :panel
 :key :control-panel
 :props {:bounds {:x 16 :y 16 :width 320 :height 260}
         :style {:background-color (theme/get-color :background :panel-secondary)
                 :border-radius (theme/get-border-radius :xl)
                 :shadow (theme/get-shadow :md)}}
 :children
 [{:type :label
   :key :title
   :props {:text "Silent King Controls"
           :style {:color (theme/get-color :text :primary)
                   :font-size (theme/get-font-size :heading)
                   :font-weight :bold}}}
  ;; ...
  ]}
```

**Pros**
- Uniform shape; easy to spec/validate.
- Direct mapping to multimethod dispatch (`defmulti render-primitive :type`).

**Cons**
- Heavier syntax than Hiccup.
- Slightly more boilerplate in simple UIs.

**Clarifying questions**
- Do you prefer **Hiccup‑like** (`[:panel props child ...]`) or **map‑based** (`{:type :panel ...}`) primitives?
- How important is **Spec validation** of UI trees for you in this first iteration?

---

## 4. Component Model (Pure Functions)

### 4.1 Basic Component Shape

We can define components as **pure functions from props → element tree**.

Example, control panel as a single component:
```clojure
(defn control-panel
  [{:keys [zoom stats ranges theme]}]
  [:panel {:key :control-panel
           :bounds {:x (theme/get-panel-dimension :control :margin)
                    :y (theme/get-panel-dimension :control :margin)
                    :width (theme/get-panel-dimension :control :width)
                    :height (theme/get-panel-dimension :control :height)}
           :style {:background-color (theme/get-color :background :panel-secondary)
                   :border-radius (theme/get-border-radius :xl)
                   :shadow (theme/get-shadow :md)}}
   ;; children...])
```

### 4.2 Event Props as Data, Not Functions

Instead of passing raw fns that close over `game-state`, components should emit **event descriptors**:

```clojure
[:button {:on-click [:ui/reset-camera]} "Reset Camera"]
[:slider {:value zoom
          :range (:zoom ranges)
          :on-change [:ui/set-zoom]}]
```

The runtime then interprets `:on-click` / `:on-change` as **messages** that the app turns into **state updates**.

**Clarifying questions**
- Do you want **all callbacks** to be “event vectors” (like Re-frame), or do you want to allow raw fns in some cases?
- Should we encourage **namespaced event keywords** (`:ui.control/reset-camera`) for clarity?

---

## 5. Rendering Architecture Options

We need to decide how the Reactified primitives map to actual drawing. There are two main approaches, plus a hybrid.

### Option 1: Immediate‑Mode Renderer (No ECS Widgets)

**Idea**
- Treat the primitive tree as a **virtual UI** that we render **directly** every frame.
- The renderer walks the tree, computes layout on the fly, and calls Skija drawing primitives without creating ECS widget entities.

**Rough flow**
```clojure
(defn render-ui-tree
  [^Canvas canvas root-element time viewport]
  (let [layout-tree (layout/compute-layout root-element viewport)]
    (render/paint-tree canvas layout-tree time)))
```

**Pros**
- Pure, React‑like, easy to reason about.
- No need for `:entities` or `layout-dirty` tracking; the layout is recomputed from scratch each frame.
- Sidesteps some of the complexity of the current widget ECS.

**Cons**
- We throw away a lot of existing `widgets.*` code (layout, interaction, z‑ordering).
- Need to re‑implement hit testing, scroll math, dropdown behavior, etc. for the new tree shape.

**Good fit if**
- We are comfortable gradually deprecating ECS widgets for UI.
- We want a clean “functional UI” layer dedicated to overlay widgets.

### Option 2: React → ECS‑Style Adapter (Virtual to Internal UI Entities)

**Idea**
- Use an ECS‑style internal representation as the **“DOM”** for Reactified screens (similar in spirit to the legacy widgets, but implemented fresh).
- The Reactified tree is **diffed** against a cached tree, and differences are applied as mutations on an internal set of UI entities and components (bounds, layout hints, render props, interaction state, etc.).

**Rough flow**
```clojure
(defn reconcile!
  [game-state old-tree new-tree]
  ;; diff trees by :key and :type, then:
  ;; - create new entities for new nodes
  ;; - update components on existing entities
  ;; - remove entities for deleted nodes
  )

(defn render-reactified-ui!
  [game-state root-component props]
  (let [old-tree (get-in @game-state [:ui-react/root])
        new-tree (root-component props)]
    (reconcile! game-state old-tree new-tree)
    (swap! game-state assoc :ui-react/root new-tree)))
```

**Pros**
- Lets us design a **React-style API** while reusing familiar concepts from the legacy widget system (as documented in `src/silent_king/widgets/*.md`).
- Keeps layout/render/interaction logic encapsulated in one place behind an adapter.

**Cons**
- Requires building a **reconciler** (diffing algorithm) and a mapping from primitive props → internal UI components.
- Updates are not purely functional; they mutate internal entities.
- We would be introducing a new ECS‑backed UI layer, not reusing the old one.

**Good fit if**
- We want to leverage the conceptual model of the old widgets (layout, draw order, interaction) while still moving to a Reactified authoring story.
- We are comfortable implementing a fresh ECS‑style UI backend guided by the Markdown docs and tests, rather than depending on the deleted code.

### Option 3: Hybrid: Immediate‑Mode for New Widgets, ECS for Legacy

**Idea**
- Use **Option 1** for new, self‑contained Reactified panels and overlays.
- Optionally introduce an **internal ECS‑style layer** (Option 2) later if we find we need a UI “DOM” for performance or incremental updates.

**Pros**
- Lets us experiment with a clean Reactified stack immediately (the legacy UI is already removed).
- We can optionally introduce a small adapter or internal ECS layer to reuse math and ideas from `src/silent_king/widgets/*.md` without reviving the old implementation.

**Cons**
- Two UI systems running side by side during the transition.
- Need a clear boundary (e.g. immediate‑mode UI in a dedicated overlay layer, or explicit “old UI off / new UI on” switches).

**Clarifying questions**
- Which direction sounds more appealing: **pure immediate‑mode renderer** first, or a **React → internal ECS adapter** that mirrors the legacy widget concepts from the Markdown docs?
- How important is **minimizing coupling to an ECS‑style UI backend** versus keeping everything purely functional and immediate‑mode?

---

## 6. Layout in the Reactified World

In the legacy system, layout was driven by widget entities and `:layout` components (see `src/silent_king/widgets/layout.md`). In Reactified land we can:

### Option A: Port Legacy Layout Semantics into a New Module

- Each primitive node carries layout hints (similar to the old `:layout` maps).
- A new layout engine (pure or ECS‑backed) walks the primitive tree and computes child bounds using rules inspired by:
  - `compute-vstack-layout`, `compute-hstack-layout`, and `apply-anchor-position` from the legacy design.

**Pros**
- Minimal conceptual change from the previous behavior; easier to ensure visual parity with the old panels.
- We can reuse the mental model and tests from the old system while re-implementing the code in a more functional style.

**Cons**
- Layout may still end up partially stateful if we choose an ECS‑backed representation.
- We need to carefully design the API so it fits React-style, prop-based components.

### Option B: Functional Layout in the Tree

If we lean toward a **pure immediate‑mode layout**:
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
- Requires porting or re-specifying the layout rules described in `src/silent_king/widgets/layout.md` into pure functions.

**Clarifying questions**
- Would you prefer to **port the legacy layout semantics** into a new engine (possibly ECS‑backed), or invest in a **from-scratch pure layout module** as part of the Reactified plan?
- Are you open to a world where UI layout is recomputed every frame (like typical immediate‑mode UIs), given the relatively small widget counts?

---

## 7. Event & Interaction Model

We need a clear, React‑like story for events:

1. User input → hit testing over the rendered UI.
2. The interaction layer identifies a **primitive node** and event type.
3. The node’s props contain an **event handler descriptor** (e.g. `[:ui/reset-camera]`).
4. The app consumes the event descriptor and applies a **pure state transition** to `game-state`.

### 7.1 Integration with an ECS‑Style Interaction Layer

If we go with the **adapter approach (Option 2)**:
- Internal UI entities would still have `:interaction` components (similar to the legacy design in `src/silent_king/widgets/interaction.md`).
- The Reactified layer controls `:on-click`, `:on-change`, etc. by:
  - encoding them as **event descriptors** on primitives,
  - then generating corresponding interaction callbacks that dispatch those descriptors into a central handler.

Sketch:
```clojure
[:button {:on-click [:ui/reset-camera]} "Reset Camera"]
```

Adapter layer:
```clojure
(defn attach-interaction-callbacks
  [primitive]
  (update primitive :props
          (fn [props]
            (cond-> props
              (:on-click props)
              (assoc :on-click
                     (fn dispatch-click []
                       (dispatch-event (:on-click props))))))))
```

Here `dispatch-event` is our own function that takes an event vector and mutates `game-state` (or enqueues an event in a queue).

### 7.2 Functional Event Handling (No ECS Interaction Components)

If we go more immediate‑mode:
- Hit testing and interaction would operate directly on the **layout tree**.
- We define `handle-mouse-event` functions that walk the tree, find the target node, and produce a sequence of **event descriptors**.
- The app then interprets those descriptors and updates `game-state`.

**Clarifying questions**
- Do you envision a **central event dispatcher** (single `handle-event!` that pattern matches event vectors), or many smaller handlers sprinkled around?
- How much of the legacy interaction behavior (as documented in `src/silent_king/widgets/interaction.md`) do you want to preserve vs. rethink—especially for complex widgets like dropdowns and scroll‑views?

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

**Clarifying questions**
- Are you comfortable deriving **all UI props** from `game-state` (and maybe a separate `ui-state` sub‑tree), or do you want a **separate UI atom** for Reactified components?
- Should per‑panel UI state (e.g. star inspector visibility, performance dashboard position) continue to live under `[:ui ...]` in `game-state`, or would you like a different structure?

---

## 9. Bootstrapping Strategy (No Legacy UI)

The legacy widget UI is already removed; the Reactified UI will be built **from scratch**, guided by the Markdown docs listed in section 1.2. Here’s a staged rollout that reflects that reality.

1. **Define primitive vocabulary and component model**
   - Decide between Hiccup vs map primitives.
   - Define `render-primitive` multimethod (if we go direct Skija) or mapping to an internal UI representation.
   - Add Spec (optional) for primitive nodes.

2. **Decide on an initial implementation route**
   - **Adapter route**: Implement a minimal reconciler from primitives → internal ECS‑style UI entities for a small subset (`:panel`, `:vstack`, `:hstack`, `:label`, `:button`, `:slider`), with semantics guided by `src/silent_king/widgets/*.md`.
   - **Immediate‑mode route**: Implement a prototype immediate‑mode renderer that walks the primitive tree, computes layout, and issues Skija calls directly.

3. **Build the first new Reactified screen**
   - Choose a concrete feature (e.g. a new debug overlay, an alternate control panel, or a redesigned dashboard) and implement it entirely in the Reactified system, using the legacy docs as functional reference where applicable.
   - Route events as data (e.g. `[:ui/reset-camera]`) into a central dispatcher that calls existing helpers (`reset-camera!`, `state/toggle-minimap!`, etc.), but keep the **component code** free of direct `update-entity!` usage.

4. **Extend vocabulary and infrastructure**
   - Add more primitives (`:scroll-view`, `:dropdown`, `:toggle`, charts, etc.) as needed by new Reactified screens.
   - Generalize layout and interaction helpers so that new UI work can preferentially use the Reactified system instead of the old one.

5. **Validate against legacy behavior**
   - For each Reactified screen, compare its behavior and visuals against the corresponding legacy Markdown doc(s) (e.g. `controls.md`, `star_inspector.md`, etc.).
   - Use those docs as acceptance criteria rather than trying to match the old implementation line-for-line.

**Clarifying questions**
- Does this **bootstrapping-first plan** (no legacy UI, just legacy docs) match how you’d like to evolve the UI?
- What kind of **new or replacement screen** would you prefer to build first in the Reactified system (e.g. a new debug overlay, a redesigned performance dashboard, an experimental control panel)?

---

## 10. Open Questions & Decisions To Make

Here’s a consolidated list of choices we should resolve before coding:

1. **Primitive representation**
   - Hiccup‑like vectors vs explicit maps?
   - How much Spec/validation do we want up front?

2. **Rendering strategy**
   - Adapter to an internal ECS‑style UI layer (React → internal entities)?
   - Immediate‑mode renderer (no ECS) for new UI?
   - Hybrid (start adapter‑based, add immediate‑mode later)?

3. **Event representation**
   - Use Re‑frame‑style event vectors everywhere (`[:ui/reset-camera]`)?
   - Allow raw functions in a few places, or keep everything as data?
   - Central event dispatcher vs many small handlers?

4. **Layout evolution**
   - Reuse `widgets.layout` and existing layout math via adapter?
   - Extract / reimplement layout as pure functions over primitive trees?

5. **State organization**
   - Single `game-state` atom as the source of truth, mapping into props?
   - Dedicated `ui-react` sub‑tree for Reactified panel state?
   - Where should per‑panel UI state live (`:ui` vs a new root key)?

6. **Migration priorities**
   - Which panel or overlay’s **functionality** (as described in `src/silent_king/ui/*.md`) do we want the Reactified system to cover first?
   - How quickly do we want to treat the legacy docs as **fixed requirements** and build all new UI exclusively in the Reactified system?

---

## 11. What I Need From You

To tighten this plan for a second draft, it would help to know:

- Which **primitive representation** do you prefer (vectors vs maps), and do you want Spec validations included early?
- Do you want to build an **internal ECS‑style UI layer** (adapter route) for new Reactified screens, or start with a **clean immediate‑mode renderer** that only borrows ideas/math from the legacy Markdown docs?
- Are you comfortable standardizing on **event vectors** for `:on-click`/`:on-change` etc., or do you want the flexibility of passing raw functions?
- Which area of the previous UI (controls, star inspector, hyperlane settings, performance dashboard, or a new debug overlay) should we target as the **first feature to implement or replace** in the Reactified system?
- How strongly do you want to mirror the legacy layout semantics (as described in `widgets/layout.md`) vs building a **new pure layout layer**?

Once we make those calls, we can write a more concrete v2 of this document with:
- a fixed primitive schema,
- a sketched API for component authors,
- and a small, concrete migration plan for the first panel.
