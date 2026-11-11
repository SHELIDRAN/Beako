//! Search filters for the two catalogs Neko can browse:
//!
//! - **MangaDex**, filtered with the tag vocabulary Comix uses (genres, formats, demographics,
//!   statuses, and types), mapped onto MangaDex's own tags, plus MangaDex's themes.
//! - **MangaBaka**, a metadata database with richer filters (rating, chapter counts, anime
//!   adaptations, licensing). Its results are matched back to MangaDex to be read.

use std::collections::BTreeSet;

use anyhow::{Result, bail};
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::api::{Client, Filters, Manga, Page};

/// Comix's genres. The adult ones (Adult, Ecchi, Hentai, Mature, Smut) aren't tags on MangaDex;
/// they're covered by the content filters in Settings.
pub const COMIX_GENRES: [&str; 26] = [
    "Action",
    "Adventure",
    "Boys' Love",
    "Comedy",
    "Crime",
    "Drama",
    "Fantasy",
    "Girls' Love",
    "Harem",
    "Historical",
    "Horror",
    "Isekai",
    "Magical Girls",
    "Mecha",
    "Medical",
    "Mystery",
    "Philosophical",
    "Psychological",
    "Romance",
    "Sci-Fi",
    "Slice of Life",
    "Sports",
    "Superhero",
    "Thriller",
    "Tragedy",
    "Wuxia",
];

/// Comix's formats.
pub const COMIX_FORMATS: [&str; 9] = [
    "4-Koma",
    "Adaptation",
    "Anthology",
    "Award Winning",
    "Doujinshi",
    "Full Color",
    "Long Strip",
    "Oneshot",
    "Web Comic",
];

pub const DEMOGRAPHICS: [(&str, &str); 4] = [
    ("shounen", "Shounen"),
    ("shoujo", "Shoujo"),
    ("seinen", "Seinen"),
    ("josei", "Josei"),
];

pub const STATUSES: [(&str, &str); 4] = [
    ("ongoing", "Releasing"),
    ("completed", "Finished"),
    ("hiatus", "On Hiatus"),
    ("cancelled", "Discontinued"),
];

/// Comix's "types", which MangaDex expresses as the original language.
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize)]
pub enum Kind {
    Manga,
    Manhwa,
    Manhua,
    Other,
}

impl Kind {
    pub const ALL: [Kind; 4] = [Kind::Manga, Kind::Manhwa, Kind::Manhua, Kind::Other];

    pub fn label(self) -> &'static str {
        match self {
            Kind::Manga => "Manga",
            Kind::Manhwa => "Manhwa",
            Kind::Manhua => "Manhua",
            Kind::Other => "Other",
        }
    }

    fn languages(self) -> &'static [&'static str] {
        match self {
            Kind::Manga => &["ja"],
            Kind::Manhwa => &["ko"],
            Kind::Manhua => &["zh", "zh-hk"],
            Kind::Other => &["en", "fr", "es", "id", "vi", "th", "pt-br", "ru", "de", "it"],
        }
    }
}

/// Sort orders offered by Comix, mapped to MangaDex.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum Sort {
    Relevance,
    LatestUpdate,
    RecentlyAdded,
    TitleAsc,
    TitleDesc,
    YearNewest,
    YearOldest,
    HighestRated,
    MostFollowed,
}

impl Sort {
    pub const ALL: [Sort; 9] = [
        Sort::Relevance,
        Sort::LatestUpdate,
        Sort::RecentlyAdded,
        Sort::MostFollowed,
        Sort::HighestRated,
        Sort::TitleAsc,
        Sort::TitleDesc,
        Sort::YearNewest,
        Sort::YearOldest,
    ];

    pub fn label(self) -> &'static str {
        match self {
            Sort::Relevance => "Best Match",
            Sort::LatestUpdate => "Latest Update",
            Sort::RecentlyAdded => "Recently Added",
            Sort::TitleAsc => "Title (A–Z)",
            Sort::TitleDesc => "Title (Z–A)",
            Sort::YearNewest => "Year (Newest)",
            Sort::YearOldest => "Year (Oldest)",
            Sort::HighestRated => "Highest Rated",
            Sort::MostFollowed => "Most Followed",
        }
    }

    pub fn mangadex_order(self) -> (&'static str, &'static str) {
        match self {
            Sort::Relevance => ("order[relevance]", "desc"),
            Sort::LatestUpdate => ("order[latestUploadedChapter]", "desc"),
            Sort::RecentlyAdded => ("order[createdAt]", "desc"),
            Sort::TitleAsc => ("order[title]", "asc"),
            Sort::TitleDesc => ("order[title]", "desc"),
            Sort::YearNewest => ("order[year]", "desc"),
            Sort::YearOldest => ("order[year]", "asc"),
            Sort::HighestRated => ("order[rating]", "desc"),
            Sort::MostFollowed => ("order[followedCount]", "desc"),
        }
    }
}

