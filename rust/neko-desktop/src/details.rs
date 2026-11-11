//! Title details: cover, metadata, description, and the chapter list.

use eframe::egui::{
    self, CornerRadius, CursorIcon, Rect, RichText, Role, Sense, Ui, Vec2, WidgetInfo, pos2, vec2,
};

use crate::api::{Chapter, Manga, READING_STATUSES, language_name, reading_status_label, status_label};
use crate::app::{Action, App, Loadable, page_frame};
use crate::cards::{self, COVER_ASPECT};
use crate::theme::{Palette, Weight, font, radius, space, text};
use crate::widgets::{self, ButtonStyle, Icon};

const ROW_HEIGHT: f32 = 56.0;

impl App {
    pub fn details_view(&mut self, ui: &mut Ui) {
        let Some(details) = self.details.as_mut() else {
            return;
        };
        let p = Palette::of(ui.ctx());
        let manga = details.manga.clone();
        let in_library = self.library.contains(&manga.id);
        let chapters = self
            .chapters
            .get(&manga.id)
            .map(|(_, c)| c.clone())
            .unwrap_or_default();
        let language = self.library.settings.filters.language.clone();
        let progress = self.library.progress(&manga.id).cloned().unwrap_or_default();
        let mut newest_first = details.newest_first;
        let mut expanded = details.expanded;
        let signed_in = self.account.username.is_some();
        let status = self.library.statuses.get(&manga.id).cloned();
        let actions = &mut self.actions;
        let images = &mut self.images;
        let jobs = &self.jobs;

        egui::ScrollArea::vertical()
            .id_salt(("details", &manga.id))
            .auto_shrink([false, false])
            .show(ui, |ui| {
                // A wash of the cover's colors behind the header, fading into the page.
                let hero = Rect::from_min_size(ui.min_rect().min, vec2(ui.available_width(), 460.0));
                cards::paint_cover_backdrop(ui, images, jobs, &manga, hero, 1.0);
                paint_hero_fade(ui, hero, &p);
                page_frame().show(ui, |ui| {
                    let width = ui.available_width();
                    ui.set_width(width);
                    ui.add_space(space::M);
                    if widgets::button(ui, ButtonStyle::Plain, Some(Icon::ChevronLeft), "Back").clicked() {
                        actions.push(Action::Back);
                    }
                    ui.add_space(space::M);

                    // Header: cover beside title, metadata, and actions.
                    let cover_w = if width > 760.0 { 220.0 } else { 160.0 };
                    ui.horizontal_top(|ui| {
                        let (cover, _) =
                            ui.allocate_exact_size(vec2(cover_w, cover_w * COVER_ASPECT), Sense::hover());
                        let shadow = egui::epaint::Shadow {
                            offset: [0, 10],
                            blur: 30,
                            spread: 0,
                            color: p.shadow,
                        };
                        ui.painter()
                            .add(shadow.as_shape(cover, CornerRadius::same(radius::GROUP)));
                        cards::paint_cover(ui, images, jobs, &manga, cover, radius::GROUP);
                        ui.add_space(space::XL);
                        ui.vertical(|ui| {
                            ui.set_max_width(width - cover_w - space::XL);
                            header_text(ui, &manga, &p);
                            ui.add_space(space::L);
                            ui.horizontal(|ui| {
                                let (label, target) = read_target(&chapters, &progress);
                                ui.add_enabled_ui(target.is_some(), |ui| {
                                    if widgets::button(ui, ButtonStyle::Prominent, Some(Icon::Book), &label)
                                        .clicked()
                                        && let Some((chapter_id, page)) = target
                                    {
                                        actions.push(Action::Read {
                                            manga: manga.clone(),
                                            chapter_id,
                                            page,
                                        });
                                    }
                                });
                                ui.add_space(space::XS);
                                let (icon, label) = if in_library {
                                    (Icon::Check, "In Library")
                                } else {
                                    (Icon::Plus, "Add to Library")
                                };
                                let r = widgets::button(ui, ButtonStyle::Bordered, Some(icon), label);
                                let r = if in_library {
                                    r.on_hover_text("Remove from your library")
                                } else {
                                    r
                                };
                                if r.clicked() {
                                    actions.push(Action::ToggleLibrary(manga.clone()));
                                }
                                if signed_in {
                                    ui.add_space(space::XS);
                                    status_picker(ui, &manga.id, status.as_deref(), actions);
                                }
                            });
                        });
                    });

                    if !manga.description.is_empty() {
                        ui.add_space(space::XL);
                        let max_rows = if expanded { usize::MAX } else { 4 };
                        let desc_w = width.min(760.0);
                        let galley = widgets::wrapped_text(
                            ui,
                            &manga.description,
                            text::body(),
                            p.label,
                            desc_w,
                            max_rows,
                        );
                        let truncated = galley.rows.len() >= 4 && !expanded && galley.elided;
                        let (r, _) = ui.allocate_exact_size(galley.size(), Sense::hover());
                        ui.painter().galley(r.min, galley, p.label);
                        if truncated || expanded {
                            let label = if expanded { "Less" } else { "More" };
                            if widgets::button(ui, ButtonStyle::Plain, None, label).clicked() {
                                expanded = !expanded;
                            }
                        }
                    }

                    ui.add_space(space::XXL);
                    chapter_section(
                        ui,
                        width,
                        &manga,
                        &chapters,
                        &language,
                        &progress,
                        &mut newest_first,
                        actions,
                    );
                    ui.add_space(space::XXL);
                });
            });

        if let Some(d) = self.details.as_mut() {
            d.newest_first = newest_first;
            d.expanded = expanded;
        }
    }
}

