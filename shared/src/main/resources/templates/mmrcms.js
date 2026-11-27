// My Manga Reader CMS sites (Scan VF, Read Comics Online, Manga Online…), for Beako. A JavaScript
// port of Mangayomi's Dart template dart/manga/multisrc/mmrcms/mmrcms.dart (mangayomi-extensions,
// Apache-2.0, © kodjodevf and contributors). Each installed source supplies its own name, address
// and date format.

function __exists(el) { return !!el && el.outerHtml !== ""; }

const __AUTHOR = ["author(s)", "autor(es)", "auteur(s)", "著作", "yazar(lar)", "mangaka(lar)",
    "pengarang/penulis", "pengarang", "penulis", "autor", "المؤلف", "перевод", "autor/autorzy"];
const __ARTIST = ["artist(s)", "artiste(s)", "sanatçi(lar)", "artista(s)", "artist(s)/ilustrator",
    "الرسام", "seniman", "rysownik/rysownicy", "artista"];
const __GENRE = ["categories", "categorías", "catégories", "ジャンル", "kategoriler", "categorias",
    "kategorie", "التصنيفات", "жанр", "kategori", "tagi", "género"];
const __STATUS = ["status", "statut", "estado", "状態", "durum", "الحالة", "статус"];
const __ONGOING = ["ongoing", "مستمرة", "en cours", "em lançamento", "prace w toku", "ativo",
    "em andamento", "activo"];
const __COMPLETE = ["complete", "مكتملة", "complet", "completo", "zakończone", "concluído",
    "finalizado"];

class DefaultExtension extends MProvider {
    get baseUrl() {
        let url = "";
        try { url = String(new SharedPreferences().get("domain_url") || "").trim(); } catch (e) {}
        if (!url) url = this.source.baseUrl;
        return url.endsWith("/") ? url.slice(0, -1) : url;
    }

    getHeaders(url) { return { Referer: this.baseUrl + "/" }; }

    mangaPath() {
        const special = { "Scan VF": "", "Read Comics Online": "comic" };
        return this.source.name in special ? special[this.source.name] : "manga";
    }

    absolute(url) {
        if (!url) return "";
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("http")) return url;
        return this.baseUrl + (url.startsWith("/") ? "" : "/") + url;
    }

    // The site's own cover, or the CMS's standard cover path for the title.
    guessCover(mangaUrl, url) {
        if (!url || url.endsWith("no-image.png")) {
            const slug = String(mangaUrl || "").replace(/\/+$/, "").split("/").pop();
            return `${this.baseUrl}/uploads/manga/${slug}/cover/cover_250x350.jpg`;
        }
        return this.absolute(url);
    }

    async html(url) {
        const res = await new Client().get(url, this.getHeaders());
        return new Document(res.body);
    }

    fromElement(el) {
        const anchor = el.selectFirst(".media-heading a, .manga-heading a");
        if (!__exists(anchor)) return null;
        const link = anchor.getHref;
        const img = el.selectFirst("img");
        return {
            name: anchor.text.trim(),
            imageUrl: this.guessCover(link, __exists(img) ? img.getSrc : ""),
            link: link,
        };
    }

    async getPopular(page) {
        const doc = await this.html(`${this.baseUrl}/filterList?page=${page}&sortBy=views&asc=false`);
        const list = doc.select("div.chapter-container, div.media").map((e) => this.fromElement(e)).filter((m) => m);
        return { list: list, hasNextPage: list.length > 0 };
    }

    async getLatestUpdates(page) {
        const doc = await this.html(`${this.baseUrl}/latest-release?page=${page}`);
        const seen = new Set();
        const list = [];
        for (const el of doc.select("div.mangalist div.manga-item")) {
            const manga = this.fromElement(el);
            if (manga && !seen.has(manga.link)) {
                seen.add(manga.link);
                list.push(manga);
            }
        }
        return { list: list, hasNextPage: list.length > 0 };
    }

    async search(query, page, filters) {
        if (!query || page > 1) return { list: [], hasNextPage: false };
        const res = await new Client().get(
            `${this.baseUrl}/search?query=${encodeURIComponent(query)}`, this.getHeaders());
        const suggestions = (JSON.parse(res.body) || {}).suggestions || [];
        const sub = this.mangaPath();
        const list = suggestions.map((s) => {
            const path = sub ? `${sub}/${s.data}` : s.data;
            return { name: s.value, link: `${this.baseUrl}/${path}`, imageUrl: this.guessCover("/" + path, "") };
        });
        return { list: list, hasNextPage: false };
    }

    ownText(el) {
        let text = el.text;
        for (const child of el.children) text = text.replace(child.text, "");
        return text.trim();
    }

    async getDetail(url) {
        const doc = await this.html(this.absolute(url));
        const manga = { chapters: [] };
        const heading = doc.selectFirst(".panel-heading, .listmanga-header, .widget-title");
        const title = __exists(heading) ? heading.text.trim() : "";
        manga.name = title;
        const cover = doc.selectFirst(".row img.img-responsive");
        manga.imageUrl = this.guessCover(url, __exists(cover) ? cover.getSrc : "");
        const well = doc.selectFirst(".row .well");
        if (__exists(well)) {
            let text = well.text;
            for (const h of well.select("h5")) text = text.replace(h.text, "");
            manga.description = text.replace(/\n{3,}/g, "\n\n").trim();
        }
        for (const el of doc.select(".panel-body h3, .row .dl-horizontal dt")) {
            const label = this.ownText(el).toLowerCase().replace(/ :$/, "").trim();
            let value = el.selectFirst("div.text");
            if (!__exists(value) || !value.text.trim()) value = el.nextElementSibling;
            if (!__exists(value)) continue;
            if (__AUTHOR.includes(label)) manga.author = value.text.trim();
            else if (__ARTIST.includes(label)) manga.artist = value.text.trim();
            else if (__GENRE.includes(label)) manga.genre = value.select("a").map((a) => a.text.trim());
            else if (__STATUS.includes(label)) {
                const status = value.text.trim().toLowerCase();
                manga.status = __ONGOING.includes(status) ? 0 : __COMPLETE.includes(status) ? 1
                    : status === "dropped" ? 3 : 5;
            }
        }
        for (const li of doc.select("ul.chapters > li:not(.btn)")) {
            const wrapper = li.selectFirst(".chapter-title-rtl");
            const anchor = __exists(wrapper) ? wrapper.selectFirst("a") : null;
            if (!__exists(anchor)) continue;
            const chapter = { name: this.chapterName(wrapper.text, title), url: anchor.getHref };
            const date = li.selectFirst(".date-chapter-title-rtl");
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

    // "Some Title 12 : The Name" → "Chapter 12: The Name".
    chapterName(name, title) {
        const text = String(name || "").trim();
        try {
            const initial = title ? text.replace(title, "Chapter") : text;
            const parts = initial.split(":");
            const first = parts[0].trim();
            if (parts.length === 1) return first;
            const rest = parts.slice(1).join(":").trim();
            return first === rest ? first : `${first}: ${rest}`;
        } catch (e) {
            return text;
        }
    }

    async getPageList(url) {
        const doc = await this.html(this.absolute(url));
        return doc.select("#all img.img-responsive[data-src]")
            .map((img) => this.absolute(img.attr("data-src").trim()))
            .filter((u) => u);
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
