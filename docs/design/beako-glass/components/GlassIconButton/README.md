# GlassIconButton

An icon-only circular glass button with an accessible name.

- `control-md` circle, `rim`-tinted glass, an `icon-md` Material Outlined icon in `text`.
- **Always** pass `description`: it is the screen-reader name and should also be the tooltip.
- Use only for actions whose icon is unambiguous (search, refresh, close, more); otherwise use GlassButton with a label.
- **Toggle** — give it `aria-pressed` (Follow series ⇄ Unfollow series): an `ink-fill` circle **rises** from below and the icon turns `ink-on-fill`; turning off sends the ink up and out (set `data-was-on`). The accessible name changes with the state.
- **Arrow** — `bk-icon-button--swing` makes a forward arrow (Next chapter) **swing** in from 90° on hover or focus.
- Every icon button dips to 96% while pressed.
