//! Browse, organized by source the way Mangayomi does it: a list of sources (last used,
//! pinned, all) with a search across all of them, and each source's own Popular, Latest, and
//! search-with-filters screen.

use std::cell::RefCell;

use eframe::egui::{
    self, Align2, Color32, CornerRadius, CursorIcon, Margin, Rect, RichText, Sense, Ui, vec2,
};

use crate::api::Manga;
use crate::app::{Action, App, Loadable, page_frame, page_header};
use crate::cards;
use crate::images::Images;
use crate::jobs::Jobs;
use crate::sources::{self, Listing, ShelfInfo, SourceInfo};
use crate::theme::{Palette, Weight, font, radius, space, text};
use crate::widgets::{self, ButtonStyle, Icon};

/// How long typing (or adjusting filters) pauses before a search runs.
const SEARCH_DELAY: f64 = 0.45;
const SHEET_WIDTH: f32 = 340.0;

impl App {
    pub fn sources_view(&mut self, ui: &mut Ui) {
        match self.browse.source {
            Some(id) => self.source_view(ui, id),
            None => self.source_list(ui),
        }
    }

    // -----------------------------------------------------------------------------------------
    // The list of sources

    fn source_list(&mut self, ui: &mut Ui) {
        let p = Palette::of(ui.ctx());
        let mut query = std::mem::take(&mut self.global.query);
        let mut submit = false;
        let images = RefCell::new(&mut self.images);
        let jobs = &self.jobs;
        let actions = RefCell::new(&mut self.actions);
        let settings = &self.library.settings;
        let global = &self.global;

        egui::ScrollArea::vertical().id_salt("sources").auto_shrink([false, false]).show(ui, |ui| {
            page_frame().show(ui, |ui| {
                ui.set_width(ui.available_width());
                page_header(ui, "Browse", Some("Sources"), |ui| {
                    let r = widgets::search_field(ui, &mut query, "Search all sources", 300.0);
                    if r.lost_focus() && ui.input(|i| i.key_pressed(egui::Key::Enter)) {
                        submit = true;
                    }
                    if r.changed() && query.trim().is_empty() {
                        submit = true; // Clearing the field returns to the list.
                    }
                });

                if !global.submitted.is_empty() {
                    for source in sources::all() {
                        let info = source.info();
                        let state = global.results.get(info.id).cloned().unwrap_or(Loadable::Loading);
                        global_row(ui, &mut images.borrow_mut(), jobs, info, &state, &global.submitted, &mut actions.borrow_mut());
                        ui.add_space(space::XL);
                    }
                    return;
                }

                let all: Vec<SourceInfo> = sources::all().iter().map(|s| s.info()).collect();
                let pinned: Vec<SourceInfo> =
                    all.iter().filter(|s| settings.pinned_sources.iter().any(|p| p == s.id)).copied().collect();
                let last = settings.last_source.as_deref().and_then(|id| all.iter().find(|s| s.id == id)).copied();

                let section = |ui: &mut Ui, title: &str, list: &[SourceInfo]| {
                    if list.is_empty() {
                        return;
                    }
                    ui.horizontal(|ui| {
                        ui.add_space(space::M);
                        ui.label(RichText::new(title).font(text::subheadline_emphasized()).color(p.secondary_label));
                    });
                    ui.add_space(space::XS);
                    crate::settings_view::group(ui, |ui| {
                        for (i, info) in list.iter().enumerate() {
                            if i > 0 {
                                crate::settings_view::divider(ui);
                            }
                            let is_pinned = settings.pinned_sources.iter().any(|p| p == info.id);
                            source_row(ui, info, is_pinned, &mut actions.borrow_mut());
                        }
                    });
                    ui.add_space(space::L);
                };
                // Like Mangayomi: pinned sources leave the main list, and the last-used source
                // only gets its own group when it isn't already pinned.
                let is_pinned = |s: &SourceInfo| pinned.iter().any(|p| p.id == s.id);
                if let Some(l) = last.filter(|l| !is_pinned(l)) {
                    section(ui, "Last Used", &[l]);
                }
                section(ui, "Pinned", &pinned);
                let others: Vec<SourceInfo> = all.iter().filter(|s| !is_pinned(s)).copied().collect();
                section(ui, if pinned.is_empty() { "All Sources" } else { "Other Sources" }, &others);
                ui.label(
                    RichText::new("Titles from every source are read through MangaDex, where chapters are available.")
                        .font(text::subheadline())
                        .color(p.secondary_label),
                );
                ui.add_space(space::XXL);
            });
        });

        self.global.query = query;
        if submit {
            let q = self.global.query.clone();
            self.actions.push(Action::GlobalSearch(q));
        }
    }

