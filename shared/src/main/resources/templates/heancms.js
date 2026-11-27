// HeanCMS sites (OmegaScans, YugenMangas, Perf Scan…), for Beako. Ported from Mangayomi's Dart
// template dart/manga/multisrc/heancms/heancms.dart (Apache-2.0) and brought up to date with the
// sites' current API: chapters come from /chapter/query and pages from /chapter/<series>/<chapter>
// (checked against OmegaScans, Nov 2026). Each installed source supplies its address and API.

class DefaultExtension extends MProvider {
    get api() {
        const url = this.source.apiUrl && this.source.apiUrl !== this.source.baseUrl
            ? this.source.apiUrl
            : this.source.baseUrl.replace("://", "://api.");
        return url.endsWith("/") ? url.slice(0, -1) : url;
    }

    get base() {
        const url = this.source.baseUrl;
        return url.endsWith("/") ? url.slice(0, -1) : url;
    }

    getHeaders(url) { return { Origin: this.base, Referer: this.base + "/" }; }

    async json(url) {
        const res = await new Client().get(url, this.getHeaders());
        return JSON.parse(res.body);
    }

    cover(thumbnail) {
        if (!thumbnail) return "";
        return thumbnail.startsWith("http") ? thumbnail : `${this.api}/cover/${thumbnail}`;
    }

    async query(order, page, text) {
        const url = `${this.api}/query/?page=${page}&query_string=${encodeURIComponent(text || "")}` +
            `&series_status=All&order=desc&orderBy=${order}&perPage=20&tags_ids=[]&series_type=Comic`;
        const body = await this.json(url);
        const list = (body.data || []).map((s) => ({
            name: s.title,
            imageUrl: this.cover(s.thumbnail),
            link: `/series/${s.series_slug}`,
        }));
        const meta = body.meta || {};
        return { list: list, hasNextPage: (meta.current_page || page) < (meta.last_page || page) };
    }

    async getPopular(page) { return this.query("total_views", page, ""); }

    async getLatestUpdates(page) { return this.query("latest", page, ""); }

    async search(query, page, filters) { return this.query("total_views", page, query); }

    async getDetail(url) {
        const slug = url.replace(/\/+$/, "").split("/").pop();
        const series = await this.json(`${this.api}/series/${slug}`);
        const manga = {
            name: series.title,
            imageUrl: this.cover(series.thumbnail),
            author: series.author || "",
            description: String(series.description || "").replace(/<[^>]+>/g, "").trim(),
            genre: (series.tags || []).map((t) => t.name).filter((n) => n),
            chapters: [],
        };
        // Chapters, newest first, a page at a time. Paid chapters (still locked) are left out.
        for (let page = 1; page <= 50; page++) {
            const body = await this.json(
                `${this.api}/chapter/query?page=${page}&perPage=100&series_id=${series.id}`);
            for (const c of body.data || []) {
                if (c.price && c.price > 0) continue;
                const chapter = {
                    name: c.chapter_title ? `${c.chapter_name}: ${c.chapter_title}` : c.chapter_name,
                    url: `/series/${slug}/${c.chapter_slug}`,
                };
                const time = Date.parse(c.created_at || "");
                // An unreadable date stays unknown, never "now" or 1970.
                if (!isNaN(time) && time > 0) chapter.dateUpload = String(time);
                manga.chapters.push(chapter);
            }
            const meta = body.meta || {};
            if (!meta.last_page || page >= meta.last_page) break;
        }
        return manga;
    }

    async getPageList(url) {
        const parts = url.replace(/\/+$/, "").split("/");
        const chapter = parts.pop();
        const series = parts.pop();
        try {
            const body = await this.json(`${this.api}/chapter/${series}/${chapter}`);
            const images = ((body.chapter || {}).chapter_data || {}).images || [];
            if (images.length) {
                return images.map((u) => (u.startsWith("http") ? u : `${this.api}/${u}`));
            }
        } catch (e) {}
        // Older sites: the chapter page itself lists the images.
        const res = await new Client().get(this.base + url, this.getHeaders());
        const doc = new Document(res.body);
        return doc.select("div.min-h-screen > div.container > p.items-center img, main img")
            .map((img) => img.attr("data-src") || img.attr("src"))
            .filter((u) => u && u.startsWith("http"));
    }

    getFilterList() { return []; }

    getSourcePreferences() { return []; }
}
