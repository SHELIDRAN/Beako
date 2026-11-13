//! Controls drawn to match Apple's component guidelines: sidebar rows, push buttons, capsule
//! tags, a segmented control, a rounded search field, and vector symbols.

use std::f32::consts::{PI, TAU};

use eframe::egui::{
    self, Align2, Color32, CornerRadius, CursorIcon, FontId, Pos2, Rect, Response, RichText, Role, Sense,
    Shape, Stroke, StrokeKind, Ui, Vec2, WidgetInfo, pos2, vec2,
};

use crate::filters::TagState;
use crate::theme::{Palette, Weight, font, radius, space, text};

/// Vector symbols in a single line weight, sized to sit beside 14 pt text.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Icon {
    Library,
    Browse,
    Settings,
    Search,
    ChevronLeft,
    ChevronRight,
    Check,
    Plus,
    Close,
    Retry,
    Book,
    External,
    ArrowUpDown,
    Person,
    Filter,
    Minus,
    Sliders,
    Sync,
}

pub fn paint_icon(painter: &egui::Painter, icon: Icon, rect: Rect, color: Color32) {
    let s = rect.width().min(rect.height());
    let c = rect.center();
    let w = (s / 11.0).max(1.4);
    let stroke = Stroke::new(w, color);
    let p = |x: f32, y: f32| pos2(c.x + x * s, c.y + y * s);
    match icon {
        Icon::Library => {
            // Three books on a shelf, the last one leaning.
            let books = [(-0.36, -0.30, 0.16), (-0.14, -0.38, 0.16)];
            for (x, top, bw) in books {
                let r = Rect::from_min_max(p(x, top), p(x + bw, 0.38));
                painter.rect_stroke(r, CornerRadius::same(2), stroke, StrokeKind::Middle);
            }
            let lean = [p(0.08, -0.22), p(0.24, -0.27), p(0.40, 0.33), p(0.24, 0.38)];
            painter.add(Shape::closed_line(lean.to_vec(), stroke));
        }
        Icon::Browse => {
            painter.circle_stroke(c, s * 0.40, stroke);
            let needle = [p(0.17, -0.17), p(0.05, 0.05), p(-0.17, 0.17), p(-0.05, -0.05)];
            painter.add(Shape::convex_polygon(needle.to_vec(), color, Stroke::NONE));
        }
        Icon::Settings => {
            let teeth = 8;
            for i in 0..teeth {
                let a = i as f32 / teeth as f32 * TAU;
                let dir = vec2(a.cos(), a.sin());
                painter.line_segment(
                    [c + dir * s * 0.28, c + dir * s * 0.42],
                    Stroke::new(w * 1.6, color),
                );
            }
            painter.circle_stroke(c, s * 0.28, stroke);
            painter.circle_stroke(c, s * 0.11, stroke);
        }
        Icon::Search => {
            let center = p(-0.08, -0.08);
            painter.circle_stroke(center, s * 0.26, stroke);
            painter.line_segment([p(0.11, 0.11), p(0.36, 0.36)], Stroke::new(w * 1.2, color));
        }
        Icon::ChevronLeft => {
            painter.add(Shape::line(
                vec![p(0.10, -0.30), p(-0.14, 0.0), p(0.10, 0.30)],
                stroke,
            ));
        }
        Icon::ChevronRight => {
            painter.add(Shape::line(
                vec![p(-0.10, -0.30), p(0.14, 0.0), p(-0.10, 0.30)],
                stroke,
            ));
        }
        Icon::Check => {
            painter.add(Shape::line(
                vec![p(-0.30, 0.02), p(-0.08, 0.24), p(0.32, -0.24)],
                Stroke::new(w * 1.2, color),
            ));
        }
        Icon::Plus => {
            painter.line_segment([p(0.0, -0.30), p(0.0, 0.30)], stroke);
            painter.line_segment([p(-0.30, 0.0), p(0.30, 0.0)], stroke);
        }
        Icon::Close => {
            painter.line_segment([p(-0.24, -0.24), p(0.24, 0.24)], stroke);
            painter.line_segment([p(0.24, -0.24), p(-0.24, 0.24)], stroke);
        }
        Icon::Retry => {
            let r = s * 0.30;
            let pts: Vec<Pos2> = (0..=20)
                .map(|i| {
                    let a = -PI * 0.35 + i as f32 / 20.0 * PI * 1.6;
                    c + vec2(a.cos(), a.sin()) * r
                })
                .collect();
            let end = pts[0];
            painter.add(Shape::line(pts, stroke));
            painter.add(Shape::line(
                vec![
                    end + vec2(-s * 0.16, -s * 0.02),
                    end,
                    end + vec2(-s * 0.02, s * 0.15),
                ],
                stroke,
            ));
        }
        Icon::Book => {
            let left = [
                p(0.0, -0.22),
                p(-0.18, -0.30),
                p(-0.40, -0.28),
                p(-0.40, 0.28),
                p(-0.18, 0.26),
                p(0.0, 0.34),
            ];
            let right = [
                p(0.0, -0.22),
                p(0.18, -0.30),
                p(0.40, -0.28),
                p(0.40, 0.28),
                p(0.18, 0.26),
                p(0.0, 0.34),
            ];
            painter.add(Shape::line(left.to_vec(), stroke));
            painter.add(Shape::line(right.to_vec(), stroke));
            painter.line_segment([p(0.0, -0.22), p(0.0, 0.34)], stroke);
        }
        Icon::External => {
            let r = Rect::from_min_max(p(-0.34, -0.20), p(0.20, 0.34));
            painter.add(Shape::line(
                vec![
                    p(-0.04, -0.20),
                    r.left_top(),
                    r.left_bottom(),
                    r.right_bottom(),
                    p(0.20, 0.04),
                ],
                stroke,
            ));
            painter.line_segment([p(-0.02, 0.02), p(0.34, -0.34)], stroke);
            painter.add(Shape::line(
                vec![p(0.08, -0.34), p(0.34, -0.34), p(0.34, -0.08)],
                stroke,
            ));
        }
        Icon::ArrowUpDown => {
            painter.line_segment([p(-0.16, -0.32), p(-0.16, 0.32)], stroke);
            painter.add(Shape::line(
                vec![p(-0.32, -0.16), p(-0.16, -0.32), p(0.0, -0.16)],
                stroke,
            ));
            painter.line_segment([p(0.18, -0.32), p(0.18, 0.32)], stroke);
            painter.add(Shape::line(
                vec![p(0.02, 0.16), p(0.18, 0.32), p(0.34, 0.16)],
                stroke,
            ));
        }
        Icon::Person => {
            painter.circle_stroke(p(0.0, -0.14), s * 0.16, stroke);
            let pts: Vec<Pos2> = (0..=16)
                .map(|i| {
                    let a = PI + i as f32 / 16.0 * PI;
                    pos2(c.x + a.cos() * s * 0.32, c.y + s * 0.38 + a.sin() * s * 0.30)
                })
                .collect();
            painter.add(Shape::line(pts, stroke));
        }
        Icon::Filter => {
            painter.line_segment([p(-0.36, -0.22), p(0.36, -0.22)], stroke);
            painter.line_segment([p(-0.22, 0.0), p(0.22, 0.0)], stroke);
            painter.line_segment([p(-0.08, 0.22), p(0.08, 0.22)], stroke);
        }
        Icon::Minus => {
            painter.line_segment([p(-0.28, 0.0), p(0.28, 0.0)], Stroke::new(w * 1.2, color));
        }
        Icon::Sliders => {
            for (y, x) in [(-0.22, 0.12), (0.0, -0.14), (0.22, 0.04)] {
                painter.line_segment([p(-0.36, y), p(0.36, y)], stroke);
                painter.circle_filled(p(x, y), s * 0.08, color);
            }
        }
        Icon::Sync => {
            let r = s * 0.30;
            for (start, sign) in [(-PI * 0.15, 1.0f32), (PI * 0.85, 1.0)] {
                let pts: Vec<Pos2> = (0..=10)
                    .map(|i| {
                        let a = start + sign * i as f32 / 10.0 * PI * 0.8;
                        c + vec2(a.cos(), a.sin()) * r
                    })
                    .collect();
                let end = *pts.last().expect("non-empty");
                let dir = (end - pts[pts.len() - 2]).normalized();
                let side = vec2(-dir.y, dir.x);
                painter.add(Shape::line(pts, stroke));
                painter.add(Shape::line(
                    vec![
                        end - dir * s * 0.12 + side * s * 0.10,
                        end,
                        end - dir * s * 0.12 - side * s * 0.10,
                    ],
                    stroke,
                ));
            }
        }
    }
}

