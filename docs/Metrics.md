# Metrics System

This document outlines how performance and world metrics are tracked, stored, and displayed in Silent King.

## Overview

Metrics are centrally managed in the `game-state` atom. They are collected during the render loop and displayed via the `performance-overlay` UI component.

## Data Structure

Metrics are stored in `game-state` under the path `[:metrics :performance]`.

```clojure
{:metrics
 {:performance
  {:fps-history []        ;; Vector of recent FPS values (rolling window)
   :frame-time-history [] ;; Vector of recent frame times
   :last-sample-time 0.0  ;; Timestamp of last update
   :latest {              ;; The most recent metrics snapshot
     :fps 0.0
     :frame-time-ms 0.0
     :memory-mb 0.0
     :current-time 0.0
     ;; World metrics (currently not populated in active render path):
     ;; :visible-stars
     ;; :visible-hyperlanes
     ;; :draw-calls
     ;; :total-stars
     ;; :hyperlane-count
   }}}}
```

## Metric Collection

### System Metrics (FPS, Memory)
System metrics are calculated and recorded in `silent-king.core/draw-frame` at the end of every frame.

1.  **FPS**: Calculated as `frame-count / current-time`.
2.  **Frame Time**: Derived from FPS (`1000.0 / fps`).
3.  **Memory**: Calculated using `Runtime/getRuntime` (`totalMemory - freeMemory`).

These are updated via `silent-king.state/record-performance-metrics!`, which:
*   Updates the `:latest` map.
*   Appends new values to `:fps-history` and `:frame-time-history`.
*   Trims history vectors to `max-performance-samples` (120).

### World Metrics (Stars, Hyperlanes, etc.)
**Note**: Detailed world metrics (e.g., visible star counts) were previously calculated in `silent-king.core/frame-world-plan`.

With the migration to the ReactUI system (`silent-king.reactui`), the rendering logic moved to `silent-king.reactui.primitives.galaxy`. Currently, this primitive calculates visibility for culling purposes but **does not** report these counts back to the global metrics state. As a result, fields like `visible-stars` in the overlay may show `0` or missing data.

## Display

The metrics are visualized by the `silent-king.reactui.components.performance-overlay` component.

*   **FPS & Frame Time**: Displayed as text and a historical bar chart.
*   **Memory**: Displayed in MB.
*   **Render Stats**: The component is designed to show `Visible Stars`, `Visible Hyperlanes`, and `Draw Calls`, reading from the `:latest` metrics map.

## Key Files

*   `src/silent_king/state.clj`: Defines the metrics data structure and update logic (`record-performance-metrics!`).
*   `src/silent_king/core.clj`: Calculates system metrics in the main render loop (`draw-frame`).
*   `src/silent_king/reactui/components/performance_overlay.clj`: UI component for displaying metrics.
