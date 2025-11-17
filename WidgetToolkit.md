# Widget Toolkit Design Plan

## Executive Summary

This document outlines a comprehensive widget toolkit for Silent King that leverages Clojure's functional programming strengths and Skija's rich 2D graphics capabilities. The toolkit will be built on the existing ECS architecture, treating widgets as specialized entities with layout, interaction, and rendering components.

## Design Philosophy

### Core Principles

1. **Data-Driven**: Widget definitions are pure Clojure data structures (maps, vectors)
2. **Declarative**: Describe what the UI should look like, not how to build it
3. **Functional & Composable**: Widgets are functions that compose naturally
4. **ECS-Integrated**: Widgets are entities with components (`:widget`, `:bounds`, `:visual`, `:interaction`)
5. **Immutable State**: UI state transformations are pure functions
6. **REPL-Friendly**: Live coding and hot-reloading of UI definitions

### Leveraging Clojure Strengths

- **Persistent data structures**: Efficient structural sharing for UI trees
- **Multi-methods**: Polymorphic rendering based on widget type
- **Protocols**: Clean abstraction for widget behaviors
- **Spec**: Runtime validation of widget definitions
- **Threading macros**: Elegant composition of widget transformations
- **Destructuring**: Clean parameter extraction from widget data

### Leveraging Skija Strengths

- **Canvas-based rendering**: Direct 2D drawing API
- **Rich text rendering**: Fonts, text shaping, measuring
- **Advanced painting**: Gradients, shaders, blend modes, image filters
- **Path rendering**: Complex shapes, rounded rectangles, bezier curves
- **Image manipulation**: Nine-patch scaling, tiling, atlas rendering
- **Hardware acceleration**: OpenGL-backed surfaces

## Architecture

### Component Types

Every widget entity has these core components:

```clojure
;; Widget identity and type
:widget {:type :button              ; Widget type keyword
         :id :my-button             ; Unique identifier
         :parent-id nil             ; Parent widget ID (for nesting)
         :children []}              ; Child widget IDs

;; Spatial bounds (screen space)
:bounds {:x 20.0                    ; Top-left x
         :y 20.0                    ; Top-left y
         :width 200.0               ; Width in pixels
         :height 40.0}              ; Height in pixels

;; Layout hints (for parent containers)
:layout {:type :auto                ; :fixed, :auto, :fill, :relative
         :padding {:top 5 :right 10 :bottom 5 :left 10}
         :margin {:top 0 :right 0 :bottom 0 :left 0}
         :anchor :top-left          ; :top-left, :center, :bottom-right, etc.
         :z-index 0}                ; Rendering order

;; Visual appearance
:visual {:background-color 0xFF3366CC
         :border-color 0xFF6699FF
         :border-width 2.0
         :border-radius 8.0
         :opacity 1.0
         :shader nil                ; Custom Shader for advanced effects
         :shadow {:offset-x 2.0 :offset-y 2.0 :blur 4.0 :color 0x80000000}}

;; Interaction state
:interaction {:enabled true
              :hovered false
              :pressed false
              :focused false
              :hover-cursor :pointer ; :default, :pointer, :text, :move, :resize
              :on-click nil          ; Callback fn
              :on-hover nil
              :on-drag nil}

;; Animation state
:animation {:type :none             ; :none, :fade, :slide, :scale, :custom
            :duration 0.3           ; seconds
            :easing :ease-out       ; :linear, :ease-in, :ease-out, :ease-in-out
            :progress 0.0           ; 0.0 - 1.0
            :from {}                ; Starting properties
            :to {}}                 ; Target properties
```

### Widget Hierarchy

```
Container Widgets (manage layout)
├─ Panel (basic rectangular container)
├─ VStack (vertical stack layout)
├─ HStack (horizontal stack layout)
├─ Grid (grid layout with rows/columns)
├─ Tabs (tabbed interface)
├─ ScrollView (scrollable viewport)
└─ Window (draggable floating window)

Interactive Widgets (respond to input)
├─ Button (clickable button)
├─ Toggle (checkbox/switch)
├─ Slider (value slider)
├─ TextField (text input)
├─ Dropdown (selection dropdown)
├─ RadioGroup (radio button group)
└─ ColorPicker (color selection)

Display Widgets (present information)
├─ Label (text display)
├─ Icon (image/icon display)
├─ ProgressBar (progress indicator)
├─ Gauge (circular gauge)
├─ Chart (line/bar/pie charts)
├─ Minimap (2D view thumbnail)
└─ Tooltip (hover information)

Specialized Widgets (domain-specific)
├─ StarInspector (selected star details)
├─ GalaxyMap (navigable galaxy overview)
├─ HyperlaneControl (hyperlane visualization controls)
├─ Timeline (temporal visualization)
└─ DebugOverlay (performance metrics)
```

## Widget Catalog

### 1. Container Widgets

#### Panel
**Purpose**: Basic rectangular container for grouping widgets

**Components**:
```clojure
{:widget {:type :panel :id :main-panel}
 :bounds {:x 20 :y 20 :width 300 :height 400}
 :layout {:padding {:all 10} :gap 5}
 :visual {:background-color 0xCC222222
          :border-radius 12.0
          :shadow {:offset-x 0 :offset-y 4 :blur 12 :color 0x80000000}}}
```

