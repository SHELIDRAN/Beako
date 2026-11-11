//! The local library, reading progress and settings, saved as JSON in the user's app-data folder
//! (`%APPDATA%\Neko\Neko Desktop\data\library.json` on Windows).

use std::collections::{BTreeSet, HashMap};
use std::path::PathBuf;
use std::time::{SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};

use crate::api::{Chapter, Filters, Manga};

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

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
#[serde(default)]
pub struct Settings {
    pub filters: Filters,
    pub reading_mode: ReadingMode,
}

#[derive(Debug, Default, Serialize, Deserialize)]
#[serde(default)]
pub struct Library {
    pub entries: Vec<Entry>,
    pub progress: HashMap<String, Progress>,
    pub settings: Settings,
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
        self.last_error = result
            .err()
            .map(|e| format!("Couldn't save your library: {e}"));
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
