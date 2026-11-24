# Beako Glass

Beako Glass is the look of **Beako**, a native Windows manga reader ported from Neko. It is **neutral liquid glass**: a near-black ground (or, in the Porcelain palette, a pale grey one), white and grey text, and panels that blur what is behind them under a thin bright rim. Covers bring the colour; the interface stays out of their way, apart from one orange **accent** kept for the action that matters most.

Extracted from the Compose Multiplatform desktop app (`desktop/…/theme` and `components`, commit `49b0162`) and the owner's screen plan in `docs/UI_BLUEPRINT.md`. Components here are CSS renditions of the Compose code, for reference and mock-ups; the Kotlin code stays the source of truth.

## Principles

1. **Content is the colour; the accent is a signal.** The UI is white, grey and glass. Hue appears only in the ground glow of the chosen palette, the gold/silver/bronze ranks, `danger` / `positive` states, and the orange `accent` — on one call to action per screen, the active tab, focus on soft fields, unread dots and alerts that need action.
2. **One selection shape.** Every selection — segmented switches, the sidebar's current item, the current page in the Sidebar panel — is the same clear glass **droplet** that slides with a soft, slightly overshooting spring. On Porcelain the droplet is a white pill lifted by its shadow. The only stronger mark is the `ink-fill` pill of an *open group* in the Sidebar panel.
3. **Glass is a layer, not a decoration.** Use it for things that float over content: the sidebar, card captions, controls, banners. Lists and pages sit straight on the ground.
4. **Nothing by colour alone.** Included tags get ✓, excluded get ✕ and a strikethrough, ratings carry ★, errors carry words.
5. **Comfort settings change everything at once.** Reduce motion makes every transition instant; Reduce transparency turns all glass into `glass-solid`.

## Content fundamentals

- **Sentence case** everywhere: "Latest updates", "Reading history", "Try Comick again".
- **Plain and specific.** Say what happened and what Beako did about it: "Showing MangaDex results — Comick is unavailable", "…found no matches".
- **Short empty states.** A title that states the fact, an optional one-line detail, one action: "No comics match these filters" + **Clear all**; "Nothing here yet."
- **Honest ends.** No invented totals: "That's everything Comick returned — refine the search for more".
- **Numbers compact.** 15 234 → "15.2k", 1.2M; scores to one decimal with a star: "★ 8.5". Metadata joins with a spaced bullet: "Manhwa  •  Ongoing  •  182 chapters".
- **Name third-party code as such.** Extensions are "third-party code"; Comick is never offered as a reading source.

## Visual foundations

### Colour