**Rendering**: Rounded rectangle with optional shadow, gradient background support

---

#### VStack / HStack
**Purpose**: Automatic vertical/horizontal layout

**Features**:
- Automatic child positioning based on axis
- Configurable gap between children
- Alignment options (start, center, end, stretch)
- Fill/auto sizing modes

**Example**:
```clojure
{:widget {:type :vstack :id :sidebar}
 :layout {:gap 8 :align :stretch :padding {:all 12}}
 :bounds {:x 0 :y 0 :width 250 :height :fill}}
```

---

#### Grid
**Purpose**: Grid-based layout with rows and columns

**Features**:
- Fixed or auto-sized rows/columns
- Span support (rowspan/colspan)
- Responsive breakpoints
- Gap between cells

---

#### ScrollView
**Purpose**: Scrollable viewport for overflow content

**Features**:
- Vertical/horizontal scrolling
- Momentum scrolling
- Scrollbar styling
- Viewport clipping

**Special considerations**:
- Needs scroll offset state
- Mouse wheel event handling
- Touch-like drag scrolling

---

#### Window
**Purpose**: Draggable floating window

**Features**:
- Title bar with close/minimize buttons
- Draggable header
- Resizable borders
- Z-order management
- Snap-to-edge behavior

---

### 2. Interactive Widgets

#### Button
**Purpose**: Clickable action trigger

**States**: default, hovered, pressed, disabled

**Visual variations**:
- Solid fill
- Outlined
- Ghost (transparent with hover)
- Gradient fill with shader

**Example**:
```clojure
{:widget {:type :button :id :reset-camera}
 :bounds {:x 20 :y 20 :width 120 :height 36}
 :visual {:background-color 0xFF3366CC
          :border-radius 6.0
          :text "Reset Camera"
          :font-size 14
          :text-color 0xFFFFFFFF}
 :interaction {:on-click #(reset-camera!)
               :hover-cursor :pointer}}
```

**Rendering features**:
- State-dependent colors (hover brightening)
- Smooth transitions between states
- Icon + text layout
- Skija shader for gradients

---

#### Slider
**Purpose**: Continuous value selection

**Features**:
- Horizontal/vertical orientation
- Min/max/step values
- Track + thumb rendering
- Value label (optional)
- Snapping to steps

**Components**:
```clojure
{:widget {:type :slider :id :zoom-slider}
 :bounds {:x 20 :y 100 :width 200 :height 20}
 :value {:min 0.4 :max 10.0 :current 1.0 :step 0.1}
 :visual {:track-color 0xFF444444
          :track-active-color 0xFF6699FF
          :thumb-color 0xFFFFFFFF
          :thumb-radius 10.0}
 :interaction {:on-change #(set-zoom! %)}}
```

---

#### Toggle
**Purpose**: Boolean state toggle (checkbox/switch)

**Visual styles**:
- Checkbox (box with checkmark)
- Switch (iOS-style sliding toggle)

**Rendering**:
- Animated transition (slide/fade)
- Checkmark using Skija Path
- Rounded pill shape for switch

---

#### TextField
**Purpose**: Text input

**Features**:
- Single/multi-line modes
- Cursor rendering (blinking)
- Text selection
- Placeholder text
- Input validation
- Auto-complete suggestions

**Challenges**:
- Keyboard input handling (GLFW callbacks)
- Text cursor positioning
- Selection rendering
- IME support (complex)

---

#### Dropdown
**Purpose**: Selection from list of options

**Features**:
- Collapsed/expanded states
- Option list rendering (as temporary window)
- Keyboard navigation (arrow keys)
- Search/filter

**Components**:
```clojure
{:widget {:type :dropdown :id :lod-selector}
 :bounds {:x 20 :y 200 :width 180 :height 32}
 :value {:options [:xs :small :medium :full]
         :selected :medium
         :labels {:xs "XS (64x64)"
                  :small "Small (128x128)"
                  :medium "Medium (256x256)"
                  :full "Full Resolution"}}
 :interaction {:on-select #(set-lod! %)}}
```

---

### 3. Display Widgets

#### Label
**Purpose**: Static text display

**Features**:
- Multi-line text with word wrap
- Text alignment (left/center/right)
- Font styling (family, size, weight)
- Text effects (shadow, outline)
- Icon prefix/suffix

**Skija features**:
- `Font` and `Typeface` for styling
- `TextLine` for shaping
- Advanced text measuring

---

#### ProgressBar
**Purpose**: Progress/loading indicator

**Visual styles**:
- Linear bar (horizontal/vertical)
- Circular/ring
- Indeterminate (animated)

**Rendering**:
- Animated fill using time-based shader
- Gradient fill
- Pulse animation for indeterminate

---

#### Gauge
**Purpose**: Circular value display (speedometer-style)

**Features**:
- Configurable min/max/value
- Arc rendering with Skija Path
- Needle/pointer
- Tick marks
- Value label

**Skija features**:
- `Canvas.drawArc()` for gauge background
- `Canvas.rotate()` for needle
- Shader for gradient fill

---

#### Chart
**Purpose**: Data visualization

**Types**:
- Line chart (time series)
- Bar chart (comparisons)
- Pie chart (proportions)
- Scatter plot (correlations)

