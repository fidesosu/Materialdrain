package tools.senko.materialdrain.provider.genericrest

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tools.senko.materialdrain.provider.api.AuthConfig
import tools.senko.materialdrain.provider.api.AuthType
import tools.senko.materialdrain.provider.api.Credentials
import tools.senko.materialdrain.provider.api.LoginEndpoint
import tools.senko.materialdrain.provider.api.OtpConfig
import tools.senko.materialdrain.provider.api.PasswordAuth
import tools.senko.materialdrain.provider.api.PasswordAuthMode
import tools.senko.materialdrain.provider.api.ResponseCondition
import tools.senko.materialdrain.provider.api.SignInResult
import java.util.Base64

class AuthHeadersTest {

    private val loginAuth = AuthConfig(
        type = AuthType.BASIC,
        passwordAuth = PasswordAuth(PasswordAuthMode.LOGIN, LoginEndpoint(path = "/user/login", token = "$.auth_key"))
    )

    private fun decodeBasic(header: String) = String(Base64.getDecoder().decode(header.removePrefix("Basic ")))

    @Test
    fun `a signed-in session wins over the api key`() {
        val plan = AuthHeaders.plan(loginAuth, Credentials(apiKey = "key", username = "me", loginToken = "session"))
        assertEquals(AuthPlan.SignedIn("session"), plan)
    }

    @Test
    fun `without a session the api key is used, a saved password never signs in by itself`() {
        assertEquals(AuthPlan.ApiKey("key"), AuthHeaders.plan(loginAuth, Credentials(apiKey = "key", username = "me", password = "secret")))
        assertEquals(AuthPlan.None, AuthHeaders.plan(loginAuth, Credentials(username = "me", password = "secret")))
    }

    @Test
    fun `basic password mode sends them directly, before the api key`() {
        val basic = AuthConfig(type = AuthType.BASIC, passwordAuth = PasswordAuth(PasswordAuthMode.BASIC))
        assertEquals(AuthPlan.PasswordBasic("me", "secret"), AuthHeaders.plan(basic, Credentials(apiKey = "key", username = "me", password = "secret")))
        assertEquals(AuthPlan.ApiKey("key"), AuthHeaders.plan(basic, Credentials(apiKey = "key", username = "me")))
    }

    @Test
    fun `credentials the host doesn't take are ignored`() {
        val keyOnly = AuthConfig(type = AuthType.BEARER)
        assertEquals(AuthPlan.ApiKey("key"), AuthHeaders.plan(keyOnly, Credentials(apiKey = "key", username = "me", password = "secret", loginToken = "session")))
        assertEquals(AuthPlan.None, AuthHeaders.plan(keyOnly, Credentials(username = "me", password = "secret")))
    }

    @Test
    fun `nothing entered means no credentials`() {
        assertEquals(AuthPlan.None, AuthHeaders.plan(loginAuth, Credentials()))
    }

    @Test
    fun `basic api key goes in the password field with the configured username`() {
        val (name, value) = AuthHeaders.forKey(AuthConfig(type = AuthType.BASIC), "abc123")!!
        assertEquals("Authorization", name)
        assertEquals(":abc123", decodeBasic(value))
        assertEquals("user:abc123", decodeBasic(AuthHeaders.forKey(AuthConfig(type = AuthType.BASIC, keyUsername = "user"), "abc123")!!.second))
    }

    @Test
    fun `bearer and custom header attach the key as-is`() {
        assertEquals("Authorization" to "Bearer abc", AuthHeaders.forKey(AuthConfig(type = AuthType.BEARER), "abc"))
        assertEquals("X-Api-Key" to "abc", AuthHeaders.forKey(AuthConfig(type = AuthType.HEADER, headerName = "X-Api-Key"), "abc"))
        assertNull(AuthHeaders.forKey(AuthConfig(type = AuthType.NONE), "abc"))
    }

    private val pixeldrainFields = mapOf("username" to "{username}", "password" to "{password}", "totp" to "{otp}", "app_name" to "Materialdrain")

