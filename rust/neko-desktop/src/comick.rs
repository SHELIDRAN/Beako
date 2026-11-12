//! Comick: the catalog behind the Comick source (search, genres, community tags, charts) and a
//! tracker that keeps your Comick library in step with what you read.
//!
//! Catalog reads need no account. The tracker signs in with OAuth 2.0 (authorization code with
//! PKCE) using a Comick developer app's public client ID; the browser hands the code back to a
//! one-shot listener on 127.0.0.1. Tokens are kept in Windows Credential Manager.

use std::io::{Read as _, Write as _};
use std::net::TcpListener;
use std::sync::Mutex;
use std::time::{Duration, Instant};

use anyhow::{Context as _, Result, anyhow, bail};
use base64::Engine as _;
use serde::{Deserialize, Serialize};
use serde_json::{Value, json};
use sha2::Digest as _;

use crate::api::{Client, Filters, Manga};

pub const API: &str = "https://api.comick.dev";
pub const COVERS: &str = "https://meo.comick.pictures";
const AUTHORIZE_URL: &str = "https://comick.dev/api/auth/oauth2/authorize";
const TOKEN_URL: &str = "https://comick.dev/api/auth/oauth2/token";
const LIBRARY_URL: &str = "https://api.comick.dev/integrations/v1";
/// The callback registered on the Comick developer app. A fixed port, because Comick matches
/// callback URLs exactly.
pub const CALLBACK_PORT: u16 = 47823;
pub const CALLBACK_URL: &str = "http://127.0.0.1:47823/comick/callback";
const SCOPES: &str = "library:read library:write offline_access";
/// The OAuth resource indicator Comick requires for library access (RFC 8707).
const RESOURCE: &str = "https://api.comick.dev/integrations/v1";
const KEYRING_SERVICE: &str = "Neko Desktop";
const KEYRING_USER: &str = "comick";

/// A genre, theme, format, or content descriptor from Comick's genre list.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct Genre {
    pub slug: String,
    pub name: String,
    pub group: String,
}

/// One of Comick's community tags.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct ComickTag {
    pub slug: String,
    pub name: String,
    pub count: u32,
}

/// The charts on Comick's home page.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum Chart {
    /// New titles with the most follows in the period.
    MostFollowedNew,
    /// Titles with the most recent attention.
    RecentPopular,
    Trending,
}

/// The medium-size cover (about 190 KB instead of the 1–2 MB original).
fn cover_of(v: &Value) -> Option<String> {
    let key = v["md_covers"].as_array()?.first()?["b2key"].as_str()?;
    Some(match key.rsplit_once('.') {
        Some((stem, ext)) => format!("{COVERS}/{stem}-m.{ext}"),
        None => format!("{COVERS}/{key}"),
    })
}

/// Comick's short title ID, from a title the Comick source produced.
pub fn hid_of(m: &Manga) -> Option<&str> {
    m.id.strip_prefix("comick:")
}

