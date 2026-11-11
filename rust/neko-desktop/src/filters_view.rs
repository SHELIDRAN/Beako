//! The Filters sheet beside Browse results.
//!
//! For MangaDex it uses Comix's vocabulary (genres, formats, types, statuses, demographics,
//! and sort orders) plus MangaDex's themes. For MangaBaka it exposes that catalog's own filters.
//! Changes apply after a short pause, the same way typing in the search field does.

use eframe::egui::{self, CornerRadius, Margin, RichText, ScrollArea, Ui, vec2};

use crate::search::TagState;

use crate::api::Tag;
use crate::app::{App, Loadable};
use crate::library::Catalog;
use crate::search::{
    self, BAKA_GENRES, BAKA_SORTS, BAKA_STATUSES, BAKA_TYPES, COMIX_FORMATS, COMIX_GENRES, DEMOGRAPHICS,
    Kind, STATUSES, Sort,
};
use crate::theme::{Palette, space, text};
use crate::widgets::{self, ButtonStyle};

pub const SHEET_WIDTH: f32 = 340.0;

impl App {
    /// Draws the sheet. Returns true when a filter changed.
    pub fn filters_sheet(&mut self, ui: &mut Ui) -> bool {
        let p = Palette::of(ui.ctx());
        let before_md = self.library.settings.search.clone();
        let before_baka = self.library.settings.baka.clone();
        let before_comick = self.library.settings.comick.clone();
        let catalog = self.library.settings.catalog;
        let mut close = false;

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
            ui.horizontal(|ui| {
                ui.label(RichText::new("Filters").font(text::title2()).color(p.label));
                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    if widgets::button(ui, ButtonStyle::Plain, None, "Done").clicked() {
                        close = true;
                    }
                    let active = match catalog {
                        Catalog::MangaDex => self.library.settings.search.active_count(),
                        Catalog::MangaBaka => self.library.settings.baka.active_count(),
                        Catalog::Comick => self.library.settings.comick.active_count(),
                    };
                    ui.add_enabled_ui(active > 0, |ui| {
                        if widgets::button(ui, ButtonStyle::Plain, None, "Reset").clicked() {
                            match catalog {
                                Catalog::MangaDex => self.library.settings.search.clear(),
                                Catalog::MangaBaka => self.library.settings.baka = Default::default(),
                                Catalog::Comick => self.library.settings.comick = Default::default(),
                            }
                        }
                    });
                });
            });
            ui.add_space(space::S);
            ScrollArea::vertical()
                .id_salt("filters")
                .auto_shrink([false, false])
                .show(ui, |ui| {
                    ui.set_width(SHEET_WIDTH - 60.0);
                    match catalog {
                        Catalog::MangaDex => {
                            let tags = self.tags.clone();
                            mangadex_filters(ui, &mut self.library.settings.search, &tags);
                        }
                        Catalog::MangaBaka => baka_filters(ui, &mut self.library.settings.baka),
                        Catalog::Comick => {
                            let genres = self.comick.genres.clone();
                            comick_filters(
                                ui,
                                &mut self.library.settings.comick,
                                &genres,
                                &self.comick.tags,
                                &mut self.comick.tag_query,
                            );
                        }
                    }
                    ui.add_space(space::L);
                });
        });

        if close {
            self.browse.show_filters = false;
        }
        let changed = before_md != self.library.settings.search
            || before_baka != self.library.settings.baka
            || before_comick != self.library.settings.comick;
        if changed {
            self.library.save();
        }
        changed
    }
}

fn heading(ui: &mut Ui, label: &str, detail: Option<&str>) {
    let p = Palette::of(ui.ctx());
    ui.add_space(space::L);
    ui.label(RichText::new(label).font(text::headline()).color(p.label));
    if let Some(d) = detail {
        ui.label(
            RichText::new(d)
                .font(text::subheadline())
                .color(p.secondary_label),
        );
    }
    ui.add_space(space::XS);
}

fn wrap(ui: &mut Ui, add: impl FnOnce(&mut Ui)) {
    ui.horizontal_wrapped(|ui| {
        ui.spacing_mut().item_spacing = vec2(6.0, 6.0);
        add(ui);
    });
}