/// Draws the keyboard focus ring Apple uses: an accent outline just outside the control.
pub fn focus_ring(ui: &Ui, response: &Response, rect: Rect, corner: u8) {
    if response.has_focus() {
        let p = Palette::of(ui.ctx());
        ui.painter().rect_stroke(
            rect.expand(2.0),
            CornerRadius::same(corner.saturating_add(2)),
            Stroke::new(2.0, p.accent.gamma_multiply(0.8)),
            StrokeKind::Outside,
        );
    }
}

/// A sidebar row: symbol and label, a subtle selection fill, accent-tinted symbol when selected.
pub fn sidebar_item(ui: &mut Ui, icon: Icon, label: &str, selected: bool, badge: Option<usize>) -> Response {
    let p = Palette::of(ui.ctx());
    let (rect, response) = ui.allocate_exact_size(vec2(ui.available_width(), 32.0), Sense::click());
    response.widget_info(|| WidgetInfo::selected(Role::Tab, true, selected, label));
    let fill = if selected {
        p.fill_hover
    } else if response.hovered() {
        p.fill
    } else {
        Color32::TRANSPARENT
    };
    let painter = ui.painter();
    painter.rect_filled(rect, CornerRadius::same(radius::CONTROL), fill);
    let icon_rect = Rect::from_center_size(pos2(rect.left() + 20.0, rect.center().y), Vec2::splat(18.0));
    paint_icon(
        painter,
        icon,
        icon_rect,
        if selected { p.accent } else { p.secondary_label },
    );
    let weight = if selected {
        Weight::Semibold
    } else {
        Weight::Medium
    };
    painter.text(
        pos2(rect.left() + 38.0, rect.center().y),
        Align2::LEFT_CENTER,
        label,
        font(14.0, weight),
        p.label,
    );
    if let Some(n) = badge.filter(|n| *n > 0) {
        painter.text(
            pos2(rect.right() - 10.0, rect.center().y),
            Align2::RIGHT_CENTER,
            n.to_string(),
            text::subheadline(),
            p.secondary_label,
        );
    }
    focus_ring(ui, &response, rect, radius::CONTROL);
    response.on_hover_cursor(CursorIcon::PointingHand)
}

