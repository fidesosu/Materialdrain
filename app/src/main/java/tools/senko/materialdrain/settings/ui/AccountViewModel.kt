package tools.senko.materialdrain.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tools.senko.materialdrain.UiMessages
import tools.senko.materialdrain.auth.SessionManager
import tools.senko.materialdrain.provider.pixeldrain.internal.ActivityEvent
import tools.senko.materialdrain.provider.pixeldrain.internal.ApiResponse
import tools.senko.materialdrain.provider.pixeldrain.internal.FileUploadResponse
import tools.senko.materialdrain.provider.pixeldrain.internal.Invoice
import tools.senko.materialdrain.provider.pixeldrain.internal.PixeldrainUserApi
import tools.senko.materialdrain.provider.pixeldrain.internal.TimeSeries
import tools.senko.materialdrain.provider.pixeldrain.internal.Transaction
import tools.senko.materialdrain.provider.pixeldrain.internal.UserInfo
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import android.content.res.Resources
import tools.senko.materialdrain.R
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Something which is loaded from the account. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Loaded<T>(val data: T) : Loadable<T>
    /** [websiteOnly]: the server only allows it for a session made by logging in on the pixeldrain website. */
    data class Failed(val message: String, val websiteOnly: Boolean = false) : Loadable<Nothing>
}

val <T> Loadable<T>.dataOrNull: T? get() = (this as? Loadable.Loaded<T>)?.data

/** The usage the statistics chart can show. */
enum class UsageStatistic(val apiName: String, @StringRes val labelRes: Int, val inBytes: Boolean) {
    BANDWIDTH("bandwidth", R.string.settings_transfer, true),
    TRANSFER_PAID("transfer_paid", R.string.settings_premium, true),
    VIEWS("views", R.string.files_views, false),
    DOWNLOADS("downloads", R.string.files_downloads, false)
}

/** The statistic's name in the app's language. */
val UsageStatistic.label: String
    @Composable get() = stringResource(labelRes)

/** How far back the statistics chart goes, and how long each of its bars is. */
enum class StatsRange(@StringRes val labelRes: Int, val days: Long, val intervalMinutes: Int) {
    WEEK(R.string.settings_7_days, 7, 360),
    MONTH(R.string.settings_30_days, 30, 1440),
    QUARTER(R.string.settings_90_days, 90, 1440)
}

/** The range's name in the app's language. */
val StatsRange.label: String
    @Composable get() = stringResource(labelRes)

/** What on the account page is being changed, so its button can show it's busy. */
enum class AccountTask { PROFILE, SHARING, CREDIT, PLAN, BRANDING }

data class AccountUiState(
    /** Whether there's an API key to ask with: a login or a key entered by hand. */
    val hasKey: Boolean = false,
    val user: Loadable<UserInfo> = Loadable.Loading,
    val busy: Set<AccountTask> = emptySet(),
    val statistic: UsageStatistic = UsageStatistic.BANDWIDTH,
    val range: StatsRange = StatsRange.MONTH,
    val series: Loadable<TimeSeries> = Loadable.Loading,
    /** Another statistic or range is being loaded; [series] still holds the one before, so the chart can move to the new one. */
    val seriesLoading: Boolean = false,
    /** What [series] is of, which is what's asked for once it's loaded. */
    val seriesStatistic: UsageStatistic = UsageStatistic.BANDWIDTH,
    val seriesRange: StatsRange = StatsRange.MONTH,
    val activityMonth: YearMonth = YearMonth.now(ZoneOffset.UTC),
    val activity: Loadable<List<ActivityEvent>> = Loadable.Loading,
    val transactionsMonth: YearMonth = YearMonth.now(ZoneOffset.UTC),
    val transactions: Loadable<List<Transaction>> = Loadable.Loading,
    val invoices: Loadable<List<Invoice>> = Loadable.Loading
)

/**
 * The pixeldrain account of the Account settings: what GET /user says about it, its statistics, activity, billing and
 * every change the API allows an app to make (the password, two-factor authentication, the e-mail addresses, the API
 * keys and deleting the account are only allowed from the website, see WebsiteLink). The outcome of a change goes to
 * the snackbar (see UiMessages).
 */
