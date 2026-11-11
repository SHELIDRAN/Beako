//! The reader: pages fill the window on black, and the controls float above them on a
//! translucent layer that fades away while reading (HIG › Materials: glass belongs to the
//! floating controls, never to the content).

use std::collections::HashMap;

use eframe::egui::{
    self, Align2, Color32, CornerRadius, CursorIcon, Key, Rect, Response, Role, Sense, Stroke,
    StrokeKind, Ui, Vec2, ViewportCommand, WidgetInfo, pos2, vec2,
};

use crate::api::{Chapter, Manga};
use crate::app::{Action, App, Loadable};
use crate::images::{ImageState, texture_size};
use crate::jobs::ImageKind;
use crate::library::ReadingMode;
use crate::theme::{Weight, font, radius, space, text};
use crate::widgets::{self, Icon};

/// Controls hide after this long without pointer movement.
const CHROME_TIMEOUT: f64 = 2.5;
const READ_AHEAD: usize = 4;
const MAX_STRIP_WIDTH: f32 = 900.0;

/// Colors for the always-dark reading surface. White on the bar fill is 15.9:1;
/// the secondary text (#AEAEB2) on it is 8.0:1.
const BAR_FILL: Color32 = Color32::from_rgba_premultiplied(26, 26, 28, 222);
const BAR_TEXT: Color32 = Color32::WHITE;
const BAR_SECONDARY: Color32 = Color32::from_rgb(0xAE, 0xAE, 0xB2);
const BAR_ACCENT: Color32 = Color32::from_rgb(0xFF, 0x5C, 0x83);

pub struct Reader {
    pub manga: Manga,
    chapters: Vec<Chapter>,
    index: usize,
    pages: Loadable<(Vec<String>, Vec<String>)>,
    page: usize,
    start_page: usize,
    mode: ReadingMode,
    chrome_until: f64,
    pin_chrome: bool,
    scroll_to: Option<usize>,
    fullscreen: bool,
    /// Known heights of pages in vertical mode, as a fraction of their width.
    aspect: HashMap<usize, f32>,
    marked_read: bool,
}