#[derive(Clone, Copy, PartialEq, Eq)]
pub enum ButtonStyle {
    /// Filled with the accent color: the one primary action on a screen.
    Prominent,
    /// Tinted gray: secondary actions.
    Bordered,
    /// Text only, accent colored: tertiary actions and toolbar items.
    Plain,
    /// Accent-tinted fill with accent text: a secondary action that is currently active.
    Tinted,
}

/// A push button with an optional leading symbol. Minimum 28 pt tall, the desktop default.
pub fn button(ui: &mut Ui, style: ButtonStyle, icon: Option<Icon>, label: &str) -> Response {
    let p = Palette::of(ui.ctx());
    let font_id = if style == ButtonStyle::Prominent {
        text::headline()
    } else {
        font(14.0, Weight::Medium)
    };
    let galley = ui
        .painter()
        .layout_no_wrap(label.to_owned(), font_id, Color32::PLACEHOLDER);
    let icon_w = if icon.is_some() {
        16.0 + space::S - if label.is_empty() { space::S } else { 0.0 }
    } else {
        0.0
    };
    let pad = if style == ButtonStyle::Plain { 6.0 } else { 14.0 };
    let size = vec2(galley.size().x + icon_w + pad * 2.0, 32.0).max(vec2(32.0, 32.0));
    let (rect, response) = ui.allocate_exact_size(size, Sense::click());
    response.widget_info(|| WidgetInfo::labeled(Role::Button, ui.is_enabled(), label));
    let enabled = ui.is_enabled();
    let pressed = response.is_pointer_button_down_on();
    let (fill, fg) = match style {
        ButtonStyle::Prominent => {
            let f = if pressed {
                p.accent_fill.gamma_multiply(0.85)
            } else {
                p.accent_fill
            };
            (
                if response.hovered() && !pressed {
                    lighten(f, 0.08)
                } else {
                    f
                },
                Color32::WHITE,
            )
        }
        ButtonStyle::Bordered => (if response.hovered() { p.fill_hover } else { p.fill }, p.label),
        ButtonStyle::Tinted => (
            p.accent
                .gamma_multiply(if response.hovered() { 0.24 } else { 0.16 }),
            p.accent,
        ),
        ButtonStyle::Plain => (
            if response.hovered() {
                p.fill
            } else {
                Color32::TRANSPARENT
            },
            p.accent,
        ),
    };
    // Disabled buttons drop their tint and keep readable gray text rather than fading out.
    let (fill, fg) = if enabled {
        (fill, fg)
    } else {
        (p.fill, p.secondary_label)
    };
    let alpha = 1.0;
    let painter = ui.painter();
    painter.rect_filled(
        rect,
        CornerRadius::same(radius::CONTROL),
        fill.gamma_multiply(alpha),
    );
    let content_w = galley.size().x + icon_w;
    let mut x = rect.center().x - content_w / 2.0;
    if let Some(icon) = icon {
        let r = Rect::from_center_size(pos2(x + 8.0, rect.center().y), Vec2::splat(16.0));
        paint_icon(painter, icon, r, fg.gamma_multiply(alpha));
        x += icon_w;
    }
    painter.galley(
        pos2(x, rect.center().y - galley.size().y / 2.0),
        galley,
        fg.gamma_multiply(alpha),
    );
    focus_ring(ui, &response, rect, radius::CONTROL);
    if enabled {
        response.on_hover_cursor(CursorIcon::PointingHand)
    } else {
        response
    }
}