    // Pixeldrain's real response shapes (docs/pixeldrain_api.txt, POST /user/login)
    private val pixeldrainLogin = LoginEndpoint(
        path = "/user/login",
        fields = pixeldrainFields,
        token = "$.auth_key",
        otp = OtpConfig(
            requiredWhen = ResponseCondition("$.value", "otp_required"),
            rejectedWhen = ResponseCondition("$.value", "otp_incorrect")
        )
    )
    private val json = Json { ignoreUnknownKeys = true }
    private fun error(value: String, message: String) =
        json.parseToJsonElement("""{"success":false,"value":"$value","message":"$message"}""")

    @Test
    fun `a password-only sign-in on a two-factor account asks for the code`() {
        val result = AuthHeaders.signInResult(pixeldrainLogin, 400, error("otp_required", "A second proof is required"), otpSent = false)
        assertEquals(SignInResult.OtpRequired(6), result)
    }

    @Test
    fun `a wrong code is rejected, whether the host says so or keeps asking`() {
        val wrong = AuthHeaders.signInResult(pixeldrainLogin, 400, error("otp_incorrect", "The code is not correct"), otpSent = true)
        assertEquals(SignInResult.OtpRejected(6, "The code is not correct"), wrong)
        val stillAsking = AuthHeaders.signInResult(pixeldrainLogin, 400, error("otp_required", "A second proof is required"), otpSent = true)
        assertTrue(stillAsking is SignInResult.OtpRejected)
    }

    @Test
    fun `success returns the session token`() {
        val body = json.parseToJsonElement("""{"auth_key":"550e8400-e29b-41d4-a716-446655440000","app_name":"Materialdrain"}""")
        assertEquals(SignInResult.Success("550e8400-e29b-41d4-a716-446655440000"), AuthHeaders.signInResult(pixeldrainLogin, 201, body, otpSent = true))
    }

    @Test
    fun `other errors fail with the host's own message`() {
        val result = AuthHeaders.signInResult(pixeldrainLogin, 400, error("password_incorrect", "The entered password is not correct"), otpSent = false)
        assertEquals(SignInResult.Failed("The entered password is not correct"), result)
    }

    @Test
    fun `otp_required is still detected when wrapped in a multiple_errors envelope`() {
        // Same shape as the missing_field response Pixeldrain's login endpoint returns for a malformed request,
        // but with otp_required as the wrapped entry instead - the detection has to look inside "errors" too,
        // not just at the top level, or a host that wraps this particular error would never show the code prompt.
        val wrapped = json.parseToJsonElement(
            """{"success":false,"value":"multiple_errors","message":"See the errors array",
                "errors":[{"success":false,"value":"otp_required","message":"A second proof of identity is required"}]}"""
        )
        assertEquals(SignInResult.OtpRequired(6), AuthHeaders.signInResult(pixeldrainLogin, 400, wrapped, otpSent = false))

        val wrappedIncorrect = json.parseToJsonElement(
            """{"value":"multiple_errors","message":"See the errors array",
                "errors":[{"value":"otp_incorrect","message":"The entered one-time password is not correct"}]}"""
        )
        val rejected = AuthHeaders.signInResult(pixeldrainLogin, 400, wrappedIncorrect, otpSent = true)
        assertEquals(SignInResult.OtpRejected(6, "The entered one-time password is not correct"), rejected)
    }

    @Test
    fun `the code field is left out until there's a code`() {
        assertEquals(
            mapOf("username" to "me", "password" to "secret", "app_name" to "Materialdrain"),
            AuthHeaders.loginFields(pixeldrainFields, "me", "secret", otp = null)
        )
        assertEquals(
            mapOf("username" to "me", "password" to "secret", "totp" to "123456", "app_name" to "Materialdrain"),
            AuthHeaders.loginFields(pixeldrainFields, "me", "secret", otp = "123456")
        )
    }
}
