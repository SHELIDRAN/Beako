//! Frosted glass.
//!
//! - **Window material**: on Windows 11 the window gets the system Mica backdrop, so the
//!   sidebar is real translucent glass over the desktop wallpaper (Windows 10 gets Acrylic).
//! - **In-app glass**: egui can't blur what's behind a shape, so glass drawn over content uses a
//!   tiny pre-blurred copy of that content (made on a worker thread), stretched under the panel
//!   and tinted. Over a manga page, the reader's controls look like frosted glass over the art.
//!
//! Following Apple's guidance, glass is only for the floating functional layer (sidebar,
//! toolbars, reader controls, notices), never for content itself.

use eframe::egui::{
    self, Color32, CornerRadius, Painter, Rect, Shape, Stroke, StrokeKind, TextureHandle, pos2,
};

use crate::theme::Palette;

/// Applies Mica (Windows 11) or Acrylic (Windows 10) behind the window. Returns whether a
/// material is active; when it isn't, everything is drawn opaque.
pub fn apply_window_material(cc: &eframe::CreationContext<'_>) -> bool {
    #[cfg(windows)]
    {
        let dark = cc.egui_ctx.global_style().visuals.dark_mode;
        if window_vibrancy::apply_mica(cc, Some(dark)).is_ok() {
            return true;
        }
        if window_vibrancy::apply_acrylic(cc, None).is_ok() {
            return true;
        }
        false
    }
    #[cfg(not(windows))]
    {
        let _ = cc;
        false
    }
}

/// Keeps the display on while reading, using the Windows power API.
pub fn keep_awake(on: bool) {
    #[cfg(windows)]
    unsafe {
        use windows_sys::Win32::System::Power::{
            ES_CONTINUOUS, ES_DISPLAY_REQUIRED, ES_SYSTEM_REQUIRED, SetThreadExecutionState,
        };
        let flags = if on {
            ES_CONTINUOUS | ES_DISPLAY_REQUIRED | ES_SYSTEM_REQUIRED
        } else {
            ES_CONTINUOUS
        };
        SetThreadExecutionState(flags);
    }
    #[cfg(not(windows))]
    let _ = on;
}

/// A glass panel over app chrome: translucent fill, a light top edge, a hairline, and a soft
/// shadow.
pub fn panel(painter: &Painter, rect: Rect, corner: CornerRadius, p: &Palette) {
    let shadow = egui::epaint::Shadow {
        offset: [0, 10],
        blur: 32,
        spread: 0,
        color: p.shadow,
    };
    painter.add(shadow.as_shape(rect, corner));
    let fill = if p.dark {
        Color32::from_rgba_unmultiplied(44, 44, 48, 225)
    } else {
        Color32::from_rgba_unmultiplied(252, 252, 254, 228)
    };
    painter.rect(
        rect,
        corner,
        fill,
        Stroke::new(1.0, p.separator.gamma_multiply(0.9)),
        StrokeKind::Inside,
    );
    highlight(painter, rect, corner, if p.dark { 22 } else { 160 });
}

/// Glass over an image: the image's blurred copy, aligned to where the image is drawn and
/// clipped to it, under a tint. `image_rect` is where the full image sits on screen.
pub fn frosted(
    painter: &Painter,
    rect: Rect,
    corner: CornerRadius,
    backdrop: Option<(&TextureHandle, Rect)>,
    tint: Color32,
    opacity: f32,
) {
    let shadow = egui::epaint::Shadow {
        offset: [0, 10],
        blur: 34,
        spread: 0,
        color: Color32::from_black_alpha((110.0 * opacity) as u8),
    };
    painter.add(shadow.as_shape(rect, corner));
    // Black first, so the parts of the bar outside the image match the reader's background.
    painter.rect_filled(rect, corner, Color32::from_black_alpha((235.0 * opacity) as u8));
    if let Some((tex, image_rect)) = backdrop {
        // The blurred copy carries a soft transparent margin; place it so the page inside lines
        // up with where the page is drawn.
        let image_rect = crate::jobs::blur_rect(crate::images::texture_size(tex), image_rect);
        let visible = rect.intersect(image_rect);
        if visible.is_positive() {
            let uv = Rect::from_min_max(
                pos2(
                    (rect.left() - image_rect.left()) / image_rect.width(),
                    (rect.top() - image_rect.top()) / image_rect.height(),
                ),
                pos2(
                    (rect.right() - image_rect.left()) / image_rect.width(),
                    (rect.bottom() - image_rect.top()) / image_rect.height(),
                ),
            );
            let tinted = Color32::from_white_alpha((255.0 * opacity) as u8);
            let shape = egui::epaint::RectShape::filled(rect, corner, tinted).with_texture(tex.id(), uv);
            painter
                .with_clip_rect(visible.intersect(painter.clip_rect()))
                .add(Shape::Rect(shape));
        }
    }
    painter.rect(
        rect,
        corner,
        tint.gamma_multiply(opacity),
        Stroke::new(1.0, Color32::from_white_alpha((30.0 * opacity) as u8)),
        StrokeKind::Inside,
    );
    highlight(painter, rect, corner, (40.0 * opacity) as u8);
}

/// A faint light line along the top edge, the way glass catches light.
fn highlight(painter: &Painter, rect: Rect, corner: CornerRadius, alpha: u8) {
    let r = (corner.nw as f32).min(rect.height() / 2.0);
    let y = rect.top() + 1.0;
    painter.line_segment(
        [pos2(rect.left() + r, y), pos2(rect.right() - r, y)],
        Stroke::new(1.0, Color32::from_white_alpha(alpha)),
    );
}