    // -----------------------------------------------------------------------------------------
    // One source

    fn source_view(&mut self, ui: &mut Ui, id: &'static str) {
        let Some(source) = sources::get(id) else { return };
        let info = source.info();
        let p = Palette::of(ui.ctx());
        let now = ui.input(|i| i.time);

        // Run the search once typing or filtering pauses, or immediately on Enter.
        if let Some(edited) = self.browse.last_edit {
            if now - edited >= SEARCH_DELAY {
                self.browse.last_edit = None;
                self.actions.push(Action::SourceFiltersChanged(id));
            } else {
                ui.ctx()
                    .request_repaint_after(std::time::Duration::from_secs_f64(SEARCH_DELAY));
            }
        }

        if self.browse.show_filters {
            let changed = egui::Panel::right("filters-sheet")
                .exact_size(SHEET_WIDTH)
                .resizable(false)
                .show_separator_line(false)
                .frame(egui::Frame::NONE)
                .show(ui, |ui| self.filter_sheet(ui, id))
                .inner;
            if changed {
                self.browse.last_edit = Some(now);
            }
        }

        let active = self.active_filters(id);
        let searching = !self.browse.results_query.is_empty() || active > 0;
        let subtitle = if searching {
            if self.browse.loading && self.browse.items.is_empty() {
                "Searching…".to_owned()
            } else if self.browse.items.is_empty() {
                "No results".to_owned()
            } else {
                format!("Results from {}", info.name)
            }
        } else {
            info.description.to_owned()
        };

        let items = std::mem::take(&mut self.browse.items);
        let mut query = std::mem::take(&mut self.browse.query);
        let mut tab = self.browse.tab.clone();
        let mut edited = false;
        let mut submitted = false;
        let mut toggle_filters = false;
        let mut back = false;
        let show_filters = self.browse.show_filters;
        let focus_search = std::mem::take(&mut self.browse.focus_search);
        let loading = self.browse.loading;
        let error = self.browse.error.clone();
        let has_next = self.browse.has_next;
        let opening = self.browse.opening.clone();
        let shelves: Vec<(ShelfInfo, crate::app::ShelfState)> = if searching || tab != Listing::Popular {
            Vec::new()
        } else {
            source
                .shelves()
                .iter()
                .map(|s| {
                    (
                        *s,
                        self.shelves
                            .get(&(id, s.id))
                            .cloned()
                            .unwrap_or((0, Loadable::Loading)),
                    )
                })
                .collect()
        };
        let images = RefCell::new(&mut self.images);
        let jobs = &self.jobs;
        let library = &self.library;
        let actions = RefCell::new(&mut self.actions);

        let reached_end = cards::grid(
            ui,
            "source-grid",
            &items,
            |ui| {
                ui.add_space(space::M);
                if widgets::button(ui, ButtonStyle::Plain, Some(Icon::ChevronLeft), "Sources").clicked() {
                    back = true;
                }
                page_header(ui, info.name, Some(&subtitle), |ui| {
                    let label = if active > 0 {
                        format!("Filters · {active}")
                    } else {
                        "Filters".to_owned()
                    };
                    let style = if show_filters || active > 0 {
                        ButtonStyle::Tinted
                    } else {
                        ButtonStyle::Bordered
                    };
                    if widgets::button(ui, style, Some(Icon::Filter), &label).clicked() {
                        toggle_filters = true;
                    }
                    ui.add_space(space::XS);
                    let width = (ui.available_width() - 260.0).clamp(180.0, 300.0);
                    let r = widgets::search_field(ui, &mut query, &format!("Search {}", info.name), width);
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
                if !searching {
                    let mut latest = tab == Listing::Latest;
                    let mut options = vec![(false, "Popular")];
                    if info.supports_latest {
                        options.push((true, "Latest"));
                    }
                    if widgets::segmented(ui, &mut latest, &options) {
                        tab = if latest { Listing::Latest } else { Listing::Popular };
                        submitted = true;
                    }
                }
                if let Some(other) = info.reads_from {
                    ui.add_space(space::XS);
                    ui.label(
                        RichText::new(format!("Titles open on {other}, where their chapters are read."))
                            .font(text::subheadline())
                            .color(p.secondary_label),
                    );
                }
                ui.add_space(space::XL);
                for (shelf, (period, state)) in &shelves {
                    shelf_row(
                        ui,
                        &mut images.borrow_mut(),
                        jobs,
                        id,
                        shelf,
                        *period,
                        state,
                        opening.as_deref(),
                        &mut actions.borrow_mut(),
                    );
                    ui.add_space(space::XL);
                }
                if !shelves.is_empty() {
                    ui.label(RichText::new("Popular").font(text::title2()).color(p.label));
                    ui.add_space(space::M);
                }
            },
            |ui, manga, width| {
                let caption = manga.caption.clone().unwrap_or_else(|| manga.byline());
                let in_library = library.contains(&manga.id);
                let r = cards::cover_card(
                    ui,
                    &mut images.borrow_mut(),
                    jobs,
                    manga,
                    width,
                    &caption,
                    in_library,
                );
                if opening.as_deref() == Some(manga.id.as_str()) {
                    busy_overlay(ui, r.rect, width);
                }
                let mut actions = actions.borrow_mut();
                if r.clicked() {
                    actions.push(Action::OpenManga(manga.clone()));
                }
                if manga.source.is_empty() || manga.source == "mangadex" {
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
                }
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
                        actions.borrow_mut().push(if items.is_empty() {
                            Action::ReloadBrowse
                        } else {
                            Action::LoadMoreBrowse
                        });
                    }
                } else if loading {
                    ui.vertical_centered(|ui| {
                        ui.add_space(if items.is_empty() { 120.0 } else { 0.0 });
                        widgets::spinner(ui, 22.0);
                    });
                } else if items.is_empty() {
                    widgets::placeholder_view(
                        ui,
                        Icon::Search,
                        "No Results",
                        "Try a different title or loosen the filters.",
                        None,
                    );
                } else if !has_next {
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

        self.browse.items = items;
        self.browse.query = query;
        if back {
            self.actions.push(Action::CloseSource);
        }
        if toggle_filters {
            self.browse.show_filters = !self.browse.show_filters;
        }
        if edited {
            self.browse.last_edit = Some(now);
        }
        if tab != self.browse.tab {
            self.browse.tab = tab;
        }
        if submitted {
            self.browse.last_edit = None;
            self.actions.push(Action::ReloadBrowse);
        } else if reached_end && has_next && !self.browse.loading && self.browse.error.is_none() {
            self.actions.push(Action::LoadMoreBrowse);
        }
    }

    /// The source's filters in a sheet beside the results. Returns true when one changed.
    fn filter_sheet(&mut self, ui: &mut Ui, id: &'static str) -> bool {
        let p = Palette::of(ui.ctx());
        let mut close = false;
        let mut changed = false;
        let frame = egui::Frame::NONE
            .fill(if p.dark { p.elevated } else { p.background })
            .corner_radius(CornerRadius::same(14))
            .stroke(egui::Stroke::new(1.0, p.separator))
            .shadow(egui::epaint::Shadow {
                offset: [0, 10],
                blur: 30,
                spread: 0,
                color: p.shadow,
            })
            .inner_margin(Margin {
                left: 18,
                right: 10,
                top: 16,
                bottom: 12,
            })
            .outer_margin(Margin {
                left: 4,
                right: 16,
                top: 16,
                bottom: 16,
            });
        frame.show(ui, |ui| {
            ui.set_width(SHEET_WIDTH - 48.0);
            let state = self.source_filters.entry(id).or_insert(Loadable::Loading);
            ui.horizontal(|ui| {
                ui.label(RichText::new("Filters").font(text::title2()).color(p.label));
                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    if widgets::button(ui, ButtonStyle::Plain, None, "Done").clicked() {
                        close = true;
                    }
                    if let Loadable::Ready(list) = state {
                        ui.add_enabled_ui(list.active_count() > 0, |ui| {
                            if widgets::button(ui, ButtonStyle::Plain, None, "Reset").clicked() {
                                list.reset();
                                changed = true;
                            }
                        });
                    }
                });
            });
            ui.add_space(space::S);
            egui::ScrollArea::vertical()
                .id_salt(("filters", id))
                .auto_shrink([false, false])
                .show(ui, |ui| {
                    ui.set_width(SHEET_WIDTH - 60.0);
                    match state {
                        Loadable::Ready(list) => {
                            changed |= crate::filters::show(ui, list, SHEET_WIDTH - 70.0)
                        }
                        Loadable::Failed(e) => {
                            ui.label(
                                RichText::new(format!("Couldn’t load filters: {e}"))
                                    .font(text::callout())
                                    .color(p.secondary_label),
                            );
                            if widgets::button(ui, ButtonStyle::Bordered, Some(Icon::Retry), "Try Again")
                                .clicked()
                            {
                                *state = Loadable::Loading;
                                self.jobs.source_filters(id);
                            }
                        }
                        _ => {
                            ui.add_space(space::L);
                            widgets::spinner(ui, 18.0);
                        }
                    }
                    ui.add_space(space::L);
                });
        });
        if close {
            self.browse.show_filters = false;
        }
        changed
    }
}

/// A source in the list: icon, name, description, and its pin and Latest buttons.
fn source_row(ui: &mut Ui, info: &SourceInfo, pinned: bool, actions: &mut Vec<Action>) {
    let p = Palette::of(ui.ctx());
    let (rect, response) = ui.allocate_exact_size(vec2(ui.available_width(), 60.0), Sense::click());
    response
        .widget_info(|| egui::WidgetInfo::labeled(egui::Role::Button, true, format!("Open {}", info.name)));
    if response.hovered() {
        ui.painter().rect_filled(
            rect.expand2(vec2(8.0, 0.0)),
            CornerRadius::same(radius::CONTROL),
            p.fill,
        );
    }
    let [r, g, b] = info.color;
    let icon = Rect::from_center_size(egui::pos2(rect.left() + 20.0, rect.center().y), vec2(36.0, 36.0));
    ui.painter()
        .rect_filled(icon, CornerRadius::same(9), Color32::from_rgb(r, g, b));
    ui.painter().text(
        icon.center(),
        Align2::CENTER_CENTER,
        info.name.chars().next().unwrap_or('?').to_string(),
        font(17.0, Weight::Bold),
        Color32::WHITE,
    );
    let x = icon.right() + space::M;
    ui.painter().text(
        egui::pos2(x, rect.top() + 18.0),
        Align2::LEFT_CENTER,
        info.name,
        text::headline(),
        p.label,
    );
    let mut sub = vec![
        info.description.to_owned(),
        if info.lang == "multi" {
            "Multi-language".into()
        } else {
            info.lang.to_uppercase()
        },
    ];
    if let Some(other) = info.reads_from {
        sub.push(format!("Reads chapters from {other}"));
    }
    ui.painter().text(
        egui::pos2(x, rect.top() + 40.0),
        Align2::LEFT_CENTER,
        sub.join("  ·  "),
        text::subheadline(),
        p.secondary_label,
    );

    // Trailing buttons sit on top of the row.
    let mut trailing = ui.new_child(
        egui::UiBuilder::new()
            .max_rect(Rect::from_min_max(
                egui::pos2(rect.right() - 200.0, rect.top()),
                rect.right_bottom(),
            ))
            .layout(egui::Layout::right_to_left(egui::Align::Center)),
    );
    let pin_label = if pinned { "Unpin" } else { "Pin" };
    let pin = widgets::button(
        &mut trailing,
        if pinned {
            ButtonStyle::Tinted
        } else {
            ButtonStyle::Plain
        },
        None,
        pin_label,
    );
    if pin.clicked() {
        actions.push(Action::TogglePin(info.id));
    }
    let mut latest_clicked = false;
    if info.supports_latest {
        latest_clicked = widgets::button(&mut trailing, ButtonStyle::Bordered, None, "Latest").clicked();
    }
    if latest_clicked {
        actions.push(Action::OpenSource(info.id, Listing::Latest));
    } else if response.clicked() && !pin.clicked() {
        actions.push(Action::OpenSource(info.id, Listing::Popular));
    }
    widgets::focus_ring(ui, &response, rect, radius::CONTROL);
    response
        .on_hover_cursor(CursorIcon::PointingHand)
        .context_menu(|ui| {
            if ui.button("Open Website").clicked() {
                actions.push(Action::OpenUrl(info.base_url.to_owned()));
                ui.close();
            }
        });
}

/// One source's results in a search across all sources.
#[allow(clippy::too_many_arguments)]
fn global_row(
    ui: &mut Ui,
    images: &mut Images,
    jobs: &Jobs,
    info: SourceInfo,
    state: &Loadable<Vec<Manga>>,
    query: &str,
    actions: &mut Vec<Action>,
) {
    let p = Palette::of(ui.ctx());
    ui.horizontal(|ui| {
        ui.label(RichText::new(info.name).font(text::title2()).color(p.label));
        ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
            if widgets::button(ui, ButtonStyle::Plain, Some(Icon::ChevronRight), "See All").clicked() {
                actions.push(Action::OpenSource(info.id, Listing::Search(query.to_owned())));
            }
        });
    });
    ui.add_space(space::S);
    match state {
        Loadable::Ready(list) if !list.is_empty() => {
            cover_strip(ui, images, jobs, ("global", info.id), list, false, None, actions)
        }
        Loadable::Ready(_) => {
            ui.label(
                RichText::new("No results")
                    .font(text::callout())
                    .color(p.secondary_label),
            );
        }
        Loadable::Failed(e) => {
            ui.label(
                RichText::new(e.as_str())
                    .font(text::callout())
                    .color(p.secondary_label),
            );
        }
        _ => {
            let (r, _) = ui.allocate_exact_size(vec2(ui.available_width(), 60.0), Sense::hover());
            widgets::paint_spinner(
                ui,
                Rect::from_center_size(r.center(), vec2(22.0, 22.0)),
                p.secondary_label,
            );
        }
    }
}