/// A Comick title as a [`Manga`], with the IDs MangaDex also lists (MangaUpdates, AniList,
/// MyAnimeList) under MangaDex's link keys so the title can be found there.
pub fn parse_comic(v: &Value) -> Manga {
    let ext = &v["external_ids"];
    let id_of = |k: &str| match &ext[k] {
        Value::String(s) if !s.is_empty() => Some(s.clone()),
        Value::Number(n) => Some(n.to_string()),
        _ => None,
    };
    let mut links = std::collections::BTreeMap::new();
    for (ours, theirs) in [("mu", "mangaupdates"), ("al", "anilist"), ("mal", "myanimelist")] {
        if let Some(id) = id_of(theirs) {
            links.insert(ours.to_owned(), id);
        }
    }
    let kind = match v["country"].as_str() {
        Some("jp") => Some("Manga"),
        Some("kr") => Some("Manhwa"),
        Some("cn" | "hk" | "tw") => Some("Manhua"),
        _ => None,
    };
    let rating = v["bayesian_rating"]
        .as_str()
        .or_else(|| v["rating"].as_str())
        .and_then(|r| r.parse::<f64>().ok())
        .or_else(|| v["rating"].as_f64());
    let year = v["year"].as_u64().map(|y| y as u32);
    let mut caption = Vec::new();
    if let Some(r) = rating {
        caption.push(format!("★ {r:.1}"));
    }
    caption.extend(kind.map(str::to_owned));
    caption.extend(year.map(|y| y.to_string()));

    // An English title from the alternate titles when the main one is romanized.
    let english = v["md_titles"].as_array().and_then(|titles| {
        titles
            .iter()
            .find(|t| t["lang"] == "en")
            .and_then(|t| t["title"].as_str())
            .map(str::to_owned)
    });
    let title = v["title"].as_str().unwrap_or_default().to_owned();
    let (title, alt_title) = match (v["is_english_title"].as_bool(), english) {
        (Some(true), _) | (_, None) => (title, None),
        (_, Some(en)) if en != title => (en, Some(title)),
        (_, Some(_)) => (title, None),
    };
    let status = match v["status"].as_u64() {
        Some(1) => Some("ongoing"),
        Some(2) => Some("completed"),
        Some(3) => Some("cancelled"),
        Some(4) => Some("hiatus"),
        _ => None,
    };
    Manga {
        id: format!("comick:{}", v["hid"].as_str().unwrap_or_default()),
        source: "comick".into(),
        title,
        alt_title,
        description: v["desc"].as_str().unwrap_or_default().to_owned(),
        status: status.map(str::to_owned),
        year,
        content_rating: v["content_rating"].as_str().map(str::to_owned),
        links,
        cover_override: cover_of(v),
        caption: (!caption.is_empty()).then(|| caption.join(" · ")),
        ..Default::default()
    }
}

impl Client {
    fn comick_get(&self, path: &str, query: &[(&str, String)]) -> Result<Value> {
        let resp = self
            .http
            .get(format!("{API}/{path}"))
            .query(query)
            .send()
            .context("Couldn't reach Comick")?;
        let status = resp.status();
        let text = resp.text().unwrap_or_default();
        if !status.is_success() {
            if text.contains("Just a moment") {
                bail!("Comick is asking for a browser check right now. Try again in a minute");
            }
            let v: Value = serde_json::from_str(&text).unwrap_or(Value::Null);
            let msg = v["message"].as_str().unwrap_or("");
            bail!(
                "Comick returned {status}{}",
                if msg.is_empty() {
                    String::new()
                } else {
                    format!(": {msg}")
                }
            );
        }
        serde_json::from_str(&text).context("Comick sent a response Neko couldn't read")
    }

    /// Comick's catalog search with already-built query parameters.
    pub fn comick_search(&self, query: &[(&str, String)]) -> Result<Vec<Manga>> {
        let body = self.comick_get("v1.0/search", query)?;
        Ok(body.as_array().into_iter().flatten().map(parse_comic).collect())
    }

    /// A chart for the last `days` (7, 30, or 90).
    pub fn comick_chart(&self, chart: Chart, days: u32, filters: &Filters) -> Result<Vec<Manga>> {
        let mut ratings: Vec<(&str, String)> = vec![("content_rating", "safe".into())];
        if filters.show_suggestive {
            ratings.push(("content_rating", "suggestive".into()));
        }
        if filters.show_erotica {
            ratings.push(("content_rating", "erotica".into()));
        }
        match chart {
            Chart::MostFollowedNew => {
                // Comick's own "top follows" list has no covers; the same ranking comes from
                // search, sorted by follows among titles added in the period.
                let mut q = vec![
                    ("limit", "20".to_owned()),
                    ("sort", "user_follow_count".to_owned()),
                    ("time", days.to_string()),
                ];
                q.extend(ratings);
                self.comick_search(&q)
            }
            Chart::RecentPopular | Chart::Trending => {
                let mut q = vec![("day", days.to_string())];
                if filters.show_erotica {
                    q.push(("accept_mature_content", "true".into()));
                }
                let body = self.comick_get("top", &q)?;
                let list = match chart {
                    Chart::RecentPopular => body["recentRank"].clone(),
                    _ => body["trending"][days.to_string()].clone(),
                };
                let list = if list.as_array().is_some_and(|a| !a.is_empty()) {
                    list
                } else {
                    body["rank"].clone()
                };
                Ok(list
                    .as_array()
                    .into_iter()
                    .flatten()
                    .filter(|v| cover_of(v).is_some())
                    .map(parse_comic)
                    .collect())
            }
        }
    }

