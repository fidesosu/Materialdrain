package tools.senko.materialdrain.provider.s3

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

internal data class S3Object(val key: String, val size: Long?, val lastModified: String?, val etag: String?)

internal data class S3ListResult(
    val objects: List<S3Object>,
    val commonPrefixes: List<String>,
    val nextContinuationToken: String?
)

/**
 * Parses S3's XML responses. Namespace-unaware on purpose: every S3-compatible host (AWS, MinIO, B2, R2)
 * uses the same unprefixed tag names (`Contents`, `Key`, ...), and not asking the parser to resolve the
 * `xmlns` on the root element avoids a class of namespace-matching bugs for zero benefit here.
 */
internal object S3Xml {

    fun parseListResult(xml: String): S3ListResult? {
        val root = parseDocument(xml)?.documentElement ?: return null
        val objects = root.childElements("Contents").map { contents ->
            S3Object(
                key = contents.childText("Key").orEmpty(),
                size = contents.childText("Size")?.toLongOrNull(),
                lastModified = contents.childText("LastModified"),
                etag = contents.childText("ETag")?.trim('"')
            )
        }
        val prefixes = root.childElements("CommonPrefixes").mapNotNull { it.childText("Prefix") }
        return S3ListResult(objects, prefixes, root.childText("NextContinuationToken"))
    }

    /** The `<Error><Message>` (or `<Code>` when there's no message) of an S3 error response body. */
    fun parseErrorMessage(xml: String): String? {
        val root = parseDocument(xml)?.documentElement ?: return null
        return root.childText("Message") ?: root.childText("Code")
    }

    private fun parseDocument(xml: String) = try {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(xml)))
    } catch (_: Exception) {
        null
    }

    private fun Element.childElements(tag: String): List<Element> {
        val out = mutableListOf<Element>()
        val children = childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName == tag) out.add(node)
        }
        return out
    }

    private fun Element.childText(tag: String): String? {
        val children = getElementsByTagName(tag)
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            if (node.parentNode === this) return node.textContent?.trim()?.takeIf { it.isNotEmpty() }
        }
        return null
    }
}
