package tools.senko.materialdrain.preferences

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class AvatarCropTest {

    private val wide = AvatarImage(File("wide.png"), width = 1600, height = 900, animated = false)
    private val tall = AvatarImage(File("tall.gif"), width = 500, height = 1200, animated = true)
    private val frame = 900f

    private fun assertCrop(expected: AvatarCrop, actual: AvatarCrop) {
        assertEquals(expected.left, actual.left, 0.01f)
        assertEquals(expected.top, actual.top, 0.01f)
        assertEquals(expected.side, actual.side, 0.01f)
    }

    @Test
    fun unzoomedAndCentredIsTheMiddleSquare() {
        assertCrop(AvatarCrop.centred(1600, 900), cropOf(wide, frame, zoom = 1f, offset = Offset.Zero))
        assertCrop(AvatarCrop(0f, 350f, 500f), cropOf(tall, frame, zoom = 1f, offset = Offset.Zero))
    }

    @Test
    fun movingThePictureRightShowsMoreOfItsLeft() {
        // At zoom 1 the wide picture is shown at its own size in a 900 px square: 100 px on the screen is 100 px of it
        assertCrop(AvatarCrop(250f, 0f, 900f), cropOf(wide, frame, zoom = 1f, offset = Offset(100f, 0f)))
    }

    @Test
    fun zoomingInShowsASmallerSquare() {
        assertCrop(AvatarCrop(575f, 225f, 450f), cropOf(wide, frame, zoom = 2f, offset = Offset.Zero))
    }

    @Test
    fun placementAndCropAreEachOthersInverse() {
        listOf(
            AvatarPicture(wide, AvatarCrop(100f, 50f, 600f)),
            AvatarPicture(wide, AvatarCrop.centred(1600, 900)),
            AvatarPicture(tall, AvatarCrop(0f, 700f, 500f)),
            AvatarPicture(tall, AvatarCrop(120f, 300f, 200f))
        ).forEach { picture ->
            val (zoom, offset) = placementOf(picture, frame)
            assertCrop(picture.crop, cropOf(picture.image, frame, zoom, offset))
        }
    }

    @Test
    fun squareIsGivenOrTakenAPercent() {
        assertEquals(true, AvatarImage(File("a"), 1000, 995, false).isSquare)
        assertEquals(false, AvatarImage(File("a"), 1000, 900, false).isSquare)
    }
}