impl Reader {
    pub fn new(
        manga: Manga,
        chapters: Vec<Chapter>,
        index: usize,
        page: usize,
        mode: ReadingMode,
    ) -> Self {
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
            scroll_to: None,
            fullscreen: false,
            aspect: HashMap::new(),
            marked_read: false,
        }
    }

    fn chapter(&self) -> &Chapter {
        &self.chapters[self.index]
    }

    pub fn pages_loaded(
        &mut self,
        chapter_id: &str,
        result: Result<(Vec<String>, Vec<String>), String>,
    ) {
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
        let first = indices
            .iter()
            .copied()
            .find(|&i| differs(&self.chapters[i]))?;
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
        app.jobs.page_urls(
            self.chapter().id.clone(),
            app.library.settings.filters.data_saver,
        );
        app.library
            .set_position(&self.manga.id, &self.chapters[index], 0);
    }

    fn go_to_page(&mut self, page: usize) {
        let n = self.page_count();
        if n > 0 {
            self.page = page.min(n - 1);
            self.scroll_to = Some(self.page);
        }
    }

    fn step(&mut self, forward: bool, app: &mut App) {
        let n = self.page_count();
        if forward {
            if self.page + 1 < n {
                self.go_to_page(self.page + 1);
            } else if let Some(next) = self.neighbour(true) {
                self.open_chapter(next, app);
            } else if n > 0 {
                app.actions.push(Action::Notice(
                    "You’re all caught up. This is the latest chapter.".into(),
                ));
            }
        } else if self.page > 0 {
            self.go_to_page(self.page - 1);
        } else if let Some(prev) = self.neighbour(false) {
            self.open_chapter(prev, app);
            self.start_page = usize::MAX; // Land on the last page of the previous chapter.
        }
    }

    fn handle_keys(&mut self, ui: &Ui, app: &mut App) {
        let rtl = self.mode == ReadingMode::RightToLeft;
        let vertical = self.mode == ReadingMode::Vertical;
        let mut forward = None;
        ui.input_mut(|i| {
            if i.key_pressed(Key::Escape) {
                if self.fullscreen {
                    self.fullscreen = false;
                    ui.ctx()
                        .send_viewport_cmd(ViewportCommand::Fullscreen(false));
                } else {
                    app.actions.push(Action::CloseReader);
                }
            }
            if i.key_pressed(Key::F11) || i.consume_key(egui::Modifiers::NONE, Key::F) {
                self.fullscreen = !self.fullscreen;
                ui.ctx()
                    .send_viewport_cmd(ViewportCommand::Fullscreen(self.fullscreen));
            }
            if !vertical {
                if i.key_pressed(Key::ArrowRight) {
                    forward = Some(!rtl);
                }
                if i.key_pressed(Key::ArrowLeft) {
                    forward = Some(rtl);
                }
                if i.key_pressed(Key::Space)
                    || i.key_pressed(Key::PageDown)
                    || i.key_pressed(Key::ArrowDown)
                {
                    forward = Some(true);
                }
                if i.key_pressed(Key::PageUp) || i.key_pressed(Key::ArrowUp) {
                    forward = Some(false);
                }
                if i.key_pressed(Key::Home) {
                    self.page = 0;
                }
                if i.key_pressed(Key::End) {
                    self.page = self.page_count().saturating_sub(1);
                }
            }
            if i.consume_key(egui::Modifiers::NONE, Key::OpenBracket)
                && let Some(prev) = self.neighbour(false)
            {
                self.open_chapter(prev, app);
            }
            if i.consume_key(egui::Modifiers::NONE, Key::CloseBracket)
                && let Some(next) = self.neighbour(true)
            {
                self.open_chapter(next, app);
            }
        });
        if let Some(f) = forward {
            self.step(f, app);
        }
    }

    pub fn show(&mut self, ui: &mut Ui, app: &mut App) {
        let ctx = ui.ctx().clone();
        let now = ctx.input(|i| i.time);
        let rect = ui.max_rect();
        ui.painter()
            .rect_filled(rect, CornerRadius::ZERO, Color32::BLACK);

        if self.chrome_until == f64::MAX {
            self.chrome_until = now + CHROME_TIMEOUT + 1.0;
        }
        if ctx.input(|i| i.pointer.delta() != Vec2::ZERO) {
            self.chrome_until = now + CHROME_TIMEOUT;
        }
        self.handle_keys(ui, app);
        let previous_page = self.page;

        match self.pages.clone() {
            Loadable::Ready((urls, fallbacks)) => match self.mode {
                ReadingMode::Vertical => self.strip(ui, app, rect, &urls, &fallbacks),
                _ => self.paged(ui, app, rect, &urls, &fallbacks),
            },
            Loadable::Failed(e) => {
                self.chrome_until = now + CHROME_TIMEOUT;
                let mut child =
                    ui.new_child(egui::UiBuilder::new().max_rect(rect.shrink(space::XXL)));
                let retry = message(
                    &mut child,
                    "Couldn’t Open This Chapter",
                    &e,
                    Some("Try Again"),
                );
                if retry {
                    self.pages = Loadable::Loading;
                    app.jobs.page_urls(
                        self.chapter().id.clone(),
                        app.library.settings.filters.data_saver,
                    );
                }
            }
            _ => {
                let r = Rect::from_center_size(rect.center(), Vec2::splat(30.0));
                widgets::paint_spinner(ui, r, BAR_SECONDARY);
            }
        }

        let n = self.page_count();
        if n > 0 {
            if self.page != previous_page || !self.marked_read {
                app.library.set_position(
                    &self.manga.id,
                    &self.chapters[self.index].clone(),
                    self.page,
                );
            }
            if self.page + 1 >= n && !self.marked_read {
                self.marked_read = true;
                app.library
                    .set_read(&self.manga.id, &self.chapter().id.clone(), true);
            }
        }

        let visible =
            now < self.chrome_until || self.pin_chrome || !matches!(self.pages, Loadable::Ready(_));
        let t = ctx.animate_bool_with_time(egui::Id::unique("reader-chrome"), visible, 0.18);
        if visible {
            ctx.request_repaint_after(std::time::Duration::from_secs_f64(
                (self.chrome_until - now).clamp(0.05, 3.0),
            ));
        } else {
            ctx.set_cursor_icon(CursorIcon::None);
        }
        if t > 0.0 {
            self.chrome(ui, app, rect, t);
        }
    }

    fn paged(
        &mut self,
        ui: &mut Ui,
        app: &mut App,
        rect: Rect,
        urls: &[String],
        fallbacks: &[String],
    ) {
        let ctx = ui.ctx().clone();
        let page = self.page.min(urls.len() - 1);
        for i in page.saturating_sub(1)..(page + READ_AHEAD).min(urls.len()) {
            app.images.prefetch(
                &app.jobs,
                &ctx,
                &urls[i],
                Some(&fallbacks[i]),
                ImageKind::Page,
            );
        }
        let state = app.images.get_with_fallback(
            &app.jobs,
            &ctx,
            &urls[page],
            Some(&fallbacks[page]),
            ImageKind::Page,
        );
        let mut retry = false;
        match state {
            ImageState::Ready(tex) => {
                let size = texture_size(tex);
                let scale = (rect.width() / size.x).min(rect.height() / size.y);
                let target = Rect::from_center_size(rect.center(), size * scale);
                egui::Image::new(egui::load::SizedTexture::from_handle(tex))
                    .alt_text(format!("Page {} of {}", page + 1, urls.len()))
                    .paint_at(ui, target);
            }
            ImageState::Loading => {
                widgets::paint_spinner(
                    ui,
                    Rect::from_center_size(rect.center(), Vec2::splat(30.0)),
                    BAR_SECONDARY,
                );
            }
            ImageState::Failed(e) => {
                let e = e.clone();
                let mut child =
                    ui.new_child(egui::UiBuilder::new().max_rect(rect.shrink(space::XXL)));
                retry = message(&mut child, "This Page Didn’t Load", &e, Some("Try Again"));
            }
        }
        if retry {
            app.images.retry(&app.jobs, &ctx, &urls[page]);
        }

        // Tap zones: the outer thirds turn pages, the middle shows or hides the controls.
        let response = ui.interact(rect, ui.scope_id().with("page-zones"), Sense::click());
        if response.clicked()
            && let Some(pos) = response.interact_pointer_pos()
        {
            let x = (pos.x - rect.left()) / rect.width();
            let rtl = self.mode == ReadingMode::RightToLeft;
            if x < 0.3 {
                self.step(rtl, app);
            } else if x > 0.7 {
                self.step(!rtl, app);
            } else {
                self.toggle_chrome(&ctx);
            }
        }
        // Scrolling the wheel also turns pages.
        let scroll = ui.input(|i| i.smooth_scroll_delta.y);
        let acc_id = egui::Id::unique("wheel-accumulator");
        let mut acc: f32 = ctx.data(|d| d.get_temp(acc_id)).unwrap_or(0.0);
        if response.hovered() && scroll != 0.0 {
            acc += scroll;
            if acc.abs() > 120.0 {
                self.step(acc < 0.0, app);
                acc = 0.0;
            }
        }
        ctx.data_mut(|d| d.insert_temp(acc_id, acc));
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
    ) {
        let ctx = ui.ctx().clone();
        let width = rect.width().min(MAX_STRIP_WIDTH);
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
                    y = page_rect.bottom();
                    if page_rect.top() <= center && page_rect.bottom() > center {
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
                    let state = app.images.get_with_fallback(
                        &app.jobs,
                        &ctx,
                        url,
                        Some(&fallbacks[i]),
                        ImageKind::Page,
                    );
                    match state {
                        ImageState::Ready(tex) => {
                            let size = texture_size(tex);
                            self.aspect.insert(i, size.y / size.x);
                            egui::Image::new(egui::load::SizedTexture::from_handle(tex))
                                .alt_text(format!("Page {} of {}", i + 1, urls.len()))
                                .paint_at(ui, page_rect);
                        }
                        ImageState::Loading => {
                            let r = Rect::from_center_size(page_rect.center(), Vec2::splat(26.0));
                            widgets::paint_spinner(ui, r, BAR_SECONDARY);
                        }
                        ImageState::Failed(_) => {
                            let mut c = ui.new_child(
                                egui::UiBuilder::new().max_rect(page_rect.shrink(space::XL)),
                            );
                            if message(&mut c, "This Page Didn’t Load", "", Some("Try Again")) {
                                retry = Some(i);
                            }
                        }
                    }
                }
                let content = Rect::from_min_max(pos2(left, origin), pos2(left + width, y));
                let r = ui.allocate_rect(content, Sense::click());
                if r.clicked() {
                    toggle = true;
                }
                // The end of the chapter leads straight into the next one.
                let end = Rect::from_min_size(pos2(left, y), vec2(width, 160.0));
                let mut c = ui.new_child(egui::UiBuilder::new().max_rect(end));
                c.vertical_centered(|ui| {
                    ui.add_space(space::XL);
                    if has_next {
                        next_chapter =
                            dark_button(ui, Some(Icon::ChevronRight), "Next Chapter").clicked();
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
            app.images.retry(&app.jobs, &ctx, &urls[i]);
        }
        if toggle {
            self.toggle_chrome(&ctx);
        }
        self.page = centered_page;
        for i in self.page..(self.page + READ_AHEAD).min(urls.len()) {
            app.images.prefetch(
                &app.jobs,
                &ctx,
                &urls[i],
                Some(&fallbacks[i]),
                ImageKind::Page,
            );
        }
        if next_chapter && let Some(next) = self.neighbour(true) {
            self.open_chapter(next, app);
        }
    }

    fn chrome(&mut self, ui: &mut Ui, app: &mut App, rect: Rect, t: f32) {
        let n = self.page_count();
        let fade = |c: Color32| c.gamma_multiply(t);
        let mut ui = ui.new_child(egui::UiBuilder::new().max_rect(rect));
        if t < 1.0 {
            ui.multiply_opacity(t);
        }
        let hover = |r: &Response| r.hovered() || r.has_focus();

        // Top bar: back, title and chapter, reading options.
        let top = Rect::from_min_size(
            rect.min + vec2(space::L, space::M),
            vec2(rect.width() - space::L * 2.0, 48.0),
        );
        glass(&ui, top, t);
        let mut bar = ui.new_child(
            egui::UiBuilder::new()
                .max_rect(top.shrink2(vec2(space::S, 8.0)))
                .layout(egui::Layout::left_to_right(egui::Align::Center)),
        );
        let back = dark_icon_button(&mut bar, Icon::ChevronLeft, "Close Reader (Esc)");
        if back.clicked() {
            app.actions.push(Action::CloseReader);
        }
        let mut keep = hover(&back);
        bar.add_space(space::XS);
        let title_w = (top.width() - 360.0).max(120.0);
        let title = widgets::wrapped_text(
            &bar,
            &self.manga.title,
            text::headline(),
            BAR_TEXT,
            title_w,
            1,
        );
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
            let fs_label = if self.fullscreen {
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
            .on_hover_text(fs_label);
            if fs.clicked() {
                self.fullscreen = !self.fullscreen;
                bar.ctx()
                    .send_viewport_cmd(ViewportCommand::Fullscreen(self.fullscreen));
            }
            keep |= hover(&fs);
            let mode = self.mode;
            let menu = egui::containers::menu::MenuButton::new(
                egui::RichText::new(mode.label())
                    .font(font(13.0, Weight::Medium))
                    .color(BAR_TEXT),
            );
            let (r, _) = menu.ui(bar, |ui| {
                for m in ReadingMode::ALL {
                    if ui.selectable_label(self.mode == m, m.label()).clicked() {
                        self.mode = m;
                        self.scroll_to = Some(self.page);
                        ui.close();
                    }
                }
            });
            keep |= hover(&r) || r.context_menu_opened();
            if self.mode != mode {
                app.library.settings.reading_mode = self.mode;
                app.library.save();
            }
        });

        // Bottom bar: chapter skipping, the page scrubber, and the page count.
        if n > 0 {
            let w = (rect.width() - space::L * 2.0).min(720.0);
            let bottom = Rect::from_center_size(
                pos2(rect.center().x, rect.bottom() - space::M - 26.0),
                vec2(w, 52.0),
            );
            glass(&ui, bottom, t);
            let mut bar = ui.new_child(
                egui::UiBuilder::new()
                    .max_rect(bottom.shrink2(vec2(space::S, 10.0)))
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
            let label = format!("{} of {}", self.page + 1, n);
            let label_w = 76.0;
            let track_w = bar.available_width() - label_w - 40.0;
            let s = scrubber(&mut bar, &mut self.page, n, track_w, rtl);
            keep |= hover(&s) || s.dragged();
            if s.changed() {
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
            self.chrome_until = ui.input(|i| i.time) + CHROME_TIMEOUT;
        }
    }
}

/// The floating control material: a dark translucent fill, a hairline edge, and a soft shadow.
fn glass(ui: &Ui, rect: Rect, t: f32) {
    let shadow = egui::epaint::Shadow {
        offset: [0, 8],
        blur: 30,
        spread: 0,
        color: Color32::from_black_alpha((110.0 * t) as u8),
    };
    ui.painter()
        .add(shadow.as_shape(rect, CornerRadius::same(radius::CAPSULE)));
    ui.painter().rect(
        rect,
        CornerRadius::same(radius::CAPSULE),
        BAR_FILL.gamma_multiply(t),
        Stroke::new(1.0, Color32::from_white_alpha((26.0 * t) as u8)),
        StrokeKind::Inside,
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
    let galley =
        ui.painter()
            .layout_no_wrap(label.to_owned(), font(13.0, Weight::Medium), BAR_TEXT);
    let icon_w = if icon.is_some() { 22.0 } else { 0.0 };
    let (rect, response) =
        ui.allocate_exact_size(vec2(galley.size().x + icon_w + 28.0, 32.0), Sense::click());
    response.widget_info(|| WidgetInfo::labeled(Role::Button, true, label));
    let fill = Color32::from_white_alpha(if response.hovered() { 40 } else { 24 });
    ui.painter()
        .rect_filled(rect, CornerRadius::same(radius::CAPSULE), fill);
    let x = rect.center().x - (galley.size().x + icon_w) / 2.0;
    ui.painter().galley(
        pos2(x, rect.center().y - galley.size().y / 2.0),
        galley,
        BAR_TEXT,
    );
    if let Some(icon) = icon {
        let r = Rect::from_center_size(
            pos2(rect.right() - 22.0, rect.center().y),
            Vec2::splat(14.0),
        );
        widgets::paint_icon(ui.painter(), icon, r, BAR_TEXT);
    }
    widgets::focus_ring(ui, &response, rect, radius::CAPSULE);
    response.on_hover_cursor(CursorIcon::PointingHand)
}

/// A page scrubber: drag or click anywhere on the track. Arrow keys work when it has focus.
fn scrubber(ui: &mut Ui, page: &mut usize, count: usize, width: f32, rtl: bool) -> Response {
    let (rect, mut response) =
        ui.allocate_exact_size(vec2(width.max(60.0), 30.0), Sense::click_and_drag());
    response.widget_info(|| {
        WidgetInfo::labeled(Role::Slider, true, format!("Page {} of {count}", *page + 1))
    });
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
        let (l, r) = ui.input(|i| {
            (
                i.key_pressed(Key::ArrowLeft),
                i.key_pressed(Key::ArrowRight),
            )
        });
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
        ui.label(
            egui::RichText::new(title)
                .font(text::title2())
                .color(BAR_TEXT),
        );
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
