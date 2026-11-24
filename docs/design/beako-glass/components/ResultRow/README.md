# ResultRow

One search result as a glass row: rank, cover, title, details and description.

- **Rank** — position in the current sort, `headline-small` in `text-muted`, 44 px column.
- **Cover** — `row-cover-width` × `row-cover-height`, `radius-thumb`.
- **Body** — title `title-large` (one line); metadata `body-small` `text-muted` joined with "  •  " (type, status, "N chapters"); alternative titles; description `body-medium` `text-muted`, two lines.
- **Side** — 120 px, right-aligned: year `title-medium`, demographic and "N follows" `body-small` muted, rating "★ 8.7" `title-medium` in `gold`.
- **States** — padding 10 px, `radius-panel`; hover fills `hover`, **sweeping** in from the left over 360 ms (also on keyboard focus). Click opens the title page; right-click offers Add to library, Track on Comick, Copy link.
- Demographic is written out in words, never by colour or symbol alone.
