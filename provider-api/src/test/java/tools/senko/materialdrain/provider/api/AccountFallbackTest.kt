package tools.senko.materialdrain.provider.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The account's login may only go to Pixeldrain's own address, whatever a config says. */
class AccountFallbackTest {

    private fun config(baseUrl: String, fallback: Boolean) = GenericRestConfig(
        name = "test",
        baseUrl = baseUrl,
        endpoints = emptyMap(),
        accountFallback = fallback
    )

    @Test
    fun `pixeldrain itself may receive the account login`() {
        assertTrue(allowsAccountFallback(config("https://pixeldrain.com/api", fallback = true)))
        assertTrue(allowsAccountFallback(config("https://www.pixeldrain.com/api", fallback = true)))
    }

    @Test
    fun `another server never receives the account login, even when the config asks`() {
        assertFalse(allowsAccountFallback(config("https://pixeldrain.com.evil.example/api", fallback = true)))
        assertFalse(allowsAccountFallback(config("https://evil.example/pixeldrain.com/api", fallback = true)))
        assertFalse(allowsAccountFallback(config("https://nas.example.org/api", fallback = true)))
    }

    @Test
    fun `nothing is sent when the config does not ask for it`() {
        assertFalse(allowsAccountFallback(config("https://pixeldrain.com/api", fallback = false)))
    }

    @Test
    fun `a broken address sends nothing`() {
        assertFalse(allowsAccountFallback(config("not a url at all", fallback = true)))
    }
}