fn header_text(ui: &mut Ui, manga: &Manga, p: &Palette) {
    let width = ui.available_width();
    let title = widgets::wrapped_text(ui, &manga.title, text::title1(), p.label, width, 3);
    let (r, _) = ui.allocate_exact_size(title.size(), Sense::hover());
    ui.painter().galley(r.min, title, p.label);
    ui.add_space(2.0);
    if let Some(alt) = manga
        .alt_title
        .as_ref()
        .filter(|a| !a.eq_ignore_ascii_case(&manga.title))
    {
        ui.label(RichText::new(alt).font(text::callout()).color(p.secondary_label));
    }
    let byline = manga.byline();
    if !byline.is_empty() {
        ui.add_space(space::XS);
        let g = widgets::wrapped_text(ui, &byline, font(15.0, Weight::Medium), p.accent, width, 2);
        let (r, _) = ui.allocate_exact_size(g.size(), Sense::hover());
        ui.painter().galley(r.min, g, p.accent);
    }
    let mut meta: Vec<String> = Vec::new();
    if let Some(s) = &manga.status {
        meta.push(status_label(s).to_owned());
    }
    if let Some(y) = manga.year {
        meta.push(y.to_string());
    }
    if let Some(d) = &manga.demographic {
        let mut c = d.chars();
        meta.push(
            c.next()
                .map(|f| f.to_uppercase().collect::<String>() + c.as_str())
                .unwrap_or_default(),
        );
    }
    if let Some(lang) = &manga.original_language {
        meta.push(language_name(lang));
    }
    if !meta.is_empty() {
        ui.add_space(space::XS);
        ui.label(
            RichText::new(meta.join("  ·  "))
                .font(text::callout())
                .color(p.secondary_label),
        );
    }
    if !manga.tags.is_empty() {
        ui.add_space(space::M);
        ui.horizontal_wrapped(|ui| {
            ui.spacing_mut().item_spacing = vec2(6.0, 6.0);
            for t in manga.tags.iter().take(10) {
                widgets::tag(ui, t);
            }
        });
    }
}

/// The primary button's label and the chapter and page it opens.
fn read_target(
    chapters: &Loadable<Vec<Chapter>>,
    progress: &crate::library::Progress,
) -> (String, Option<(String, usize)>) {
    let Some(list) = chapters.ready() else {
        return ("Read".into(), None);
    };
    let readable: Vec<&Chapter> = list.iter().filter(|c| c.external_url.is_none()).collect();
    if let Some(current) = progress
        .chapter_id
        .as_ref()
        .and_then(|id| readable.iter().find(|c| &c.id == id))
    {
        if !progress.read.contains(&current.id) {
            return (
                format!("Continue {}", current.short_name()),
                Some((current.id.clone(), progress.page)),
            );
        }
        // Finished that chapter: offer the next one with a different number.
        let pos = readable.iter().position(|c| c.id == current.id).unwrap_or(0);
        if let Some(next) = readable[pos..]
            .iter()
            .find(|c| c.chapter != current.chapter && !progress.read.contains(&c.id))
        {
            return (format!("Read {}", next.short_name()), Some((next.id.clone(), 0)));
        }
    }
    match readable.iter().find(|c| !progress.read.contains(&c.id)) {
        Some(c) if progress.read.is_empty() => ("Start Reading".into(), Some((c.id.clone(), 0))),
        Some(c) => (format!("Read {}", c.short_name()), Some((c.id.clone(), 0))),
        None => match readable.first() {
            Some(c) => ("Read Again".into(), Some((c.id.clone(), 0))),
            None => ("No Chapters to Read".into(), None),
        },
    }
}

