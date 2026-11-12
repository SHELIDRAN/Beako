//! App state, navigation, and the window shell (sidebar + content area).

use std::collections::{HashMap, HashSet};

use eframe::egui::{self, Color32, CornerRadius, Frame, Key, Margin, Rect, Stroke, pos2, vec2};

use crate::api::{Chapter, Manga};
use crate::auth::Credentials;
use crate::filters::FilterList;
use crate::images::Images;
use crate::jobs::{Event, Jobs};
use crate::library::Library;
use crate::reader::Reader;
use crate::sources::{self, Listing};
use crate::theme::{self, Palette, space, text};
use crate::widgets::{self, Icon};

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Section {
    Library,
    Browse,
    Settings,
}

/// Something that loads in the background.
#[derive(Clone, Debug, Default)]
pub enum Loadable<T> {
    #[default]
    Idle,
    Loading,
    Ready(T),
    Failed(String),
}

impl<T> Loadable<T> {
    pub fn ready(&self) -> Option<&T> {
        match self {
            Loadable::Ready(v) => Some(v),
            _ => None,
        }
    }
}

pub struct BrowseState {
    /// The open source, or `None` for the list of sources.
    pub source: Option<&'static str>,
    /// Popular or Latest; searching and filtering switch to a search listing.
    pub tab: Listing,
    pub query: String,
    /// The query the current results are for.
    pub results_query: String,
    pub items: Vec<Manga>,
    pub page: u32,
    pub has_next: bool,
    pub loading: bool,
    pub error: Option<String>,
    pub request: u64,
    pub last_edit: Option<f64>,
    pub focus_search: bool,
    pub show_filters: bool,
    /// The title being opened through its source.
    pub opening: Option<String>,
}

/// A search across every source.
#[derive(Default)]
pub struct GlobalSearch {
    pub query: String,
    /// The query the results are for; empty means no search is shown.
    pub submitted: String,
    pub request: u64,
    pub results: HashMap<&'static str, Loadable<Vec<Manga>>>,
}

pub struct Details {
    pub manga: Manga,
    pub details_loaded: bool,
    pub newest_first: bool,
    pub expanded: bool,
}

/// The sign-in form in Settings. The password is cleared after every attempt.
#[derive(Default)]
pub struct AccountState {
    pub username: Option<String>,
    pub busy: bool,
    pub syncing: bool,
    pub error: Option<String>,
    pub form: Credentials,
    pub last_sync: Option<String>,
}

