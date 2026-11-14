// A minimal source in Mangayomi's format, written for Beako's tests (not a real site).
const mangayomiSources = [{ "name": "Sample", "id": 1, "lang": "en", "baseUrl": "https://sample.test" }];

class DefaultExtension extends MProvider {
  getHeaders(url) { return { "Referer": this.source.baseUrl + "/" }; }

  async getPreference(key) { return new SharedPreferences().get(key); }

  async list(path) {
    const res = await new Client().get(`${this.source.baseUrl}/${path}`, this.getHeaders());
    const doc = new Document(res.body);
    const list = doc.select("div.item").map((item) => ({
      name: item.selectFirst("h3").text,
      imageUrl: item.selectFirst("img").getSrc,
      link: item.selectFirst("a").getHref,
    }));
    return { list, hasNextPage: doc.selectFirst("a.next").className ? true : false };
  }

  async getPopular(page) { return this.list(`popular?page=${page}`); }

  async search(query, page, filters) {
    const order = await this.getPreference("pref_order");
    return this.list(`search?q=${encodeURIComponent(query)}&order=${order}&n=${filters.length}`);
  }

  async getDetail(url) {
    const res = await new Client().get(this.source.baseUrl + url, this.getHeaders());
    const doc = new Document(res.body);
    const chapters = doc.select("li.chapter a").map((a) => ({
      name: a.text, url: a.getHref, dateUpload: String(Date.UTC(2026, 0, 2)), scanlator: a.attr("data-group"),
    }));
    return {
      name: doc.selectFirst("h1").text,
      description: doc.selectFirst("meta[name='description']").attr("content"),
      genre: doc.select("span.genre").map((g) => g.text),
      chapters,
    };
  }

  async getPageList(url) {
    const res = await new Client().get(this.source.baseUrl + url);
    const doc = new Document(res.body);
    const pages = doc.select("img.page").map((img) => img.getSrc);
    pages.push({ url: "https://img.sample.test/extra.jpg", headers: { "X-Test": "1" } });
    return pages;
  }

  getFilterList() { return [{ type_name: "SelectFilter", name: "Type", state: 0, values: [] }]; }

  getSourcePreferences() {
    return [{ key: "pref_order", listPreference: { title: "Order", valueIndex: 1, entries: ["A", "B"], entryValues: ["az", "views"] } }];
  }
}
