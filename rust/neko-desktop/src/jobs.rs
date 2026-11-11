//! Background work: API calls and image downloads run on small thread pools so the window never
//! waits on the network. Finished work comes back as an [`Event`] the app drains every frame.

use std::collections::HashMap;
use std::hash::{Hash, Hasher};
use std::path::PathBuf;
use std::sync::Arc;

use crossbeam_channel::{Receiver, Sender, unbounded};
use eframe::egui::{self, ColorImage};

use crate::api::{Chapter, Client, Feed, Filters, Manga, Page, Tag};
use crate::auth::Credentials;
use crate::search::{BakaSearch, BakaSeries, Search};

/// A decoded image plus, for pages, a tiny blurred copy used as the frosted-glass backdrop
/// behind the reader's controls.
pub struct Decoded {
    pub image: ColorImage,
    pub blurred: Option<ColorImage>,
}

pub struct SyncResult {
    pub follows: Vec<Manga>,
    pub statuses: HashMap<String, String>,
}

#[allow(clippy::large_enum_variant)] // Events are short-lived; boxing would only add allocations.
pub enum Event {
    MangaList {
        request: u64,
        result: Result<Page<Manga>, String>,
    },
    BakaList {
        request: u64,
        result: Result<Page<BakaSeries>, String>,
    },
    Resolved {
        baka_id: u64,
        result: Result<Manga, String>,
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
        key: String,
        result: Result<Decoded, String>,
    },
    Tags(Result<Vec<Tag>, String>),
    /// The signed-in username, `None` after signing out, or why signing in failed.
    Account(Result<Option<String>, String>),
    Synced(Result<SyncResult, String>),
    ReadMarkers {
        manga_id: String,
        result: Result<Vec<String>, String>,
    },
    /// A background write to MangaDex failed.
    Notice(String),
}

#[derive(Clone, Copy, PartialEq, Eq, Debug, Hash)]
pub enum ImageKind {
    /// Cached on disk and shrunk to a thumbnail.
    Cover,
    /// A cover blurred into a soft color field, for backdrops.
    CoverBlur,
    /// A page, optionally with its blank margins trimmed.
    Page { crop: bool },
}

impl ImageKind {
    pub fn key(self, url: &str) -> String {
        match self {
            ImageKind::Cover => url.to_owned(),
            ImageKind::CoverBlur => format!("blur|{url}"),
            ImageKind::Page { crop: false } => format!("page|{url}"),
            ImageKind::Page { crop: true } => format!("crop|{url}"),
        }
    }
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
    client: Arc<Client>,
}

impl Jobs {
    pub fn new(ctx: egui::Context, cache_dir: Option<PathBuf>) -> anyhow::Result<Self> {
        let client = Arc::new(Client::new()?);
        let (events_tx, events) = unbounded();
        Ok(Self {
            api: pool("api", 4, &client),
            covers: pool("covers", 6, &client),
            pages: pool("pages", 4, &client),
            events_tx,
            events,
            ctx,
            cache_dir,
            client,
        })
    }

