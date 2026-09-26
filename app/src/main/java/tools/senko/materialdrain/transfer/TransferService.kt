package tools.senko.materialdrain.transfer

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tools.senko.materialdrain.AppContainer

private const val TAG_TRANSFER_SERVICE = "TransferService"
private const val NOTIFICATION_UPDATE_INTERVAL_MS = 500L

/**
 * Foreground service which is running while at least one upload or download is active. It does not
 * perform the transfers itself, it keeps the app process alive (so transfers continue in the
 * background) and shows their progress in a notification.
 */
class TransferService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private var latestStartId = 0

    private lateinit var registry: TransferRegistry
    private lateinit var notifier: TransferNotifier

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        registry = AppContainer.get(application).transferRegistry
        notifier = TransferNotifier(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (intent?.action == ACTION_CANCEL_ALL_TRANSFERS) registry.cancelAll()

        // Every startForegroundService() call has to be answered with startForeground() quickly
        if (!startAsForeground(notifier.buildProgressNotification(registry.active.value.values.toList()))) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (collectJob == null) {
            collectJob = scope.launch {
                registry.active.collect { transfers ->
                    if (transfers.isEmpty()) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        // Only stops when no newer start request arrived in the meantime
                        stopSelf(latestStartId)
                    } else {
                        notifier.updateProgressNotification(transfers.values.toList())
                    }
                    delay(NOTIFICATION_UPDATE_INTERVAL_MS)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground(notification: Notification): Boolean {
        return try {
            ServiceCompat.startForeground(
                this,
                PROGRESS_NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            )
            true
        } catch (e: Exception) {
            Log.w(TAG_TRANSFER_SERVICE, "Could not start as foreground service: ${e.message}")
            false
        }
    }

    /** Android 15 limits data sync services to a few hours per day, the system then asks the service to stop. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG_TRANSFER_SERVICE, "Foreground service time limit reached, stopping the service.")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, TransferService::class.java))
            } catch (e: Exception) {
                // e.g. not allowed to start from the background: the transfer continues without the notification
                Log.w(TAG_TRANSFER_SERVICE, "Could not start the transfer service: ${e.message}")
            }
        }
    }
}
