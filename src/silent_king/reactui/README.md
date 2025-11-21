# ReactUI

This directory contains a custom, immediate-mode UI framework inspired by React, built to work within the Silent King application. It sits on top of the Skia graphics library (via HumbleUI Skija).

## Architecture

The framework follows a unidirectional data flow and a component-based architecture. The lifecycle of a UI frame is as follows:

1.  **Normalization**: The UI tree, defined as Hiccup-style vectors (e.g., `[:button {:on-click ...} "Click Me"]`), is "normalized" into a tree of maps (`{:type :button, :props {...}, :children [...]}`).
2.  **Layout**: A layout pass runs on the normalized tree (`layout/compute-layout`). This calculates the position and bounds of every element based on its type and properties.
3.  **Rendering**: A render pass (`render/plan-tree`) traverses the layout tree and generates a list of **draw commands**. These commands are pure data structures.
4.  **Execution**: The draw commands are executed by the Skia backend (`silent-king.render.skia`), performing the actual drawing on the canvas.

## Primitives vs. Components

### Primitives
Primitives are the fundamental building blocks of the UI. They are the "leaf nodes" or "atomic elements" that the engine understands directly.
-   **Definition**: Primitives are registered via multimethods (e.g., `normalize-tag`, `layout-node`, `render-node`).
-   **Examples**: `:button`, `:label`, `:window`, `:slider`, `:dropdown`.
-   **Role**: They handle their own specific layout logic, rendering commands, and interaction behavior.

### Components
Components are higher-level abstractions that compose primitives (or other components).
-   **Definition**: They are simply Clojure functions that return a Hiccup vector.
-   **Role**: They provide reusable UI patterns and manage local state or logic before passing it down to primitives. They do not have special handling in the core engine; they expand into primitives during the normalization phase.

## Interaction Handling

Interaction is handled by the `silent-king.reactui.interaction` namespace and the core event loop.

1.  **Input Events**: GLFW input events (mouse move, click, scroll) are captured in `core.clj`.
2.  **Hit Testing**: The engine uses the **layout tree** (which contains the calculated bounds of every element) to determine which node is under the cursor (`interaction/node-at`).
3.  **Dispatch**: Events are dispatched to the target node via multimethods (`pointer-down!`, `pointer-up!`, `pointer-drag!`).
4.  **Capture System**: For interactions like dragging a slider or moving a window, the node is "captured" (`capture-node!`). This ensures that the node continues to receive events even if the mouse moves outside its bounds, until the interaction ends (`release-capture!`).

## Key Files

-   **`core.clj`**: The main entry point. Handles the normalization -> layout -> render pipeline and orchestrates input handling.
-   **`primitives.clj`**: Registers all available UI primitives.
-   **`layout.clj`**: Contains the layout logic.
-   **`render.clj`**: Generates draw commands from the layout tree.
-   **`interaction.clj`**: Helper functions for hit testing and event processing.
-   **`events.clj`**: A system for dispatching and handling UI events (e.g., `:on-click`).
