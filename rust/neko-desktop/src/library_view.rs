//! Library: a Continue Reading shelf, then every saved title, most recently read first.

use eframe::egui::{self, CornerRadius, CursorIcon, Rect, Role, Sense, Ui, WidgetInfo, pos2, vec2};

use crate::api::{CoverSize, Manga, READING_STATUSES, reading_status_label};
use crate::app::{Action, App, Section, page_header};
use crate::cards;
use crate::images::{ImageState, Images};
use crate::jobs::{ImageKind, Jobs};
use crate::library::Progress;
use crate::theme::{Palette, Weight, font, radius, space, text};
use crate::widgets::{self, ButtonStyle, Icon};

const SHELF_CARD: egui::Vec2 = egui::Vec2::new(300.0, 132.0);

impl App {
    pub fn library_view(&mut self, ui: &mut Ui) {
        let all: Vec<Manga> = self
            .library
            .sorted_entries()
            .into_iter()
            .map(|e| e.manga.clone())
            .collect();
        if all.is_empty() {
            crate::app::page_frame().show(ui, |ui| {
                page_header(ui, "Library", None, |ui| self.sync_button(ui));
                let browse = widgets::placeholder_view(
                    ui,
                    Icon::Library,
                    "Your Library Is Empty",
                    "Titles you add from Browse appear here, with your place saved in each one. \
                     Sign in to MangaDex in Settings to bring in the titles you follow.",
                    Some("Browse MangaDex"),
                );
                if browse {
                    self.actions.push(Action::Section(Section::Browse));
                }
            });
            return;
        }

        // Filter by MangaDex reading status, when there are any.
        let filter_id = egui::Id::unique("library-status-filter");
        let mut filter: Option<String> = ui.ctx().data(|d| d.get_temp(filter_id)).unwrap_or(None);
        let has_statuses = all.iter().any(|m| self.library.statuses.contains_key(&m.id));
        if !has_statuses {
            filter = None;
        }
        let entries: Vec<Manga> = match &filter {
            Some(f) => all
                .iter()
                .filter(|m| self.library.statuses.get(&m.id) == Some(f))
                .cloned()
                .collect(),
            None => all.clone(),
        };

        let continue_reading: Vec<(Manga, Progress)> = self
            .recent_titles(8)
            .into_iter()
            .filter_map(|m| {
                let p = self.library.progress(&m.id)?.clone();
                let done = p.chapter_id.as_ref().is_some_and(|c| p.read.contains(c));
                (!done).then_some((m, p))
            })
            .collect();

        let count = entries.len();
        let subtitle = if count == 1 {
            "1 title".to_owned()
        } else {
            format!("{count} titles")
        };
        let syncing = self.account.syncing;
        let signed_in = self.signed_in();
        // The header and the cards both draw covers and queue actions.
        let images = std::cell::RefCell::new(&mut self.images);
        let jobs = &self.jobs;
        let library = &self.library;
        let actions = std::cell::RefCell::new(&mut self.actions);
        let mut new_filter = filter.clone();
        cards::grid(
            ui,
            "library-grid",
            &entries,
            |ui| {
                page_header(ui, "Library", Some(&subtitle), |ui| {
                    if signed_in {
                        if syncing {
                            widgets::spinner(ui, 18.0);
                        } else if widgets::button(ui, ButtonStyle::Bordered, Some(Icon::Sync), "Sync")
                            .clicked()
                        {
                            actions.borrow_mut().push(Action::Sync);
                        }
                    }
                });
                if !continue_reading.is_empty() && filter.is_none() {
                    let p = Palette::of(ui.ctx());
                    ui.label(
                        egui::RichText::new("Continue Reading")
                            .font(text::title2())
                            .color(p.label),
                    );
                    ui.add_space(space::M);
                    egui::ScrollArea::horizontal().id_salt("shelf").show(ui, |ui| {
                        ui.horizontal(|ui| {
                            ui.spacing_mut().item_spacing.x = space::L;
                            for (manga, progress) in &continue_reading {
                                if shelf_card(ui, &mut images.borrow_mut(), jobs, manga, progress).clicked() {
                                    actions.borrow_mut().push(Action::OpenManga(manga.clone()));
                                }
                            }
                        });
                        ui.add_space(space::M);
                    });
                    ui.add_space(space::L);
                }
                if has_statuses {
                    ui.horizontal_wrapped(|ui| {
                        ui.spacing_mut().item_spacing = vec2(6.0, 6.0);
                        if widgets::choice_chip(ui, "All", new_filter.is_none()).clicked() {
                            new_filter = None;
                        }
                        for (key, label) in READING_STATUSES {
                            let n = library.statuses.values().filter(|s| *s == key).count();
                            if n == 0 {
                                continue;
                            }
                            let on = new_filter.as_deref() == Some(key);
                            if widgets::choice_chip(ui, &format!("{label} {n}"), on).clicked() {
                                new_filter = Some(key.to_owned());
                            }
                        }
                    });
                    ui.add_space(space::L);
                } else if !continue_reading.is_empty() {
                    let p = Palette::of(ui.ctx());
                    ui.label(
                        egui::RichText::new("All Titles")
                            .font(text::title2())
                            .color(p.label),
                    );
                    ui.add_space(space::M);
                }
            },
            |ui, manga, width| {
                let progress = library.progress(&manga.id).and_then(|p| p.chapter_name.clone());
                let caption = match (library.statuses.get(&manga.id), progress) {
                    (Some(s), Some(name)) => format!("{} · {name}", reading_status_label(s)),
                    (Some(s), None) => reading_status_label(s).to_owned(),
                    (None, Some(name)) => format!("Continue {name}"),
                    (None, None) => "Not started".to_owned(),
                };
                let r = cards::cover_card(ui, &mut images.borrow_mut(), jobs, manga, width, &caption, false);
                if r.clicked() {
                    actions.borrow_mut().push(Action::OpenManga(manga.clone()));
                }
                r.context_menu(|ui| {
                    if ui.button("Remove from Library").clicked() {
                        actions.borrow_mut().push(Action::ToggleLibrary(manga.clone()));
                        ui.close();
                    }
                });
            },
            |_| {},
        );
        if new_filter != filter {
            ui.ctx().data_mut(|d| d.insert_temp(filter_id, new_filter));
        }
    }