    pub fn comick_genres(&self) -> Result<Vec<Genre>> {
        let body = self.comick_get("genre", &[])?;
        Ok(body
            .as_array()
            .into_iter()
            .flatten()
            .filter_map(|g| {
                Some(Genre {
                    slug: g["slug"].as_str()?.to_owned(),
                    name: g["name"].as_str()?.to_owned(),
                    group: g["group"].as_str().unwrap_or("").to_lowercase(),
                })
            })
            .collect())
    }

    /// Every community tag with at least one title, most used first.
    pub fn comick_tags(&self) -> Result<Vec<ComickTag>> {
        let body = self.comick_get("category", &[])?;
        let mut tags: Vec<ComickTag> = body
            .as_array()
            .into_iter()
            .flatten()
            .filter_map(|t| {
                let count = t["comic_count"].as_u64()? as u32;
                (count > 0).then(|| ComickTag {
                    slug: t["slug"].as_str().unwrap_or_default().to_owned(),
                    name: t["title"].as_str().unwrap_or_default().to_owned(),
                    count,
                })
            })
            .collect();
        tags.sort_by_key(|t| std::cmp::Reverse(t.count));
        Ok(tags)
    }

    /// Finds the Comick title for a MangaDex title, by shared IDs or exact title.
    pub fn comick_find(&self, manga: &Manga) -> Result<Manga> {
        let mut titles = vec![manga.title.clone()];
        titles.extend(manga.alt_title.clone());
        for title in titles {
            let q = vec![
                ("q", title.chars().take(100).collect::<String>()),
                ("limit", "15".into()),
            ];
            let found = self.comick_search(&q)?;
            if let Some(m) = found.iter().find(|c| crate::sources::links_match(manga, c)) {
                return Ok(m.clone());
            }
            if let Some(m) = found.iter().find(|c| crate::sources::titles_match(&title, c)) {
                return Ok(m.clone());
            }
        }
        bail!("Couldn’t find “{}” on Comick", manga.title)
    }
}

// ---------------------------------------------------------------------------------------------
// Tracker

/// Reading statuses in Comick's library.
pub const LIST_STATUSES: [(u8, &str); 5] = [
    (1, "Reading"),
    (2, "Completed"),
    (3, "On Hold"),
    (4, "Dropped"),
    (5, "Plan to Read"),
];

pub fn list_status_label(n: u8) -> &'static str {
    LIST_STATUSES
        .iter()
        .find(|(k, _)| *k == n)
        .map_or("Unknown", |(_, l)| l)
}

/// A title in the person's Comick library.
#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
pub struct LibraryEntry {
    pub hid: String,
    pub title: String,
    pub status: Option<u8>,
    pub rating: Option<u8>,
    /// Chapter number, kept as text the way Comick stores it ("12.5").
    pub progress: Option<String>,
}

fn parse_entry(v: &Value) -> LibraryEntry {
    let d = if v["data"].is_object() { &v["data"] } else { v };
    LibraryEntry {
        hid: d["hid"].as_str().unwrap_or_default().to_owned(),
        title: d["title"].as_str().unwrap_or_default().to_owned(),
        status: d["status"].as_u64().map(|s| s as u8),
        rating: d["rating"].as_u64().map(|r| r as u8),
        progress: match &d["progress"]["number"] {
            Value::String(s) => Some(s.clone()),
            Value::Number(n) => Some(n.to_string()),
            _ => None,
        },
    }
}

