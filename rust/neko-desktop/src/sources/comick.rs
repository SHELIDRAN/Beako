//! The Comick source: Comick's charts, popular and latest lists, and search with its genres,
//! community tags, demographics, chapter and score ranges.
//!
//! Comick serves chapter lists and pages only behind a browser check, so opening a Comick title
//! finds it on MangaDex and reads it there.

use anyhow::Result;

use super::{Listing, Results, ShelfInfo, Source, SourceInfo};
use crate::api::{Client, Filters, Manga};
use crate::comick::Chart;
use crate::filters::{Filter, FilterList, SliderFormat, TagState, opt};

const PAGE_SIZE: u32 = 30;
const PERIODS: &[(u32, &str)] = &[(7, "7d"), (30, "1m"), (90, "3m")];
const SHELVES: &[ShelfInfo] = &[
    ShelfInfo {
        id: "followed-new",
        title: "Most Followed New Comics",
        periods: PERIODS,
    },
    ShelfInfo {
        id: "recent-popular",
        title: "Most Recent Popular",
        periods: PERIODS,
    },
    ShelfInfo {
        id: "trending",
        title: "Trending",
        periods: PERIODS,
    },
];

pub struct Comick;

fn check(key: &str, name: &str, value: &str) -> Filter {
    Filter::CheckBox {
        key: key.into(),
        name: name.into(),
        value: value.into(),
        checked: false,
    }
}

fn group(key: &str, name: &str, hint: Option<&str>, filters: Vec<Filter>, searchable: bool) -> Filter {
    Filter::Group {
        key: key.into(),
        name: name.into(),
        hint: hint.map(str::to_owned),
        filters,
        searchable,
    }
}

fn tri(key: &str, name: &str, value: &str, count: Option<u32>) -> Filter {
    Filter::TriState {
        key: key.into(),
        name: name.into(),
        value: value.into(),
        state: TagState::Any,
        count,
    }
}

impl Source for Comick {
    fn info(&self) -> SourceInfo {
        SourceInfo {
            id: "comick",
            name: "Comick",
            lang: "multi",
            description: "Charts, community tags, and detailed filters",
            base_url: "https://comick.dev",
            supports_latest: true,
            reads_from: Some("MangaDex"),
            color: [0x3B, 0x82, 0xF6],
        }
    }

