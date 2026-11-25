// MangaReader / MangaThemesia (WordPress) sites, for Beako. A JavaScript port of Mangayomi's Dart
// template dart/manga/multisrc/mangareader/mangareader.dart (mangayomi-extensions, Apache-2.0,
// © kodjodevf and contributors). Each installed source supplies its own name, address and date
// format.

function __exists(el) { return !!el && el.outerHtml !== ""; }

function __withoutDomain(url) { return String(url).replace(/^https?:\/\/[^/]+/i, ""); }

class DefaultExtension extends MProvider {
    get baseUrl() {
        let url = "";
        try { url = String(new SharedPreferences().get("override_baseurl") || "").trim(); } catch (e) {}
        if (!url) url = this.source.baseUrl;
        return url.endsWith("/") ? url.slice(0, -1) : url;
    }

    getHeaders(url) { return { Referer: this.baseUrl + "/" }; }

    directory() { return this.source.name === "Sushi-Scan" ? "/catalogue" : "/manga"; }

    titles(html) {
        const list = [];
        for (const element of new Document(html).select(".utao .uta .imgu, .listupd .bs .bsx, .listo .bs .bsx")) {
            const link = element.selectFirst("a");
            if (!__exists(link)) continue;
            let image = element.getSrc;
            if (!image || image.includes("data:image")) image = element.getDataSrc || image;
            list.push({ name: link.attr("title").trim() || link.text.trim(), imageUrl: image, link: link.attr("href") });
        }
        return { list: list, hasNextPage: list.length > 0 };
    }

    async listing(order, page) {
        const res = await new Client().get(
            `${this.baseUrl}${this.directory()}/?page=${page}&order=${order}`, this.getHeaders());
        return this.titles(res.body);
    }

    async getPopular(page) { return this.listing("popular", page); }

    async getLatestUpdates(page) { return this.listing("update", page); }

    async search(query, page, filters) {
        const q = encodeURIComponent(query);
        const path = this.source.name === "Sushi-Scan" ? `/page/${page}/?s=${q}` : `/?s=${q}&page=${page}`;
        const res = await new Client().get(this.baseUrl + path, this.getHeaders());
        return this.titles(res.body);
    }

    async getDetail(url) {
        const res = await new Client().get(this.baseUrl + __withoutDomain(url), this.getHeaders());
        const doc = new Document(res.body);
        const info = doc.selectFirst("div.bigcontent, div.animefull, div.main-info, div.postbody");
        const root = __exists(info) ? info : doc;
        const label = (word) =>
            `.infotable tr:contains(${word}) td:last-child, .tsinfo .imptdt:contains(${word}) i, .fmed b:contains(${word})+span`;
        const manga = { chapters: [] };
        manga.author = root.selectFirst(
            ["Author", "Auteur", "autor", "المؤلف", "Mangaka", "seniman", "Pengarang", "Yazar", "ผู้วาด"].map(label).join(", ")).text.trim();
        manga.description = root.selectFirst(".desc, .entry-content[itemprop=description]").text.trim();
        manga.genre = root.select("div.gnr a, .mgen a, .seriestugenre a").map((e) => e.text.trim());
        const thumb = doc.selectFirst("div.thumb img, .infomanga img, .seriestucontl img");
        if (__exists(thumb)) manga.imageUrl = thumb.attr("data-src") || thumb.getSrc;
        for (const element of doc.select("div.bxcl li, div.cl li, #chapterlist li, ul li:has(div.chbox):has(div.eph-num)")) {
            const link = element.selectFirst("a");
            if (!__exists(link)) continue;
            const named = element.selectFirst(".lch a, .chapternum");
            const chapter = {
                name: (__exists(named) ? named.text : link.text).trim(),
                url: link.attr("href"),
            };
            const date = element.selectFirst(".chapterdate");
            if (this.source.dateFormat && __exists(date) && date.text.trim()) {
                const parsed = parseDates([date.text.trim()], this.source.dateFormat,
                    this.source.dateFormatLocale)[0];
                // An unreadable date stays unknown, never "now" or 1970.
                if (parsed) chapter.dateUpload = parsed;
            }
            manga.chapters.push(chapter);
        }
        return manga;
    }

    async getPageList(url) {
        const res = await new Client().get(this.baseUrl + __withoutDomain(url), this.getHeaders());
        const doc = new Document(res.body);
        let pages = doc.xpath('//*[@id="readerarea"]/p/img/@src');
        if (pages.length <= 1) pages = doc.xpath('//*[@id="readerarea"]/img/@src');
        if (pages.length > 1 && pages.some((p) => p.includes("data:image"))) {
            pages = doc.xpath('//*[@id="readerarea"]/img/@data-src');
        }
        if (pages.length > 1) return pages.filter((p) => p);
        // Newer themes list the images in the page's script: "images":[...]
        const match = res.body.match(/"images"\s*:\s*(\[.*?\])/);
        if (match) {
            try { return JSON.parse(match[1]); } catch (e) {}
        }
        return pages;
    }

    getFilterList() { return []; }

    getSourcePreferences() {
        return [{
            key: "override_baseurl",
            editTextPreference: {
                title: "Site address", summary: "", value: this.source.baseUrl,
                dialogTitle: "Site address", dialogMessage: "Default: " + this.source.baseUrl,
            },
        }];
    }
}