#[derive(Clone, Debug, Serialize, Deserialize)]
struct Stored {
    client_id: String,
    refresh_token: String,
    username: String,
}

struct Access {
    token: String,
    expires: Instant,
}

#[derive(Default)]
pub struct ComickSession {
    stored: Mutex<Option<Stored>>,
    access: Mutex<Option<Access>>,
}

fn entry() -> Result<keyring::Entry> {
    keyring::Entry::new(KEYRING_SERVICE, KEYRING_USER).map_err(|e| anyhow!("{e}"))
}

fn random_url_safe(bytes: usize) -> String {
    let mut buf = vec![0u8; bytes];
    getrandom::fill(&mut buf).expect("the OS random source is available");
    base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(buf)
}

pub fn pkce_challenge(verifier: &str) -> String {
    base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(sha2::Sha256::digest(verifier.as_bytes()))
}

impl ComickSession {
    pub fn load() -> Self {
        let stored = entry()
            .ok()
            .and_then(|e| e.get_password().ok())
            .and_then(|j| serde_json::from_str(&j).ok());
        Self {
            stored: Mutex::new(stored),
            access: Mutex::new(None),
        }
    }

    pub fn username(&self) -> Option<String> {
        self.stored.lock().ok()?.as_ref().map(|s| s.username.clone())
    }

    pub fn sign_out(&self) {
        *self.stored.lock().expect("lock") = None;
        *self.access.lock().expect("lock") = None;
        if let Ok(e) = entry() {
            let _ = e.delete_credential();
        }
    }

    /// Opens the browser to Comick's consent page and waits for the redirect back.
    pub fn connect(&self, http: &reqwest::blocking::Client, client_id: &str) -> Result<String> {
        let client_id = client_id.trim();
        if client_id.is_empty() {
            bail!("Enter the client ID of your Comick developer app");
        }
        let listener = TcpListener::bind(("127.0.0.1", CALLBACK_PORT)).with_context(|| {
            format!("Port {CALLBACK_PORT} is in use. Close other apps using it and try again")
        })?;
        listener.set_nonblocking(false)?;
        let verifier = random_url_safe(48);
        let state = random_url_safe(16);
        let url = reqwest::Url::parse_with_params(
            AUTHORIZE_URL,
            &[
                ("response_type", "code"),
                ("client_id", client_id),
                ("redirect_uri", CALLBACK_URL),
                ("scope", SCOPES),
                ("resource", RESOURCE),
                ("state", state.as_str()),
                ("code_challenge", pkce_challenge(&verifier).as_str()),
                ("code_challenge_method", "S256"),
            ],
        )?;
        open::that_detached(url.as_str()).context("Couldn't open your browser")?;

        let code = wait_for_code(&listener, &state)?;
        let form = [
            ("grant_type", "authorization_code"),
            ("code", code.as_str()),
            ("redirect_uri", CALLBACK_URL),
            ("client_id", client_id),
            ("code_verifier", verifier.as_str()),
            ("resource", RESOURCE),
        ];
        let tokens = request_tokens(http, &form)?;
        if !tokens.scope.is_empty()
            && !(tokens.scope.contains("library:write") && tokens.scope.contains("offline_access"))
        {
            bail!("Allow Neko to update your library and stay signed in when Comick asks, then try again");
        }
        let token = tokens.access.clone();
        *self.access.lock().expect("lock") = Some(Access {
            token: tokens.access,
            expires: Instant::now() + Duration::from_secs(tokens.expires_in),
        });
        let me: Value = http
            .get(format!("{LIBRARY_URL}/me"))
            .bearer_auth(&token)
            .send()?
            .json()
            .unwrap_or(Value::Null);
        let username = me["username"]
            .as_str()
            .or_else(|| me["data"]["username"].as_str())
            .unwrap_or("Comick reader");
        let stored = Stored {
            client_id: client_id.to_owned(),
            refresh_token: tokens.refresh.unwrap_or_default(),
            username: username.to_owned(),
        };
        save(&stored)?;
        *self.stored.lock().expect("lock") = Some(stored);
        Ok(username.to_owned())
    }