**Features**:
- Axis rendering (labels, ticks, grid)
- Data point plotting
- Legend
- Tooltips on hover
- Zoom/pan support

**Rendering complexity**:
- Coordinate transformation (data → screen)
- Path building for lines
- Clipping to chart area
- Anti-aliased rendering

---

#### Minimap
**Purpose**: Thumbnail view of game world

**Features**:
- Scaled-down rendering of stars
- Viewport indicator (current camera view)
- Click-to-navigate
- Real-time updates

**Implementation**:
- Render to offscreen surface
- Simplified star rendering (no LOD)
- Viewport overlay rectangle
- Transform mouse coords to world coords

---

### 4. Specialized Widgets

#### StarInspector
**Purpose**: Display details of selected star

**Layout**:
```
┌─ Star Inspector ─────────────┐
│ ★ Star #4237                 │
│                               │
│ Position: (2345.2, 1892.4)   │
│ Size: 35.2 px                 │
│ Density: 0.73                 │
│ Rotation Speed: 1.8 rad/s    │
│                               │
│ Hyperlanes: 4 connections     │
│ ├─ Star #4238 (123.4 ly)     │
│ ├─ Star #4240 (89.2 ly)      │
│ └─ Star #4251 (201.8 ly)     │
└───────────────────────────────┘
```

**Components**:
- Panel container
- Label widgets for properties
- List of connected stars (VStack)
- Thumbnail image of star

---

#### GalaxyMap
**Purpose**: Overview map for navigation

**Features**:
- Entire galaxy rendered at small scale
- Cluster visualization
- Camera position indicator
- Click/drag to navigate
- Zoom controls

**Rendering**:
- Density-based rendering (aggregate stars)
- Heatmap coloring
- Hyperlane network (simplified)

---

#### HyperlaneControl
**Purpose**: Controls for hyperlane visualization

**UI Elements**:
- Toggle for enable/disable
- Slider for opacity
- Dropdown for color scheme
- Toggle for animation
- Slider for animation speed

---

#### DebugOverlay
**Purpose**: Performance and debug information

**Metrics**:
- FPS counter
- Frame time graph
- Entity count
- Visible entity count
- Memory usage
- Input state

**Layout**:
- Compact top-right corner display
- Expandable detailed view
- Real-time graphs using Chart widget

---

## Implementation Plan

**Philosophy**: Each phase delivers a concrete, playable feature improvement to Silent King. Build infrastructure incrementally, only what's needed to support each phase's user-facing features.

---

### Phase 1: Interactive Control Panel (Week 1)

**Game Feature**: Replace text-only UI with interactive camera controls

**What You'll See**:
- Polished control panel in top-left corner with:
  - Zoom slider (replaces text "Zoom: 1.5x")
  - "Reset Camera" button (centers view, resets zoom to 1.0)
  - "Toggle Hyperlanes" button (replaces 'H' key)
  - FPS counter and stats
- Smooth hover/pressed states on buttons
- Real-time slider interaction

**Infrastructure Built**:
1. Core widget system
   - `silent-king.widgets.core`: Entity creation, component management
   - `silent-king.widgets.render`: Multi-method rendering dispatch
   - `silent-king.widgets.interaction`: Hit testing, mouse events

2. Basic widgets
   - Panel (rounded rect container)
   - Label (text display)
   - Button (clickable with hover states)
   - Slider (horizontal value slider)
   - VStack (vertical layout for control panel)

3. Integration
   - Widget rendering in `draw-frame`
   - Mouse event routing (widgets first, then camera)
   - Camera control callbacks

**Deliverables**:
```clojure
;; In -main, after loading assets:
(ui/create-control-panel! game-state)
;; Adds interactive control panel to game
```

**Visual Impact**: Clean, modern control panel replaces debug text overlay

---

#### Phase 1 Implementation Status

**✅ COMPLETED:**

1. **Files Created:**
   - `src/silent_king/widgets/core.clj` - Core widget system with entity creation, component management, and VStack layout
   - `src/silent_king/widgets/render.clj` - Multi-method widget rendering with state-dependent styling
   - `src/silent_king/widgets/interaction.clj` - Hit testing and mouse event handling
   - `src/silent_king/ui/controls.clj` - High-level UI construction including control panel creation
   - `src/silent_king/widgets/draw_order.clj` - Deterministic render ordering helpers that keep parent containers behind their children

2. **Files Modified:**
   - `src/silent_king/core.clj` - Integrated widget rendering in `draw-frame` and mouse event routing in callbacks
   - `src/silent_king/widgets/core.clj` - Fixed VStack layout updates so child bounds are always written back to the right entities
   - `src/silent_king/widgets/render.clj` - Switched to the shared draw-order helper and removed invalid Skija `close` calls
   - `src/silent_king/widgets/interaction.clj` - Smarter hover/capture handling so sliders/buttons release state even when the mouse-up happens elsewhere

3. **Widget Types Implemented:**
   - Panel - Container with rounded corners, shadows, and background color
   - Label - Text display with alignment options
   - Button - Interactive button with hover/pressed states and callbacks
   - Slider - Horizontal value slider with draggable thumb
   - VStack - Vertical stack layout container