/// Tri-state for tags: ignored, required, or excluded.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum TagState {
    #[default]
    Any,
    Include,
    Exclude,
}

impl TagState {
    pub fn next(self) -> Self {
        match self {
            TagState::Any => TagState::Include,
            TagState::Include => TagState::Exclude,
            TagState::Exclude => TagState::Any,
        }
    }
}

/// Filters for the MangaDex catalog. Tags are stored by name so they survive tag-ID lookups.
#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct Search {
    pub include: BTreeSet<String>,
    pub exclude: BTreeSet<String>,
    /// Require every included tag (AND) instead of any (OR).
    pub match_all: bool,
    pub kinds: BTreeSet<Kind>,
    pub statuses: BTreeSet<String>,
    pub demographics: BTreeSet<String>,
    pub year: Option<u32>,
    pub sort: Option<Sort>,
    /// Tag name → MangaDex ID, filled once the tag list loads. Not saved.
    #[serde(skip)]
    pub tag_ids: Vec<(String, String)>,
}

impl Search {
    pub fn tag_state(&self, name: &str) -> TagState {
        if self.include.contains(name) {
            TagState::Include
        } else if self.exclude.contains(name) {
            TagState::Exclude
        } else {
            TagState::Any
        }
    }

    pub fn set_tag(&mut self, name: &str, state: TagState) {
        self.include.remove(name);
        self.exclude.remove(name);
        match state {
            TagState::Include => {
                self.include.insert(name.to_owned());
            }
            TagState::Exclude => {
                self.exclude.insert(name.to_owned());
            }
            TagState::Any => {}
        }
    }

    /// How many filters are active, for the badge on the Filters button.
    pub fn active_count(&self) -> usize {
        self.include.len()
            + self.exclude.len()
            + self.kinds.len()
            + self.statuses.len()
            + self.demographics.len()
            + usize::from(self.year.is_some())
            + usize::from(self.sort.is_some())
    }

    pub fn clear(&mut self) {
        let ids = std::mem::take(&mut self.tag_ids);
        *self = Search {
            tag_ids: ids,
            ..Default::default()
        };
    }

    fn id_of(&self, name: &str) -> Option<&str> {
        let norm = normalize(name);
        self.tag_ids
            .iter()
            .find(|(n, _)| normalize(n) == norm)
            .map(|(_, id)| id.as_str())
    }