    pub fn signed_in_as(&self) -> Option<String> {
        self.client.session.username()
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

    /// Runs a write in the background and reports only failures.
    fn write(&self, what: &'static str, work: impl FnOnce(&Client) -> anyhow::Result<()> + Send + 'static) {
        let tx = self.events_tx.clone();
        let ctx = self.ctx.clone();
        let _ = self.api.send(Box::new(move |client| {
            if let Err(e) = work(client) {
                let _ = tx.send(Event::Notice(format!("Couldn’t {what} on MangaDex: {}", err(e))));
                ctx.request_repaint();
            }
        }));
    }

    pub fn list_manga(
        &self,
        request: u64,
        feed: Feed,
        query: String,
        offset: u32,
        filters: Filters,
        search: Search,
    ) {
        self.send(&self.api, move |c| Event::MangaList {
            request,
            result: c.list_manga(feed, &query, offset, &filters, &search).map_err(err),
        });
    }

    pub fn baka_search(&self, request: u64, query: String, page: u32, search: BakaSearch, filters: Filters) {
        self.send(&self.api, move |c| Event::BakaList {
            request,
            result: c.baka_search(&query, page, &search, &filters).map_err(err),
        });
    }

    pub fn resolve_baka(&self, series: BakaSeries) {
        self.send(&self.api, move |c| Event::Resolved {
            baka_id: series.id,
            result: c.resolve_baka(&series).map_err(err),
        });
    }

    pub fn tags(&self) {
        self.send(&self.api, |c| Event::Tags(c.tags().map_err(err)));
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

    pub fn sign_in(&self, creds: Credentials) {
        self.send(&self.api, move |c| {
            let result = c
                .session
                .sign_in(&c.http, &creds)
                .and_then(|()| c.me())
                .map(Some)
                .map_err(err);
            Event::Account(result)
        });
    }

    pub fn sign_out(&self) {
        self.client.session.sign_out();
        let _ = self.events_tx.send(Event::Account(Ok(None)));
    }

    /// Pulls follows and reading statuses from MangaDex.
    pub fn sync(&self) {
        self.send(&self.api, |c| {
            let result = c.follows().and_then(|follows| {
                Ok(SyncResult {
                    follows,
                    statuses: c.reading_statuses()?,
                })
            });
            Event::Synced(result.map_err(err))
        });
    }

    pub fn read_markers(&self, manga_id: String) {
        self.send(&self.api, move |c| {
            let result = c.read_markers(&manga_id).map_err(err);
            Event::ReadMarkers { manga_id, result }
        });
    }

    pub fn push_read(&self, manga_id: String, read: Vec<String>, unread: Vec<String>) {
        self.write("update read chapters", move |c| {
            c.set_read_markers(&manga_id, &read, &unread)
        });
    }

    pub fn set_follow(&self, manga_id: String, follow: bool) {
        self.write(
            if follow {
                "follow the title"
            } else {
                "unfollow the title"
            },
            move |c| c.set_follow(&manga_id, follow),
        );
    }

    pub fn set_status(&self, manga_id: String, status: Option<String>) {
        self.write("update the reading status", move |c| {
            c.set_reading_status(&manga_id, status.as_deref())
        });
    }

    /// Downloads and decodes an image. `fallback` is tried when `url` fails.
    pub fn image(&self, url: String, fallback: Option<String>, kind: ImageKind, max_side: usize) {
        let cache = match kind {
            ImageKind::Cover | ImageKind::CoverBlur => self.cache_dir.as_ref().map(|d| d.join("covers")),
            ImageKind::Page { .. } => None,
        };
        let queue = if matches!(kind, ImageKind::Page { .. }) {
            &self.pages
        } else {
            &self.covers
        };
        let key = kind.key(&url);
        self.send(queue, move |c| {
            let result = fetch_image(c, &url, fallback.as_deref(), cache, kind, max_side);
            Event::Image { key, result }
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
) -> Result<Decoded, String> {
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

pub fn decode(bytes: &[u8], kind: ImageKind, max_side: usize) -> Result<Decoded, String> {
    let img = image::load_from_memory(bytes).map_err(|e| format!("Couldn't read the image: {e}"))?;
    if kind == ImageKind::CoverBlur {
        return Ok(Decoded {
            image: to_color_image(&blur(&img)),
            blurred: None,
        });
    }
    let img = match kind {
        ImageKind::Page { crop: true } => crop_borders(img),
        _ => img,
    };
    let (w, h) = (img.width() as f32, img.height() as f32);
    // Covers never draw wider than ~400 physical pixels; pages are capped at a readable 2000 px
    // width and at what the GPU can hold in one texture.
    let max_w = if kind == ImageKind::Cover { 400.0 } else { 2000.0 };
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
    let blurred = matches!(kind, ImageKind::Page { .. }).then(|| to_color_image(&blur(&img)));
    Ok(Decoded {
        image: to_color_image(&img),
        blurred,
    })
}

fn to_color_image(img: &image::DynamicImage) -> ColorImage {
    let rgba = img.to_rgba8();
    ColorImage::from_rgba_unmultiplied([rgba.width() as usize, rgba.height() as usize], rgba.as_raw())
}

/// Width of the image inside a blurred copy, and the transparent margin around it.
pub const BLUR_INNER: u32 = 48;
pub const BLUR_PAD: u32 = 12;

/// A small, heavily blurred copy with a transparent margin, so the image's edges soften into
/// whatever is behind it the way real frosted glass would. Drawn stretched with linear
/// filtering it reads as frosted glass.
fn blur(img: &image::DynamicImage) -> image::DynamicImage {
    let w = BLUR_INNER;
    let h = ((img.height() as f32 / img.width().max(1) as f32) * w as f32)
        .round()
        .clamp(8.0, 400.0) as u32;
    let small = img
        .resize_exact(w, h, image::imageops::FilterType::Triangle)
        .to_rgba8();
    let mut canvas =
        image::RgbaImage::from_pixel(w + BLUR_PAD * 2, h + BLUR_PAD * 2, image::Rgba([0, 0, 0, 0]));
    image::imageops::overlay(&mut canvas, &small, BLUR_PAD as i64, BLUR_PAD as i64);
    image::DynamicImage::ImageRgba8(image::imageops::blur(&canvas, 3.0))
}

/// Where a blurred copy should be drawn so its inner image lines up with `image_rect`.
pub fn blur_rect(blurred_size: eframe::egui::Vec2, image_rect: eframe::egui::Rect) -> eframe::egui::Rect {
    let inner_h = (blurred_size.y - (BLUR_PAD * 2) as f32).max(1.0);
    let pad_x = image_rect.width() * BLUR_PAD as f32 / BLUR_INNER as f32;
    let pad_y = image_rect.height() * BLUR_PAD as f32 / inner_h;
    image_rect.expand2(eframe::egui::vec2(pad_x, pad_y))
}

/// Trims uniform margins (white or black scan borders) around a page. Leaves the page alone if
/// trimming would remove more than 40 % of either side, which means the "border" is content.
pub fn crop_borders(img: image::DynamicImage) -> image::DynamicImage {
    let rgb = img.to_luma8();
    let (w, h) = rgb.dimensions();
    if w < 32 || h < 32 {
        return img;
    }
    let corners = [
        rgb.get_pixel(0, 0)[0],
        rgb.get_pixel(w - 1, 0)[0],
        rgb.get_pixel(0, h - 1)[0],
        rgb.get_pixel(w - 1, h - 1)[0],
    ];
    let bg = corners.iter().map(|&c| c as u32).sum::<u32>() / 4;
    let is_bg = |v: u8| (v as i32 - bg as i32).abs() < 28;
    // A line counts as blank when fewer than 0.5 % of its pixels differ from the background.
    let row_blank = |y: u32| (0..w).filter(|&x| !is_bg(rgb.get_pixel(x, y)[0])).count() * 200 < w as usize;
    let col_blank = |x: u32| (0..h).filter(|&y| !is_bg(rgb.get_pixel(x, y)[0])).count() * 200 < h as usize;
    let top = (0..h).find(|&y| !row_blank(y)).unwrap_or(0);
    let bottom = (0..h).rev().find(|&y| !row_blank(y)).unwrap_or(h - 1);
    let left = (0..w).find(|&x| !col_blank(x)).unwrap_or(0);
    let right = (0..w).rev().find(|&x| !col_blank(x)).unwrap_or(w - 1);
    if bottom <= top || right <= left {
        return img;
    }
    let (cw, ch) = (right - left + 1, bottom - top + 1);
    if cw * 10 < w * 6 || ch * 10 < h * 6 || (cw == w && ch == h) {
        return img;
    }
    // Keep a hairline of margin so art doesn't touch the edge.
    let pad = 4;
    let x = left.saturating_sub(pad);
    let y = top.saturating_sub(pad);
    img.crop_imm(x, y, (cw + pad * 2).min(w - x), (ch + pad * 2).min(h - y))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn png(img: image::RgbaImage) -> Vec<u8> {
        let mut out = Vec::new();
        image::DynamicImage::ImageRgba8(img)
            .write_to(&mut std::io::Cursor::new(&mut out), image::ImageFormat::Png)
            .unwrap();
        out
    }

    #[test]
    fn decode_shrinks_tall_pages_to_texture_limit() {
        let bytes = png(image::RgbaImage::from_pixel(
            100,
            3000,
            image::Rgba([10, 20, 30, 255]),
        ));
        let out = decode(&bytes, ImageKind::Page { crop: false }, 1000).unwrap();
        assert_eq!(out.image.size, [33, 1000]);
        assert!(out.blurred.is_some());
        let cover = decode(&bytes, ImageKind::Cover, 16384).unwrap();
        assert_eq!(cover.image.size, [100, 3000]);
        assert!(cover.blurred.is_none());
    }

    #[test]
    fn crops_white_margins() {
        let mut img = image::RgbaImage::from_pixel(200, 300, image::Rgba([255, 255, 255, 255]));
        for x in 40..160 {
            for y in 50..250 {
                img.put_pixel(x, y, image::Rgba([0, 0, 0, 255]));
            }
        }
        let out = crop_borders(image::DynamicImage::ImageRgba8(img));
        assert_eq!((out.width(), out.height()), (128, 208));
    }

    #[test]
    fn keeps_pages_without_margins() {
        let img = image::RgbaImage::from_fn(100, 100, |x, _| image::Rgba([(x * 2) as u8, 0, 0, 255]));
        let out = crop_borders(image::DynamicImage::ImageRgba8(img));
        assert_eq!((out.width(), out.height()), (100, 100));
    }
}
