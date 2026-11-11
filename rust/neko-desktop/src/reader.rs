//! The reader: pages fill the window, and the controls float above them on frosted glass that
//! fades away while reading (HIG › Materials: glass belongs to the floating controls, never to
//! the content).
//!
//! Paged modes support fit options, two-page spreads, zoom (Ctrl + wheel, +/-, double-click)
//! and panning. Vertical mode is a continuous strip for webtoons.

use std::collections::HashMap;

use eframe::egui::{
    self, Align2, Color32, CornerRadius, CursorIcon, Key, Rect, Response, Role, Sense, Ui, Vec2,
    ViewportCommand, WidgetInfo, pos2, vec2,
};

use crate::api::{Chapter, Manga};
use crate::app::{Action, App, Loadable};
use crate::images::{ImageState, Images, texture_size};
use crate::jobs::{ImageKind, Jobs};
use crate::library::{Backdrop, Fit, PageLayout, ReaderSettings, ReadingMode};
use crate::theme::{Palette, Weight, font, radius, space, text};
use crate::widgets::{self, Icon};

/// Text on the reader's dark glass. White on the tinted bar is about 15:1; the secondary
/// gray (#AEAEB2) about 8:1.
const BAR_TINT: Color32 = Color32::from_rgba_premultiplied(22, 22, 24, 150);
const BAR_TEXT: Color32 = Color32::WHITE;
const BAR_SECONDARY: Color32 = Color32::from_rgb(0xAE, 0xAE, 0xB2);
const BAR_ACCENT: Color32 = Color32::from_rgb(0xFF, 0x5C, 0x83);
const MIN_ZOOM: f32 = 1.0;
const MAX_ZOOM: f32 = 5.0;

pub struct Reader {
    pub manga: Manga,
    chapters: Vec<Chapter>,
    index: usize,
    pages: Loadable<(Vec<String>, Vec<String>)>,
    /// The first page of what's on screen (the left page of a spread in left-to-right).
    page: usize,
    start_page: usize,
    mode: ReadingMode,
    chrome_until: f64,
    pin_chrome: bool,
    show_settings: bool,
    scroll_to: Option<usize>,
    fullscreen: bool,
    /// Known heights of pages in vertical mode, as a fraction of their width.
    aspect: HashMap<usize, f32>,
    marked_read: bool,
    zoom: f32,
    pan: Vec2,
    /// Wheel movement collected toward a page turn.
    wheel: f32,
    awake: bool,
    /// The blurred first visible page and where it was drawn, for the frosted controls.
    backdrop: Option<(egui::TextureHandle, Rect)>,
}

impl Reader {
    pub fn new(manga: Manga, chapters: Vec<Chapter>, index: usize, page: usize, mode: ReadingMode) -> Self {
        Self {
            manga,
            chapters,
            index,
            pages: Loadable::Loading,
            page: 0,
            start_page: page,
            mode,
            chrome_until: f64::MAX,
            pin_chrome: false,
            show_settings: false,
            scroll_to: None,
            fullscreen: false,
            aspect: HashMap::new(),
            marked_read: false,
            zoom: 1.0,
            pan: Vec2::ZERO,
            wheel: 0.0,
            awake: false,
            backdrop: None,
        }
    }

    fn chapter(&self) -> &Chapter {
        &self.chapters[self.index]
    }

    pub fn pages_loaded(&mut self, chapter_id: &str, result: Result<(Vec<String>, Vec<String>), String>) {
        if self.chapter().id != chapter_id {
            return;
        }
        self.pages = match result {
            Ok(urls) => {
                self.page = self.start_page.min(urls.0.len().saturating_sub(1));
                self.scroll_to = Some(self.page);
                Loadable::Ready(urls)
            }
            Err(e) => Loadable::Failed(e),
        };
    }

    fn page_count(&self) -> usize {
        self.pages.ready().map_or(0, |(p, _)| p.len())
    }

    /// The neighbouring chapter with a different number, preferring the same scanlation group.
    fn neighbour(&self, forward: bool) -> Option<usize> {
        let current = self.chapter();
        let indices: Vec<usize> = if forward {
            (self.index + 1..self.chapters.len()).collect()
        } else {
            (0..self.index).rev().collect()
        };
        let differs = |c: &Chapter| current.chapter.is_none() || c.chapter != current.chapter;
        let first = indices.iter().copied().find(|&i| differs(&self.chapters[i]))?;
        let number = self.chapters[first].chapter.clone();
        indices
            .iter()
            .copied()
            .filter(|&i| self.chapters[i].chapter == number && differs(&self.chapters[i]))
            .find(|&i| self.chapters[i].group == current.group)
            .or(Some(first))
    }

    fn open_chapter(&mut self, index: usize, app: &mut App) {
        self.index = index;
        self.pages = Loadable::Loading;
        self.page = 0;
        self.start_page = 0;
        self.aspect.clear();
        self.marked_read = false;
        self.reset_view();
        app.jobs
            .page_urls(self.chapter().id.clone(), app.library.settings.filters.data_saver);
        app.library.set_position(&self.manga.id, &self.chapters[index], 0);
    }

    fn reset_view(&mut self) {
        self.zoom = 1.0;
        self.pan = Vec2::ZERO;
        self.wheel = 0.0;
    }

