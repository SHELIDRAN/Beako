//! Browse: MangaDex's popular, latest, new, and top-rated lists, MangaBaka's catalog, search,
//! and the Filters sheet.

use eframe::egui::{self, CornerRadius, RichText, Ui, vec2};

use crate::api::Feed;
use crate::app::{Action, App, page_header};
use crate::cards;
use crate::library::Catalog;
use crate::theme::{Palette, space, text};
use crate::widgets::{self, ButtonStyle, Icon};

/// How long typing (or adjusting filters) pauses before a search runs.
const SEARCH_DELAY: f64 = 0.45;

impl App {
    pub fn browse_view(&mut self, ui: &mut Ui) {
        let p = Palette::of(ui.ctx());
        let now = ui.input(|i| i.time);

        // Run the search once typing pauses, or immediately on Enter.
        if let Some(edited) = self.browse.last_edit {
            if now - edited >= SEARCH_DELAY {
                self.browse.last_edit = None;
                self.actions.push(Action::ReloadBrowse);
            } else {
                ui.ctx()
                    .request_repaint_after(std::time::Duration::from_secs_f64(SEARCH_DELAY));
            }
        }

        if self.browse.show_filters {
            let changed = egui::Panel::right("filters-sheet")
                .exact_size(crate::filters_view::SHEET_WIDTH)
                .resizable(false)
                .show_separator_line(false)
                .frame(egui::Frame::NONE)
                .show(ui, |ui| self.filters_sheet(ui))
                .inner;
            if changed {
                self.browse.last_edit = Some(now);
            }
        }

        let catalog = self.library.settings.catalog;
        let searching = !self.browse.results_query.is_empty();
        let active_filters = match catalog {
            Catalog::MangaDex => self.library.settings.search.active_count(),
            Catalog::MangaBaka => self.library.settings.baka.active_count(),
        };
        let subtitle = if self.browse.loading && self.browse.items.is_empty() {
            if searching {
                "Searching…".to_owned()
            } else {
                "Loading…".to_owned()
            }
        } else if searching || active_filters > 0 {
            match self.browse.total {
                0 => "No results".to_owned(),
                1 => "1 result".to_owned(),
                n if n >= 10_000 => "10,000+ results".to_owned(),
                n => format!("{} results", group_digits(n)),
            }
        } else {
            match catalog {
                Catalog::MangaDex => "Discover titles on MangaDex".to_owned(),
                Catalog::MangaBaka => "Discover titles on MangaBaka".to_owned(),
            }
        };
        let title = if searching { "Search" } else { "Browse" };

        let items = std::mem::take(&mut self.browse.items);
        let mut feed = self.browse.feed;
        let mut query = std::mem::take(&mut self.browse.query);
        let mut submitted = false;
        let mut edited = false;
        let mut catalog_choice = catalog;
        let mut toggle_filters = false;
        let show_filters = self.browse.show_filters;
        let focus_search = std::mem::take(&mut self.browse.focus_search);
        let state_loading = self.browse.loading;
        let error = self.browse.error.clone();
        let total = self.browse.total;
        let resolving = self.browse.resolving;
        let feed_overridden = self.library.settings.search.sort.is_some();
        let images = &mut self.images;
        let jobs = &self.jobs;
        let library = &self.library;
        let baka = &self.browse.baka;
        let actions = &mut self.actions;
        let mut footer_action = None;

        let reached_end = cards::grid(
            ui,
            "browse-grid",
            &items,
            |ui| {
                page_header(ui, title, Some(&subtitle), |ui| {
                    let label = if active_filters > 0 {
                        format!("Filters · {active_filters}")
                    } else {
                        "Filters".to_owned()
                    };
                    let style = if show_filters || active_filters > 0 {
                        ButtonStyle::Tinted
                    } else {
                        ButtonStyle::Bordered
                    };
                    if widgets::button(ui, style, Some(Icon::Filter), &label).clicked() {
                        toggle_filters = true;
                    }
                    ui.add_space(space::XS);
                    let width = (ui.available_width() - 260.0).clamp(180.0, 300.0);
                    let hint = match catalog {
                        Catalog::MangaDex => "Search MangaDex",
                        Catalog::MangaBaka => "Search MangaBaka",
                    };
                    let r = widgets::search_field(ui, &mut query, hint, width);
                    if focus_search {
                        r.request_focus();
                    }
                    if r.changed() {
                        edited = true;
                    }
                    if r.lost_focus() && ui.input(|i| i.key_pressed(egui::Key::Enter)) {
                        submitted = true;
                    }
                });
                ui.horizontal(|ui| {
                    widgets::segmented(
                        ui,
                        &mut catalog_choice,
                        &[(Catalog::MangaDex, "MangaDex"), (Catalog::MangaBaka, "MangaBaka")],
                    );
                    if catalog == Catalog::MangaDex && !searching && !feed_overridden {
                        ui.add_space(space::M);
                        let options: Vec<(Feed, &str)> = Feed::ALL.iter().map(|f| (*f, f.label())).collect();
                        if widgets::segmented(ui, &mut feed, &options) {
                            submitted = true;
                        }
                    }
                });
                if catalog == Catalog::MangaBaka {
                    ui.add_space(space::XS);
                    ui.label(
                        RichText::new("Titles from MangaBaka open on MangaDex when they’re available there.")
                            .font(text::subheadline())
                            .color(p.secondary_label),
                    );
                }
                ui.add_space(space::XL);
            },
            |ui, manga, width| {
                let baka_id = manga
                    .id
                    .strip_prefix("mangabaka:")
                    .and_then(|id| id.parse::<u64>().ok());
                let in_library = baka_id.is_none() && library.contains(&manga.id);
                let caption = match baka_id.and_then(|id| baka.get(&id)) {
                    Some(series) => series.caption(),
                    None => manga.byline(),
                };
                let r = cards::cover_card(ui, images, jobs, manga, width, &caption, in_library);
                if let Some(id) = baka_id {
                    if resolving == Some(id) {
                        let cover =
                            egui::Rect::from_min_size(r.rect.min, vec2(width, width * cards::COVER_ASPECT));
                        ui.painter().rect_filled(
                            cover,
                            CornerRadius::same(8),
                            egui::Color32::from_black_alpha(110),
                        );
                        let spin = egui::Rect::from_center_size(cover.center(), vec2(26.0, 26.0));
                        widgets::paint_spinner(ui, spin, egui::Color32::WHITE);
                    }
                    if r.clicked() {
                        actions.push(Action::ResolveBaka(id));
                    }
                    return;
                }
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
                        "Couldn’t Load Titles",
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
                } else if items.is_empty() {
                    widgets::placeholder_view(
                        ui,
                        Icon::Search,
                        "No Results",
                        "Try a different title or loosen the filters. Titles without chapters in your language are hidden.",
                        None,
                    );
                } else if items.len() as u32 >= total {
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
        self.browse.items = items;
        self.browse.query = query;
        self.actions.extend(footer_action);
        if toggle_filters {
            self.browse.show_filters = !self.browse.show_filters;
        }
        if catalog_choice != catalog {
            self.actions.push(Action::SetCatalog(catalog_choice));
        }
        if edited {
            self.browse.last_edit = Some(now);
        }
        self.browse.feed = feed;
        if submitted {
            self.browse.last_edit = None;
            self.actions.push(Action::ReloadBrowse);
        } else if reached_end && has_more && !self.browse.loading {
            self.actions.push(Action::LoadMoreBrowse);
        }
    }
}

/// 12345 → "12,345".
fn group_digits(n: u32) -> String {
    let s = n.to_string();
    let mut out = String::new();
    for (i, c) in s.chars().enumerate() {
        if i > 0 && (s.len() - i).is_multiple_of(3) {
            out.push(',');
        }
        out.push(c);
    }
    out
}

#[cfg(test)]
mod tests {
    #[test]
    fn groups_digits() {
        assert_eq!(super::group_digits(1234567), "1,234,567");
        assert_eq!(super::group_digits(999), "999");
    }
}
