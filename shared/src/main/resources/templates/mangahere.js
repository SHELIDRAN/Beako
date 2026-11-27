// MangaHere, for Beako. A JavaScript port of Mangayomi's Dart source
// dart/manga/src/en/mangahere/mangahere.dart (mangayomi-extensions, Apache-2.0, © kodjodevf and
// contributors).

class DefaultExtension extends MProvider {
    get baseUrl() {
        const url = this.source.baseUrl;
        return url.endsWith("/") ? url.slice(0, -1) : url;
    }

    getHeaders(url) { return { Referer: this.baseUrl + "/", Cookie: "isAdult=1" }; }

    async page(url) {
        const res = await new Client().get(url, this.getHeaders());
        return res.body;
    }

    list(html, kind) {
        const doc = new Document(html);
        const items = `//*[contains(@class, "manga-list-${kind}-list")]/li/a`;
        const names = doc.xpath(items + "/@title");
        const images = doc.xpath(`${items}/img[@class="manga-list-${kind}-cover"]/@src`);
        const links = doc.xpath(items + "/@href");
        const list = [];
        for (let i = 0; i < names.length && i < links.length; i++) {
            list.push({ name: names[i].trim(), imageUrl: images[i] || "", link: links[i] });
        }
        return { list: list, hasNextPage: list.length > 0 };
    }

    async getPopular(page) {
        return this.list(await this.page(`${this.baseUrl}/directory/${page}.htm`), 1);
    }

    async getLatestUpdates(page) {
        return this.list(await this.page(`${this.baseUrl}/directory/${page}.htm?latest`), 1);
    }

    async search(query, page, filters) {
        const url = `${this.baseUrl}/search?title=${encodeURIComponent(query)}&page=${page}`;
        return this.list(await this.page(url), 4);
    }

    absolute(url) {
        if (url.startsWith("http")) return url;
        return this.baseUrl + (url.startsWith("/") ? "" : "/") + url;
    }

    async getDetail(url) {
        const doc = new Document(await this.page(this.absolute(url)));
        const first = (path) => doc.xpathFirst(path).trim();
        const manga = {
            author: first('//*[@class="detail-info-right-say"]/a/text()'),
            description: first('//*[@class="fullcontent"]/text()'),
            genre: doc.xpath('//*[@class="detail-info-right-tag-list"]/a/text()'),
            chapters: [],
        };
        const status = first('//*[@class="detail-info-right-title-tip"]/text()');
        manga.status = status === "Ongoing" ? 0 : status === "Completed" ? 1 : 5;
        const links = doc.xpath('//*[@class="detail-main-list"]/li/a/@href');
        const names = doc.xpath('//*[@class="detail-main-list"]/li/a/div/p[@class="title3"]/text()');
        const dates = doc.xpath('//*[@class="detail-main-list"]/li/a/div/p[@class="title2"]/text()');
        const parsed = this.source.dateFormat
            ? parseDates(dates, this.source.dateFormat, this.source.dateFormatLocale)
            : [];
        for (let i = 0; i < names.length && i < links.length; i++) {
            const chapter = { name: names[i].trim(), url: links[i] };
            // An unreadable date stays unknown, never "now" or 1970.
            if (parsed[i]) chapter.dateUpload = parsed[i];
            manga.chapters.push(chapter);
        }
        return manga;
    }

    // The page list is hidden in packed scripts: either every image in one ("newImgs"), or a key
    // to ask chapterfun.ashx for each page in turn.
    async getPageList(url) {
        const pageUrl = this.absolute(url);
        const html = await this.page(pageUrl);
        const doc = new Document(html);
        const pageNumbers = doc.xpath("//body/div/div/span/a/text()");
        if (!pageNumbers.length) {
            const script = doc.xpathFirst("//script[contains(text(),'function(p,a,c,k,e,d)')]/text()")
                .replace("eval", "");
            const code = unpackJs(script);
            const start = code.indexOf("newImgs=['") + 10;
            const end = code.indexOf("'];", start);
            if (start < 10 || end < 0) return [];
            return code.substring(start, end).split("','").map((u) => "https:" + u);
        }
        const count = parseInt(pageNumbers[pageNumbers.length - 2], 10) || 0;
        const at = html.indexOf("eval(function(p,a,c,k,e,d)");
        const keyCode = unpackJs(html.substring(at, html.indexOf("</script>", at)).replace("eval", ""));
        let key = keyCode.substring(keyCode.indexOf("'"), keyCode.indexOf(";"));
        const idAt = html.indexOf("chapterid");
        const chapterId = html.substring(idAt + 11, html.indexOf(";", idAt)).trim();
        const base = pageUrl.substring(0, pageUrl.lastIndexOf("/"));
        const host = this.baseUrl.replace(/^https?:\/\//, "");
        const pages = [];
        for (let i = 1; i <= count; i++) {
            const res = await new Client().get(
                `${base}/chapterfun.ashx?cid=${chapterId}&page=${i}&key=${key}`,
                { Referer: pageUrl, Accept: "*/*", Host: host, "X-Requested-With": "XMLHttpRequest" });
            const text = res.body || "";
            if (!text) key = "";
            const code = unpackJs(text.replace("eval", ""));
            const baseStart = code.indexOf("pix=") + 5;
            const baseLink = code.substring(baseStart, code.indexOf(";", baseStart) - 1);
            const imageStart = code.indexOf("pvalue=") + 9;
            const image = code.substring(imageStart, code.indexOf("\"", imageStart));
            if (baseLink && image) pages.push("https:" + baseLink + image);
        }
        return pages;
    }

    getFilterList() { return []; }

    getSourcePreferences() { return []; }
}
