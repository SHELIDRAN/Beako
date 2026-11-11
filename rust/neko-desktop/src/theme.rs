//! Design tokens: semantic colors, type scale, and spacing modeled on Apple's Human Interface
//! Guidelines, adapted for a Windows desktop window.
//!
//! Every color has a light and a dark value and the app follows the system appearance; there
//! is no in-app appearance switch (HIG › Dark Mode). Contrast ratios are noted next to each text
//! color and meet WCAG AA at the sizes they are used for.

use std::sync::Arc;

use eframe::egui::{
    self, Color32, CornerRadius, FontData, FontDefinitions, FontFamily, FontId, Margin, Shadow, Stroke,
    Theme, Vec2, Visuals,
};

/// Semantic colors, named for their role rather than their value.
#[derive(Clone, Copy, Debug)]
pub struct Palette {
    pub dark: bool,
    /// Window content background.
    pub background: Color32,
    /// Grouped-list background and the reader chrome under content.
    pub grouped_background: Color32,
    /// Rows and cards that sit on `grouped_background`.
    pub elevated: Color32,
    pub sidebar: Color32,
    /// Primary text. Light: #1D1D1F on white, 16.8:1. Dark: #F5F5F7 on #1C1C1E, 15.6:1.
    pub label: Color32,
    /// Secondary text. Light: #6E6E73 on white, 5.1:1. Dark: #98989D on #1C1C1E, 5.9:1.
    pub secondary_label: Color32,
    pub separator: Color32,
    /// Neko's brand color used as text or symbols. Light #D70F45 on white, 5.2:1.
    /// Dark #FF5C83 on #1C1C1E, 5.8:1.
    pub accent: Color32,
    /// Fill for prominent buttons. White text on #D70F45 is 5.2:1 in both appearances.
    pub accent_fill: Color32,
    /// Subtle tint for selection and secondary buttons.
    pub fill: Color32,
    pub fill_hover: Color32,
    pub placeholder: Color32,
    pub shadow: Color32,
}

impl Palette {
    pub const LIGHT: Palette = Palette {
        dark: false,
        background: Color32::from_rgb(0xFF, 0xFF, 0xFF),
        grouped_background: Color32::from_rgb(0xF5, 0xF5, 0xF7),
        elevated: Color32::from_rgb(0xFF, 0xFF, 0xFF),
        sidebar: Color32::from_rgb(0xEE, 0xEE, 0xF1),
        label: Color32::from_rgb(0x1D, 0x1D, 0x1F),
        secondary_label: Color32::from_rgb(0x6E, 0x6E, 0x73),
        separator: Color32::from_rgb(0xD8, 0xD8, 0xDC),
        accent: Color32::from_rgb(0xD7, 0x0F, 0x45),
        accent_fill: Color32::from_rgb(0xD7, 0x0F, 0x45),
        fill: Color32::from_black_alpha(14),
        fill_hover: Color32::from_black_alpha(24),
        placeholder: Color32::from_rgb(0xE8, 0xE8, 0xEC),
        shadow: Color32::from_black_alpha(34),
    };

    pub const DARK: Palette = Palette {
        dark: true,
        background: Color32::from_rgb(0x1C, 0x1C, 0x1E),
        grouped_background: Color32::from_rgb(0x26, 0x26, 0x28),
        elevated: Color32::from_rgb(0x2C, 0x2C, 0x2E),
        sidebar: Color32::from_rgb(0x24, 0x24, 0x26),
        label: Color32::from_rgb(0xF5, 0xF5, 0xF7),
        secondary_label: Color32::from_rgb(0x98, 0x98, 0x9D),
        separator: Color32::from_rgb(0x3A, 0x3A, 0x3C),
        accent: Color32::from_rgb(0xFF, 0x5C, 0x83),
        accent_fill: Color32::from_rgb(0xD7, 0x0F, 0x45),
        fill: Color32::from_rgba_premultiplied(20, 20, 20, 20), // white at 8%
        fill_hover: Color32::from_rgba_premultiplied(34, 34, 34, 34), // white at 13%
        placeholder: Color32::from_rgb(0x2C, 0x2C, 0x2E),
        shadow: Color32::from_black_alpha(90),
    };