fn lighten(c: Color32, amount: f32) -> Color32 {
    let f = |v: u8| (v as f32 + (255.0 - v as f32) * amount) as u8;
    Color32::from_rgba_premultiplied(f(c.r()), f(c.g()), f(c.b()), c.a())
}

/// A capsule label for genres and status.
pub fn tag(ui: &mut Ui, label: &str) -> Response {
    let p = Palette::of(ui.ctx());
    let galley = ui.painter().layout_no_wrap(
        label.to_owned(),
        text::subheadline_emphasized(),
        p.secondary_label,
    );
    let (rect, response) = ui.allocate_exact_size(galley.size() + vec2(20.0, 10.0), Sense::hover());
    ui.painter()
        .rect_filled(rect, CornerRadius::same(radius::CAPSULE), p.fill);
    ui.painter()
        .galley(rect.center() - galley.size() / 2.0, galley, p.secondary_label);
    response
}

/// A segmented control: mutually exclusive choices in one capsule (HIG › Segmented controls).
pub fn segmented<T: Copy + PartialEq>(ui: &mut Ui, value: &mut T, options: &[(T, &str)]) -> bool {
    let p = Palette::of(ui.ctx());
    let font_id = font(13.0, Weight::Medium);
    let widths: Vec<f32> = options
        .iter()
        .map(|(_, l)| {
            ui.painter()
                .layout_no_wrap((*l).to_owned(), font_id.clone(), p.label)
                .size()
                .x
                + 28.0
        })
        .collect();
    let seg_w = widths.iter().copied().fold(0.0, f32::max);
    let total = vec2(seg_w * options.len() as f32 + 4.0, 30.0);
    let (outer, _) = ui.allocate_exact_size(total, Sense::hover());
    ui.painter()
        .rect_filled(outer, CornerRadius::same(radius::CONTROL + 1), p.fill);
    let mut changed = false;
    for (i, (v, label)) in options.iter().enumerate() {
        let r = Rect::from_min_size(
            pos2(outer.left() + 2.0 + seg_w * i as f32, outer.top() + 2.0),
            vec2(seg_w, 26.0),
        );
        let id = ui.scope_id().with(("segment", i, *label));
        let response = ui.interact(r, id, Sense::click());
        let selected = *value == *v;
        response.widget_info(|| WidgetInfo::selected(Role::RadioButton, true, selected, *label));
        if selected {
            let painter = ui.painter();
            let shadow = r.translate(vec2(0.0, 1.0));
            painter.rect_filled(
                shadow,
                CornerRadius::same(radius::CONTROL - 1),
                p.shadow.gamma_multiply(0.5),
            );
            painter.rect_filled(
                r,
                CornerRadius::same(radius::CONTROL - 1),
                if p.dark { p.separator } else { p.elevated },
            );
        } else if response.hovered() {
            ui.painter()
                .rect_filled(r, CornerRadius::same(radius::CONTROL - 1), p.fill);
        }
        ui.painter().text(
            r.center(),
            Align2::CENTER_CENTER,
            *label,
            font_id.clone(),
            p.label,
        );
        focus_ring(ui, &response, r, radius::CONTROL - 1);
        if response.clicked() && !selected {
            *value = *v;
            changed = true;
        }
    }
    changed
}

