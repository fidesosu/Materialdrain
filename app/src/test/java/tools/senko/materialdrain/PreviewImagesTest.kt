package tools.senko.materialdrain

import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.composeTestRule
import com.github.takahirom.roborazzi.inspectionMode
import com.github.takahirom.roborazzi.manualAdvance
import com.github.takahirom.roborazzi.toRoborazziComposeOptions
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.android.screenshotid.AndroidPreviewScreenshotIdBuilder
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

/**
 * Draws every @Preview of the app to a picture, without a device: ./gradlew :app:recordRoborazziDebug puts them in
 * app/build/outputs/roborazzi, one per preview (and per light and dark). Run with the other tests, it checks that each
 * preview draws without failing.
 *
 * Drawn as Android Studio draws them (in inspection mode), as they look 1.5 seconds in: the time stops there, so an
 * animation that never ends (a blinking cursor, a spinning progress) doesn't keep the picture from being taken.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Android 15 on a phone: Robolectric can't start Android 16 on this Java version yet. A preview with a device of its own
// (see ScreenPreviews) is drawn on that one
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class PreviewImagesTest(private val preview: ComposablePreview<AndroidPreviewInfo>) {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun preview() {
        val name = AndroidPreviewScreenshotIdBuilder(preview).build()
        preview.captureRoboImage(
            filePath = "build/outputs/roborazzi/$name.png",
            roborazziComposeOptions = preview.toRoborazziComposeOptions().builder()
                .composeTestRule(composeRule)
                .inspectionMode(true)
                .manualAdvance(composeRule, PICTURE_AT_MILLIS)
                .build()
        )
    }

    companion object {
        private const val PICTURE_AT_MILLIS = 1_500L

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun previews(): List<ComposablePreview<AndroidPreviewInfo>> =
            AndroidComposablePreviewScanner()
                .scanPackageTrees("tools.senko.materialdrain")
                // The previews are private, at the end of the file of what they show
                .includePrivatePreviews()
                .getPreviews()
    }
}
