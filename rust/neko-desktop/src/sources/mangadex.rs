//! The MangaDex source: popular and latest lists, search, and filters built on Comix's tag
//! vocabulary (genres, formats, types, statuses, demographics) mapped onto MangaDex's tags,
//! plus MangaDex's themes. MangaDex is also where every title is read.

use anyhow::Result;

use super::{Listing, Results, Source, SourceInfo};
use crate::api::{Client, Filters, Manga};
use crate::filters::{Filter, FilterList, TagState, opt};

/// Comix's genres. The adult ones (Adult, Ecchi, Hentai, Mature, Smut) aren't tags on MangaDex;
/// the content settings cover them.
const GENRES: [&str; 26] = [
    "Action",
    "Adventure",
    "Boys' Love",
    "Comedy",
    "Crime",
    "Drama",
    "Fantasy",
    "Girls' Love",
    "Harem",
    "Historical",
    "Horror",
    "Isekai",
    "Magical Girls",
    "Mecha",
    "Medical",
    "Mystery",
    "Philosophical",
    "Psychological",
    "Romance",
    "Sci-Fi",
    "Slice of Life",
    "Sports",
    "Superhero",
    "Thriller",
    "Tragedy",
    "Wuxia",
];

/// Comix's formats.
const FORMATS: [&str; 9] = [
    "4-Koma",
    "Adaptation",
    "Anthology",
    "Award Winning",
    "Doujinshi",
    "Full Color",
    "Long Strip",
    "Oneshot",
    "Web Comic",
];

const PAGE_SIZE: u32 = 32;

pub struct MangaDex;

fn check(key: &str, name: &str, value: &str) -> Filter {
    Filter::CheckBox {
        key: key.into(),
        name: name.into(),
        value: value.into(),
        checked: false,
    }
}

fn group(key: &str, name: &str, hint: Option<&str>, filters: Vec<Filter>) -> Filter {
    Filter::Group {
        key: key.into(),
        name: name.into(),
        hint: hint.map(str::to_owned),
        filters,
        searchable: false,
    }
}

impl Source for MangaDex {
    fn info(&self) -> SourceInfo {
        SourceInfo {
            id: "mangadex",
            name: "MangaDex",
            lang: "multi",
            description: "Scanlations in dozens of languages",
            base_url: "https://mangadex.org",
            supports_latest: true,
            reads_from: None,
            color: [0xFF, 0x67, 0x40],
        }
    }

