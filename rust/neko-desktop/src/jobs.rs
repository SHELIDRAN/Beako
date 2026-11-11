//! Background work: API calls and image downloads run on small thread pools so the window never
//! waits on the network. Finished work comes back as an [`Event`] the app drains every frame.

use std::hash::{Hash, Hasher};
use std::path::PathBuf;
use std::sync::Arc;

use crossbeam_channel::{Receiver, Sender, unbounded};
use eframe::egui::{self, ColorImage};

use crate::api::{Chapter, Client, Feed, Filters, Manga, Page};

#[allow(clippy::large_enum_variant)] // Events are short-lived; boxing would only add allocations.
pub enum Event {
    MangaList {
        request: u64,
        result: Result<Page<Manga>, String>,
    },
    MangaDetails {
        id: String,
        result: Result<Manga, String>,
    },
    Chapters {
        manga_id: String,
        language: String,
        result: Result<Vec<Chapter>, String>,
    },
    PageUrls {
        chapter_id: String,
        result: Result<(Vec<String>, Vec<String>), String>,
    },
    Image {
        url: String,
        result: Result<ColorImage, String>,
    },
}

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum ImageKind {
    /// Cached on disk and shrunk to a thumbnail.
    Cover,
    /// Kept in memory only, shrunk only when larger than the GPU allows.
    Page,
}

type Job = Box<dyn FnOnce(&Client) + Send>;

pub struct Jobs {
    api: Sender<Job>,
    covers: Sender<Job>,
    pages: Sender<Job>,
    events_tx: Sender<Event>,
    events: Receiver<Event>,
    ctx: egui::Context,
    cache_dir: Option<PathBuf>,
}

impl Jobs {
    pub fn new(ctx: egui::Context, cache_dir: Option<PathBuf>) -> anyhow::Result<Self> {
        let client = Arc::new(Client::new()?);
        let (events_tx, events) = unbounded();
        Ok(Self {
            api: pool("api", 3, &client),
            covers: pool("covers", 6, &client),
            pages: pool("pages", 4, &client),
            events_tx,
            events,
            ctx,
            cache_dir,
        })
    }

    pub fn drain(&self) -> impl Iterator<Item = Event> + '_ {
        self.events.try_iter()
    }

    fn send(&self, queue: &Sender<Job>, work: impl FnOnce(&Client) -> Event + Send + 'static) {
        let tx = self.events_tx.clone();
        let ctx = self.ctx.clone();
        let _ = queue.send(Box::new(move |client| {
            let _ = tx.send(work(client));
            ctx.request_repaint();
        }));
    }

    pub fn list_manga(
        &self,
        request: u64,
        feed: Feed,
        query: String,
        offset: u32,
        filters: Filters,
    ) {
        self.send(&self.api, move |c| Event::MangaList {
            request,
            result: c.list_manga(feed, &query, offset, &filters).map_err(err),
        });
    }

    pub fn manga_details(&self, id: String) {
        self.send(&self.api, move |c| {
            let result = c.manga(&id).map_err(err);
            Event::MangaDetails { id, result }
        });
    }

    pub fn chapters(&self, manga_id: String, filters: Filters) {
        self.send(&self.api, move |c| {
            let result = c.chapters(&manga_id, &filters).map_err(err);
            Event::Chapters {
                manga_id,
                language: filters.language,
                result,
            }
        });
    }

    pub fn page_urls(&self, chapter_id: String, data_saver: bool) {
        self.send(&self.api, move |c| {
            let result = c.page_urls(&chapter_id, data_saver).map_err(err);
            Event::PageUrls { chapter_id, result }
        });
    }

    /// Downloads and decodes an image. `fallback` is tried when `url` fails.
    pub fn image(&self, url: String, fallback: Option<String>, kind: ImageKind, max_side: usize) {
        let cache = match kind {
            ImageKind::Cover => self.cache_dir.as_ref().map(|d| d.join("covers")),
            ImageKind::Page => None,
        };
        let queue = if kind == ImageKind::Cover {
            &self.covers
        } else {
            &self.pages
        };
        self.send(queue, move |c| {
            let result = fetch_image(c, &url, fallback.as_deref(), cache, kind, max_side);
            Event::Image { url, result }
        });
    }
}

