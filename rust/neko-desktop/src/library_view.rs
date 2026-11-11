//! Library: titles people saved, most recently read first.

use eframe::egui::Ui;

use crate::api::Manga;
use crate::app::{Action, App, Section, page_header};
use crate::cards;
use crate::widgets::{self, Icon};

impl App {
    pub fn library_view(&mut self, ui: &mut Ui) {
        let entries: Vec<Manga> = self
            .library
            .sorted_entries()
            .into_iter()
            .map(|e| e.manga.clone())
            .collect();
        if entries.is_empty() {
            crate::app::page_frame().show(ui, |ui| {
                page_header(ui, "Library", None, |_| {});
                let browse = widgets::placeholder_view(
                    ui,
                    Icon::Library,
                    "Your Library Is Empty",
                    "Titles you add from Browse appear here, with your place saved in each one.",
                    Some("Browse MangaDex"),
                );
                if browse {
                    self.actions.push(Action::Section(Section::Browse));
                }
            });
            return;
        }

        let count = entries.len();
        let subtitle = if count == 1 {
            "1 title".to_owned()
        } else {
            format!("{count} titles")
        };
        let images = &mut self.images;
        let jobs = &self.jobs;
        let library = &self.library;
        let actions = &mut self.actions;
        cards::grid(
            ui,
            "library-grid",
            &entries,
            |ui| page_header(ui, "Library", Some(&subtitle), |_| {}),
            |ui, manga, width| {
                let caption = match library
                    .progress(&manga.id)
                    .and_then(|p| p.chapter_name.clone())
                {
                    Some(name) => format!("Continue {name}"),
                    None => "Not started".to_owned(),
                };
                let r = cards::cover_card(ui, images, jobs, manga, width, &caption, false);
                if r.clicked() {
                    actions.push(Action::OpenManga(manga.clone()));
                }
                r.context_menu(|ui| {
                    if ui.button("Remove from Library").clicked() {
                        actions.push(Action::ToggleLibrary(manga.clone()));
                        ui.close();
                    }
                });
            },
            |_| {},
        );
    }
}
