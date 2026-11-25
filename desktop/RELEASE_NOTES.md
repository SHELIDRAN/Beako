Beako is a manga reader for Windows, based on the [Neko](https://github.com/nekomangaorg/Neko) Android app (Apache-2.0).

## Beako 0.1.9

**Install:** download `Beako-0.1.9-windows-x64.msi` below and run it; it upgrades earlier versions in place and keeps your library and settings. The installer isn't code-signed yet, so Windows SmartScreen may warn you: choose *More info → Run anyway*. The `.sha256` file lets you check the download.

**This is the last update you need to download by hand.** From 0.1.9 on, Beako tells you when a new version is out and installs it for you.

### New
- **Updates from inside the app.** Shortly after start and twice a day, Beako checks for a new version. A notice shows what's new, with **Update now**: Beako downloads the installer, checks it against its published checksum, closes, installs the update (Windows asks for permission once) and opens again. **Later** hides the notice for three days. More → General → About has **Check for updates** and a switch for automatic checks.
- **Motion setting** (More → Appearance → Motion): **Calm** (no bounce, slowest), **Balanced** (the new default: softer and slower than before) or **Lively** (the old springy feel). It sets how the side menu slides open and closed and how the selection highlight moves.
- **Browse menu regrouped:** **Comick**, with **Search with tags** under it, and **MangaDex**, with **Search MangaDex** under it.
- **Search MangaDex:** a Search tab on the MangaDex page.
- **Random picks** in Search with tags: random titles that match the tags, genres, types (Manga, Manhwa, Manhua) and other filters you've chosen. **Shuffle again** gives a new set.

### Still to come
- Dart sources not built on the Madara, MangaReader or MangaBox templates; JavaScript sources and those templates work.
- Discord Rich Presence works if you paste a Discord application ID in More → Discord; a built-in one is coming.

Beako uses comick.dev only for discovery and tracking: it never reads chapters or images from Comick. Not affiliated with Comick, MangaDex, Mangayomi or Neko. The extension bridge and site templates are adapted from [Mangayomi](https://github.com/kodjodevf/mangayomi) and its extensions (Apache-2.0).
