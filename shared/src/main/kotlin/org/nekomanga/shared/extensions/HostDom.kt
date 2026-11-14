package org.nekomanga.shared.extensions

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * The HTML side of the extension API: documents and elements live here (parsed with jsoup) and
 * scripts hold integer handles. Mirrors Mangayomi's `Document` / `Element` behaviour, including
 * returning an empty element (empty strings) when a selector finds nothing. Handles are dropped
 * after each extension call.
 */
internal class HostDom {
    private val elements = HashMap<Int, Element?>()
    private var next = 0

    fun clear() {
        elements.clear()
        next = 0
    }

    private fun keep(element: Element?): Int {
        next += 1
        elements[next] = element
        return next
    }

    private fun get(key: Any?): Element? = elements[(key as? Number)?.toInt() ?: return null]

    fun parse(html: String): Int = keep(Jsoup.parse(html))

    fun documentPart(doc: Any?, part: String): Int {
        val document = get(doc) as? org.jsoup.nodes.Document
        return keep(
            when (part) {
                "body" -> document?.body()
                "head" -> document?.head()
                "documentElement" -> document?.firstElementChild()
                else -> document?.parent()
            },
        )
    }

    fun string(key: Any?, kind: String): String {
        val element = get(key) ?: return ""
        return when (kind) {
            "text" -> element.text()
            "innerHtml" -> element.html()
            "outerHtml" -> element.outerHtml()
            "className" -> element.className()
            "localName" -> element.normalName()
            "namespaceUri" -> "http://www.w3.org/1999/xhtml"
            "getSrc" -> firstAttr(element, "src")
            "getImg" -> firstImg(element)
            "getHref" -> firstAttr(element, "href")
            "getDataSrc" -> firstAttr(element, "data-src")
            else -> ""
        }
    }

    fun selectFirst(key: Any?, selector: String): Int =
        keep(runCatching { get(key)?.selectFirst(selector) }.getOrNull())

    fun select(key: Any?, selector: String): List<Int> = runCatching {
        get(key)?.select(selector).orEmpty().map(::keep)
    }
        .getOrDefault(emptyList())

    fun attr(key: Any?, name: String): String = get(key)?.attr(name).orEmpty()

    fun hasAttr(key: Any?, name: String): Boolean = get(key)?.hasAttr(name) == true

    fun sibling(key: Any?, which: String): Int =
        keep(
            if (which == "previousElementSibling") get(key)?.previousElementSibling()
            else get(key)?.nextElementSibling(),
        )

    fun listBy(key: Any?, kind: String, name: String): List<Int> {
        val element = get(key) ?: return emptyList()
        val found =
            when (kind) {
                "children" -> element.children()
                "getElementsByTagName" -> element.getElementsByTag(name)
                "getElementsByClassName" -> element.getElementsByClass(name)
                else -> emptyList()
            }
        return found.map(::keep)
    }

    fun byId(key: Any?, id: String): Int = keep(get(key)?.getElementById(id))

    /**
     * XPath results as strings, as Mangayomi gives them: `…/@attr` yields attribute values,
     * `…/text()` text, anything else the elements' text.
     */
    fun xpath(key: Any?, expression: String): List<String> {
        val element = get(key) ?: return emptyList()
        return runCatching {
                when {
                    expression.contains("/@") -> {
                        val attribute = expression.substringAfterLast("/@")
                        element.selectXpath(expression.substringBeforeLast("/@")).map {
                            it.attr(attribute)
                        }
                    }
                    expression.endsWith("/text()") ->
                        element.selectXpath(expression, TextNode::class.java).map {
                            it.text().trim()
                        }
                    else -> element.selectXpath(expression).map { it.text() }
                }
            }
            .getOrDefault(emptyList())
    }

    /** Mangayomi reads these from the element's HTML, so a wrapper's child image counts too. */
    private fun firstAttr(element: Element, name: String): String =
        if (element.hasAttr(name)) element.attr(name)
        else element.selectFirst("[$name]")?.attr(name).orEmpty()

    private fun firstImg(element: Element): String {
        val img = if (element.normalName() == "img") element else element.selectFirst("img")
        return img?.let { it.attr("data-src").ifBlank { it.attr("src") } }.orEmpty()
    }
}
