// MangaBox sites (Manganato, Mangakakalot, Mangabat, Mangairo), for Beako. A JavaScript port of
// Mangayomi's Dart template dart/manga/multisrc/mangabox/mangabox.dart (mangayomi-extensions,
// Apache-2.0, © kodjodevf and contributors).

class DefaultExtension extends MProvider {
    get baseUrl() {
        const url = this.source.baseUrl;
        return url.endsWith("/") ? url.slice(0, -1) : url;
    }

    getHeaders(url) { return { Referer: this.baseUrl + "/" }; }

    isMangairo() { return this.source.name === "Mangairo"; }

    async html(url) {
        const res = await new Client().get(url, this.getHeaders());
        return new Document(res.body);
    }

    // The Dart template uses XPath `^=` matches, which standard XPath lacks; these CSS selectors
    // pick the same cards.
    titles(doc) {
        const list = [];
        const cards = doc.select(
            "div[class^=genres-item], div.content-genres-item, div.list-truyen-item-wrap, div.story-item, " +
            "div.story_item, div.search-story-item, div.list-story-item");
        for (const card of cards) {
            let link = card.selectFirst("h3 a, h2 a");
            let name = link.text.trim();
            if (!name) {
                link = card.selectFirst("a[title]");
                name = link.attr("title").trim();
            }
            const href = link.attr("href");
            if (!name || !href) continue;
            const img = card.selectFirst("img");
            list.push({ name: name, imageUrl: img.attr("data-src") || img.attr("src"), link: href });
        }
        return { list: list, hasNextPage: list.length > 0 };
    }

    async getPopular(page) {
        const path = this.isMangairo()
            ? `manga-list/type-topview/ctg-all/state-all/page-${page}`
            : `manga-list/hot-manga?page=${page}`;
        return this.titles(await this.html(`${this.baseUrl}/${path}`));
    }

    async getLatestUpdates(page) {
        const path = this.isMangairo()
            ? `manga-list/type-latest/ctg-all/state-all/page-${page}`
            : `manga-list/latest-manga?page=${page}`;
        return this.titles(await this.html(`${this.baseUrl}/${path}`));
    }

    normalise(query) {
        let s = query.toLowerCase();
        s = s.replace(/[àáạảãâầấậẩẫăằắặẳẵ]/g, "a").replace(/[èéẹẻẽêềếệểễ]/g, "e")
            .replace(/[ìíịỉĩ]/g, "i").replace(/[òóọỏõôồốộổỗơờớợởỡ]/g, "o")
            .replace(/[ùúụủũưừứựửữ]/g, "u").replace(/[ỳýỵỷỹ]/g, "y").replace(/đ/g, "d");
        s = s.replace(/[!@%^*()+=<>?\/,.:;'" "&#\[\]~\-$_]/g, "_").replace(/_+/g, "_").replace(/^_+|_+$/g, "");
        return s;
    }

    async search(query, page, filters) {
        const q = this.normalise(query);
        const path = this.isMangairo() ? `list/search/${q}?page=${page}` : `search/story/${q}?page=${page}`;
        return this.titles(await this.html(`${this.baseUrl}/${path}`));
    }

    async getDetail(url) {
        const doc = await this.html(url);
        const manga = { chapters: [] };
        manga.author = doc.xpathFirst(
            '//*[@class="table-label" and contains(text(), "Author")]/parent::tr/td[2]/text()|//li[contains(text(), "Author")]/a/text()');
        let description = doc.xpathFirst(
            '//*[@id="contentBox" ]/text() | //*[@id="story_discription" ]/text() | //div[@id="noidungm"]/text()');
        if (description) {
            description = description.split("summary:").pop().split("Summary:").pop()
                .replace(/\n/g, " ").replace("Description :", "").trim();
            manga.description = description;
        }
        manga.genre = doc.xpath(
            '//*[@class="table-label" and contains(text(), "Genres")]/parent::tr/td[2]/a/text() | //li[contains(text(), "Genres")]/a/text()');
        for (const element of doc.select("div.chapter-list div.row, ul.row-content-chapter li, div#chapter_list li")) {
            const a = element.selectFirst("a");
            const href = a.getHref;
            if (!href) continue;
            const chapter = { name: a.text.trim(), url: href };
            const spans = element.select("span");
            const date = spans.length ? spans[spans.length - 1].text.trim() : element.selectFirst("p").text.trim();
            if (date && this.source.dateFormat) {
                const parsed = parseDates([date], this.source.dateFormat, this.source.dateFormatLocale)[0];
                // An unreadable date stays unknown, never "now" or 1970.
                if (parsed) chapter.dateUpload = parsed;
            }
            manga.chapters.push(chapter);
        }
        return manga;
    }

    async getPageList(url) {
        const doc = await this.html(url);
        return doc.xpath('//div[@class="container-chapter-reader" or @class="panel-read-story"]/img/@src')
            .filter((u) => u);
    }

    getFilterList() { return []; }

    getSourcePreferences() { return []; }
}
