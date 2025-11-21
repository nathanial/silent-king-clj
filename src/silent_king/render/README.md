# Render

This directory handles the rendering logic for the game. The rendering system is designed to be **data-driven** and separates the "planning" of what to draw from the "execution" of drawing commands.

## Architecture

### 1. Command-Based Rendering
Instead of making direct calls to the graphics API (Skia) throughout the game logic, the code generates **Draw Commands**.
-   **Draw Commands**: Pure Clojure maps that describe a drawing operation.
    -   Example: `{:type :image-rect, :src {...}, :dst {...}}` or `{:type :circle, :center {...}, :radius 10, :paint {...}}`.
-   **`commands.clj`**: Defines the schema and helper functions for creating these command maps.

### 2. Planning Phase
The "planning" functions (e.g., in `galaxy.clj`) take the current game state and generate a sequence of draw commands.
-   **Pure Functions**: These functions are side-effect free. They calculate screen coordinates, visibility, and visual styles, returning a list of commands.
-   **Visibility Culling**: Logic for determining what is on screen (e.g., `star-visible?`) happens during this phase to minimize the number of commands generated.

### 3. Execution Phase
The `skia.clj` namespace is responsible for interpreting the draw commands and executing them.
-   **`draw-commands!`**: Iterates over the list of commands and calls the corresponding Skia methods (e.g., `.drawImageRect`, `.drawCircle`).
-   **Abstraction**: This layer isolates the game logic from the specific graphics library, making it easier to test, debug, or potentially swap the backend.

## Files

-   **`commands.clj`**: Definitions of abstract draw commands.
-   **`galaxy.clj`**: Rendering logic specific to the galaxy view (stars, planets, orbits). It calculates transforms and emits commands.
-   **`skia.clj`**: The backend implementation that executes commands using HumbleUI Skija.
