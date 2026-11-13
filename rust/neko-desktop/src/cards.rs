//! Cover art, cover cards, and the adaptive grid that holds them.

use eframe::egui::{
    self, CornerRadius, CursorIcon, Rect, Response, Role, Sense, Ui, Vec2, WidgetInfo, pos2, vec2,
};

use crate::api::{CoverSize, Manga};
use crate::images::{ImageState, Images, texture_size};
use crate::jobs::{ImageKind, Jobs};
use crate::theme::{Palette, Weight, font, radius, space, text};
use crate::widgets::{self, Icon};

/// Covers are 2:3 portrait.
pub const COVER_ASPECT: f32 = 1.5;

/// Paints a cover filling `rect` (cropping to fit), or a placeholder while it loads.
pub fn paint_cover(ui: &Ui, images: &mut Images, jobs: &Jobs, manga: &Manga, rect: Rect, corner: u8) {
    let p = Palette::of(ui.ctx());
    let size = if rect.width() > 140.0 {
        CoverSize::Large
    } else {
        CoverSize::Small
    };
    let state = manga
        .cover_url(size)
        .map(|url| images.get(jobs, ui.ctx(), &url, ImageKind::Cover));
    match state {
        Some(ImageState::Ready(loaded)) => {
            let now = ui.input(|i| i.time);
            let fade = loaded.fade(now);
            if fade < 1.0 {
                ui.painter()
                    .rect_filled(rect, CornerRadius::same(corner), p.placeholder);
                ui.ctx().request_repaint();
            }
            let uv = aspect_fill_uv(texture_size(&loaded.texture), rect.size());
            egui::Image::new(egui::load::SizedTexture::from_handle(&loaded.texture))
                .uv(uv)
                .tint(egui::Color32::from_white_alpha((255.0 * fade) as u8))
                .corner_radius(CornerRadius::same(corner))
                .paint_at(ui, rect);
        }
        _ => {
            ui.painter()
                .rect_filled(rect, CornerRadius::same(corner), p.placeholder);
            if rect.width() > 60.0 {
                let icon = Rect::from_center_size(rect.center(), Vec2::splat(rect.width() * 0.22));
                widgets::paint_icon(
                    ui.painter(),
                    Icon::Book,
                    icon,
                    p.secondary_label.gamma_multiply(0.5),
                );
            }
        }
    }
    // A hairline keeps light covers from bleeding into a white window.
    ui.painter().rect_stroke(
        rect,
        CornerRadius::same(corner),
        egui::Stroke::new(1.0, p.label.gamma_multiply(0.08)),
        egui::StrokeKind::Inside,
    );
}

/// UV rect that crops a texture to fill `target` without distortion.
pub fn aspect_fill_uv(tex: Vec2, target: Vec2) -> Rect {
    let tex_aspect = tex.x / tex.y.max(1.0);
    let target_aspect = target.x / target.y.max(1.0);
    if tex_aspect > target_aspect {
        let w = target_aspect / tex_aspect;
        Rect::from_min_max(pos2((1.0 - w) / 2.0, 0.0), pos2((1.0 + w) / 2.0, 1.0))
    } else {
        let h = tex_aspect / target_aspect;
        // Keep the top of the cover, where titles and faces usually are.
        Rect::from_min_max(pos2(0.0, 0.0), pos2(1.0, h))
    }
}

pub fn card_height(width: f32) -> f32 {
    width * COVER_ASPECT + space::S + 38.0 + 18.0
}