/// A rounded search field with a magnifying-glass symbol and a clear button.
pub fn search_field(ui: &mut Ui, query: &mut String, hint: &str, width: f32) -> Response {
    let p = Palette::of(ui.ctx());
    let (rect, _) = ui.allocate_exact_size(vec2(width, 32.0), Sense::hover());
    ui.painter()
        .rect_filled(rect, CornerRadius::same(radius::CONTROL + 1), p.fill);
    let icon_rect = Rect::from_center_size(pos2(rect.left() + 16.0, rect.center().y), Vec2::splat(16.0));
    paint_icon(ui.painter(), Icon::Search, icon_rect, p.secondary_label);
    let clear_w = if query.is_empty() { 0.0 } else { 28.0 };
    let text_rect = Rect::from_min_max(
        pos2(rect.left() + 30.0, rect.top()),
        pos2(rect.right() - 8.0 - clear_w, rect.bottom()),
    );
    let edit = egui::TextEdit::singleline(query)
        .hint_text(RichText::new(hint).color(p.secondary_label))
        .font(text::body())
        .text_color(p.label)
        .frame(egui::Frame::NONE)
        .align(egui::Align2::LEFT_CENTER)
        .desired_width(text_rect.width());
    let response = ui.put(text_rect, edit);
    if response.has_focus() {
        ui.painter().rect_stroke(
            rect.expand(1.0),
            CornerRadius::same(radius::CONTROL + 2),
            Stroke::new(2.0, p.accent.gamma_multiply(0.8)),
            StrokeKind::Outside,
        );
    }
    if !query.is_empty() {
        let clear = Rect::from_center_size(pos2(rect.right() - 18.0, rect.center().y), Vec2::splat(20.0));
        let r = ui.interact(clear, ui.scope_id().with("clear-search"), Sense::click());
        r.widget_info(|| WidgetInfo::labeled(Role::Button, true, "Clear search"));
        ui.painter().circle_filled(
            clear.center(),
            8.0,
            p.secondary_label
                .gamma_multiply(if r.hovered() { 0.9 } else { 0.6 }),
        );
        paint_icon(
            ui.painter(),
            Icon::Close,
            Rect::from_center_size(clear.center(), Vec2::splat(10.0)),
            p.background,
        );
        if r.clicked() {
            query.clear();
            response.request_focus();
        }
    }
    response
}

/// A centered message for empty, loading, and error states: a symbol, a title, a line of
/// guidance, and an optional action.
pub fn placeholder_view(ui: &mut Ui, icon: Icon, title: &str, message: &str, action: Option<&str>) -> bool {
    let p = Palette::of(ui.ctx());
    let mut clicked = false;
    ui.vertical_centered(|ui| {
        ui.add_space((ui.available_height() * 0.28).max(space::XL));
        let (r, _) = ui.allocate_exact_size(Vec2::splat(44.0), Sense::hover());
        paint_icon(ui.painter(), icon, r, p.secondary_label);
        ui.add_space(space::M);
        ui.label(RichText::new(title).font(text::title2()).color(p.label));
        ui.add_space(space::XS);
        ui.add(egui::Label::new(RichText::new(message).font(text::body()).color(p.secondary_label)).wrap());
        if let Some(action) = action {
            ui.add_space(space::L);
            clicked = button(ui, ButtonStyle::Bordered, None, action).clicked();
        }
    });
    clicked
}

/// A spinner sized like the system's small activity indicator.
pub fn spinner(ui: &mut Ui, size: f32) {
    let p = Palette::of(ui.ctx());
    let (rect, _) = ui.allocate_exact_size(Vec2::splat(size), Sense::hover());
    paint_spinner(ui, rect, p.secondary_label);
}

