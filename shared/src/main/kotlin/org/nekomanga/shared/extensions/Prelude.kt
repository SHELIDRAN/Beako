package org.nekomanga.shared.extensions

/**
 * The JavaScript side of the extension API, defined before a source's code runs. Adapted from
 * Mangayomi's JS bridge (lib/eval/javascript, Apache-2.0, © kodjodevf and contributors): same class
 * and method names, so Mangayomi sources run unchanged. Every call goes to Beako through two
 * functions, `__send` (synchronous) and `__http` (asynchronous); scripts have nothing else.
 */
internal fun prelude(sourceJson: String): String =
    """
var console = { log() {}, info() {}, warn() {}, error() {}, debug() {} };
function setTimeout(fn) { Promise.resolve().then(fn); return 0; }
function clearTimeout() {}
function sendMessage(name, args) { return __send(name, args); }
function __call(name, args) { return __send(name, JSON.stringify(args)); }

class Client {
    constructor(reqcopyWith) { this.reqcopyWith = reqcopyWith || null; }
    async _request(method, url, headers, body) {
        const answer = await __http(JSON.stringify(
            [method, String(url), headers || {}, body === undefined ? null : body, this.reqcopyWith]));
        return JSON.parse(answer);
    }
    head(url, headers = {}) { return this._request("HEAD", url, headers); }
    get(url, headers = {}) { return this._request("GET", url, headers); }
    post(url, headers, body) { return this._request("POST", url, headers, body); }
    put(url, headers, body) { return this._request("PUT", url, headers, body); }
    delete(url, headers, body) { return this._request("DELETE", url, headers, body); }
    patch(url, headers, body) { return this._request("PATCH", url, headers, body); }
}

class SharedPreferences {
    get(key) { return JSON.parse(__call("pref_get", [key])); }
    getString(key, defaultValue) { return JSON.parse(__call("pref_get_string", [key, defaultValue])); }
    setString(key, value) { return __call("pref_set_string", [key, String(value)]); }
}

class Element {
    constructor(key) { this.key = key; }
    getString(type) { return __call("ele_string", [this.key, type]); }
    get text() { return this.getString("text"); }
    get outerHtml() { return this.getString("outerHtml"); }
    get innerHtml() { return this.getString("innerHtml"); }
    get className() { return this.getString("className"); }
    get localName() { return this.getString("localName"); }
    get namespaceUri() { return this.getString("namespaceUri"); }
    get getSrc() { return this.getString("getSrc"); }
    get getImg() { return this.getString("getImg"); }
    get getHref() { return this.getString("getHref"); }
    get getDataSrc() { return this.getString("getDataSrc"); }
    selectFirst(selector) { return new Element(__call("select_first", [this.key, selector])); }
    select(selector) { return JSON.parse(__call("select", [this.key, selector])).map((k) => new Element(k)); }
    attr(name) { return __call("attr", [this.key, name]); }
    hasAttr(name) { return __call("has_attr", [this.key, name]); }
    getElementSibling(type) { return new Element(__call("sibling", [this.key, type])); }
    get previousElementSibling() { return this.getElementSibling("previousElementSibling"); }
    get nextElementSibling() { return this.getElementSibling("nextElementSibling"); }
    getElementsListBy(type, name) {
        return JSON.parse(__call("list_by", [this.key, type, name || ""])).map((k) => new Element(k));
    }
    get children() { return this.getElementsListBy("children"); }
    getElementsByTagName(name) { return this.getElementsListBy("getElementsByTagName", name); }
    getElementsByClassName(name) { return this.getElementsListBy("getElementsByClassName", name); }
    xpath(path) { return JSON.parse(__call("xpath", [this.key, path])); }
    xpathFirst(path) { const all = this.xpath(path); return all.length ? all[0] : ""; }
}

class Document extends Element {
    constructor(html) { super(__call("doc_parse", [html == null ? "" : String(html)])); }
    get body() { return new Element(__call("doc_part", [this.key, "body"])); }
    get head() { return new Element(__call("doc_part", [this.key, "head"])); }
    get documentElement() { return new Element(__call("doc_part", [this.key, "documentElement"])); }
    get parent() { return new Element(__call("doc_part", [this.key, "parent"])); }
    getElementById(id) { return new Element(__call("by_id", [this.key, id])); }
}

function cryptoHandler(text, iv, secretKeyString, encrypt) { return __call("cryptoHandler", [text, iv, secretKeyString, !!encrypt]); }
function encryptAESCryptoJS(plainText, passphrase) { return __call("encryptAESCryptoJS", [plainText, passphrase]); }
function decryptAESCryptoJS(encrypted, passphrase) { return __call("decryptAESCryptoJS", [encrypted, passphrase]); }
function decryptAESGCM(encrypted, keyHex, ivHex, tagHex) { return __call("decryptAESGCM", [encrypted, keyHex, ivHex, tagHex || ""]); }
function unpackJs(packedJS) { return __call("unpackJs", [packedJS]); }
function unpackJsAndCombine(scriptBlock) { return __call("unpackJs", [scriptBlock]); }
function parseDates(value, dateFormat, dateFormatLocale) {
    return JSON.parse(__call("parseDates", [value || [], dateFormat || "", dateFormatLocale || ""]));
}
function deobfuscateJsPassword() { throw new Error("deobfuscateJsPassword isn't supported in Beako"); }
async function evaluateJavascriptViaWebview() { throw new Error("Web view scripts aren't supported in Beako"); }

class MProvider {
    get source() { return $sourceJson; }
    get supportsLatest() { return true; }
    getHeaders(url) { return {}; }
    async getPopular(page) { throw new Error("getPopular not implemented"); }
    async getLatestUpdates(page) { throw new Error("getLatestUpdates not implemented"); }
    async search(query, page, filters) { throw new Error("search not implemented"); }
    async getDetail(url) { throw new Error("getDetail not implemented"); }
    async getPageList(url) { throw new Error("getPageList not implemented"); }
    getFilterList() { return []; }
    getSourcePreferences() { return []; }
}
async function jsonStringify(fn) { return JSON.stringify(await fn()); }
// A source without getFilterList (Mangafire) searches with no filters, as in Mangayomi.
function __filters() { try { return extention.getFilterList() || []; } catch (e) { return []; } }
"""
