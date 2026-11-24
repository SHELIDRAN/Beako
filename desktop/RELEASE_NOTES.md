Beako is a manga reader for Windows, based on the [Neko](https://github.com/nekomangaorg/Neko) Android app (Apache-2.0).

## Beako 0.1.6

**Install:** download `Beako-0.1.6-windows-x64.msi` below and run it; it upgrades earlier versions in place. The installer isn't code-signed yet, so Windows SmartScreen may warn you: choose *More info → Run anyway*. The `.sha256` file lets you check the download.

### New
- **Dynamic colour scheme**, light and dark, in More → Appearance. It's built from your Windows accent colour (Settings → Personalization → Colors): the background takes a light tint of it, and unread dots, the tab underline, focus rings and the main button use it. Change the accent in Windows and Beako follows within half a minute. Text stays easy to read whatever colour you pick. It works with "Follow Windows light or dark mode" too.

0.1.5 brought 27 colour schemes from Neko, following Windows' light or dark mode, the new Beako logo, a right-click menu on search results, Ctrl+F, visible keyboard focus and a Mica title bar on Windows 11.

### Still to come
- Dart-based Mangayomi sources (most sources in the big repositories); JavaScript ones work.
- Discord Rich Presence works if you paste a Discord application ID in More → Discord; a built-in one is coming.

Beako uses comick.dev only for discovery and tracking: it never reads chapters or images from Comick. Not affiliated with Comick, MangaDex, Mangayomi or Neko. The extension bridge is adapted from [Mangayomi](https://github.com/kodjodevf/mangayomi) (Apache-2.0).