    /// Pages shown together starting at `page`: one, or two in a spread.
    #[allow(clippy::too_many_arguments)]
    fn spread(
        &self,
        page: usize,
        two_up: bool,
        cover_alone: bool,
        images: &mut Images,
        jobs: &Jobs,
        ctx: &egui::Context,
        crop: bool,
    ) -> Vec<usize> {
        let n = self.page_count();
        if !two_up || page + 1 >= n || (cover_alone && page == 0) {
            return vec![page];
        }
        // Wide pages (already a two-page spread in the scan) stand alone.
        let wide = |i: usize, images: &mut Images| {
            let (urls, fallbacks) = self.pages.ready().expect("pages ready");
            match images.get_with_fallback(jobs, ctx, &urls[i], Some(&fallbacks[i]), ImageKind::Page { crop })
            {
                ImageState::Ready(l) => {
                    let s = texture_size(&l.texture);
                    s.x > s.y
                }
                _ => false,
            }
        };
        if wide(page, images) || wide(page + 1, images) {
            vec![page]
        } else {
            vec![page, page + 1]
        }
    }

    /// The first page of the spread before `page`.
    fn previous_start(&self, page: usize, two_up: bool, cover_alone: bool) -> usize {
        if page == 0 {
            return 0;
        }
        if !two_up {
            return page - 1;
        }
        if cover_alone && page <= 1 {
            return 0;
        }
        // Spreads start on odd pages after a lone cover, even pages otherwise.
        let parity = usize::from(cover_alone);
        let candidate = page.saturating_sub(2);
        if (candidate % 2 == parity) || candidate == 0 {
            candidate
        } else {
            page - 1
        }
    }

    fn step(
        &mut self,
        forward: bool,
        spread_len: usize,
        two_up: bool,
        settings: &ReaderSettings,
        app: &mut App,
    ) {
        let n = self.page_count();
        self.reset_view();
        if forward {
            if self.page + spread_len < n {
                self.page += spread_len;
                self.scroll_to = Some(self.page);
            } else if !settings.auto_next_chapter {
                app.actions.push(Action::Notice(
                    "That’s the last page. Press ] for the next chapter.".into(),
                ));
            } else if let Some(next) = self.neighbour(true) {
                self.open_chapter(next, app);
            } else if n > 0 {
                app.actions.push(Action::Notice(
                    "You’re all caught up. This is the latest chapter.".into(),
                ));
            }
        } else if self.page > 0 {
            self.page = self.previous_start(self.page, two_up, settings.cover_alone);
            self.scroll_to = Some(self.page);
        } else if let Some(prev) = self.neighbour(false) {
            self.open_chapter(prev, app);
            self.start_page = usize::MAX; // Land on the last page of the previous chapter.
        }
    }

    fn set_fullscreen(&mut self, ctx: &egui::Context, on: bool) {
        self.fullscreen = on;
        ctx.send_viewport_cmd(ViewportCommand::Fullscreen(on));
    }

    /// Keyboard commands. Returns a page turn to make: Some(true) forward, Some(false) back.
    fn handle_keys(&mut self, ui: &Ui, app: &mut App) -> Option<bool> {
        let rtl = self.mode == ReadingMode::RightToLeft;
        let vertical = self.mode == ReadingMode::Vertical;
        let mut turn = None;
        let ctx = ui.ctx().clone();
        let typing = ctx.egui_wants_keyboard_input();
        ui.input_mut(|i| {
            if i.key_pressed(Key::Escape) {
                if self.show_settings {
                    self.show_settings = false;
                } else if self.fullscreen {
                    self.set_fullscreen(&ctx, false);
                } else {
                    app.actions.push(Action::CloseReader);
                }
            }
            if typing {
                return;
            }
            if i.key_pressed(Key::F11) || i.consume_key(egui::Modifiers::NONE, Key::F) {
                let on = !self.fullscreen;
                self.set_fullscreen(&ctx, on);
            }
            if i.consume_key(egui::Modifiers::NONE, Key::S) {
                self.show_settings = !self.show_settings;
            }
            if !vertical {
                if i.key_pressed(Key::ArrowRight) {
                    turn = Some(!rtl);
                }
                if i.key_pressed(Key::ArrowLeft) {
                    turn = Some(rtl);
                }
                if i.key_pressed(Key::Space) || i.key_pressed(Key::PageDown) {
                    turn = Some(true);
                }
                if i.key_pressed(Key::PageUp) {
                    turn = Some(false);
                }
                if i.key_pressed(Key::ArrowDown) && self.zoom <= 1.0 {
                    turn = Some(true);
                }
                if i.key_pressed(Key::ArrowUp) && self.zoom <= 1.0 {
                    turn = Some(false);
                }
                if i.key_pressed(Key::Home) {
                    self.page = 0;
                }
                if i.key_pressed(Key::End) {
                    self.page = self.page_count().saturating_sub(1);
                }
                if i.key_pressed(Key::Plus) || i.key_pressed(Key::Equals) {
                    self.zoom = (self.zoom * 1.25).min(MAX_ZOOM);
                }
                if i.key_pressed(Key::Minus) {
                    self.zoom = (self.zoom / 1.25).max(MIN_ZOOM);
                }
                if i.key_pressed(Key::Num0) {
                    self.reset_view();
                }
            }
            if i.consume_key(egui::Modifiers::NONE, Key::OpenBracket) {
                turn = None;
                if let Some(prev) = self.neighbour(false) {
                    self.open_chapter(prev, app);
                }
            }
            if i.consume_key(egui::Modifiers::NONE, Key::CloseBracket) {
                turn = None;
                if let Some(next) = self.neighbour(true) {
                    self.open_chapter(next, app);
                }
            }
        });
        turn
    }

