# Beako UI blueprint

The owner's screen plan, arranged for building. Sources:

- Owner's sketch: [`docs/design/ui-sketch-2026-11-13.pdf`](design/ui-sketch-2026-11-13.pdf) (sidebar, Library, Latest updates, Browse).
- Owner's reference screenshots of comick.dev's search page (filters, sort menu, results list), Nov 13 2026. **Layout and content reference only**: rebuild in Beako's own glass style, don't copy Comick's look.
- Chosen shelf card and day switch: [mockup](https://claude.ai/artifact/48C2XRR1AaMUacC1iFqHuP), spec in `docs/PLAN.md` → "2. Discovery".

Every screen follows the visual rules below. Items marked **[gap]** or **[fix]** are decisions made while reviewing the sketch; the full list is in "Errors and gaps" at the end.

## Visual rules

- **Neutral liquid glass**: near-black ground, white and grey text, no colour tint. Glass = heavy blur + saturation with only a light dark tint and a 1 px light rim. Text on glass gets a soft shadow and must still pass 4.5:1 contrast.
- **Selection = droplet pill**: every segmented control and the sidebar's selected item use the same clear glass droplet (faint rim, soft shadow, no bright glint) that slides with a springy overshoot.
- **Cards** = glass caption cards (cover, frosted caption, glass chips).
- Type: Inter. Motion: fade-through between screens (~150–200 ms), droplet slide ~550 ms. **Reduce motion** makes all of it instant; **reduce transparency** swaps glass for solid panels.
- Every control is keyboard-reachable with a visible focus ring; icon-only buttons have tooltips and accessible names; nothing is shown by colour alone.

## 1. App shell: the sidebar

A floating glass pill down the left edge.

- **Top: account avatars** for **Comick** and **MangaDex**. Signed in: the account's avatar (Comick from `GET /integrations/v1/me`). Signed out: a "+" ring; click opens Connect. Click a signed-in avatar for a small menu (Sync now, Disconnect). **[gap]** AniList is also a tracker but isn't on the sketch: it lives in Settings → Tracking, with an optional third avatar.
- **Items, in order**: Library · Latest updates · Browse · Reading history · More (settings). Icon above label, as drawn.
- **Selected item**: droplet pill behind its icon.
- **Collapse behaviour [fix]**: the sketch says the bar "collapses and hides when the mouse isn't hovering". Fully hiding it would leave no visible way to navigate and would trap keyboard users, so:
  - Default: collapses to a **narrow icon-only rail** when the pointer leaves (after ~400 ms) and expands with labels on hover (after ~150 ms) or when it gets keyboard focus.
  - Fully hidden only inside the reader and in fullscreen.
  - Setting: Sidebar = Auto-collapse (default) / Always expanded / Icons only.
- Shortcuts: Ctrl+1 Library, Ctrl+2 Latest updates, Ctrl+3 Browse, Ctrl+4 History, Ctrl+, Settings, Ctrl+F search.
- The app opens on the last screen used (Library on first run).

## 2. Library (sidebar item 1)

- **Search library** field at the top. Matches title, every alternative title (English, romaji, native; from Comick's `titles` list and MangaDex alt titles), tags and genres. If nothing in the library matches, the list shows **"Search sources for '…'"**, which opens Search (section 5) with the same text: Comick first, MangaDex fallback.
- **Categories** dropdown: filter by one or more user categories. Categories are optional and set in the **Add to library** dialog (pick none, one or several; create one inline).
- **Group by** dropdown: None · Reading status (Not started / Reading / Completed) · Unread (has unread chapters) · Downloaded · Publication status (ongoing, completed…) · Type (manga, manhwa, manhua) · Category. Each group is a collapsible section with a count.
- **Sort [gap]** (not on the sketch, needed): Last read · Latest chapter · Title · Date added · Unread count; ascending/descending.
- **Grid** of glass caption cards; instead of a rank chip, an **unread count** chip and a **downloaded** badge. Card size slider in the toolbar.
- Data: local library (Room, Phase 2). "Downloaded" grouping needs the download manager (Phase 2).

## 3. Latest updates (sidebar item 2)

- **Shelf: "Latest updated comics in your library"**, newest first, "View all ›" opens the full list grouped by day (Today, Yesterday, This week…). Each item: cover, title, chapter number, the source it came from, time ago, unread dot. Data: the library update checker (Phase 2) reading chapter lists from installed extensions and MangaDex; Comick's `last_released_at` is a hint only.
- **Shelf: "Updated collections you follow" [gap]**: Comick's API has **no endpoints for custom lists or followed collections**, and the library permission explicitly excludes custom-list contents. Until Comick adds them (ask on their feedback tracker), this shelf shows **"New in your Comick library"**: entries from `GET /me/library` whose `last_released_at` is newer than your progress.

## 4. Browse (sidebar item 3), default view

The view before search is opened.

- **Search field** at the top **[gap: not drawn]**; focusing it or typing opens Search (section 5).
- **Most Followed New Comics ›** with the droplet pill **7d · 1m · 3m** (`GET /top?type=newfollow&day=7|30|90`). "›" / View all opens the full ranked list (where the longer windows 6m, 9m, 1y, 2y can also be offered).
- **Most Recent Popular ›** (`GET /top`; which feed key this is must be checked on a real response).
- **Updates** section with a droplet switch **Hot · New** (`GET /chapter/?order=hot|new`) and a **Filters** button: type (manga, manhwa, manhua, others), demographic group, content rating, language (defaults to the app language). Shown as result rows (section 5 style) with infinite scroll; the API stops returning rows after page 100. Clicking a row opens the title page, never a Comick reader.
- Shelves use glass caption cards; rank chips on the ranked shelves.

## 5. Search (opened from the search field)

Layout follows the owner's comick.dev screenshots, restyled.

### Header
- Title **Search** with a subtitle "Choose your options" while no filter is set.
- **Tabs: Comics · Creators [fix]**. Comick also has Anime, Groups and Users tabs; Beako is manga-only, and groups/users aren't part of the plan, so they're left out. Creators uses `type=author`.
- **Sort**: droplet segmented **Latest · Popular** plus a **⌄ menu**: Bayesian rating ("More votes, more trust"), Average rating ("Simple average of ratings"), Last updated. Mapping to `sort`: Latest → `created_at`, Popular → `follow` (check against comick.dev's own behaviour), Bayesian rating → `rating`, Average rating → `average_rating`, Last updated → `uploaded`. A keyword search ranks by relevance first.

### Filter panel
A collapsible glass panel, three columns on wide windows, one column when narrow. Changes apply live after a ~400 ms pause.

| Column 1 | Column 2 | Column 3 |
| --- | --- | --- |
| **Genres** — searchable multi-select, tri-state (include, exclude, clear); `genres` ≤ 10, `excludes` ≤ 40 | **Tags** — "Select twice to exclude": searchable tri-state tag picker with counts; included tags are compulsory; `tags` ≤ 10, `excluded-tags` ≤ 30 | **Demographic** — Shounen, Shoujo, Seinen, Josei, None (`demographic` 1–5) |
| **Added to Comick** (sketch: "Created at") — Any, last 7 / 30 / 90 / 180 / 365 days (`time`) | **Type** — Manga, Manhwa, Manhua, Others (`country` jp / kr / cn; Others: see gaps) | **Min chapters ≥**, **Max chapters ≤**, **Min score ≥** (`minimum`, `maximum`, `minimum_rating`) |
| **Status** — All, Ongoing, Completed, Cancelled, Hiatus (`status` 1–4) and **Translation completed** toggle (`completed=true`) **[gap]** | **Released** — From year → To year (`from`, `to`) | **Hide comics in my library** (client-side, see gaps) · **Include comics without chapters** (`showall=true`) |

Added below the grid:
- **Content rating [gap]**: Safe (default), Suggestive; Erotica and Pornographic appear only when Settings → Show NSFW is on (`content_rating`, `excluded_content_rating`).
- An **active filters** row of chips with ✕ on each, **Clear all**, and **Save as preset** (presets also appear on Browse).
- Filters are remembered between launches.

### Results list
One row per title, from the screenshot, restyled as glass rows:

- **Rank** (position in the current sort) in a large muted numeral on the left.
- **Cover** (2:3) with the **country flag** as a small badge on its corner.
- **Title** (one line, ellipsis) · meta line "**N chapters** • **Uploaded 4 hours ago** • translation-status icon" · **alternative titles** (one line) · **description** (two lines).
- **Right column**: year · demographic symbol **with a text label or tooltip** and follow count · ★ rating with the Bayesian score as a smaller secondary number.
- Hover: glass highlight. Click: title page. Right-click: Add to library, Track on Comick, Copy link.
- **Infinite scroll**, 30 per page (the API allows up to 50 per page through page 50). No total count exists, so no "N results" header; at the end show "That's everything Comick returned — refine the search for more".
- States: loading = skeleton rows; nothing found for a keyword = MangaDex fallback banner (Comick rule in `CLAUDE.md`); filters with no match = "No comics match these filters" + Clear all; Comick unreachable = banner with Retry.
- **Before building the rows**: capture one real search response on a Windows PC and save it as a test fixture. The Rust prototype reads `title`, `md_titles`, `desc`, `country`, `year`, `status`, `content_rating`, `bayesian_rating`, `rating`, `md_covers`, `external_ids`; chapter count, upload time, follow count and translation status are **not yet confirmed** in search results.

## 6. Reading history (sidebar item 4)

Not detailed in the sketch. Proposed: a list grouped by day, each row with cover, title, last chapter read, source, time; Resume button; remove one entry or clear all; respects Incognito mode. Phase 2.

## 7. More (sidebar item 5)

Stat tiles, Downloaded only, Incognito, Download queue, Categories, Statistics, Settings, About; settings tree as in `docs/PLAN.md` → "5. Smoothness, settings and customization".

## Errors and gaps found in the plan

| # | Where | Problem | Decision |
| --- | --- | --- | --- |
| 1 | Sidebar | "Collapses and hides" leaves no visible navigation and breaks keyboard use | Collapse to an icon rail; hide fully only in the reader/fullscreen; user setting |
| 2 | Latest updates | "Updated collections followed by user": Comick's API has no custom-list or followed-collection endpoints; library scope excludes custom lists | Show "New in your Comick library" instead; ask Comick for list endpoints |
| 3 | Search filters | "Hide comics in my list": the API's `exclude-mylist` is documented as non-functional | Filter results client-side against the Beako library and the synced Comick library |
| 4 | Search filters | Type "Others" has no `country` code (the API takes jp, kr, cn…) | Omit the country filter and drop jp/kr/cn results client-side when only "Others" is ticked |
| 5 | Search tabs | Anime, Groups, Users tabs: Beako is manga-only | Comics and Creators only |
| 6 | Search filters | No content-rating control; adult titles would show by default | Content rating filter, Safe by default, adult options behind the NSFW setting |
| 7 | Search filters | Status mixes up series status and translation status | Separate Status (series) and Translation completed toggle |
| 8 | Browse | No search field drawn | Search field at the top of Browse |
| 9 | Library | No sort control | Sort menu |
| 10 | Library | "Downloaded" grouping needs downloads | Available once the download manager lands (Phase 2) |
| 11 | Results | Country flags: Windows doesn't render flag emoji (shows letters) | Bundle SVG flags (e.g. the MIT-licensed `flag-icons` set) |
| 12 | Results | ♂/♀ demographic shown by colour and symbol only | Add a text label or tooltip |
| 13 | Results | Search returns no total count and keyword search isn't exhaustive | No result count; "end of results" message |
| 14 | Results | Chapter count, upload time, follow count and translation status not confirmed in search responses | **Resolved**: real search responses carry `last_chapter`, `uploaded_at`, `user_follow_count` and `translation_completed` |
| 15 | Browse | "Most Recent Popular" feed key in `/top` unconfirmed; tag all-of vs any-of unconfirmed | **Resolved**: it is `/top?type=trending&day=N`; tags are all-of |
| 16 | Live filters | Every filter change is a request; rapid changes can hit the 200/min limit | 400 ms debounce, cancel stale requests, cache by query |
| 17 | Sidebar | AniList tracker not on the sketch | Settings → Tracking; optional third avatar |

## Dependencies

Verify each library's desktop support and pick versions at implementation time.

| Need | Library |
| --- | --- |
| In-app glass blur | Haze (`dev.chrisbanes.haze`) |
| Mica / Acrylic window backdrop | ComposeWindowStyler |
| Fade-through and shared-axis motion | `io.github.fornewid:material-motion-compose-core` (desktop build exists) |
| Navigation and screen state | Navigation 3 (Compose Multiplatform build), lifecycle-viewmodel |
| Covers and images | Coil 3 with its OkHttp network module |
| HTTP and JSON | OkHttp, Retrofit, kotlinx.serialization (reused from Neko) |
| "4 hours ago", dates | kotlinx-datetime |
| Infinite lists | AndroidX Paging (multiplatform `paging-common`) |
| Library, categories, history, saved presets | Room (KMP) with the bundled SQLite driver |
| Tokens in Windows Credential Manager | a JVM keyring library (e.g. `java-keyring`) or JNA |
| Country flags | `flag-icons` SVGs as Compose resources |
| Strings | Compose Multiplatform resources |

## Skills to use

- `design-skills:design-systems`: turn the glass rules into tokens and components (glass panel, caption card, droplet pill, tri-state chip, result row).
- `design-skills:interaction-design`: sidebar hover timing, droplet motion, live filtering, tri-state chip behaviour.
- `design-skills:accessibility-audit`: contrast on glass, keyboard use of the sidebar and tri-state chips, screen-reader names.
- `design-skills:ux-writing`: labels, empty, error and fallback messages.
- `apple-design`: materials and motion reference for the glass.
- `/run`: screenshot each screen as it lands.

## Build order

1. Phase 1: app shell and sidebar → Browse default view (shelves, Updates) → Search (filters, results) → title page.
2. Phase 2: Library (search, categories, group by, sort) → Latest updates → Reading history.
3. Phase 3: account avatars and sign-in (Comick, MangaDex, AniList).