    fn filter_list(&self, client: &Client) -> Result<FilterList> {
        let genres = client.comick_genres()?;
        let tags = client.comick_tags()?;
        let this_year = 1970 + (crate::library::now() / 31_556_952) as u32;
        let years = |first: &str| {
            let mut v = vec![opt(first, "")];
            v.extend(
                (1960..=this_year + 1)
                    .rev()
                    .map(|y| opt(&y.to_string(), &y.to_string())),
            );
            v
        };
        let genre_group = |group_key: &str, title: &str, hint: Option<&str>| {
            let members = genres
                .iter()
                .filter(|g| g.group == group_key)
                .map(|g| tri("genre", &g.name, &g.slug, None))
                .collect();
            group(&format!("genres-{group_key}"), title, hint, members, false)
        };

        Ok(FilterList::new(vec![
            Filter::Select {
                key: "sort".into(),
                name: "Sort By".into(),
                options: vec![
                    opt("Most Followed", "user_follow_count"),
                    opt("Most Viewed", "view"),
                    opt("Highest Rated", "rating"),
                    opt("Latest Chapter", "uploaded"),
                    opt("Newest", "created_at"),
                    opt("Best Match", ""),
                ],
                selected: 0,
            },
            group(
                "tags",
                "Tags",
                Some("Search, then click to include. Click a chosen tag again to exclude it."),
                tags.iter()
                    .map(|t| tri("tag", &t.name, &t.slug, Some(t.count)))
                    .collect(),
                true,
            ),
            group(
                "country",
                "Type",
                None,
                vec![
                    check("country", "Manga", "jp"),
                    check("country", "Manhwa", "kr"),
                    check("country", "Manhua", "cn"),
                ],
                false,
            ),
            group(
                "demographic",
                "Demographic",
                None,
                vec![
                    check("demographic", "Shounen", "1"),
                    check("demographic", "Shoujo", "2"),
                    check("demographic", "Seinen", "3"),
                    check("demographic", "Josei", "4"),
                    check("demographic", "None", "5"),
                ],
                false,
            ),
            group(
                "status",
                "Status",
                None,
                vec![
                    check("status", "Ongoing", "1"),
                    check("status", "Completed", "2"),
                    check("status", "Cancelled", "3"),
                    check("status", "Hiatus", "4"),
                ],
                false,
            ),
            group(
                "content_rating",
                "Content Rating",
                Some("Limited by the content settings in Settings."),
                vec![
                    check("content_rating", "Safe", "safe"),
                    check("content_rating", "Suggestive", "suggestive"),
                    check("content_rating", "Erotica", "erotica"),
                ],
                false,
            ),
            Filter::Select {
                key: "time".into(),
                name: "Added to Comick".into(),
                options: vec![
                    opt("Any Time", ""),
                    opt("Past Week", "7"),
                    opt("Past Month", "30"),
                    opt("Past 3 Months", "90"),
                    opt("Past Year", "365"),
                ],
                selected: 0,
            },
            Filter::Slider {
                key: "minimum".into(),
                name: "Minimum Chapters".into(),
                min: 0,
                max: 500,
                step: 10,
                value: 0,
                format: SliderFormat::AtLeast,
            },
            Filter::Slider {
                key: "maximum".into(),
                name: "Maximum Chapters".into(),
                min: 0,
                max: 500,
                step: 10,
                value: 0,
                format: SliderFormat::AtMost,
            },
            Filter::Slider {
                key: "minimum_rating".into(),
                name: "Minimum Score".into(),
                min: 0,
                max: 9,
                step: 1,
                value: 0,
                format: SliderFormat::Stars,
            },
            Filter::Select {
                key: "from".into(),
                name: "Published From".into(),
                options: years("Any Year"),
                selected: 0,
            },
            Filter::Select {
                key: "to".into(),
                name: "Published Until".into(),
                options: years("Any Year"),
                selected: 0,
            },
            genre_group(
                "genre",
                "Genres",
                Some("Click once to include, twice to exclude."),
            ),
            genre_group("theme", "Themes", None),
            genre_group("format", "Formats", None),
            genre_group("content", "Content", None),
        ]))
    }

    fn list(
        &self,
        client: &Client,
        listing: &Listing,
        page: u32,
        filters: &FilterList,
        prefs: &Filters,
    ) -> Result<Results> {
        let mut q: Vec<(&'static str, String)> = vec![
            ("limit", PAGE_SIZE.to_string()),
            ("page", page.max(1).to_string()),
        ];
        match listing {
            Listing::Popular => {
                q.push(("sort", "user_follow_count".into()));
                ratings(&mut q, &FilterList::default(), prefs);
            }
            Listing::Latest => {
                q.push(("sort", "uploaded".into()));
                ratings(&mut q, &FilterList::default(), prefs);
            }
            Listing::Search(query) => {
                let query = query.trim();
                if !query.is_empty() {
                    q.push(("q", query.chars().take(100).collect()));
                }
                apply(&mut q, filters, !query.is_empty());
                ratings(&mut q, filters, prefs);
            }
        }
        let items = client.comick_search(&q)?;
        // Comick doesn't report totals; keep paging while pages come back full.
        let has_next = items.len() as u32 == PAGE_SIZE;
        Ok(Results { items, has_next })
    }

    fn shelves(&self) -> &'static [ShelfInfo] {
        SHELVES
    }

    fn shelf(&self, client: &Client, id: &str, period: u32, prefs: &Filters) -> Result<Vec<Manga>> {
        let chart = match id {
            "followed-new" => Chart::MostFollowedNew,
            "recent-popular" => Chart::RecentPopular,
            _ => Chart::Trending,
        };
        client.comick_chart(chart, period, prefs)
    }

    fn open(&self, client: &Client, manga: &Manga) -> Result<Manga> {
        super::find_on_mangadex(client, manga)
    }
}

