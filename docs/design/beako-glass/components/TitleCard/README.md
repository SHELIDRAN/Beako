# TitleCard

The glass caption card: a 2:3 cover with a frosted caption, an optional rank chip or unread badge, and a small lift on hover. It is the unit of every shelf and the Library grid.

- **Frame** — `card-width` × `card-height`, `radius-card`, 1 px `rim` border, `shadow-card`; a 56 px `card-highlight` fade along the top edge.
- **Cover** — fills the card; `surface-raised` while it loads or when there is none.
- **Caption** — glass blurring the card's own cover, inset `space-sm`, `radius-caption`, padding 9 × 11 px: title `title-small` (two lines, ellipsis), then a row of type Chip, "★ 8.5" and the follow count ("15.2k", right-aligned) in `label-small`. All caption text gets `text-shadow`.
- **Top corners** — RankChip on ranked shelves (top-left); unread count badge in `label-medium`, `text` fill with `ground` ink (top-right).
- **Hover and focus** — lifts 6 px and scales 1.5% on the droplet spring while the shadow deepens from `shadow-card` to `shadow-card-hover` (the drop grows and falls further down-right, 450 ms ease-out). Reduce motion keeps the shadow change and drops the movement.
- **Shelf** — a titled horizontal row of these: name in `headline-small`, an optional DropletSegmented beside it, cards 18 px apart scrolling edge to edge inside the `space-xxl` inset; loading = six Skeleton cards; empty = "Nothing here yet."
- The whole card is one button whose accessible name is the title.