    fn filter_list(&self, client: &Client) -> Result<FilterList> {
        let tags = client.tags()?;
        let tag_id = |name: &str| {
            let n = super::normalize(name);
            tags.iter()
                .find(|t| super::normalize(&t.name) == n)
                .map(|t| t.id.clone())
        };
        let tri = |key: &str, name: &str, id: String| Filter::TriState {
            key: key.into(),
            name: name.into(),
            value: id,
            state: TagState::Any,
            count: None,
        };
        let genres = GENRES
            .iter()
            .filter_map(|g| tag_id(g).map(|id| tri("genre", g, id)))
            .collect();
        let formats = FORMATS
            .iter()
            .filter_map(|f| tag_id(f).map(|id| tri("format", f, id)))
            .collect();
        let themes = tags
            .iter()
            .filter(|t| t.group == "theme" || t.group == "content")
            .map(|t| tri("theme", &t.name, t.id.clone()))
            .collect();
        let this_year = 1970 + (crate::library::now() / 31_556_952) as u32;
        let mut years = vec![opt("Any Year", "")];
        years.extend(
            (1960..=this_year + 1)
                .rev()
                .map(|y| opt(&y.to_string(), &y.to_string())),
        );

        Ok(FilterList::new(vec![
            Filter::Sort {
                key: "sort".into(),
                name: "Sort By".into(),
                options: vec![
                    opt("Best Match", "relevance"),
                    opt("Latest Update", "latestUploadedChapter"),
                    opt("Recently Added", "createdAt"),
                    opt("Most Followed", "followedCount"),
                    opt("Highest Rated", "rating"),
                    opt("Title", "title"),
                    opt("Year", "year"),
                ],
                selected: None,
                ascending: false,
            },
            group(
                "type",
                "Type",
                None,
                vec![
                    check("type", "Manga", "ja"),
                    check("type", "Manhwa", "ko"),
                    check("type", "Manhua", "zh"),
                    check("type", "Other", "other"),
                ],
            ),
            group(
                "status",
                "Status",
                None,
                vec![
                    check("status", "Releasing", "ongoing"),
                    check("status", "Finished", "completed"),
                    check("status", "On Hiatus", "hiatus"),
                    check("status", "Discontinued", "cancelled"),
                ],
            ),
            group(
                "demographic",
                "Demographic",
                None,
                vec![
                    check("demographic", "Shounen", "shounen"),
                    check("demographic", "Shoujo", "shoujo"),
                    check("demographic", "Seinen", "seinen"),
                    check("demographic", "Josei", "josei"),
                ],
            ),
            Filter::Select {
                key: "year".into(),
                name: "Year".into(),
                options: years,
                selected: 0,
            },
            group(
                "genres",
                "Genres",
                Some("Click once to include, twice to exclude."),
                genres,
            ),
            group("formats", "Formats", None, formats),
            group("themes", "Themes", None, themes),
            Filter::Select {
                key: "tag_mode".into(),
                name: "Included Tags Must".into(),
                options: vec![opt("Match Any", "OR"), opt("Match All", "AND")],
                selected: 0,
            },
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
        let offset = (page.max(1) - 1) * PAGE_SIZE;
        let mut q: Vec<(&'static str, String)> =
            vec![("limit", PAGE_SIZE.to_string()), ("offset", offset.to_string())];
        match listing {
            Listing::Popular => q.push(("order[followedCount]", "desc".into())),
            Listing::Latest => q.push(("order[latestUploadedChapter]", "desc".into())),
            Listing::Search(query) => {
                apply(&mut q, filters);
                let query = query.trim();
                if !query.is_empty() {
                    q.push(("title", query.to_owned()));
                }
                match filters.sort("sort") {
                    Some((field, asc)) if field != "relevance" || !query.is_empty() => {
                        q.push((order_key(field), if asc { "asc" } else { "desc" }.into()));
                    }
                    _ if query.is_empty() => q.push(("order[followedCount]", "desc".into())),
                    _ => q.push(("order[relevance]", "desc".into())),
                }
            }
        }
        let page = client.list_manga(q, prefs)?;
        let has_next = offset + (page.items.len() as u32) < page.total;
        Ok(Results {
            items: page.items,
            has_next,
        })
    }

    fn open(&self, _client: &Client, manga: &Manga) -> Result<Manga> {
        Ok(manga.clone())
    }
}

fn order_key(field: &str) -> &'static str {
    match field {
        "latestUploadedChapter" => "order[latestUploadedChapter]",
        "createdAt" => "order[createdAt]",
        "followedCount" => "order[followedCount]",
        "rating" => "order[rating]",
        "title" => "order[title]",
        "year" => "order[year]",
        _ => "order[relevance]",
    }
}

/// Turns the chosen filters into MangaDex query parameters.
fn apply(q: &mut Vec<(&'static str, String)>, f: &FilterList) {
    let mut included = 0;
    for key in ["genres", "formats", "themes"] {
        let (inc, exc) = f.tri(key);
        included += inc.len();
        q.extend(inc.into_iter().map(|id| ("includedTags[]", id.to_owned())));
        q.extend(exc.into_iter().map(|id| ("excludedTags[]", id.to_owned())));
    }
    if included > 0 {
        q.push((
            "includedTagsMode",
            f.select("tag_mode").unwrap_or("OR").to_owned(),
        ));
    }
    for t in f.checked("type") {
        let langs: &[&str] = match t {
            "ja" => &["ja"],
            "ko" => &["ko"],
            "zh" => &["zh", "zh-hk"],
            _ => &["en", "fr", "es", "id", "vi", "th", "pt-br", "ru", "de", "it"],
        };
        q.extend(langs.iter().map(|l| ("originalLanguage[]", (*l).to_owned())));
    }
    q.extend(
        f.checked("status")
            .into_iter()
            .map(|s| ("status[]", s.to_owned())),
    );
    q.extend(
        f.checked("demographic")
            .into_iter()
            .map(|d| ("publicationDemographic[]", d.to_owned())),
    );
    if let Some(y) = f.select("year") {
        q.push(("year", y.to_owned()));
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn maps_filters_to_query() {
        let mut list = FilterList::new(vec![
            group(
                "genres",
                "Genres",
                None,
                vec![Filter::TriState {
                    key: "genre".into(),
                    name: "Action".into(),
                    value: "tag-action".into(),
                    state: TagState::Include,
                    count: None,
                }],
            ),
            group(
                "themes",
                "Themes",
                None,
                vec![Filter::TriState {
                    key: "theme".into(),
                    name: "Gore".into(),
                    value: "tag-gore".into(),
                    state: TagState::Exclude,
                    count: None,
                }],
            ),
            group("type", "Type", None, vec![check("type", "Manhwa", "ko")]),
            Filter::Select {
                key: "tag_mode".into(),
                name: "Mode".into(),
                options: vec![opt("Any", "OR"), opt("All", "AND")],
                selected: 1,
            },
        ]);
        if let Filter::Group { filters, .. } = &mut list.filters[2] {
            if let Filter::CheckBox { checked, .. } = &mut filters[0] {
                *checked = true;
            }
        }
        let mut q = Vec::new();
        apply(&mut q, &list);
        assert!(q.contains(&("includedTags[]", "tag-action".into())));
        assert!(q.contains(&("excludedTags[]", "tag-gore".into())));
        assert!(q.contains(&("includedTagsMode", "AND".into())));
        assert!(q.contains(&("originalLanguage[]", "ko".into())));
    }
}