pub fn paint_spinner(ui: &Ui, rect: Rect, color: Color32) {
    ui.ctx().request_repaint();
    let t = ui.input(|i| i.time) as f32;
    let spokes = 8;
    let c = rect.center();
    let r = rect.width() / 2.0;
    let active = ((t * 10.0) as usize) % spokes;
    for i in 0..spokes {
        let a = i as f32 / spokes as f32 * TAU - PI / 2.0;
        let dir = vec2(a.cos(), a.sin());
        let age = (active + spokes - i) % spokes;
        let alpha = 1.0 - age as f32 / spokes as f32 * 0.85;
        ui.painter().line_segment(
            [c + dir * r * 0.45, c + dir * r],
            Stroke::new((r * 0.22).max(1.5), color.gamma_multiply(alpha)),
        );
    }
}

/// Text that truncates with an ellipsis after `max_rows` lines.
pub fn wrapped_text(
    ui: &Ui,
    text: &str,
    font_id: FontId,
    color: Color32,
    width: f32,
    max_rows: usize,
) -> std::sync::Arc<egui::Galley> {
    let mut job = egui::text::LayoutJob::simple(text.to_owned(), font_id, color, width);
    job.wrap.max_rows = max_rows;
    job.wrap.break_anywhere = false;
    job.wrap.overflow_character = Some('…');
    ui.painter().layout_job(job)
}

/// A selectable capsule: on, off. Used for single-value filters (type, status, demographic).
pub fn choice_chip(ui: &mut Ui, label: &str, selected: bool) -> Response {
    chip(
        ui,
        label,
        if selected {
            TagState::Include
        } else {
            TagState::Any
        },
    )
}

/// A tri-state capsule for tags: tap once to require, again to exclude, again to clear.
pub fn chip(ui: &mut Ui, label: &str, state: TagState) -> Response {
    let p = Palette::of(ui.ctx());
    let font_id = font(13.0, Weight::Medium);
    let galley = ui
        .painter()
        .layout_no_wrap(label.to_owned(), font_id.clone(), p.label);
    let icon_w = if state == TagState::Any { 0.0 } else { 16.0 };
    let size = vec2(galley.size().x + 24.0 + icon_w, 30.0);
    let (rect, response) = ui.allocate_exact_size(size, Sense::click());
    let desc = match state {
        TagState::Any => label.to_owned(),
        TagState::Include => format!("{label}, included"),
        TagState::Exclude => format!("{label}, excluded"),
    };
    response.widget_info(|| WidgetInfo::selected(Role::CheckBox, true, state != TagState::Any, &desc));
    let (fill, fg, stroke) = match state {
        TagState::Any => (
            if response.hovered() { p.fill_hover } else { p.fill },
            p.label,
            Stroke::NONE,
        ),
        TagState::Include => (p.accent_fill, Color32::WHITE, Stroke::NONE),
        TagState::Exclude => (
            Color32::TRANSPARENT,
            p.secondary_label,
            Stroke::new(1.2, p.secondary_label.gamma_multiply(0.7)),
        ),
    };
    let painter = ui.painter();
    painter.rect(
        rect,
        CornerRadius::same(radius::CAPSULE),
        fill,
        stroke,
        StrokeKind::Inside,
    );
    let mut x = rect.left() + 12.0;
    if state != TagState::Any {
        let icon = if state == TagState::Include {
            Icon::Check
        } else {
            Icon::Minus
        };
        paint_icon(
            painter,
            icon,
            Rect::from_center_size(pos2(x + 6.0, rect.center().y), Vec2::splat(13.0)),
            fg,
        );
        x += icon_w;
    }
    let text_pos = pos2(x, rect.center().y - galley.size().y / 2.0);
    let text_w = galley.size().x;
    painter.galley(text_pos, galley, fg);
    if state == TagState::Exclude {
        painter.line_segment(
            [
                pos2(x - 1.0, rect.center().y),
                pos2(x + text_w + 1.0, rect.center().y),
            ],
            Stroke::new(1.2, fg),
        );
    }
    focus_ring(ui, &response, rect, radius::CAPSULE);
    response.on_hover_cursor(CursorIcon::PointingHand)
}

