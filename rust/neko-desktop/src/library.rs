//! The local library, reading progress and settings, saved as JSON in the user's app-data folder
//! (`%APPDATA%\Neko\Neko Desktop\data\library.json` on Windows).

use std::collections::{BTreeSet, HashMap};
use std::path::PathBuf;
use std::time::{SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};

use crate::api::{Chapter, Filters, Manga};
use crate::filters::FilterList;

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
pub struct Progress {
    /// The chapter to continue from.
    pub chapter_id: Option<String>,
    pub chapter_name: Option<String>,
    pub page: usize,
    pub read: BTreeSet<String>,
    pub updated: u64,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Entry {
    pub manga: Manga,
    pub added: u64,
}

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum ReadingMode {
    #[default]
    LeftToRight,
    RightToLeft,
    Vertical,
}

impl ReadingMode {
    pub const ALL: [ReadingMode; 3] = [
        ReadingMode::LeftToRight,
        ReadingMode::RightToLeft,
        ReadingMode::Vertical,
    ];

    pub fn label(self) -> &'static str {
        match self {
            ReadingMode::LeftToRight => "Left to Right",
            ReadingMode::RightToLeft => "Right to Left",
            ReadingMode::Vertical => "Vertical Scroll",
        }
    }
}

/// How a page is sized in the paged reading modes.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum Fit {
    #[default]
    Screen,
    Width,
    Height,
    Original,
}

impl Fit {
    pub const ALL: [Fit; 4] = [Fit::Screen, Fit::Width, Fit::Height, Fit::Original];

    pub fn label(self) -> &'static str {
        match self {
            Fit::Screen => "Fit Screen",
            Fit::Width => "Fit Width",
            Fit::Height => "Fit Height",
            Fit::Original => "Original Size",
        }
    }
}

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum PageLayout {
    #[default]
    Single,
    Double,
    /// Two pages side by side when the window is wider than it is tall.
    Automatic,
}

impl PageLayout {
    pub const ALL: [PageLayout; 3] = [PageLayout::Single, PageLayout::Double, PageLayout::Automatic];

    pub fn label(self) -> &'static str {
        match self {
            PageLayout::Single => "Single Page",
            PageLayout::Double => "Two Pages",
            PageLayout::Automatic => "Automatic",
        }
    }
}

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum Backdrop {
    #[default]
    Black,
    Gray,
    White,
}

impl Backdrop {
    pub const ALL: [Backdrop; 3] = [Backdrop::Black, Backdrop::Gray, Backdrop::White];

    pub fn label(self) -> &'static str {
        match self {
            Backdrop::Black => "Black",
            Backdrop::Gray => "Gray",
            Backdrop::White => "White",
        }
    }
}

/// Everything people can adjust about the reader.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct ReaderSettings {
    pub fit: Fit,
    pub layout: PageLayout,
    /// In two-page layouts, show the first page (the cover) on its own.
    pub cover_alone: bool,
    pub backdrop: Backdrop,
    /// Width of the vertical strip as a share of the window, 30–100 %.
    pub strip_width: u32,
    /// Space between pages in vertical scroll, in points.
    pub page_gap: u32,
    pub tap_zones: bool,
    pub invert_tap_zones: bool,
    pub wheel_turns_pages: bool,
    pub always_show_page_number: bool,
    /// Seconds before the controls fade; 0 keeps them visible.
    pub hide_controls_after: u32,
    pub preload: u32,
    pub crop_borders: bool,
    /// 0–80 % dimming for night reading.
    pub dim: u32,
    pub keep_awake: bool,
    pub auto_next_chapter: bool,
}

impl Default for ReaderSettings {
    fn default() -> Self {
        Self {
            fit: Fit::Screen,
            layout: PageLayout::Single,
            cover_alone: true,
            backdrop: Backdrop::Black,
            strip_width: 60,
            page_gap: 0,
            tap_zones: true,
            invert_tap_zones: false,
            wheel_turns_pages: true,
            always_show_page_number: true,
            hide_controls_after: 3,
            preload: 4,
            crop_borders: false,
            dim: 0,
            keep_awake: true,
            auto_next_chapter: true,
        }
    }
}

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
#[serde(default)]
pub struct Settings {
    pub filters: Filters,
    pub reading_mode: ReadingMode,
    pub reader: ReaderSettings,
    /// Chosen filters for each source, by source ID.
    pub source_filters: HashMap<String, FilterList>,
    pub pinned_sources: Vec<String>,
    pub last_source: Option<String>,
    /// Kept so people don't retype them; the secret lives in the credential store.
    pub mangadex_username: String,
    pub mangadex_client_id: String,
    /// Stop mirroring read chapters and library changes to MangaDex while signed in.
    pub pause_sync: bool,
    /// The public client ID of the person's Comick developer app.
    pub comick_client_id: String,
    /// Update Comick progress automatically as chapters are read.
    pub comick_auto_progress: Option<bool>,
}

impl Settings {
    pub fn comick_auto_progress(&self) -> bool {
        self.comick_auto_progress.unwrap_or(true)
    }
}

