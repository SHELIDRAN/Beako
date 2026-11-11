//! A small, blocking MangaDex API client.
//!
//! Every call here runs on a background worker thread (see `jobs.rs`), never on the UI thread.

use std::collections::{BTreeMap, HashMap};
use std::sync::Arc;
use std::time::Duration;

use anyhow::{Context as _, Result, bail};
use serde::{Deserialize, Serialize};
use serde_json::{Value, json};

use crate::auth::Session;
use crate::search::{Search, Sort};

pub const API: &str = "https://api.mangadex.org";
pub const UPLOADS: &str = "https://uploads.mangadex.org";
const USER_AGENT: &str = concat!("Neko-Desktop/", env!("CARGO_PKG_VERSION"));

/// One page of results from a list endpoint.
#[derive(Clone, Debug)]
pub struct Page<T> {
    pub items: Vec<T>,
    pub total: u32,
}

/// What the rest of the app needs to know about a title.
#[derive(Clone, Debug, Default, Serialize, Deserialize, PartialEq)]
pub struct Manga {
    pub id: String,
    pub title: String,
    #[serde(default)]
    pub alt_title: Option<String>,
    #[serde(default)]
    pub description: String,
    #[serde(default)]
    pub authors: Vec<String>,
    #[serde(default)]
    pub artists: Vec<String>,
    #[serde(default)]
    pub cover_file: Option<String>,
    #[serde(default)]
    pub status: Option<String>,
    #[serde(default)]
    pub year: Option<u32>,
    #[serde(default)]
    pub original_language: Option<String>,
    #[serde(default)]
    pub content_rating: Option<String>,
    #[serde(default)]
    pub demographic: Option<String>,
    #[serde(default)]
    pub tags: Vec<String>,
    #[serde(default)]
    pub languages: Vec<String>,
    /// IDs on other sites, keyed by MangaDex's short names (`al`, `mal`, `mu`, `kt`, ...).
    #[serde(default)]
    pub links: BTreeMap<String, String>,
    /// A cover from another catalog (MangaBaka), used before the MangaDex cover is known.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub cover_override: Option<String>,
}

impl Manga {
    /// Cover thumbnail URL. MangaDex serves 256 px and 512 px wide variants.
    pub fn cover_url(&self, width: CoverSize) -> Option<String> {
        if let Some(url) = &self.cover_override {
            return Some(url.clone());
        }
        let file = self.cover_file.as_ref()?;
        let suffix = match width {
            CoverSize::Small => ".256.jpg",
            CoverSize::Large => ".512.jpg",
        };
        Some(format!("{UPLOADS}/covers/{}/{file}{suffix}", self.id))
    }

