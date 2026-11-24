# DropletSegmented

A segmented switch whose selection is a glass droplet that slides between options — 7d · 1m · 3m, Hot · New.

- **Use for** two to five short, mutually exclusive views of the same content, typically beside a shelf title.
- **Anatomy** — a glass track (`rim` tint, `blur-control`, `radius-pill`, `space-xs` padding), equal options of `segment-width` × `control-sm`, one Droplet.
- **Labels** — `label-medium`; `text` when selected, `text-muted` otherwise. Keep them one or two words.
- **Accessibility** — a radio group with an accessible name (the consumer passes `label`), each option a radio with its selected state.
- **The consumer provides** `options`, `selectedIndex`, `onSelect`, `label`, optionally `optionWidth` (default 64).