    fn token(&self, http: &reqwest::blocking::Client) -> Result<String> {
        if let Some(a) = self.access.lock().expect("lock").as_ref()
            && a.expires > Instant::now() + Duration::from_secs(30)
        {
            return Ok(a.token.clone());
        }
        let stored = self
            .stored
            .lock()
            .expect("lock")
            .clone()
            .context("Connect Comick in Settings first")?;
        let form = [
            ("grant_type", "refresh_token"),
            ("refresh_token", stored.refresh_token.as_str()),
            ("client_id", stored.client_id.as_str()),
            ("resource", RESOURCE),
        ];
        let tokens = match request_tokens(http, &form) {
            Ok(t) => t,
            Err(e) if e.to_string().contains("invalid_grant") => {
                self.sign_out();
                bail!("Your Comick connection expired. Connect again in Settings");
            }
            Err(e) => return Err(e),
        };
        if let Some(refresh) = tokens.refresh.clone() {
            let updated = Stored {
                refresh_token: refresh,
                ..stored
            };
            let _ = save(&updated);
            *self.stored.lock().expect("lock") = Some(updated);
        }
        let token = tokens.access.clone();
        *self.access.lock().expect("lock") = Some(Access {
            token: tokens.access,
            expires: Instant::now() + Duration::from_secs(tokens.expires_in),
        });
        Ok(token)
    }

    fn call(
        &self,
        http: &reqwest::blocking::Client,
        method: reqwest::Method,
        path: &str,
        body: Option<Value>,
    ) -> Result<Option<Value>> {
        let token = self.token(http)?;
        let mut req = http
            .request(method, format!("{LIBRARY_URL}{path}"))
            .bearer_auth(token);
        if let Some(b) = body {
            req = req.json(&b);
        }
        let resp = req.send().context("Couldn't reach Comick")?;
        let status = resp.status();
        if status == reqwest::StatusCode::NOT_FOUND {
            return Ok(None);
        }
        let text = resp.text().unwrap_or_default();
        if !status.is_success() {
            let v: Value = serde_json::from_str(&text).unwrap_or(Value::Null);
            let msg = v["error"]
                .as_str()
                .or_else(|| v["message"].as_str())
                .unwrap_or("");
            bail!(
                "Comick returned {status}{}",
                if msg.is_empty() {
                    String::new()
                } else {
                    format!(": {msg}")
                }
            );
        }
        Ok(Some(serde_json::from_str(&text).unwrap_or(Value::Null)))
    }

    /// The library entry for a title, or `None` when it isn't in the library.
    pub fn entry(&self, http: &reqwest::blocking::Client, hid: &str) -> Result<Option<LibraryEntry>> {
        Ok(self
            .call(http, reqwest::Method::GET, &format!("/me/library/{hid}"), None)?
            .map(|v| parse_entry(&v)))
    }

    /// Adds or updates a title in the library.
    pub fn save_entry(&self, http: &reqwest::blocking::Client, e: &LibraryEntry) -> Result<LibraryEntry> {
        let mut body = json!({ "status": e.status.unwrap_or(1) });
        if let Some(r) = e.rating.filter(|r| *r > 0) {
            body["rating"] = json!(r);
        }
        if let Some(p) = &e.progress {
            body["progress"] = json!({ "number": p });
        }
        let saved = self.call(
            http,
            reqwest::Method::PUT,
            &format!("/me/library/{}", e.hid),
            Some(body),
        )?;
        Ok(saved
            .map(|v| parse_entry(&v))
            .filter(|s| !s.hid.is_empty())
            .unwrap_or_else(|| e.clone()))
    }

