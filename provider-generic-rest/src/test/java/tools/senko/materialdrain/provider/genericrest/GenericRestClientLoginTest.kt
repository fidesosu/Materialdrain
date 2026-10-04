package tools.senko.materialdrain.provider.genericrest

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import tools.senko.materialdrain.provider.api.LoginBody
import tools.senko.materialdrain.provider.api.LoginEndpoint

/** Records exactly what [GenericRestClient.login] sends over the wire, the way Pixeldrain would see it. */
class GenericRestClientLoginTest {

    private lateinit var server: MockWebServer
    private lateinit var client: GenericRestClient

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        client = GenericRestClient(server.url("/api").toString())
    }

    @After
    fun stop() {
        server.shutdown()
    }

    @Test
    fun `a form login sends x-www-form-urlencoded with every field`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"auth_key":"abc"}"""))
        val endpoint = LoginEndpoint(
            path = "/user/login",
            body = LoginBody.FORM,
            fields = mapOf("username" to "{username}", "password" to "{password}", "app_name" to "Materialdrain"),
            token = "$.auth_key"
        )

        client.login(endpoint, "me", "secret", otp = null)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        val contentType = request.headers["Content-Type"]
        assertEquals("application/x-www-form-urlencoded", contentType?.substringBefore(';')?.trim())
        val body = request.body.readUtf8()
        assertEquals(setOf("username=me", "password=secret", "app_name=Materialdrain"), body.split('&').toSet())
    }

    @Test
    fun `the otp field is only sent once a code is provided`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"value":"otp_required"}"""))
        val endpoint = LoginEndpoint(
            path = "/user/login",
            fields = mapOf("username" to "{username}", "password" to "{password}", "totp" to "{otp}"),
            token = "$.auth_key"
        )

        client.login(endpoint, "me", "secret", otp = null)
        val firstBody = server.takeRequest().body.readUtf8()
        assertNull("totp must be absent, not empty, until a code exists", firstBody.split('&').firstOrNull { it.startsWith("totp") })

        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"auth_key":"abc"}"""))
        client.login(endpoint, "me", "secret", otp = "123456")
        val secondBody = server.takeRequest().body.readUtf8()
        assertEquals(setOf("username=me", "password=secret", "totp=123456"), secondBody.split('&').toSet())
    }
}