    pub fn byline(&self) -> String {
        let mut names: Vec<&str> = self.authors.iter().map(String::as_str).collect();
        for artist in &self.artists {
            if !names.contains(&artist.as_str()) {
                names.push(artist);
            }
        }
        names.join(", ")
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum CoverSize {
    Small,
    Large,
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
pub struct Chapter {
    pub id: String,
    pub chapter: Option<String>,
    pub volume: Option<String>,
    pub title: Option<String>,
    pub language: String,
    pub pages: u32,
    pub group: Option<String>,
    pub published: String,
    /// Some chapters are hosted by the publisher; they open in the browser.
    pub external_url: Option<String>,
}

impl Chapter {
    /// "Chapter 12", "Vol. 2 Chapter 12", or "Oneshot".
    pub fn short_name(&self) -> String {
        match (&self.volume, &self.chapter) {
            (_, Some(ch)) if !ch.is_empty() => format!("Chapter {ch}"),
            (Some(v), _) if !v.is_empty() => format!("Volume {v}"),
            _ => "Oneshot".to_owned(),
        }
    }

    /// The name with the chapter title, when it has one.
    pub fn display_name(&self) -> String {
        match self.title.as_deref().map(str::trim) {
            Some(t) if !t.is_empty() => format!("{} · {t}", self.short_name()),
            _ => self.short_name(),
        }
    }

    pub fn number(&self) -> Option<f64> {
        self.chapter.as_deref()?.parse().ok()
    }

    /// "Mar 4, 2024" from an RFC 3339 timestamp, without pulling in a date crate.
    pub fn published_date(&self) -> String {
        format_date(&self.published)
    }
}

pub fn format_date(rfc3339: &str) -> String {
    const MONTHS: [&str; 12] = [
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    ];
    let date = rfc3339.get(..10).unwrap_or("");
    let mut parts = date.split('-');
    let (Some(y), Some(m), Some(d)) = (parts.next(), parts.next(), parts.next()) else {
        return String::new();
    };
    let month = m
        .parse::<usize>()
        .ok()
        .and_then(|m| MONTHS.get(m.wrapping_sub(1)));
    match (month, d.parse::<u32>()) {
        (Some(month), Ok(day)) => format!("{month} {day}, {y}"),
        _ => date.to_owned(),
    }
}

/// The list a Browse page shows.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum Feed {
    Popular,
    LatestUpdates,
    NewTitles,
    TopRated,
}

impl Feed {
    pub const ALL: [Feed; 4] = [
        Feed::Popular,
        Feed::LatestUpdates,
        Feed::NewTitles,
        Feed::TopRated,
    ];

    pub fn label(self) -> &'static str {
        match self {
            Feed::Popular => "Popular",
            Feed::LatestUpdates => "Latest",
            Feed::NewTitles => "New",
            Feed::TopRated => "Top Rated",
        }
    }

    fn order(self) -> (&'static str, &'static str) {
        match self {
            Feed::Popular => ("order[followedCount]", "desc"),
            Feed::LatestUpdates => ("order[latestUploadedChapter]", "desc"),
            Feed::NewTitles => ("order[createdAt]", "desc"),
            Feed::TopRated => ("order[rating]", "desc"),
        }
    }
}

/// User-controlled filters that apply to every request.
#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
pub struct Filters {
    pub language: String,
    pub show_suggestive: bool,
    pub show_erotica: bool,
    pub data_saver: bool,
}

impl Default for Filters {
    fn default() -> Self {
        Self {
            language: "en".into(),
            show_suggestive: true,
            show_erotica: false,
            data_saver: false,
        }
    }
}

impl Filters {
    fn ratings(&self) -> Vec<(&'static str, String)> {
        let mut q = vec![("contentRating[]", "safe".to_owned())];
        if self.show_suggestive {
            q.push(("contentRating[]", "suggestive".to_owned()));
        }
        if self.show_erotica {
            q.push(("contentRating[]", "erotica".to_owned()));
        }
        q
    }
}

/// A MangaDex tag, used by the search filters.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct Tag {
    pub id: String,
    pub name: String,
    /// `genre`, `theme`, `format`, or `content`.
    pub group: String,
}

/// Reading statuses MangaDex keeps for signed-in people.
pub const READING_STATUSES: [(&str, &str); 6] = [
    ("reading", "Reading"),
    ("on_hold", "On Hold"),
    ("plan_to_read", "Plan to Read"),
    ("re_reading", "Re-reading"),
    ("completed", "Completed"),
    ("dropped", "Dropped"),
];

pub fn reading_status_label(status: &str) -> &str {
    READING_STATUSES
        .iter()
        .find(|(k, _)| *k == status)
        .map_or(status, |(_, l)| l)
}

pub struct Client {
    pub(crate) http: reqwest::blocking::Client,
    pub session: Arc<Session>,
}

impl Client {
    pub fn new() -> Result<Self> {
        let http = reqwest::blocking::Client::builder()
            .user_agent(USER_AGENT)
            .connect_timeout(Duration::from_secs(10))
            .timeout(Duration::from_secs(45))
            .build()?;
        let session = Arc::new(Session::load());
        Ok(Self { http, session })
    }