4. **Core Systems Working:**
   - Multi-method rendering dispatch by widget type
   - Nil-safe rendering (widgets skip rendering if bounds are invalid)
   - Mouse event routing (widgets get priority over camera controls)
   - Hit testing with deterministic z-index/ancestry sorting so panel backgrounds paint before children
   - State-dependent visual styling (hover, pressed states)
   - Paint object caching for performance

5. **Regression Tests in Place:**
   - `test/silent_king/widgets/core_test.clj` validates VStack layout spacing and stretch calculations
   - `test/silent_king/widgets/interaction_test.clj` covers hover/click/drag edge cases (e.g., releasing outside the widget)
   - `test/silent_king/widgets/draw_order_test.clj` ensures parent containers always render before their children
   - `run-tests.sh` / `clj -M:test` provide a fast way to run the suite headlessly

**Verification Checklist:**

1. `./run.sh` launches successfully, prints "Control panel created", and displays a visible panel with correctly stacked children
2. Widgets respond to hover/drag without locking up, and releasing outside a widget resets interaction state
3. `./run-tests.sh` (or `clj -M:test`) passes to prove layout, interaction, and draw-order invariants
3. **EXPECTED**: Control panel should now be visible in top-left corner with all widgets rendered
4. **TEST**: Hover over buttons - they should lighten
5. **TEST**: Click "Reset Camera" - camera should reset to zoom 1.0, centered
6. **TEST**: Click "Toggle Hyperlanes" - hyperlanes should appear/disappear
7. **TEST**: Drag the zoom slider - zoom level should change in real-time
8. **TEST**: Verify FPS counter updates each frame

**TECHNICAL NOTES:**

- Skija RRect requires `makeXYWH` factory method (not `makeComplexXYWH`) for uniform corner radii
- `RRect` must be imported from `io.github.humbleui.types`, not `io.github.humbleui.skija`
- All widget rendering methods have defensive nil checks for bounds to prevent crashes
- Mouse event routing checks widgets first (returns true if handled), then falls back to camera controls
- VStack layout uses dirty-tracking system for efficient updates (see `widgets/layout.clj`)

---

#### Phase 2 Implementation Status

**✅ COMPLETED:**

1. **Files Created/Updated:**
   - `src/silent_king/camera.clj` - Shared non-linear transform helpers reused by render, hyperlane, and minimap math
   - `src/silent_king/widgets/minimap.clj` - Pure math helpers for bounds, density bucketing, and camera viewport projection
   - `src/silent_king/widgets/render.clj` - Minimap renderer now draws density tiles and an accurate viewport rectangle
   - `src/silent_king/widgets/interaction.clj` - Click-to-pan uses the shared minimap transform and centers the camera correctly
   - `src/silent_king/core.clj` - Saves live viewport size for minimap usage

2. **Features Complete:**
   - Density-colored minimap that buckets all stars without an offscreen surface
   - Accurate viewport rectangle that reflects the non-linear camera transform
   - Click-to-navigate that pans the camera via animation while keeping world coordinates centered
   - Minimap visibility toggle wired through the control panel

3. **Tests Added:**
   - `test/silent_king/widgets/minimap_test.clj` covers minimap click behavior and hidden-state handling

4. **Verification Checklist:**
   - `./run.sh` shows the minimap in the bottom-right corner with density shading and viewport rectangle
   - Clicking inside the minimap recenters the camera smoothly
   - `./run-tests.sh` passes, covering minimap math plus prior widget suites

---

### Phase 2: Minimap Navigator (Week 2)

**Game Feature**: Minimap in bottom-right corner for galaxy navigation

**What You'll See**:
- 250x250px minimap showing entire galaxy at small scale
- Color-coded density visualization (bright = dense star clusters)
- Red rectangle showing current camera viewport
- Click minimap to jump camera to that location
- Smooth camera transitions when clicking
- Toggle button to show/hide minimap

**Infrastructure Built**:
1. Minimap widget
   - Offscreen rendering of galaxy overview
   - Density-based star aggregation (performance)
   - Viewport overlay rectangle
   - Click-to-navigate interaction

2. Layout improvements
   - Anchor system (bottom-right, top-left, etc.)
   - Margin support
   - Z-index for overlapping widgets

3. Animation system (basic)
   - Camera pan transitions
   - Smooth easing (ease-in-out)

**Deliverables**:
```clojure
;; Add to control panel or standalone:
(ui/create-minimap! game-state)
;; Adds minimap with click-to-navigate
```

**Visual Impact**: Professional navigation tool, see entire galaxy at a glance

---

### Phase 3: Star Selection & Inspector (Week 3)

**Game Feature**: Click stars to select them, view details in side panel

**What You'll See**:
- Click any star to select it (highlights with glow)
- Inspector panel slides in from the right edge showing:
  - Star image/icon
  - Star ID
  - Position coordinates
  - Size, density, rotation speed
  - List of connected hyperlanes (with distances)
- Click "Zoom to Star" button to center camera on selected star
- Click background or press ESC to deselect

**Infrastructure Built**:
1. Star selection system
   - World-space hit testing (transform screen → world coords)
   - Selection state in game-state
   - Selected star highlighting (glow effect)

2. StarInspector widget
   - Expandable side panel (slide-in animation)
   - Property display (labels with values)
   - Hyperlane connection list (VStack)
   - "Zoom to Star" action button

