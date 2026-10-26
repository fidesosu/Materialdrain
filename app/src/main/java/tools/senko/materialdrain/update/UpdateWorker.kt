package tools.senko.materialdrain.update

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import tools.senko.materialdrain.AppContainer
import tools.senko.materialdrain.MainActivity
import tools.senko.materialdrain.R
import java.util.concurrent.TimeUnit

/** The notifications of a new version (Settings → About, Also check daily in the background). */
const val CHANNEL_APP_UPDATES = "app_updates"

/** In the intent of the update notification: the app offers the update when it opens (see MainActivity.handleIntent). */
const val EXTRA_OFFER_UPDATE = "tools.senko.materialdrain.OFFER_UPDATE"

private const val WORK_NAME = "update-check"
private const val NOTIFICATION_ID = 50

/**
 * The daily check for a new version, while the setting is on (off by default): a notification when one is out, once per
 * version and never for one that was skipped. Only with a network, and not on mobile data while the data saver holds
 * what the app loads by itself. The notification names only the version, nothing of the files.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = AppContainer.get(applicationContext as Application)
        val settings = container.appSettings
        val updates = container.appUpdates
        if (!settings.checkUpdatesInBackground.value || !updates.installed.updatable || container.dataSaver.active.value) {
            return Result.success()
        }
        val update = (updates.check() as? UpdateResult.Available)?.update ?: return Result.success()
        val code = update.info.versionCode
        if (!shouldNotify(code, skipped = settings.skippedUpdateCode, notified = settings.notifiedUpdateCode)) return Result.success()
        if (notify(applicationContext, update)) settings.notifiedUpdateCode = code
        return Result.success()
    }

    companion object {
        /** Whether the version [code] gets a notification: one per version, none for the one the user skipped. */
        internal fun shouldNotify(code: Long, skipped: Long, notified: Long) = code != skipped && code != notified

        /** Starts or stops the daily check, as the setting is; one already planned stays as it is. */
        fun schedule(context: Context, enabled: Boolean) {
            val work = WorkManager.getInstance(context)
            if (!enabled) {
                work.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** The channel of the update notifications, made when the first one comes. */
        fun ensureChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_APP_UPDATES, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "A new version of Materialdrain, from the daily check in the background"
                }
            )
        }

        /** The notification of [update]; false when notifications aren't allowed. */
        internal fun notify(context: Context, update: AvailableUpdate): Boolean {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return false
            ensureChannel(context)
            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_OFFER_UPDATE, true)
            val count = update.changes.size
            val notification = NotificationCompat.Builder(context, CHANNEL_APP_UPDATES)
                .setSmallIcon(R.drawable.icon_download)
                .setContentTitle("Materialdrain ${update.info.versionName} is out")
                .setContentText(
                    when (count) {
                        0 -> "Tap to see it"
                        1 -> "1 change · tap to see it"
                        else -> "$count changes · tap to see them"
                    }
                )
                .setContentIntent(PendingIntent.getActivity(context, NOTIFICATION_ID, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            return true
        }
    }
}