    pub fn show(&mut self, ui: &mut Ui, app: &mut App) {
        let ctx = ui.ctx().clone();
        let now = ctx.input(|i| i.time);
        let rect = ui.max_rect();
        let settings = app.library.settings.reader.clone();
        self.mode = app.library.settings.reading_mode;
        let background = match settings.backdrop {
            Backdrop::Black => Color32::BLACK,
            Backdrop::Gray => Color32::from_rgb(0x2B, 0x2B, 0x2E),
            Backdrop::White => Color32::from_rgb(0xF5, 0xF5, 0xF7),
        };
        ui.painter().rect_filled(rect, CornerRadius::ZERO, background);

        if settings.keep_awake != self.awake {
            self.awake = settings.keep_awake;
            crate::glass::keep_awake(self.awake);
        }
        if self.chrome_until == f64::MAX {
            self.chrome_until = now + settings.hide_controls_after.max(2) as f64 + 1.0;
        }
        if ctx.input(|i| i.pointer.delta() != Vec2::ZERO) {
            self.chrome_until = now + settings.hide_controls_after as f64;
        }
        let turn = self.handle_keys(ui, app);
        let previous_page = self.page;
        let crop = settings.crop_borders;

        // Two pages side by side in a wide window, when the layout asks for it.
        let two_up = self.mode != ReadingMode::Vertical
            && match settings.layout {
                PageLayout::Single => false,
                PageLayout::Double => true,
                PageLayout::Automatic => rect.width() > rect.height() * 1.15,
            };
        let spread = if self.page_count() > 0 {
            let jobs = &app.jobs;
            self.spread(
                self.page,
                two_up,
                settings.cover_alone,
                &mut app.images,
                jobs,
                &ctx,
                crop,
            )
        } else {
            vec![]
        };
        if let Some(forward) = turn {
            self.step(forward, spread.len().max(1), two_up, &settings, app);
        }

        self.backdrop = None;
        match self.pages.clone() {
            Loadable::Ready((urls, fallbacks)) => match self.mode {
                ReadingMode::Vertical => self.strip(ui, app, rect, &urls, &fallbacks, &settings),
                _ => {
                    let spread = self.spread(
                        self.page,
                        two_up,
                        settings.cover_alone,
                        &mut app.images,
                        &app.jobs,
                        &ctx,
                        crop,
                    );
                    self.paged(ui, app, rect, &urls, &fallbacks, &spread, two_up, &settings);
                }
            },
            Loadable::Failed(e) => {
                self.chrome_until = now + 3.0;
                let mut child = ui.new_child(egui::UiBuilder::new().max_rect(rect.shrink(space::XXL)));
                let retry = message(&mut child, "Couldn’t Open This Chapter", &e, Some("Try Again"));
                if retry {
                    self.pages = Loadable::Loading;
                    app.jobs
                        .page_urls(self.chapter().id.clone(), app.library.settings.filters.data_saver);
                }
            }
            _ => {
                let r = Rect::from_center_size(rect.center(), Vec2::splat(30.0));
                widgets::paint_spinner(ui, r, BAR_SECONDARY);
            }
        }

        if settings.dim > 0 {
            let alpha = (settings.dim as f32 / 100.0 * 255.0) as u8;
            ui.painter()
                .rect_filled(rect, CornerRadius::ZERO, Color32::from_black_alpha(alpha));
        }

        let n = self.page_count();
        if n > 0 {
            if self.page != previous_page || !self.marked_read {
                app.library
                    .set_position(&self.manga.id, &self.chapters[self.index].clone(), self.page);
            }
            let last_visible = self.page + spread.len().max(1) - 1;
            if last_visible + 1 >= n && !self.marked_read {
                self.marked_read = true;
                let (manga_id, chapter_id) = (self.manga.id.clone(), self.chapter().id.clone());
                app.mark_read(&manga_id, &chapter_id, true);
            }
        }

        let never_hide = settings.hide_controls_after == 0;
        let visible = never_hide
            || now < self.chrome_until
            || self.pin_chrome
            || self.show_settings
            || !matches!(self.pages, Loadable::Ready(_));
        let t = ctx.animate_bool_with_time(egui::Id::unique("reader-chrome"), visible, 0.18);
        if visible && !never_hide {
            ctx.request_repaint_after(std::time::Duration::from_secs_f64(
                (self.chrome_until - now).clamp(0.05, 3.0),
            ));
        } else if !visible {
            ctx.set_cursor_icon(CursorIcon::None);
        }
        if t > 0.0 {
            self.chrome(ui, app, rect, t, spread.len().max(1), two_up, &settings);
        }
        if t < 1.0 && settings.always_show_page_number && n > 0 {
            page_badge(ui, rect, &self.page_label(spread.len()), 1.0 - t);
        }
        if self.show_settings {
            self.settings_panel(ui, app, rect);
        }
    }

    fn page_label(&self, spread_len: usize) -> String {
        let n = self.page_count();
        if spread_len > 1 {
            format!("{}–{} of {n}", self.page + 1, self.page + spread_len)
        } else {
            format!("{} of {n}", self.page + 1)
        }
    }