    fn sync_button(&mut self, ui: &mut Ui) {
        if !self.signed_in() {
            return;
        }
        if self.account.syncing {
            widgets::spinner(ui, 18.0);
        } else if widgets::button(ui, ButtonStyle::Bordered, Some(Icon::Sync), "Sync").clicked() {
            self.actions.push(Action::Sync);
        }
    }
}

/// A wide card on the Continue Reading shelf: the cover's colors behind the cover, title,
/// and where you left off.
fn shelf_card(
    ui: &mut Ui,
    images: &mut Images,
    jobs: &Jobs,
    manga: &Manga,
    progress: &Progress,
) -> egui::Response {
    let p = Palette::of(ui.ctx());
    let (rect, response) = ui.allocate_exact_size(SHELF_CARD, Sense::click());
    response.widget_info(|| WidgetInfo::labeled(Role::Button, true, format!("Continue {}", manga.title)));
    let corner = CornerRadius::same(14);
    let lift = ui.ctx().animate_bool_responsive(response.id, response.hovered());
    let shadow = egui::epaint::Shadow {
        offset: [0, (4.0 + 6.0 * lift) as i8],
        blur: (14.0 + 12.0 * lift) as u8,
        spread: 0,
        color: p.shadow,
    };
    ui.painter().add(shadow.as_shape(rect, corner));
    ui.painter().rect_filled(rect, corner, p.elevated);
    // Blurred cover as the card's material.
    if let Some(url) = manga.cover_url(CoverSize::Small)
        && let ImageState::Ready(loaded) = images.get(jobs, ui.ctx(), &url, ImageKind::CoverBlur)
    {
        let fade = loaded.fade(ui.input(|i| i.time));
        let uv = cards::aspect_fill_uv(crate::images::texture_size(&loaded.texture), rect.size());
        egui::Image::new(egui::load::SizedTexture::from_handle(&loaded.texture))
            .uv(uv)
            .corner_radius(corner)
            .tint(egui::Color32::from_white_alpha((255.0 * fade) as u8))
            .paint_at(ui, rect);
    }
    // A veil keeps the text readable over any cover.
    let veil = if p.dark {
        egui::Color32::from_black_alpha(120)
    } else {
        egui::Color32::from_white_alpha(150)
    };
    ui.painter().rect(
        rect,
        corner,
        veil,
        egui::Stroke::new(1.0, p.separator),
        egui::StrokeKind::Inside,
    );

    let cover = Rect::from_min_size(rect.min + vec2(12.0, 12.0), vec2(72.0, 108.0));
    cards::paint_cover(ui, images, jobs, manga, cover, radius::CONTROL);
    let x = cover.right() + space::M;
    let w = rect.right() - x - space::M;
    let title = widgets::wrapped_text(ui, &manga.title, font(14.0, Weight::Semibold), p.label, w, 2);
    let title_h = title.size().y;
    ui.painter().galley(pos2(x, rect.top() + 16.0), title, p.label);
    let mut where_ = progress
        .chapter_name
        .clone()
        .unwrap_or_else(|| "Start reading".into());
    if progress.page > 0 {
        where_.push_str(&format!(" · Page {}", progress.page + 1));
    }
    let sub = widgets::wrapped_text(ui, &where_, text::subheadline(), p.secondary_label, w, 1);
    ui.painter()
        .galley(pos2(x, rect.top() + 20.0 + title_h), sub, p.secondary_label);
    // A small "Continue" capsule anchored to the bottom.
    let pill = Rect::from_min_size(pos2(x, rect.bottom() - 40.0), vec2(96.0, 28.0));
    ui.painter()
        .rect_filled(pill, CornerRadius::same(radius::CAPSULE), p.accent_fill);
    ui.painter().text(
        pill.center(),
        egui::Align2::CENTER_CENTER,
        "Continue",
        font(13.0, Weight::Semibold),
        egui::Color32::WHITE,
    );
    widgets::focus_ring(ui, &response, rect, 14);
    response.on_hover_cursor(CursorIcon::PointingHand)
}