3. ScrollView (if needed for long hyperlane lists)
   - Viewport clipping
   - Vertical scrolling
   - Scrollbar rendering

**Deliverables**:
```clojure
;; Click handling in mouse callback:
(ui/handle-star-click! game-state world-x world-y)
;; Shows inspector for clicked star

;; Inspector widget:
(ui/create-star-inspector! game-state)
;; Displays info for selected star
```

**Visual Impact**: Deep interactivity, explore individual stars and connections

**Status**: ✅ Implemented in `silent_king.core`, `silent_king/ui/star_inspector.clj`, and the widget subsystems. Includes right-side slide-in panel, world-space selection, glow highlight, hyperlane scroll list, and `./run-tests.sh` coverage (see `silent-king.ui.star-inspector-test` & updated interaction tests).

---

### Phase 4: Hyperlane Visualization Controls (Week 4)

**Game Feature**: Advanced hyperlane visualization settings panel

**What You'll See**:
- "Hyperlane Settings" panel (initially collapsed, click to expand)
  - Master toggle: Enable/Disable hyperlanes
  - Opacity slider: 0% (invisible) to 100% (solid)
  - Color scheme dropdown: "Blue" (default), "Red", "Green", "Rainbow"
  - Animation toggle: Enable/Disable pulsing animation
  - Animation speed slider: Slow to Fast
  - Line width slider: Thin to Thick
- Real-time preview as you adjust settings
- "Reset to Defaults" button

**Infrastructure Built**:
1. Advanced interactive widgets
   - Dropdown/Select (color scheme picker)
   - Toggle/Checkbox (checkboxes for features)
   - Collapsible panel (expand/collapse animation)

2. Settings integration
   - Settings state in game-state
   - Live update of hyperlane rendering
   - Settings persistence (basic)

3. HStack layout
   - Horizontal layout for labels + controls

**Deliverables**:
```clojure
;; Settings panel:
(ui/create-hyperlane-settings! game-state)
;; Interactive control panel for hyperlanes
```

**Visual Impact**: Fine-tuned control over hyperlane appearance, artistic customization

---

### Phase 5: Performance Dashboard (Week 5)

**Game Feature**: Comprehensive performance monitoring overlay

**What You'll See**:
- Expandable performance dashboard (top-right corner)
  - Collapsed: Just FPS counter
  - Expanded: Full dashboard showing:
    - FPS graph (last 60 frames, line chart)
    - Frame time graph (ms)
    - Entity counts (stars, hyperlanes, widgets)
    - Visible entity counts (after culling)
    - Memory usage estimate
    - Render stats (draw calls, etc.)
- Click/drag to move dashboard
- Click pin icon to lock position

**Infrastructure Built**:
1. Chart widget
   - Line chart for FPS/frame time
   - Auto-scaling Y axis
   - Real-time data updates
   - Grid lines and labels

2. Draggable window
   - Drag from title bar
   - Boundary constraints
   - Drop shadow

3. Data collection
   - Frame time tracking
   - Entity count queries
   - Render stats collection

**Deliverables**:
```clojure
;; Performance overlay:
(ui/create-performance-dashboard! game-state)
;; Real-time performance monitoring
```

**Visual Impact**: Professional dev tools feel, understand performance at a glance

---

### Phase 6: Polish & Complete UI System (Week 6)

**Game Feature**: Complete, polished UI with all features integrated

**What You'll See**:
- Unified theme across all UI elements
- Smooth animations everywhere (fade in/out, slide, scale)
- Keyboard shortcuts overlay (press '?' to show)
  - H: Toggle Hyperlanes
  - M: Toggle Minimap
  - I: Toggle Inspector
  - P: Toggle Performance Dashboard
  - R: Reset Camera
  - ESC: Close panels/deselect
- Tooltips on all interactive elements (hover to see)
- High-contrast mode toggle (accessibility)
- Resizable panels (drag edges to resize)
- Multiple color themes: "Dark" (default), "Light", "Nord", "Dracula"

**Infrastructure Built**:
1. Complete animation system
   - Multiple easing functions
   - Chained animations
   - Animation callbacks

2. Theming system
   - Theme definitions (JSON/EDN)
   - Hot-reload themes from file
   - Theme switcher dropdown

3. Keyboard system
   - Global keyboard shortcuts
   - Keyboard navigation (tab between controls)
   - Focus indicators

4. Tooltip system
   - Auto-positioning (avoid screen edges)
   - Delay before showing
   - Rich content support

5. Advanced widgets
   - Resizable panels (drag handles)
   - Context menus (right-click)
   - Dropdown menus

6. Performance optimization
   - Render caching for static widgets
   - Dirty rectangle tracking
   - Paint/Font object pooling

**Deliverables**:
```clojure
;; Complete UI setup:
(ui/initialize-ui! game-state)
;; Sets up all UI systems with keyboard shortcuts, theming, etc.
```

**Visual Impact**: Production-quality, professional UI that feels cohesive and polished

---

## Summary of Deliverables by Phase