    #[allow(clippy::too_many_arguments)]
    fn paged(
        &mut self,
        ui: &mut Ui,
        app: &mut App,
        rect: Rect,
        urls: &[String],
        fallbacks: &[String],
        spread: &[usize],
        two_up: bool,
        settings: &ReaderSettings,
    ) {
        let ctx = ui.ctx().clone();
        let kind = ImageKind::Page {
            crop: settings.crop_borders,
        };
        let first = spread.first().copied().unwrap_or(0).min(urls.len() - 1);
        let ahead = settings.preload.max(1) as usize + spread.len();
        for i in first.saturating_sub(1)..(first + ahead).min(urls.len()) {
            app.images
                .prefetch(&app.jobs, &ctx, &urls[i], Some(&fallbacks[i]), kind);
        }

        // Gather sizes; draw nothing until every page in the spread is ready.
        let mut textures = Vec::new();
        let mut failed = None;
        for &i in spread {
            match app
                .images
                .get_with_fallback(&app.jobs, &ctx, &urls[i], Some(&fallbacks[i]), kind)
            {
                ImageState::Ready(l) => textures.push((l.texture.clone(), l.blurred.clone())),
                ImageState::Loading => {}
                ImageState::Failed(e) => failed = Some((i, e.clone())),
            }
        }

        // Order on screen: right-to-left puts the first page on the right.
        let rtl = self.mode == ReadingMode::RightToLeft;
        let mut response = ui.interact(rect, ui.scope_id().with("page-zones"), Sense::click_and_drag());
        if let Some((i, e)) = failed {
            let mut child = ui.new_child(egui::UiBuilder::new().max_rect(rect.shrink(space::XXL)));
            if message(&mut child, "This Page Didn’t Load", &e, Some("Try Again")) {
                app.images.retry(&app.jobs, &ctx, &urls[i], kind);
            }
        } else if textures.len() < spread.len() {
            widgets::paint_spinner(
                ui,
                Rect::from_center_size(rect.center(), Vec2::splat(30.0)),
                BAR_SECONDARY,
            );
        } else {
            let sizes: Vec<Vec2> = textures.iter().map(|(t, _)| texture_size(t)).collect();
            let ratio: f32 = sizes.iter().map(|s| s.x / s.y.max(1.0)).sum();
            let ppp = ctx.pixels_per_point();
            let base_h = match settings.fit {
                Fit::Screen => (rect.height()).min(rect.width() / ratio),
                Fit::Width => rect.width() / ratio,
                Fit::Height => rect.height(),
                Fit::Original => sizes.iter().map(|s| s.y).fold(0.0, f32::max) / ppp,
            };

            // Zoom toward the pointer with Ctrl + wheel; drag to pan when the page is larger
            // than the window.
            let (zoom_delta, hover) = ui.input(|i| (i.zoom_delta(), i.pointer.hover_pos()));
            if zoom_delta != 1.0 && response.hovered() {
                let old = self.zoom;
                self.zoom = (self.zoom * zoom_delta).clamp(MIN_ZOOM, MAX_ZOOM);
                if let Some(pos) = hover {
                    let from_center = pos - rect.center() - self.pan;
                    self.pan -= from_center * (self.zoom / old - 1.0);
                }
            }
            if response.double_clicked() {
                if self.zoom > 1.0 {
                    self.reset_view();
                } else if let Some(pos) = response.interact_pointer_pos() {
                    self.zoom = 2.0;
                    self.pan = -(pos - rect.center()) * 1.0;
                }
            }
            let h = base_h * self.zoom;
            let size = vec2(h * ratio, h);
            let overflow = (size - rect.size()).max(Vec2::ZERO) / 2.0;
            if response.dragged() && (overflow.x > 0.0 || overflow.y > 0.0) {
                self.pan += response.drag_delta();
                ui.ctx().set_cursor_icon(CursorIcon::Grabbing);
            }
            // The wheel scrolls tall pages, then turns the page at the edge.
            let scroll = ui.input(|i| {
                if i.modifiers.ctrl {
                    0.0
                } else {
                    i.smooth_scroll_delta.y
                }
            });
            let mut turn = None;
            if response.hovered() && scroll != 0.0 {
                let can_scroll = if scroll < 0.0 {
                    self.pan.y > -overflow.y + 0.5
                } else {
                    self.pan.y < overflow.y - 0.5
                };
                if overflow.y > 0.0 && can_scroll {
                    self.pan.y += scroll;
                    self.wheel = 0.0;
                } else if settings.wheel_turns_pages {
                    self.wheel += scroll;
                    if self.wheel.abs() > 120.0 {
                        turn = Some(self.wheel < 0.0);
                        self.wheel = 0.0;
                    }
                }
            }
            self.pan = self.pan.clamp(-overflow, overflow);
            // New pages start at the top when they're taller than the window.
            if self.zoom == 1.0 && overflow.y > 0.0 && self.pan == Vec2::ZERO {
                self.pan.y = overflow.y;
            }

            let mut x = rect.center().x - size.x / 2.0 + self.pan.x;
            let top = rect.center().y - size.y / 2.0 + self.pan.y;
            let order: Vec<usize> = if rtl {
                (0..textures.len()).rev().collect()
            } else {
                (0..textures.len()).collect()
            };
            for (pos, idx) in order.into_iter().enumerate() {
                let (tex, blurred) = &textures[idx];
                let s = sizes[idx];
                let w = h * s.x / s.y.max(1.0);
                let page_rect = Rect::from_min_size(pos2(x, top), vec2(w, h));
                egui::Image::new(egui::load::SizedTexture::from_handle(tex))
                    .alt_text(format!("Page {} of {}", spread[idx] + 1, urls.len()))
                    .paint_at(ui, page_rect);
                if pos == 0
                    && let Some(b) = blurred
                {
                    self.backdrop = Some((b.clone(), page_rect));
                }
                x += w;
            }
            if let Some(forward) = turn {
                self.step(forward, spread.len(), two_up, settings, app);
                return;
            }
        }

        // Click zones: the outer thirds turn pages, the middle shows or hides the controls.
        if response.clicked()
            && !response.double_clicked()
            && let Some(pos) = response.interact_pointer_pos()
        {
            let x = (pos.x - rect.left()) / rect.width();
            let mut back_on_left = !rtl;
            if settings.invert_tap_zones {
                back_on_left = !back_on_left;
            }
            if settings.tap_zones && self.zoom <= 1.0 && x < 0.3 {
                self.step(!back_on_left, spread.len(), two_up, settings, app);
            } else if settings.tap_zones && self.zoom <= 1.0 && x > 0.7 {
                self.step(back_on_left, spread.len(), two_up, settings, app);
            } else {
                self.toggle_chrome(&ctx);
            }
        }
        response = response.on_hover_cursor(if self.zoom > 1.0 {
            CursorIcon::Grab
        } else {
            CursorIcon::Default
        });
        let _ = response;
    }

