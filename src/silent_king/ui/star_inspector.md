# silent-king.ui.star-inspector (legacy)

This namespace previously implemented the **star selection and inspector panel** on top of the widget ECS.

## Responsibilities (old system)

- Managed the `[:ui :star-inspector]` sub-state (visibility, positions, animation targets) using `clojure.spec`.
- Constructed a side-panel widget tree with:
  - Title and subtitle labels for the selected star.
  - A `:star-preview` widget showing a mini rendering of the star.
  - Labels for position, size, density, and rotation.
  - A scrollable list of hyperlane connections to neighboring stars.
  - A `"Zoom to Star"` button that triggered a camera pan animation.
- Implemented selection logic:
  - Ray-picked a star from a world-space click.
  - Built a rich selection map (star id, position, size, density, connected hyperlanes).
  - Updated `[:selection :star-id]` and `[:selection :details]` in `game-state`.
- Animated panel slide-in/slide-out by gradually adjusting the panel’s `:bounds :x` and requesting layout.

## Notes for Reactified rewrite

For the Reactified UI, we will want to re-express this behavior as:

- A **pure `star-inspector` component** that takes:
  - `selection` (or `nil`), `hyperlane-connections`, and `camera/viewport` props.
  - Emits events like `:ui/clear-selection` and `:ui/zoom-to-selected-star`.
- A central **selection model** (likely a map under `:selection` in `game-state`) that the renderer and components agree on.
- An animation model for panel entrance/exit that lives either in:
  - A Reactified state machine, or
  - A small, generic animation helper, instead of mutating ECS widget bounds directly.