#[allow(clippy::too_many_arguments)]
fn chapter_section(
    ui: &mut Ui,
    width: f32,
    manga: &Manga,
    chapters: &Loadable<Vec<Chapter>>,
    language: &str,
    progress: &crate::library::Progress,
    newest_first: &mut bool,
    actions: &mut Vec<Action>,
) {
    let p = Palette::of(ui.ctx());
    let count = chapters.ready().map(Vec::len);
    ui.allocate_ui(vec2(width, 32.0), |ui| {
        ui.horizontal(|ui| {
            ui.label(RichText::new("Chapters").font(text::title2()).color(p.label));
            let sub = match count {
                Some(n) => format!("{n} in {}", language_name(language)),
                None => language_name(language),
            };
            ui.label(RichText::new(sub).font(text::callout()).color(p.secondary_label));
            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                let label = if *newest_first {
                    "Newest First"
                } else {
                    "Oldest First"
                };
                if widgets::button(ui, ButtonStyle::Plain, Some(Icon::ArrowUpDown), label).clicked() {
                    *newest_first = !*newest_first;
                }
            });
        })
    });
    ui.add_space(space::S);

    let list = match chapters {
        Loadable::Ready(list) => list,
        Loadable::Failed(e) => {
            if widgets::placeholder_view(ui, Icon::Retry, "Couldn’t Load Chapters", e, Some("Try Again")) {
                actions.push(Action::ReloadChapters(manga.id.clone()));
            }
            return;
        }
        _ => {
            ui.add_space(space::XL);
            ui.vertical_centered(|ui| widgets::spinner(ui, 22.0));
            return;
        }
    };
    if list.is_empty() {
        let msg = format!(
            "This title has no chapters in {} on MangaDex. You can change the chapter language in Settings.",
            language_name(language)
        );
        widgets::placeholder_view(ui, Icon::Book, "No Chapters", &msg, None);
        return;
    }

    // An inset grouped list. Only rows inside the viewport are drawn, so titles with thousands
    // of chapters scroll smoothly.
    let (group, _) = ui.allocate_exact_size(vec2(width, ROW_HEIGHT * list.len() as f32), Sense::hover());
    ui.painter()
        .rect_filled(group, CornerRadius::same(radius::GROUP), p.grouped_background);
    let origin = group.top();
    let clip = ui.clip_rect();
    let first = (((clip.top() - origin) / ROW_HEIGHT).floor().max(0.0)) as usize;
    let last = ((((clip.bottom() - origin) / ROW_HEIGHT).ceil().max(0.0)) as usize).min(list.len());

    for i in first..last {
        let idx = if *newest_first { list.len() - 1 - i } else { i };
        let chapter = &list[idx];
        let rect = Rect::from_min_size(
            pos2(group.left(), origin + i as f32 * ROW_HEIGHT),
            vec2(width, ROW_HEIGHT),
        );
        let read = progress.read.contains(&chapter.id);
        let current = progress.chapter_id.as_deref() == Some(chapter.id.as_str()) && !read;
        let response = ui.interact(rect, ui.scope_id().with(("chapter", &chapter.id)), Sense::click());
        response.widget_info(|| {
            let state = if read { ", read" } else { "" };
            WidgetInfo::labeled(Role::Button, true, format!("{}{state}", chapter.display_name()))
        });
        let corner = row_corners(i, list.len());
        if response.hovered() {
            ui.painter().rect_filled(rect, corner, p.fill);
        }
        if i + 1 < list.len() {
            let y = rect.bottom() - 0.5;
            ui.painter().hline(
                (rect.left() + 44.0)..=(rect.right() - 16.0),
                y,
                egui::Stroke::new(1.0, p.separator),
            );
        }

        // Leading status: a filled dot for where people left off, a check for read chapters.
        let status = Rect::from_center_size(pos2(rect.left() + 22.0, rect.center().y), Vec2::splat(16.0));
        if read {
            widgets::paint_icon(ui.painter(), Icon::Check, status, p.secondary_label);
        } else if current {
            ui.painter().circle_filled(status.center(), 4.5, p.accent);
        }

        let title_color = if read { p.secondary_label } else { p.label };
        let title_font = if read { text::body() } else { text::headline() };
        let text_w = width - 44.0 - 56.0;
        let title = widgets::wrapped_text(ui, &chapter.display_name(), title_font, title_color, text_w, 1);
        ui.painter()
            .galley(pos2(rect.left() + 44.0, rect.top() + 10.0), title, title_color);
        let mut sub = Vec::new();
        if let Some(g) = &chapter.group {
            sub.push(g.clone());
        }
        let date = chapter.published_date();
        if !date.is_empty() {
            sub.push(date);
        }
        if current && progress.page > 0 {
            sub.push(format!("Page {}", progress.page + 1));
        }
        if chapter.external_url.is_some() {
            sub.push("Opens on the publisher’s site".into());
        }
        let sub = widgets::wrapped_text(
            ui,
            &sub.join("  ·  "),
            text::subheadline(),
            p.secondary_label,
            text_w,
            1,
        );
        ui.painter().galley(
            pos2(rect.left() + 44.0, rect.top() + 31.0),
            sub,
            p.secondary_label,
        );

        let trailing = Rect::from_center_size(pos2(rect.right() - 24.0, rect.center().y), Vec2::splat(14.0));
        let icon = if chapter.external_url.is_some() {
            Icon::External
        } else {
            Icon::ChevronRight
        };
        widgets::paint_icon(ui.painter(), icon, trailing, p.secondary_label);
        widgets::focus_ring(ui, &response, rect.shrink(2.0), radius::CONTROL);

        if response.clicked() {
            match &chapter.external_url {
                Some(url) => {
                    actions.push(Action::OpenUrl(url.clone()));
                    actions.push(Action::SetRead {
                        manga_id: manga.id.clone(),
                        chapter_id: chapter.id.clone(),
                        read: true,
                    });
                }
                None => {
                    let page = if current { progress.page } else { 0 };
                    actions.push(Action::Read {
                        manga: manga.clone(),
                        chapter_id: chapter.id.clone(),
                        page,
                    });
                }
            }
        }
        response
            .on_hover_cursor(CursorIcon::PointingHand)
            .context_menu(|ui| {
                let label = if read { "Mark as Unread" } else { "Mark as Read" };
                if ui.button(label).clicked() {
                    actions.push(Action::SetRead {
                        manga_id: manga.id.clone(),
                        chapter_id: chapter.id.clone(),
                        read: !read,
                    });
                    ui.close();
                }
                if ui.button("Mark Previous Chapters as Read").clicked() {
                    actions.push(Action::MarkPreviousRead {
                        manga_id: manga.id.clone(),
                        chapter_id: chapter.id.clone(),
                    });
                    ui.close();
                }
            });
    }
}

