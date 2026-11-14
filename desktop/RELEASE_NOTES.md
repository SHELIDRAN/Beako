Beako is a manga reader for Windows, based on the [Neko](https://github.com/nekomangaorg/Neko) Android app (Apache-2.0).

## Beako 0.1.1 — fixes from the first preview

**Install:** download `Beako-0.1.1-windows-x64.msi` below and run it; it upgrades 0.1.0 in place. The installer isn't code-signed yet, so Windows SmartScreen may warn you: choose *More info → Run anyway*. The `.sha256` file lets you check the download.

### Fixed
- **Comick and AniList sign-in crashed** in the installed app ("com/sun/net/httpserver/HttpServer"). The installer now includes the full Java runtime, and every release is self-tested as an installed app before it's published. (The installer is larger as a result.)
- **About half the covers were blank** on Browse and title pages: covers whose original is a PNG asked Comick for an image that doesn't exist.
- **Installed sources failed silently** when they search with spaces in the address (for example Comix): addresses are now encoded the way a browser does it. Sources that read `apiUrl` or call `client.get(url)` with no headers work too.

### New
- **Choose where to read on every title page.** The "Reading from…" / "Not available on any source" button opens a source picker: MangaDex and each installed source with what was found (matched, no match, or the source's error), and a search by hand on any of them, prefilled with the title and its other names (romaji, original title), to pick the right one when automatic matching misses.
- **AniList is one click:** More → Accounts and tracking → AniList → Connect. No need to create your own API client any more.
- **A diagnostic log** for reporting problems: `%APPDATA%\Beako\beako.log` records source errors, images that fail to load and crashes. It never contains passwords or sign-in tokens.
- Reopening a title page no longer searches every installed source again.

### Still to come
- Dart-based Mangayomi sources (most sources in the big repositories); JavaScript ones work.
- Discord Rich Presence works if you paste a Discord application ID in More → Discord; a built-in one is coming.

Beako uses comick.dev only for discovery and tracking: it never reads chapters or images from Comick. Not affiliated with Comick, MangaDex, Mangayomi or Neko. The extension bridge is adapted from [Mangayomi](https://github.com/kodjodevf/mangayomi) (Apache-2.0).
