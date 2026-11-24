# IconToggleGroup

An icon-only single choice on a small raised `surface` card: the chosen icon is full `text`, the others sit at `text-muted` and 70% opacity, and when you choose, the dark ink **travels** to the new icon line by line — the leading line first, the others 45 ms behind — on the droplet spring, while the card dips 1 px under your click.

Proposed — from the reference animation, not yet in the Kotlin app.

- **Use** for 2–4 visual options whose icons are self-explanatory: reader alignment, page fit, reading direction. Use `DropletSegmented` when the options are words.
- **Accessibility** `role="radiogroup"` with an `aria-label`; each option is a `radio` with its own `aria-label` and a tooltip. Selection is shown by ink and opacity; inactive icons keep at least 3:1 against `surface` (`text-muted` at 70%). Thin line icons need `stroke-width` 2.4 at 28 px.
- **Size** 56 × 48 per option, 8 px apart, 10 px card padding, `radius-panel`, `shadow-glass`.

## Ink

The selection is drawn by `.bk-icon-toggle__ink` (three 2.2 px bars) laid over the icons. The consumer moves each bar onto the matching line of the chosen icon (left, width, top) and sets `data-ink` on the group, which greys every icon underneath. Without the script, the chosen icon simply turns `text`. Reduce motion: the ink jumps.