fn mangadex_filters(ui: &mut Ui, s: &mut search::Search, tags: &Loadable<Vec<Tag>>) {
    heading(ui, "Sort By", None);
    let current = s.sort.map_or("Default for the List", Sort::label);
    egui::ComboBox::from_id_salt("md-sort")
        .selected_text(current)
        .width(SHEET_WIDTH - 70.0)
        .show_ui(ui, |ui| {
            ui.selectable_value(&mut s.sort, None, "Default for the List");
            for sort in Sort::ALL {
                ui.selectable_value(&mut s.sort, Some(sort), sort.label());
            }
        });

    heading(ui, "Type", None);
    wrap(ui, |ui| {
        for kind in Kind::ALL {
            let on = s.kinds.contains(&kind);
            if widgets::choice_chip(ui, kind.label(), on).clicked() {
                if on {
                    s.kinds.remove(&kind);
                } else {
                    s.kinds.insert(kind);
                }
            }
        }
    });

    heading(ui, "Status", None);
    wrap(ui, |ui| {
        for (key, label) in STATUSES {
            toggle_in(ui, &mut s.statuses, key, label);
        }
    });

    heading(ui, "Demographic", None);
    wrap(ui, |ui| {
        for (key, label) in DEMOGRAPHICS {
            toggle_in(ui, &mut s.demographics, key, label);
        }
    });

    heading(ui, "Year", None);
    year_picker(ui, "md-year", &mut s.year, 140.0);

    heading(ui, "Genres", Some("Click once to include, twice to exclude."));
    wrap(ui, |ui| {
        for name in COMIX_GENRES {
            tag_chip(ui, s, name);
        }
    });

    heading(ui, "Formats", None);
    wrap(ui, |ui| {
        for name in COMIX_FORMATS {
            tag_chip(ui, s, name);
        }
    });

    heading(ui, "Themes", None);
    match tags {
        Loadable::Ready(list) => wrap(ui, |ui| {
            for t in list.iter().filter(|t| t.group == "theme" || t.group == "content") {
                tag_chip(ui, s, &t.name);
            }
        }),
        Loadable::Failed(e) => {
            ui.label(RichText::new(format!("Couldn’t load tags: {e}")).font(text::subheadline()));
        }
        _ => widgets::spinner(ui, 16.0),
    }

    if !s.include.is_empty() {
        heading(ui, "Included Tags Must", None);
        let mut all = s.match_all;
        widgets::segmented(ui, &mut all, &[(false, "Match Any"), (true, "Match All")]);
        s.match_all = all;
    }
}

fn tag_chip(ui: &mut Ui, s: &mut search::Search, name: &str) {
    let state = s.tag_state(name);
    if widgets::chip(ui, name, state).clicked() {
        s.set_tag(name, state.next());
    }
}

fn toggle_in(ui: &mut Ui, set: &mut std::collections::BTreeSet<String>, key: &str, label: &str) {
    let on = set.contains(key);
    if widgets::choice_chip(ui, label, on).clicked() {
        if on {
            set.remove(key);
        } else {
            set.insert(key.to_owned());
        }
    }
}

fn year_picker(ui: &mut Ui, id: &str, year: &mut Option<u32>, width: f32) {
    let this_year = 1970 + (crate::library::now() / 31_556_952) as u32;
    let label = year.map_or("Any Year".to_owned(), |y| y.to_string());
    egui::ComboBox::from_id_salt(id)
        .selected_text(label)
        .width(width)
        .height(320.0)
        .show_ui(ui, |ui| {
            ui.selectable_value(year, None, "Any Year");
            for y in (1960..=this_year + 1).rev() {
                ui.selectable_value(year, Some(y), y.to_string());
            }
        });
}

fn tri_state(ui: &mut Ui, id: &str, value: &mut Option<bool>, labels: [&str; 3]) {
    let mut v = *value;
    widgets::segmented(
        ui,
        &mut v,
        &[
            (None, labels[0]),
            (Some(true), labels[1]),
            (Some(false), labels[2]),
        ],
    );
    let _ = id;
    *value = v;
}