    /// A request on behalf of the signed-in person.
    fn authed(
        &self,
        method: reqwest::Method,
        url: &str,
        query: &[(&str, String)],
        body: Option<Value>,
    ) -> Result<Value> {
        let token = self.session.access_token(&self.http)?;
        let mut req = self.http.request(method, url).bearer_auth(token).query(query);
        if let Some(body) = body {
            req = req.json(&body);
        }
        let resp = req.send().context("Couldn't reach MangaDex")?;
        let status = resp.status();
        let text = resp.text().unwrap_or_default();
        let body: Value = serde_json::from_str(&text).unwrap_or(Value::Null);
        if !status.is_success() || body["result"] == "error" {
            let detail = body["errors"][0]["detail"].as_str().unwrap_or("");
            bail!(
                "MangaDex returned {status}{}",
                if detail.is_empty() {
                    String::new()
                } else {
                    format!(": {detail}")
                }
            );
        }
        Ok(body)
    }

    /// The signed-in person's username.
    pub fn me(&self) -> Result<String> {
        let body = self.authed(reqwest::Method::GET, &format!("{API}/user/me"), &[], None)?;
        Ok(body["data"]["attributes"]["username"]
            .as_str()
            .unwrap_or("")
            .to_owned())
    }

    /// Every title the signed-in person follows.
    pub fn follows(&self) -> Result<Vec<Manga>> {
        let mut out = Vec::new();
        let mut offset = 0u32;
        loop {
            let q = [
                ("limit", "100".to_owned()),
                ("offset", offset.to_string()),
                ("includes[]", "cover_art".to_owned()),
                ("includes[]", "author".to_owned()),
                ("includes[]", "artist".to_owned()),
            ];
            let body = self.authed(
                reqwest::Method::GET,
                &format!("{API}/user/follows/manga"),
                &q,
                None,
            )?;
            let batch: Vec<Manga> = body["data"]
                .as_array()
                .into_iter()
                .flatten()
                .map(parse_manga)
                .collect();
            let total = body["total"].as_u64().unwrap_or(0) as u32;
            offset += batch.len() as u32;
            let done = batch.is_empty() || offset >= total;
            out.extend(batch);
            if done {
                return Ok(out);
            }
        }
    }

    /// Reading status for every title that has one.
    pub fn reading_statuses(&self) -> Result<HashMap<String, String>> {
        let body = self.authed(reqwest::Method::GET, &format!("{API}/manga/status"), &[], None)?;
        Ok(body["statuses"]
            .as_object()
            .into_iter()
            .flatten()
            .filter_map(|(k, v)| v.as_str().map(|s| (k.clone(), s.to_owned())))
            .collect())
    }

    /// Sets or clears a title's reading status.
    pub fn set_reading_status(&self, manga_id: &str, status: Option<&str>) -> Result<()> {
        let body = json!({ "status": status });
        self.authed(
            reqwest::Method::POST,
            &format!("{API}/manga/{manga_id}/status"),
            &[],
            Some(body),
        )?;
        Ok(())
    }

    pub fn set_follow(&self, manga_id: &str, follow: bool) -> Result<()> {
        let method = if follow {
            reqwest::Method::POST
        } else {
            reqwest::Method::DELETE
        };
        self.authed(method, &format!("{API}/manga/{manga_id}/follow"), &[], None)?;
        Ok(())
    }

    /// Chapter IDs the signed-in person has read in a title.
    pub fn read_markers(&self, manga_id: &str) -> Result<Vec<String>> {
        let body = self.authed(
            reqwest::Method::GET,
            &format!("{API}/manga/{manga_id}/read"),
            &[],
            None,
        )?;
        Ok(body["data"]
            .as_array()
            .into_iter()
            .flatten()
            .filter_map(|v| v.as_str().map(str::to_owned))
            .collect())
    }

    pub fn set_read_markers(&self, manga_id: &str, read: &[String], unread: &[String]) -> Result<()> {
        let body = json!({ "chapterIdsRead": read, "chapterIdsUnread": unread });
        self.authed(
            reqwest::Method::POST,
            &format!("{API}/manga/{manga_id}/read"),
            &[],
            Some(body),
        )?;
        Ok(())
    }

