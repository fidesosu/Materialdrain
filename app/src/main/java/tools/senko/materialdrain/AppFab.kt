package tools.senko.materialdrain

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.ui.components.FadeText
import tools.senko.materialdrain.ui.icons.symbols.SettingsFilled
import tools.senko.materialdrain.ui.icons.symbols.Symbols
import tools.senko.materialdrain.ui.icons.symbols.Upload
import androidx.compose.ui.res.stringResource
import android.content.res.Resources


/** How far below its corner the button starts, enough to be off the screen; it slides up from there. */
private val FabEntryOffset = 160.dp

data class FabDetails(
    /** The button of one screen is a different button than that of another screen, they only share the widget. */
    val screen: Screen,
    val icon: ImageVector,
    val text: String?,
    val onClick: () -> Unit,
    val isExtended: Boolean,
    val yOffset: Dp = 0.dp,
    /** Other texts this button shows, its text area is as wide as the widest of them so it never changes size. */
    val reserveTextWidthFor: List<String> = emptyList(),
    /** The icon makes a full turn each time this value increases. */
    val iconSpinTrigger: Int = 0
)

/**
 * The button of [screen], or null when it has none. The upload screen has Upload once there's something to upload
 * ([canUpload]); the settings have Save on the categories saved with it ([settingsHasSaveButton]), which confirms the
 * save by spinning its icon ([settingsSaveCount] counting up) and changing its text for a moment ([settingsJustSaved]).
 */
internal fun screenFab(
    /** The app's texts, for the button's */
    resources: Resources,
    screen: Screen,
    canUpload: Boolean,
    onUpload: () -> Unit,
    settingsHasSaveButton: Boolean,
    settingsJustSaved: Boolean,
    settingsSaveCount: Int,
    onSaveSettings: () -> Unit
): FabDetails? = when (screen) {
    // Nothing to upload yet (or already uploading): the upload screen offers its own actions instead
    Screen.Upload -> if (canUpload) FabDetails(
        screen = Screen.Upload,
        icon = Symbols.Upload,
        text = resources.getString(R.string.upload_upload),
        onClick = onUpload,
        isExtended = true,
    ) else null
    // Only the categories whose settings are saved with the button (see SettingsCategory.hasSaveButton)
    Screen.Settings -> if (settingsHasSaveButton) FabDetails(
        screen = Screen.Settings,
        icon = Symbols.SettingsFilled, // The filled gear, as the save button
        text = resources.getString(if (settingsJustSaved) R.string.app_settings_saved else R.string.app_save_settings),
        // Both texts take the same space, so the button doesn't change size when it confirms the save
        reserveTextWidthFor = listOf(resources.getString(R.string.app_save_settings), resources.getString(R.string.app_settings_saved)),
        iconSpinTrigger = settingsSaveCount,
        onClick = onSaveSettings,
        isExtended = true
    ) else null
    // The file screens have no button of their own: searching is the magnifier in the top bar, and the Filesystem's
    // upload is an icon in its own header (see BrowserScreen), so it works the same whichever navigation style is on
    Screen.Files, Screen.Lists, Screen.Filesystem, Screen.FileDetail -> null
}

/**
 * The screen's floating button ([fab], null for none). It slides down and fades when it goes, and in when it comes back;
 * moving to another screen's button crossfades. [lift] moves it up, over the selection bar and a snackbar.
 */
@Composable
internal fun AppFab(fab: FabDetails?, lift: Dp, reduceMotion: Boolean, onHeightChanged: (Dp) -> Unit) {
    val density = LocalDensity.current
    val entryOffsetPx = with(density) { FabEntryOffset.toPx() }
    // Moved visually (an offset, not padding), so the FAB rises over the selection bar and a snackbar
    Box(modifier = Modifier.offset(y = -lift)) {
        // Keep the last button around so it can animate out instead of vanishing
        var lastFab by remember { mutableStateOf<FabDetails?>(null) }
        LaunchedEffect(fab) { if (fab != null) lastFab = fab }

        // 0 = shown, 1 = gone. Animated by its own state, so leaving slides it down instead of removing it at once
        val fabHidden by animateFloatAsState(
            targetValue = if (fab != null) 0f else 1f,
            animationSpec = tween(if (reduceMotion) 100 else 320, easing = FastOutSlowInEasing),
            label = "fabHidden"
        )

        // The icon makes a full turn whenever the trigger of the button counts up
        val iconRotation = remember { Animatable(0f) }
        var handledSpinTrigger by remember { mutableIntStateOf(0) }
        val spinTrigger = (fab ?: lastFab)?.iconSpinTrigger ?: 0
        LaunchedEffect(spinTrigger) {
            if (spinTrigger > handledSpinTrigger) {
                handledSpinTrigger = spinTrigger
                // Reduced animations: no spinning
                if (!reduceMotion) {
                    iconRotation.snapTo(0f)
                    iconRotation.animateTo(360f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
                }
            }
        }

        Box(modifier = Modifier.graphicsLayer {
            translationY = fabHidden * entryOffsetPx
            alpha = 1f - fabHidden
        }) {
            if (fabHidden < 1f) (fab ?: lastFab)?.let { details ->
                ExtendedFloatingActionButton(
                    onClick = details.onClick,
                    expanded = details.isExtended,
                    icon = {
                        AnimatedContent(
                            targetState = details.icon,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                            },
                            label = "fabIconAnimation"
                        ) { targetIcon ->
                            Icon(
                                targetIcon,
                                contentDescription = details.text ?: stringResource(R.string.app_fab_icon),
                                modifier = Modifier.rotate(iconRotation.value)
                            )
                        }
                    },
                    text = {
                        // Moving to the button of another screen crossfades and animates the size of the button,
                        // as it always did. Only when the button of the same screen changes its text the
                        // characters cycle, and then the size stays as it is.
                        AnimatedContent(
                            targetState = details,
                            contentKey = { it.screen },
                            transitionSpec = {
                                val crossfade = fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                                // Reduced animations: the button takes its new size at once
                                if (reduceMotion) crossfade.using(SizeTransform(clip = false) { _, _ -> snap() }) else crossfade
                            },
                            label = "fabTextAnimation"
                        ) { shown ->
                            shown.text?.let { fabText ->
                                FadeText(
                                    text = fabText,
                                    fixedWidth = true,
                                    widthReferenceTexts = shown.reserveTextWidthFor
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .onSizeChanged { onHeightChanged(with(density) { it.height.toDp() }) }
                        .then(if (details.yOffset != 0.dp) Modifier.offset(y = details.yOffset) else Modifier)
                )
            }
        }
    }
}
