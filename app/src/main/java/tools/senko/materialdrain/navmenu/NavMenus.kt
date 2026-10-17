package tools.senko.materialdrain.navmenu

import androidx.compose.ui.graphics.vector.ImageVector
import tools.senko.materialdrain.Tab

enum class NavFabPosition(val bias: Float) { START(-1f), CENTER(0f), END(1f) }

/** Which menu the FAB navigation prototype shows (Developer settings): the active host's tabs, or a mock of a big one. */
enum class NavMenuPreview(val label: String) { PIXELDRAIN("This host"), TRUENAS_MOCK("TrueNAS (mock)") }

/** [tab] is null for mock destinations, which only exist to preview how a bigger menu looks. */
data class NavMenuItem(val id: String, val label: String, val icon: ImageVector, val tab: Tab? = null)

data class NavMenuSection(val title: String, val items: List<NavMenuItem>)

/**
 * [sections] are in top-to-bottom reading order. The window is bottom-anchored, so the last section sits
 * right above [pinned], the row closest to the FAB (and the thumb), and earlier sections are a scroll away.
 */
data class NavMenu(val pinned: List<NavMenuItem>, val sections: List<NavMenuSection> = emptyList())

private fun Tab.menuItem() = NavMenuItem(id = "${screen.name}:$id", label = title, icon = icon, tab = this)

/**
 * The tabs of the active host, as its bottom bar has them: those its config puts in a section (ScreenConfig.section) in
 * groups of that name, in the order they first come, the others in the row nearest the button. Settings isn't a tile:
 * it's in the ⋮ menu at the top right of the screen, out of the thumb zone because it's rarely used.
 */
private fun tabsNavMenu(tabs: List<Tab>): NavMenu {
    val (grouped, pinned) = tabs.partition { it.section != null }
    return NavMenu(
        pinned = pinned.map { it.menuItem() },
        sections = grouped.groupBy { it.section!! }.map { (title, inSection) -> NavMenuSection(title, inSection.map { it.menuItem() }) }
    )
}

fun NavMenuPreview.menu(tabs: List<Tab>): NavMenu = when (this) {
    NavMenuPreview.PIXELDRAIN -> tabsNavMenu(tabs)
    NavMenuPreview.TRUENAS_MOCK -> TrueNasMockNavMenu
}