    /// All MangaDex tags, for the search filters.
    pub fn tags(&self) -> Result<Vec<Tag>> {
        let body = self.get_json(&format!("{API}/manga/tag"), &[])?;
        let mut tags: Vec<Tag> = body["data"]
            .as_array()
            .into_iter()
            .flatten()
            .map(|t| Tag {
                id: t["id"].as_str().unwrap_or_default().to_owned(),
                name: t["attributes"]["name"]["en"]
                    .as_str()
                    .unwrap_or_default()
                    .to_owned(),
                group: t["attributes"]["group"].as_str().unwrap_or_default().to_owned(),
            })
            .collect();
        tags.sort_by(|a, b| a.name.cmp(&b.name));
        Ok(tags)
    }

    /// Finds MangaDex titles by name; used to open titles found on other catalogs.
    pub fn find_by_title(&self, title: &str) -> Result<Vec<Manga>> {
        let q = [
            ("title", title.to_owned()),
            ("limit", "20".to_owned()),
            ("includes[]", "cover_art".to_owned()),
            ("includes[]", "author".to_owned()),
            ("includes[]", "artist".to_owned()),
            ("order[relevance]", "desc".to_owned()),
            ("contentRating[]", "safe".to_owned()),
            ("contentRating[]", "suggestive".to_owned()),
            ("contentRating[]", "erotica".to_owned()),
            ("contentRating[]", "pornographic".to_owned()),
        ];
        let body = self.get_json(&format!("{API}/manga"), &q)?;
        Ok(body["data"]
            .as_array()
            .into_iter()
            .flatten()
            .map(parse_manga)
            .collect())
    }

    fn get_json(&self, url: &str, query: &[(&str, String)]) -> Result<Value> {
        let resp = self
            .http
            .get(url)
            .query(query)
            .send()
            .context("Couldn't reach MangaDex")?;
        let status = resp.status();
        let body: Value = resp
            .json()
            .context("MangaDex sent a response Neko couldn't read")?;
        if !status.is_success() || body["result"] == "error" {
            let detail = body["errors"][0]["detail"].as_str().unwrap_or("").to_owned();
            bail!(
                "MangaDex returned {status}{}",
                if detail.is_empty() {
                    String::new()
                } else {
                    format!(": {detail}")
                }
            );
        }
        Ok(body)
    }

    pub fn get_bytes(&self, url: &str) -> Result<Vec<u8>> {
        let resp = self.http.get(url).send()?;
        if !resp.status().is_success() {
            bail!("HTTP {} for {url}", resp.status());
        }
        Ok(resp.bytes()?.to_vec())
    }

    /// Browse a feed, or search when `query` isn't empty.
    pub fn list_manga(
        &self,
        feed: Feed,
        query: &str,
        offset: u32,
        filters: &Filters,
        search: &Search,
    ) -> Result<Page<Manga>> {
        let mut q: Vec<(&str, String)> = vec![
            ("limit", "32".into()),
            ("offset", offset.to_string()),
            ("includes[]", "cover_art".into()),
            ("includes[]", "author".into()),
            ("includes[]", "artist".into()),
            ("hasAvailableChapters", "true".into()),
            ("availableTranslatedLanguage[]", filters.language.clone()),
        ];
        q.extend(filters.ratings());
        search.apply(&mut q);
        let query = query.trim();
        if !query.is_empty() {
            q.push(("title", query.into()));
        }
        match (search.sort, query.is_empty()) {
            (Some(sort), _) if sort != Sort::Relevance || !query.is_empty() => {
                let (k, v) = sort.mangadex_order();
                q.push((k, v.into()));
            }
            (_, true) => {
                let (k, v) = feed.order();
                q.push((k, v.into()));
            }
            (_, false) => q.push(("order[relevance]", "desc".into())),
        }
        let body = self.get_json(&format!("{API}/manga"), &q)?;
        Ok(Page {
            items: body["data"]
                .as_array()
                .into_iter()
                .flatten()
                .map(parse_manga)
                .collect(),
            total: body["total"].as_u64().unwrap_or(0) as u32,
        })
    }