/// User intents collected while drawing a frame and applied after it, which keeps the drawing
/// code free of borrow juggling.
pub enum Action {
    Section(Section),
    OpenManga(Manga),
    Back,
    Read {
        manga: Manga,
        chapter_id: String,
        page: usize,
    },
    ToggleLibrary(Manga),
    SetRead {
        manga_id: String,
        chapter_id: String,
        read: bool,
    },
    MarkPreviousRead {
        manga_id: String,
        chapter_id: String,
    },
    SetStatus {
        manga_id: String,
        status: Option<String>,
    },
    LoadMoreBrowse,
    ReloadBrowse,
    OpenSource(&'static str, Listing),
    CloseSource,
    TogglePin(&'static str),
    GlobalSearch(String),
    ShelfPeriod(&'static str, &'static str, u32),
    /// Saves a source's filters and reloads its results.
    SourceFiltersChanged(&'static str),
    ReloadChapters(String),
    OpenUrl(String),
    CloseReader,
    FiltersChanged,
    SignIn,
    SignOut,
    Sync,
    Notice(String),
    ComickConnect,
    ComickDisconnect,
    ComickLink(Manga),
    ComickSave {
        manga_id: String,
        entry: crate::comick::LibraryEntry,
    },
    ComickUnlink(String),
}

/// Comick's catalog data and the tracker connection.
#[derive(Default)]
pub struct ComickState {
    pub username: Option<String>,
    pub busy: bool,
    pub error: Option<String>,
    /// Titles being matched to Comick.
    pub linking: HashSet<String>,
}

/// A shelf's chosen period and its titles.
pub type ShelfState = (u32, Loadable<Vec<Manga>>);

pub struct App {
    pub jobs: Jobs,
    pub images: Images,
    pub library: Library,
    pub section: Section,
    pub details: Option<Details>,
    pub reader: Option<Reader>,
    pub browse: BrowseState,
    pub global: GlobalSearch,
    /// Each source's declared filters, with saved choices applied.
    pub source_filters: HashMap<&'static str, Loadable<FilterList>>,
    pub shelves: HashMap<(&'static str, &'static str), ShelfState>,
    pub account: AccountState,
    pub comick: ComickState,
    /// Chapter lists keyed by manga id; each knows the language it was fetched in.
    pub chapters: HashMap<String, (String, Loadable<Vec<Chapter>>)>,
    pub actions: Vec<Action>,
    notice: Option<(String, f64)>,
    pub data_dir: Option<std::path::PathBuf>,
    /// Whether the window has a system backdrop (Mica) behind the sidebar.
    pub vibrancy: bool,
}

impl App {
    pub fn new(cc: &eframe::CreationContext<'_>) -> Self {
        theme::install(&cc.egui_ctx);
        // The app follows the system appearance. This override exists for screenshots and testing.
        match std::env::var("NEKO_APPEARANCE").as_deref() {
            Ok("light") => cc.egui_ctx.set_theme(egui::Theme::Light),
            Ok("dark") => cc.egui_ctx.set_theme(egui::Theme::Dark),
            _ => {}
        }
        let vibrancy = crate::glass::apply_window_material(cc);
        let dirs = directories::ProjectDirs::from("org", "Neko", "Neko Desktop");
        let data_dir = dirs.as_ref().map(|d| d.data_dir().to_path_buf());
        let cache_dir = dirs.as_ref().map(|d| d.cache_dir().to_path_buf());
        let library = Library::load(data_dir.as_ref().map(|d| d.join("library.json")));
        let jobs = Jobs::new(cc.egui_ctx.clone(), cache_dir).expect("couldn't start the network client");
        let section = if library.entries.is_empty() {
            Section::Browse
        } else {
            Section::Library
        };
        let username = jobs.signed_in_as();
        let form = Credentials {
            username: library.settings.mangadex_username.clone(),
            client_id: library.settings.mangadex_client_id.clone(),
            ..Default::default()
        };
        let mut app = Self {
            jobs,
            images: Images::default(),
            library,
            section,
            details: None,
            reader: None,
            browse: BrowseState {
                source: None,
                tab: Listing::Popular,
                query: String::new(),
                results_query: String::new(),
                items: Vec::new(),
                page: 0,
                has_next: false,
                loading: false,
                error: None,
                request: 0,
                last_edit: None,
                focus_search: false,
                show_filters: false,
                opening: None,
            },
            global: GlobalSearch::default(),
            source_filters: HashMap::new(),
            shelves: HashMap::new(),
            comick: ComickState::default(),
            account: AccountState {
                username: username.clone(),
                form,
                ..Default::default()
            },
            chapters: HashMap::new(),
            actions: Vec::new(),
            notice: None,
            data_dir,
            vibrancy,
        };
        app.comick.username = app.jobs.comick_user();
        if username.is_some() && !app.library.settings.pause_sync {
            app.account.syncing = true;
            app.jobs.sync();
        }
        app
    }

    pub fn signed_in(&self) -> bool {
        self.account.username.is_some()
    }

    fn syncing_enabled(&self) -> bool {
        self.signed_in() && !self.library.settings.pause_sync
    }

    /// The listing the open source should show: a search when there's a query or filters.
    fn current_listing(&self, source: &str) -> Listing {
        let filtering = self.active_filters(source) > 0;
        let q = self.browse.query.trim();
        if !q.is_empty() || filtering {
            Listing::Search(q.to_owned())
        } else {
            self.browse.tab.clone()
        }
    }

    pub fn active_filters(&self, source: &str) -> usize {
        self.source_filters
            .get(source)
            .and_then(Loadable::ready)
            .map_or(0, FilterList::active_count)
    }

    pub fn load_browse(&mut self, append: bool) {
        let Some(source) = self.browse.source else { return };
        let listing = self.current_listing(source);
        let filters = self
            .source_filters
            .get(source)
            .and_then(Loadable::ready)
            .cloned()
            .unwrap_or_default();
        let b = &mut self.browse;
        b.request += 1;
        b.loading = true;
        b.error = None;
        if !append {
            b.items.clear();
            b.page = 0;
            b.has_next = false;
            b.results_query = b.query.trim().to_owned();
        }
        b.page += 1;
        self.jobs.source_list(
            b.request,
            source,
            listing,
            b.page,
            filters,
            self.library.settings.filters.clone(),
        );
    }

    /// Opens a source: loads its filters and shelves the first time, then its list.
    fn open_source(&mut self, id: &'static str, tab: Listing) {
        let Some(source) = sources::get(id) else { return };
        let id = source.info().id;
        self.browse.source = Some(id);
        self.browse.tab = tab;
        self.browse.show_filters = false;
        if let Listing::Search(q) = &self.browse.tab {
            self.browse.query = q.clone();
            self.browse.tab = Listing::Popular;
        } else {
            self.browse.query.clear();
        }
        self.library.settings.last_source = Some(id.to_owned());
        self.library.save();
        if !self.source_filters.contains_key(id) {
            self.source_filters.insert(id, Loadable::Loading);
            self.jobs.source_filters(id);
        }
        for shelf in source.shelves() {
            if !self.shelves.contains_key(&(id, shelf.id)) {
                let period = shelf.periods.first().map_or(0, |(d, _)| *d);
                self.load_shelf(id, shelf.id, period);
            }
        }
        self.load_browse(false);
    }

    fn load_shelf(&mut self, source: &'static str, shelf: &'static str, period: u32) {
        self.shelves.insert((source, shelf), (period, Loadable::Loading));
        self.jobs
            .shelf(source, shelf, period, self.library.settings.filters.clone());
    }

    pub fn ensure_chapters(&mut self, manga_id: &str, force: bool) {
        let language = self.library.settings.filters.language.clone();
        let stale = match self.chapters.get(manga_id) {
            None => true,
            Some((lang, state)) => force || *lang != language || matches!(state, Loadable::Idle),
        };
        if stale {
            self.chapters
                .insert(manga_id.to_owned(), (language, Loadable::Loading));
            self.jobs
                .chapters(manga_id.to_owned(), self.library.settings.filters.clone());
        }
    }

    pub fn notify(&mut self, ctx: &egui::Context, message: impl Into<String>) {
        self.notice = Some((message.into(), ctx.input(|i| i.time) + 3.5));
    }

    /// After reading a chapter, moves the Comick progress forward (never back).
    fn push_comick_progress(&mut self, manga_id: &str, chapter_id: &str) {
        if self.comick.username.is_none() || !self.library.settings.comick_auto_progress() {
            return;
        }
        let Some(entry) = self.library.comick.get(manga_id).cloned() else {
            return;
        };
        let number = self
            .chapters
            .get(manga_id)
            .and_then(|(_, c)| c.ready())
            .and_then(|list| list.iter().find(|c| c.id == chapter_id))
            .and_then(Chapter::number);
        let Some(number) = number else { return };
        let current = entry
            .progress
            .as_deref()
            .and_then(|p| p.parse::<f64>().ok())
            .unwrap_or(0.0);
        if number <= current {
            return;
        }
        let mut updated = entry;
        updated.progress = Some(if number.fract() == 0.0 {
            format!("{number:.0}")
        } else {
            number.to_string()
        });
        if matches!(updated.status, None | Some(5)) {
            updated.status = Some(1);
        }
        self.library.comick.insert(manga_id.to_owned(), updated.clone());
        self.library.save();
        self.jobs.comick_save(manga_id.to_owned(), updated);
    }

    /// Marks a chapter read locally and, when syncing, on MangaDex and Comick.
    pub fn mark_read(&mut self, manga_id: &str, chapter_id: &str, read: bool) {
        self.library.set_read(manga_id, chapter_id, read);
        if read {
            self.push_comick_progress(manga_id, chapter_id);
        }
        if self.syncing_enabled() {
            let ids = vec![chapter_id.to_owned()];
            let (r, u) = if read { (ids, vec![]) } else { (vec![], ids) };
            self.jobs.push_read(manga_id.to_owned(), r, u);
        }
    }

    fn handle_events(&mut self, ctx: &egui::Context) {
        let events: Vec<Event> = self.jobs.drain().collect();
        for event in events {
            match event {
                Event::SourceList { request, result } => {
                    if request != self.browse.request {
                        continue; // A newer search replaced this one.
                    }
                    self.browse.loading = false;
                    match result {
                        Ok((items, has_next)) => {
                            let known: HashSet<String> =
                                self.browse.items.iter().map(|m| m.id.clone()).collect();
                            self.browse
                                .items
                                .extend(items.into_iter().filter(|m| !known.contains(&m.id)));
                            self.browse.has_next = has_next;
                        }
                        Err(e) => self.browse.error = Some(e),
                    }
                }
                Event::GlobalResults {
                    request,
                    source,
                    result,
                } => {
                    if request == self.global.request {
                        let state = match result {
                            Ok(items) => Loadable::Ready(items),
                            Err(e) => Loadable::Failed(e),
                        };
                        self.global.results.insert(source, state);
                    }
                }
                Event::SourceFilters { source, result } => {
                    let state = match result {
                        Ok(mut list) => {
                            if let Some(saved) = self.library.settings.source_filters.get(source) {
                                list.restore(saved);
                            }
                            Loadable::Ready(list)
                        }
                        Err(e) => Loadable::Failed(e),
                    };
                    let had_saved = self.active_filters(source) == 0
                        && state.ready().is_some_and(|l| l.active_count() > 0);
                    self.source_filters.insert(source, state);
                    // Saved filters can only be sent once the list (and its tag IDs) is known.
                    if had_saved && self.browse.source == Some(source) {
                        self.load_browse(false);
                    }
                }
                Event::Shelf {
                    source,
                    shelf,
                    period,
                    result,
                } => {
                    if self
                        .shelves
                        .get(&(source, shelf))
                        .is_some_and(|(p, _)| *p == period)
                    {
                        let state = match result {
                            Ok(items) => Loadable::Ready(items),
                            Err(e) => Loadable::Failed(e),
                        };
                        self.shelves.insert((source, shelf), (period, state));
                    }
                }
                Event::Opened { manga_id, result } => {
                    if self.browse.opening.as_deref() == Some(manga_id.as_str()) {
                        self.browse.opening = None;
                        match result {
                            Ok(manga) => self.actions.push(Action::OpenManga(manga)),
                            Err(e) => self.notify(ctx, e),
                        }
                    }
                }
                Event::MangaDetails { id, result } => {
                    if let Ok(manga) = result {
                        self.library.refresh(&manga);
                        if let Some(d) = self.details.as_mut().filter(|d| d.manga.id == id) {
                            d.manga = manga;
                            d.details_loaded = true;
                        }
                    }
                }
                Event::Chapters {
                    manga_id,
                    language,
                    result,
                } => {
                    let state = match result {
                        Ok(list) => Loadable::Ready(list),
                        Err(e) => Loadable::Failed(e),
                    };
                    self.chapters.insert(manga_id, (language, state));
                }
                Event::PageUrls { chapter_id, result } => {
                    if let Some(reader) = self.reader.as_mut() {
                        reader.pages_loaded(&chapter_id, result);
                    }
                }
                Event::Image { key, result } => self.images.finish(ctx, key, result),
                Event::Account(result) => {
                    self.account.busy = false;
                    self.account.form.password.clear();
                    match result {
                        Ok(Some(name)) => {
                            self.account.error = None;
                            self.notify(ctx, format!("Signed in to MangaDex as {name}"));
                            self.account.username = Some(name);
                            self.library.settings.mangadex_username =
                                self.account.form.username.trim().to_owned();
                            self.library.settings.mangadex_client_id =
                                self.account.form.client_id.trim().to_owned();
                            self.account.form.client_secret.clear();
                            self.library.save();
                            self.actions.push(Action::Sync);
                        }
                        Ok(None) => {
                            self.account.username = None;
                            self.library.statuses.clear();
                            self.library.save();
                        }
                        Err(e) => self.account.error = Some(e),
                    }
                }
                Event::Synced(result) => {
                    self.account.syncing = false;
                    match result {
                        Ok(sync) => {
                            let mut added = 0;
                            for manga in &sync.follows {
                                if !self.library.contains(&manga.id) {
                                    self.library.add(manga);
                                    added += 1;
                                }
                            }
                            self.library.statuses = sync.statuses;
                            self.library.save();
                            let msg = match added {
                                0 => format!(
                                    "Library is up to date with your {} MangaDex follows",
                                    sync.follows.len()
                                ),
                                1 => "Added 1 title from your MangaDex follows".to_owned(),
                                n => format!("Added {n} titles from your MangaDex follows"),
                            };
                            self.account.last_sync = Some(msg.clone());
                            self.notify(ctx, msg);
                        }
                        Err(e) => {
                            if e.contains("expired") {
                                self.account.username = None;
                            }
                            self.notify(ctx, format!("Couldn’t sync with MangaDex: {e}"));
                        }
                    }
                }
                Event::ReadMarkers { manga_id, result } => {
                    if let Ok(ids) = result {
                        self.library.merge_read(&manga_id, &ids);
                    }
                }
                Event::Notice(n) => self.notify(ctx, n),
                Event::ComickAccount(result) => {
                    self.comick.busy = false;
                    match result {
                        Ok(Some(name)) => {
                            self.comick.error = None;
                            self.notify(ctx, format!("Connected to Comick as {name}"));
                            self.comick.username = Some(name);
                        }
                        Ok(None) => {
                            self.comick.username = None;
                            self.library.comick.clear();
                            self.library.save();
                        }
                        Err(e) => self.comick.error = Some(e),
                    }
                }
                Event::ComickEntry { manga_id, result } => {
                    self.comick.linking.remove(&manga_id);
                    match result {
                        Ok(Some(entry)) => {
                            self.library.comick.insert(manga_id, entry);
                            self.library.save();
                        }
                        Ok(None) => {}
                        Err(e) => {
                            if e.contains("expired") {
                                self.comick.username = None;
                            }
                            self.notify(ctx, e);
                        }
                    }
                }
            }
        }
    }

    fn apply_actions(&mut self, ctx: &egui::Context) {
        let actions: Vec<Action> = std::mem::take(&mut self.actions);
        for action in actions {
            match action {
                Action::Section(s) => {
                    self.section = s;
                    self.details = None;
                }
                Action::OpenManga(manga) if !manga.source.is_empty() && manga.source != "mangadex" => {
                    // A title from another source: ask the source for its readable version.
                    self.browse.opening = Some(manga.id.clone());
                    self.jobs.open(manga);
                }
                Action::OpenManga(manga) => {
                    self.jobs.manga_details(manga.id.clone());
                    self.ensure_chapters(&manga.id, false);
                    if self.syncing_enabled() {
                        self.jobs.read_markers(manga.id.clone());
                    }
                    if self.comick.username.is_some()
                        && let Some(e) = self.library.comick.get(&manga.id)
                    {
                        self.jobs.comick_refresh(manga.id.clone(), e.hid.clone());
                    }
                    self.details = Some(Details {
                        manga,
                        details_loaded: false,
                        newest_first: true,
                        expanded: false,
                    });
                }
                Action::Back => {
                    if self.reader.is_some() {
                        self.close_reader();
                    } else {
                        self.details = None;
                    }
                }
                Action::Read {
                    manga,
                    chapter_id,
                    page,
                } => {
                    let Some(list) = self.chapters.get(&manga.id).and_then(|(_, c)| c.ready()) else {
                        continue;
                    };
                    let readable: Vec<Chapter> = list
                        .iter()
                        .filter(|c| c.external_url.is_none())
                        .cloned()
                        .collect();
                    if let Some(index) = readable.iter().position(|c| c.id == chapter_id) {
                        let mode = self.library.settings.reading_mode;
                        let reader = Reader::new(manga, readable, index, page, mode);
                        self.jobs
                            .page_urls(chapter_id, self.library.settings.filters.data_saver);
                        self.reader = Some(reader);
                    }
                }
                Action::ToggleLibrary(manga) => {
                    let adding = !self.library.contains(&manga.id);
                    if adding {
                        self.library.add(&manga);
                        self.notify(ctx, format!("Added “{}” to your library", manga.title));
                    } else {
                        self.library.remove(&manga.id);
                        self.notify(ctx, format!("Removed “{}” from your library", manga.title));
                    }
                    if self.syncing_enabled() {
                        self.jobs.set_follow(manga.id.clone(), adding);
                    }
                }
                Action::SetRead {
                    manga_id,
                    chapter_id,
                    read,
                } => self.mark_read(&manga_id, &chapter_id, read),
                Action::MarkPreviousRead { manga_id, chapter_id } => {
                    if let Some(list) = self.chapters.get(&manga_id).and_then(|(_, c)| c.ready()) {
                        let ids: Vec<String> = list
                            .iter()
                            .take_while(|c| c.id != chapter_id)
                            .map(|c| c.id.clone())
                            .collect();
                        let progress = self.library.progress.entry(manga_id.clone()).or_default();
                        progress.read.extend(ids.iter().cloned());
                        self.library.save();
                        if self.syncing_enabled() && !ids.is_empty() {
                            self.jobs.push_read(manga_id, ids, vec![]);
                        }
                    }
                }
                Action::SetStatus { manga_id, status } => {
                    match &status {
                        Some(s) => self.library.statuses.insert(manga_id.clone(), s.clone()),
                        None => self.library.statuses.remove(&manga_id),
                    };
                    self.library.save();
                    if self.signed_in() {
                        self.jobs.set_status(manga_id, status);
                    }
                }
                Action::LoadMoreBrowse => {
                    if !self.browse.loading && self.browse.error.is_none() {
                        self.load_browse(true);
                    }
                }
                Action::ReloadBrowse => self.load_browse(false),
                Action::OpenSource(id, tab) => self.open_source(id, tab),
                Action::CloseSource => {
                    self.browse.source = None;
                    self.browse.show_filters = false;
                    self.browse.query.clear();
                    self.browse.items.clear();
                }
                Action::TogglePin(id) => {
                    let pinned = &mut self.library.settings.pinned_sources;
                    if let Some(i) = pinned.iter().position(|p| p == id) {
                        pinned.remove(i);
                    } else {
                        pinned.push(id.to_owned());
                    }
                    self.library.save();
                }
                Action::GlobalSearch(query) => {
                    let q = query.trim().to_owned();
                    self.global.request += 1;
                    self.global.results.clear();
                    self.global.submitted = q.clone();
                    if !q.is_empty() {
                        for s in sources::all() {
                            self.global.results.insert(s.info().id, Loadable::Loading);
                        }
                        self.jobs.global_search(
                            self.global.request,
                            q,
                            self.library.settings.filters.clone(),
                        );
                    }
                }
                Action::ShelfPeriod(source, shelf, period) => self.load_shelf(source, shelf, period),
                Action::SourceFiltersChanged(source) => {
                    if let Some(list) = self.source_filters.get(source).and_then(Loadable::ready) {
                        self.library
                            .settings
                            .source_filters
                            .insert(source.to_owned(), list.chosen());
                        self.library.save();
                    }
                    self.load_browse(false);
                }
                Action::ReloadChapters(id) => self.ensure_chapters(&id, true),
                Action::OpenUrl(url) => {
                    if let Err(e) = open::that_detached(&url) {
                        self.notify(ctx, format!("Couldn't open the browser: {e}"));
                    }
                }
                Action::CloseReader => self.close_reader(),
                Action::FiltersChanged => {
                    // Language or content settings changed: refresh what depends on them.
                    self.library.save();
                    self.chapters.clear();
                    self.shelves.clear();
                    if self.browse.source.is_some() {
                        self.load_browse(false);
                    }
                }
                Action::SignIn => {
                    self.account.busy = true;
                    self.account.error = None;
                    self.jobs.sign_in(self.account.form.clone());
                }
                Action::SignOut => {
                    self.jobs.sign_out();
                    self.notify(ctx, "Signed out of MangaDex");
                }
                Action::Sync => {
                    if self.signed_in() && !self.account.syncing {
                        self.account.syncing = true;
                        self.jobs.sync();
                    }
                }
                Action::Notice(n) => self.notify(ctx, n),
                Action::ComickConnect => {
                    self.comick.busy = true;
                    self.comick.error = None;
                    self.library.settings.comick_client_id =
                        self.library.settings.comick_client_id.trim().to_owned();
                    self.library.save();
                    self.jobs
                        .comick_connect(self.library.settings.comick_client_id.clone());
                }
                Action::ComickDisconnect => {
                    self.jobs.comick_disconnect();
                    self.notify(ctx, "Disconnected from Comick");
                }
                Action::ComickLink(manga) => {
                    self.comick.linking.insert(manga.id.clone());
                    self.jobs.comick_link(manga);
                }
                Action::ComickSave { manga_id, entry } => {
                    self.library.comick.insert(manga_id.clone(), entry.clone());
                    self.library.save();
                    self.jobs.comick_save(manga_id, entry);
                }
                Action::ComickUnlink(manga_id) => {
                    if let Some(entry) = self.library.comick.remove(&manga_id) {
                        self.library.save();
                        self.jobs.comick_remove(entry.hid);
                        self.notify(ctx, format!("Removed “{}” from your Comick library", entry.title));
                    }
                }
            }
        }
    }

    fn close_reader(&mut self) {
        if let Some(reader) = self.reader.take() {
            crate::glass::keep_awake(false);
            let id = reader.manga.id.clone();
            self.library.save();
            if self.details.as_ref().is_none_or(|d| d.manga.id != id) {
                self.actions.push(Action::OpenManga(reader.manga));
            }
        }
    }

    fn shortcuts(&mut self, ctx: &egui::Context) {
        if self.reader.is_some() {
            return; // The reader handles its own keys.
        }
        let typing = ctx.memory(|m| m.focused().is_some()) && ctx.egui_wants_keyboard_input();
        ctx.input_mut(|i| {
            if i.consume_key(egui::Modifiers::COMMAND, Key::F) {
                self.actions.push(Action::Section(Section::Browse));
                self.browse.focus_search = true;
            }
            for (key, section) in [
                (Key::Num1, Section::Library),
                (Key::Num2, Section::Browse),
                (Key::Comma, Section::Settings),
            ] {
                if i.consume_key(egui::Modifiers::COMMAND, key) {
                    self.actions.push(Action::Section(section));
                }
            }
            let back = i.consume_key(egui::Modifiers::ALT, Key::ArrowLeft)
                || (!typing && i.key_pressed(Key::Escape));
            if back && self.details.is_some() {
                self.actions.push(Action::Back);
            }
            if i.pointer.button_pressed(egui::PointerButton::Extra1) && self.details.is_some() {
                self.actions.push(Action::Back);
            }
        });
    }

    fn sidebar(&mut self, ui: &mut egui::Ui) {
        let p = Palette::of(ui.ctx());
        ui.add_space(space::S);
        ui.horizontal(|ui| {
            ui.add_space(space::S);
            ui.label(
                egui::RichText::new("Neko")
                    .font(theme::font(20.0, theme::Weight::Bold))
                    .color(p.label),
            );
        });
        ui.add_space(space::L);
        let in_library = self.library.entries.len();
        let items = [
            (Section::Library, Icon::Library, "Library", Some(in_library)),
            (Section::Browse, Icon::Browse, "Browse", None),
        ];
        for (section, icon, label, badge) in items {
            let selected = self.section == section;
            if widgets::sidebar_item(ui, icon, label, selected, badge).clicked() {
                self.actions.push(Action::Section(section));
            }
            ui.add_space(2.0);
        }
        ui.add_space(space::L);
        // Recently read titles give a one-click way back into reading.
        let recent = self.recent_titles(5);
        if !recent.is_empty() {
            ui.horizontal(|ui| {
                ui.add_space(space::S);
                ui.label(
                    egui::RichText::new("Recently Read")
                        .font(text::caption())
                        .color(p.secondary_label),
                );
            });
            ui.add_space(space::XS);
            for manga in recent {
                let selected = self.details.as_ref().is_some_and(|d| d.manga.id == manga.id);
                let r = recent_row(ui, &mut self.images, &self.jobs, &manga, selected);
                if r.clicked() {
                    self.actions.push(Action::OpenManga(manga));
                }
            }
        }
        ui.with_layout(egui::Layout::bottom_up(egui::Align::Min), |ui| {
            ui.add_space(space::S);
            if widgets::sidebar_item(
                ui,
                Icon::Settings,
                "Settings",
                self.section == Section::Settings,
                None,
            )
            .clicked()
            {
                self.actions.push(Action::Section(Section::Settings));
            }
            if let Some(name) = &self.account.username {
                ui.add_space(2.0);
                let label = if self.account.syncing {
                    "Syncing…".to_owned()
                } else {
                    name.clone()
                };
                let r = widgets::sidebar_item(ui, Icon::Person, &label, false, None)
                    .on_hover_text("Signed in to MangaDex. Click to sync your follows.");
                if r.clicked() {
                    self.actions.push(Action::Sync);
                }
            }
        });
    }

    /// Library titles with reading progress, most recent first.
    pub fn recent_titles(&self, limit: usize) -> Vec<Manga> {
        let mut recent: Vec<(&String, &crate::library::Progress)> = self
            .library
            .progress
            .iter()
            .filter(|(_, p)| p.chapter_id.is_some())
            .collect();
        recent.sort_by_key(|(_, p)| std::cmp::Reverse(p.updated));
        recent
            .into_iter()
            .filter_map(|(id, _)| {
                self.library
                    .entries
                    .iter()
                    .find(|e| &e.manga.id == id)
                    .map(|e| e.manga.clone())
            })
            .take(limit)
            .collect()
    }

    fn notice_overlay(&mut self, ctx: &egui::Context) {
        let now = ctx.input(|i| i.time);
        let Some((message, until)) = self.notice.clone() else {
            return;
        };
        if now > until {
            self.notice = None;
            return;
        }
        ctx.request_repaint_after(std::time::Duration::from_millis(250));
        let p = Palette::of(ctx);
        let screen = ctx.content_rect();
        let painter = ctx.layer_painter(egui::LayerId::new(
            egui::Order::Tooltip,
            egui::Id::unique("notice"),
        ));
        let galley = painter.layout_no_wrap(message, text::callout(), p.label);
        let size = galley.size() + vec2(40.0, 20.0);
        let rect = Rect::from_center_size(pos2(screen.center().x, screen.bottom() - 44.0), size);
        crate::glass::panel(&painter, rect, CornerRadius::same(255), &p);
        painter.galley(rect.center() - galley.size() / 2.0, galley, p.label);
    }
}

fn recent_row(
    ui: &mut egui::Ui,
    images: &mut Images,
    jobs: &Jobs,
    manga: &Manga,
    selected: bool,
) -> egui::Response {
    let p = Palette::of(ui.ctx());
    let (rect, response) = ui.allocate_exact_size(vec2(ui.available_width(), 40.0), egui::Sense::click());
    response.widget_info(|| egui::WidgetInfo::labeled(egui::Role::Link, true, &manga.title));
    let fill = if selected {
        p.fill_hover
    } else if response.hovered() {
        p.fill
    } else {
        Color32::TRANSPARENT
    };
    ui.painter()
        .rect_filled(rect, CornerRadius::same(theme::radius::CONTROL), fill);
    let thumb = Rect::from_min_size(pos2(rect.left() + 8.0, rect.top() + 4.0), vec2(22.0, 32.0));
    crate::cards::paint_cover(ui, images, jobs, manga, thumb, 3);
    let galley = widgets::wrapped_text(ui, &manga.title, text::callout(), p.label, rect.width() - 46.0, 1);
    ui.painter().galley(
        pos2(thumb.right() + 10.0, rect.center().y - galley.size().y / 2.0),
        galley,
        p.label,
    );
    widgets::focus_ring(ui, &response, rect, theme::radius::CONTROL);
    response.on_hover_cursor(egui::CursorIcon::PointingHand)
}

impl eframe::App for App {
    fn ui(&mut self, ui: &mut egui::Ui, _frame: &mut eframe::Frame) {
        let ctx = ui.ctx().clone();
        self.images.begin_frame();
        self.handle_events(&ctx);
        self.shortcuts(&ctx);
        let p = Palette::of(&ctx);

        if self.reader.is_some() {
            let mut reader = self.reader.take().expect("checked above");
            egui::CentralPanel::no_frame().show(ui, |ui| {
                reader.show(ui, self);
            });
            if self.reader.is_none() {
                self.reader = Some(reader);
            }
        } else {
            // With Mica behind the window, the sidebar is a tint over the system material;
            // without it, an opaque color that matches.
            let sidebar_fill = if self.vibrancy {
                p.sidebar.gamma_multiply(0.55)
            } else {
                p.sidebar
            };
            egui::Panel::left("sidebar")
                .exact_size(236.0)
                .resizable(false)
                .show_separator_line(false)
                .frame(
                    Frame::NONE
                        .fill(sidebar_fill)
                        .inner_margin(Margin {
                            left: 10,
                            right: 10,
                            top: 14,
                            bottom: 10,
                        })
                        .stroke(Stroke::NONE),
                )
                .show(ui, |ui| self.sidebar(ui));
            // A hairline between the sidebar and content, the way macOS draws it.
            let content = ui.available_rect_before_wrap();
            ui.painter()
                .vline(content.left(), content.y_range(), Stroke::new(1.0, p.separator));

            egui::CentralPanel::no_frame()
                .frame(Frame::NONE.fill(p.background))
                .show(ui, |ui| {
                    if self.details.is_some() {
                        self.details_view(ui);
                    } else {
                        match self.section {
                            Section::Library => self.library_view(ui),
                            Section::Browse => self.sources_view(ui),
                            Section::Settings => self.settings_view(ui),
                        }
                    }
                });
        }
        if let Some(err) = self.library.last_error.take() {
            self.notify(&ctx, err);
        }
        self.notice_overlay(&ctx);
        self.apply_actions(&ctx);
    }

    fn clear_color(&self, visuals: &egui::Visuals) -> [f32; 4] {
        if self.vibrancy {
            [0.0; 4]
        } else {
            visuals.panel_fill.to_normalized_gamma_f32()
        }
    }

    fn save(&mut self, _storage: &mut dyn eframe::Storage) {
        self.library.save();
    }
}

/// The large title at the top of each section, with room for trailing controls.
pub fn page_header(
    ui: &mut egui::Ui,
    title: &str,
    subtitle: Option<&str>,
    trailing: impl FnOnce(&mut egui::Ui),
) {
    let p = Palette::of(ui.ctx());
    ui.add_space(space::XL);
    ui.horizontal(|ui| {
        ui.vertical(|ui| {
            ui.label(
                egui::RichText::new(title)
                    .font(text::large_title())
                    .color(p.label),
            );
            if let Some(sub) = subtitle {
                ui.label(
                    egui::RichText::new(sub)
                        .font(text::callout())
                        .color(p.secondary_label),
                );
            }
        });
        ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), trailing);
    });
    ui.add_space(space::L);
}

/// Content inset from the window edges.
pub fn page_frame() -> Frame {
    Frame::NONE.inner_margin(Margin::symmetric(space::PAGE as i8, 0))
}
