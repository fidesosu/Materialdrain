package tools.senko.materialdrain.provider.webdav

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

private const val DAV_NS = "DAV:"

/** One `<D:response>` entry of a PROPFIND multistatus body, normalized across server quirks. [href] is kept URL-encoded, as the server sent it. */
internal data class WebDavEntry(
    val href: String,
    val isCollection: Boolean,
    val contentLength: Long?,
    val lastModified: String?,
    val contentType: String?,
    val etag: String?
)

/**
 * Parses a WebDAV `multistatus` (PROPFIND) response body. Namespace-aware so it doesn't care which
 * prefix a server uses for `DAV:` (`d:`, `D:`, `lp1:`, unprefixed with a default namespace, ...).
 */
internal object WebDavXml {

    fun parseMultistatus(xml: String): List<WebDavEntry> {
        val document = try {
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        } catch (_: Exception) {
            return emptyList()
        }
        val responses = document.getElementsByTagNameNS(DAV_NS, "response")
        return (0 until responses.length).mapNotNull { i -> parseResponse(responses.item(i) as? Element) }
    }

    private fun parseResponse(response: Element?): WebDavEntry? {
        if (response == null) return null
        val href = firstChildText(response, "href") ?: return null
        val propstats = response.getElementsByTagNameNS(DAV_NS, "propstat")
        // Prefer the propstat whose status is 200 OK; servers that only ever send one still work since
        // that one is picked when no 200 is found.
        val prop = (0 until propstats.length)
            .mapNotNull { i -> propstats.item(i) as? Element }
            .let { all ->
                all.firstOrNull { firstChildText(it, "status")?.contains(" 200 ") == true } ?: all.firstOrNull()
            }
            ?.let { firstChildElement(it, "prop") } ?: return WebDavEntry(href, false, null, null, null, null)

        val resourceType = firstChildElement(prop, "resourcetype")
        val isCollection = resourceType != null && firstChildElement(resourceType, "collection") != null
        return WebDavEntry(
            href = href,
            isCollection = isCollection,
            contentLength = firstChildText(prop, "getcontentlength")?.toLongOrNull(),
            lastModified = firstChildText(prop, "getlastmodified"),
            contentType = firstChildText(prop, "getcontenttype"),
            etag = firstChildText(prop, "getetag")
        )
    }

    private fun firstChildElement(parent: Element, localName: String): Element? {
        val children = parent.getElementsByTagNameNS(DAV_NS, localName)
        for (i in 0 until children.length) {
            val candidate = children.item(i) as? Element ?: continue
            // getElementsByTagNameNS is recursive; only direct children of `parent` count here, so a
            // `<resourcetype>` nested under a different `<prop>` (e.g. a nested propstat) isn't picked up.
            if (candidate.parentNode === parent) return candidate
        }
        return null
    }

    private fun firstChildText(parent: Element, localName: String): String? =
        firstChildElement(parent, localName)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
}
