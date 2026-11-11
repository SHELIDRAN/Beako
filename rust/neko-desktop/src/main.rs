//! Neko for Windows: a MangaDex reader styled after Apple's design language.

// No console window behind the app in release builds.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod api;
mod app;
mod auth;
mod browse;
mod cards;
mod comick;
mod details;
mod filters_view;
mod glass;
mod images;
mod jobs;
mod library;
mod library_view;
mod reader;
mod search;
mod settings_view;
mod theme;
mod widgets;

use eframe::egui;

fn main() -> eframe::Result {
    let icon = image::load_from_memory(include_bytes!("../assets/icon.png"))
        .map(|img| {
            let rgba = img.to_rgba8();
            egui::IconData {
                width: rgba.width(),
                height: rgba.height(),
                rgba: rgba.into_raw(),
            }
        })
        .ok();
    let mut viewport = egui::ViewportBuilder::default()
        .with_title("Neko")
        .with_app_id("org.neko.desktop")
        .with_inner_size([1280.0, 820.0])
        .with_min_inner_size([960.0, 620.0])
        // A transparent surface lets the Windows Mica material show through the sidebar.
        .with_transparent(cfg!(windows));
    if let Some(icon) = icon {
        viewport = viewport.with_icon(icon);
    }
    let options = eframe::NativeOptions {
        viewport,
        persist_window: true,
        ..Default::default()
    };
    eframe::run_native("Neko", options, Box::new(|cc| Ok(Box::new(app::App::new(cc)))))
}