fn baka_filters(ui: &mut Ui, s: &mut search::BakaSearch) {
    let p = Palette::of(ui.ctx());
    heading(ui, "Sort By", None);
    let current = BAKA_SORTS
        .iter()
        .find(|(k, _)| *k == s.sort)
        .map_or("Most Popular", |(_, l)| l);
    egui::ComboBox::from_id_salt("baka-sort")
        .selected_text(current)
        .width(SHEET_WIDTH - 70.0)
        .show_ui(ui, |ui| {
            for (key, label) in BAKA_SORTS {
                ui.selectable_value(&mut s.sort, key.to_owned(), label);
            }
        });

    heading(ui, "Type", None);
    wrap(ui, |ui| {
        for (key, label) in BAKA_TYPES {
            toggle_in(ui, &mut s.types, key, label);
        }
    });

    heading(ui, "Status", None);
    wrap(ui, |ui| {
        for (key, label) in BAKA_STATUSES {
            toggle_in(ui, &mut s.statuses, key, label);
        }
    });

    heading(ui, "Minimum Rating", None);
    ui.horizontal(|ui| {
        widgets::slider(
            ui,
            &mut s.min_rating,
            0..=95,
            5,
            SHEET_WIDTH - 140.0,
            "Minimum rating",
        );
        let label = if s.min_rating == 0 {
            "Any".to_owned()
        } else {
            format!("★ {:.1}+", s.min_rating as f32 / 10.0)
        };
        ui.label(
            RichText::new(label)
                .font(text::callout())
                .color(p.secondary_label),
        );
    });

    heading(ui, "Minimum Chapters", None);
    ui.horizontal(|ui| {
        widgets::slider(
            ui,
            &mut s.min_chapters,
            0..=500,
            10,
            SHEET_WIDTH - 140.0,
            "Minimum chapters",
        );
        let label = if s.min_chapters == 0 {
            "Any".to_owned()
        } else {
            format!("{}+", s.min_chapters)
        };
        ui.label(
            RichText::new(label)
                .font(text::callout())
                .color(p.secondary_label),
        );
    });

    heading(ui, "Published", None);
    ui.horizontal(|ui| {
        year_picker(ui, "baka-from", &mut s.year_from, 105.0);
        ui.label(RichText::new("to").font(text::callout()).color(p.secondary_label));
        year_picker(ui, "baka-to", &mut s.year_to, 105.0);
    });

    heading(ui, "Genres", Some("Click once to include, twice to exclude."));
    wrap(ui, |ui| {
        for (key, label) in BAKA_GENRES {
            let state = s.genre_state(key);
            if widgets::chip(ui, label, state).clicked() {
                s.set_genre(key, state.next());
            }
        }
    });

    heading(ui, "Anime Adaptation", None);
    tri_state(ui, "anime", &mut s.has_anime, ["Any", "Has Anime", "No Anime"]);
    heading(ui, "Licensed in English", None);
    tri_state(ui, "licensed", &mut s.licensed, ["Any", "Licensed", "Unlicensed"]);
}