class AccountViewModel(
    /** The app's texts (its messages) */
    private val resources: Resources,
    private val userApi: PixeldrainUserApi,
    private val sessionManager: SessionManager,
    private val uiMessages: UiMessages
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState(hasKey = sessionManager.currentApiKey().isNotBlank()))
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    private val links = Channel<String>(Channel.BUFFERED)
    /** Pages to open in the browser: the checkout of an invoice. */
    val openLinks: Flow<String> = links.receiveAsFlow()

    private val key get() = sessionManager.currentApiKey()

    private var watchingKey = false

    /**
     * The account page was opened: the account is loaded (again, so it's current), and from then on whenever the key
     * changes. Nothing is asked for before the page is first opened.
     */
    /** Loads the account once, for something which shows it outside its page (the settings' account tile). */
    fun ensureLoaded() {
        if (!watchingKey) onPageShown()
    }

    fun onPageShown() {
        if (watchingKey) {
            refresh()
            return
        }
        watchingKey = true
        // A login, a logout or a saved key is another account (or none): everything is loaded again
        viewModelScope.launch {
            sessionManager.apiKey.collect { apiKey ->
                if (apiKey.isBlank()) {
                    refreshJob?.cancel()
                    _uiState.value = AccountUiState(hasKey = false)
                } else {
                    // Nothing of the account before is shown for this one
                    refreshJob?.cancel()
                    _uiState.value = AccountUiState(hasKey = true)
                    refresh()
                }
            }
        }
    }

    private var refreshJob: Job? = null

    /** Loads the account and everything shown about it. */
    fun refresh() {
        if (key.isBlank()) return
        refreshJob?.cancel()
        _uiState.update { it.copy(hasKey = true) }
        refreshJob = viewModelScope.launch {
            launch { loadUser() }
            launch { loadSeries() }
            launch { loadActivity() }
            launch { loadTransactions() }
            launch { loadInvoices() }
        }
    }

    private suspend fun loadUser(quietly: Boolean = false) {
        if (!quietly) _uiState.update { it.copy(user = if (it.user is Loadable.Loaded) it.user else Loadable.Loading) }
        val result = userApi.getUser(key).toLoadable()
        // A failed reload after a change keeps what is shown
        if (quietly && result is Loadable.Failed) return
        _uiState.update { it.copy(user = result) }
    }

    // ---- Statistics ----

    fun setStatistic(statistic: UsageStatistic) {
        if (statistic == _uiState.value.statistic) return
        _uiState.update { it.copy(statistic = statistic) }
        viewModelScope.launch { loadSeries() }
    }

    fun setRange(range: StatsRange) {
        if (range == _uiState.value.range) return
        _uiState.update { it.copy(range = range) }
        viewModelScope.launch { loadSeries() }
    }

    private var seriesJob: Job? = null

    private suspend fun loadSeries() {
        seriesJob?.cancel()
        val state = _uiState.value
        _uiState.update { it.copy(seriesLoading = true) }
        val end = Instant.now().truncatedTo(ChronoUnit.HOURS)
        val start = end.minus(state.range.days, ChronoUnit.DAYS)
        val iso = DateTimeFormatter.ISO_INSTANT
        val job = viewModelScope.launch {
            val result = userApi.getTimeSeries(key, state.statistic.apiName, iso.format(start), iso.format(end), state.range.intervalMinutes).toLoadable()
            // Only the answer for what is still asked for
            if (_uiState.value.statistic == state.statistic && _uiState.value.range == state.range) _uiState.update {
                it.copy(series = result, seriesLoading = false, seriesStatistic = state.statistic, seriesRange = state.range)
            }
        }
        seriesJob = job
        job.join()
    }

    // ---- Activity and transactions, a month at a time ----

    fun showActivityMonth(month: YearMonth) {
        if (month.isAfter(YearMonth.now(ZoneOffset.UTC))) return
        _uiState.update { it.copy(activityMonth = month) }
        viewModelScope.launch { loadActivity() }
    }

    fun showTransactionsMonth(month: YearMonth) {
        if (month.isAfter(YearMonth.now(ZoneOffset.UTC))) return
        _uiState.update { it.copy(transactionsMonth = month) }
        viewModelScope.launch { loadTransactions() }
    }

    private suspend fun loadActivity() {
        val month = _uiState.value.activityMonth
        _uiState.update { it.copy(activity = Loadable.Loading) }
        val result = userApi.getActivity(key, month.toString()).toLoadable()
        if (_uiState.value.activityMonth == month) _uiState.update { it.copy(activity = result.map { list -> list.sortedByDescending(ActivityEvent::time) }) }
    }

    private suspend fun loadTransactions() {
        val month = _uiState.value.transactionsMonth
        _uiState.update { it.copy(transactions = Loadable.Loading) }
        val result = userApi.getTransactions(key, month.toString()).toLoadable()
        if (_uiState.value.transactionsMonth == month) _uiState.update { it.copy(transactions = result.map { list -> list.sortedByDescending(Transaction::time) }) }
    }

    private suspend fun loadInvoices() {
        val result = userApi.getInvoices(key).toLoadable()
        _uiState.update { it.copy(invoices = result.map { list -> list.sortedByDescending(Invoice::time) }) }
    }

    // ---- Profile ----

    fun changeUsername(username: String) = change(AccountTask.PROFILE, mapOf("username" to username.trim()), resources.getString(R.string.settings_username_changed_to, username.trim()))

    // ---- Sharing ----

    fun setHotlinking(enabled: Boolean) {
        updateShown { it.copy(hotlinkingEnabled = enabled) }
        change(AccountTask.SHARING, mapOf("hotlinking_enabled" to enabled.toString()), null)
    }

    fun setSkipFileViewer(enabled: Boolean) {
        updateShown { it.copy(skipFileViewer = enabled) }
        change(AccountTask.SHARING, mapOf("skip_file_viewer" to enabled.toString()), null)
    }

    /** [domains] separated by spaces, commas or new lines. */
    fun setEmbedDomains(domains: String) {
        val list = domains.split(' ', ',', '\n').map(String::trim).filter(String::isNotEmpty).joinToString(" ")
        change(AccountTask.SHARING, mapOf("embed_domains" to list), if (list.isEmpty()) resources.getString(R.string.settings_no_site_can_embed_your) else resources.getString(R.string.settings_embed_domains_saved))
    }

    /** [bytes] 0 for no cap of your own. */
    fun setTransferCap(bytes: Long) =
        change(AccountTask.SHARING, mapOf("transfer_cap" to bytes.toString()), if (bytes == 0L) resources.getString(R.string.settings_transfer_cap_removed) else resources.getString(R.string.settings_transfer_cap_saved))

    fun setAffiliate(username: String) =
        change(AccountTask.SHARING, mapOf("affiliate_user_name" to username.trim()), if (username.isBlank()) resources.getString(R.string.settings_affiliate_removed) else resources.getString(R.string.settings_affiliate_saved))

    // ---- Plan and credit ----

    /** [plan] is prepaid or prepaid_lite. */
    fun activatePlan(plan: String, label: String) = change(AccountTask.PLAN, mapOf("subscription" to plan), resources.getString(R.string.settings_is_now_your_plan, label))

    /** Makes an invoice and opens its checkout in the browser. */
    fun addCredit(amountEur: Long, network: String, country: String) {
        if (AccountTask.CREDIT in _uiState.value.busy) return
        _uiState.update { it.copy(busy = it.busy + AccountTask.CREDIT) }
        viewModelScope.launch {
            when (val response = userApi.createInvoice(key, amountEur * 1_000_000, network, country.trim().uppercase())) {
                is ApiResponse.Success -> {
                    response.data.checkoutUrl.takeIf { it.isNotBlank() }?.let { links.send(it) }
                    // Remembered for the next time, as the website does
                    userApi.updateUser(key, buildMap {
                        put("checkout_country", country.trim().uppercase())
                        put("checkout_provider", network)
                    })
                    uiMessages.show(resources.getString(R.string.settings_credit_is_added_once_paid), long = true)
                    loadInvoices()
                    loadUser(quietly = true)
                }
                is ApiResponse.Error -> uiMessages.show(describe(response.errorDetails, resources), long = true)
            }
            _uiState.update { it.copy(busy = it.busy - AccountTask.CREDIT) }
        }
    }

    fun payInvoice(invoiceId: String) {
        viewModelScope.launch {
            when (val response = userApi.getInvoiceCheckoutUrl(key, invoiceId)) {
                is ApiResponse.Success -> links.send(response.data)
                is ApiResponse.Error -> uiMessages.show(describe(response.errorDetails, resources), long = true)
            }
            // An invoice whose payment is gone is cancelled by asking for it
            loadInvoices()
        }
    }

    // ---- File viewer branding ----

    fun saveBranding(fields: Map<String, String>) = perform(AccountTask.BRANDING, { userApi.setFileCustomization(key, fields) }) {
        uiMessages.show(resources.getString(R.string.settings_file_viewer_saved))
    }

    // ---- Helpers ----

    /** Shows a change at once, before the server confirms it (a switch). */
    private fun updateShown(change: (UserInfo) -> UserInfo) = _uiState.update { state ->
        val user = state.user.dataOrNull ?: return@update state
        state.copy(user = Loadable.Loaded(change(user)))
    }

    /** PUT /user with [fields], then the account again; [success] for the snackbar. */
    private fun change(task: AccountTask, fields: Map<String, String>, success: String?) =
        perform(task, { userApi.updateUser(key, fields) }) { success?.let { uiMessages.show(it) } }

    /**
     * Runs a change of [task], one at a time, and reads the account again afterwards: also after a failure,
     * so a switch which was turned before the server said no shows the real setting again.
     */
    private fun perform(
        task: AccountTask,
        request: suspend () -> ApiResponse<FileUploadResponse>,
        onSuccess: suspend () -> Unit
    ) {
        if (task in _uiState.value.busy) return
        _uiState.update { it.copy(busy = it.busy + task) }
        viewModelScope.launch {
            when (val response = request()) {
                is ApiResponse.Success -> onSuccess()
                is ApiResponse.Error -> uiMessages.show(describe(response.errorDetails, resources), long = true)
            }
            if (key.isNotBlank()) loadUser(quietly = true)
            _uiState.update { it.copy(busy = it.busy - task) }
        }
    }

    private fun <T> ApiResponse<T>.toLoadable(): Loadable<T> = when (this) {
        is ApiResponse.Success -> Loadable.Loaded(data)
        is ApiResponse.Error -> Loadable.Failed(describe(errorDetails, resources), websiteOnly = errorDetails.value == WEBSITE_LOGIN_REQUIRED)
    }

    private fun <T, R> Loadable<T>.map(transform: (T) -> R): Loadable<R> = when (this) {
        is Loadable.Loaded -> Loadable.Loaded(transform(data))
        is Loadable.Failed -> this
        Loadable.Loading -> Loadable.Loading
    }
}