    fn toggle_chrome(&mut self, ctx: &egui::Context) {
        let now = ctx.input(|i| i.time);
        if now < self.chrome_until || self.pin_chrome {
            self.chrome_until = now;
            self.pin_chrome = false;
        } else {
            self.pin_chrome = true;
        }
    }

    fn strip(
        &mut self,
        ui: &mut Ui,
        app: &mut App,
        rect: Rect,
        urls: &[String],
        fallbacks: &[String],
        settings: &ReaderSettings,
    ) {
        let ctx = ui.ctx().clone();
        let kind = ImageKind::Page {
            crop: settings.crop_borders,
        };
        let width = (rect.width() * settings.strip_width as f32 / 100.0)
            .max(240.0)
            .min(rect.width());
        let gap = settings.page_gap as f32;
        let scroll_to = self.scroll_to.take();
        let mut centered_page = self.page;
        let mut retry = None;
        let mut next_chapter = false;
        let mut toggle = false;
        let has_next = self.neighbour(true).is_some();
        let mut child = ui.new_child(egui::UiBuilder::new().max_rect(rect));
        egui::ScrollArea::vertical()
            .id_salt(("strip", &self.chapter().id))
            .auto_shrink([false, false])
            .show_viewport(&mut child, |ui, viewport| {
                let left = rect.center().x - width / 2.0;
                let mut y = ui.min_rect().top();
                let origin = y;
                let center = viewport.center().y + origin;
                for (i, url) in urls.iter().enumerate() {
                    let aspect = *self.aspect.get(&i).unwrap_or(&1.45);
                    let page_rect = Rect::from_min_size(pos2(left, y), vec2(width, width * aspect));
                    y = page_rect.bottom() + gap;
                    if page_rect.top() <= center && page_rect.bottom() + gap > center {
                        centered_page = i;
                    }
                    if scroll_to == Some(i) {
                        ui.scroll_to_rect(page_rect, Some(egui::Align::TOP));
                    }
                    let near = page_rect.bottom() >= origin + viewport.top() - 2000.0
                        && page_rect.top() <= origin + viewport.bottom() + 3000.0;
                    if !near {
                        continue;
                    }
                    match app
                        .images
                        .get_with_fallback(&app.jobs, &ctx, url, Some(&fallbacks[i]), kind)
                    {
                        ImageState::Ready(l) => {
                            let size = texture_size(&l.texture);
                            self.aspect.insert(i, size.y / size.x);
                            egui::Image::new(egui::load::SizedTexture::from_handle(&l.texture))
                                .alt_text(format!("Page {} of {}", i + 1, urls.len()))
                                .paint_at(ui, page_rect);
                        }
                        ImageState::Loading => {
                            let r = Rect::from_center_size(page_rect.center(), Vec2::splat(26.0));
                            widgets::paint_spinner(ui, r, BAR_SECONDARY);
                        }
                        ImageState::Failed(_) => {
                            let mut c =
                                ui.new_child(egui::UiBuilder::new().max_rect(page_rect.shrink(space::XL)));
                            if message(&mut c, "This Page Didn’t Load", "", Some("Try Again")) {
                                retry = Some(i);
                            }
                        }
                    }
                }
                let content = Rect::from_min_max(pos2(left, origin), pos2(left + width, y));
                if ui.allocate_rect(content, Sense::click()).clicked() {
                    toggle = true;
                }
                // The end of the chapter leads straight into the next one.
                let end = Rect::from_min_size(pos2(left, y), vec2(width, 160.0));
                let mut c = ui.new_child(egui::UiBuilder::new().max_rect(end));
                c.vertical_centered(|ui| {
                    ui.add_space(space::XL);
                    if has_next {
                        next_chapter = dark_button(ui, Some(Icon::ChevronRight), "Next Chapter").clicked();
                    } else {
                        ui.label(
                            egui::RichText::new("You’re all caught up")
                                .font(text::headline())
                                .color(BAR_SECONDARY),
                        );
                    }
                });
                ui.allocate_rect(end, Sense::hover());
            });
        if let Some(i) = retry {
            app.images.retry(&app.jobs, &ctx, &urls[i], kind);
        }
        if toggle {
            self.toggle_chrome(&ctx);
        }
        self.page = centered_page;
        for i in self.page..(self.page + settings.preload.max(1) as usize).min(urls.len()) {
            app.images
                .prefetch(&app.jobs, &ctx, &urls[i], Some(&fallbacks[i]), kind);
        }
        if next_chapter && let Some(next) = self.neighbour(true) {
            self.open_chapter(next, app);
        }
    }

