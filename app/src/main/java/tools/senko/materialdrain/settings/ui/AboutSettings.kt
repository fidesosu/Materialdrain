package tools.senko.materialdrain.settings.ui

import tools.senko.materialdrain.BuildConfig
import tools.senko.materialdrain.ui.theme.AppShapes
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import tools.senko.materialdrain.ui.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What this build of the app is, read from the installed package. */
internal data class AppAbout(
    val versionName: String,
    val versionCode: Long,
    val kind: String,
    /** When the commit this build was made from was made (BuildConfig.COMMIT_TIME, see app/build.gradle.kts) */
    val committed: String,
    val packageName: String
) {
    /** For copying into a bug report. */
    fun asText(): String = buildString {
        appendLine("Materialdrain $versionName ($kind)")
        appendLine("Version code $versionCode")
        appendLine("Commit date $committed")
        append("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
    }
}

internal fun readAbout(context: Context): AppAbout {
    // Not there in a preview (in Android Studio), which has no app installed: no version then
    val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    val versionName = info?.versionName.orEmpty()
    val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val kind = when {
        debuggable -> "Debug build"
        // "1.6.4-dev.5": a build of the dev branch (see app/build.gradle.kts)
        "-dev." in versionName -> "Development build"
        else -> "Stable release"
    }
    val committed = Instant.ofEpochSecond(BuildConfig.COMMIT_TIME)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
    return AppAbout(versionName, info?.longVersionCode ?: 0L, kind, committed, context.packageName)
}

/** The top of the About page: the app's icon, name and version, the details of this build, and a button to copy them. */
@Composable
fun SettingsEnvironment.AboutSection() {
    val context = LocalContext.current
    val about = remember { readAbout(context) }
    val icon = remember {
        runCatching { context.packageManager.getApplicationIcon(context.packageName).toBitmap(192, 192).asImageBitmap() }.getOrNull()
    }
    var copied by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Image(it, contentDescription = null, modifier = Modifier.size(64.dp).clip(AppShapes.Large))
                Spacer(Modifier.width(16.dp))
            }
            Column {
                Text("Materialdrain", style = MaterialTheme.typography.headlineSmall)
                Text(about.versionName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    about.kind,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .clip(AppShapes.Small)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AboutRow("Version code", about.versionCode.toString())
                AboutRow("Commit date", about.committed)
                AboutRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                AboutRow("Package", about.packageName)
            }
        }

        OutlinedButton(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Materialdrain version", about.asText()))
            copied = true
        }) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (copied) "Copied" else "Copy these details")
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.55f))
    }
}

// ---- Previews ----
// The About page is drawn by SettingsAboutPreview in SettingsScreen.kt.