    pub fn manga(&self, id: &str) -> Result<Manga> {
        let q = [
            ("includes[]", "cover_art".to_owned()),
            ("includes[]", "author".to_owned()),
            ("includes[]", "artist".to_owned()),
        ];
        let body = self.get_json(&format!("{API}/manga/{id}"), &q)?;
        Ok(parse_manga(&body["data"]))
    }

    /// Every chapter in one language, oldest first. Duplicates from several groups are kept so
    /// people can pick their preferred translation.
    pub fn chapters(&self, manga_id: &str, filters: &Filters) -> Result<Vec<Chapter>> {
        let mut out = Vec::new();
        let mut offset = 0u32;
        loop {
            let mut q: Vec<(&str, String)> = vec![
                ("limit", "500".into()),
                ("offset", offset.to_string()),
                ("translatedLanguage[]", filters.language.clone()),
                ("order[volume]", "asc".into()),
                ("order[chapter]", "asc".into()),
                ("includes[]", "scanlation_group".into()),
            ];
            // The feed must not hide chapters of a title people already opened.
            for rating in ["safe", "suggestive", "erotica", "pornographic"] {
                q.push(("contentRating[]", rating.into()));
            }
            let body = self.get_json(&format!("{API}/manga/{manga_id}/feed"), &q)?;
            let batch = body["data"].as_array().cloned().unwrap_or_default();
            let total = body["total"].as_u64().unwrap_or(0) as u32;
            offset += batch.len() as u32;
            out.extend(batch.iter().map(parse_chapter));
            if batch.is_empty() || offset >= total {
                break;
            }
        }
        out.sort_by(|a, b| {
            let key = |c: &Chapter| c.number().unwrap_or(f64::MAX);
            key(a)
                .total_cmp(&key(b))
                .then_with(|| a.published.cmp(&b.published))
        });
        Ok(out)
    }

    /// Image URLs for a chapter's pages. The first list uses the MangaDex@Home server MangaDex
    /// assigns; the second is the origin server, used when a page fails from the first.
    pub fn page_urls(&self, chapter_id: &str, data_saver: bool) -> Result<(Vec<String>, Vec<String>)> {
        let body = self.get_json(&format!("{API}/at-home/server/{chapter_id}"), &[])?;
        let base = body["baseUrl"]
            .as_str()
            .unwrap_or(UPLOADS)
            .trim_end_matches('/')
            .to_owned();
        let hash = body["chapter"]["hash"].as_str().unwrap_or_default();
        let (key, dir) = if data_saver {
            ("dataSaver", "data-saver")
        } else {
            ("data", "data")
        };
        let files: Vec<&str> = body["chapter"][key]
            .as_array()
            .into_iter()
            .flatten()
            .filter_map(Value::as_str)
            .collect();
        if files.is_empty() {
            bail!("This chapter has no pages on MangaDex");
        }
        let build = |host: &str| {
            files
                .iter()
                .map(|f| format!("{host}/{dir}/{hash}/{f}"))
                .collect::<Vec<_>>()
        };
        Ok((build(&base), build(UPLOADS)))
    }
}

/// MangaDex localizes strings as `{ "en": "...", "ja-ro": "..." }`.
fn localized(map: &Value, preferred: &str) -> Option<String> {
    let obj = map.as_object()?;
    obj.get(preferred)
        .or_else(|| obj.get("en"))
        .or_else(|| obj.values().next())
        .and_then(Value::as_str)
        .map(str::to_owned)
}

