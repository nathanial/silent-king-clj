# Rendering System Architecture

## Overview

Silent King uses a **Retained Mode** rendering architecture built on top of **Skija** (Java bindings for Skia) and **LWJGL** (OpenGL). The rendering pipeline is data-driven: the game logic produces a sequence of immutable "draw commands" (Clojure maps), which are then interpreted and executed by the rendering backend.

This decoupling allows for a clean separation between game state, rendering logic, and the underlying graphics API.

## Key Components

### 1. Entry Point & Main Loop (`core.clj`)
- **`init-glfw` & `create-window`**: Handles low-level windowing setup using LWJGL / GLFW.
- **`render-loop`**: The main application loop.
  - Updates game time.
  - Manages the Skija `Surface` (resizing).
  - Calls `draw-frame`.
  - Swaps buffers and polls events.

### 2. Frame Composition (`core.clj` -> `reactui`)
The rendering process is integrated into the "Reactified" UI system. The entire game world is effectively a UI component.

- **`draw-frame`**: 
  - Invokes `react-app/render!` to generate the entire frame's command list.
  - Clears the screen.
  - Execute commands via `skia/draw-commands!`.
  
- **`reactui/app.clj`**: Orchestrates the UI layout (docking, windows) and includes the `:galaxy` primitive for the main game view.

### 3. Galaxy Rendering (`reactui/primitives/galaxy.clj`)
The `plan-galaxy` function is responsible for rendering the game world. It functions as a bridge between the game state and the command generation.

**Rendering Steps:**
1.  **Culling**: Calculates which stars/planets are visible within the widget bounds based on the camera's zoom and pan.
2.  **Layering**: Generates commands in a specific order (Painter's Algorithm):
    -   Background (Black)
    -   Voronoi Cells (`voronoi/plan-voronoi-cells`)
    -   Regions / Void Carving (`regions/plan-regions`)
    -   Hyperlanes (`hyperlanes/plan-all-hyperlanes`)
    -   Planet Orbit Rings
    -   Stars (LOD-based: Atlas vs Full Res)
    -   Planets
3.  **Command Aggregation**: Returns a flat sequence of render commands to the UI system.

### 4. Command Generation (`render/galaxy.clj`)
Helper functions responsible for creating specific draw command maps for game entities.

- **`plan-star-from-atlas`**: Calculates texture coordinates from the atlas and world-to-screen transforms.
- **`plan-selection-highlight`**: Generates commands for the selection ring animation.
- **`star-visible?`**: Logic for frustum culling.

### 5. Command Execution (`render/skia.clj`)
The "interpreter" that translates the data-driven commands into actual Skija calls.

- **`draw-commands!`**: Iterates recursively through command structures.
- **`execute-command!`**: Dispatches based on the `:op` key (e.g., `:rect`, `:image-rect`, `:line`).
- **Wrappers**: Functions like `draw-rect!` and `draw-image-rect!` handle type conversions (Clojure numbers to Java floats) and resource management (Paints, Shaders).

## Data Structures

### Draw Command Example
```clojure
{:op :image-rect
 :image <SkijaImage>
 :src {:x 0 :y 0 :width 64 :height 64}   ;; Texture coordinates
 :dst {:x 100 :y 100 :width 32 :height 32} ;; Screen coordinates
 :transform {:rotation-deg 45
             :anchor :center}}
```

### Render Metrics
Performance metrics are collected during the render planning phase (e.g., number of visible stars, draw calls) and stored in the `game-state` for display in the Performance Overlay.

## Coordinate Systems
- **World Space**: The game's logical coordinate system (Galaxy generation).
- **Screen Space**: Pixel coordinates relative to the window (or UI widget).
- **Transforms**: Handled via `silent-king.camera` helpers (`transform-position`, `zoom->position-scale`) before command generation. Skija's matrix stack is primarily used for local transformations (rotations) and UI scaling.

## Asset Management (`assets.clj`)
- Textures are loaded into **Atlases** (XS, Small, Medium) to minimize state changes and improve batching potential (though Skija handles batching internally).
- **LOD System**: The renderer selects the appropriate atlas based on the current camera zoom level.
