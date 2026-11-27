// AllAnime / AllManga, for Beako. A JavaScript port of Mangayomi's Dart source
// dart/manga/src/en/allanime/allanime.dart (mangayomi-extensions, Apache-2.0). It reads the site's
// public GraphQL API.

const __API = "https://api.allanime.day/api";
const __SITE = "https://allmanga.to";
const __COVERS = "https://wp.youtube-anime.com/aln.youtube-anime.com";
const __PAGES = "https://ytimgf.youtube-anime.com";
const __PAGES_SMALL = "https://wp.youtube-anime.com";

const __CARD = "_id name thumbnail englishName";

class DefaultExtension extends MProvider {
    getHeaders(url) { return { Accept: "*/*", Referer: __SITE + "/" }; }

    async graphql(query, variables) {
        const res = await new Client().post(
            __API,
            { Accept: "*/*", Referer: __SITE + "/", "Content-Type": "application/json" },
            { query: query, variables: variables },
        );
        return (JSON.parse(res.body) || {}).data || {};
    }

    cover(url) {
        if (!url) return "";
        return url.startsWith("http") ? url : `${__COVERS}/${url}`;
    }

    card(m) {
        if (!m || !m._id || !m.thumbnail) return null;
        return {
            name: m.englishName || m.name || m.nativeName || "",
            imageUrl: this.cover(String(m.thumbnail)),
            link: `${__SITE}/manga/${m._id}`,
        };
    }

    preference(key, fallback) {
        try { return String(new SharedPreferences().get(key) || fallback); } catch (e) { return fallback; }
    }

    async getPopular(page) {
        const data = await this.graphql(
            `query ($type: VaildPopularTypeEnumType!, $size: Int!, $page: Int, $dateRange: Int,
                    $allowAdult: Boolean, $allowUnknown: Boolean) {
                queryPopular(type: $type, size: $size, dateRange: $dateRange, page: $page,
                             allowAdult: $allowAdult, allowUnknown: $allowUnknown) {
                    recommendations { anyCard { ${__CARD} } }
                }
            }`,
            { type: "manga", size: 20, page: page, dateRange: 0, allowAdult: false, allowUnknown: false },
        );
        const items = ((data.queryPopular || {}).recommendations || []).map((r) => this.card(r.anyCard));
        const list = items.filter((m) => m);
        return { list: list, hasNextPage: list.length > 0 };
    }

    async mangas(page, query) {
        const search = { isManga: true, allowAdult: false, allowUnknown: false };
        if (query) search.query = query;
        const data = await this.graphql(
            `query ($search: SearchInput, $size: Int, $page: Int,
                    $translationType: VaildTranslationTypeMangaEnumType,
                    $countryOrigin: VaildCountryOriginEnumType) {
                mangas(search: $search, limit: $size, page: $page, translationType: $translationType,
                       countryOrigin: $countryOrigin) {
                    edges { ${__CARD} }
                }
            }`,
            { search: search, size: 20, page: page, translationType: "sub", countryOrigin: "ALL" },
        );
        const list = ((data.mangas || {}).edges || []).map((m) => this.card(m)).filter((m) => m);
        return { list: list, hasNextPage: list.length > 0 };
    }

    async getLatestUpdates(page) { return this.mangas(page, ""); }

    async search(query, page, filters) { return this.mangas(page, query); }

    async getDetail(url) {
        const id = url.replace(/\/+$/, "").split("/").pop();
        const data = await this.graphql(
            `query ($id: String!) {
                manga(_id: $id) {
                    _id name thumbnail description authors genres tags status altNames englishName
                }
            }`,
            { id: id },
        );
        const m = data.manga;
        if (!m) throw new Error("AllAnime has no details for this title");
        const episodes = await this.graphql(
            `query ($id: String!, $chapterNumStart: Float!, $chapterNumEnd: Float!) {
                episodeInfos(showId: $id, episodeNumStart: $chapterNumStart,
                             episodeNumEnd: $chapterNumEnd) {
                    episodeIdNum notes uploadDates
                }
            }`,
            { id: `manga@${id}`, chapterNumStart: 0, chapterNumEnd: 9999 },
        );
        const infos = (episodes.episodeInfos || []).filter((e) => e && e.episodeIdNum != null);
        infos.sort((a, b) => b.episodeIdNum - a.episodeIdNum);
        const chapters = infos.map((e) => {
            const chapter = {
                name: `Chapter ${e.episodeIdNum}`,
                url: `${__SITE}/manga/${id}/chapter-${e.episodeIdNum}-sub`,
            };
            const time = Date.parse(((e.uploadDates || {}).sub || "").trim());
            // An unreadable date stays unknown, never "now" or 1970.
            if (!isNaN(time) && time > 0) chapter.dateUpload = String(time);
            return chapter;
        });
        const description = new Document(m.description || "").text.trim();
        const alt = (m.altNames || []).filter((n) => n);
        const genres = [];
        for (const g of (m.genres || []).concat(m.tags || [])) if (!genres.includes(g)) genres.push(g);
        const status = String(m.status || "").toLowerCase();
        return {
            name: m.englishName || m.name || "",
            imageUrl: this.cover(m.thumbnail || ""),
            author: (m.authors || [])[0] || "",
            genre: genres,
            status: /finished|complete/.test(status) ? 1 : /releasing|ongoing|publishing/.test(status) ? 0 : 5,
            description: alt.length ? `${description}\n\nAlternative names:\n${alt.join("\n")}` : description,
            chapters: chapters,
        };
    }

    async getPageList(url) {
        const parts = url.replace(/\/+$/, "").split("/");
        if (parts.length < 3) return [];
        const chapter = parts.pop().split("-");
        const id = parts.pop();
        const data = await this.graphql(
            `query ($id: String!, $translationType: VaildTranslationTypeMangaEnumType!,
                    $chapterNum: String!) {
                chapterPages(mangaId: $id, translationType: $translationType, chapterString: $chapterNum) {
                    edges { pictureUrls pictureUrlHead }
                }
            }`,
            { id: id, translationType: chapter[chapter.length - 1] === "dub" ? "dub" : "sub", chapterNum: chapter[1] },
        );
        const edge = ((data.chapterPages || {}).edges || [])[0];
        if (!edge) return [];
        const quality = this.preference("IMAGEQUALITY", "original");
        const head = String(edge.pictureUrlHead || "").replace(/^https?:\/\//, "").replace(/\/$/, "");
        return (edge.pictureUrls || []).map((p) => {
            const path = String(p.url || "");
            const url = quality === "800" ? `${__PAGES}/${path}?w=800`
                : quality === "480" ? `${__PAGES_SMALL}/${head}/${path}?w=480`
                : `${__PAGES}/${path}`;
            return { url: url, headers: this.getHeaders(url) };
        });
    }

    getFilterList() { return []; }

    getSourcePreferences() {
        return [{
            key: "IMAGEQUALITY",
            listPreference: {
                title: "Image quality", summary: "", valueIndex: 0,
                entries: ["Original", "800 wide", "480 wide"],
                entryValues: ["original", "800", "480"],
            },
        }];
    }
}
