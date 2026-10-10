package tools.senko.materialdrain.preferences

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
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
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What this build of the app is, read from the installed package. */
private data class AppAbout(
    val versionName: String,
    val versionCode: Long,
    val kind: String,
    /** When the commit this build was made from was made, from the version (see app/build.gradle.kts); null when it isn't one of those */
    val committed: String?,
    val packageName: String
) {
    /** For copying into a bug report. */
    fun asText(): String = buildString {
        appendLine("Materialdrain $versionName ($kind)")
        appendLine("Version code $versionCode")
        committed?.let { appendLine("Built from a commit of $it") }
        append("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
    }
}

/** "1.4.20261009.1530" or "1.4.20261009.1530-dev": a base version, then the commit's date and time in UTC. */
private val VERSION_PATTERN = Regex("""^\d+(?:\.\d+)*\.(\d{8})\.(\d{4})(-dev)?$""")

private fun readAbout(context: Context): AppAbout {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionName = info.versionName.orEmpty()
    val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val match = VERSION_PATTERN.find(versionName)
    val kind = when {
        debuggable -> "Debug build"
        match?.groupValues?.get(3) == "-dev" -> "Development build"
        else -> "Stable release"
    }
    val committed = match?.let {
        runCatching {
            LocalDateTime.parse(it.groupValues[1] + it.groupValues[2], DateTimeFormatter.ofPattern("yyyyMMddHHmm"))
                .atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
        }.getOrNull()
    }
    return AppAbout(versionName, info.longVersionCode, kind, committed, context.packageName)
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
                Image(it, contentDescription = null, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)))
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AboutRow("Version code", about.versionCode.toString())
                about.committed?.let { AboutRow("Built from a commit of", it) }
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