    pub fn apply(&self, q: &mut Vec<(&'static str, String)>) {
        for name in &self.include {
            if let Some(id) = self.id_of(name) {
                q.push(("includedTags[]", id.to_owned()));
            }
        }
        for name in &self.exclude {
            if let Some(id) = self.id_of(name) {
                q.push(("excludedTags[]", id.to_owned()));
            }
        }
        if !self.include.is_empty() {
            q.push((
                "includedTagsMode",
                if self.match_all { "AND" } else { "OR" }.into(),
            ));
        }
        for kind in &self.kinds {
            for lang in kind.languages() {
                q.push(("originalLanguage[]", (*lang).to_owned()));
            }
        }
        for s in &self.statuses {
            q.push(("status[]", s.clone()));
        }
        for d in &self.demographics {
            q.push(("publicationDemographic[]", d.clone()));
        }
        if let Some(y) = self.year {
            q.push(("year", y.to_string()));
        }
    }
}

/// "Boys' Love" and Comix's "Boys Love" are the same tag.
pub fn normalize(name: &str) -> String {
    name.to_lowercase().replace(['\'', '’'], "").replace('-', " ")
}

// ---------------------------------------------------------------------------------------------
// MangaBaka

pub const BAKA_API: &str = "https://api.mangabaka.org/v1";

pub const BAKA_TYPES: [(&str, &str); 6] = [
    ("manga", "Manga"),
    ("manhwa", "Manhwa"),
    ("manhua", "Manhua"),
    ("novel", "Novel"),
    ("oel", "OEL"),
    ("other", "Other"),
];

pub const BAKA_STATUSES: [(&str, &str); 5] = [
    ("releasing", "Releasing"),
    ("completed", "Completed"),
    ("hiatus", "On Hiatus"),
    ("cancelled", "Cancelled"),
    ("upcoming", "Upcoming"),
];

pub const BAKA_GENRES: [(&str, &str); 36] = [
    ("action", "Action"),
    ("adventure", "Adventure"),
    ("avant_garde", "Avant Garde"),
    ("award_winning", "Award Winning"),
    ("boys_love", "Boys Love"),
    ("comedy", "Comedy"),
    ("doujinshi", "Doujinshi"),
    ("drama", "Drama"),
    ("fantasy", "Fantasy"),
    ("gender_bender", "Gender Bender"),
    ("girls_love", "Girls Love"),
    ("gourmet", "Gourmet"),
    ("harem", "Harem"),
    ("historical", "Historical"),
    ("horror", "Horror"),
    ("josei", "Josei"),
    ("mahou_shoujo", "Mahou Shoujo"),
    ("martial_arts", "Martial Arts"),
    ("mecha", "Mecha"),
    ("music", "Music"),
    ("mystery", "Mystery"),
    ("psychological", "Psychological"),
    ("romance", "Romance"),
    ("school_life", "School Life"),
    ("sci-fi", "Sci-Fi"),
    ("seinen", "Seinen"),
    ("shoujo", "Shoujo"),
    ("shounen", "Shounen"),
    ("slice_of_life", "Slice of Life"),
    ("sports", "Sports"),
    ("supernatural", "Supernatural"),
    ("suspense", "Suspense"),
    ("thriller", "Thriller"),
    ("tragedy", "Tragedy"),
    ("yaoi", "Yaoi"),
    ("yuri", "Yuri"),
];

pub const BAKA_SORTS: [(&str, &str); 9] = [
    ("popularity_asc", "Most Popular"),
    ("trending_7d", "Trending This Week"),
    ("trending_30d", "Trending This Month"),
    ("score_desc", "Highest Rated"),
    ("latest", "Recently Updated"),
    ("published_start_date_desc", "Newest"),
    ("chapters_desc", "Most Chapters"),
    ("name_asc", "Title (A–Z)"),
    ("random", "Surprise Me"),
];

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct BakaSearch {
    pub sort: String,
    pub types: BTreeSet<String>,
    pub statuses: BTreeSet<String>,
    pub include: BTreeSet<String>,
    pub exclude: BTreeSet<String>,
    pub min_rating: u32,
    pub min_chapters: u32,
    pub year_from: Option<u32>,
    pub year_to: Option<u32>,
    pub has_anime: Option<bool>,
    pub licensed: Option<bool>,
}

impl Default for BakaSearch {
    fn default() -> Self {
        Self {
            sort: "popularity_asc".into(),
            types: BTreeSet::new(),
            statuses: BTreeSet::new(),
            include: BTreeSet::new(),
            exclude: BTreeSet::new(),
            min_rating: 0,
            min_chapters: 0,
            year_from: None,
            year_to: None,
            has_anime: None,
            licensed: None,
        }
    }
}

impl BakaSearch {
    pub fn active_count(&self) -> usize {
        let d = BakaSearch::default();
        self.types.len()
            + self.statuses.len()
            + self.include.len()
            + self.exclude.len()
            + usize::from(self.min_rating > 0)
            + usize::from(self.min_chapters > 0)
            + usize::from(self.year_from.is_some() || self.year_to.is_some())
            + usize::from(self.has_anime.is_some())
            + usize::from(self.licensed.is_some())
            + usize::from(self.sort != d.sort)
    }

    pub fn genre_state(&self, key: &str) -> TagState {
        if self.include.contains(key) {
            TagState::Include
        } else if self.exclude.contains(key) {
            TagState::Exclude
        } else {
            TagState::Any
        }
    }

    pub fn set_genre(&mut self, key: &str, state: TagState) {
        self.include.remove(key);
        self.exclude.remove(key);
        match state {
            TagState::Include => {
                self.include.insert(key.to_owned());
            }
            TagState::Exclude => {
                self.exclude.insert(key.to_owned());
            }
            TagState::Any => {}
        }
    }
}

/// Which catalog an outside title came from.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub enum Origin {
    #[default]
    MangaBaka,
    Comick,
}

impl Origin {
    pub fn prefix(self) -> &'static str {
        match self {
            Origin::MangaBaka => "mangabaka",
            Origin::Comick => "comick",
        }
    }
}

