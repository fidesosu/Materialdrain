package tools.senko.materialdrain.ui

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import tools.senko.materialdrain.settings.AppSettings
import kotlin.math.roundToInt

/** How strong the app's vibrations are (Feedback settings): how hard each one is played, of the device's strongest. */
enum class VibrationStrength(val label: String, val scale: Float) {
    LIGHT("Light", 0.3f),
    MEDIUM("Medium", 0.6f),
    STRONG("Strong", 1f)
}

/** What a vibration says. */
enum class HapticKind {
    /** Letting go now would do what the gesture is for (open, skip, close) */
    THRESHOLD,

    /** The finger went back from there: a lighter one */
    RELEASE,

    /** A long press was taken */
    LONG_PRESS
}

/** Where in the app a vibration comes from, each turned on or off in the Feedback settings. */
enum class HapticPlace {
    /** The miniplayer's swipes */
    PLAYER,

    /** A swipe along the tab strip going to another tab (see TabStrip), or a swipe across the page doing so */
    NAV_SWIPE
}

/**
 * The app's vibrations, played with the Feedback settings' strength (see [VibrationStrength]) rather than the system's
 * fixed one, and only where they're turned on ([places]). Off altogether, [perform] does nothing.
 */
@Immutable
class AppHaptics internal constructor(
    private val vibrator: Vibrator?,
    private val enabled: Boolean,
    private val strength: VibrationStrength,
    private val places: Set<HapticPlace>
) {
    fun perform(kind: HapticKind, place: HapticPlace) {
        if (!enabled || place !in places) return
        vibrator?.let { vibrate(it, kind, strength) }
    }

    companion object {
        /** No vibrations: previews, and before the settings are read. */
        val None = AppHaptics(null, enabled = false, strength = VibrationStrength.LIGHT, places = emptySet())

        /** The device's vibrator, if it has one. */
        fun vibratorOf(context: Context): Vibrator? {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                context.getSystemService(Vibrator::class.java)
            }
            return vibrator?.takeIf { it.hasVibrator() }
        }

        /**
         * Plays [kind] at [strength]: a short "click" (or a lighter "tick" for [HapticKind.RELEASE]) scaled to it where the
         * device can play those (Android 11 and up, most phones); else a pulse of that strength, or the system's tick or
         * click where it can't vary its strength either. As touch feedback, so the system's own setting for that applies.
         */
        fun vibrate(vibrator: Vibrator, kind: HapticKind, strength: VibrationStrength) {
            val scale = strength.scale * if (kind == HapticKind.RELEASE) 0.6f else 1f
            val primitive = if (kind == HapticKind.RELEASE) VibrationEffect.Composition.PRIMITIVE_TICK else VibrationEffect.Composition.PRIMITIVE_CLICK
            val effect = when {
                Build.VERSION.SDK_INT >= 30 && vibrator.areAllPrimitivesSupported(primitive) ->
                    VibrationEffect.startComposition().addPrimitive(primitive, scale.coerceIn(0f, 1f)).compose()
                vibrator.hasAmplitudeControl() ->
                    VibrationEffect.createOneShot(if (kind == HapticKind.RELEASE) 8L else 14L, (255 * scale).roundToInt().coerceIn(1, 255))
                else -> VibrationEffect.createPredefined(
                    if (kind == HapticKind.RELEASE || strength == VibrationStrength.LIGHT) VibrationEffect.EFFECT_TICK else VibrationEffect.EFFECT_CLICK
                )
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= 33) {
                    vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(effect)
                }
            }
        }
    }
}

/** The app's vibrations for the screens deep down (the miniplayer, the tab strip), see [AppHaptics]. */
val LocalAppHaptics = staticCompositionLocalOf { AppHaptics.None }

/** [AppHaptics] as the Feedback settings have them now. */
@Composable
fun rememberAppHaptics(settings: AppSettings): AppHaptics {
    val context = LocalContext.current
    val enabled by settings.vibration.collectAsState()
    val strength by settings.vibrationStrength.collectAsState()
    val player by settings.vibratePlayer.collectAsState()
    val navSwipe by settings.vibrateNavSwipe.collectAsState()
    val vibrator = remember(context) { AppHaptics.vibratorOf(context) }
    return remember(vibrator, enabled, strength, player, navSwipe) {
        val places = buildSet {
            if (player) add(HapticPlace.PLAYER)
            if (navSwipe) add(HapticPlace.NAV_SWIPE)
        }
        AppHaptics(vibrator, enabled, strength, places)
    }
}