/// A ranked shelf such as a chart, with its period switch.
#[allow(clippy::too_many_arguments)]
fn shelf_row(
    ui: &mut Ui,
    images: &mut Images,
    jobs: &Jobs,
    source: &'static str,
    shelf: &ShelfInfo,
    period: u32,
    state: &Loadable<Vec<Manga>>,
    opening: Option<&str>,
    actions: &mut Vec<Action>,
) {
    let p = Palette::of(ui.ctx());
    ui.horizontal(|ui| {
        ui.label(RichText::new(shelf.title).font(text::title2()).color(p.label));
        if !shelf.periods.is_empty() {
            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                ui.push_id(shelf.id, |ui| {
                    let mut chosen = period;
                    if widgets::segmented(ui, &mut chosen, shelf.periods) {
                        actions.push(Action::ShelfPeriod(source, shelf.id, chosen));
                    }
                });
            });
        }
    });
    ui.add_space(space::S);
    match state {
        Loadable::Ready(list) if !list.is_empty() => cover_strip(
            ui,
            images,
            jobs,
            ("shelf", shelf.id),
            list,
            true,
            opening,
            actions,
        ),
        Loadable::Failed(e) => {
            ui.label(
                RichText::new(format!("Couldn’t load this list: {e}"))
                    .font(text::callout())
                    .color(p.secondary_label),
            );
        }
        Loadable::Ready(_) => {
            ui.label(
                RichText::new("Nothing here yet")
                    .font(text::callout())
                    .color(p.secondary_label),
            );
        }
        _ => {
            let (r, _) = ui.allocate_exact_size(
                vec2(ui.available_width(), cards::card_height(132.0)),
                Sense::hover(),
            );
            widgets::paint_spinner(
                ui,
                Rect::from_center_size(r.center(), vec2(22.0, 22.0)),
                p.secondary_label,
            );
        }
    }
}