    #[allow(clippy::too_many_arguments)]
    fn chrome(
        &mut self,
        ui: &mut Ui,
        app: &mut App,
        rect: Rect,
        t: f32,
        spread_len: usize,
        two_up: bool,
        settings: &ReaderSettings,
    ) {
        let n = self.page_count();
        let fade = |c: Color32| c.gamma_multiply(t);
        let mut ui = ui.new_child(egui::UiBuilder::new().max_rect(rect));
        if t < 1.0 {
            ui.multiply_opacity(t);
        }
        let hover = |r: &Response| r.hovered() || r.has_focus();
        let backdrop_owned = self.backdrop.clone();
        let backdrop = backdrop_owned.as_ref().map(|(tex, r)| (tex, *r));

        // Top bar: back, title and chapter, reader settings, full screen.
        let top = Rect::from_min_size(
            rect.min + vec2(space::L, space::M),
            vec2(rect.width() - space::L * 2.0, 52.0),
        );
        crate::glass::frosted(
            ui.painter(),
            top,
            CornerRadius::same(radius::CAPSULE),
            backdrop,
            BAR_TINT,
            t,
        );
        let mut bar = ui.new_child(
            egui::UiBuilder::new()
                .max_rect(top.shrink2(vec2(space::S, 10.0)))
                .layout(egui::Layout::left_to_right(egui::Align::Center)),
        );
        let back = dark_icon_button(&mut bar, Icon::ChevronLeft, "Close Reader (Esc)");
        if back.clicked() {
            app.actions.push(Action::CloseReader);
        }
        let mut keep = hover(&back);
        bar.add_space(space::XS);
        let title_w = (top.width() - 320.0).max(120.0);
        let title = widgets::wrapped_text(&bar, &self.manga.title, text::headline(), BAR_TEXT, title_w, 1);
        let chapter = widgets::wrapped_text(
            &bar,
            &self.chapter().display_name(),
            text::subheadline(),
            BAR_SECONDARY,
            title_w,
            1,
        );
        let (r, _) = bar.allocate_exact_size(vec2(title_w, 34.0), Sense::hover());
        bar.painter().galley(r.left_top(), title, fade(BAR_TEXT));
        bar.painter()
            .galley(r.left_top() + vec2(0.0, 18.0), chapter, fade(BAR_SECONDARY));
        bar.with_layout(egui::Layout::right_to_left(egui::Align::Center), |bar| {
            let label = if self.fullscreen {
                "Exit Full Screen (F)"
            } else {
                "Full Screen (F)"
            };
            let fs = dark_button(
                bar,
                None,
                if self.fullscreen {
                    "Exit Full Screen"
                } else {
                    "Full Screen"
                },
            )
            .on_hover_text(label);
            if fs.clicked() {
                let on = !self.fullscreen;
                self.set_fullscreen(bar.ctx(), on);
            }
            keep |= hover(&fs);
            let s = dark_icon_button(bar, Icon::Sliders, "Reader Settings (S)");
            if s.clicked() {
                self.show_settings = !self.show_settings;
            }
            keep |= hover(&s);
            if self.zoom > 1.0 {
                let z = dark_button(bar, None, &format!("{:.0}%", self.zoom * 100.0))
                    .on_hover_text("Reset Zoom (0)");
                if z.clicked() {
                    self.reset_view();
                }
                keep |= hover(&z);
            }
        });

        // Bottom bar: chapter skipping, the page scrubber, and the page count.
        if n > 0 {
            let w = (rect.width() - space::L * 2.0).min(760.0);
            let bottom = Rect::from_center_size(
                pos2(rect.center().x, rect.bottom() - space::M - 28.0),
                vec2(w, 56.0),
            );
            crate::glass::frosted(
                ui.painter(),
                bottom,
                CornerRadius::same(radius::CAPSULE),
                backdrop,
                BAR_TINT,
                t,
            );
            let mut bar = ui.new_child(
                egui::UiBuilder::new()
                    .max_rect(bottom.shrink2(vec2(space::S, 12.0)))
                    .layout(egui::Layout::left_to_right(egui::Align::Center)),
            );
            let rtl = self.mode == ReadingMode::RightToLeft;
            let prev = self.neighbour(false);
            let next = self.neighbour(true);
            let (left_target, right_target) = if rtl { (next, prev) } else { (prev, next) };
            let left_label = if rtl {
                "Next Chapter (])"
            } else {
                "Previous Chapter ([)"
            };
            let right_label = if rtl {
                "Previous Chapter ([)"
            } else {
                "Next Chapter (])"
            };
            let l = bar
                .add_enabled_ui(left_target.is_some(), |ui| {
                    dark_icon_button(ui, Icon::ChevronLeft, left_label)
                })
                .inner;
            keep |= hover(&l);
            if l.clicked()
                && let Some(i) = left_target
            {
                self.open_chapter(i, app);
            }
            let label = self.page_label(spread_len);
            let label_w = 90.0;
            let track_w = bar.available_width() - label_w - 40.0;
            let before = self.page;
            let s = scrubber(&mut bar, &mut self.page, n, track_w, rtl);
            keep |= hover(&s) || s.dragged();
            if s.changed() {
                if two_up && settings.cover_alone && self.page > 0 && self.page.is_multiple_of(2) {
                    self.page -= 1; // Keep spreads aligned after the cover.
                }
                if self.page != before {
                    self.reset_view();
                }
                self.scroll_to = Some(self.page);
            }
            let (lr, _) = bar.allocate_exact_size(vec2(label_w, 30.0), Sense::hover());
            bar.painter().text(
                lr.center(),
                Align2::CENTER_CENTER,
                label,
                font(13.0, Weight::Medium),
                fade(BAR_TEXT),
            );
            let r = bar
                .add_enabled_ui(right_target.is_some(), |ui| {
                    dark_icon_button(ui, Icon::ChevronRight, right_label)
                })
                .inner;
            keep |= hover(&r);
            if r.clicked()
                && let Some(i) = right_target
            {
                self.open_chapter(i, app);
            }
        }
        if keep {
            self.chrome_until = ui.input(|i| i.time) + settings.hide_controls_after.max(2) as f64;
        }
    }

