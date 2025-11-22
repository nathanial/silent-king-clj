# Window Docking System Implementation Plan

This document outlines the plan for implementing a flexible window docking system in Silent King, supporting side docking (left, right, top, bottom), center docking, tabbing, and drag-and-drop interactions.

## 1. Core Concepts & Architecture

The current window system uses "Floating Windows" managed by `state.clj` (bounds, minimized status) and `app.clj` (instantiation). We will extend this to support "Docked Windows".

### 1.1. Docking State Model (`src/silent_king/state.clj`)

We will introduce a new `:docking` schema in the game state under `:ui`.

```clojure
{:ui
  {:windows {...} ;; Existing floating window state
   :docking
     {:left   {:windows [window-id1 window-id2] ;; Ordered list of window IDs
               :active  window-id1              ;; Currently visible tab
               :size    300.0}                  ;; Width (or height) of the dock area
      :right  {:windows [] :active nil :size 300.0}
      :bottom {:windows [] :active nil :size 200.0}
      :top    {:windows [] :active nil :size 200.0}
      ;; Center usually represents the main viewport, but we could allow docking there too.
      ;; For now, we assume center is the main game view (Galaxy/System view).
      }}}
```

### 1.2. Window Lifecycle

A window can be in one of two states:
1.  **Floating**: Managed by `:ui :windows` bounds and `window-order`.
2.  **Docked**: Removed from `window-order` (so it's not rendered as a float), and added to one of the `:docking` lists.

## 2. New Components & Primitives

### 2.1. `dock-container` Primitive

A new React primitive that renders a dock area.
-   **Props**: `:side` (:left, :right, :top, :bottom), `:width/:height`, `:active-window`.
-   **Visuals**:
    -   Background panel.
    -   **Tab Bar**: Renders tabs for each docked window. Clicking a tab sets `:active`.
    -   **Content Area**: Renders the content of the `:active` window.
    -   **Splitter**: A resize handle on the inner edge to adjust the dock's size.

### 2.2. `root-layout` Component

We need a high-level layout component (likely in `app.clj`) that orchestrates the screen real estate.
1.  Calculate screen bounds.
2.  Subtract enabled/populated dock areas from the edges.
3.  Render the **Dock Containers** in the subtracted areas.
4.  Render the **Main Viewport** (Game View) in the remaining center rect.
5.  Render **Floating Windows** on top (z-index layer).

## 3. Interaction Design

### 3.1. Dragging to Dock

-   **Trigger**: Dragging a floating window by its header.
-   **Detection**: While dragging, check if the mouse cursor enters a "Dock Trigger Zone" (e.g., the outer 50px of the screen edges).
-   **Feedback**: Show a semi-transparent highlight overlay indicating where the window will dock.
-   **Action (Drop)**:
    -   Remove window ID from `window-order`.
    -   Add window ID to the target dock's `:windows` list.
    -   Set it as `:active`.
    -   The window primitive switches to rendering inside the dock container (no header, no resize borders).

### 3.2. Undocking (Tear-out)

-   **Trigger**: Dragging a tab from a Dock Container.
-   **Action**:
    -   Remove window ID from the dock's `:windows` list.
    -   Calculate drag start position.
    -   Add window ID to `window-order` (bring to front).
    -   Set `:bounds` for the window at the mouse position.
    -   Initiate a "drag" interaction immediately so the user is holding the new floating window.

### 3.3. Reordering Tabs

-   Support dragging tabs within the same dock to reorder them.

## 4. Implementation Roadmap

### Step 1: State Management Updates
-   Modify `src/silent_king/state.clj`:
    -   Add `default-docking-state`.
    -   Add helper functions: `dock-window!`, `undock-window!`, `get-docked-windows`, `set-dock-size!`.
    -   Update `create-game-state` to include `:docking`.

### Step 2: Layout Logic (`src/silent_king/reactui/layout.clj`)
-   Implement a layout helper that takes `viewport` and `dock-state` and returns:
    -   Rects for Left, Right, Top, Bottom docks.
    -   Rect for Center (remaining space).

### Step 3: Dock Primitive (`src/silent_king/reactui/primitives/dock.clj`)
-   Create `dock-container` primitive.
-   Implement rendering of tabs and content.
-   Handle resize (splitter) interactions.

### Step 4: Drag & Drop Integration
-   Update `src/silent_king/reactui/interaction.clj` to support dock zones.
-   Modify `window.clj` to handle "drop" events that trigger docking.
-   Modify `dock.clj` to handle tab dragging (undocking).

### Step 5: App Integration (`src/silent_king/reactui/app.clj`)
-   Refactor `app-root` to use the new `root-layout` strategy.
-   Ensure existing windows (Inspector, Performance, etc.) can be seamlessly moved between floating and docked states.

## 5. Edge Cases & Considerations

-   **Minimizing**: Docked windows probably shouldn't be "minimized" in the traditional sense. They can be switched away from (inactive tab). If the user wants to hide the dock entirely, we might need a "Collapse Dock" feature.
-   **Empty Docks**: If a dock has no windows, it should have 0 width/height or be hidden, allowing the center view to expand.
-   **Persistence**: Docking state should ideally be persisted (if we were saving layout), but for now, defaults in `state.clj` are fine.
