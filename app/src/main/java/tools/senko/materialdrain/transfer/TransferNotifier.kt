package tools.senko.materialdrain.transfer

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import tools.senko.materialdrain.AppContainer
import tools.senko.materialdrain.R
import java.util.concurrent.atomic.AtomicInteger
import tools.senko.materialdrain.util.formatEta
import tools.senko.materialdrain.util.formatSize
import tools.senko.materialdrain.util.formatSpeed

const val CHANNEL_TRANSFER_PROGRESS = "transfer_progress"
const val CHANNEL_TRANSFER_COMPLETE = "transfer_complete"
const val CHANNEL_TRANSFER_FAILED = "transfer_failed"
private const val CHANNEL_GROUP_TRANSFERS = "transfers"

const val PROGRESS_NOTIFICATION_ID = 1
private const val FIRST_RESULT_NOTIFICATION_ID = 100

const val ACTION_CANCEL_ALL_TRANSFERS = "tools.senko.materialdrain.action.CANCEL_ALL_TRANSFERS"

/** "Try again" on a failed transfer's notification, which [EXTRA_NOTIFICATION_ID] names (see TransferRegistry.retry). */
const val ACTION_RETRY_TRANSFER = "tools.senko.materialdrain.action.RETRY_TRANSFER"
const val EXTRA_NOTIFICATION_ID = "notification_id"

/**
 * Builds the transfer notifications. Three separate channels are used so that the user can tune each
 * kind in the Android notification settings (importance, sound, vibration, lock screen, badge, ...).
 */
class TransferNotifier(private val context: Context) {

    private val resultIds = AtomicInteger(FIRST_RESULT_NOTIFICATION_ID)

    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannelGroup(NotificationChannelGroup(CHANNEL_GROUP_TRANSFERS, context.getString(R.string.upload_transfers)))