/// Turns the chosen filters into Comick query parameters.
fn apply(q: &mut Vec<(&'static str, String)>, f: &FilterList, has_query: bool) {
    // The first sort option is the default; typing switches the default to best match.
    match f.select("sort") {
        Some(sort) => q.push(("sort", sort.to_owned())),
        None if !has_query => q.push(("sort", "user_follow_count".into())),
        None => {}
    }
    let (inc, exc) = f.tri("tags");
    q.extend(inc.into_iter().map(|t| ("tags", t.to_owned())));
    q.extend(exc.into_iter().map(|t| ("excluded-tags", t.to_owned())));
    for group in ["genres-genre", "genres-theme", "genres-format", "genres-content"] {
        let (inc, exc) = f.tri(group);
        q.extend(inc.into_iter().map(|g| ("genres", g.to_owned())));
        q.extend(exc.into_iter().map(|g| ("excludes", g.to_owned())));
    }
    q.extend(
        f.checked("country")
            .into_iter()
            .map(|c| ("country", c.to_owned())),
    );
    q.extend(
        f.checked("demographic")
            .into_iter()
            .map(|d| ("demographic", d.to_owned())),
    );
    q.extend(f.checked("status").into_iter().map(|s| ("status", s.to_owned())));
    for key in ["time", "from", "to"] {
        if let Some(v) = f.select(key) {
            q.push((key, v.to_owned()));
        }
    }
    for key in ["minimum", "maximum", "minimum_rating"] {
        if let Some(v) = f.slider(key) {
            q.push((key, v.to_string()));
        }
    }
}

/// Content ratings chosen in the filters, limited to what Settings allows.
fn ratings(q: &mut Vec<(&'static str, String)>, f: &FilterList, prefs: &Filters) {
    let allowed = |r: &str| match r {
        "suggestive" => prefs.show_suggestive,
        "erotica" => prefs.show_erotica,
        _ => true,
    };
    let picked: Vec<&str> = f
        .checked("content_rating")
        .into_iter()
        .filter(|r| allowed(r))
        .collect();
    let list: Vec<&str> = if picked.is_empty() {
        ["safe", "suggestive", "erotica"]
            .into_iter()
            .filter(|r| allowed(r))
            .collect()
    } else {
        picked
    };
    q.extend(list.into_iter().map(|r| ("content_rating", r.to_owned())));
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn maps_filters_to_query() {
        let mut list = FilterList::new(vec![
            Filter::Select {
                key: "sort".into(),
                name: "Sort".into(),
                options: vec![opt("Most Followed", "user_follow_count"), opt("Rated", "rating")],
                selected: 1,
            },
            group(
                "tags",
                "Tags",
                None,
                vec![tri("tag", "Strong Male Lead", "strong-male-lead", Some(3287))],
                true,
            ),
            group(
                "genres-genre",
                "Genres",
                None,
                vec![tri("genre", "Comedy", "comedy", None)],
                false,
            ),
            group(
                "demographic",
                "Demographic",
                None,
                vec![check("demographic", "Shounen", "1")],
                false,
            ),
            Filter::Slider {
                key: "maximum".into(),
                name: "Max".into(),
                min: 0,
                max: 500,
                step: 10,
                value: 50,
                format: SliderFormat::AtMost,
            },
        ]);
        for (i, state) in [(1, TagState::Include), (2, TagState::Exclude)] {
            if let Filter::Group { filters, .. } = &mut list.filters[i] {
                if let Filter::TriState { state: s, .. } = &mut filters[0] {
                    *s = state;
                }
            }
        }
        if let Filter::Group { filters, .. } = &mut list.filters[3] {
            if let Filter::CheckBox { checked, .. } = &mut filters[0] {
                *checked = true;
            }
        }
        let mut q = Vec::new();
        apply(&mut q, &list, false);
        ratings(&mut q, &list, &Filters::default());
        for pair in [
            ("sort", "rating"),
            ("tags", "strong-male-lead"),
            ("excludes", "comedy"),
            ("demographic", "1"),
            ("maximum", "50"),
            ("content_rating", "safe"),
            ("content_rating", "suggestive"),
        ] {
            assert!(q.contains(&(pair.0, pair.1.to_owned())), "missing {pair:?}");
        }
        assert!(
            !q.iter().any(|(_, v)| v == "erotica"),
            "erotica is off by default"
        );
    }
}