| Phase | Feature | Key Widgets | User Value |
|-------|---------|-------------|------------|
| 1 | Control Panel | Panel, Button, Slider, Label, VStack | Replace debug text with real controls |
| 2 ✅ | Minimap | Minimap, Animation | Navigate galaxy with single click |
| 3 | Star Inspector | Inspector, ScrollView, Selection | Explore individual stars and connections |
| 4 | Hyperlane Settings | Dropdown, Toggle, Collapsible Panel | Customize hyperlane appearance |
| 5 | Performance Dashboard | Chart, Draggable Window | Monitor performance in real-time |
| 6 | Polish & Themes | Tooltips, Keyboard, Theming, Animations | Professional, cohesive experience |

---

## Technical Implementation Details

### Rendering System

**Multi-method dispatch**:
```clojure
(defmulti render-widget
  "Render a widget based on its type"
  (fn [canvas widget-entity game-state time]
    (:type (state/get-component widget-entity :widget))))

(defmethod render-widget :button
  [^Canvas canvas widget-entity game-state time]
  (let [bounds (state/get-component widget-entity :bounds)
        visual (state/get-component widget-entity :visual)
        interaction (state/get-component widget-entity :interaction)

        ;; State-dependent colors
        bg-color (cond
                   (:pressed interaction) (darken (:background-color visual) 0.2)
                   (:hovered interaction) (lighten (:background-color visual) 0.1)
                   :else (:background-color visual))]

    ;; Render rounded rectangle
    (draw-rounded-rect canvas bounds bg-color (:border-radius visual))

    ;; Render text (centered)
    (draw-centered-text canvas (:text visual) bounds (:text-color visual))))
```

**Paint caching**:
```clojure
(def paint-cache (atom {}))

(defn get-or-create-paint [color]
  (if-let [cached (@paint-cache color)]
    cached
    (let [paint (doto (Paint.) (.setColor (unchecked-int color)))]
      (swap! paint-cache assoc color paint)
      paint)))
```

---

### Layout System

**Constraint-based layout**:
```clojure
(defn compute-vstack-layout
  "Calculate bounds for VStack children"
  [parent-bounds children layout-config]
  (let [{:keys [gap padding align]} layout-config
        content-width (- (:width parent-bounds)
                        (:left padding)
                        (:right padding))
        y-offset (atom (:top padding))]

    (for [child children]
      (let [child-layout (state/get-component child :layout)
            child-height (compute-child-height child content-width)
            child-width (case align
                         :stretch content-width
                         :fill content-width
                         :auto (get-auto-width child)
                         (:width (state/get-component child :bounds)))
            child-x (case align
                     :start (:left padding)
                     :center (+ (:left padding) (/ (- content-width child-width) 2))
                     :end (- (:width parent-bounds) (:right padding) child-width))
            child-y @y-offset
            child-bounds {:x (+ (:x parent-bounds) child-x)
                         :y (+ (:y parent-bounds) child-y)
                         :width child-width
                         :height child-height}]

        (swap! y-offset + child-height gap)
        [child child-bounds]))))
```

---

### Interaction System

**Hit testing**:
```clojure
(defn point-in-bounds? [x y bounds]
  (and (>= x (:x bounds))
       (>= y (:y bounds))
       (<= x (+ (:x bounds) (:width bounds)))
       (<= y (+ (:y bounds) (:height bounds)))))

(defn find-widget-at-point
  "Find topmost widget at screen coordinates (x, y)"
  [game-state x y]
  (let [widgets (state/filter-entities-with game-state [:widget :bounds])
        ;; Sort by z-index (highest first)
        sorted-widgets (sort-by
                        (fn [[_ w]]
                          (get-in (state/get-component w :layout) [:z-index] 0))
                        >
                        widgets)]
    (some (fn [[id widget]]
            (when (point-in-bounds? x y (state/get-component widget :bounds))
              [id widget]))
          sorted-widgets)))
```

**Event routing**:
```clojure
(defn handle-mouse-click [game-state x y]
  (when-let [[widget-id widget] (find-widget-at-point game-state x y)]
    (let [interaction (state/get-component widget :interaction)]
      (when (and (:enabled interaction)
                 (:on-click interaction))
        ;; Invoke callback
        ((:on-click interaction) widget-id)

        ;; Update pressed state
        (state/update-entity! game-state widget-id
                             #(state/add-component % :interaction
                                                  (assoc interaction :pressed true)))))))
```

---

### Animation System

**Transition framework**:
```clojure
(defn start-transition!
  "Start an animated transition on a widget"
  [game-state widget-id property from to duration easing]
  (state/update-entity! game-state widget-id
    #(state/add-component % :animation
       {:type :property
        :property property
        :from from
        :to to
        :duration duration
        :easing easing
        :start-time (get-current-time game-state)
        :progress 0.0})))

(defn update-animations!
  "Update all widget animations (called each frame)"
  [game-state current-time]
  (let [animated-widgets (state/filter-entities-with game-state [:widget :animation])]
    (doseq [[widget-id widget] animated-widgets]
      (let [animation (state/get-component widget :animation)
            elapsed (- current-time (:start-time animation))
            progress (min 1.0 (/ elapsed (:duration animation)))
            eased-progress (apply-easing progress (:easing animation))
            current-value (interpolate (:from animation)
                                      (:to animation)
                                      eased-progress)]

        ;; Update widget property
        (state/update-entity! game-state widget-id
          #(update-in % [:components :visual (:property animation)]
                     (constantly current-value)))

        ;; Update animation progress
        (state/update-entity! game-state widget-id
          #(assoc-in % [:components :animation :progress] progress))

        ;; Remove animation when complete
        (when (>= progress 1.0)
          (state/update-entity! game-state widget-id
            #(state/remove-component % :animation)))))))

(defn apply-easing [t easing]
  (case easing
    :linear t
    :ease-in (* t t)
    :ease-out (- (* 2 t) (* t t))
    :ease-in-out (if (< t 0.5)
                   (* 2 t t)
                   (- 1 (* 2 (- 1 t) (- 1 t))))
    t))
```

