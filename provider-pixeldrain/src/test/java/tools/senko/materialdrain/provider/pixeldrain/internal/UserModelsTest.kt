package tools.senko.materialdrain.provider.pixeldrain.internal

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserModelsTest {

    // The same settings as the user API reads the account's answers with
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun `GET user reads the plan, usage and settings`() {
        val user = json.decodeFromString<UserInfo>(
            """
            {
              "id": "8e08a626-040e-417e-8713-a0f43737a235",
              "username": "some_user",
              "email_addresses": ["user@example.com"],
              "email_address_pending": "",
              "can_upload": true,
              "otp_enabled": false,
              "subscription": {
                "id": "prepaid", "name": "Prepaid", "type": "prepaid",
                "file_size_limit": 107374182400, "file_expiry_days": 0, "storage_space": -1,
                "price_per_tb_storage": 4000000, "price_per_tb_bandwidth": 1000000,
                "monthly_transfer_cap": -1, "file_viewer_branding": true,
                "filesystem_access": true, "filesystem_storage_limit": -1
              },
              "storage_space_used": 1234567890,
              "file_count": 420,
              "balance_micro_eur": 12500000,
              "hotlinking_enabled": true,
              "monthly_transfer_cap": 0,
              "monthly_transfer_used": 1234567890,
              "file_viewer_branding": null,
              "file_embed_domains": "",
              "skip_file_viewer": false,
              "checkout_country": "NLD"
            }
            """
        )
        assertEquals("some_user", user.username)
        assertEquals(listOf("user@example.com"), user.emailAddresses)
        assertEquals("prepaid", user.subscription.id)
        assertEquals(-1L, user.subscription.storageSpace)
        assertEquals(12_500_000L, user.balanceMicroEur)
        assertEquals(0L, user.monthlyTransferCap)
        assertTrue(user.hotlinkingEnabled)
        assertEquals(null, user.fileViewerBranding)
    }

    @Test
    fun `nulls where a list or text is expected read as empty`() {
        val user = json.decodeFromString<UserInfo>("""{"username": "a", "email_addresses": null, "affiliate_user_name": null}""")
        assertEquals(emptyList<String>(), user.emailAddresses)
        assertEquals("", user.affiliateUserName)
    }

    @Test
    fun `activity and transactions read as lists, and null as no list`() {
        val events = json.decodeFromString<List<ActivityEvent>?>(
            """[{"time": "2024-02-04T18:34:13Z", "event": "file_instance_expired", "file_id": "1234abcd", "file_name": "screenshot.png", "file_removal_reason": "file_expired"}]"""
        )
        assertEquals("screenshot.png", events?.single()?.fileName)
        assertEquals(null, json.decodeFromString<List<Transaction>?>("null"))
    }
}