fn err(e: anyhow::Error) -> String {
    // The outermost context is the human-readable part; the root cause helps when it differs.
    let root = e.root_cause().to_string();
    let top = e.to_string();
    if root == top {
        top
    } else {
        format!("{top}. {root}")
    }
}

fn pool(name: &str, threads: usize, client: &Arc<Client>) -> Sender<Job> {
    let (tx, rx) = unbounded::<Job>();
    for i in 0..threads {
        let rx = rx.clone();
        let client = Arc::clone(client);
        std::thread::Builder::new()
            .name(format!("{name}-{i}"))
            .spawn(move || {
                for job in rx {
                    job(&client);
                }
            })
            .expect("failed to start a worker thread");
    }
    tx
}

fn fetch_image(
    client: &Client,
    url: &str,
    fallback: Option<&str>,
    cache_dir: Option<PathBuf>,
    kind: ImageKind,
    max_side: usize,
) -> Result<ColorImage, String> {
    let cache_path = cache_dir.map(|dir| {
        let mut h = std::collections::hash_map::DefaultHasher::new();
        url.hash(&mut h);
        dir.join(format!("{:016x}", h.finish()))
    });
    let cached = cache_path.as_ref().and_then(|p| std::fs::read(p).ok());
    let bytes = match cached {
        Some(b) => b,
        None => {
            let bytes = client
                .get_bytes(url)
                .or_else(|e| match fallback {
                    Some(f) => client.get_bytes(f),
                    None => Err(e),
                })
                .map_err(|e| e.to_string())?;
            if let Some(path) = &cache_path {
                if let Some(parent) = path.parent() {
                    let _ = std::fs::create_dir_all(parent);
                }
                let _ = std::fs::write(path, &bytes);
            }
            bytes
        }
    };
    decode(&bytes, kind, max_side)
}

pub fn decode(bytes: &[u8], kind: ImageKind, max_side: usize) -> Result<ColorImage, String> {
    let img =
        image::load_from_memory(bytes).map_err(|e| format!("Couldn't read the image: {e}"))?;
    let (w, h) = (img.width() as f32, img.height() as f32);
    // Covers never draw wider than ~400 physical pixels; pages are capped at a readable 2000 px
    // width and at what the GPU can hold in one texture.
    let max_w = match kind {
        ImageKind::Cover => 400.0,
        ImageKind::Page => 2000.0,
    };
    let scale = (max_w / w)
        .min(max_side as f32 / h)
        .min(max_side as f32 / w)
        .min(1.0);
    let img = if scale < 1.0 {
        img.resize_exact(
            (w * scale).round().max(1.0) as u32,
            (h * scale).round().max(1.0) as u32,
            image::imageops::FilterType::Triangle,
        )
    } else {
        img
    };
    let rgba = img.to_rgba8();
    let size = [rgba.width() as usize, rgba.height() as usize];
    Ok(ColorImage::from_rgba_unmultiplied(size, rgba.as_raw()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn decode_shrinks_tall_pages_to_texture_limit() {
        let img = image::RgbaImage::from_pixel(100, 3000, image::Rgba([10, 20, 30, 255]));
        let mut png = Vec::new();
        image::DynamicImage::ImageRgba8(img)
            .write_to(&mut std::io::Cursor::new(&mut png), image::ImageFormat::Png)
            .unwrap();
        let out = decode(&png, ImageKind::Page, 1000).unwrap();
        assert_eq!(out.size[1], 1000);
        assert_eq!(out.size[0], 33);
        let cover = decode(&png, ImageKind::Cover, 16384).unwrap();
        assert_eq!(cover.size, [100, 3000]);
    }
}