---

### Theming System

**Theme definition**:
```clojure
(def default-theme
  {:colors {:primary 0xFF3366CC
            :secondary 0xFF6699FF
            :background 0xFF1A1A1A
            :surface 0xFF2A2A2A
            :text 0xFFFFFFFF
            :text-secondary 0xFFAAAAAA
            :border 0xFF444444
            :success 0xFF66CC66
            :warning 0xFFFFCC66
            :error 0xFFCC6666}

   :typography {:font-family "Inter"
                :sizes {:small 12
                        :medium 14
                        :large 16
                        :xlarge 20}
                :weights {:normal 400
                          :medium 500
                          :bold 700}}

   :spacing {:xs 4
             :sm 8
             :md 12
             :lg 16
             :xl 24}

   :borders {:radius-sm 4.0
             :radius-md 8.0
             :radius-lg 12.0
             :width-thin 1.0
             :width-medium 2.0
             :width-thick 3.0}

   :shadows {:small {:offset-x 0 :offset-y 2 :blur 4 :color 0x40000000}
             :medium {:offset-x 0 :offset-y 4 :blur 8 :color 0x60000000}
             :large {:offset-x 0 :offset-y 8 :blur 16 :color 0x80000000}}})

(defn get-theme-color [color-key]
  (get-in default-theme [:colors color-key]))

(defn get-theme-spacing [spacing-key]
  (get-in default-theme [:spacing spacing-key]))
```

**Applying theme to widgets**:
```clojure
(defn create-themed-button [text on-click]
  (state/create-entity
    :widget {:type :button}
    :bounds {:width :auto :height 36}
    :visual {:background-color (get-theme-color :primary)
             :text-color (get-theme-color :text)
             :border-radius (get-theme-border :radius-md)
             :text text
             :font-size (get-theme-font-size :medium)}
    :layout {:padding {:all (get-theme-spacing :md)}}
    :interaction {:on-click on-click}))
```

---

## Integration with Existing Code

### Rendering Integration

**Modified `draw-frame` in `core.clj`**:
```clojure
(defn draw-frame [^Canvas canvas width height time game-state]
  ;; Clear background
  (.clear canvas (unchecked-int 0xFF000000))

  ;; Draw game world (stars, hyperlanes)
  (draw-game-world canvas width height time game-state)

  ;; Update widget animations
  (widgets/update-animations! game-state time)

  ;; Render all widgets (sorted by z-index)
  (widgets/render-all-widgets canvas game-state time))
```

---

### Input Integration

**Modified mouse callbacks**:
```clojure
(defn setup-mouse-callbacks [window game-state]
  (doto window
    (GLFW/glfwSetCursorPosCallback
     (reify GLFWCursorPosCallbackI
       (invoke [_ win xpos ypos]
         ;; Try widget interaction first
         (if-let [handled (widgets/handle-mouse-move game-state xpos ypos)]
           nil  ; Widget handled it
           ;; Fall back to game world interaction (camera pan)
           (handle-camera-pan game-state xpos ypos)))))

    (GLFW/glfwSetMouseButtonCallback
     (reify GLFWMouseButtonCallbackI
       (invoke [_ win button action mods]
         (when (= button GLFW/GLFW_MOUSE_BUTTON_LEFT)
           (let [input (state/get-input game-state)
                 x (:mouse-x input)
                 y (:mouse-y input)]
             ;; Try widget interaction first
             (if-let [handled (widgets/handle-mouse-click game-state x y (= action GLFW/GLFW_PRESS))]
               nil  ; Widget handled it
               ;; Fall back to game world interaction
               (handle-camera-drag-start game-state (= action GLFW/GLFW_PRESS))))))))
```

---

## Example Usage

### Simple UI Definition

```clojure
(ns silent-king.ui.main
  (:require [silent-king.widgets.core :as w]
            [silent-king.state :as state]))

(defn create-main-ui [game-state]
  ;; Create a panel in top-left corner
  (let [panel (w/panel
               {:bounds {:x 20 :y 20 :width 280 :height :auto}
                :layout {:padding {:all 16} :gap 12}
                :visual {:background-color 0xCC222222
                         :border-radius 12.0
                         :shadow :medium}})

        ;; Title label
        title (w/label
               {:text "Silent King Controls"
                :visual {:text-color :text
                         :font-size :large
                         :font-weight :bold}})

        ;; Zoom slider
        zoom-label (w/label {:text "Zoom"})
        zoom-slider (w/slider
                     {:id :zoom-slider
                      :value {:min 0.4 :max 10.0 :current 1.0 :step 0.1}
                      :interaction {:on-change #(set-camera-zoom! game-state %)}})

        ;; Hyperlane toggle
        hyperlane-toggle (w/toggle
                          {:id :hyperlane-toggle
                           :label "Show Hyperlanes"
                           :value (state/hyperlanes-enabled? game-state)
                           :interaction {:on-change #(state/toggle-hyperlanes! game-state)}})

        ;; Reset button
        reset-btn (w/button
                   {:text "Reset Camera"
                    :interaction {:on-click #(reset-camera! game-state)}})

        ;; Compose into VStack
        container (w/vstack
                   {:children [title
                              zoom-label
                              zoom-slider
                              hyperlane-toggle
                              reset-btn]})]

    ;; Add to game state
    (w/add-widget-tree! game-state panel container)))
```

