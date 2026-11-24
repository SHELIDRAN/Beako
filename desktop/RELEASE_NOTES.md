Beako is a manga reader for Windows, based on the [Neko](https://github.com/nekomangaorg/Neko) Android app (Apache-2.0).

## Beako 0.1.4

**Install:** download `Beako-0.1.4-windows-x64.msi` below and run it; it upgrades earlier versions in place. The installer isn't code-signed yet, so Windows SmartScreen may warn you: choose *More info → Run anyway*. The `.sha256` file lets you check the download.

### New look: Beako Glass
- **A new navigation menu.** The sidebar is now a full menu: search (Ctrl+K), your library and new-chapter counts, Browse with its Discover and Search pages, your accounts, and More at the bottom. It collapses to a slim icon rail; the pin button keeps it open, and More → Appearance → Sidebar still chooses how it behaves.
- **Porcelain, a light colour scheme**, in More → Appearance. The reader keeps text you can read on its own black, gray or white page background.
- **More is split into tabs:** General, Accounts, Sources and Backup.
- **Title page:** "Continue reading" is a large orange button, and a round + button adds the title to your library (when it's there, it opens the categories menu).
- **Reader settings:** page fit is a row of icons, and the background is a Black · Gray · White switch.
- **Library:** a softer search box, and the sort order is a small pill that opens into a choice.
- **Clearer messages:** failed downloads, tracker sync problems and sign-in errors now show as alerts.
- **Motion:** buttons dip when pressed, rows and tag chips fill with a sweep, arrows swing in on hover, and selections slide into place. More → Appearance → Reduce motion turns all of it off.

### Still to come
- Dart-based Mangayomi sources (most sources in the big repositories); JavaScript ones work.
- Discord Rich Presence works if you paste a Discord application ID in More → Discord; a built-in one is coming.

Beako uses comick.dev only for discovery and tracking: it never reads chapters or images from Comick. Not affiliated with Comick, MangaDex, Mangayomi or Neko. The extension bridge is adapted from [Mangayomi](https://github.com/kodjodevf/mangayomi) (Apache-2.0).