    pub fn remove_entry(&self, http: &reqwest::blocking::Client, hid: &str) -> Result<()> {
        self.call(http, reqwest::Method::DELETE, &format!("/me/library/{hid}"), None)?;
        Ok(())
    }
}

struct Tokens {
    access: String,
    refresh: Option<String>,
    expires_in: u64,
    scope: String,
}

fn request_tokens(http: &reqwest::blocking::Client, form: &[(&str, &str)]) -> Result<Tokens> {
    let resp = http
        .post(TOKEN_URL)
        .form(form)
        .send()
        .context("Couldn't reach Comick sign-in")?;
    let status = resp.status();
    let body: Value = resp.json().unwrap_or(Value::Null);
    if !status.is_success() {
        let code = body["error"].as_str().unwrap_or("");
        let detail = body["error_description"].as_str().unwrap_or("");
        bail!(
            "Comick sign-in failed ({}){}",
            if code.is_empty() {
                status.to_string()
            } else {
                code.to_owned()
            },
            if detail.is_empty() {
                String::new()
            } else {
                format!(": {detail}")
            }
        );
    }
    Ok(Tokens {
        access: body["access_token"]
            .as_str()
            .context("Comick didn't return an access token")?
            .to_owned(),
        refresh: body["refresh_token"].as_str().map(str::to_owned),
        expires_in: body["expires_in"].as_u64().unwrap_or(3600),
        scope: body["scope"].as_str().unwrap_or("").to_owned(),
    })
}

fn save(stored: &Stored) -> Result<()> {
    entry()?
        .set_password(&serde_json::to_string(stored)?)
        .map_err(|e| anyhow!("Couldn't save the Comick connection to the system credential store: {e}"))
}

