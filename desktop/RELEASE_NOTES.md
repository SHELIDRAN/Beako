Beako is a manga reader for Windows, based on the [Neko](https://github.com/nekomangaorg/Neko) Android app (Apache-2.0).

## Beako 0.1.10

**Install:** on 0.1.9, choose **Update now** when Beako offers this version (or More → General → About → Check for updates). Otherwise download `Beako-0.1.10-windows-x64.msi` below and run it; it upgrades earlier versions in place and keeps your library and settings. The installer isn't code-signed yet, so Windows SmartScreen may warn you: choose *More info → Run anyway*. The `.sha256` file lets you check the download.

### New
- **Browse → Updates:** Comick's latest chapter releases, **Hot** or **New**, in your reading language, filterable by Manga, Manhwa and Manhua, with **Load more**. Each one opens the title page.
- **Saved presets** in Search with tags: **Save as preset** names your current filters. Presets show above the filters and on Browse, where one click opens Search with them. Your filters (not the keyword) are also kept between launches.
- **More filters:** released from / to year, and at least / at most chapters.
- **Country flags** on covers and in search results.
- **Beako remembers its window:** it opens where you left it, at the same size, or maximised.
- **Import from Neko Desktop** (More → Backup): brings in the library of the earlier Neko Desktop prototype, with read chapters and your last page.

### Improved
- **Larger interface sizes** (150% and up): Search with tags' buttons wrap and tab bars scroll instead of being cut off.
- **MangaDex@Home reports:** pages loaded from MangaDex's volunteer image servers are reported to MangaDex in the background, as MangaDex asks, so slow servers can be taken out of rotation. Nothing about you is sent.

### Still to come
- Dart sources not built on the Madara, MangaReader or MangaBox templates; JavaScript sources and those templates work.
- Discord Rich Presence works if you paste a Discord application ID in More → Discord; a built-in one is coming.

Beako uses comick.dev only for discovery and tracking: it never reads chapters or images from Comick. Not affiliated with Comick, MangaDex, Mangayomi or Neko. The extension bridge and site templates are adapted from [Mangayomi](https://github.com/kodjodevf/mangayomi) and its extensions (Apache-2.0). Country flags from [flag-icons](https://github.com/lipis/flag-icons) (MIT).
