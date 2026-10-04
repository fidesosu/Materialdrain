package tools.senko.materialdrain.provider.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavXmlTest {

    @Test
    fun `parses a multistatus response with a prefixed DAV namespace`() {
        // The shape Nextcloud/Apache mod_dav actually send: Depth 1 PROPFIND returns the folder itself first,
        // then each child, all in a propstat with a 200 OK status.
        val xml = """<?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/remote.php/dav/files/alice/Photos/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype><d:collection/></d:resourcetype>
                    <d:getlastmodified>Mon, 01 Jan 2024 00:00:00 GMT</d:getlastmodified>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/alice/Photos/IMG_001.jpg</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype/>
                    <d:getcontentlength>12345</d:getcontentlength>
                    <d:getcontenttype>image/jpeg</d:getcontenttype>
                    <d:getetag>"abc123"</d:getetag>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/alice/Photos/Vacation/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype><d:collection/></d:resourcetype>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val entries = WebDavXml.parseMultistatus(xml)
        assertEquals(3, entries.size)

        val self = entries[0]
        assertTrue(self.isCollection)
        assertEquals("/remote.php/dav/files/alice/Photos/", self.href)

        val file = entries[1]
        assertEquals(false, file.isCollection)
        assertEquals(12345L, file.contentLength)
        assertEquals("image/jpeg", file.contentType)
        assertEquals("\"abc123\"", file.etag)

        val subfolder = entries[2]
        assertTrue(subfolder.isCollection)
    }

    @Test
    fun `picks the propstat with a 200 status when a server sends several`() {
        // Some servers (notably ownCloud for ACL-restricted properties) return one 200 propstat and one
        // 404 propstat per response; only the 200 one has the properties this app reads.
        val xml = """<?xml version="1.0"?>
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/dav/file.txt</D:href>
                <D:propstat>
                  <D:prop><D:resourcetype/><D:getcontentlength>42</D:getcontentlength></D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
                <D:propstat>
                  <D:prop><D:someRestrictedProperty/></D:prop>
                  <D:status>HTTP/1.1 404 Not Found</D:status>
                </D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()

        val entries = WebDavXml.parseMultistatus(xml)
        assertEquals(1, entries.size)
        assertEquals(42L, entries[0].contentLength)
    }

    @Test
    fun `an unprefixed default DAV namespace still parses`() {
        val xml = """<?xml version="1.0"?>
            <multistatus xmlns="DAV:">
              <response>
                <href>/dav/file.txt</href>
                <propstat>
                  <prop><resourcetype/><getcontentlength>7</getcontentlength></prop>
                  <status>HTTP/1.1 200 OK</status>
                </propstat>
              </response>
            </multistatus>
        """.trimIndent()
        val entries = WebDavXml.parseMultistatus(xml)
        assertEquals(1, entries.size)
        assertEquals(7L, entries[0].contentLength)
    }

    @Test
    fun `malformed xml returns an empty list instead of throwing`() {
        assertEquals(emptyList<WebDavEntry>(), WebDavXml.parseMultistatus("not xml"))
    }
}