fn comick_filters(
    ui: &mut Ui,
    s: &mut crate::comick::ComickSearch,
    genres: &Loadable<Vec<crate::comick::Genre>>,
    tags: &Loadable<Vec<crate::comick::ComickTag>>,
    tag_query: &mut String,
) {
    use crate::comick::{CREATED, DEMOGRAPHICS, RATINGS, SORTS, STATUSES, TYPES};
    let p = Palette::of(ui.ctx());

    heading(ui, "Sort By", None);
    let current = SORTS
        .iter()
        .find(|(k, _)| *k == s.sort)
        .map_or("Most Followed", |(_, l)| l);
    egui::ComboBox::from_id_salt("comick-sort")
        .selected_text(current)
        .width(SHEET_WIDTH - 70.0)
        .show_ui(ui, |ui| {
            for (key, label) in SORTS {
                ui.selectable_value(&mut s.sort, key.to_owned(), label);
            }
        });

    // Tags: type to find one of Comick's thousands of community tags.
    heading(
        ui,
        "Tags",
        Some("Search, then click once to include or twice to exclude."),
    );
    let chosen: Vec<(String, String, TagState)> = s
        .tags
        .iter()
        .map(|(slug, name)| (slug.clone(), name.clone(), TagState::Include))
        .chain(
            s.excluded_tags
                .iter()
                .map(|(slug, name)| (slug.clone(), name.clone(), TagState::Exclude)),
        )
        .collect();
    if !chosen.is_empty() {
        wrap(ui, |ui| {
            for (slug, name, state) in &chosen {
                if widgets::chip(ui, name, *state).clicked() {
                    s.set_tag(slug, name, state.next());
                }
            }
        });
        ui.add_space(space::S);
    }
    widgets::search_field(ui, tag_query, "Search tags", SHEET_WIDTH - 70.0);
    ui.add_space(space::XS);
    match tags {
        Loadable::Ready(list) => {
            let needle = tag_query.trim().to_lowercase();
            let matches: Vec<&crate::comick::ComickTag> = list
                .iter()
                .filter(|t| needle.is_empty() || t.name.to_lowercase().contains(&needle))
                .filter(|t| s.tag_state(&t.slug) == TagState::Any)
                .take(if needle.is_empty() { 12 } else { 24 })
                .collect();
            if matches.is_empty() {
                ui.label(
                    RichText::new("No tags match")
                        .font(text::subheadline())
                        .color(p.secondary_label),
                );
            }
            for t in matches {
                let r = suggestion_row(ui, &t.name, t.count);
                if r.clicked() {
                    s.set_tag(&t.slug, &t.name, TagState::Include);
                } else if r.secondary_clicked() {
                    s.set_tag(&t.slug, &t.name, TagState::Exclude);
                }
            }
        }
        Loadable::Failed(e) => {
            ui.label(RichText::new(format!("Couldn’t load tags: {e}")).font(text::subheadline()));
        }
        _ => widgets::spinner(ui, 16.0),
    }

    heading(ui, "Type", None);
    wrap(ui, |ui| {
        for (key, label) in TYPES {
            toggle_in(ui, &mut s.countries, key, label);
        }
    });

    heading(ui, "Demographic", None);
    wrap(ui, |ui| {
        for (key, label) in DEMOGRAPHICS {
            let on = s.demographics.contains(&key);
            if widgets::choice_chip(ui, label, on).clicked() {
                if on {
                    s.demographics.remove(&key);
                } else {
                    s.demographics.insert(key);
                }
            }
        }
    });

    heading(ui, "Status", None);
    wrap(ui, |ui| {
        for (key, label) in STATUSES {
            let on = s.statuses.contains(&key);
            if widgets::choice_chip(ui, label, on).clicked() {
                if on {
                    s.statuses.remove(&key);
                } else {
                    s.statuses.insert(key);
                }
            }
        }
    });

    heading(
        ui,
        "Content Rating",
        Some("Limited by the content settings in Settings."),
    );
    wrap(ui, |ui| {
        for (key, label) in RATINGS {
            toggle_in(ui, &mut s.content_ratings, key, label);
        }
    });

    heading(ui, "Added to Comick", None);
    let added = CREATED
        .iter()
        .find(|(d, _)| *d == s.created_within)
        .map_or("Any Time", |(_, l)| l);
    egui::ComboBox::from_id_salt("comick-created")
        .selected_text(added)
        .width(160.0)
        .show_ui(ui, |ui| {
            for (days, label) in CREATED {
                ui.selectable_value(&mut s.created_within, days, label);
            }
        });

    heading(ui, "Chapters", None);
    ui.horizontal(|ui| {
        ui.label(
            RichText::new("At least")
                .font(text::callout())
                .color(p.secondary_label),
        );
        widgets::slider(
            ui,
            &mut s.min_chapters,
            0..=500,
            10,
            SHEET_WIDTH - 200.0,
            "Minimum chapters",
        );
        ui.label(
            RichText::new(if s.min_chapters == 0 {
                "Any".to_owned()
            } else {
                s.min_chapters.to_string()
            })
            .font(text::callout()),
        );
    });
    ui.horizontal(|ui| {
        ui.label(
            RichText::new("At most")
                .font(text::callout())
                .color(p.secondary_label),
        );
        ui.add_space(4.0);
        widgets::slider(
            ui,
            &mut s.max_chapters,
            0..=500,
            10,
            SHEET_WIDTH - 200.0,
            "Maximum chapters",
        );
        ui.label(
            RichText::new(if s.max_chapters == 0 {
                "Any".to_owned()
            } else {
                s.max_chapters.to_string()
            })
            .font(text::callout()),
        );
    });

    heading(ui, "Minimum Score", None);
    ui.horizontal(|ui| {
        widgets::slider(
            ui,
            &mut s.min_score,
            0..=9,
            1,
            SHEET_WIDTH - 140.0,
            "Minimum score",
        );
        let label = if s.min_score == 0 {
            "Any".to_owned()
        } else {
            format!("★ {}+", s.min_score)
        };
        ui.label(
            RichText::new(label)
                .font(text::callout())
                .color(p.secondary_label),
        );
    });

    heading(ui, "Published", None);
    ui.horizontal(|ui| {
        year_picker(ui, "comick-from", &mut s.year_from, 105.0);
        ui.label(RichText::new("to").font(text::callout()).color(p.secondary_label));
        year_picker(ui, "comick-to", &mut s.year_to, 105.0);
    });

    match genres {
        Loadable::Ready(list) => {
            for (group, title) in [
                ("genre", "Genres"),
                ("theme", "Themes"),
                ("format", "Formats"),
                ("content", "Content"),
            ] {
                let items: Vec<&crate::comick::Genre> = list.iter().filter(|g| g.group == group).collect();
                if items.is_empty() {
                    continue;
                }
                heading(
                    ui,
                    title,
                    (group == "genre").then_some("Click once to include, twice to exclude."),
                );
                wrap(ui, |ui| {
                    for g in items {
                        let state = s.genre_state(&g.slug);
                        if widgets::chip(ui, &g.name, state).clicked() {
                            s.set_genre(&g.slug, state.next());
                        }
                    }
                });
            }
        }
        Loadable::Failed(e) => {
            heading(ui, "Genres", None);
            ui.label(RichText::new(format!("Couldn’t load genres: {e}")).font(text::subheadline()));
        }
        _ => {
            heading(ui, "Genres", None);
            widgets::spinner(ui, 16.0);
        }
    }
}

