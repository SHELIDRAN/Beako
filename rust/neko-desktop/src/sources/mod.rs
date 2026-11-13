//! Sources, modeled on Mangayomi's extension interface.
//!
//! Each source describes itself ([`SourceInfo`]), declares its filters as a generic
//! [`FilterList`], and answers the same questions: what's popular, what's new, and what matches a
//! search with those filters. It can also offer chart shelves, and it turns one of its titles
//! into a readable one ([`Source::open`]).
//!
//! Sources are compiled in rather than downloaded as code, so a source can't run anything the
//! app didn't ship with.

pub mod comick;
pub mod mangadex;

use anyhow::{Result, bail};

use crate::api::{Client, Filters, Manga};
use crate::filters::FilterList;

#[derive(Clone, Copy, Debug)]
pub struct SourceInfo {
    pub id: &'static str,
    pub name: &'static str,
    /// Language code, or "multi" for sources in many languages.
    pub lang: &'static str,
    pub description: &'static str,
    pub base_url: &'static str,
    pub supports_latest: bool,
    /// Where chapters are read from when it isn't the source itself.
    pub reads_from: Option<&'static str>,
    /// An accent for the source's icon.
    pub color: [u8; 3],
}

/// What to list: Mangayomi's Popular, Latest, and Search (with filters).
#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub enum Listing {
    Popular,
    Latest,
    Search(String),
}

pub struct Results {
    pub items: Vec<Manga>,
    pub has_next: bool,
}

/// A horizontally scrolling row on a source's home, such as a chart.
#[derive(Clone, Copy, Debug)]
pub struct ShelfInfo {
    pub id: &'static str,
    pub title: &'static str,
    /// Period choices in days, if the shelf has them.
    pub periods: &'static [(u32, &'static str)],
}

pub trait Source: Send + Sync {
    fn info(&self) -> SourceInfo;

    /// The filters this source understands. May fetch lists such as tags.
    fn filter_list(&self, client: &Client) -> Result<FilterList>;

    fn list(
        &self,
        client: &Client,
        listing: &Listing,
        page: u32,
        filters: &FilterList,
        prefs: &Filters,
    ) -> Result<Results>;

    fn shelves(&self) -> &'static [ShelfInfo] {
        &[]
    }

    fn shelf(&self, _client: &Client, id: &str, _period: u32, _prefs: &Filters) -> Result<Vec<Manga>> {
        bail!("Unknown shelf {id}")
    }

    /// The readable title for one of this source's titles: details with chapters available.
    fn open(&self, client: &Client, manga: &Manga) -> Result<Manga>;
}

static MANGADEX: mangadex::MangaDex = mangadex::MangaDex;
static COMICK: comick::Comick = comick::Comick;

pub fn all() -> [&'static dyn Source; 2] {
    [&MANGADEX, &COMICK]
}

pub fn get(id: &str) -> Option<&'static dyn Source> {
    let id = if id.is_empty() { "mangadex" } else { id };
    all().into_iter().find(|s| s.info().id == id)
}

/// "Boys' Love" and "Boys Love" are the same.
pub fn normalize(name: &str) -> String {
    name.to_lowercase()
        .replace(['\'', '’'], "")
        .replace('-', " ")
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ")
}

/// Whether two titles share an ID on AniList, MyAnimeList, MangaUpdates, or Kitsu.
pub fn links_match(a: &Manga, b: &Manga) -> bool {
    ["al", "mal", "mu", "kt"]
        .iter()
        .any(|k| match (a.links.get(*k), b.links.get(*k)) {
            (Some(x), Some(y)) => x.trim() == y.trim() && !x.trim().is_empty(),
            _ => false,
        })
}

pub fn titles_match(title: &str, m: &Manga) -> bool {
    let t = normalize(title);
    normalize(&m.title) == t || m.alt_title.as_deref().is_some_and(|a| normalize(a) == t)
}

/// Finds a title from another source on MangaDex, first by shared IDs, then by exact title.
pub fn find_on_mangadex(client: &Client, manga: &Manga) -> Result<Manga> {
    let mut titles = vec![manga.title.clone()];
    titles.extend(manga.alt_title.clone());
    for title in &titles {
        let candidates = client.find_by_title(title)?;
        if let Some(m) = candidates.iter().find(|m| links_match(m, manga)) {
            return Ok(m.clone());
        }
        if let Some(m) = candidates
            .iter()
            .find(|m| titles.iter().any(|t| titles_match(t, m)))
        {
            return Ok(m.clone());
        }
    }
    bail!("Couldn’t find “{}” on MangaDex", manga.title)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn registry_and_matching() {
        assert_eq!(get("").unwrap().info().id, "mangadex");
        assert_eq!(get("comick").unwrap().info().name, "Comick");
        assert!(get("mangabaka").is_none());

        let mut a = Manga {
            title: "Solo Leveling".into(),
            ..Default::default()
        };
        let mut b = Manga {
            title: "Na Honjaman Level-Up".into(),
            alt_title: Some("Solo  leveling".into()),
            ..Default::default()
        };
        assert!(titles_match(&a.title, &b));
        a.links.insert("mu".into(), "6z1uqw7".into());
        b.links.insert("mu".into(), "6z1uqw7".into());
        assert!(links_match(&a, &b));
        assert_eq!(normalize("Boys' Love"), normalize("Boys Love"));
    }
}