/// Accepts the browser's redirect, answers it with a short page, and returns the code.
fn wait_for_code(listener: &TcpListener, state: &str) -> Result<String> {
    let deadline = Instant::now() + Duration::from_secs(300);
    listener.set_nonblocking(true)?;
    loop {
        if Instant::now() > deadline {
            bail!("Comick sign-in timed out. Try connecting again");
        }
        let (mut stream, _) = match listener.accept() {
            Ok(s) => s,
            Err(e) if e.kind() == std::io::ErrorKind::WouldBlock => {
                std::thread::sleep(Duration::from_millis(150));
                continue;
            }
            Err(e) => return Err(e.into()),
        };
        stream.set_nonblocking(false)?;
        stream.set_read_timeout(Some(Duration::from_secs(5)))?;
        let mut buf = [0u8; 4096];
        let n = stream.read(&mut buf).unwrap_or(0);
        let request = String::from_utf8_lossy(&buf[..n]);
        let path = request.split_whitespace().nth(1).unwrap_or("/");
        if !path.starts_with("/comick/callback") {
            let _ =
                stream.write_all(b"HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
            continue;
        }
        let url = reqwest::Url::parse(&format!("http://127.0.0.1{path}"))?;
        let param = |k: &str| {
            url.query_pairs()
                .find(|(key, _)| key == k)
                .map(|(_, v)| v.into_owned())
        };
        let (title, message, result) = match (param("code"), param("state"), param("error")) {
            (_, _, Some(err)) => (
                "Not Connected",
                "Comick didn’t grant access. You can close this tab.",
                Err(anyhow!("Comick sign-in was cancelled ({err})")),
            ),
            (Some(code), Some(s), None) if s == state => (
                "Connected",
                "Neko is connected to Comick. You can close this tab and return to Neko.",
                Ok(code),
            ),
            _ => (
                "Not Connected",
                "The sign-in response didn’t match. Try again from Neko.",
                Err(anyhow!("The Comick sign-in response didn't match; try again")),
            ),
        };
        let page = format!(
            "<!doctype html><meta charset=utf-8><title>{title}</title><body style=\"font-family:system-ui,sans-serif;display:grid;place-items:center;height:90vh;color:#1d1d1f\"><div style=\"text-align:center\"><h1 style=\"font-weight:600\">{title}</h1><p style=\"color:#6e6e73\">{message}</p></div>"
        );
        let _ = write!(
            stream,
            "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{page}",
            page.len()
        );
        return result;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pkce_challenge_is_base64url_sha256() {
        // Expected value computed independently:
        // base64.urlsafe_b64encode(hashlib.sha256(verifier).digest()).rstrip(b"=")
        assert_eq!(
            pkce_challenge("dBjftJeZ4CVP-mJoQs4n1hVw3_C7zP4X5HoJ4Z7uJqo"),
            "dGoIRmBZe0f4cZNIGoqf78MF0axcFbV-vNsRIwMt6rQ"
        );
        let verifier = random_url_safe(48);
        assert_eq!(verifier.len(), 64);
        assert!(
            verifier
                .chars()
                .all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_')
        );
    }

    #[test]
    fn callback_listener_returns_code_and_checks_state() {
        for (state, expect_ok) in [("good", true), ("forged", false)] {
            let listener = TcpListener::bind("127.0.0.1:0").unwrap();
            let port = listener.local_addr().unwrap().port();
            let browser = std::thread::spawn(move || {
                let mut s = std::net::TcpStream::connect(("127.0.0.1", port)).unwrap();
                write!(
                    s,
                    "GET /comick/callback?code=abc123&state={state} HTTP/1.1\r\nHost: x\r\n\r\n"
                )
                .unwrap();
                let mut page = String::new();
                let _ = s.read_to_string(&mut page);
                page
            });
            let result = wait_for_code(&listener, "good");
            let page = browser.join().unwrap();
            assert!(page.starts_with("HTTP/1.1 200"));
            if expect_ok {
                assert_eq!(result.unwrap(), "abc123");
                assert!(page.contains("Connected"));
            } else {
                assert!(result.is_err());
            }
        }
    }

    #[test]
    fn parses_library_entry() {
        let e = parse_entry(
            &json!({ "data": { "hid": "abc", "title": "T", "status": 1, "rating": 8, "progress": { "number": "12.5" } } }),
        );
        assert_eq!(
            e,
            LibraryEntry {
                hid: "abc".into(),
                title: "T".into(),
                status: Some(1),
                rating: Some(8),
                progress: Some("12.5".into())
            }
        );
    }

    #[test]
    fn parses_comic_into_a_title() {
        let v = json!({
            "id": 29330, "hid": "yDm32aOJ", "title": "TBATE", "bayesian_rating": "9.29", "year": 2018,
            "country": "kr", "is_english_title": null, "status": 4, "desc": "A king reborn.",
            "md_titles": [{ "title": "The Beginning After the End", "lang": "en" }],
            "md_covers": [{ "b2key": "pR5o5N.jpg" }],
            "external_ids": { "anilist": null, "mangaupdates": "rwg23en", "myanimelist": 12345 }
        });
        let m = parse_comic(&v);
        assert_eq!(m.id, "comick:yDm32aOJ");
        assert_eq!(m.source, "comick");
        assert_eq!(m.title, "The Beginning After the End");
        assert_eq!(m.alt_title.as_deref(), Some("TBATE"));
        assert_eq!(
            m.cover_override.as_deref(),
            Some("https://meo.comick.pictures/pR5o5N-m.jpg")
        );
        assert_eq!(m.links.get("mu").map(String::as_str), Some("rwg23en"));
        assert_eq!(m.links.get("mal").map(String::as_str), Some("12345"));
        assert_eq!(m.caption.as_deref(), Some("★ 9.3 · Manhwa · 2018"));
        assert_eq!(m.status.as_deref(), Some("hiatus"));
        assert_eq!(hid_of(&m), Some("yDm32aOJ"));
    }
}
