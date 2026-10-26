package tools.senko.materialdrain.settings.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import okhttp3.OkHttpClient
import tools.senko.materialdrain.UiMessages
import tools.senko.materialdrain.auth.SessionManager
import tools.senko.materialdrain.provider.ConfigSource
import tools.senko.materialdrain.provider.ProviderConfigStore
import tools.senko.materialdrain.provider.ProviderRegistry
import tools.senko.materialdrain.provider.ProviderUpdater
import tools.senko.materialdrain.provider.api.Credentials
import tools.senko.materialdrain.provider.pixeldrain.PixeldrainStorageProvider
import tools.senko.materialdrain.provider.pixeldrain.internal.ActivityEvent
import tools.senko.materialdrain.provider.pixeldrain.internal.ApiResponse
import tools.senko.materialdrain.provider.pixeldrain.internal.CreatedInvoice
import tools.senko.materialdrain.provider.pixeldrain.internal.FileUploadResponse
import tools.senko.materialdrain.provider.pixeldrain.internal.Invoice
import tools.senko.materialdrain.provider.pixeldrain.internal.LoginResult
import tools.senko.materialdrain.provider.pixeldrain.internal.LoginSession
import tools.senko.materialdrain.provider.pixeldrain.internal.PixeldrainUserApi
import tools.senko.materialdrain.provider.pixeldrain.internal.Subscription
import tools.senko.materialdrain.provider.pixeldrain.internal.TimeSeries
import tools.senko.materialdrain.provider.pixeldrain.internal.TotpSecret
import tools.senko.materialdrain.provider.pixeldrain.internal.Transaction
import tools.senko.materialdrain.provider.pixeldrain.internal.UserFilesListResponse
import tools.senko.materialdrain.provider.pixeldrain.internal.UserInfo
import tools.senko.materialdrain.provider.pixeldrain.internal.UserListsResponse
import tools.senko.materialdrain.settings.AppSettings
import java.time.Instant
import java.time.temporal.ChronoUnit

/*
 * The settings in a preview (in Android Studio, see SettingsScreen.kt's previews): the app's own settings and ViewModels,
 * with a made-up account behind them instead of Pixeldrain, and a few of the example hosts. Nothing here runs in the app.
 */

/**
 * What the settings' items read and change, for a preview: signed in to the made-up account ([PreviewUserApi]) or not.
 * The settings have their defaults (a preview keeps nothing), the hosts are Pixeldrain and a few of the bundled examples.
 */
@Composable
internal fun rememberPreviewSettings(signedIn: Boolean = true): SettingsEnvironment {
    val context = LocalContext.current
    return remember(signedIn) {
        val session = SessionManager(context, previewUser = if (signedIn) "sam" else null)
        val configStore = ProviderConfigStore(context).also { importExampleHosts(context, it) }
        val pixeldrain = PixeldrainStorageProvider(apiKeyProvider = { session.currentApiKey() })
        val registry = ProviderRegistry(configStore, pixeldrain) { Credentials(apiKey = session.currentApiKey()) }
        SettingsEnvironment(
            appSettings = AppSettings(context),
            authViewModel = AuthViewModel(context.resources, PreviewUserApi, session),
            accountViewModel = AccountViewModel(context.resources, PreviewUserApi, session, UiMessages()),
            apiKeyInput = "",
            onApiKeyInputChange = {},
            providerSettingsViewModel = ProviderSettingsViewModel(context.resources, configStore, registry, ProviderUpdater(context.resources, configStore, OkHttpClient(), appVersion = 0))
        )
    }
}

/** The bundled examples (docs/provider-configs) the Hosts page shows in a preview; one that can't be read is left out. */
private val ExampleHostFiles = listOf("webdav.json", "smb.json", "s3.json")

private fun importExampleHosts(context: Context, store: ProviderConfigStore) {
    if (store.providers.value.isNotEmpty()) return
    ExampleHostFiles.forEach { file ->
        val text = runCatching { context.assets.open(file).bufferedReader().use { it.readText() } }.getOrNull() ?: return@forEach
        ConfigSource.of(text)?.let { store.import(it) }
    }
}

/** A made-up time, [days] ago, as the API writes its times. */
private fun daysAgo(days: Long): String = Instant.now().minus(days, ChronoUnit.DAYS).toString()

private const val GB = 1_000_000_000L

/**
 * Pixeldrain's account API for the previews: a made-up account on a paid plan, with some use, statistics, activity and
 * billing. Every change succeeds without changing anything.
 */
private object PreviewUserApi : PixeldrainUserApi {
    private val done = ApiResponse.Success(FileUploadResponse(success = true))