---

### Complex Layout Example

```clojure
(defn create-dashboard [game-state]
  ;; Main container (full screen)
  (w/panel
   {:bounds {:x 0 :y 0 :width :fill :height :fill}
    :visual {:background-color 0x00000000}}  ; Transparent

   ;; Split into left sidebar and main area
   (w/hstack
    {:gap 0}

    ;; Left sidebar
    (w/panel
     {:bounds {:width 300 :height :fill}
      :visual {:background-color 0xCC1A1A1A}}

     (w/vstack
      {:padding {:all 20} :gap 16}

      ;; Star inspector
      (w/star-inspector {:selected-star-id (get-selected-star game-state)})

      ;; Hyperlane controls
      (w/hyperlane-control)))

    ;; Main area (game view)
    (w/panel
     {:bounds {:width :fill :height :fill}}

     ;; Top bar
     (w/panel
      {:bounds {:height 60}
       :layout {:anchor :top}
       :visual {:background-color 0xCC222222}}

      (w/hstack
       {:padding {:all 12} :gap 12 :align :center}

       (w/label {:text "Silent King" :font-size :xlarge :font-weight :bold})
       (w/spacer {:width :fill})
       (w/button {:text "Settings" :style :ghost})
       (w/button {:text "Help" :style :ghost})))

     ;; Minimap (bottom-right corner)
     (w/panel
      {:bounds {:width 250 :height 250}
       :layout {:anchor :bottom-right :margin {:all 20}}
       :visual {:background-color 0xCC222222 :border-radius 12.0}}

      (w/minimap {:zoom 0.05})))))
```

---

## Testing Strategy

### Unit Tests
- Component manipulation functions
- Layout calculations
- Hit testing
- Animation interpolation
- Theme color resolution

### Integration Tests
- Widget tree construction
- Event routing
- Render pass execution
- State synchronization

### Visual Tests
- Screenshot comparison
- Interaction recording/playback
- Performance benchmarks (FPS impact)

### REPL-Driven Development
```clojure
;; Live widget creation at REPL
(require '[silent-king.widgets.core :as w])

;; Add a test button
(def test-btn
  (w/add-widget! @game-state
    (w/button {:text "Test" :bounds {:x 400 :y 400}})))

;; Modify it live
(w/update-widget! @game-state test-btn
  #(assoc-in % [:components :visual :background-color] 0xFFFF0000))

;; Remove it
(w/remove-widget! @game-state test-btn)
```

---

## Performance Considerations

### Rendering Optimization
1. **Dirty rectangle tracking**: Only re-render changed regions
2. **Occlusion culling**: Skip widgets fully covered by others
3. **Paint object pooling**: Reuse Paint instances
4. **Render caching**: Cache complex widget renders to images
5. **LOD for widgets**: Simplify rendering at small sizes

### Layout Optimization
1. **Lazy layout**: Only compute when bounds change
2. **Layout memoization**: Cache layout results
3. **Incremental updates**: Only re-layout affected subtrees

### Memory Management
1. **Widget pooling**: Reuse widget entities
2. **Weak references**: For callbacks and event handlers
3. **Resource cleanup**: Properly close Skija objects (Paint, Font, Image)

---

## Future Enhancements

### Advanced Features
- **Drag and drop**: Generic drag-drop framework
- **Gestures**: Multi-touch gesture recognition
- **Transitions**: Scene transitions (fade, slide, zoom)
- **Modal dialogs**: Dialog system with overlay
- **Context menus**: Right-click context menus
- **Keyboard shortcuts**: Global hotkey system
- **Undo/redo**: Command pattern for widget state

### Developer Experience
- **Hot reload**: Live widget definition updates
- **Visual editor**: WYSIWYG widget composer
- **Component library**: Pre-built widget templates
- **Documentation generator**: Auto-generate widget docs from code
- **Storybook-style gallery**: Interactive widget showcase

### Accessibility
- **Keyboard navigation**: Full keyboard support
- **Screen reader**: ARIA-like hints
- **High contrast mode**: Theme variant
- **Focus indicators**: Clear visual focus
- **Text scaling**: Respect system font size preferences

---

## Conclusion

This widget toolkit design leverages the strengths of both Clojure and Skija to create a powerful, flexible UI system that integrates seamlessly with Silent King's existing ECS architecture. The functional, data-driven approach enables:

- **Declarative UI definitions**: Easy to read and modify
- **Compositional design**: Build complex UIs from simple parts
- **REPL-driven development**: Immediate feedback and iteration
- **Performance**: Efficient rendering with Skija's hardware acceleration
- **Extensibility**: Easy to add new widget types

The phased implementation plan provides a clear path from basic infrastructure to a production-ready toolkit, with each phase building on the previous work to deliver incremental value.