    pub fn of(ctx: &egui::Context) -> Palette {
        if ctx.global_style().visuals.dark_mode {
            Self::DARK
        } else {
            Self::LIGHT
        }
    }
}

/// Font weights available in the bundled Inter family.
#[derive(Clone, Copy, Debug)]
pub enum Weight {
    Regular,
    Medium,
    Semibold,
    Bold,
}

impl Weight {
    fn family_name(self) -> &'static str {
        match self {
            Weight::Regular => "inter-regular",
            Weight::Medium => "inter-medium",
            Weight::Semibold => "inter-semibold",
            Weight::Bold => "inter-bold",
        }
    }
}

pub fn font(size: f32, weight: Weight) -> FontId {
    match weight {
        Weight::Regular => FontId::new(size, FontFamily::Proportional),
        w => FontId::new(size, FontFamily::Name(w.family_name().into())),
    }
}

/// The type scale. Desktop body text is 14 pt (above the 13 pt HIG default, since Windows renders
/// type smaller than macOS at the same point size); nothing is smaller than 11 pt.
pub mod text {
    use super::{FontId, Weight, font};

    pub fn large_title() -> FontId {
        font(28.0, Weight::Bold)
    }
    pub fn title1() -> FontId {
        font(24.0, Weight::Bold)
    }
    pub fn title2() -> FontId {
        font(19.0, Weight::Semibold)
    }
    pub fn headline() -> FontId {
        font(14.0, Weight::Semibold)
    }
    pub fn body() -> FontId {
        font(14.0, Weight::Regular)
    }
    pub fn callout() -> FontId {
        font(13.0, Weight::Regular)
    }
    pub fn subheadline() -> FontId {
        font(12.0, Weight::Regular)
    }
    pub fn subheadline_emphasized() -> FontId {
        font(12.0, Weight::Medium)
    }
    pub fn caption() -> FontId {
        font(11.0, Weight::Medium)
    }
}

/// Spacing on an 4-point grid.
pub mod space {
    pub const XS: f32 = 4.0;
    pub const S: f32 = 8.0;
    pub const M: f32 = 12.0;
    pub const L: f32 = 16.0;
    pub const XL: f32 = 24.0;
    pub const XXL: f32 = 32.0;
    /// Margin between window content and its edges.
    pub const PAGE: f32 = 28.0;
}

pub mod radius {
    pub const CONTROL: u8 = 7;
    pub const COVER: u8 = 8;
    pub const GROUP: u8 = 10;
    pub const CAPSULE: u8 = 255;
}

pub fn install(ctx: &egui::Context) {
    install_fonts(ctx);
    ctx.set_visuals_of(Theme::Light, visuals(Palette::LIGHT));
    ctx.set_visuals_of(Theme::Dark, visuals(Palette::DARK));
    ctx.all_styles_mut(|style| {
        let s = &mut style.spacing;
        s.item_spacing = Vec2::new(8.0, 8.0);
        s.button_padding = Vec2::new(12.0, 5.0);
        s.interact_size = Vec2::new(28.0, 28.0);
        s.menu_margin = Margin::same(6);
        s.window_margin = Margin::same(16);
        s.scroll.bar_width = 8.0;
        s.scroll.floating = true;
        style.text_styles.insert(egui::TextStyle::Body, text::body());
        style.text_styles.insert(egui::TextStyle::Button, text::body());
        style
            .text_styles
            .insert(egui::TextStyle::Small, text::subheadline());
        style.text_styles.insert(egui::TextStyle::Heading, text::title2());
        style.interaction.selectable_labels = false;
    });
}

