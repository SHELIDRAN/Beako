//! Source-defined filters, modeled on Mangayomi's filter list.
//!
//! A source declares its filters as a [`FilterList`] of generic building blocks (headers,
//! selects, sorts, check boxes, tri-state tags, groups, text, and sliders). The app draws them
//! without knowing what they mean, and the source reads the chosen values back by key when it
//! searches. Chosen values are saved per source.

use eframe::egui::{self, CornerRadius, RichText, Ui, vec2};
use serde::{Deserialize, Serialize};

use crate::theme::{Palette, radius, space, text};
use crate::widgets::{self, ButtonStyle, Icon};

/// Ignored, required, or excluded.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum TagState {
    #[default]
    Any,
    Include,
    Exclude,
}

impl TagState {
    pub fn next(self) -> Self {
        match self {
            TagState::Any => TagState::Include,
            TagState::Include => TagState::Exclude,
            TagState::Exclude => TagState::Any,
        }
    }
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct Opt {
    pub label: String,
    pub value: String,
}

pub fn opt(label: &str, value: &str) -> Opt {
    Opt {
        label: label.to_owned(),
        value: value.to_owned(),
    }
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub enum Filter {
    Header {
        name: String,
    },
    Separator,
    Text {
        key: String,
        name: String,
        value: String,
    },
    Select {
        key: String,
        name: String,
        options: Vec<Opt>,
        selected: usize,
    },
    /// `selected: None` means the source's default order.
    Sort {
        key: String,
        name: String,
        options: Vec<Opt>,
        selected: Option<usize>,
        ascending: bool,
    },
    CheckBox {
        key: String,
        name: String,
        value: String,
        checked: bool,
    },
    TriState {
        key: String,
        name: String,
        value: String,
        state: TagState,
        count: Option<u32>,
    },
    /// Related check boxes or tri-states. Searchable groups show a search field and suggestions
    /// instead of every member, for lists with thousands of entries.
    Group {
        key: String,
        name: String,
        hint: Option<String>,
        filters: Vec<Filter>,
        searchable: bool,
    },
    Slider {
        key: String,
        name: String,
        min: u32,
        max: u32,
        step: u32,
        value: u32,
        format: SliderFormat,
    },
}

/// How a slider shows its value. The minimum always reads as "Any".
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum SliderFormat {
    Count,
    /// Plus sign after the number: "20+".
    AtLeast,
    AtMost,
    Stars,
}

#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
pub struct FilterList {
    pub filters: Vec<Filter>,
}

impl Filter {
    fn key(&self) -> Option<&str> {
        match self {
            Filter::Text { key, .. }
            | Filter::Select { key, .. }
            | Filter::Sort { key, .. }
            | Filter::CheckBox { key, .. }
            | Filter::TriState { key, .. }
            | Filter::Group { key, .. }
            | Filter::Slider { key, .. } => Some(key),
            Filter::Header { .. } | Filter::Separator => None,
        }
    }

    fn active(&self) -> usize {
        match self {
            Filter::Text { value, .. } => usize::from(!value.trim().is_empty()),
            Filter::Select { selected, .. } => usize::from(*selected != 0),
            Filter::Sort { selected, .. } => usize::from(selected.is_some()),
            Filter::CheckBox { checked, .. } => usize::from(*checked),
            Filter::TriState { state, .. } => usize::from(*state != TagState::Any),
            Filter::Group { filters, .. } => filters.iter().map(Filter::active).sum(),
            Filter::Slider { value, min, .. } => usize::from(value != min),
            Filter::Header { .. } | Filter::Separator => 0,
        }
    }

    fn reset(&mut self) {
        match self {
            Filter::Text { value, .. } => value.clear(),
            Filter::Select { selected, .. } => *selected = 0,
            Filter::Sort {
                selected, ascending, ..
            } => {
                *selected = None;
                *ascending = false;
            }
            Filter::CheckBox { checked, .. } => *checked = false,
            Filter::TriState { state, .. } => *state = TagState::Any,
            Filter::Group { filters, .. } => filters.iter_mut().for_each(Filter::reset),
            Filter::Slider { value, min, .. } => *value = *min,
            Filter::Header { .. } | Filter::Separator => {}
        }
    }

    /// Copies the chosen value from a saved copy of the same filter.
    fn restore(&mut self, saved: &Filter) {
        match (self, saved) {
            (Filter::Text { value, .. }, Filter::Text { value: v, .. }) => *value = v.clone(),
            (
                Filter::Select {
                    selected, options, ..
                },
                Filter::Select { selected: s, .. },
            ) => {
                if *s < options.len() {
                    *selected = *s;
                }
            }
            (
                Filter::Sort {
                    selected,
                    ascending,
                    options,
                    ..
                },
                Filter::Sort {
                    selected: s,
                    ascending: a,
                    ..
                },
            ) => {
                if s.is_none_or(|i| i < options.len()) {
                    *selected = *s;
                    *ascending = *a;
                }
            }
            (Filter::CheckBox { checked, .. }, Filter::CheckBox { checked: c, .. }) => *checked = *c,
            (Filter::TriState { state, .. }, Filter::TriState { state: s, .. }) => *state = *s,
            (Filter::Slider { value, min, max, .. }, Filter::Slider { value: v, .. }) => {
                *value = (*v).clamp(*min, *max)
            }
            (Filter::Group { filters, .. }, Filter::Group { filters: saved, .. }) => {
                for f in filters.iter_mut() {
                    let id = member_id(f);
                    if let Some(s) = saved.iter().find(|s| member_id(s) == id) {
                        f.restore(s);
                    }
                }
            }
            _ => {}
        }
    }
}

/// Group members are identified by their value, so saved tags survive reordering.
fn member_id(f: &Filter) -> Option<&str> {
    match f {
        Filter::CheckBox { value, .. } | Filter::TriState { value, .. } => Some(value),
        other => other.key(),
    }
}

impl FilterList {
    pub fn new(filters: Vec<Filter>) -> Self {
        Self { filters }
    }

    pub fn active_count(&self) -> usize {
        self.filters.iter().map(Filter::active).sum()
    }

    pub fn reset(&mut self) {
        self.filters.iter_mut().for_each(Filter::reset);
    }

    /// Applies saved choices to a freshly declared list.
    pub fn restore(&mut self, saved: &FilterList) {
        for f in &mut self.filters {
            if let Some(key) = f.key().map(str::to_owned)
                && let Some(s) = saved.filters.iter().find(|s| s.key() == Some(key.as_str()))
            {
                f.restore(s);
            }
        }
    }

    /// Only the chosen values, for saving.
    pub fn chosen(&self) -> FilterList {
        FilterList {
            filters: self.filters.iter().filter(|f| f.active() > 0).cloned().collect(),
        }
    }

    fn find(&self, key: &str) -> Option<&Filter> {
        self.filters.iter().find(|f| f.key() == Some(key))
    }

    /// The selected option's value, unless the first ("any") option is selected.
    pub fn select(&self, key: &str) -> Option<&str> {
        match self.find(key) {
            Some(Filter::Select {
                options, selected, ..
            }) if *selected > 0 => options.get(*selected).map(|o| o.value.as_str()),
            _ => None,
        }
    }

    pub fn sort(&self, key: &str) -> Option<(&str, bool)> {
        match self.find(key) {
            Some(Filter::Sort {
                options,
                selected: Some(i),
                ascending,
                ..
            }) => options.get(*i).map(|o| (o.value.as_str(), *ascending)),
            _ => None,
        }
    }

    /// Values of checked boxes in a group (or a single check box).
    pub fn checked(&self, key: &str) -> Vec<&str> {
        match self.find(key) {
            Some(Filter::Group { filters, .. }) => filters
                .iter()
                .filter_map(|f| match f {
                    Filter::CheckBox {
                        value, checked: true, ..
                    } => Some(value.as_str()),
                    _ => None,
                })
                .collect(),
            Some(Filter::CheckBox {
                value, checked: true, ..
            }) => vec![value.as_str()],
            _ => Vec::new(),
        }
    }

    /// Included and excluded values in a tri-state group.
    pub fn tri(&self, key: &str) -> (Vec<&str>, Vec<&str>) {
        let mut inc = Vec::new();
        let mut exc = Vec::new();
        if let Some(Filter::Group { filters, .. }) = self.find(key) {
            for f in filters {
                if let Filter::TriState { value, state, .. } = f {
                    match state {
                        TagState::Include => inc.push(value.as_str()),
                        TagState::Exclude => exc.push(value.as_str()),
                        TagState::Any => {}
                    }
                }
            }
        }
        (inc, exc)
    }

    pub fn slider(&self, key: &str) -> Option<u32> {
        match self.find(key) {
            Some(Filter::Slider { value, min, .. }) if value != min => Some(*value),
            _ => None,
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Drawing

/// Draws every filter. Returns true when a value changed.
pub fn show(ui: &mut Ui, list: &mut FilterList, width: f32) -> bool {
    let before = list.clone();
    for (i, f) in list.filters.iter_mut().enumerate() {
        ui.push_id(i, |ui| show_filter(ui, f, width));
    }
    *list != before
}

fn heading(ui: &mut Ui, label: &str, hint: Option<&str>) {
    let p = Palette::of(ui.ctx());
    ui.add_space(space::L);
    ui.label(RichText::new(label).font(text::headline()).color(p.label));
    if let Some(h) = hint {
        ui.label(
            RichText::new(h)
                .font(text::subheadline())
                .color(p.secondary_label),
        );
    }
    ui.add_space(space::XS);
}

fn show_filter(ui: &mut Ui, f: &mut Filter, width: f32) {
    let p = Palette::of(ui.ctx());
    match f {
        Filter::Header { name } => heading(ui, name, None),
        Filter::Separator => {
            ui.add_space(space::M);
            let r = ui.available_rect_before_wrap();
            ui.painter()
                .hline(r.x_range(), r.top(), egui::Stroke::new(1.0, p.separator));
            ui.add_space(1.0);
        }
        Filter::Text { name, value, .. } => {
            heading(ui, name, None);
            ui.add(
                egui::TextEdit::singleline(value)
                    .desired_width(width)
                    .margin(egui::Margin::symmetric(10, 6)),
            );
        }
        Filter::Select {
            key,
            name,
            options,
            selected,
        } => {
            heading(ui, name, None);
            let current = options.get(*selected).map_or("", |o| o.label.as_str()).to_owned();
            egui::ComboBox::from_id_salt(key.as_str())
                .selected_text(current)
                .width(width)
                .height(320.0)
                .show_ui(ui, |ui| {
                    for (i, o) in options.iter().enumerate() {
                        ui.selectable_value(selected, i, o.label.as_str());
                    }
                });
        }
        Filter::Sort {
            key,
            name,
            options,
            selected,
            ascending,
        } => {
            heading(ui, name, None);
            ui.horizontal(|ui| {
                let current = selected
                    .and_then(|i| options.get(i))
                    .map_or("Default", |o| o.label.as_str())
                    .to_owned();
                egui::ComboBox::from_id_salt(key.as_str())
                    .selected_text(current)
                    .width(width - 48.0)
                    .show_ui(ui, |ui| {
                        ui.selectable_value(selected, None, "Default");
                        for (i, o) in options.iter().enumerate() {
                            ui.selectable_value(selected, Some(i), o.label.as_str());
                        }
                    });
                if selected.is_some() {
                    let label = if *ascending { "Ascending" } else { "Descending" };
                    let r = widgets::button(ui, ButtonStyle::Bordered, Some(Icon::ArrowUpDown), "")
                        .on_hover_text(label);
                    r.widget_info(|| egui::WidgetInfo::labeled(egui::Role::Button, true, label));
                    if r.clicked() {
                        *ascending = !*ascending;
                    }
                }
            });
        }
        Filter::CheckBox { name, checked, .. } => {
            ui.add_space(space::S);
            ui.horizontal(|ui| {
                ui.label(RichText::new(name.as_str()).font(text::body()).color(p.label));
                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    widgets::toggle(ui, checked, name);
                });
            });
        }
        Filter::TriState { name, state, .. } => {
            if widgets::chip(ui, name, *state).clicked() {
                *state = state.next();
            }
        }
        Filter::Slider {
            name,
            min,
            max,
            step,
            value,
            format,
            ..
        } => {
            heading(ui, name, None);
            ui.horizontal(|ui| {
                widgets::slider(ui, value, *min..=*max, *step, width - 80.0, name);
                let label = if *value == *min {
                    "Any".to_owned()
                } else {
                    match format {
                        SliderFormat::Count => value.to_string(),
                        SliderFormat::AtLeast => format!("{value}+"),
                        SliderFormat::AtMost => format!("≤ {value}"),
                        SliderFormat::Stars => format!("★ {value}+"),
                    }
                };
                ui.label(
                    RichText::new(label)
                        .font(text::callout())
                        .color(p.secondary_label),
                );
            });
        }
        Filter::Group {
            key,
            name,
            hint,
            filters,
            searchable,
        } => {
            heading(ui, name, hint.as_deref());
            if *searchable {
                searchable_group(ui, key, filters, width);
            } else {
                wrap(ui, |ui| {
                    for member in filters.iter_mut() {
                        group_member(ui, member);
                    }
                });
            }
        }
    }
}

fn wrap(ui: &mut Ui, add: impl FnOnce(&mut Ui)) {
    ui.horizontal_wrapped(|ui| {
        ui.spacing_mut().item_spacing = vec2(6.0, 6.0);
        add(ui);
    });
}

fn group_member(ui: &mut Ui, member: &mut Filter) {
    match member {
        Filter::CheckBox { name, checked, .. } => {
            if widgets::choice_chip(ui, name, *checked).clicked() {
                *checked = !*checked;
            }
        }
        Filter::TriState { name, state, .. } if widgets::chip(ui, name, *state).clicked() => {
            *state = state.next();
        }
        _ => {}
    }
}

/// Chosen members as chips, then a search field and matching suggestions.
fn searchable_group(ui: &mut Ui, key: &str, filters: &mut [Filter], width: f32) {
    let p = Palette::of(ui.ctx());
    let chosen: Vec<usize> = filters
        .iter()
        .enumerate()
        .filter(|(_, f)| f.active() > 0)
        .map(|(i, _)| i)
        .collect();
    if !chosen.is_empty() {
        wrap(ui, |ui| {
            for i in chosen {
                group_member(ui, &mut filters[i]);
            }
        });
        ui.add_space(space::S);
    }
    let id = egui::Id::unique(("filter-search", key));
    let mut query: String = ui.ctx().data(|d| d.get_temp(id)).unwrap_or_default();
    widgets::search_field(ui, &mut query, "Search", width);
    ui.ctx().data_mut(|d| d.insert_temp(id, query.clone()));
    ui.add_space(space::XS);
    let needle = query.trim().to_lowercase();
    let limit = if needle.is_empty() { 12 } else { 30 };
    let mut shown = 0;
    for f in filters.iter_mut() {
        if shown >= limit {
            break;
        }
        let (name, count) = match f {
            Filter::TriState {
                name,
                count,
                state: TagState::Any,
                ..
            } => (name.clone(), *count),
            Filter::CheckBox {
                name, checked: false, ..
            } => (name.clone(), None),
            _ => continue,
        };
        if !needle.is_empty() && !name.to_lowercase().contains(&needle) {
            continue;
        }
        shown += 1;
        let r = suggestion_row(ui, &name, count);
        match f {
            Filter::TriState { state, .. } if r.clicked() => *state = TagState::Include,
            Filter::TriState { state, .. } if r.secondary_clicked() => *state = TagState::Exclude,
            Filter::CheckBox { checked, .. } if r.clicked() => *checked = true,
            _ => {}
        }
    }
    if shown == 0 {
        ui.label(
            RichText::new("No matches")
                .font(text::subheadline())
                .color(p.secondary_label),
        );
    }
}

/// A row in a searchable group's suggestions. Right-click excludes.
fn suggestion_row(ui: &mut Ui, name: &str, count: Option<u32>) -> egui::Response {
    let p = Palette::of(ui.ctx());
    let (rect, response) = ui.allocate_exact_size(vec2(ui.available_width(), 30.0), egui::Sense::click());
    response.widget_info(|| egui::WidgetInfo::labeled(egui::Role::Button, true, format!("Include {name}")));
    if response.hovered() {
        ui.painter()
            .rect_filled(rect, CornerRadius::same(radius::CONTROL), p.fill);
    }
    let plus = egui::Rect::from_center_size(
        egui::pos2(rect.left() + 14.0, rect.center().y),
        egui::Vec2::splat(12.0),
    );
    widgets::paint_icon(ui.painter(), Icon::Plus, plus, p.accent);
    ui.painter().text(
        egui::pos2(rect.left() + 30.0, rect.center().y),
        egui::Align2::LEFT_CENTER,
        name,
        text::callout(),
        p.label,
    );
    if let Some(c) = count {
        ui.painter().text(
            egui::pos2(rect.right() - 8.0, rect.center().y),
            egui::Align2::RIGHT_CENTER,
            c.to_string(),
            text::subheadline(),
            p.secondary_label,
        );
    }
    widgets::focus_ring(ui, &response, rect, radius::CONTROL);
    response
        .on_hover_text("Click to include. Right-click to exclude.")
        .on_hover_cursor(egui::CursorIcon::PointingHand)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample() -> FilterList {
        FilterList::new(vec![
            Filter::Header {
                name: "Filters".into(),
            },
            Filter::Sort {
                key: "sort".into(),
                name: "Sort".into(),
                options: vec![opt("A", "a"), opt("B", "b")],
                selected: None,
                ascending: false,
            },
            Filter::Select {
                key: "year".into(),
                name: "Year".into(),
                options: vec![opt("Any", ""), opt("2020", "2020")],
                selected: 0,
            },
            Filter::Group {
                key: "genres".into(),
                name: "Genres".into(),
                hint: None,
                searchable: false,
                filters: vec![
                    Filter::TriState {
                        key: "g".into(),
                        name: "Action".into(),
                        value: "action".into(),
                        state: TagState::Any,
                        count: None,
                    },
                    Filter::TriState {
                        key: "g".into(),
                        name: "Comedy".into(),
                        value: "comedy".into(),
                        state: TagState::Any,
                        count: None,
                    },
                ],
            },
            Filter::Group {
                key: "status".into(),
                name: "Status".into(),
                hint: None,
                searchable: false,
                filters: vec![Filter::CheckBox {
                    key: "s".into(),
                    name: "Ongoing".into(),
                    value: "ongoing".into(),
                    checked: false,
                }],
            },
            Filter::Slider {
                key: "min".into(),
                name: "Min".into(),
                min: 0,
                max: 100,
                step: 10,
                value: 0,
                format: SliderFormat::AtLeast,
            },
        ])
    }

    #[test]
    fn reads_values_and_counts_active_filters() {
        let mut l = sample();
        assert_eq!(l.active_count(), 0);
        if let Filter::Group { filters, .. } = &mut l.filters[3] {
            if let Filter::TriState { state, .. } = &mut filters[0] {
                *state = TagState::Include;
            }
            if let Filter::TriState { state, .. } = &mut filters[1] {
                *state = TagState::Exclude;
            }
        }
        if let Filter::Sort { selected, .. } = &mut l.filters[1] {
            *selected = Some(1);
        }
        if let Filter::Select { selected, .. } = &mut l.filters[2] {
            *selected = 1;
        }
        if let Filter::Slider { value, .. } = &mut l.filters[5] {
            *value = 30;
        }
        assert_eq!(l.tri("genres"), (vec!["action"], vec!["comedy"]));
        assert_eq!(l.sort("sort"), Some(("b", false)));
        assert_eq!(l.select("year"), Some("2020"));
        assert_eq!(l.slider("min"), Some(30));
        assert!(l.checked("status").is_empty());
        assert_eq!(l.active_count(), 5);

        // Saved choices carry over to a freshly declared list.
        let saved: FilterList = serde_json::from_str(&serde_json::to_string(&l.chosen()).unwrap()).unwrap();
        let mut fresh = sample();
        fresh.restore(&saved);
        assert_eq!(fresh, l);
        fresh.reset();
        assert_eq!(fresh.active_count(), 0);
    }
}