const val WEBSITE_LOGIN_REQUIRED = "website_login_required"

/** What an error of the user API means, in the words of the account page. */
internal fun describe(error: FileUploadResponse, resources: Resources): String = when (error.value) {
    WEBSITE_LOGIN_REQUIRED -> resources.getString(R.string.settings_only_possible_on_pixeldrain_com)
    "api_key_missing" -> resources.getString(R.string.settings_log_in_or_enter_an)
    "authentication_failed", "unauthorized" -> resources.getString(R.string.settings_the_api_key_isn_t)
    "username_already_registered" -> resources.getString(R.string.settings_that_username_is_already_taken)
    "email_address_already_registered" -> resources.getString(R.string.settings_another_account_already_uses_that)
    "invalid_email_address" -> resources.getString(R.string.settings_that_e_mail_address_isn)
    "too_many_email_addresses" -> resources.getString(R.string.settings_an_account_can_have_at)
    "login_link_already_sent" -> resources.getString(R.string.settings_a_link_was_already_sent)
    "last_email_address" -> resources.getString(R.string.settings_an_account_needs_at_least)
    "not_enough_credit_for_prepaid" -> resources.getString(R.string.settings_a_prepaid_plan_needs_at)
    "invalid_domain_name" -> resources.getString(R.string.settings_each_embed_domain_needs_a)
    "user_not_found" -> resources.getString(R.string.settings_no_account_has_that_username)
    "otp_incorrect" -> resources.getString(R.string.settings_that_code_isn_t_correct)
    "app_name_reserved" -> resources.getString(R.string.settings_that_name_is_reserved_for)
    "branding_image_not_found", "not_found" -> resources.getString(R.string.settings_one_of_the_images_wasn)
    "invalid_file_type" -> resources.getString(R.string.settings_images_need_to_be_jpeg)
    "file_too_large" -> resources.getString(R.string.settings_images_can_be_at_most)
    "invalid_link" -> "Links need to start with https://."
    else -> error.message?.takeIf { it.isNotBlank() } ?: error.value ?: resources.getString(R.string.settings_something_went_wrong)
}
