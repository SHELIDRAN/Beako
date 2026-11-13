# Comick API: what Beako uses

Condensed from Comick's developer docs and OpenAPI spec (`comick-openapi.json`, API version 1.1, downloaded Nov 13, 2026). The spec is the source of truth; this page records the decisions that matter for Beako.

## Two APIs, two auth modes

| | Catalog | Library (tracker) |
| --- | --- | --- |
| Base | `https://api.comick.dev` | `https://api.comick.dev/integrations/v1` |
| Auth | none | OAuth access token, `Authorization: Bearer …` |
| Use in Beako | discovery: search, tags, charts, details | tracker: status, progress, rating |

"Sign in with Comick" (OpenID Connect, issuer `https://comick.dev/api/auth/oidc`) is a **separate** flow that only identifies the user; its tokens can't read the library. Beako doesn't need it.

## Catalog endpoints for discovery

- `GET /v1.0/search/` — search (`q`) or browse with filters: `genres` / `excludes` (genre slugs), `tags` / `excluded-tags` (community tag slugs), `demographic` (1 shounen, 2 shoujo, 3 seinen, 4 josei), `country` (jp, kr, cn…), `content_rating`, `status`, `completed` (translation complete), `from` / `to` years, `minimum` / `maximum` chapters, `minimum_rating`, `time` (added in last N days), `sort` (`view`, `created_at`, `uploaded`, `rating`, `average_rating`, `follow`, `user_follow_count`), `page`, `limit`, `tachiyomi=true` (adds `cover_url`). Paging: page ≤ 50 with limit ≤ 50, or page ≤ 5 with limit ≤ 300; default page 1, limit 15.
  - Results include `external_ids` (`anilist`, `myanimelist`, `mangabaka`, `mangaupdates`). **Use these to match a Comick title to MangaDex** (MangaDex stores AniList/MAL/MU links) without extra requests.
- `GET /top` — homepage feeds (ranking, trending, recent, completed…), or one ranked feed with `type` (`trending`, `newfollow`, `follow`) + `day` (7, 30, 90, 180, 270, 360, 720). Filters: `comic_types` (manga, manhwa, manhua, others), `gender`, `safe_only`, `accept_mature_content`. Cached ~1 hour.
- `GET /genre/` — genres with `slug`, `comic_count`, `group`. Cache a day.
- `GET /category/` (all community tags) and `GET /category/popular` (top 300 by `comic_count`). Cache a day.
- `GET /v1.0/comic/{slug}/` — details (`comic`, authors, artists, `langList`, `firstChapters`). `title_locale=en` adds display titles.
- `GET /chapter/` — latest chapter updates (`lang` repeated, `type`, `order=hot|new`, `page` ≤ 100 useful).
- `GET /people/{slug}/` — an author or artist and their works.
- Flow: search → `slug` → details → comic `hid` → `GET /v1.0/comic/{hid}/chapters`. Comic and chapter HIDs are different; never parse a HID as a number.

Comick still exposes chapter lists and `GET /chapter/{hid}/get_images` metadata, but many chapters link to external providers or have no images. Per the owner's plan, Beako reads chapters from installed extensions and MangaDex, not Comick.

## Library (tracker) endpoints

All need an OAuth access token; reads need `library:read`, writes need `library:write`.

- `GET /me` — connected user's public UUID, username, avatar.
- `GET /me/library` — the user's library, paged with an opaque `next_cursor` (pass it back unchanged with the same filters).
- `GET /titles?q=…&media_type=manga&limit=…` — find a title to track (`q` 2–100 chars, `limit` 1–20). `GET /titles/{hid}` — details with `external_ids`.
- `GET|PUT|PATCH|DELETE /me/library/{hid}` — read, add, update or remove one entry.

Mapping (from Comick's "Mihon and tracker integrations" guide):
- **Bind:** `GET /me/library/{hid}`; keep remote progress/status if it exists, `PUT` a new entry after a 404. Never lower remote progress during initial binding.
- **Update:** `PATCH` only the fields that changed.
- **Status:** 1 reading, 2 completed, 3 on hold, 4 dropped, 5 plan to read. No rereading status.
- **Progress:** last chapter read as a **string** (`"12.5"`, `"0"`), or `{"hid": …}` for a Comick chapter; `null` clears. Preserve decimal or non-numeric values.
- **Rating:** integer 1–10, `null` to clear. Never send 0 or round an imported decimal score.
- Not supported: start/finish dates, private entries, final chapter totals. Don't treat `last_released_number` as a total.

## OAuth for the library (public native client)

- Authorize in the **system browser** (never an embedded WebView): `https://comick.dev/api/auth/oauth2/authorize` with `response_type=code`, `client_id`, exact `redirect_uri`, `scope=library:read library:write offline_access`, `resource=https://api.comick.dev/integrations/v1`, fresh random `state`, `code_challenge` (base64url SHA-256 of a 43–128 char verifier, no padding), `code_challenge_method=S256`. Loopback callbacks are allowed.
- Exchange: `POST https://comick.dev/api/auth/oauth2/token` (form-encoded) with `grant_type=authorization_code`, `client_id`, `code`, `redirect_uri`, `code_verifier`, `resource`. Codes are single-use; if the exchange fails or the response is lost, restart authorization.
- Check the granted `scope` in the response; users can decline write or offline access.
- **Lifetimes:** access token 15 minutes, refresh token 30 days. **Refresh tokens rotate on every use with no grace period** — reusing an old one can revoke the whole token family. So: one refresh at a time, atomically save the new access + refresh token, never auto-retry a refresh after an ambiguous network failure. On `invalid_grant`, stop syncing and show "Reconnect Comick".
- For a failed API call, refresh at most once, then retry the call once.
- Revoke: `POST https://comick.dev/api/auth/oauth2/revoke` with `token`, `client_id`, `token_type_hint`. On disconnect, revoke both tokens and erase local tokens and cached library data.
- Storage: refresh token in Windows Credential Manager; access token in memory only. Never log tokens or put them in crash reports.

## Limits and errors

- Catalog: **200 requests/minute per client IP**.
- Library: **60/minute per app+user**, 1,000/minute per app, 120/minute per IP.
- Read `x-ratelimit-limit`, `x-ratelimit-remaining`, `x-ratelimit-reset` and `Retry-After`. On 429 wait, reduce concurrency, back off exponentially; repeated excess can get the IP blocked.
- Honor `Cache-Control`; library responses are `private, no-store` (don't cache them publicly or on disk unencrypted).
- Cloudflare may answer with an HTML challenge before the API: **check status and `Content-Type` before parsing JSON** and show a clear "Comick is unavailable right now" state.
- 400 bad input, 401 missing/expired/revoked token, 403 insufficient scope or app/account not eligible, 404 unknown id, 5xx server error.