/// A horizontally scrolling row of cover cards, optionally numbered.
#[allow(clippy::too_many_arguments)]
fn cover_strip(
    ui: &mut Ui,
    images: &mut Images,
    jobs: &Jobs,
    id: (&str, &str),
    list: &[Manga],
    ranked: bool,
    opening: Option<&str>,
    actions: &mut Vec<Action>,
) {
    let width = 132.0;
    egui::ScrollArea::horizontal().id_salt(id).show(ui, |ui| {
        ui.horizontal(|ui| {
            ui.spacing_mut().item_spacing.x = space::L;
            for (rank, manga) in list.iter().enumerate() {
                let caption = manga.caption.clone().unwrap_or_else(|| manga.byline());
                let r = cards::cover_card(ui, images, jobs, manga, width, &caption, false);
                let cover = Rect::from_min_size(r.rect.min, vec2(width, width * cards::COVER_ASPECT));
                if ranked {
                    rank_badge(ui, cover, rank + 1);
                }
                if opening == Some(manga.id.as_str()) {
                    busy_overlay(ui, r.rect, width);
                }
                if r.clicked() {
                    actions.push(Action::OpenManga(manga.clone()));
                }
            }
        });
        ui.add_space(space::S);
    });
}

fn busy_overlay(ui: &Ui, card: Rect, width: f32) {
    let cover = Rect::from_min_size(card.min, vec2(width, width * cards::COVER_ASPECT));
    ui.painter().rect_filled(
        cover,
        CornerRadius::same(radius::COVER),
        Color32::from_black_alpha(110),
    );
    widgets::paint_spinner(
        ui,
        Rect::from_center_size(cover.center(), vec2(24.0, 24.0)),
        Color32::WHITE,
    );
}

/// A rank number on a cover's corner; the top three use the accent color.
fn rank_badge(ui: &Ui, cover: Rect, rank: usize) {
    let p = Palette::of(ui.ctx());
    let galley = ui
        .painter()
        .layout_no_wrap(rank.to_string(), font(13.0, Weight::Bold), Color32::WHITE);
    let size = vec2(galley.size().x.max(10.0) + 14.0, 24.0);
    let r = Rect::from_min_size(cover.min + vec2(6.0, 6.0), size);
    let fill = if rank <= 3 {
        p.accent_fill
    } else {
        Color32::from_black_alpha(170)
    };
    ui.painter().rect_filled(r, CornerRadius::same(7), fill);
    ui.painter()
        .galley(r.center() - galley.size() / 2.0, galley, Color32::WHITE);
}