    /// Reader options in a glass panel over the right side of the page.
    fn settings_panel(&mut self, ui: &mut Ui, app: &mut App, rect: Rect) {
        let p = Palette::of(ui.ctx());
        let panel = Rect::from_min_max(
            pos2(rect.right() - 420.0, rect.top() + 80.0),
            pos2(rect.right() - space::L, rect.bottom() - 88.0),
        );
        // Swallow clicks so they don't turn pages underneath.
        let _ = ui.interact(
            panel,
            ui.scope_id().with("settings-blocker"),
            Sense::click_and_drag(),
        );
        crate::glass::panel(ui.painter(), panel, CornerRadius::same(16), &p);
        let mut child = ui.new_child(egui::UiBuilder::new().max_rect(panel.shrink(space::L)));
        let mut mode = app.library.settings.reading_mode;
        let mut reader = app.library.settings.reader.clone();
        child.horizontal(|ui| {
            ui.label(
                egui::RichText::new("Reader Settings")
                    .font(text::title2())
                    .color(p.label),
            );
            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                if widgets::button(ui, widgets::ButtonStyle::Plain, None, "Done").clicked() {
                    self.show_settings = false;
                }
            });
        });
        child.add_space(space::S);
        egui::ScrollArea::vertical()
            .id_salt("reader-settings")
            .auto_shrink([false, false])
            .show(&mut child, |ui| {
                crate::settings_view::reader_settings_group(ui, &mut mode, &mut reader);
            });
        let s = &mut app.library.settings;
        if mode != s.reading_mode || reader != s.reader {
            if mode != s.reading_mode || reader.layout != s.reader.layout {
                self.scroll_to = Some(self.page);
                self.reset_view();
            }
            s.reading_mode = mode;
            s.reader = reader;
            app.library.save();
        }
    }
}

/// The page number shown in a corner while the controls are hidden.
fn page_badge(ui: &Ui, rect: Rect, label: &str, opacity: f32) {
    let galley = ui
        .painter()
        .layout_no_wrap(label.to_owned(), font(12.0, Weight::Medium), BAR_TEXT);
    let size = galley.size() + vec2(20.0, 10.0);
    let r = Rect::from_min_size(rect.right_bottom() - size - vec2(space::L, space::L), size);
    ui.painter().rect_filled(
        r,
        CornerRadius::same(radius::CAPSULE),
        Color32::from_black_alpha((150.0 * opacity) as u8),
    );
    ui.painter().galley(
        r.center() - galley.size() / 2.0,
        galley,
        BAR_TEXT.gamma_multiply(opacity),
    );
}

fn dark_icon_button(ui: &mut Ui, icon: Icon, label: &str) -> Response {
    let (rect, response) = ui.allocate_exact_size(Vec2::splat(32.0), Sense::click());
    response.widget_info(|| WidgetInfo::labeled(Role::Button, ui.is_enabled(), label));
    if response.hovered() && ui.is_enabled() {
        ui.painter()
            .circle_filled(rect.center(), 16.0, Color32::from_white_alpha(28));
    }
    let color = if ui.is_enabled() {
        BAR_TEXT
    } else {
        BAR_SECONDARY.gamma_multiply(0.5)
    };
    widgets::paint_icon(
        ui.painter(),
        icon,
        Rect::from_center_size(rect.center(), Vec2::splat(18.0)),
        color,
    );
    widgets::focus_ring(ui, &response, rect, 16);
    response.on_hover_text(label)
}

