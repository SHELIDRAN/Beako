//! Settings: a short grouped form of options people rarely change (HIG › Settings).

use eframe::egui::{
    self, CornerRadius, CursorIcon, Rect, RichText, Role, Sense, Ui, WidgetInfo, pos2, vec2,
};

use crate::api::language_name;
use crate::app::{Action, App, page_frame, page_header};
use crate::library::ReadingMode;
use crate::theme::{Palette, radius, space, text};
use crate::widgets::{self, ButtonStyle};

const LANGUAGES: &[&str] = &[
    "en", "es", "es-la", "fr", "de", "it", "pt-br", "ru", "uk", "pl", "tr", "id", "vi", "th", "ar",
    "ja", "ko", "zh", "zh-hk",
];

impl App {
    pub fn settings_view(&mut self, ui: &mut Ui) {
        let p = Palette::of(ui.ctx());
        let mut settings = self.library.settings.clone();
        egui::ScrollArea::vertical().auto_shrink([false, false]).show(ui, |ui| {
            page_frame().show(ui, |ui| {
                ui.set_max_width(640.0);
                page_header(ui, "Settings", None, |_| {});

                section_label(ui, "Chapters");
                group(ui, |ui| {
                    row(ui, "Chapter Language", Some("Browse shows titles with chapters in this language."), |ui| {
                        egui::ComboBox::from_id_salt("language")
                            .selected_text(language_name(&settings.filters.language))
                            .width(200.0)
                            .show_ui(ui, |ui| {
                                for code in LANGUAGES {
                                    ui.selectable_value(&mut settings.filters.language, (*code).to_owned(), language_name(code));
                                }
                            });
                    });
                    divider(ui);
                    row(ui, "Data Saver", Some("Loads smaller, compressed pages to use less bandwidth."), |ui| {
                        toggle(ui, &mut settings.filters.data_saver, "Data Saver");
                    });
                });

                section_label(ui, "Reader");
                group(ui, |ui| {
                    row(ui, "Default Reading Mode", None, |ui| {
                        egui::ComboBox::from_id_salt("mode")
                            .selected_text(settings.reading_mode.label())
                            .width(200.0)
                            .show_ui(ui, |ui| {
                                for mode in ReadingMode::ALL {
                                    ui.selectable_value(&mut settings.reading_mode, mode, mode.label());
                                }
                            });
                    });
                });

                section_label(ui, "Content");
                group(ui, |ui| {
                    row(ui, "Show Suggestive Titles", None, |ui| {
                        toggle(ui, &mut settings.filters.show_suggestive, "Show Suggestive Titles");
                    });
                    divider(ui);
                    row(ui, "Show Erotica Titles", Some("Adult content. Pornographic titles are never shown."), |ui| {
                        toggle(ui, &mut settings.filters.show_erotica, "Show Erotica Titles");
                    });
                });

                section_label(ui, "About");
                group(ui, |ui| {
                    row(ui, "Version", None, |ui| {
                        ui.label(RichText::new(env!("CARGO_PKG_VERSION")).font(text::body()).color(p.secondary_label));
                    });
                    if let Some(dir) = self.data_dir.clone() {
                        divider(ui);
                        row(ui, "Library Data", Some(&dir.display().to_string()), |ui| {
                            if widgets::button(ui, ButtonStyle::Bordered, None, "Show in Explorer").clicked() {
                                let _ = std::fs::create_dir_all(&dir);
                                self.actions.push(Action::OpenUrl(dir.display().to_string()));
                            }
                        });
                    }
                });
                ui.add_space(space::L);
                ui.label(
                    RichText::new("Neko is unofficial and not affiliated with MangaDex. It hosts no content.")
                        .font(text::subheadline())
                        .color(p.secondary_label),
                );
                ui.add_space(space::XXL);
            });
        });

        if settings.filters != self.library.settings.filters {
            self.library.settings = settings;
            self.actions.push(Action::FiltersChanged);
        } else if settings.reading_mode != self.library.settings.reading_mode {
            self.library.settings = settings;
            self.library.save();
        }
    }
}

fn section_label(ui: &mut Ui, label: &str) {
    let p = Palette::of(ui.ctx());
    ui.add_space(space::L);
    ui.horizontal(|ui| {
        ui.add_space(space::M);
        ui.label(
            RichText::new(label)
                .font(text::subheadline_emphasized())
                .color(p.secondary_label),
        );
    });
    ui.add_space(space::XS);
}

fn group(ui: &mut Ui, add: impl FnOnce(&mut Ui)) {
    let p = Palette::of(ui.ctx());
    egui::Frame::NONE
        .fill(p.grouped_background)
        .corner_radius(CornerRadius::same(radius::GROUP))
        .inner_margin(egui::Margin::symmetric(16, 4))
        .show(ui, |ui| {
            ui.set_width(ui.available_width());
            add(ui);
        });
}

fn divider(ui: &mut Ui) {
    let p = Palette::of(ui.ctx());
    let r = ui.available_rect_before_wrap();
    ui.painter()
        .hline(r.x_range(), r.top(), egui::Stroke::new(1.0, p.separator));
    ui.add_space(1.0);
}

fn row(ui: &mut Ui, label: &str, detail: Option<&str>, control: impl FnOnce(&mut Ui)) {
    let p = Palette::of(ui.ctx());
    ui.horizontal(|ui| {
        ui.set_min_height(48.0);
        ui.vertical(|ui| {
            ui.add_space(if detail.is_some() { 8.0 } else { 14.0 });
            ui.label(RichText::new(label).font(text::body()).color(p.label));
            if let Some(d) = detail {
                ui.add(
                    egui::Label::new(
                        RichText::new(d)
                            .font(text::subheadline())
                            .color(p.secondary_label),
                    )
                    .wrap(),
                );
                ui.add_space(6.0);
            }
        });
        ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), control);
    });
}

/// A switch styled like Apple's: green-free here, it uses Neko's accent when on.
pub fn toggle(ui: &mut Ui, on: &mut bool, label: &str) -> egui::Response {
    let p = Palette::of(ui.ctx());
    let (rect, mut response) = ui.allocate_exact_size(vec2(40.0, 24.0), Sense::click());
    response.widget_info(|| WidgetInfo::selected(Role::Switch, true, *on, label));
    if response.clicked() {
        *on = !*on;
        response.mark_changed();
    }
    let t = ui.ctx().animate_bool_responsive(response.id, *on);
    let off = if p.dark {
        egui::Color32::from_rgb(0x48, 0x48, 0x4C)
    } else {
        egui::Color32::from_rgb(0xDC, 0xDC, 0xE0)
    };
    let track = if *on { p.accent_fill } else { off };
    ui.painter()
        .rect_filled(rect, CornerRadius::same(12), track);
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
    ui.painter()
        .circle_filled(knob.center(), 10.0, egui::Color32::WHITE);
    widgets::focus_ring(ui, &response, rect, 12);
    response.on_hover_cursor(CursorIcon::PointingHand)
}
