package tools.senko.materialdrain.transfer


private const val MIN_SAMPLE_INTERVAL_NANOS = 400_000_000L
private const val SMOOTHING_FACTOR = 0.3

/** Smoothed transfer speed (exponential moving average). Safe to call from multiple threads. */
class TransferSpeedTracker {
    private var lastBytes = 0L
    private var lastTimeNanos = 0L
    private var smoothedBytesPerSecond = 0.0

    @Synchronized
    fun reset() {
        lastBytes = 0L
        lastTimeNanos = 0L
        smoothedBytesPerSecond = 0.0
    }

    @Synchronized
    fun update(totalBytes: Long, nowNanos: Long = System.nanoTime()): Long {
        if (lastTimeNanos == 0L) {
            lastBytes = totalBytes
            lastTimeNanos = nowNanos
            return 0L
        }
        val elapsed = nowNanos - lastTimeNanos
        if (elapsed < MIN_SAMPLE_INTERVAL_NANOS) return smoothedBytesPerSecond.toLong()

        val instantaneous = (totalBytes - lastBytes).coerceAtLeast(0L) * 1_000_000_000.0 / elapsed
        smoothedBytesPerSecond = if (smoothedBytesPerSecond == 0.0) {
            instantaneous
        } else {
            smoothedBytesPerSecond * (1 - SMOOTHING_FACTOR) + instantaneous * SMOOTHING_FACTOR
        }
        lastBytes = totalBytes
        lastTimeNanos = nowNanos
        return smoothedBytesPerSecond.toLong()
    }
}

fun estimateEtaSeconds(totalBytes: Long?, transferredBytes: Long, bytesPerSecond: Long): Long? {
    if (totalBytes == null || totalBytes <= 0 || bytesPerSecond <= 0) return null
    val remaining = (totalBytes - transferredBytes).coerceAtLeast(0L)
    return remaining / bytesPerSecond
}