fn dark_button(ui: &mut Ui, icon: Option<Icon>, label: &str) -> Response {
    let galley = ui
        .painter()
        .layout_no_wrap(label.to_owned(), font(13.0, Weight::Medium), BAR_TEXT);
    let icon_w = if icon.is_some() { 22.0 } else { 0.0 };
    let (rect, response) =
        ui.allocate_exact_size(vec2(galley.size().x + icon_w + 28.0, 32.0), Sense::click());
    response.widget_info(|| WidgetInfo::labeled(Role::Button, true, label));
    let fill = Color32::from_white_alpha(if response.hovered() { 40 } else { 24 });
    ui.painter()
        .rect_filled(rect, CornerRadius::same(radius::CAPSULE), fill);
    let x = rect.center().x - (galley.size().x + icon_w) / 2.0;
    ui.painter()
        .galley(pos2(x, rect.center().y - galley.size().y / 2.0), galley, BAR_TEXT);
    if let Some(icon) = icon {
        let r = Rect::from_center_size(pos2(rect.right() - 22.0, rect.center().y), Vec2::splat(14.0));
        widgets::paint_icon(ui.painter(), icon, r, BAR_TEXT);
    }
    widgets::focus_ring(ui, &response, rect, radius::CAPSULE);
    response.on_hover_cursor(CursorIcon::PointingHand)
}

/// A page scrubber: drag or click anywhere on the track. Arrow keys work when it has focus.
fn scrubber(ui: &mut Ui, page: &mut usize, count: usize, width: f32, rtl: bool) -> Response {
    let (rect, mut response) = ui.allocate_exact_size(vec2(width.max(60.0), 30.0), Sense::click_and_drag());
    response
        .widget_info(|| WidgetInfo::labeled(Role::Slider, true, format!("Page {} of {count}", *page + 1)));
    let track = Rect::from_center_size(rect.center(), vec2(rect.width() - 16.0, 4.0));
    let frac_of = |p: usize| {
        if count <= 1 {
            1.0
        } else {
            p as f32 / (count - 1) as f32
        }
    };
    if let Some(pos) = response.interact_pointer_pos()
        && (response.dragged() || response.clicked())
    {
        let mut f = ((pos.x - track.left()) / track.width()).clamp(0.0, 1.0);
        if rtl {
            f = 1.0 - f;
        }
        let new = (f * (count - 1) as f32).round() as usize;
        if new != *page {
            *page = new;
            response.mark_changed();
        }
    }
    if response.has_focus() {
        let (l, r) = ui.input(|i| (i.key_pressed(Key::ArrowLeft), i.key_pressed(Key::ArrowRight)));
        let (back, fwd) = if rtl { (r, l) } else { (l, r) };
        if fwd && *page + 1 < count {
            *page += 1;
            response.mark_changed();
        }
        if back && *page > 0 {
            *page -= 1;
            response.mark_changed();
        }
    }
    let mut f = frac_of(*page);
    if rtl {
        f = 1.0 - f;
    }
    let knob_x = track.left() + track.width() * f;
    ui.painter()
        .rect_filled(track, CornerRadius::same(2), Color32::from_white_alpha(52));
    let filled = if rtl {
        Rect::from_min_max(pos2(knob_x, track.top()), track.right_bottom())
    } else {
        Rect::from_min_max(track.left_top(), pos2(knob_x, track.bottom()))
    };
    ui.painter()
        .rect_filled(filled, CornerRadius::same(2), BAR_ACCENT);
    let knob_r = if response.hovered() || response.dragged() {
        8.0
    } else {
        7.0
    };
    ui.painter()
        .circle_filled(pos2(knob_x, track.center().y), knob_r, Color32::WHITE);
    widgets::focus_ring(ui, &response, rect, radius::CONTROL);
    response
}

/// A message on the dark reading surface, with an optional action.
fn message(ui: &mut Ui, title: &str, detail: &str, action: Option<&str>) -> bool {
    let mut clicked = false;
    ui.vertical_centered(|ui| {
        ui.add_space((ui.available_height() * 0.35).max(0.0));
        ui.label(egui::RichText::new(title).font(text::title2()).color(BAR_TEXT));
        if !detail.is_empty() {
            ui.add_space(space::XS);
            ui.label(
                egui::RichText::new(detail)
                    .font(text::body())
                    .color(BAR_SECONDARY),
            );
        }
        if let Some(a) = action {
            ui.add_space(space::L);
            clicked = dark_button(ui, Some(Icon::Retry), a).clicked();
        }
    });
    clicked
}

#[cfg(test)]
mod tests {
    use super::*;

    fn reader(pages: usize) -> Reader {
        let chapter = Chapter {
            id: "c".into(),
            chapter: Some("1".into()),
            volume: None,
            title: None,
            language: "en".into(),
            pages: pages as u32,
            group: None,
            published: String::new(),
            external_url: None,
        };
        let mut r = Reader::new(Manga::default(), vec![chapter], 0, 0, ReadingMode::LeftToRight);
        r.pages = Loadable::Ready((vec![String::new(); pages], vec![String::new(); pages]));
        r
    }

    #[test]
    fn spreads_step_back_to_aligned_pages() {
        let r = reader(10);
        // With a lone cover, spreads are 1–2, 3–4, …
        assert_eq!(r.previous_start(3, true, true), 1);
        assert_eq!(r.previous_start(1, true, true), 0);
        // Without, spreads are 0–1, 2–3, …
        assert_eq!(r.previous_start(4, true, false), 2);
        // Single pages step one at a time.
        assert_eq!(r.previous_start(4, false, true), 3);
    }
}
