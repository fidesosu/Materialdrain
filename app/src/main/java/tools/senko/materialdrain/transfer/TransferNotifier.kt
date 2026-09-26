package tools.senko.materialdrain.transfer

import android.Manifest
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

/**
 * Builds the transfer notifications. Three separate channels are used so that the user can tune each
 * kind in the Android notification settings (importance, sound, vibration, lock screen, badge, ...).
 */
class TransferNotifier(private val context: Context) {

    private val resultIds = AtomicInteger(FIRST_RESULT_NOTIFICATION_ID)

    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannelGroup(NotificationChannelGroup(CHANNEL_GROUP_TRANSFERS, "Transfers"))

        // Silent by default: this notification is shown for as long as a transfer runs
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TRANSFER_PROGRESS, "Transfer progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while files are being uploaded or downloaded"
                group = CHANNEL_GROUP_TRANSFERS
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TRANSFER_COMPLETE, "Completed transfers", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Shown when an upload or download finished while the app was in the background"
                group = CHANNEL_GROUP_TRANSFERS
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TRANSFER_FAILED, "Failed transfers", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shown when an upload or download failed while the app was in the background"
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

    /** The ongoing notification of the foreground service, summarising every running transfer. */
    fun buildProgressNotification(transfers: List<TransferInfo>): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_TRANSFER_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())
            .addAction(0, "Cancel", cancelAllIntent())

        if (transfers.isEmpty()) {
            return builder
                .setSmallIcon(R.drawable.icon_upload)
                .setContentTitle("Finishing transfers")
                .setProgress(0, 0, true)
                .build()
        }

        val onlyDownloads = transfers.all { it.kind == TransferKind.DOWNLOAD }
        val onlyUploads = transfers.all { it.kind == TransferKind.UPLOAD }
        val title = if (transfers.size == 1) {
            val transfer = transfers.first()
            (if (transfer.kind == TransferKind.UPLOAD) "Uploading " else "Downloading ") + transfer.title
        } else {
            when {
                onlyUploads -> "Uploading ${transfers.size} items"
                onlyDownloads -> "Downloading ${transfers.size} items"
                else -> "${transfers.size} transfers running"
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
            if (eta != null) add("${formatEta(eta)} left")
        }.joinToString(" · ")

        builder
            .setSmallIcon(if (onlyDownloads) R.drawable.icon_download else R.drawable.icon_upload)
            .setContentTitle(title)
            .setContentText(details.ifEmpty { "Starting…" })
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

    fun notifyResult(info: TransferInfo, outcome: TransferOutcome, message: String?, openUri: Uri?, mimeType: String?) {
        val failed = outcome == TransferOutcome.FAILED
        val verb = if (info.kind == TransferKind.UPLOAD) "Upload" else "Download"

        val contentIntent = if (!failed && openUri != null) {
            val viewIntent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(openUri, mimeType ?: "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            PendingIntent.getActivity(context, resultIds.get(), viewIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            openAppIntent()
        }

        val notification = NotificationCompat.Builder(context, if (failed) CHANNEL_TRANSFER_FAILED else CHANNEL_TRANSFER_COMPLETE)
            .setSmallIcon(if (info.kind == TransferKind.UPLOAD) R.drawable.icon_upload else R.drawable.icon_download)
            .setContentTitle(if (failed) "$verb failed" else "$verb complete")
            .setContentText(message ?: info.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message ?: info.title))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(if (failed) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_STATUS)
            .build()
        notify(resultIds.getAndIncrement(), notification)
    }

    private fun notify(id: Int, notification: Notification) {
        // Without the permission the foreground service still keeps the transfer alive, just silently
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
