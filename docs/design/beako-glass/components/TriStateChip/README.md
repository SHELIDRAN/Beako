# TriStateChip

A tag chip with three states — not selected, included (✓) and excluded (✕ with a strikethrough) — so state is never shown by colour alone. Click cycles none → included → excluded.

| State | Fill | Border | Mark / label |
| --- | --- | --- | --- |
| None | `tri-none-fill` | `rim` | label `text-muted` |
| Included | `tri-included-fill` | `tri-included-rim` | ✓ `text` |
| Excluded | `tri-excluded-fill` | `tri-excluded-rim` | ✕ `danger`, label struck through |

- `control-sm` high, `radius-pill`, `space-md` side padding, `label-medium`.
- Included tags are compulsory matches (all-of), as on Comick; excluded tags remove titles.
- **Accessibility** — role checkbox; the state description says "Not selected", "Included" or "Excluded".
- Used in the searchable tag picker: chosen tags stay visible, the rest shows the most-used first.
- **Motion** — the included or excluded fill **sweeps** in from the left (380 ms) and the ✓ or ✕ pops in on the droplet spring; presses dip to 96%. Reduce motion: instant.