/// A row in the tag suggestions: the name and how many titles use it. Right-click excludes.
fn suggestion_row(ui: &mut Ui, name: &str, count: u32) -> egui::Response {
    let p = Palette::of(ui.ctx());
    let (rect, response) = ui.allocate_exact_size(vec2(ui.available_width(), 30.0), egui::Sense::click());
    response
        .widget_info(|| egui::WidgetInfo::labeled(egui::Role::Button, true, format!("Include tag {name}")));
    if response.hovered() {
        ui.painter()
            .rect_filled(rect, CornerRadius::same(crate::theme::radius::CONTROL), p.fill);
    }
    let plus = egui::Rect::from_center_size(
        egui::pos2(rect.left() + 14.0, rect.center().y),
        egui::Vec2::splat(12.0),
    );
    widgets::paint_icon(ui.painter(), crate::widgets::Icon::Plus, plus, p.accent);
    ui.painter().text(
        egui::pos2(rect.left() + 30.0, rect.center().y),
        egui::Align2::LEFT_CENTER,
        name,
        text::callout(),
        p.label,
    );
    ui.painter().text(
        egui::pos2(rect.right() - 8.0, rect.center().y),
        egui::Align2::RIGHT_CENTER,
        count.to_string(),
        text::subheadline(),
        p.secondary_label,
    );
    widgets::focus_ring(ui, &response, rect, crate::theme::radius::CONTROL);
    response
        .on_hover_text("Click to include. Right-click to exclude.")
        .on_hover_cursor(egui::CursorIcon::PointingHand)
}
