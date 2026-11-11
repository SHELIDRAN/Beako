//! Browse: MangaDex's popular, latest, new, and top-rated lists, plus search.

use eframe::egui::{self, RichText, Ui};

use crate::api::Feed;
use crate::app::{Action, App, page_header};
use crate::cards;
use crate::theme::{Palette, space, text};
use crate::widgets::{self, Icon};

/// How long typing pauses before a search runs.
const SEARCH_DELAY: f64 = 0.45;

impl App {
    pub fn browse_view(&mut self, ui: &mut Ui) {
        let p = Palette::of(ui.ctx());
        let now = ui.input(|i| i.time);

        // Run the search once typing pauses, or immediately on Enter.
        if let Some(edited) = self.browse.last_edit {
            if now - edited >= SEARCH_DELAY {
                self.browse.last_edit = None;
                if self.browse.query.trim() != self.browse.results_query {
                    self.actions.push(Action::ReloadBrowse);
                }
            } else {
                ui.ctx()
                    .request_repaint_after(std::time::Duration::from_secs_f64(SEARCH_DELAY));
            }
        }

        let searching = !self.browse.results_query.is_empty();
        let subtitle = if searching {
            match (
                self.browse.loading && self.browse.items.is_empty(),
                self.browse.total,
            ) {
                (true, _) => "Searching…".to_owned(),
                (false, 0) => String::new(),
                (false, 1) => "1 result".to_owned(),
                (false, n) => format!("{n} results"),
            }
        } else {
            "Discover titles on MangaDex".to_owned()
        };

        let items = std::mem::take(&mut self.browse.items);
        let mut feed = self.browse.feed;
        let mut query = std::mem::take(&mut self.browse.query);
        let mut submitted = false;
        let mut edited = false;
        let focus_search = std::mem::take(&mut self.browse.focus_search);
        let state_loading = self.browse.loading;
        let error = self.browse.error.clone();
        let total = self.browse.total;
        let images = &mut self.images;
        let jobs = &self.jobs;
        let library = &self.library;
        let actions = &mut self.actions;
        let mut footer_action = None;

        let reached_end = cards::grid(
            ui,
            "browse-grid",
            &items,
            |ui| {
                page_header(
                    ui,
                    if searching { "Search" } else { "Browse" },
                    Some(&subtitle),
                    |ui| {
                        let r = widgets::search_field(ui, &mut query, "Search MangaDex", 280.0);
                        if focus_search {
                            r.request_focus();
                        }
                        if r.changed() {
                            edited = true;
                        }
                        if r.lost_focus() && ui.input(|i| i.key_pressed(egui::Key::Enter)) {
                            submitted = true;
                        }
                    },
                );
                if !searching {
                    let options: Vec<(Feed, &str)> =
                        Feed::ALL.iter().map(|f| (*f, f.label())).collect();
                    if widgets::segmented(ui, &mut feed, &options) {
                        submitted = true;
                    }
                    ui.add_space(space::XL);
                }
            },
            |ui, manga, width| {
                let caption = manga.byline();
                let in_library = library.contains(&manga.id);
                let r = cards::cover_card(ui, images, jobs, manga, width, &caption, in_library);
                if r.clicked() {
                    actions.push(Action::OpenManga(manga.clone()));
                }
                r.context_menu(|ui| {
                    let label = if in_library {
                        "Remove from Library"
                    } else {
                        "Add to Library"
                    };
                    if ui.button(label).clicked() {
                        actions.push(Action::ToggleLibrary(manga.clone()));
                        ui.close();
                    }
                });
            },
            |ui| {
                ui.add_space(space::L);
                if let Some(err) = &error {
                    let retry = widgets::placeholder_view(
                        ui,
                        Icon::Retry,
                        "MangaDex Didn’t Respond",
                        &format!("{err}. Check your connection and try again."),
                        Some("Try Again"),
                    );
                    if retry {
                        footer_action = Some(if items.is_empty() {
                            Action::ReloadBrowse
                        } else {
                            Action::LoadMoreBrowse
                        });
                    }
                } else if state_loading {
                    ui.vertical_centered(|ui| {
                        ui.add_space(if items.is_empty() { 120.0 } else { 0.0 });
                        widgets::spinner(ui, 22.0);
                    });
                } else if items.is_empty() && searching {
                    widgets::placeholder_view(
                        ui,
                        Icon::Search,
                        "No Results",
                        "Check the spelling or try a different title. Titles without chapters in your language are hidden.",
                        None,
                    );
                } else if !items.is_empty() && items.len() as u32 >= total {
                    ui.vertical_centered(|ui| {
                        ui.label(
                            RichText::new("You’ve reached the end")
                                .font(text::subheadline())
                                .color(p.secondary_label),
                        );
                    });
                }
            },
        );

        let has_more = (items.len() as u32) < total;
        self.actions.extend(footer_action);
        self.browse.items = items;
        self.browse.query = query;
        if edited {
            self.browse.last_edit = Some(now);
        }
        if feed != self.browse.feed {
            self.browse.feed = feed;
        }
        if submitted {
            self.browse.last_edit = None;
            self.actions.push(Action::ReloadBrowse);
        } else if reached_end && has_more && !self.browse.loading {
            self.actions.push(Action::LoadMoreBrowse);
        }
    }
}