Six palettes (themes). The five dark ones — Neutral, Midnight, Warm, Forest, OLED black — change only the ground layers: `ground`, `ground-glow`, `surface`, `surface-raised`, `glass-solid`. **Porcelain** is the one light palette: besides the grounds it flips `text` (#17171a), `text-muted`, the glass tint and rim, the droplet (solid white), chip and tri-state fills, and darkens `gold`, `positive` and `danger` so they still read on white. Everything else is shared.

| Layer | Token | Use |
| --- | --- | --- |
| Window | `ground` with a radial `ground-glow` from the top-left (centre 120,0; radius 1400 px) | Every screen |
| Solid panels | `surface`, `surface-raised` | Dialogs, menus, cover placeholders |
| Glass | blur `blur-glass` + `glass-tint` + rim `rim-bright`→`rim` | Floating chrome |
| Text | `text`, `text-muted` | Everything readable |

- `text` on any ground is about 18:1; `text-muted` is at least 6.8:1 on every palette's surfaces.
- Text on glass uses `text` with `text-shadow` (blur 6) so it keeps 4.5:1 over bright covers.
- The primary button inverts: `text` fill, `ground` ink.
- On Porcelain `text` is 15:1 on the ground and `text-muted` (#55565e) at least 6.1:1.

### Accent

| Token | Value | Use |
| --- | --- | --- |
| `accent` | #df5830 | The brand orange: icons, the tab underline, unread dots, the soft-field caret. 3.2:1 on Porcelain, 5.2:1 on Neutral — never body text. |
| `accent-fill` | #c8461f | Fill of the accent button, ArrowButton and ToggleIconButton, so white `accent-ink` holds 4.8:1. |
| `accent-soft`, `accent-rim` | orange washes | The accent alert's background and border; focus ring on soft fields. |
| `shadow-accent-glow` | 0 6 20 orange 40% | Hover glow on accent buttons; the accent alert. |

At most **one** accent call to action per screen; a screen uses the inverted Primary *or* the Accent for its main action, not both side by side. Text on the ground is never orange.

### Soft elevation

Next to glass, Beako has a soft (neumorphic) layer for plain grounds, strongest on Porcelain:

```
raised: shadow-soft-raised  (soft-light up-left, soft-dark down-right; fill = ground)
inset:  shadow-soft-inset   (pressed, selected, a field to type into)
float:  surface + shadow-float  (popovers, flyouts)
flat:   1px rim               (fallback on busy grounds)
```

Soft shadows only work when the element's fill equals what is behind it. Over covers and imagery, use glass.

### Glass recipe

```
blur:   blur-glass (24px) × Glass strength (0.5–1.5)
tint:   glass-tint (large glass) or rim (controls) × Glass strength
rim:    1px, vertical gradient rim-bright → rim
shadow: shadow-glass on floating surfaces only
reduce transparency → solid glass-solid fill, same rim
```

### Droplet

A mostly clear pill: vertical gradient `droplet-top` → `droplet-bottom`, 1 px `droplet-top` rim, `shadow-droplet`, no bright glint. Height `control-md` in the sidebar, `control-sm` in segmented switches.

### Type

**Inter** (Regular, Medium, SemiBold, Bold; SIL OFL, see `licenses/Inter-OFL.txt`) in a Material 3 scale. Headings tighten slightly (−0.6 to −0.2 px). Body is 14/20; labels are SemiBold.

### Shape and space

Controls are full pills (`radius-pill`); containers round 12–18 px (`radius-field` … `radius-card`); popovers 20 px (`radius-menu`); widget cards and the Sidebar panel 28 px (`radius-widget`); the sidebar 30 px. Large calls to action are `control-cta` 48 px tall. Spacing is a 4-based scale `space-xs` 4 → `space-xxl` 32; screens inset 32 px and shelves scroll edge to edge.

### Motion

| What | Spec |
| --- | --- |
| Screen change | Fade-through: out 60 ms, in 150 ms after 60 ms with a scale from 0.985 |
| Droplet slide, card lift | Spring, damping 0.62, stiffness 400 (Compose MediumLow) — about 550 ms with a little overshoot |
| Sidebar expand / collapse | Waits 150 ms on hover in, 400 ms on hover out; width 68→104 px over 220 ms |
| Sidebar labels | Fade in 160 ms, out 90 ms |
| Reader chrome | Fade 180 ms |
| Card hover | Lifts 6 px and scales 1.5% |
| ArrowButton | Knob fill sweeps across the pill, 420 ms `cubic-bezier(.65,0,.35,1)`; arrow nudges 3 px |
| ToggleIconButton | Ink circle rises from below, 380 ms, and leaves through the top when turned off; + rotates into − on the droplet spring |
| MorphSelect | The pill blooms into its popover from its own size (0.42 × 0.3 → 1), blurred 8 px → sharp; its rows rise in 60 ms apart |
| Tabs underline | Leading edge moves first, trailing edge 90 ms later, so it stretches then settles (droplet spring) |
| Segmented indicator | Slides to the label, droplet spring |
| Sidebar panel | Width 248 ↔ 68 over 260 ms; labels are uncovered left to right, 22 ms apart down the list; the flyout blooms out of the rail icon; groups fold open with + turning into − |
| Accent button hover | Lifts 1 px and glows, 200 ms |

**Reduce motion** snaps all of them — the OS setting, or Beako's own with `data-reduce-motion` on any ancestor.

#### Motion vocabulary

Six moves, taken from the reference motion studies. Reuse them instead of inventing new ones; each has one job.

| Move | What it looks like | Used by | Timing |
| --- | --- | --- | --- |
| **Press** | Whatever you click dips to 96% | Every button, chip, segment, tab and toggle | 90 ms down, 220 ms back |
| **Sweep** | A fill grows from one edge across the shape | ArrowButton fill, TriStateChip include/exclude, ResultRow and Sidebar row hover | 360–420 ms `cubic-bezier(.65,0,.35,1)` |
| **Rise** | An ink circle rises from below and leaves through the top | ToggleIconButton, GlassIconButton toggles (Follow) | 380 ms, same curve |
| **Swing** | An arrow drops in at 90° and swings to rest | GlassIconButton arrows (Next chapter) on hover | 520 ms droplet spring |
| **Travel** | Ink moves to the new choice part by part, leading edge first | IconToggleGroup lines (45 ms apart), Tabs underline edges (90 ms apart) | 420–460 ms droplet spring |
| **Bloom** | A popover grows out of what opened it, blurred to sharp, its rows following | MorphSelect, Sidebar flyout, Sidebar labels on expand | 300–420 ms spring; rows 20–60 ms apart |

Rules: one move per interaction; selection and state use Travel, Sweep or Rise, never colour alone; nothing loops; nothing runs on scroll. The CSS curves are `--bk-ease-spring`, `--bk-ease-out` and `--bk-ease-inout` in `bundle.css`.

## Iconography

Material Icons **Outlined** (Compose `Icons.Outlined.*`): CollectionsBookmark, Update, Explore, History, MoreHoriz in the sidebar; Search, Close, ErrorOutline, Add in controls. Sizes `icon-sm` 16, `icon-md` 18, `icon-lg` 22. Tint `text` when active, `text-muted` otherwise. Icon-only buttons always carry an accessible name and tooltip. Previews here draw the same Material paths inline.

The only bitmap mark is the app logo (`assets/Logo/logo.webp`, 512 px): a black-and-white round badge on a crimson square. Where it sits small in the interface (the Sidebar panel header), crop it to a circle so only the badge shows.

## Accessibility settings

| Setting | Range | Effect |
| --- | --- | --- |
| Palette | Neutral, Midnight, Warm, Forest, OLED black, Porcelain (light) | Swaps the ground layers; Porcelain also flips text and glass |
| Glass strength | 0.5–1.5 | Scales blur and tint |
| Interface size | 0.8–1.5 | Scales on top of Windows display scaling |
| Reduce motion | on/off | Every transition instant |
| Reduce transparency | on/off | Glass → `glass-solid` |
| Sidebar | Auto-collapse, Always expanded, Icons only | |

Every control is keyboard-reachable with a visible `focus` ring.

## Not synced

- **Proposed, not yet in the app:** the Porcelain palette, the accent tokens, soft shadows, and the components ArrowButton, ToggleIconButton, SoftField, IconToggleGroup, MorphSelect (with WidgetCard), Tabs, Alert, SoftCard, the Sidebar's panel layout and GlassButton's Accent variant. They were designed from reference images and motion studies, so a re-sync from the Kotlin code will not find them; keep them until the app implements them.

- Mica / Acrylic window backdrop (ComposeWindowStyler) and the Haze blur itself have no CSS equivalent beyond `backdrop-filter`; the blueprint also asks for saturation, which the code does not set yet.
- Shadows are CSS approximations of Compose elevations.
- Screen-level compositions (Library grid, Browse shelves, Search filters panel, title page, reader, Source Picker) are not cards here; their building blocks are.
- Components were hand-written as CSS from the Kotlin sources (read-only route; no build was run).

---

## Consuming this system (generated — do not edit)

Every path named below is under `project/` in this design system: read `project/api/tokens.md`, not `api/tokens.md`.

25 components are documented without a runnable `components/bundle.js`: read each component’s card, and its README where it has one (`components/<Comp>/README.md`), and build to those guidelines. Tokens: the values are on `api/tokens.md`; a Slides deck or Design canvas also takes `tokens.json` by file path. On a Design canvas, also copy `tokens.css` to `project/ds/<folder>/tokens.css` in the call that installs this system, and link it in each artboard that uses this system, right after the `support.js` line and before the system’s other stylesheets: `<link rel="stylesheet" href="ds/<folder>/tokens.css">` (with one `../` in front for each folder the artboard is in).

Fonts: `tokens.css` loads each font from its file, by path.

- **Design canvas:** copy `fonts/Inter-Regular.otf`, `fonts/Inter-Medium.otf`, `fonts/Inter-SemiBold.otf` and `fonts/Inter-Bold.otf` to the same paths under the canvas’s `project/ds/<folder>/` (`<folder>` is the folder you install this system under; `fonts/Inter-Regular.otf` lands on `project/ds/<folder>/fonts/Inter-Regular.otf`), in the same call that installs this system; the copy of `tokens.css` that each artboard links loads them from there. If the install’s reply leaves a font out, bring that font in as for any other page.
- **Slides deck:** for each family you use in the deck (`faces` holds at most the number of typefaces Slides’ `deck-files.md` gives), install its file with the system and name the path it lands on as that family’s `src` in `project/deck.json` `faces`, under the key in the Slides column. A deck takes a font as a file only under the rule in Slides’ `fonts.md` (no space or leading underscore in its path, for two): any other font, or one kept by id, is uploaded as `fonts.md` says.
- **Any other page:** one file per family is enough (below: the upright face nearest regular weight; bold and italic synthesize). Fetch it as the fetch column says (Artifact tool `read`, that id or path as `path`), upload it as an asset (`publish`, `file_path`, `asset:true`) and declare it with an `@font-face { font-family: "<family>"; src: url(<uploaded>) }` rule.

| family | file | fetch | CSS | Slides `faces` key |
| --- | --- | --- | --- | --- |
| Inter | `fonts/Inter-Regular.otf` (weight 400) | `read` the file with `project/` in front | `var(--font-sans)` | `inter` |

**Read, per thing:** a component’s props, parts and examples: `api/components/<Comp>.md`; token values: `api/tokens.md`; stored assets and their paths: `api/assets/<Group>.md`. After this README, fetch the cards and fonts you need in ONE message as parallel calls — none depends on another.

**Two rules.** Before you use a thing — a component, a token group, an icon, an asset — read its card from the index below; a value you did not read from a card is a guess. `tokens.json`, `manifest.json` and `design-system.json` are sources for tools: hand them over. `components/<Comp>/README.md` is the long-form second read a card links to; `SKILL.md` and `artifact-type/` beside them are authoring guidance, not needed to consume the system.

## Index (generated — do not edit)

**Tokens**

- `api/tokens.md` — Every token: surface, text, fill, palette, type, spacing, radius, shadow, blur, size. (23.2k)

**Icons and assets**

- `api/assets/Logo.md` — 1 file, by asset id. (0.7k)

**Components** (`api/components/<Comp>.md`, 25)

- **Feedback**: `Alert` — A compact raised notice that sits inside content — an icon, one line of text, an optional action — with an accent version that glows to ask for attention · `Banner` — A notice across the top of a screen, with optional actions on the right · `StateMessage` — A centred message for empty and error states, with an optional action below
- **Actions**: `ArrowButton` — A large pill call to action with a round arrow knob on the right; on hover or focus the knob's accent-fill sweeps across the whole pill and the label turns acc… · `DropletSegmented` — A segmented switch whose selection is a glass droplet that slides between options — 7d · 1m · 3m, Hot · New · `GlassButton` — A pill button on glass; primary makes it the solid, high-emphasis action · `GlassIconButton` — An icon-only circular glass button with an accessible name · `IconToggleGroup` — An icon-only single choice on a small raised surface card: the chosen icon is full text, the others sit at text-muted and 70% opacity, and the change cross-fad… · `ToggleIconButton` — A round on/off button: off is an accent-fill circle with a plus; on, an ink-fill circle wipes down from the top and the plus turns into a minus
- **Content**: `Chip` — A small label pill on card captions — the comic type ("Manhwa") or a score ("★ 8.5") · `RankChip` — The position badge on ranked shelves: gold, silver and bronze for the top three, dark glass below · `ResultRow` — One search result as a glass row: rank, cover, title, details and description · `TitleCard` — The glass caption card: a 2:3 cover with a frosted caption, an optional rank chip or unread badge, and a small lift on hover
- **Surfaces**: `Droplet` — The clear glass pill that marks a selection everywhere in Beako: mostly transparent, a faint rim, a soft shadow and no bright glint · `GlassSurface` — The floating liquid-glass panel: what is behind is blurred under a light dark tint, with a 1 px rim that fades from rim-bright to rim, and a shadow-glass drop… · `Skeleton` — A flat grey block (chip-rim, 8% white) shown where content is still loading · `SoftCard` — The four soft elevations in one place: flat (1 px rim), raised (shadow-soft-raised, same fill as the ground), floating (surface + shadow-float) and pressed (sh…
- **Inputs**: `GlassTextField` — A labelled single-line text box on glass; secret hides what is typed · `MorphSelect` — A small pill trigger ("This week ⌄") that grows out of itself into a popover — an action on top, a two-way segmented choice below — and shrinks back into the p… · `SearchField` — The pill search box at the top of Browse and Search · `SoftField` — A soft (neumorphic) search field: it is the same colour as the ground under it and is shaped only by shadow-soft-raised (light top-left, dark bottom-right), wi… · `TriStateChip` — A tag chip with three states — not selected, included (✓) and excluded (✕ with a strikethrough) — so state is never shown by colour alone
- **Navigation**: `NavPanel` — The full glass menu: logo and collapse control, a search with its shortcut, sections ("Main", "Accounts") with counts and add buttons, an expandable group whos… · `Sidebar` — The floating glass navigation pill down the left edge: account rings on top, then Library · Latest updates · Browse · Reading history · More · `Tabs` — Text tabs on a 1 px rim hairline; the current tab is text with a 2 px accent underline that slides (and stretches to the label's width) on the droplet spring