/// A cover with its title (two lines at most) and a caption under it.
pub fn cover_card(
    ui: &mut Ui,
    images: &mut Images,
    jobs: &Jobs,
    manga: &Manga,
    width: f32,
    caption: &str,
    in_library: bool,
) -> Response {
    let p = Palette::of(ui.ctx());
    let (rect, response) = ui.allocate_exact_size(vec2(width, card_height(width)), Sense::click());
    response.widget_info(|| WidgetInfo::labeled(Role::Button, true, &manga.title));
    if !ui.is_rect_visible(rect) {
        return response;
    }
    let cover = Rect::from_min_size(rect.min, vec2(width, width * COVER_ASPECT));
    let hovered = response.hovered();
    let shadow = egui::epaint::Shadow {
        offset: [0, if hovered { 8 } else { 3 }],
        blur: if hovered { 22 } else { 10 },
        spread: 0,
        color: p.shadow.gamma_multiply(if hovered { 1.0 } else { 0.7 }),
    };
    ui.painter()
        .add(shadow.as_shape(cover, CornerRadius::same(radius::COVER)));
    paint_cover(ui, images, jobs, manga, cover, radius::COVER);
    if hovered {
        ui.painter().rect_filled(
            cover,
            CornerRadius::same(radius::COVER),
            egui::Color32::from_white_alpha(14),
        );
    }
    if in_library {
        // A small badge so library titles stand out while browsing; it's labeled for screen readers
        // through the card's tooltip.
        let badge = Rect::from_min_size(cover.right_top() + vec2(-30.0, 8.0), Vec2::splat(22.0));
        ui.painter().circle_filled(badge.center(), 11.0, p.accent_fill);
        widgets::paint_icon(ui.painter(), Icon::Check, badge.shrink(5.0), egui::Color32::WHITE);
    }
    widgets::focus_ring(ui, &response, cover, radius::COVER);

    let mut y = cover.bottom() + space::S;
    let title = widgets::wrapped_text(ui, &manga.title, font(13.0, Weight::Semibold), p.label, width, 2);
    let title_h = title.size().y;
    ui.painter().galley(pos2(rect.left(), y), title, p.label);
    y += title_h + 2.0;
    if !caption.is_empty() {
        let cap = widgets::wrapped_text(ui, caption, text::subheadline(), p.secondary_label, width, 1);
        ui.painter().galley(pos2(rect.left(), y), cap, p.secondary_label);
    }
    let response = if in_library {
        response.on_hover_text("In your library")
    } else {
        response
    };
    response.on_hover_cursor(CursorIcon::PointingHand)
}

/// Lays cards out in as many columns as fit, drawing only the rows on screen.
/// Calls `on_end_reached` when the last row becomes visible.
pub fn grid<T>(
    ui: &mut Ui,
    id: &str,
    items: &[T],
    header: impl FnOnce(&mut Ui),
    mut card: impl FnMut(&mut Ui, &T, f32),
    footer: impl FnOnce(&mut Ui),
) -> bool {
    let gap_x = space::XL;
    let gap_y = space::XL;
    let margin = crate::theme::space::PAGE;
    let avail = ui.available_width() - margin * 2.0;
    let min_w = 150.0;
    let cols = (((avail + gap_x) / (min_w + gap_x)).floor() as usize).max(1);
    let width = ((avail - gap_x * (cols as f32 - 1.0)) / cols as f32).min(220.0);
    let row_h = card_height(width) + gap_y;
    let rows = items.len().div_ceil(cols);
    let mut reached_end = false;

    egui::ScrollArea::vertical()
        .id_salt(id)
        .auto_shrink([false, false])
        .show_viewport(ui, |ui, viewport| {
            crate::app::page_frame().show(ui, |ui| {
                ui.set_width(avail);
                header(ui);
                let top = ui.cursor().top();
                let origin_y = top - ui.min_rect().top();
                // Translate the viewport into row indices, leaving one row of slack each way.
                let first =
                    (((viewport.top() - origin_y) / row_h).floor().max(0.0) as usize).saturating_sub(1);
                let last =
                    ((((viewport.bottom() - origin_y) / row_h).ceil().max(0.0)) as usize + 1).min(rows);
                ui.allocate_space(vec2(avail, row_h * rows as f32));
                for row in first..last {
                    for col in 0..cols {
                        let Some(item) = items.get(row * cols + col) else {
                            break;
                        };
                        let min = pos2(
                            ui.min_rect().left() + col as f32 * (width + gap_x),
                            top + row as f32 * row_h,
                        );
                        let rect = Rect::from_min_size(min, vec2(width, card_height(width)));
                        let mut child = ui.new_child(
                            egui::UiBuilder::new()
                                .max_rect(rect)
                                .id_salt((id, row * cols + col)),
                        );
                        card(&mut child, item, width);
                    }
                }
                if rows > 0 && last >= rows {
                    reached_end = true;
                }
                footer(ui);
                ui.add_space(space::XXL);
            });
        });
    reached_end
}

/// A cover blurred into a soft wash of its colors, used behind the title header.
pub fn paint_cover_backdrop(
    ui: &Ui,
    images: &mut Images,
    jobs: &Jobs,
    manga: &Manga,
    rect: Rect,
    strength: f32,
) {
    let Some(url) = manga.cover_url(CoverSize::Small) else {
        return;
    };
    if let ImageState::Ready(loaded) = images.get(jobs, ui.ctx(), &url, ImageKind::CoverBlur) {
        let fade = loaded.fade(ui.input(|i| i.time)) * strength;
        let uv = aspect_fill_uv(texture_size(&loaded.texture), rect.size());
        egui::Image::new(egui::load::SizedTexture::from_handle(&loaded.texture))
            .uv(uv)
            .tint(egui::Color32::from_white_alpha((255.0 * fade) as u8))
            .paint_at(ui, rect);
    }
}
