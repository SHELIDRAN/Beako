//! GPU textures for covers and pages, loaded on demand and evicted least-recently-used.

use std::collections::HashMap;

use eframe::egui::{self, TextureHandle, TextureOptions, Vec2};

use crate::jobs::{Decoded, ImageKind, Jobs};

pub enum ImageState {
    Loading,
    Ready(Loaded),
    Failed(String),
}

pub struct Loaded {
    pub texture: TextureHandle,
    /// A blurred copy, for frosted glass drawn over this image.
    pub blurred: Option<TextureHandle>,
    /// When the texture arrived, for a short fade-in.
    pub at: f64,
}

impl Loaded {
    /// 0 → 1 over a quarter second after loading.
    pub fn fade(&self, now: f64) -> f32 {
        ((now - self.at) / 0.25).clamp(0.0, 1.0) as f32
    }
}

struct Entry {
    state: ImageState,
    kind: ImageKind,
    url: String,
    last_used: u64,
    fallback: Option<String>,
}

#[derive(Default)]
pub struct Images {
    entries: HashMap<String, Entry>,
    frame: u64,
}

const MAX_COVERS: usize = 400;
const MAX_PAGES: usize = 30;

impl Images {
    pub fn begin_frame(&mut self) {
        self.frame += 1;
    }

    /// The image for `url`, starting a download the first time it's asked for.
    pub fn get(&mut self, jobs: &Jobs, ctx: &egui::Context, url: &str, kind: ImageKind) -> &ImageState {
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
        let key = kind.key(url);
        if !self.entries.contains_key(&key) {
            let max_side = ctx.input(|i| i.max_texture_side).min(16_384);
            jobs.image(url.to_owned(), fallback.map(str::to_owned), kind, max_side);
            self.entries.insert(
                key.clone(),
                Entry {
                    state: ImageState::Loading,
                    kind,
                    url: url.to_owned(),
                    last_used: frame,
                    fallback: fallback.map(str::to_owned),
                },
            );
        }
        let entry = self.entries.get_mut(&key).expect("inserted above");
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
    pub fn retry(&mut self, jobs: &Jobs, ctx: &egui::Context, url: &str, kind: ImageKind) {
        if let Some(entry) = self.entries.remove(&kind.key(url)) {
            let _ = self.get_with_fallback(jobs, ctx, &entry.url, entry.fallback.as_deref(), entry.kind);
        }
    }

    pub fn finish(&mut self, ctx: &egui::Context, key: String, result: Result<Decoded, String>) {
        let Some(entry) = self.entries.get_mut(&key) else {
            return;
        };
        entry.state = match result {
            Ok(decoded) => {
                let options = TextureOptions::LINEAR.with_mipmap_mode(Some(egui::TextureFilter::Linear));
                let blurred = decoded
                    .blurred
                    .map(|b| ctx.load_texture(format!("{key}#blur"), b, TextureOptions::LINEAR));
                ImageState::Ready(Loaded {
                    texture: ctx.load_texture(key, decoded.image, options),
                    blurred,
                    at: ctx.input(|i| i.time),
                })
            }
            Err(e) => ImageState::Failed(e),
        };
        self.evict();
    }

    fn evict(&mut self) {
        for (is_page, limit) in [(false, MAX_COVERS), (true, MAX_PAGES)] {
            let mut ready: Vec<(u64, String)> = self
                .entries
                .iter()
                .filter(|(_, e)| {
                    matches!(e.kind, ImageKind::Page { .. }) == is_page
                        && matches!(e.state, ImageState::Ready(_))
                })
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
