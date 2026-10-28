package tools.senko.materialdrain.compat

import android.graphics.drawable.Animatable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tools.senko.materialdrain.screenshots.AppDriver

/**
 * The app's image loader (Coil, set up in AppContainer): animated pictures move, a still one stays still, and a thumbnail
 * which was loaded once comes from the disk the next time, though the host sends no cache headers with it.
 */
@RunWith(AndroidJUnit4::class)
class ImageLoadingTest {

    private val app = AppDriver()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() = app.reset()

    private fun asset(name: String): ByteArray =
        InstrumentationRegistry.getInstrumentation().context.assets.open("images/$name").use { it.readBytes() }

    /** [data] loaded by the app's image loader, without its memory cache, so it's decoded each time. */
    private fun load(data: Any, diskCache: CachePolicy = CachePolicy.ENABLED) = runBlocking {
        app.container // sets up the image loader
        val request = ImageRequest.Builder(context)
            .data(data)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(diskCache)
            .build()
        context.imageLoader.execute(request)
    }

    private fun isAnimated(name: String): Boolean {
        val result = load(asset(name))
        assertTrue("$name didn't load: $result", result is SuccessResult)
        return (result as SuccessResult).image.asDrawable(context.resources) is Animatable
    }

    @Test
    fun animatedPicturesMove() {
        assertTrue(isAnimated("animated.gif"))
        assertTrue(isAnimated("animated.webp"))
        assertFalse(isAnimated("still.png"))
    }

    @Test
    fun aThumbnailComesFromTheDiskTheSecondTime() {
        app.prepare { }
        val url = "https://pixeldrain.com/api/file/cItyLt03/thumbnail"
        context.imageLoader.diskCache?.remove(url)
        val asked = { app.fake.requests.count { it == "/api/file/cItyLt03/thumbnail" } }
        val before = asked()
        assertTrue(load(url) is SuccessResult)
        assertEquals("the first load asks the host", before + 1, asked())
        assertTrue(load(url) is SuccessResult)
        assertEquals("the second comes from the disk", before + 1, asked())
    }
}