/// A title from MangaBaka or Comick, with the IDs used to find it on MangaDex.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct BakaSeries {
    pub id: u64,
    pub origin: Origin,
    /// Comick's short ID, used by its library API.
    pub hid: Option<String>,
    pub title: String,
    pub native_title: Option<String>,
    pub romanized_title: Option<String>,
    pub cover: Option<String>,
    pub authors: Vec<String>,
    pub year: Option<u32>,
    pub kind: Option<String>,
    pub rating: Option<f64>,
    pub anilist: Option<String>,
    pub mal: Option<String>,
    pub mangaupdates: Option<String>,
    pub kitsu: Option<String>,
}

impl BakaSeries {
    /// A placeholder `Manga` so MangaBaka results can share the cover grid.
    pub fn as_manga(&self) -> Manga {
        Manga {
            id: format!("{}:{}", self.origin.prefix(), self.id),
            title: self.title.clone(),
            authors: self.authors.clone(),
            year: self.year,
            cover_override: self.cover.clone(),
            ..Default::default()
        }
    }

    pub fn caption(&self) -> String {
        let mut parts = Vec::new();
        if let Some(r) = self.rating {
            parts.push(format!("★ {:.1}", r / 10.0));
        }
        if let Some(k) = &self.kind {
            let label = BAKA_TYPES
                .iter()
                .find(|(key, _)| key == k)
                .map_or(k.as_str(), |(_, l)| l);
            parts.push(label.to_owned());
        }
        if let Some(y) = self.year {
            parts.push(y.to_string());
        }
        parts.join(" · ")
    }
}

fn parse_baka(v: &Value) -> BakaSeries {
    let id_of = |site: &str| -> Option<String> {
        match &v["source"][site]["id"] {
            Value::String(s) if !s.is_empty() => Some(s.clone()),
            Value::Number(n) => Some(n.to_string()),
            _ => None,
        }
    };
    let s = |k: &str| v[k].as_str().map(str::to_owned).filter(|s| !s.is_empty());
    BakaSeries {
        id: v["id"].as_u64().unwrap_or(0),
        origin: Origin::MangaBaka,
        hid: None,
        title: s("title").unwrap_or_default(),
        native_title: s("native_title"),
        romanized_title: s("romanized_title"),
        cover: v["cover"]["x250"]["x2"]
            .as_str()
            .or_else(|| v["cover"]["x250"]["x1"].as_str())
            .or_else(|| v["cover"]["raw"]["url"].as_str())
            .map(str::to_owned),
        authors: v["authors"]
            .as_array()
            .into_iter()
            .flatten()
            .filter_map(|a| a.as_str().map(str::to_owned))
            .collect(),
        year: v["year"].as_u64().map(|y| y as u32),
        kind: s("type"),
        rating: v["rating"].as_f64(),
        anilist: id_of("anilist"),
        mal: id_of("my_anime_list"),
        mangaupdates: id_of("manga_updates"),
        kitsu: id_of("kitsu"),
    }
}

impl Client {
    pub fn baka_search(
        &self,
        query: &str,
        page: u32,
        search: &BakaSearch,
        filters: &Filters,
    ) -> Result<Page<BakaSeries>> {
        let mut q: Vec<(&str, String)> = vec![("limit", "30".into()), ("page", page.to_string())];
        let query = query.trim();
        if !query.is_empty() {
            q.push(("q", query.into()));
        }
        let sort = if !query.is_empty() && search.sort == BakaSearch::default().sort {
            "relevance_desc".to_owned()
        } else {
            search.sort.clone()
        };
        q.push(("sort_by", sort));
        for t in &search.types {
            q.push(("type", t.clone()));
        }
        for s in &search.statuses {
            q.push(("status", s.clone()));
        }
        for g in &search.include {
            q.push(("genre", g.clone()));
        }
        for g in &search.exclude {
            q.push(("genre_not", g.clone()));
        }
        q.push(("content_rating", "safe".into()));
        if filters.show_suggestive {
            q.push(("content_rating", "suggestive".into()));
        }
        if filters.show_erotica {
            q.push(("content_rating", "erotica".into()));
        }
        if search.min_rating > 0 {
            q.push(("rating_lower", search.min_rating.to_string()));
        }
        if search.min_chapters > 0 {
            q.push(("chapters_lower", search.min_chapters.to_string()));
        }
        if let Some(y) = search.year_from {
            q.push(("year_lower", y.to_string()));
        }
        if let Some(y) = search.year_to {
            q.push(("year_upper", y.to_string()));
        }
        if let Some(a) = search.has_anime {
            q.push(("has_anime", a.to_string()));
        }
        if let Some(l) = search.licensed {
            q.push(("is_licensed", l.to_string()));
        }
        let resp = self
            .http
            .get(format!("{BAKA_API}/series/search"))
            .query(&q)
            .send()?;
        let status = resp.status();
        let body: Value = resp.json().unwrap_or(Value::Null);
        if !status.is_success() {
            let msg = body["message"].as_str().unwrap_or("");
            bail!(
                "MangaBaka returned {status}{}",
                if msg.is_empty() {
                    String::new()
                } else {
                    format!(": {msg}")
                }
            );
        }
        let items: Vec<BakaSeries> = body["data"]
            .as_array()
            .into_iter()
            .flatten()
            .map(parse_baka)
            .collect();
        let total = body["pagination"]["count"].as_u64().unwrap_or(0).min(12_000) as u32;
        Ok(Page { items, total })
    }