pub fn parse_manga(v: &Value) -> Manga {
    let a = &v["attributes"];
    let titles = &a["title"];
    let alt_en = a["altTitles"]
        .as_array()
        .into_iter()
        .flatten()
        .find_map(|t| t.get("en").and_then(Value::as_str).map(str::to_owned));
    // Prefer an English title; MangaDex often stores the romanized title as the main one.
    let main = localized(titles, "en").unwrap_or_default();
    let (title, alt_title) = match (titles.get("en"), alt_en) {
        (Some(_), _) | (None, None) => (main, None),
        (None, Some(en)) => (en, Some(main)),
    };

    let mut authors = Vec::new();
    let mut artists = Vec::new();
    let mut cover_file = None;
    for rel in v["relationships"].as_array().into_iter().flatten() {
        let name = rel["attributes"]["name"].as_str().map(str::to_owned);
        match (rel["type"].as_str(), name) {
            (Some("author"), Some(n)) => authors.push(n),
            (Some("artist"), Some(n)) => artists.push(n),
            (Some("cover_art"), _) => {
                cover_file = rel["attributes"]["fileName"].as_str().map(str::to_owned);
            }
            _ => {}
        }
    }

    let tags = a["tags"]
        .as_array()
        .into_iter()
        .flatten()
        .filter_map(|t| t["attributes"]["name"]["en"].as_str().map(str::to_owned))
        .collect();
    let mut languages: Vec<String> = a["availableTranslatedLanguages"]
        .as_array()
        .into_iter()
        .flatten()
        .filter_map(|l| l.as_str().map(str::to_owned))
        .collect();
    languages.sort();
    languages.dedup();

    Manga {
        id: v["id"].as_str().unwrap_or_default().to_owned(),
        title,
        alt_title,
        description: localized(&a["description"], "en")
            .map(|d| clean_markdown(&d))
            .unwrap_or_default(),
        authors,
        artists,
        cover_file,
        status: a["status"].as_str().map(str::to_owned),
        year: a["year"].as_u64().map(|y| y as u32),
        original_language: a["originalLanguage"].as_str().map(str::to_owned),
        content_rating: a["contentRating"].as_str().map(str::to_owned),
        demographic: a["publicationDemographic"].as_str().map(str::to_owned),
        tags,
        languages,
        links: a["links"]
            .as_object()
            .into_iter()
            .flatten()
            .filter_map(|(k, v)| v.as_str().map(|s| (k.clone(), s.to_owned())))
            .collect(),
        cover_override: None,
    }
}

fn parse_chapter(v: &Value) -> Chapter {
    let a = &v["attributes"];
    let s = |key: &str| a[key].as_str().map(str::to_owned).filter(|s| !s.is_empty());
    let group = v["relationships"]
        .as_array()
        .into_iter()
        .flatten()
        .find(|r| r["type"] == "scanlation_group")
        .and_then(|r| r["attributes"]["name"].as_str())
        .map(str::to_owned);
    Chapter {
        id: v["id"].as_str().unwrap_or_default().to_owned(),
        chapter: s("chapter"),
        volume: s("volume"),
        title: s("title"),
        language: s("translatedLanguage").unwrap_or_default(),
        pages: a["pages"].as_u64().unwrap_or(0) as u32,
        group,
        published: s("readableAt").or_else(|| s("publishAt")).unwrap_or_default(),
        external_url: s("externalUrl"),
    }
}

/// Descriptions are Markdown with links and separators; show them as readable plain text.
fn clean_markdown(text: &str) -> String {
    let mut out = String::with_capacity(text.len());
    for line in text.lines() {
        let trimmed = line.trim();
        if trimmed.chars().all(|c| c == '-' || c == '_' || c == '*') && trimmed.len() >= 3 {
            break; // Everything after a horizontal rule is usually a list of links.
        }
        let mut rest = trimmed;
        while let Some(start) = rest.find('[') {
            let Some(mid) = rest[start..].find("](").map(|i| start + i) else {
                break;
            };
            let Some(end) = rest[mid..].find(')').map(|i| mid + i) else {
                break;
            };
            out.push_str(&rest[..start]);
            out.push_str(&rest[start + 1..mid]);
            rest = &rest[end + 1..];
        }
        out.push_str(rest);
        out.push('\n');
    }
    out.replace("**", "").replace("__", "").trim().to_owned()
}

