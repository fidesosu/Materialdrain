package tools.senko.materialdrain.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.util.formatEta
import tools.senko.materialdrain.util.formatSize
import tools.senko.materialdrain.util.formatSpeed

data class TransferProgress(
    val transferredBytes: Long,
    val totalBytes: Long?,
    val bytesPerSecond: Long = 0L,
    val etaSeconds: Long? = null,
    val label: String? = null
) {
    val fraction: Float?
        get() = totalBytes?.takeIf { it > 0 }?.let { (transferredBytes.toFloat() / it).coerceIn(0f, 1f) }
}

@Composable
fun TransferStatusBar(progress: TransferProgress, modifier: Modifier = Modifier) {
    val fraction = progress.fraction
    val animatedFraction by animateFloatAsState(
        targetValue = fraction ?: 0f,
        animationSpec = tween(durationMillis = 250, easing = LinearEasing),
        label = "transferProgress"
    )

    val leftText = buildList {
        if (fraction != null) add("${(fraction * 100).toInt()}%")
        if (progress.totalBytes != null && progress.totalBytes > 0) {
            add("${formatSize(progress.transferredBytes)} / ${formatSize(progress.totalBytes)}")
        } else if (progress.transferredBytes > 0) {
            add(formatSize(progress.transferredBytes))
        }
        progress.label?.let { add(it) }
    }.joinToString(" · ")

    val rightText = when {
        fraction != null && fraction >= 1f -> "Finalizing…"
        progress.bytesPerSecond > 0 -> buildString {
            append(formatSpeed(progress.bytesPerSecond))
            progress.etaSeconds?.let { append(" · ${formatEta(it)} left") }
        }
        else -> null
    }

    val textStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")

    Column(modifier = modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = leftText,
                style = textStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (rightText != null) {
                Text(
                    text = rightText,
                    style = textStyle,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
        if (fraction != null) {
            LinearProgressIndicator(progress = { animatedFraction }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
