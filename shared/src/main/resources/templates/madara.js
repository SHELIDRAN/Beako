// Madara (WordPress "wp-manga") sites, for Beako. A JavaScript port of Mangayomi's Dart template
// dart/manga/multisrc/madara/madara.dart (mangayomi-extensions, Apache-2.0, © kodjodevf and
// contributors). Each installed source supplies its own name, address and date format.

function __exists(el) { return !!el && el.outerHtml !== ""; }

function __base64ToBytes(text) {
    const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    const clean = String(text).replace(/[^A-Za-z0-9+/]/g, "");
    const bytes = [];
    let buffer = 0, bits = 0;
    for (const c of clean) {
        buffer = (buffer << 6) | chars.indexOf(c);
        bits += 6;
        if (bits >= 8) { bits -= 8; bytes.push((buffer >> bits) & 0xff); }
    }
    return bytes;
}

function __bytesToBase64(bytes) {
    const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let out = "";
    for (let i = 0; i < bytes.length; i += 3) {
        const n = (bytes[i] << 16) | ((bytes[i + 1] || 0) << 8) | (bytes[i + 2] || 0);
        out += chars[(n >> 18) & 63] + chars[(n >> 12) & 63];
        out += i + 1 < bytes.length ? chars[(n >> 6) & 63] : "=";
        out += i + 2 < bytes.length ? chars[n & 63] : "=";
    }
    return out;
}

class DefaultExtension extends MProvider {
    get baseUrl() {
        let url = "";
        try { url = String(new SharedPreferences().get("domain_url") || "").trim(); } catch (e) {}
        if (!url) url = this.source.baseUrl;
        return url.endsWith("/") ? url.slice(0, -1) : url;
    }

    getHeaders(url) { return { Referer: this.baseUrl + "/" }; }

    mangaPath() {
        const special = {
            "Olaoe": "works", "Mangax Core": "works", "Azora": "series",
            "Manga Crab": "series", "KlikManga": "series", "Hwago": "komik",
        };
        return special[this.source.name] || "manga";
    }

    imageOf(el) {
        if (!__exists(el)) return "";
        const srcset = el.attr("srcset");
        return el.attr("data-src") || el.attr("data-lazy-src") ||
            (srcset ? srcset.trim().split(" ")[0] : "") || el.getSrc || "";
    }

    titles(elements) {
        const list = [];
        for (const el of elements) {
            const title = el.selectFirst("div.post-title a:not(:has(span.manga-title-badges))");
            if (!__exists(title)) continue;
            list.push({
                name: title.text.trim(),
                imageUrl: this.imageOf(el.selectFirst("img")).split(" ")[0],
                link: title.getHref,
            });
        }
        return { list: list, hasNextPage: list.length > 0 };
    }

    async listing(order, page) {
        const res = await new Client().get(
            `${this.baseUrl}/${this.mangaPath()}/page/${page}/?m_orderby=${order}`, this.getHeaders());
        return this.titles(new Document(res.body).select("div.page-item-detail, div.manga__item"));
    }

    async getPopular(page) { return this.listing("views", page); }

    async getLatestUpdates(page) { return this.listing("latest", page); }

    async search(query, page, filters) {
        const path = page > 1 ? `/page/${page}/` : "/";
        const url = `${this.baseUrl}${path}?s=${encodeURIComponent(query)}&post_type=wp-manga`;
        const res = await new Client().get(url, this.getHeaders());
        return this.titles(new Document(res.body).select("div.c-tabs-item__content"));
    }

    chaptersOf(doc) {
        const chapters = [];
        for (const element of doc.select("li.wp-manga-chapter")) {
            const link = element.selectFirst("a");
            if (!__exists(link)) continue;
            let url = link.attr("href");
            if (!url) continue;
            const chapter = { name: link.text.trim(), url: url };
            const date = element.selectFirst("span.chapter-release-date");
            if (this.source.dateFormat && __exists(date) && date.text.trim()) {
                const parsed = parseDates([date.text.trim()], this.source.dateFormat,
                    this.source.dateFormatLocale)[0];
                // An unreadable date stays unknown, never "now" or 1970.
                if (parsed) chapter.dateUpload = parsed;
            }
            chapters.push(chapter);
        }
        return chapters;
    }

