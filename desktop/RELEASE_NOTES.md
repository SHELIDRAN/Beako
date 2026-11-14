Beako is a manga reader for Windows, based on the [Neko](https://github.com/nekomangaorg/Neko) Android app (Apache-2.0).

## Beako 0.1.0 — first preview

**Install:** download `Beako-0.1.0-windows-x64.msi` below and run it. The installer isn't code-signed yet, so Windows SmartScreen may warn you: choose *More info → Run anyway*. The `.sha256` file lets you check the download.

### Discover
- Browse comick.dev's charts (Most Followed New, Most Recent Popular, with 7 / 30 / 90 day switches) and search with Comick's tag filters, where included tags are all required.
- When Comick can't answer a keyword search, Beako searches MangaDex instead and says so.
- Title pages with "More like this", a scanlation group filter and an **Available on** list of the places you can read the title.

### Read
- MangaDex is built in. You can also install **Mangayomi-format JavaScript sources** from repositories you add (More → Sources; wotaku.wiki lists them). Beako ships no sources: they're third-party code, run in a sandbox that can only load public web pages.
- If the chosen source lacks a chapter, Beako fills the gap from another source that has it.
- Webtoon and page-by-page modes, automatic webtoon detection, split double-page spreads, fit options, full screen, and rebindable keys.
- Downloads for offline reading.

### Library and tracking
- Library with categories, grouping and sorting; reading history; Latest updates with Windows notifications.
- Sign in to **Comick** (tracker) and **MangaDex**; progress is sent as you finish chapters and never lowered.
- Backups (daily, automatic), including restoring a backup from Neko on Android.
- Five dark colour schemes, glass strength and interface size settings.

### Not in this version
- Dart-based Mangayomi sources (most sources in the big repositories) can't run yet; JavaScript ones can.
- AniList tracking and Discord Rich Presence are coming next.
- Sign-in flows were tested against recorded responses, not yet against the live Comick and MangaDex sites: please report anything that goes wrong.

Beako uses comick.dev only for discovery and tracking: it never reads chapters or images from Comick. Not affiliated with Comick, MangaDex, Mangayomi or Neko. The extension bridge is adapted from [Mangayomi](https://github.com/kodjodevf/mangayomi) (Apache-2.0).