/// English names for the languages people are most likely to pick.
pub fn language_name(code: &str) -> String {
    let names: BTreeMap<&str, &str> = [
        ("en", "English"),
        ("ja", "Japanese"),
        ("ja-ro", "Japanese (Romanized)"),
        ("ko", "Korean"),
        ("ko-ro", "Korean (Romanized)"),
        ("zh", "Chinese (Simplified)"),
        ("zh-hk", "Chinese (Traditional)"),
        ("es", "Spanish"),
        ("es-la", "Spanish (Latin America)"),
        ("fr", "French"),
        ("de", "German"),
        ("it", "Italian"),
        ("pt", "Portuguese"),
        ("pt-br", "Portuguese (Brazil)"),
        ("ru", "Russian"),
        ("uk", "Ukrainian"),
        ("pl", "Polish"),
        ("tr", "Turkish"),
        ("id", "Indonesian"),
        ("vi", "Vietnamese"),
        ("th", "Thai"),
        ("ar", "Arabic"),
        ("fa", "Persian"),
        ("he", "Hebrew"),
        ("hi", "Hindi"),
        ("nl", "Dutch"),
        ("sv", "Swedish"),
        ("cs", "Czech"),
        ("hu", "Hungarian"),
        ("ro", "Romanian"),
        ("el", "Greek"),
        ("bn", "Bengali"),
        ("ms", "Malay"),
        ("tl", "Filipino"),
        ("fi", "Finnish"),
        ("da", "Danish"),
        ("no", "Norwegian"),
        ("ca", "Catalan"),
        ("lt", "Lithuanian"),
        ("bg", "Bulgarian"),
        ("sr", "Serbian"),
        ("hr", "Croatian"),
        ("ta", "Tamil"),
        ("te", "Telugu"),
        ("my", "Burmese"),
        ("ka", "Georgian"),
        ("kk", "Kazakh"),
        ("mn", "Mongolian"),
        ("ne", "Nepali"),
        ("la", "Latin"),
    ]
    .into_iter()
    .collect();
    names
        .get(code)
        .map(|s| (*s).to_owned())
        .unwrap_or_else(|| code.to_uppercase())
}

pub fn status_label(status: &str) -> &str {
    match status {
        "ongoing" => "Ongoing",
        "completed" => "Completed",
        "hiatus" => "On Hiatus",
        "cancelled" => "Cancelled",
        other => other,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn prefers_english_alt_title() {
        let v: Value = serde_json::json!({
            "id": "abc",
            "attributes": {
                "title": { "ko-ro": "Na Honjaman Level-Up" },
                "altTitles": [{ "ko": "나 혼자만 레벨업" }, { "en": "Solo Leveling" }],
                "description": { "en": "Hunters. [Link](https://x.y)\n\n---\nlinks" },
                "tags": [{ "attributes": { "name": { "en": "Action" } } }],
                "availableTranslatedLanguages": ["fr", "en", "en"]
            },
            "relationships": [
                { "type": "author", "attributes": { "name": "Chugong" } },
                { "type": "artist", "attributes": { "name": "Chugong" } },
                { "type": "cover_art", "attributes": { "fileName": "c.jpg" } }
            ]
        });
        let m = parse_manga(&v);
        assert_eq!(m.title, "Solo Leveling");
        assert_eq!(m.alt_title.as_deref(), Some("Na Honjaman Level-Up"));
        assert_eq!(m.description, "Hunters. Link");
        assert_eq!(m.byline(), "Chugong");
        assert_eq!(m.languages, vec!["en", "fr"]);
        assert_eq!(
            m.cover_url(CoverSize::Small).unwrap(),
            "https://uploads.mangadex.org/covers/abc/c.jpg.256.jpg"
        );
    }

    #[test]
    fn chapter_names() {
        let mut c = Chapter {
            id: "1".into(),
            chapter: Some("12".into()),
            volume: Some("2".into()),
            title: Some("Rain".into()),
            language: "en".into(),
            pages: 20,
            group: None,
            published: "2024-03-04T10:00:00+00:00".into(),
            external_url: None,
        };
        assert_eq!(c.display_name(), "Chapter 12 · Rain");
        assert_eq!(c.published_date(), "Mar 4, 2024");
        c.chapter = None;
        c.title = None;
        assert_eq!(c.display_name(), "Volume 2");
        c.volume = None;
        assert_eq!(c.display_name(), "Oneshot");
    }
}