/// A switch styled like Apple's, filled with Neko's accent when on.
pub fn toggle(ui: &mut Ui, on: &mut bool, label: &str) -> Response {
    let p = Palette::of(ui.ctx());
    let (rect, mut response) = ui.allocate_exact_size(vec2(40.0, 24.0), Sense::click());
    response.widget_info(|| WidgetInfo::selected(Role::Switch, true, *on, label));
    if response.clicked() {
        *on = !*on;
        response.mark_changed();
    }
    let t = ui.ctx().animate_bool_responsive(response.id, *on);
    let off = if p.dark {
        Color32::from_rgb(0x48, 0x48, 0x4C)
    } else {
        Color32::from_rgb(0xDC, 0xDC, 0xE0)
    };
    let track = if *on { p.accent_fill } else { off };
    ui.painter().rect_filled(rect, CornerRadius::same(12), track);
    let x = egui::lerp((rect.left() + 12.0)..=(rect.right() - 12.0), t);
    let knob = Rect::from_center_size(pos2(x, rect.center().y), vec2(20.0, 20.0));
    ui.painter().add(
        egui::epaint::Shadow {
            offset: [0, 1],
            blur: 4,
            spread: 0,
            color: p.shadow,
        }
        .as_shape(knob, CornerRadius::same(10)),
    );
    ui.painter().circle_filled(knob.center(), 10.0, Color32::WHITE);
    focus_ring(ui, &response, rect, 12);
    response.on_hover_cursor(CursorIcon::PointingHand)
}

/// A slider with a thin track and a round knob, like Apple's. Arrow keys adjust it when focused.
pub fn slider(
    ui: &mut Ui,
    value: &mut u32,
    range: std::ops::RangeInclusive<u32>,
    step: u32,
    width: f32,
    label: &str,
) -> Response {
    let p = Palette::of(ui.ctx());
    let (rect, mut response) = ui.allocate_exact_size(vec2(width, 28.0), Sense::click_and_drag());
    response.widget_info(|| WidgetInfo::labeled(Role::Slider, true, format!("{label}: {value}")));
    let (lo, hi) = (*range.start() as f32, *range.end() as f32);
    let track = Rect::from_center_size(rect.center(), vec2(rect.width() - 20.0, 4.0));
    if let Some(pos) = response.interact_pointer_pos()
        && (response.dragged() || response.clicked())
    {
        let f = ((pos.x - track.left()) / track.width()).clamp(0.0, 1.0);
        let raw = lo + f * (hi - lo);
        let snapped = ((raw / step as f32).round() * step as f32).clamp(lo, hi) as u32;
        if snapped != *value {
            *value = snapped;
            response.mark_changed();
        }
    }
    if response.has_focus() {
        let (l, r) = ui.input(|i| {
            (
                i.key_pressed(egui::Key::ArrowLeft),
                i.key_pressed(egui::Key::ArrowRight),
            )
        });
        if r && *value + step <= *range.end() {
            *value += step;
            response.mark_changed();
        }
        if l && *value >= *range.start() + step {
            *value -= step;
            response.mark_changed();
        }
    }
    let f = ((*value as f32 - lo) / (hi - lo).max(1.0)).clamp(0.0, 1.0);
    let knob_x = track.left() + track.width() * f;
    ui.painter().rect_filled(
        track,
        CornerRadius::same(2),
        if p.dark {
            p.fill_hover
        } else {
            Color32::from_rgb(0xDC, 0xDC, 0xE0)
        },
    );
    ui.painter().rect_filled(
        Rect::from_min_max(track.left_top(), pos2(knob_x, track.bottom())),
        CornerRadius::same(2),
        p.accent_fill,
    );
    let knob = Rect::from_center_size(pos2(knob_x, track.center().y), Vec2::splat(20.0));
    ui.painter().add(
        egui::epaint::Shadow {
            offset: [0, 1],
            blur: 5,
            spread: 0,
            color: p.shadow,
        }
        .as_shape(knob, CornerRadius::same(10)),
    );
    ui.painter().circle_filled(knob.center(), 10.0, Color32::WHITE);
    ui.painter()
        .circle_stroke(knob.center(), 10.0, Stroke::new(0.5, p.separator));
    focus_ring(ui, &response, rect, radius::CONTROL);
    response.on_hover_cursor(CursorIcon::PointingHand)
}
