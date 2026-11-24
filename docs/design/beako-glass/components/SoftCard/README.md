# SoftCard

The soft elevations in one place: **lift** (`surface` + `shadow-card`, rises on hover), **flat** (1 px `rim`), **raised** (`shadow-soft-raised`, same fill as the ground), **floating** (`surface` + `shadow-float`) and **pressed** (`shadow-soft-inset`).

Proposed — from the styleguide's Elevation and Cards references, not yet in the Kotlin app.

- **Raised** is for resting containers on a plain ground: widget cards, grouped settings. **Floating** is for things that appear over others: popovers, flyouts. **Pressed** is a selected or held state, never a resting card. **Flat** is the fallback when the ground behind is busy.
- Soft shadows work only when the card's fill equals its ground; over covers or glass use `GlassSurface`.
- **Lift** is for any card you can open. At rest it has a bright 1 px top edge, a tight contact shadow and a soft drop falling down-right; on hover or keyboard focus it rises 6 px on the droplet spring while the shadow deepens to `shadow-card-hover` (450 ms ease-out), and sinks back to 2 px while pressed. Reduce motion: the shadow still changes, the card does not move.
- Set `data-elevation` on `.bk-softcard`; radius `radius-panel` 16.