    async getDetail(url) {
        const client = new Client();
        const res = await client.get(url, this.getHeaders());
        const doc = new Document(res.body);
        const manga = { chapters: [] };
        manga.author = doc.selectFirst("div.author-content > a").text.trim();
        const descriptions = doc.select(
            "div.description-summary div.summary__content, div.summary_content div.post-content_item > h5 + div, " +
            "div.summary_content div.manga-excerpt, .manga-summary, div.c-page__content div.modal-contenido");
        if (descriptions.length) {
            const paragraphs = [];
            descriptions.forEach((d) => d.select("p").forEach((p) => paragraphs.push(p.text.trim())));
            manga.description = paragraphs.some((p) => p)
                ? paragraphs.filter((p) => p).join("\n\n")
                : descriptions.map((d) => d.text.trim()).join("\n\n");
        }
        manga.imageUrl = this.imageOf(doc.selectFirst("div.summary_image img"));
        manga.genre = doc.select("div.genres-content a").map((e) => e.text.trim());

        // Chapters come from WordPress's ajax endpoint (older sites) or the title's ajax path.
        const headers = { Referer: this.baseUrl + "/", "X-Requested-With": "XMLHttpRequest" };
        const mangaId = doc.selectFirst("div[id^=manga-chapters-holder]").attr("data-id");
        const base = url.endsWith("/") ? url : url + "/";
        let body = "";
        if (mangaId) {
            const old = await client.post(`${this.baseUrl}/wp-admin/admin-ajax.php`, headers,
                { action: "manga_get_chapters", manga: mangaId });
            body = old.statusCode === 400 ? "" : old.body;
        }
        manga.chapters = this.chaptersOf(new Document(body));
        if (!manga.chapters.length) {
            const fresh = await client.post(`${base}ajax/chapters/`, headers, {});
            manga.chapters = this.chaptersOf(new Document(fresh.body));
        }
        if (!manga.chapters.length) {
            // Some sites list the chapters on the page itself.
            manga.chapters = this.chaptersOf(doc);
        }
        return manga;
    }

    async getPageList(url) {
        const res = await new Client().get(url, this.getHeaders());
        const doc = new Document(res.body);
        let images = doc.select(
            "div.page-break img, li.blocks-gallery-item img, .reading-content .text-left:not(:has(.blocks-gallery-item)) img")
            .map((e) => this.imageOf(e).trim()).filter((u) => u);
        if (images.length === 1) images = this.numbered(images[0], doc);
        return images.length ? images : this.protectedImages(doc);
    }

    numbered(first, doc) {
        const count = doc.selectFirst("#single-pager").select("option").length;
        if (count <= 1) return [first];
        const pages = [];
        for (let i = 0; i < count; i++) {
            const n = i + 1;
            pages.push(first.replace("01", i < 9 ? "0" + n : String(n)));
        }
        return pages;
    }

    // Chapters protected with WP Manga Chapter Protector: an AES (CryptoJS) blob and its password.
    protectedImages(doc) {
        try {
            const data = doc.selectFirst("#chapter-protector-data").innerHtml;
            if (!data) return [];
            const password = (data.match(/wpmangaprotectornonce='(.*?)';/) || [])[1];
            const raw = (data.match(/chapter_data='(.*?)';/) || [])[1];
            if (!password || !raw) return [];
            const blob = JSON.parse(raw.replace(/\\\//g, "/"));
            const salt = [];
            for (let i = 0; i < blob.s.length; i += 2) salt.push(parseInt(blob.s.substring(i, i + 2), 16));
            const salted = [83, 97, 108, 116, 101, 100, 95, 95].concat(salt, __base64ToBytes(blob.ct));
            const text = decryptAESCryptoJS(__bytesToBase64(salted), password);
            let list = JSON.parse(text);
            if (typeof list === "string") list = JSON.parse(list);
            return Array.isArray(list) ? list : [];
        } catch (e) {
            return [];
        }
    }

    getFilterList() { return []; }

    getSourcePreferences() {
        return [{
            key: "domain_url",
            editTextPreference: {
                title: "Site address", summary: "", value: this.source.baseUrl,
                dialogTitle: "Site address", dialogMessage: "",
            },
        }];
    }
}
