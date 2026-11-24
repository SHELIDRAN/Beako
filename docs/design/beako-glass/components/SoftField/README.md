# SoftField

A soft (neumorphic) search field: it is the same colour as the ground under it and is shaped only by `shadow-soft-raised` (light top-left, dark bottom-right), with a matching round or square search button beside it.

Proposed — from the soft-UI references, not yet in the Kotlin app.

## Variants

| Class | Look | Use |
| --- | --- | --- |
| `bk-soft-field` | raised pill | Main search on a plain ground (Porcelain especially) |
| `--square` | raised, `radius-panel` 16 | Next to square buttons, inside panels |
| `--inset` | pressed in, `shadow-soft-inset` | Filters inside a sheet; reads as "type here" |
| `--outline` | `surface` fill, 1 px `rim` | On busy or mixed backgrounds where soft shadows vanish |

`bk-soft-btn` (+ `--square`) is the paired search button; it presses in (`shadow-soft-inset`) while held. `bk-kbd` shows a shortcut.

## Rules

- **Only on a flat ground.** Soft shadows need the field's fill to equal what is behind it (`ground`). Over covers or glass use `SearchField` instead.
- **Focus** adds a 2 px `accent-rim` ring; the caret is `accent`.
- The placeholder is the label's text and is also the `aria-label`. Height `control-cta` (48).
- On dark palettes the effect is deliberately faint; Porcelain is where it reads best.