    /// Finds the MangaDex title for a MangaBaka series: first by shared AniList, MyAnimeList,
    /// MangaUpdates, or Kitsu IDs, then by an exact title match.
    pub fn resolve_baka(&self, series: &BakaSeries) -> Result<Manga> {
        let mut titles = vec![series.title.clone()];
        titles.extend(series.romanized_title.clone());
        titles.extend(series.native_title.clone());
        titles.dedup();
        for title in &titles {
            let candidates = self.find_by_title(title)?;
            if let Some(m) = candidates.iter().find(|m| links_match(m, series)) {
                return Ok(m.clone());
            }
            let norm = normalize(title);
            if let Some(m) = candidates.iter().find(|m| {
                normalize(&m.title) == norm || m.alt_title.as_deref().is_some_and(|a| normalize(a) == norm)
            }) {
                return Ok(m.clone());
            }
        }
        bail!("Couldn’t find “{}” on MangaDex", series.title)
    }
}

fn links_match(m: &Manga, s: &BakaSeries) -> bool {
    let eq = |key: &str, id: &Option<String>| match (m.links.get(key), id) {
        (Some(a), Some(b)) => a.trim() == b.trim(),
        _ => false,
    };
    eq("al", &s.anilist) || eq("mal", &s.mal) || eq("mu", &s.mangaupdates) || eq("kt", &s.kitsu)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn maps_filters_to_mangadex_query() {
        let mut s = Search {
            tag_ids: vec![("Boys' Love".into(), "bl".into()), ("Isekai".into(), "is".into())],
            ..Default::default()
        };
        s.set_tag("Boys Love", TagState::Include);
        s.set_tag("Isekai", TagState::Exclude);
        s.kinds.insert(Kind::Manhwa);
        s.statuses.insert("completed".into());
        let mut q = Vec::new();
        s.apply(&mut q);
        assert!(q.contains(&("includedTags[]", "bl".into())));
        assert!(q.contains(&("excludedTags[]", "is".into())));
        assert!(q.contains(&("includedTagsMode", "OR".into())));
        assert!(q.contains(&("originalLanguage[]", "ko".into())));
        assert!(q.contains(&("status[]", "completed".into())));
        assert_eq!(s.active_count(), 4);
        s.set_tag("Isekai", TagState::Exclude.next());
        assert_eq!(s.tag_state("Isekai"), TagState::Any);
    }

    #[test]
    fn parses_mangabaka_series_and_matches_links() {
        let v: Value = serde_json::json!({
            "id": 7, "title": "Solo Leveling", "type": "manhwa", "rating": 86.1, "year": 2018,
            "authors": ["Chugong"], "cover": { "x250": { "x1": "a", "x2": "b" } },
            "source": { "anilist": { "id": 105398 }, "manga_updates": { "id": "6z1uqw7" }, "my_anime_list": { "id": null } }
        });
        let s = parse_baka(&v);
        assert_eq!(s.cover.as_deref(), Some("b"));
        assert_eq!(s.anilist.as_deref(), Some("105398"));
        assert_eq!(s.mal, None);
        assert_eq!(s.caption(), "★ 8.6 · Manhwa · 2018");
        let mut m = Manga::default();
        m.links.insert("al".into(), "105398".into());
        assert!(links_match(&m, &s));
        assert_eq!(s.as_manga().id, "mangabaka:7");
    }
}
