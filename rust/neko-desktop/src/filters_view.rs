//! The Filters sheet beside Browse results.
//!
//! For MangaDex it uses Comix's vocabulary (genres, formats, types, statuses, demographics,
//! and sort orders) plus MangaDex's themes. For MangaBaka it exposes that catalog's own filters.
//! Changes apply after a short pause, the same way typing in the search field does.

use eframe::egui::{self, CornerRadius, Margin, RichText, ScrollArea, Ui, vec2};

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
                    };
                    ui.add_enabled_ui(active > 0, |ui| {
                        if widgets::button(ui, ButtonStyle::Plain, None, "Reset").clicked() {
                            match catalog {
                                Catalog::MangaDex => self.library.settings.search.clear(),
                                Catalog::MangaBaka => self.library.settings.baka = Default::default(),
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
                    }
                    ui.add_space(space::L);
                });
        });

        if close {
            self.browse.show_filters = false;
        }
        let changed = before_md != self.library.settings.search || before_baka != self.library.settings.baka;
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
