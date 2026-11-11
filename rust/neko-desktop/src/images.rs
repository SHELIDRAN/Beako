//! GPU textures for covers and pages, loaded on demand and evicted least-recently-used.

use std::collections::HashMap;

use eframe::egui::{self, ColorImage, TextureHandle, TextureOptions, Vec2};

use crate::jobs::{ImageKind, Jobs};

pub enum ImageState {
    Loading,
    Ready(TextureHandle),
    Failed(String),
}

struct Entry {
    state: ImageState,
    kind: ImageKind,
    last_used: u64,
    fallback: Option<String>,
}

#[derive(Default)]
pub struct Images {
    entries: HashMap<String, Entry>,
    frame: u64,
}

const MAX_COVERS: usize = 400;
const MAX_PAGES: usize = 24;

impl Images {
    pub fn begin_frame(&mut self) {
        self.frame += 1;
    }

    /// The image for `url`, starting a download the first time it's asked for.
    pub fn get(
        &mut self,
        jobs: &Jobs,
        ctx: &egui::Context,
        url: &str,
        kind: ImageKind,
    ) -> &ImageState {
        self.get_with_fallback(jobs, ctx, url, None, kind)
    }

    pub fn get_with_fallback(
        &mut self,
        jobs: &Jobs,
        ctx: &egui::Context,
        url: &str,
        fallback: Option<&str>,
        kind: ImageKind,
    ) -> &ImageState {
        let frame = self.frame;
        if !self.entries.contains_key(url) {
            let max_side = ctx.input(|i| i.max_texture_side).min(16_384);
            jobs.image(url.to_owned(), fallback.map(str::to_owned), kind, max_side);
            self.entries.insert(
                url.to_owned(),
                Entry {
                    state: ImageState::Loading,
                    kind,
                    last_used: frame,
                    fallback: fallback.map(str::to_owned),
                },
            );
        }
        let entry = self.entries.get_mut(url).expect("inserted above");
        entry.last_used = frame;
        &entry.state
    }

    /// Starts loading without needing the result yet (for reading ahead).
    pub fn prefetch(
        &mut self,
        jobs: &Jobs,
        ctx: &egui::Context,
        url: &str,
        fallback: Option<&str>,
        kind: ImageKind,
    ) {
        let _ = self.get_with_fallback(jobs, ctx, url, fallback, kind);
    }

    /// Lets a failed image be requested again.
    pub fn retry(&mut self, jobs: &Jobs, ctx: &egui::Context, url: &str) {
        if let Some(entry) = self.entries.remove(url) {
            let fallback = entry.fallback;
            let _ = self.get_with_fallback(jobs, ctx, url, fallback.as_deref(), entry.kind);
        }
    }

    pub fn finish(&mut self, ctx: &egui::Context, url: String, result: Result<ColorImage, String>) {
        let Some(entry) = self.entries.get_mut(&url) else {
            return;
        };
        entry.state = match result {
            Ok(img) => {
                let options =
                    TextureOptions::LINEAR.with_mipmap_mode(Some(egui::TextureFilter::Linear));
                ImageState::Ready(ctx.load_texture(url, img, options))
            }
            Err(e) => ImageState::Failed(e),
        };
        self.evict();
    }

    fn evict(&mut self) {
        for (kind, limit) in [(ImageKind::Cover, MAX_COVERS), (ImageKind::Page, MAX_PAGES)] {
            let mut ready: Vec<(u64, String)> = self
                .entries
                .iter()
                .filter(|(_, e)| e.kind == kind && matches!(e.state, ImageState::Ready(_)))
                .map(|(k, e)| (e.last_used, k.clone()))
                .collect();
            if ready.len() <= limit {
                continue;
            }
            ready.sort();
            let excess = ready.len() - limit;
            // Oldest first, and never something drawn in the last couple of frames.
            let stale = ready
                .into_iter()
                .filter(|(last_used, _)| self.frame.saturating_sub(*last_used) > 2);
            for (_, key) in stale.take(excess) {
                self.entries.remove(&key);
            }
        }
    }
}

pub fn texture_size(tex: &TextureHandle) -> Vec2 {
    let [w, h] = tex.size();
    Vec2::new(w as f32, h as f32)
}