        // Silent by default: this notification is shown for as long as a transfer runs
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TRANSFER_PROGRESS, context.getString(R.string.upload_transfer_progress), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.upload_shown_while_files_are_being)
                group = CHANNEL_GROUP_TRANSFERS
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TRANSFER_COMPLETE, context.getString(R.string.upload_completed_transfers), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.upload_when_a_transfer_finishes_in)
                group = CHANNEL_GROUP_TRANSFERS
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TRANSFER_FAILED, context.getString(R.string.upload_failed_transfers), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.upload_when_a_transfer_fails_in)
                group = CHANNEL_GROUP_TRANSFERS
            }
        )
    }

    private fun openAppIntent(): PendingIntent {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent()
        return PendingIntent.getActivity(context, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun cancelAllIntent(): PendingIntent {
        val intent = Intent(context, TransferService::class.java).setAction(ACTION_CANCEL_ALL_TRANSFERS)
        return PendingIntent.getService(context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /**
     * The colour behind the notification's icon: the system accent on Android 12 and up, the pixeldrain green before
     * (see pixeldrain_icon), instead of the system's dark default.
     */
    private fun iconColor(): Int = ContextCompat.getColor(context, R.color.pixeldrain_icon)

    /**
     * While the app lock is on, what [builder] shows on a secure lock screen: only [summary] (with no file names), until
     * the device is unlocked. Without the lock, as the system's own setting has it.
     */
    private fun privateOnLockScreen(builder: NotificationCompat.Builder, channel: String, icon: Int, summary: String) = builder.apply {
        val app = context.applicationContext as Application
        if (!AppContainer.get(app).appSettings.biometricLock.value) return@apply
        setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        setPublicVersion(
            NotificationCompat.Builder(context, channel).setColor(iconColor()).setSmallIcon(icon).setContentTitle(summary).build()
        )
    }

    /** The ongoing notification of the foreground service, summarising every running transfer. */
    fun buildProgressNotification(transfers: List<TransferInfo>): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_TRANSFER_PROGRESS)
            .setColor(iconColor())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())
            .addAction(0, context.getString(R.string.browser_cancel), cancelAllIntent())

        if (transfers.isEmpty()) {
            return builder
                .setSmallIcon(R.drawable.icon_upload)
                .setContentTitle(context.getString(R.string.upload_finishing_transfers))
                .setProgress(0, 0, true)
                .build()
        }

        val onlyDownloads = transfers.all { it.kind == TransferKind.DOWNLOAD }
        val onlyUploads = transfers.all { it.kind == TransferKind.UPLOAD }
        val title = if (transfers.size == 1) {
            val transfer = transfers.first()
            (if (transfer.kind == TransferKind.UPLOAD) context.getString(R.string.upload_uploading) else context.getString(R.string.upload_downloading)) + transfer.title
        } else {
            when {
                onlyUploads -> context.getString(R.string.upload_uploading_items, transfers.size)
                onlyDownloads -> context.getString(R.string.upload_downloading_items, transfers.size)
                else -> context.getString(R.string.upload_transfers_running, transfers.size)
            }
        }

        val transferred = transfers.sumOf { it.transferredBytes }
        // A total is only meaningful when every transfer knows its own size
        val total = if (transfers.all { (it.totalBytes ?: 0L) > 0L }) transfers.sumOf { it.totalBytes ?: 0L } else null
        val speed = transfers.sumOf { it.bytesPerSecond }
        val remaining = total?.let { (it - transferred).coerceAtLeast(0L) }
        val eta = if (remaining != null && speed > 0) remaining / speed else null

        val details = buildList {
            if (total != null) add("${(transferred * 100 / total).coerceIn(0L, 100L)}% · ${formatSize(transferred)} / ${formatSize(total)}")
            else if (transferred > 0) add(formatSize(transferred))
            if (speed > 0) add(formatSpeed(speed))
            if (eta != null) add(context.getString(R.string.common_time_left, formatEta(eta, context.resources)))
        }.joinToString(" · ")

        val icon = if (onlyDownloads) R.drawable.icon_download else R.drawable.icon_upload
        builder
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(details.ifEmpty { context.getString(R.string.upload_starting) })
        privateOnLockScreen(builder, CHANNEL_TRANSFER_PROGRESS, icon, if (transfers.size == 1) context.getString(R.string.upload_1_transfer_running) else context.getString(R.string.upload_transfers_running, transfers.size))
        if (total != null) {
            builder.setProgress(100, (transferred * 100 / total).coerceIn(0L, 100L).toInt(), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    fun updateProgressNotification(transfers: List<TransferInfo>) {
        notify(PROGRESS_NOTIFICATION_ID, buildProgressNotification(transfers))
    }

    /** The notification of a finished transfer, with "Try again" when it failed and [canRetry]; returns its id. */
    fun notifyResult(info: TransferInfo, outcome: TransferOutcome, message: String?, openUri: Uri?, mimeType: String?, canRetry: Boolean = false): Int {
        val failed = outcome == TransferOutcome.FAILED
        val upload = info.kind == TransferKind.UPLOAD
        val title = context.getString(
            when {
                failed && upload -> R.string.upload_upload_failed
                failed -> R.string.upload_download_failed
                upload -> R.string.upload_upload_complete
                else -> R.string.upload_download_complete
            }
        )

        val contentIntent = if (!failed && openUri != null) {
            val viewIntent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(openUri, mimeType ?: "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            PendingIntent.getActivity(context, resultIds.get(), viewIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            openAppIntent()
        }

        val channel = if (failed) CHANNEL_TRANSFER_FAILED else CHANNEL_TRANSFER_COMPLETE
        val icon = if (info.kind == TransferKind.UPLOAD) R.drawable.icon_upload else R.drawable.icon_download
        val id = resultIds.getAndIncrement()
        val notification = NotificationCompat.Builder(context, channel)
            .setColor(iconColor())
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(message ?: info.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message ?: info.title))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(if (failed) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_STATUS)
            .apply { if (failed && canRetry) addAction(0, context.getString(R.string.media_try_again), retryIntent(id)) }
            .let { privateOnLockScreen(it, channel, icon, title) }
            .build()
        notify(id, notification)
        return id
    }

    private fun retryIntent(notificationId: Int): PendingIntent {
        val intent = Intent(context, TransferService::class.java)
            .setAction(ACTION_RETRY_TRANSFER)
            .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        return PendingIntent.getService(context, notificationId, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun cancel(id: Int) = NotificationManagerCompat.from(context).cancel(id)

    private fun notify(id: Int, notification: Notification) {
        // Without the permission the foreground service still keeps the transfer alive, just silently
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