fn row_corners(i: usize, len: usize) -> CornerRadius {
    let r = radius::GROUP;
    let mut c = CornerRadius::ZERO;
    if i == 0 {
        c.nw = r;
        c.ne = r;
    }
    if i + 1 == len {
        c.sw = r;
        c.se = r;
    }
    c
}

/// Fades the hero backdrop into the page and keeps text over it readable: a veil of the
/// background color at 72 % over the whole area, rising to opaque at the bottom.
fn paint_hero_fade(ui: &Ui, rect: Rect, p: &Palette) {
    let bg = p.background;
    let veil = egui::Color32::from_rgba_unmultiplied(bg.r(), bg.g(), bg.b(), 184);
    let mut mesh = egui::Mesh::default();
    let mid = rect.top() + rect.height() * 0.55;
    for (y, color) in [(rect.top(), veil), (mid, veil), (rect.bottom(), bg)] {
        mesh.colored_vertex(pos2(rect.left(), y), color);
        mesh.colored_vertex(pos2(rect.right(), y), color);
    }
    for i in 0..2u32 {
        let b = i * 2;
        mesh.add_triangle(b, b + 1, b + 2);
        mesh.add_triangle(b + 1, b + 3, b + 2);
    }
    ui.painter().add(egui::Shape::mesh(mesh));
}

/// MangaDex's reading status for this title, as a pop-up button.
fn status_picker(ui: &mut Ui, manga_id: &str, current: Option<&str>, actions: &mut Vec<Action>) {
    let label = current.map_or("No Status", reading_status_label);
    let mut choice = current.map(str::to_owned);
    egui::ComboBox::from_id_salt(("status", manga_id))
        .selected_text(label)
        .width(150.0)
        .show_ui(ui, |ui| {
            ui.selectable_value(&mut choice, None, "No Status");
            for (key, name) in READING_STATUSES {
                ui.selectable_value(&mut choice, Some(key.to_owned()), name);
            }
        })
        .response
        .on_hover_text("Reading status on MangaDex");
    if choice.as_deref() != current {
        actions.push(Action::SetStatus {
            manga_id: manga_id.to_owned(),
            status: choice,
        });
    }
}
