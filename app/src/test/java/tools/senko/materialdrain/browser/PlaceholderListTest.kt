package tools.senko.materialdrain.browser

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tools.senko.materialdrain.ui.ListLook
import tools.senko.materialdrain.ui.LocalListLook
import tools.senko.materialdrain.ui.preview.PreviewSamples
import tools.senko.materialdrain.ui.preview.PreviewTheme

/**
 * While a list loads with nothing to show yet, it shows placeholder rows of the files' shape (PlaceholderList), as it's
 * set to look; once there are files, or while a list already shown is loaded again, only the files.
 */
@RunWith(RobolectricTestRunner::class)
class PlaceholderListTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun placeholdersWhileAnEmptyListLoads() {
        compose.setContent { PreviewTheme { SampleBrowserContent(BrowserMode.FILESYSTEM, entries = emptyList(), isLoading = true) } }
        compose.onNodeWithContentDescription("Loading").assertExists()
    }

    @Test
    fun theFilesOnceTheyreThere() {
        compose.setContent { PreviewTheme { SampleBrowserContent(BrowserMode.FILESYSTEM, isLoading = true) } }
        compose.onAllNodesWithContentDescription("Loading").assertCountEquals(0)
        compose.onAllNodesWithText(PreviewSamples.files.first().name).onFirst().assertExists()
    }

    @Test
    fun placeholdersInTheGridToo() {
        compose.setContent {
            CompositionLocalProvider(LocalListLook provides ListLook(grid = true)) {
                PreviewTheme { SampleBrowserContent(BrowserMode.FILES, entries = emptyList(), isLoading = true) }
            }
        }
        compose.onNodeWithContentDescription("Loading").assertExists()
    }
}