#[derive(Debug, Default, Serialize, Deserialize)]
#[serde(default)]
pub struct Library {
    pub entries: Vec<Entry>,
    pub progress: HashMap<String, Progress>,
    pub settings: Settings,
    /// MangaDex reading statuses, refreshed while signed in.
    pub statuses: HashMap<String, String>,
    /// Comick library entries for linked titles, keyed by MangaDex ID.
    pub comick: HashMap<String, crate::comick::LibraryEntry>,
    #[serde(skip)]
    path: Option<PathBuf>,
    #[serde(skip)]
    pub last_error: Option<String>,
}

pub fn now() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

impl Library {
    pub fn load(path: Option<PathBuf>) -> Self {
        let mut lib: Library = path
            .as_ref()
            .and_then(|p| std::fs::read(p).ok())
            .and_then(|bytes| serde_json::from_slice(&bytes).ok())
            .unwrap_or_default();
        lib.path = path;
        lib
    }

    pub fn save(&mut self) {
        let Some(path) = &self.path else { return };
        let result = (|| -> anyhow::Result<()> {
            if let Some(dir) = path.parent() {
                std::fs::create_dir_all(dir)?;
            }
            let tmp = path.with_extension("json.tmp");
            std::fs::write(&tmp, serde_json::to_vec_pretty(self)?)?;
            std::fs::rename(&tmp, path)?;
            Ok(())
        })();
        self.last_error = result.err().map(|e| format!("Couldn't save your library: {e}"));
    }

    pub fn contains(&self, manga_id: &str) -> bool {
        self.entries.iter().any(|e| e.manga.id == manga_id)
    }

    pub fn add(&mut self, manga: &Manga) {
        if !self.contains(&manga.id) {
            self.entries.push(Entry {
                manga: manga.clone(),
                added: now(),
            });
            self.save();
        }
    }

    pub fn remove(&mut self, manga_id: &str) {
        self.entries.retain(|e| e.manga.id != manga_id);
        self.save();
    }

    /// Keeps stored details (title, cover) current when a title is reopened.
    pub fn refresh(&mut self, manga: &Manga) {
        if let Some(e) = self.entries.iter_mut().find(|e| e.manga.id == manga.id)
            && e.manga != *manga
        {
            e.manga = manga.clone();
            self.save();
        }
    }

    pub fn progress(&self, manga_id: &str) -> Option<&Progress> {
        self.progress.get(manga_id)
    }

    /// Records the page people are on. Saves only when the chapter changes, so turning pages
    /// doesn't write to disk every time; [`Library::save`] runs on exit too.
    pub fn set_position(&mut self, manga_id: &str, chapter: &Chapter, page: usize) {
        let p = self.progress.entry(manga_id.to_owned()).or_default();
        let changed_chapter = p.chapter_id.as_deref() != Some(chapter.id.as_str());
        p.chapter_id = Some(chapter.id.clone());
        p.chapter_name = Some(chapter.short_name());
        p.page = page;
        p.updated = now();
        if changed_chapter {
            self.save();
        }
    }

    /// Merges read markers from MangaDex into local progress. Returns whether anything changed.
    pub fn merge_read(&mut self, manga_id: &str, ids: &[String]) -> bool {
        let p = self.progress.entry(manga_id.to_owned()).or_default();
        let before = p.read.len();
        p.read.extend(ids.iter().cloned());
        let changed = p.read.len() != before;
        if changed {
            self.save();
        }
        changed
    }

    pub fn set_read(&mut self, manga_id: &str, chapter_id: &str, read: bool) {
        let p = self.progress.entry(manga_id.to_owned()).or_default();
        let changed = if read {
            p.read.insert(chapter_id.to_owned())
        } else {
            p.read.remove(chapter_id)
        };
        if changed {
            self.save();
        }
    }

    /// Library titles, most recently read first, then most recently added.
    pub fn sorted_entries(&self) -> Vec<&Entry> {
        let mut list: Vec<&Entry> = self.entries.iter().collect();
        let last_read = |e: &Entry| self.progress.get(&e.manga.id).map_or(0, |p| p.updated);
        list.sort_by(|a, b| last_read(b).cmp(&last_read(a)).then(b.added.cmp(&a.added)));
        list
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn manga(id: &str) -> Manga {
        Manga {
            id: id.into(),
            title: format!("Title {id}"),
            ..Default::default()
        }
    }

    fn chapter(id: &str) -> Chapter {
        Chapter {
            id: id.into(),
            chapter: Some("1".into()),
            volume: None,
            title: None,
            language: "en".into(),
            pages: 10,
            group: None,
            published: String::new(),
            external_url: None,
        }
    }

    #[test]
    fn round_trips_through_disk() {
        let dir = std::env::temp_dir().join(format!("neko-test-{}", std::process::id()));
        let path = dir.join("library.json");
        let mut lib = Library::load(Some(path.clone()));
        lib.add(&manga("a"));
        lib.add(&manga("a"));
        lib.add(&manga("b"));
        lib.set_position("b", &chapter("c1"), 4);
        lib.set_read("b", "c1", true);

        let lib = Library::load(Some(path));
        assert_eq!(lib.entries.len(), 2);
        assert!(lib.progress("b").unwrap().read.contains("c1"));
        assert_eq!(lib.progress("b").unwrap().page, 4);
        assert_eq!(lib.sorted_entries()[0].manga.id, "b");
        std::fs::remove_dir_all(dir).ok();
    }
}
