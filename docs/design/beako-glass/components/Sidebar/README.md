# Sidebar

The floating glass navigation down the left edge, in two layouts: the **pill** Beako ships today, and a proposed **panel** with search, sections, counts and nested destinations that collapses back to the pill's width.

## Pill — shipped

Mirrors `Sidebar.kt`: account rings on top, then Library · Latest updates · Browse · Reading history · More.

- **Shape** — GlassSurface, `radius-sidebar`, 14 px vertical padding; `sidebar-collapsed` (icons only, 50 px items) or `sidebar-expanded` (icon above label, 70 px items). Screens reserve `sidebar-rail`.
- **Accounts** — `rank-chip`-sized rings with a `rim-bright` border: the avatar or initial when signed in ("Comick: signed in as …"), a "+" when not ("Connect MangaDex"). Stacked when collapsed, two per row when expanded.
- **Items** — `icon-lg` Material Outlined icons, labels `label-small`; `text` when selected, `text-muted` otherwise; the Droplet (48 × `control-md`) slides behind the selected icon.
- **Expand** — Auto-collapse waits 150 ms after hover or focus arrives and 400 ms after it leaves, then animates width over 220 ms; labels fade in 160 ms / out 90 ms. Settings: Auto-collapse, Always expanded, Icons only.
- **Keyboard** — Ctrl+1…5 jump to items; items are tabs with a selected state.

## Panel — proposed

Designed from the menu references; not yet in the app. Use it once Beako has destinations that nest (Browse → Popular · Recently added · By tag) or lists that grow (accounts, sources).

| Part | Spec |
| --- | --- |
| Panel | `bk-sidepanel bk-glass`, `radius-widget` 28, `shadow-glass`, `sidebar-panel` 248 wide |
| Head | App logo (cropped to a circle), "Beako" in Bold 18, a collapse button |
| Search | 36 px field with a `Ctrl K` hint, `chip-fill` on a 1 px `rim` |
| Section | SemiBold 11 uppercase `text-muted`, tracking 0.6 px; may end in a + |
| Row | `sidebar-item` 40 tall, icon 18 `text-muted` + Medium 14 label; hover `hover` |
| Count | `ink-fill` pill, SemiBold 12; an `accent` dot on the icon means new since the last visit |
| Group | The open parent is an `ink-fill` pill ending in −; children hang off a 1 px `tree-line`, 36 tall, 21 px in |
| Current page | The Droplet, with a dot on the tree line |
| Foot | Signed-in user above a 1 px `rim`, "Settings" under the name |

**Collapsed**, the panel narrows to `sidebar-collapsed` (68 px, the pill's width) over 260 ms: labels, counts and search text hide, the open group becomes the Droplet, and hovering or focusing a group opens its children in a **flyout** (`surface`, `radius-field`, `shadow-float`, 168 wide, 8 px out; **blooms** out of the rail icon: scale 0.7 → 1 with a 6 px blur clearing, 300 ms spring, its rows rising in 40 ms apart).

## Which layout

| Setting | Pill (today) | Panel (proposed) |
| --- | --- | --- |
| Icons only | 68 px pill | 68 px rail, groups open as flyouts |
| Auto-collapse | 68 → 104 px on hover | 68 px rail → 248 px panel on hover (same 150 / 400 ms waits) |
| Always expanded | 104 px with labels | 248 px panel |

- One sidebar per window. While every destination is flat, the pill is enough; switch to the panel when you need nesting, counts or a long accounts list.
- Collapse keeps every destination reachable: icons carry `aria-label`s and tooltips, groups open on hover **and** focus. One level of nesting only.
- **Expanding** uncovers the labels left to right, row by row (22 ms apart, with a 3 px blur clearing), the way the panel reads as it widens. Give each row `--bk-i`, its order in the list.
- **Groups** fold open and shut (360 ms); the + at the end turns into − as it opens. Hovering a row **sweeps** the `hover` wash in from the left.
- Reduce motion: width, labels, groups and flyout change instantly. Reduce transparency: `glass-solid`.
