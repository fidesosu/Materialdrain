package tools.senko.materialdrain.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The corners of the app, from small to big. Everything rounded takes one of these (or a name for one, such as
 * FloatingCardShape), so a change here changes everything of that size. The steps are Material 3's, with [Card] between
 * [Large] and [ExtraLarge] for the cards.
 */
object AppShapes {
    /** Badges and the small buttons over a preview. */
    val ExtraSmall = RoundedCornerShape(4.dp)
    /** Thumbnails, the media players' buttons, and the pills over a preview (a page number). */
    val Small = RoundedCornerShape(8.dp)
    /** Buttons, and boxes inside a card: the media players, a held preview. */
    val Medium = RoundedCornerShape(12.dp)
    /** Rows and tiles which light up when pressed, the action buttons of a file. */
    val Large = RoundedCornerShape(16.dp)
    /** Cards: the settings, a file's details, and the floating cards (menus, the search, the navigation window). */
    val Card = RoundedCornerShape(20.dp)
    /** Dialogs, the search field and other pills. */
    val ExtraLarge = RoundedCornerShape(28.dp)
}

/** [AppShapes] for the Material components, which pick their own size of corner. */
val Shapes = Shapes(
    extraSmall = AppShapes.ExtraSmall,
    small = AppShapes.Small,
    medium = AppShapes.Medium,
    large = AppShapes.Large,
    extraLarge = AppShapes.ExtraLarge
)
