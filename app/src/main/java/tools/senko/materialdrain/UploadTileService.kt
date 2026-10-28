package tools.senko.materialdrain

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.TileService

/**
 * The Quick Settings tile "Upload": opens the app on its upload screen, the way the launcher's Upload shortcut does (see
 * installLauncherShortcuts). On a locked device it asks for the device to be unlocked first.
 */
class UploadTileService : TileService() {
    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { openUpload() } else openUpload()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openUpload() {
        val intent = openScreenIntent(this, Screen.Upload)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
