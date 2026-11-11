Neko for Windows 0.3.0 adds Comick: its charts, its full search filters with tag search, and a Comick tracker.

## Download

Download `Neko-0.3.0-windows-x64.exe` below and run it. It's a single self-contained file with no installer and needs 64-bit Windows 10 or 11. Your library and settings carry over.

Windows SmartScreen may warn about an unrecognized app because the file isn't code-signed. Choose **More info**, then **Run anyway**. You can check the download against the `.sha256` file.

## New

**Comick catalog.** Browse now has a Comick tab.
- With no search or filters, it shows Comick's charts: Most Followed New Comics, Most Recent Popular, and Trending. Each can show the past 7 days, month, or 3 months.
- Below the charts, every Comick title is listed, sorted by follows.
- Clicking a Comick title opens it on MangaDex. Neko matches it by shared MangaUpdates, AniList, or MyAnimeList IDs, or else by title.

**Comick filters.**
- Genres, themes, formats, and content descriptors, each included or excluded.
- Tag search across Comick's thousands of community tags, with title counts. Click a tag to include it, or right-click to exclude it.
- Demographic, status, content rating, and type (Manga, Manhwa, Manhua).
- When the title was added to Comick, minimum and maximum chapters, minimum score, publication years, and six sort orders.

**Comick tracker.**
- Connect your Comick account in Settings → Comick using your own Comick developer app. Sign-in uses OAuth with PKCE in your browser, and tokens are kept in Windows Credential Manager.
- On any title page, choose **Track on Comick** to find the title on Comick and add it to your library.
- Set its status and score from the title page. Finished chapters update your Comick progress automatically; you can turn this off.

## Setup for the tracker

Register an app at comick.dev → Developer apps with these settings:
- **Client type:** Browser or mobile app without a private backend.
- **Application platform:** Native mobile or desktop app.
- **Permissions:** library:write.
- **Callback URL:** `http://127.0.0.1:47823/comick/callback`.

Then paste the app's client ID into Neko and click **Connect Comick**.

Neko is unofficial and isn't affiliated with MangaDex, Comick, MangaBaka, or Comix.
