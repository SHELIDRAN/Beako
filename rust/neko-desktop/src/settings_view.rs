//! Settings: a grouped form of options people rarely change (HIG › Settings).

use eframe::egui::{self, CornerRadius, RichText, Ui};

use crate::api::language_name;
use crate::app::{Action, App, page_frame, page_header};
use crate::library::{Backdrop, Fit, PageLayout, ReaderSettings, ReadingMode};
use crate::theme::{Palette, radius, space, text};
use crate::widgets::{self, ButtonStyle, Icon};

const LANGUAGES: &[&str] = &[
    "en", "es", "es-la", "fr", "de", "it", "pt-br", "ru", "uk", "pl", "tr", "id", "vi", "th", "ar", "ja",
    "ko", "zh", "zh-hk",
];

const API_CLIENTS_URL: &str = "https://mangadex.org/settings";

impl App {
    pub fn settings_view(&mut self, ui: &mut Ui) {
        let p = Palette::of(ui.ctx());
        let mut settings = self.library.settings.clone();
        egui::ScrollArea::vertical().auto_shrink([false, false]).show(ui, |ui| {
            page_frame().show(ui, |ui| {
                ui.set_max_width(680.0);
                page_header(ui, "Settings", None, |_| {});

                section_label(ui, "MangaDex Account");
                self.account_group(ui, &mut settings.pause_sync);

                section_label(ui, "Comick");
                self.comick_group(ui);

                section_label(ui, "Chapters");
                group(ui, |ui| {
                    row(ui, "Chapter Language", Some("Browse shows titles with chapters in this language."), |ui| {
                        egui::ComboBox::from_id_salt("language")
                            .selected_text(language_name(&settings.filters.language))
                            .width(200.0)
                            .show_ui(ui, |ui| {
                                for code in LANGUAGES {
                                    ui.selectable_value(
                                        &mut settings.filters.language,
                                        (*code).to_owned(),
                                        language_name(code),
                                    );
                                }
                            });
                    });
                    divider(ui);
                    row(ui, "Data Saver", Some("Loads smaller, compressed pages to use less bandwidth."), |ui| {
                        widgets::toggle(ui, &mut settings.filters.data_saver, "Data Saver");
                    });
                });

                section_label(ui, "Reader");
                reader_settings_group(ui, &mut settings.reading_mode, &mut settings.reader);

                section_label(ui, "Content");
                group(ui, |ui| {
                    row(ui, "Show Suggestive Titles", None, |ui| {
                        widgets::toggle(ui, &mut settings.filters.show_suggestive, "Show Suggestive Titles");
                    });
                    divider(ui);
                    row(ui, "Show Erotica Titles", Some("Adult content. Pornographic titles are never shown."), |ui| {
                        widgets::toggle(ui, &mut settings.filters.show_erotica, "Show Erotica Titles");
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
                    RichText::new(
                        "Neko is unofficial and not affiliated with MangaDex, Comick, or Comix. It hosts no content.",
                    )
                    .font(text::subheadline())
                    .color(p.secondary_label),
                );
                ui.add_space(space::XXL);
            });
        });

        let old = &self.library.settings;
        if settings.filters != old.filters {
            self.library.settings = settings;
            self.actions.push(Action::FiltersChanged);
        } else if settings.reading_mode != old.reading_mode
            || settings.reader != old.reader
            || settings.pause_sync != old.pause_sync
        {
            self.library.settings = settings;
            self.library.save();
        }
    }

    fn comick_group(&mut self, ui: &mut Ui) {
        let p = Palette::of(ui.ctx());
        match self.comick.username.clone() {
            Some(name) => group(ui, |ui| {
                row(
                    ui,
                    &format!("Connected as {name}"),
                    Some("Track titles from their pages in Neko."),
                    |ui| {
                        if widgets::button(ui, ButtonStyle::Bordered, None, "Disconnect").clicked() {
                            self.actions.push(Action::ComickDisconnect);
                        }
                    },
                );
                divider(ui);
                let mut auto = self.library.settings.comick_auto_progress();
                row(
                    ui,
                    "Update Progress Automatically",
                    Some("When you finish a chapter, Neko updates its chapter on Comick."),
                    |ui| {
                        widgets::toggle(ui, &mut auto, "Update Progress Automatically");
                    },
                );
                if auto != self.library.settings.comick_auto_progress() {
                    self.library.settings.comick_auto_progress = Some(auto);
                    self.library.save();
                }
            }),
            None => group(ui, |ui| {
                ui.add_space(space::M);
                ui.add(
                    egui::Label::new(
                        RichText::new(
                            "Track your reading on Comick. Register an app on comick.dev under Developer apps: \
                             choose “Browser or mobile app without a private backend” and “Native mobile or \
                             desktop app”, allow library:write, and add the callback URL below. Then paste the \
                             app’s client ID here and connect. Comick opens in your browser to approve access.",
                        )
                        .font(text::callout())
                        .color(p.secondary_label),
                    )
                    .wrap(),
                );
                ui.add_space(space::S);
                if widgets::button(
                    ui,
                    ButtonStyle::Plain,
                    Some(Icon::External),
                    "Open Comick Developer Apps",
                )
                .clicked()
                {
                    self.actions
                        .push(Action::OpenUrl("https://comick.dev/developers/apps".into()));
                }
                ui.add_space(space::S);
                egui::Grid::new("comick")
                    .num_columns(2)
                    .spacing([space::M, space::S])
                    .show(ui, |ui| {
                        ui.label(RichText::new("Callback URL").font(text::body()).color(p.label));
                        ui.horizontal(|ui| {
                            let mut url = crate::comick::CALLBACK_URL.to_owned();
                            ui.add(
                                egui::TextEdit::singleline(&mut url)
                                    .desired_width(300.0)
                                    .interactive(false)
                                    .margin(egui::Margin::symmetric(10, 6)),
                            );
                            if widgets::button(ui, ButtonStyle::Bordered, None, "Copy").clicked() {
                                ui.ctx().copy_text(crate::comick::CALLBACK_URL.to_owned());
                                self.actions
                                    .push(Action::Notice("Copied the callback URL".into()));
                            }
                        });
                        ui.end_row();
                        ui.label(RichText::new("Client ID").font(text::body()).color(p.label));
                        ui.add(
                            egui::TextEdit::singleline(&mut self.library.settings.comick_client_id)
                                .hint_text("From your Comick developer app")
                                .desired_width(320.0)
                                .margin(egui::Margin::symmetric(10, 6)),
                        );
                        ui.end_row();
                    });
                ui.add_space(space::S);
                if let Some(err) = &self.comick.error {
                    ui.add(egui::Label::new(RichText::new(err).font(text::callout()).color(p.accent)).wrap());
                    ui.add_space(space::XS);
                }
                ui.horizontal(|ui| {
                    let busy = self.comick.busy;
                    ui.add_enabled_ui(
                        !busy && !self.library.settings.comick_client_id.trim().is_empty(),
                        |ui| {
                            if widgets::button(
                                ui,
                                ButtonStyle::Prominent,
                                Some(Icon::Person),
                                "Connect Comick",
                            )
                            .clicked()
                            {
                                self.actions.push(Action::ComickConnect);
                            }
                        },
                    );
                    if busy {
                        widgets::spinner(ui, 18.0);
                        ui.label(
                            RichText::new("Waiting for you to approve in the browser…")
                                .font(text::callout())
                                .color(p.secondary_label),
                        );
                    }
                });
                ui.add_space(space::M);
            }),
        }
    }

    fn account_group(&mut self, ui: &mut Ui, pause_sync: &mut bool) {
        let p = Palette::of(ui.ctx());
        let signed_in = self.account.username.clone();
        group(ui, |ui| match signed_in {
            Some(name) => {
                row(
                    ui,
                    &format!("Signed in as {name}"),
                    Some("Using your MangaDex personal API client."),
                    |ui| {
                        if widgets::button(ui, ButtonStyle::Bordered, None, "Sign Out").clicked() {
                            self.actions.push(Action::SignOut);
                        }
                    },
                );
                divider(ui);
                let detail = self.account.last_sync.clone().unwrap_or_else(|| {
                    "Adds titles you follow on MangaDex to your library and loads their reading statuses."
                        .into()
                });
                row(ui, "Sync Follows", Some(&detail), |ui| {
                    if self.account.syncing {
                        widgets::spinner(ui, 18.0);
                    } else if widgets::button(ui, ButtonStyle::Bordered, Some(Icon::Sync), "Sync Now")
                        .clicked()
                    {
                        self.actions.push(Action::Sync);
                    }
                });
                divider(ui);
                let mut sync = !*pause_sync;
                row(
                    ui,
                    "Sync Reading to MangaDex",
                    Some("Marks chapters read on MangaDex and follows titles you add to your library."),
                    |ui| {
                        widgets::toggle(ui, &mut sync, "Sync Reading to MangaDex");
                    },
                );
                *pause_sync = !sync;
            }
            None => {
                ui.add_space(space::M);
                ui.add(
                    egui::Label::new(
                        RichText::new(
                            "Sign in with a personal API client to sync follows, reading statuses, and read \
                             chapters. Create one on mangadex.org under Settings → API Clients; once MangaDex \
                             approves it, copy its client ID and secret here. Your password is used once to sign \
                             in and is never stored.",
                        )
                        .font(text::callout())
                        .color(p.secondary_label),
                    )
                    .wrap(),
                );
                ui.add_space(space::S);
                if widgets::button(
                    ui,
                    ButtonStyle::Plain,
                    Some(Icon::External),
                    "Open MangaDex API Clients",
                )
                .clicked()
                {
                    self.actions.push(Action::OpenUrl(API_CLIENTS_URL.into()));
                }
                ui.add_space(space::S);
                let busy = self.account.busy;
                let f = &mut self.account.form;
                let mut submit = false;
                egui::Grid::new("signin")
                    .num_columns(2)
                    .spacing([space::M, space::S])
                    .show(ui, |ui| {
                        for (label, value, secret, hint) in [
                            ("Username", &mut f.username, false, "MangaDex username"),
                            ("Password", &mut f.password, true, "Used once, never saved"),
                            ("Client ID", &mut f.client_id, false, "personal-client-…"),
                            (
                                "Client Secret",
                                &mut f.client_secret,
                                true,
                                "Saved in Windows Credential Manager",
                            ),
                        ] {
                            ui.label(RichText::new(label).font(text::body()).color(p.label));
                            let r = ui.add(
                                egui::TextEdit::singleline(value)
                                    .password(secret)
                                    .hint_text(hint)
                                    .desired_width(320.0)
                                    .margin(egui::Margin::symmetric(10, 6)),
                            );
                            if r.lost_focus() && ui.input(|i| i.key_pressed(egui::Key::Enter)) {
                                submit = true;
                            }
                            ui.end_row();
                        }
                    });
                ui.add_space(space::S);
                if let Some(err) = &self.account.error {
                    ui.add(egui::Label::new(RichText::new(err).font(text::callout()).color(p.accent)).wrap());
                    ui.add_space(space::XS);
                }
                ui.horizontal(|ui| {
                    ui.add_enabled_ui(!busy, |ui| {
                        if widgets::button(ui, ButtonStyle::Prominent, Some(Icon::Person), "Sign In")
                            .clicked()
                        {
                            submit = true;
                        }
                    });
                    if busy {
                        widgets::spinner(ui, 18.0);
                    }
                });
                if submit && !busy {
                    self.actions.push(Action::SignIn);
                }
                ui.add_space(space::M);
            }
        });
    }
}

/// Every reader option, grouped. Also shown in the reader's own settings panel.
pub fn reader_settings_group(ui: &mut Ui, mode: &mut ReadingMode, r: &mut ReaderSettings) {
    group(ui, |ui| {
        row(ui, "Reading Mode", None, |ui| {
            combo(ui, "mode", mode, &ReadingMode::ALL, ReadingMode::label);
        });
        divider(ui);
        row(
            ui,
            "Page Size",
            Some("For the left-to-right and right-to-left modes."),
            |ui| {
                combo(ui, "fit", &mut r.fit, &Fit::ALL, Fit::label);
            },
        );
        divider(ui);
        row(
            ui,
            "Page Layout",
            Some("Automatic shows two pages when the window is wide."),
            |ui| {
                combo(ui, "layout", &mut r.layout, &PageLayout::ALL, PageLayout::label);
            },
        );
        if r.layout != PageLayout::Single {
            divider(ui);
            row(
                ui,
                "Show the First Page Alone",
                Some("Keeps two-page spreads aligned after the cover."),
                |ui| {
                    widgets::toggle(ui, &mut r.cover_alone, "Show the First Page Alone");
                },
            );
        }
        divider(ui);
        row(ui, "Background", None, |ui| {
            combo(ui, "backdrop", &mut r.backdrop, &Backdrop::ALL, Backdrop::label);
        });
        divider(ui);
        slider_row(
            ui,
            "Strip Width",
            "In vertical scroll.",
            &mut r.strip_width,
            30..=100,
            5,
            "%",
        );
        divider(ui);
        slider_row(
            ui,
            "Space Between Pages",
            "In vertical scroll.",
            &mut r.page_gap,
            0..=48,
            4,
            " pt",
        );
        divider(ui);
        row(
            ui,
            "Crop Borders",
            Some("Trims blank margins around scanned pages."),
            |ui| {
                widgets::toggle(ui, &mut r.crop_borders, "Crop Borders");
            },
        );
        divider(ui);
        slider_row(
            ui,
            "Dim Pages",
            "For reading at night.",
            &mut r.dim,
            0..=80,
            5,
            "%",
        );
    });
    ui.add_space(space::M);
    group(ui, |ui| {
        row(
            ui,
            "Click to Turn Pages",
            Some("Click the left or right third of the page."),
            |ui| {
                widgets::toggle(ui, &mut r.tap_zones, "Click to Turn Pages");
            },
        );
        if r.tap_zones {
            divider(ui);
            row(ui, "Swap Click Zones", None, |ui| {
                widgets::toggle(ui, &mut r.invert_tap_zones, "Swap Click Zones");
            });
        }
        divider(ui);
        row(
            ui,
            "Mouse Wheel Turns Pages",
            Some("Hold Ctrl and scroll to zoom instead."),
            |ui| {
                widgets::toggle(ui, &mut r.wheel_turns_pages, "Mouse Wheel Turns Pages");
            },
        );
        divider(ui);
        row(
            ui,
            "Continue to the Next Chapter",
            Some("Turning past the last page opens the next chapter."),
            |ui| {
                widgets::toggle(ui, &mut r.auto_next_chapter, "Continue to the Next Chapter");
            },
        );
        divider(ui);
        slider_row(
            ui,
            "Read Ahead",
            "Pages loaded before you reach them.",
            &mut r.preload,
            1..=10,
            1,
            " pages",
        );
        divider(ui);
        slider_row(
            ui,
            "Hide Controls After",
            "0 keeps them visible.",
            &mut r.hide_controls_after,
            0..=10,
            1,
            " s",
        );
        divider(ui);
        row(ui, "Always Show Page Number", None, |ui| {
            widgets::toggle(ui, &mut r.always_show_page_number, "Always Show Page Number");
        });
        divider(ui);
        row(
            ui,
            "Keep the Screen On",
            Some("Stops Windows from sleeping while you read."),
            |ui| {
                widgets::toggle(ui, &mut r.keep_awake, "Keep the Screen On");
            },
        );
    });
}

fn combo<T: Copy + PartialEq>(
    ui: &mut Ui,
    id: &str,
    value: &mut T,
    options: &[T],
    label: fn(T) -> &'static str,
) {
    egui::ComboBox::from_id_salt(id)
        .selected_text(label(*value))
        .width(180.0)
        .show_ui(ui, |ui| {
            for o in options {
                ui.selectable_value(value, *o, label(*o));
            }
        });
}

fn slider_row(
    ui: &mut Ui,
    label: &str,
    detail: &str,
    value: &mut u32,
    range: std::ops::RangeInclusive<u32>,
    step: u32,
    unit: &str,
) {
    let p = Palette::of(ui.ctx());
    row(ui, label, Some(detail), |ui| {
        ui.label(
            RichText::new(format!("{value}{unit}"))
                .font(text::callout())
                .color(p.secondary_label),
        );
        widgets::slider(ui, value, range, step, 170.0, label);
    });
}

pub fn section_label(ui: &mut Ui, label: &str) {
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

pub fn group(ui: &mut Ui, add: impl FnOnce(&mut Ui)) {
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

pub fn divider(ui: &mut Ui) {
    let p = Palette::of(ui.ctx());
    let r = ui.available_rect_before_wrap();
    ui.painter()
        .hline(r.x_range(), r.top(), egui::Stroke::new(1.0, p.separator));
    ui.add_space(1.0);
}

pub fn row(ui: &mut Ui, label: &str, detail: Option<&str>, control: impl FnOnce(&mut Ui)) {
    let p = Palette::of(ui.ctx());
    ui.horizontal(|ui| {
        ui.set_min_height(48.0);
        ui.vertical(|ui| {
            ui.set_max_width((ui.available_width() - 250.0).max(160.0));
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
