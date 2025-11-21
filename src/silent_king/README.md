# Silent King Core

This directory (`silent_king`) contains the core logic for the game.

## Architecture Overview

The application follows a functional, data-oriented architecture.

### 1. State Management
-   **Atoms**: The entire application state is held in a few global atoms (defined in `core.clj` but managed via `state.clj`):
    -   `game-state`: Contains the "world" state (stars, planets, player data) and "input" state.
    -   `render-state`: Contains ephemeral rendering resources (window handle, Skia context, textures).
-   **Immutability**: Game logic functions take the current state (map) and return a new state or derived data.

### 2. The Game Loop
The main loop is implemented in `core.clj` (`render-loop`).
1.  **Input**: Polls GLFW events and updates the `game-state` (mouse position, clicks).
2.  **Update**: Updates time-dependent state (frame count, current time).
3.  **Plan**: Calls `frame-world-plan` to generate a list of **Draw Commands** for the world and UI.
    -   This involves calculating visibility, transforms (world -> screen), and generating render primitives.
4.  **Render**: Passes the commands to the `skia` namespace to be drawn to the screen.
5.  **Metrics**: Records performance metrics (FPS, memory) back into the state.

## Key Namespaces

-   **`core.clj`**: The entry point (`-main`) and the heart of the application. It initializes the window, sets up the loop, and orchestrates the frame lifecycle.
-   **`state.clj`**: Defines the schema of the application state and provides helper functions for updating it.
-   **`galaxy.clj`**: Logic for procedurally generating the galaxy (stars, positions).
-   **`hyperlanes.clj`**: Algorithms for connecting stars (e.g., Delaunay triangulation, RNG graphs).
-   **`voronoi.clj`**: Implementation of Voronoi diagrams for territory generation.
-   **`regions.clj`**: Logic for defining political or geographic regions.
-   **`camera.clj`**: Handles coordinate transformations between "World Space" (game coordinates) and "Screen Space" (pixels).
-   **`assets.clj`**: Manages the loading and lifecycle of static assets (images, fonts, texture atlases).

## Subdirectories

-   **`reactui/`**: A custom, immediate-mode UI framework. See [reactui/README.md](reactui/README.md) for details.
-   **`render/`**: The rendering pipeline. See [render/README.md](render/README.md) for details.
-   **`minimap/`**: Logic specific to the minimap view.
-   **`tools/`**: Internal tools, such as procedural image generation.