    private val user = UserInfo(
        id = "preview",
        username = "sam",
        emailAddresses = listOf("sam@example.com"),
        otpEnabled = true,
        subscription = Subscription(
            id = "prepaid",
            name = "Prepaid",
            type = "prepaid",
            fileSizeLimit = 100 * GB,
            storageSpace = 2_000 * GB,
            pricePerTbStorage = 4_000_000,
            pricePerTbBandwidth = 1_000_000,
            monthlyTransferCap = 2_000 * GB,
            fileViewerBranding = true,
            filesystemAccess = true
        ),
        storageSpaceUsed = 312 * GB,
        filesystemStorageUsed = 128 * GB,
        fileCount = 1_284,
        filesystemNodeCount = 9_310,
        balanceMicroEur = 12_480_000,
        monthlyTransferUsed = 640 * GB
    )

    override suspend fun login(username: String, password: String, totp: String, linkLoginUserId: String, linkLoginId: String) =
        ApiResponse.Success<LoginResult>(LoginResult.LoggedIn(LoginSession(authKey = "preview")))

    override suspend fun logout(apiKey: String) = done
    override suspend fun getUserFiles(apiKey: String) = ApiResponse.Success(UserFilesListResponse(emptyList()))
    override suspend fun getUserLists(apiKey: String) = ApiResponse.Success(UserListsResponse(emptyList()))
    override suspend fun getUser(apiKey: String) = ApiResponse.Success(user)
    override suspend fun updateUser(apiKey: String, fields: Map<String, String>) = done
    override suspend fun removeEmail(apiKey: String, email: String) = done
    override suspend fun deleteAccount(apiKey: String) = done

    /** A month of daily amounts that rise and fall, so the chart has a shape. */
    override suspend fun getTimeSeries(apiKey: String, statistic: String, start: String, end: String, intervalMinutes: Int) =
        ApiResponse.Success(
            TimeSeries(
                timestamps = (30L downTo 1L).map { daysAgo(it) },
                amounts = (0 until 30).map { day -> (8 + 6 * kotlin.math.sin(day / 3.0) + day / 4.0).toLong() * GB }
            )
        )

    override suspend fun getActivity(apiKey: String, month: String) = ApiResponse.Success(
        listOf(
            ActivityEvent(daysAgo(2), "file_instance_expired", "aBcD1234", "old-screenshot.png"),
            ActivityEvent(daysAgo(9), "file_instance_blocked", "eFgH5678", "movie.mkv", "copyright")
        )
    )

    override suspend fun getTransactions(apiKey: String, month: String) = ApiResponse.Success(
        listOf(
            Transaction(daysAgo(1), newBalance = 12_480_000, storageCharge = -41_000, storageUsed = 312 * GB, bandwidthCharge = -21_000, bandwidthUsed = 21 * GB),
            Transaction(daysAgo(12), newBalance = 13_100_000, depositAmount = 10_000_000)
        )
    )

    override suspend fun createInvoice(apiKey: String, amountMicroEur: Long, network: String, country: String, name: String) =
        ApiResponse.Success(CreatedInvoice("preview", "https://example.com/checkout"))

    override suspend fun getInvoices(apiKey: String) = ApiResponse.Success(
        listOf(Invoice("inv-1", daysAgo(12), amount = 10_000_000, vat = 2_100_000, country = "NL", paymentMethod = "card", status = "paid"))
    )

    override suspend fun getInvoiceCheckoutUrl(apiKey: String, invoiceId: String) = ApiResponse.Success("https://example.com/checkout")
    override suspend fun setFileCustomization(apiKey: String, fields: Map<String, String>) = done
    override suspend fun createSession(apiKey: String, appName: String) = ApiResponse.Success(LoginSession(authKey = "preview", appName = appName))

    override suspend fun getSessions(apiKey: String) = ApiResponse.Success(
        listOf(
            LoginSession("preview", appName = "Materialdrain", creationTime = daysAgo(40), lastUsedTime = daysAgo(0)),
            LoginSession("other", appName = "website", creationTime = daysAgo(200), lastUsedTime = daysAgo(3))
        )
    )

    override suspend fun generateTotp(apiKey: String) = ApiResponse.Success(TotpSecret("JBSWY3DPEHPK3PXP", "otpauth://totp/pixeldrain:sam?secret=JBSWY3DPEHPK3PXP"))
    override suspend fun verifyTotp(apiKey: String, secret: String, otp: String) = done
    override suspend fun disableTotp(apiKey: String) = done
}