fn visuals(p: Palette) -> Visuals {
    let mut v = if p.dark { Visuals::dark() } else { Visuals::light() };
    v.override_text_color = None;
    v.panel_fill = p.background;
    v.window_fill = p.elevated;
    v.extreme_bg_color = p.elevated;
    v.faint_bg_color = p.grouped_background;
    v.text_edit_bg_color = Some(p.fill);
    v.hyperlink_color = p.accent;
    v.selection.bg_fill = p.accent.gamma_multiply(0.35);
    v.selection.stroke = Stroke::new(1.5, p.accent);
    v.window_corner_radius = CornerRadius::same(radius::GROUP);
    v.menu_corner_radius = CornerRadius::same(radius::GROUP);
    v.window_stroke = Stroke::new(1.0, p.separator);
    let shadow = Shadow {
        offset: [0, 8],
        blur: 28,
        spread: 0,
        color: p.shadow,
    };
    v.window_shadow = shadow;
    v.popup_shadow = shadow;

    let w = &mut v.widgets;
    for (state, fill) in [
        (&mut w.inactive, p.fill),
        (&mut w.hovered, p.fill_hover),
        (&mut w.active, p.fill_hover),
        (&mut w.open, p.fill_hover),
    ] {
        state.bg_fill = fill;
        state.weak_bg_fill = fill;
        state.bg_stroke = Stroke::NONE;
        state.corner_radius = CornerRadius::same(radius::CONTROL);
        state.fg_stroke = Stroke::new(1.5, p.label);
        state.expansion = 0.0;
    }
    w.noninteractive.fg_stroke = Stroke::new(1.0, p.label);
    w.noninteractive.bg_stroke = Stroke::new(1.0, p.separator);
    w.noninteractive.corner_radius = CornerRadius::same(radius::CONTROL);
    // Keyboard focus ring, the system-style accent outline.
    w.hovered.bg_stroke = Stroke::NONE;
    v
}

fn install_fonts(ctx: &egui::Context) {
    let mut fonts = FontDefinitions::default();
    let faces: [(&str, &'static [u8]); 4] = [
        (
            "inter-regular",
            include_bytes!("../assets/fonts/Inter-Regular.otf"),
        ),
        ("inter-medium", include_bytes!("../assets/fonts/Inter-Medium.otf")),
        (
            "inter-semibold",
            include_bytes!("../assets/fonts/Inter-SemiBold.otf"),
        ),
        ("inter-bold", include_bytes!("../assets/fonts/Inter-Bold.otf")),
    ];
    for (name, bytes) in faces {
        fonts
            .font_data
            .insert(name.to_owned(), Arc::new(FontData::from_static(bytes)));
    }

    // Titles often arrive in Japanese, Korean, or Chinese. Use whichever CJK system font exists
    // as a fallback so they don't render as empty boxes.
    let fallbacks = system_cjk_fonts();
    for (i, data) in fallbacks.iter().enumerate() {
        fonts.font_data.insert(format!("cjk-{i}"), Arc::new(data.clone()));
    }
    let fallback_names: Vec<String> = (0..fallbacks.len()).map(|i| format!("cjk-{i}")).collect();
    let defaults = fonts
        .families
        .get(&FontFamily::Proportional)
        .cloned()
        .unwrap_or_default();

    let chain = |primary: &str| {
        let mut list = vec![primary.to_owned()];
        list.extend(defaults.iter().cloned());
        list.extend(fallback_names.iter().cloned());
        list
    };
    let regular = chain("inter-regular");
    fonts.families.insert(FontFamily::Proportional, regular);
    for w in [Weight::Medium, Weight::Semibold, Weight::Bold] {
        let list = chain(w.family_name());
        fonts
            .families
            .insert(FontFamily::Name(w.family_name().into()), list);
    }
    ctx.set_fonts(fonts);
}

fn system_cjk_fonts() -> Vec<FontData> {
    let candidates: &[(&str, u32)] = &[
        // Windows
        ("C:\\Windows\\Fonts\\YuGothM.ttc", 0),
        ("C:\\Windows\\Fonts\\malgun.ttf", 0),
        ("C:\\Windows\\Fonts\\msyh.ttc", 0),
        // Linux, for development
        ("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc", 0),
        ("/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf", 0),
    ];
    candidates
        .iter()
        .filter_map(|(path, index)| {
            let bytes = std::fs::read(path).ok()?;
            let mut data = FontData::from_owned(bytes);
            data.index = *index;
            Some(data)
        })
        .take(3)
        .collect()
}
