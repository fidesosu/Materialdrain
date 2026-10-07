package tools.senko.materialdrain.provider.api

/** How often a transfer reports its progress: every fifth of a second, which is plenty for the screen and the notification. */
private const val PROGRESS_REPORT_INTERVAL_NANOS = 200_000_000L

/**
 * Passes on the progress of a transfer at most every fifth of a second. A fast transfer copies many chunks a second, and
 * each report updates the screen and the notification, so reporting every chunk costs far more than it shows. [finish]
 * always reports, so the last value is never lost.
 */
class ProgressThrottle(private val report: (Long) -> Unit) {
    private var lastReportNanos = 0L

    fun update(bytes: Long) {
        val now = System.nanoTime()
        if (now - lastReportNanos >= PROGRESS_REPORT_INTERVAL_NANOS) {
            lastReportNanos = now
            report(bytes)
        }
    }

    fun finish(bytes: Long) = report(bytes)
}
