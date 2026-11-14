package org.nekomanga.shared.extensions

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.evaluate
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.nekomanga.shared.net.BeakoJson
import org.nekomanga.shared.net.get
import org.nekomanga.shared.net.items
import org.nekomanga.shared.net.str

/** A source's script failed, or answered something Beako can't use. */
class ExtensionError(source: String, message: String, cause: Throwable? = null) :
    IOException("$source: $message", cause)

/** What a script's `Client` sends; implemented with OkHttp in [ExtensionHttp]. */
fun interface ExtensionTransport {
    /** Returns Mangayomi's response JSON: body, statusCode, headers and request.url. */
    suspend fun send(request: ExtensionRequest): ExtensionResponse
}

data class ExtensionRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    /** A string, a JSON object or array, or null. */
    val body: JsonElement?,
    val followRedirects: Boolean = true,
)

data class ExtensionResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String>,
    val finalUrl: String,
)

/**
 * One installed JavaScript source, running in its own QuickJS engine. QuickJS has no file, process
 * or network access of its own: the script can only call the functions Beako defines (HTTP through
 * [transport], HTML parsing, a few helpers and its own settings). Each engine runs on one thread
 * with a memory cap, and every call has a time limit.
 */
class JsSource(
    val info: ExtensionInfo,
    private val code: String,
    private val transport: ExtensionTransport,
    private val preferences: () -> Map<String, String> = { emptyMap() },
    private val onPreferenceSet: (String, String) -> Unit = { _, _ -> },
    private val callTimeoutMillis: Long = 90_000,
) : Closeable {
    private val thread = Executors.newSingleThreadExecutor { r ->
        Thread(r, "beako-ext-${info.id}").apply { isDaemon = true }
    }
    private val dispatcher: CoroutineDispatcher = thread.asCoroutineDispatcher()
    private val mutex = Mutex()
    private val dom = HostDom()
    private var engine: QuickJs? = null
    private var defaults: Map<String, JsonElement> = emptyMap()

    suspend fun popular(page: Int): ExtensionPage = pageOf(call("getPopular($page)"))

    suspend fun latest(page: Int): ExtensionPage = pageOf(call("getLatestUpdates($page)"))

    suspend fun search(query: String, page: Int = 1): ExtensionPage =
        pageOf(call("search(${quote(query)},$page,extention.getFilterList())"))

    suspend fun detail(url: String): ExtensionDetail {
        val json = call("getDetail(${quote(url)})")
        val chapters =
            (json["chapters"] ?: json["episodes"]).items().mapNotNull { c ->
                val curl = c.str("url") ?: return@mapNotNull null
                ExtensionChapter(
                    name = c.str("name").orEmpty().trim(),
                    url = curl,
                    dateUpload = c.str("dateUpload")?.toLongOrNull(),
                    scanlator = c.str("scanlator")?.takeIf { it.isNotBlank() },
                )
            }
        return ExtensionDetail(
            name = json.str("name"),
            imageUrl = json.str("imageUrl"),
            description = json.str("description"),
            author = json.str("author"),
            genres = json["genre"].items().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
            chapters = chapters,
        )
    }

    suspend fun pageList(url: String): List<ExtensionPageImage> {
        val headers = runCatching { headersFor(url) }.getOrDefault(emptyMap())
        return call("getPageList(${quote(url)})").items().mapNotNull { page ->
            when (page) {
                is JsonPrimitive ->
                    page.contentOrNull?.trim()?.let { ExtensionPageImage(it, headers) }
                is JsonObject -> {
                    val pageUrl = page.str("url")?.trim() ?: return@mapNotNull null
                    val own =
                        (page["headers"] as? JsonObject)?.mapValues { (_, v) ->
                            (v as? JsonPrimitive)?.content.orEmpty()
                        }
                    ExtensionPageImage(pageUrl, own ?: headers)
                }
                else -> null
            }
        }
    }

    /** The headers the source wants on its requests and images. */
    suspend fun headersFor(url: String): Map<String, String> {
        val json = call("getHeaders(${quote(url)})", async = false)
        return (json as? JsonObject)
            ?.mapValues { (_, v) -> (v as? JsonPrimitive)?.content.orEmpty() }
            .orEmpty()
    }

    /** The source's own settings, as declared by its script. */
    suspend fun preferenceDefinitions(): List<JsonElement> =
        call("getSourcePreferences()", async = false).items()

    private fun pageOf(json: JsonElement?): ExtensionPage =
        ExtensionPage(
            titles =
                json["list"].items().mapNotNull { m ->
                    val link = m.str("link") ?: return@mapNotNull null
                    ExtensionManga(m.str("name").orEmpty().trim(), link, m.str("imageUrl"))
                },
            hasNextPage = (json["hasNextPage"] as? JsonPrimitive)?.booleanOrNull ?: false,
        )

    private suspend fun call(expression: String, async: Boolean = true): JsonElement =
        mutex.withLock {
            withContext(dispatcher) {
                val js = engine ?: start()
                try {
                    val code =
                        if (async) "await jsonStringify(() => extention.$expression)"
                        else "JSON.stringify(extention.$expression)"
                    val text = withTimeout(callTimeoutMillis) { js.evaluate<Any?>(code) } as? String
                    text?.let { BeakoJson.parseToJsonElement(it) } ?: JsonNull
                } catch (e: QuickJsException) {
                    throw ExtensionError(info.name, e.message ?: "the script failed", e)
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    throw ExtensionError(info.name, "took too long to answer", e)
                } catch (e: kotlinx.serialization.SerializationException) {
                    throw ExtensionError(info.name, "returned something Beako can’t read", e)
                } finally {
                    dom.clear()
                }
            }
        }

    private suspend fun start(): QuickJs {
        val js = QuickJs.create(dispatcher)
        js.memoryLimit = 128L * 1024 * 1024
        js.maxStackSize = 2L * 1024 * 1024
        js.function("__send") { args ->
            send(args.getOrNull(0) as? String, args.getOrNull(1) as? String)
        }
        js.asyncFunction("__http") { args -> http(args.getOrNull(0) as? String) }
        try {
            js.evaluate<Any?>(prelude(sourceJson()))
            js.evaluate<Any?>(code + "\nvar extention = new DefaultExtension();\n")
            defaults =
                runCatching {
                        val text =
                            js.evaluate<Any?>("JSON.stringify(extention.getSourcePreferences())")
                                as? String
                        BeakoJson.parseToJsonElement(text ?: "[]").items().associate { pref ->
                            pref.str("key").orEmpty() to defaultOf(pref)
                        }
                    }
                    .getOrDefault(emptyMap())
        } catch (e: QuickJsException) {
            js.close()
            throw ExtensionError(info.name, "couldn’t load: ${e.message}", e)
        }
        engine = js
        return js
    }

    private fun sourceJson(): String = buildJsonObject {
        put("id", info.id)
        put("name", info.name)
        put("lang", info.lang)
        put("baseUrl", info.baseUrl)
        put("apiUrl", info.apiUrl)
        put("iconUrl", info.iconUrl)
        put("dateFormat", info.dateFormat)
        put("dateFormatLocale", info.dateFormatLocale)
        put("additionalParams", info.additionalParams)
        put("isManga", true)
        put("typeSource", info.typeSource)
        put("version", info.version)
    }
        .toString()

    /** The synchronous host functions; every argument list arrives as JSON. */
    private fun send(name: String?, argsJson: String?): Any? {
        val args = runCatching {
            BeakoJson.parseToJsonElement(argsJson ?: "[]").jsonArray
        }
            .getOrDefault(JsonArray(emptyList()))
        fun s(i: Int) = (args.getOrNull(i) as? JsonPrimitive)?.contentOrNull.orEmpty()
        fun k(i: Int): Any? = (args.getOrNull(i) as? JsonPrimitive)?.longOrNull
        fun list(values: List<Any>) =
            BeakoJson.encodeToString(
                JsonArray.serializer(),
                JsonArray(
                    values.map {
                        if (it is Number) JsonPrimitive(it) else JsonPrimitive(it.toString())
                    },
                ),
            )
        return when (name) {
            "doc_parse" -> dom.parse(s(0))
            "doc_part" -> dom.documentPart(k(0), s(1))
            "ele_string" -> dom.string(k(0), s(1))
            "select_first" -> dom.selectFirst(k(0), s(1))
            "select" -> list(dom.select(k(0), s(1)))
            "attr" -> dom.attr(k(0), s(1))
            "has_attr" -> dom.hasAttr(k(0), s(1))
            "sibling" -> dom.sibling(k(0), s(1))
            "list_by" -> list(dom.listBy(k(0), s(1), s(2)))
            "by_id" -> dom.byId(k(0), s(1))
            "xpath" -> list(dom.xpath(k(0), s(1)))
            "pref_get" -> preference(s(0))?.toString() ?: "null"
            "pref_get_string" ->
                JsonPrimitive(
                        preferences()[s(0)] ?: (args.getOrNull(1) as? JsonPrimitive)?.contentOrNull,
                    )
                    .toString()
            "pref_set_string" -> {
                onPreferenceSet(s(0), s(1))
                true
            }
            "cryptoHandler" ->
                HostUtils.cryptoHandler(
                    s(0),
                    s(1),
                    s(2),
                    (args.getOrNull(3) as? JsonPrimitive)?.booleanOrNull == true,
                )
            "encryptAESCryptoJS" -> HostUtils.encryptAESCryptoJS(s(0), s(1))
            "decryptAESCryptoJS" ->
                runCatching { HostUtils.decryptAESCryptoJS(s(0), s(1)) }.getOrDefault("")
            "decryptAESGCM" ->
                runCatching { HostUtils.decryptAESGCM(s(0), s(1), s(2), s(3)) }.getOrDefault("")
            "unpackJs" -> HostUtils.unpackJs(s(0))
            "parseDates" ->
                list(
                    HostUtils.parseDates(
                        (args.getOrNull(0) as? JsonArray)
                            ?.map { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }
                            .orEmpty(),
                        s(1),
                        s(2),
                    ),
                )
            else -> throw IllegalArgumentException("Unknown host call $name")
        }
    }

    /** A setting's value as Mangayomi gives it: the user's choice, else the script's default. */
    private fun preference(key: String): JsonElement? {
        val chosen = preferences()[key]
        val default = defaults[key]
        if (chosen == null) return default
        return when {
            default is JsonPrimitive && default.booleanOrNull != null ->
                JsonPrimitive(chosen == "true")
            default is JsonArray ->
                runCatching { BeakoJson.parseToJsonElement(chosen) }.getOrDefault(default)
            else -> JsonPrimitive(chosen)
        }
    }

    private fun defaultOf(pref: JsonElement): JsonElement {
        val obj = pref as? JsonObject ?: return JsonNull
        (obj["listPreference"] as? JsonObject)?.let { list ->
            val index = (list["valueIndex"] as? JsonPrimitive)?.longOrNull?.toInt() ?: 0
            return (list["entryValues"] as? JsonArray)?.getOrNull(index) ?: JsonNull
        }
        for (kind in listOf("checkBoxPreference", "switchPreferenceCompat", "editTextPreference")) {
            (obj[kind] as? JsonObject)?.let {
                return it["value"] ?: JsonNull
            }
        }
        (obj["multiSelectListPreference"] as? JsonObject)?.let {
            return it["values"] ?: JsonArray(emptyList())
        }
        return JsonNull
    }

    private suspend fun http(argsJson: String?): String {
        val args = BeakoJson.parseToJsonElement(argsJson ?: "[]").jsonArray
        val headers =
            (args.getOrNull(2) as? JsonObject)
                ?.mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull.orEmpty() }
                .orEmpty()
        val options = args.getOrNull(4) as? JsonObject
        val request =
            ExtensionRequest(
                method = args[0].jsonPrimitive.content,
                url = args[1].jsonPrimitive.content,
                headers = headers,
                body = args.getOrNull(3)?.takeIf { it !is JsonNull },
                followRedirects =
                    (options?.get("followRedirects") as? JsonPrimitive)?.booleanOrNull ?: true,
            )
        val response = transport.send(request)
        return buildJsonObject {
            put("body", response.body)
            put("statusCode", response.statusCode)
            put("headers", JsonObject(response.headers.mapValues { JsonPrimitive(it.value) }))
            put("isRedirect", false)
            put("reasonPhrase", "")
            put(
                "request",
                buildJsonObject {
                    put("url", response.finalUrl)
                    put("method", request.method)
                },
            )
        }
            .toString()
    }

    override fun close() {
        runCatching { kotlinx.coroutines.runBlocking(dispatcher) { engine?.close() } }
        engine = null
        thread.shutdownNow()
    }

    private fun quote(text: String) = JsonPrimitive(text).toString()
}
