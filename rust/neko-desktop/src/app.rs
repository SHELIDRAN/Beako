//! App state, navigation, and the window shell (sidebar + content area).

use std::collections::HashMap;

use eframe::egui::{self, CornerRadius, Frame, Key, Margin, Rect, Stroke, StrokeKind, pos2, vec2};

use crate::api::{Chapter, Feed, Manga};
use crate::images::Images;
use crate::jobs::{Event, Jobs};
use crate::library::Library;
use crate::reader::Reader;
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
    pub feed: Feed,
    pub query: String,
    /// The query the current results are for.
    pub results_query: String,
    pub items: Vec<Manga>,
    pub total: u32,
    pub loading: bool,
    pub error: Option<String>,
    pub request: u64,
    pub last_edit: Option<f64>,
    pub focus_search: bool,
}

pub struct Details {
    pub manga: Manga,
    pub details_loaded: bool,
    pub newest_first: bool,
    pub expanded: bool,
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
    LoadMoreBrowse,
    ReloadBrowse,
    ReloadChapters(String),
    OpenUrl(String),
    CloseReader,
    FiltersChanged,
    Notice(String),
}

pub struct App {
    pub jobs: Jobs,
    pub images: Images,
    pub library: Library,
    pub section: Section,
    pub details: Option<Details>,
    pub reader: Option<Reader>,
    pub browse: BrowseState,
    /// Chapter lists keyed by manga id; each knows the language it was fetched in.
    pub chapters: HashMap<String, (String, Loadable<Vec<Chapter>>)>,
    pub actions: Vec<Action>,
    notice: Option<(String, f64)>,
    pub data_dir: Option<std::path::PathBuf>,
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
        let dirs = directories::ProjectDirs::from("org", "Neko", "Neko Desktop");
        let data_dir = dirs.as_ref().map(|d| d.data_dir().to_path_buf());
        let cache_dir = dirs.as_ref().map(|d| d.cache_dir().to_path_buf());
        let library = Library::load(data_dir.as_ref().map(|d| d.join("library.json")));
        let jobs =
            Jobs::new(cc.egui_ctx.clone(), cache_dir).expect("couldn't start the network client");
        let section = if library.entries.is_empty() {
            Section::Browse
        } else {
            Section::Library
        };
        let mut app = Self {
            jobs,
            images: Images::default(),
            library,
            section,
            details: None,
            reader: None,
            browse: BrowseState {
                feed: Feed::Popular,
                query: String::new(),
                results_query: String::new(),
                items: Vec::new(),
                total: 0,
                loading: false,
                error: None,
                request: 0,
                last_edit: None,
                focus_search: false,
            },
            chapters: HashMap::new(),
            actions: Vec::new(),
            notice: None,
            data_dir,
        };
        app.load_browse(false);
        app
    }

    pub fn load_browse(&mut self, append: bool) {
        let b = &mut self.browse;
        b.request += 1;
        b.loading = true;
        b.error = None;
        if !append {
            b.items.clear();
            b.total = 0;
            b.results_query = b.query.trim().to_owned();
        }
        let offset = b.items.len() as u32;
        self.jobs.list_manga(
            b.request,
            b.feed,
            b.results_query.clone(),
            offset,
            self.library.settings.filters.clone(),
        );
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

    fn handle_events(&mut self, ctx: &egui::Context) {
        let events: Vec<Event> = self.jobs.drain().collect();
        for event in events {
            match event {
                Event::MangaList { request, result } => {
                    if request != self.browse.request {
                        continue; // A newer search replaced this one.
                    }
                    self.browse.loading = false;
                    match result {
                        Ok(page) => {
                            self.browse.total = page.total;
                            let known: std::collections::HashSet<String> =
                                self.browse.items.iter().map(|m| m.id.clone()).collect();
                            self.browse
                                .items
                                .extend(page.items.into_iter().filter(|m| !known.contains(&m.id)));
                        }
                        Err(e) => self.browse.error = Some(e),
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
                Event::Image { url, result } => self.images.finish(ctx, url, result),
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
                Action::OpenManga(manga) => {
                    self.jobs.manga_details(manga.id.clone());
                    self.ensure_chapters(&manga.id, false);
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
                    let Some(list) = self.chapters.get(&manga.id).and_then(|(_, c)| c.ready())
                    else {
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
                    if self.library.contains(&manga.id) {
                        self.library.remove(&manga.id);
                        self.notify(ctx, format!("Removed “{}” from your library", manga.title));
                    } else {
                        self.library.add(&manga);
                        self.notify(ctx, format!("Added “{}” to your library", manga.title));
                    }
                }
                Action::SetRead {
                    manga_id,
                    chapter_id,
                    read,
                } => self.library.set_read(&manga_id, &chapter_id, read),
                Action::MarkPreviousRead {
                    manga_id,
                    chapter_id,
                } => {
                    if let Some(list) = self.chapters.get(&manga_id).and_then(|(_, c)| c.ready()) {
                        let ids: Vec<String> = list
                            .iter()
                            .take_while(|c| c.id != chapter_id)
                            .map(|c| c.id.clone())
                            .collect();
                        for id in ids {
                            self.library
                                .progress
                                .entry(manga_id.clone())
                                .or_default()
                                .read
                                .insert(id);
                        }
                        self.library.save();
                    }
                }
                Action::LoadMoreBrowse => {
                    if !self.browse.loading && self.browse.error.is_none() {
                        self.load_browse(true);
                    }
                }
                Action::ReloadBrowse => self.load_browse(false),
                Action::ReloadChapters(id) => self.ensure_chapters(&id, true),
                Action::OpenUrl(url) => {
                    if let Err(e) = open::that_detached(&url) {
                        self.notify(ctx, format!("Couldn't open the browser: {e}"));
                    }
                }
                Action::CloseReader => self.close_reader(),
                Action::FiltersChanged => {
                    self.library.save();
                    self.chapters.clear();
                    self.load_browse(false);
                }
                Action::Notice(n) => self.notify(ctx, n),
            }
        }
    }

    fn close_reader(&mut self) {
        if let Some(reader) = self.reader.take() {
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
        let mut recent: Vec<(&String, &crate::library::Progress)> = self
            .library
            .progress
            .iter()
            .filter(|(_, p)| p.chapter_id.is_some())
            .collect();
        recent.sort_by_key(|(_, p)| std::cmp::Reverse(p.updated));
        let recent: Vec<Manga> = recent
            .into_iter()
            .filter_map(|(id, _)| {
                self.library
                    .entries
                    .iter()
                    .find(|e| &e.manga.id == id)
                    .map(|e| e.manga.clone())
            })
            .take(5)
            .collect();
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
                let selected = self
                    .details
                    .as_ref()
                    .is_some_and(|d| d.manga.id == manga.id);
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
        });
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
        let size = galley.size() + vec2(36.0, 18.0);
        let rect = Rect::from_center_size(pos2(screen.center().x, screen.bottom() - 40.0), size);
        painter.add(
            egui::epaint::Shadow {
                offset: [0, 6],
                blur: 24,
                spread: 0,
                color: p.shadow,
            }
            .as_shape(rect, CornerRadius::same(255)),
        );
        painter.rect(
            rect,
            CornerRadius::same(255),
            p.elevated,
            Stroke::new(1.0, p.separator),
            StrokeKind::Inside,
        );
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
    let (rect, response) =
        ui.allocate_exact_size(vec2(ui.available_width(), 40.0), egui::Sense::click());
    response.widget_info(|| egui::WidgetInfo::labeled(egui::Role::Link, true, &manga.title));
    let fill = if selected {
        p.fill_hover
    } else if response.hovered() {
        p.fill
    } else {
        egui::Color32::TRANSPARENT
    };
    ui.painter()
        .rect_filled(rect, CornerRadius::same(theme::radius::CONTROL), fill);
    let thumb = Rect::from_min_size(pos2(rect.left() + 8.0, rect.top() + 4.0), vec2(22.0, 32.0));
    crate::cards::paint_cover(ui, images, jobs, manga, thumb, 3);
    let galley = widgets::wrapped_text(
        ui,
        &manga.title,
        text::callout(),
        p.label,
        rect.width() - 46.0,
        1,
    );
    ui.painter().galley(
        pos2(
            thumb.right() + 10.0,
            rect.center().y - galley.size().y / 2.0,
        ),
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
            egui::Panel::left("sidebar")
                .exact_size(232.0)
                .resizable(false)
                .show_separator_line(false)
                .frame(
                    Frame::NONE
                        .fill(p.sidebar)
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
            ui.painter().vline(
                content.left(),
                content.y_range(),
                Stroke::new(1.0, p.separator),
            );

            egui::CentralPanel::no_frame()
                .frame(Frame::NONE.fill(p.background))
                .show(ui, |ui| {
                    if self.details.is_some() {
                        self.details_view(ui);
                    } else {
                        match self.section {
                            Section::Library => self.library_view(ui),
                            Section::Browse => self.browse_view(ui),
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
