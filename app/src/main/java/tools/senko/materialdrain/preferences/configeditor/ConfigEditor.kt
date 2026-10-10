@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package tools.senko.materialdrain.preferences.configeditor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import tools.senko.materialdrain.files.languageFor
import tools.senko.materialdrain.provider.api.HostScreen
import tools.senko.materialdrain.provider.api.ProviderConfigCodec
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.components.AppMenu
import tools.senko.materialdrain.ui.components.AppMenuItem
import tools.senko.materialdrain.ui.components.DotScrollbar
import tools.senko.materialdrain.ui.components.OverflowMenuButton
import tools.senko.materialdrain.ui.components.rememberCodeHighlighting

/** The two ways of editing a config: field by field, or its text. */
private enum class EditorMode(val label: String) { FORM("Form"), JSON("JSON") }

/** [this] with [key] set to [value], or left out when it's null; a key that's already there keeps its place. */
internal fun JsonObject.with(key: String, value: JsonElement?): JsonObject =
    JsonObject(LinkedHashMap(this).apply { if (value == null) remove(key) else put(key, value) })

/** The object at [path] in [this], an empty one when it isn't there (yet). */
private fun JsonObject.at(path: List<String>): JsonObject =
    path.fold(this) { obj, key -> obj[key] as? JsonObject ?: JsonObject(emptyMap()) }

/** What's at [path] in [this], null when it's left out. */
private fun JsonObject.valueAt(path: List<String>): JsonElement? = at(path.dropLast(1))[path.last()]?.takeUnless { it is JsonNull }

/** [this] with what's at [path] set to [value] (left out when null), making the objects on the way when they aren't there. */
private fun JsonObject.withAt(path: List<String>, value: JsonElement?): JsonObject {
    val key = path.first()
    return with(key, if (path.size == 1) value else at(listOf(key)).withAt(path.drop(1), value))
}

private val lenientJson = Json { isLenient = true }

/** From this width on, the first page stays on the left and the open page is shown next to it. */
private val TwoPaneMinWidth = 840.dp
/** How wide the first page is next to the open one. */
private val ListPaneWidth = 400.dp
/** The widest a page's fields grow: wider, lines get too long to read and fields look stretched. */
private val ContentMaxWidth = 640.dp
/** The narrowest a choice's button can be before the choices go in a dropdown instead (at the default text size). */
private val SegmentMinWidth = 80.dp
private const val FIRST_PAGE_KEY = "first"

/**
 * A config in a window of its own, the whole screen: a form with a field for each value, saying what goes where, or the
 * config's text. Like the settings, the form is pages: the first holds the basics, and every other part of the config
 * (the sign-in, the endpoints, an object inside one of them) is a row that opens a page of its own, with the whole
 * width of the screen for its fields however deep it is. On a wide screen the first page stays on the left and the open
 * one is shown next to it. A field the app doesn't know is kept and shown under "Other fields". Both views edit the same
 * config, and switching between them keeps what was typed.
 *
 * The form works on the config's JSON rather than on the app's model of it, so saving keeps every field, read or not.
 * Saving from the form writes the JSON out again, indented; saved untouched, a config keeps its own text.
 *
 * @param error why the last save was refused, shown at the top
 * @param onSave the config's text; whether it's taken is up to the caller, which passes back [error] when it isn't
 */
@Composable
fun ConfigEditor(
    title: String,
    initialText: String,
    saveLabel: String,
    error: String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val original = remember(initialText) { ProviderConfigCodec.parseObject(initialText) }
    var root by remember(initialText) { mutableStateOf(original ?: JsonObject(emptyMap())) }
    var jsonText by rememberSaveable(initialText) { mutableStateOf(initialText) }
    var jsonError by remember { mutableStateOf<String?>(null) }
    val kind = root.textOf("kind")
    val sections = sectionsFor(kind)
    // A config that isn't JSON yet, or of a kind the form doesn't know, opens as text
    var mode by rememberSaveable(initialText) { mutableStateOf(if (original != null && sectionsFor(original.textOf("kind")) != null) EditorMode.FORM else EditorMode.JSON) }
    var confirmDiscard by remember { mutableStateOf(false) }
    // The pages opened from the first one, the one on top last
    val stack = remember(initialText) { mutableStateListOf<Page>() }
    val stash = remember(initialText) { mutableMapOf<List<String>, JsonElement>() }
    val pageStates = rememberSaveableStateHolder()

    // The text to save: the form's config, written out only when it differs from the text, so formatting isn't lost
    fun currentText(): String = when (mode) {
        EditorMode.JSON -> jsonText
        EditorMode.FORM -> if (ProviderConfigCodec.parseObject(jsonText) == root) jsonText else ProviderConfigCodec.format(root)
    }
    val changed = when (mode) {
        EditorMode.JSON -> jsonText != initialText
        EditorMode.FORM -> root != original
    }
    val close = { if (changed) confirmDiscard = true else onDismiss() }

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            // Down to the edge of the screen: the pages scroll on under the navigation bar, with room for it at their end
            BoxWithConstraints(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
                val twoPane = maxWidth >= TwoPaneMinWidth
                val formShown = mode == EditorMode.FORM && sections != null
                // Two panes always show a page next to the first one: the first part that has a page of its own
                val firstPaged = sections?.firstOrNull { !it.startOpen }
                LaunchedEffect(twoPane, formShown, firstPaged) {
                    if (twoPane && formShown && stack.isEmpty() && firstPaged != null) stack.add(Page.OfSection(firstPaged))
                }
                val lowest = if (twoPane && firstPaged != null) 1 else 0
                val pop: () -> Unit = { stack.removeAt(stack.lastIndex) }
                val goBack: () -> Unit = { if (formShown && stack.size > lowest) pop() else close() }
                BackHandler(onBack = goBack)

                val form = FormState(
                    root = root,
                    onChange = { root = it },
                    open = { stack.add(it) },
                    back = { if (stack.size > lowest) pop() },
                    stash = stash
                )
                // From the first page, a page replaces whatever was open, which matters with two panes
                val openFromFirst: (Page) -> Unit = { stack.clear(); stack.add(it) }
                val configName = root.textOf("name")?.takeIf { it.isNotBlank() }
                // On one pane the page on top fills the screen, and the bar is its own
                val fullPage = if (formShown && !twoPane) stack.lastOrNull() else null

                Column(modifier = Modifier.fillMaxSize()) {
                    EditorTopBar(
                        back = fullPage != null,
                        title = fullPage?.title ?: title,
                        subtitle = if (fullPage != null) stack.trail().ifEmpty { configName.orEmpty() }
                        else listOfNotNull(configName, kindLabel(kind)).joinToString(" · "),
                        onNavigate = goBack,
                        saveLabel = saveLabel,
                        onSave = { onSave(currentText()) }
                    )

                    // Folds away as a page slides in, rather than the page jumping up by its height
                    val reduceMotion = LocalReduceMotion.current
                    AnimatedVisibility(
                        visible = fullPage == null,
                        enter = if (reduceMotion) EnterTransition.None else expandVertically(tween(250)) + fadeIn(tween(250)),
                        exit = if (reduceMotion) ExitTransition.None else shrinkVertically(tween(250)) + fadeOut(tween(150))
                    ) {
                        ModeSwitch(mode) { entry ->
                            if (entry == EditorMode.JSON) {
                                jsonText = currentText()
                                jsonError = null
                                mode = entry
                            } else {
                                val parsed = ProviderConfigCodec.parseObject(jsonText)
                                when {
                                    parsed == null -> jsonError = "The JSON can't be read. Fix it here to use the form."
                                    sectionsFor(parsed.textOf("kind")) == null ->
                                        jsonError = "The form knows the kinds generic_rest, webdav, s3 and smb. Set \"kind\" to one of them to use it."
                                    else -> {
                                        root = parsed
                                        jsonError = null
                                        // The text may have changed the kind, and with it the pages
                                        stack.clear()
                                        mode = entry
                                    }
                                }
                            }
                        }
                    }

                    (error ?: jsonError)?.let { ErrorBanner(it) }

                    when {
                        !formShown -> JsonPage(jsonText, onChange = { jsonText = it; jsonError = null }, modifier = Modifier.weight(1f))
                        twoPane -> Row(modifier = Modifier.weight(1f)) {
                            Box(modifier = Modifier.width(ListPaneWidth).fillMaxHeight()) {
                                pageStates.SaveableStateProvider(FIRST_PAGE_KEY) {
                                    FirstPage(sections, form, selected = stack.firstOrNull(), onOpen = openFromFirst)
                                }
                            }
                            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                val page = stack.lastOrNull()
                                if (page != null) PaneHeader(page.title, stack.trail(), canGoBack = stack.size > 1, onBack = goBack)
                                PageTransition(stack.size, page, pageStates, Modifier.weight(1f)) { shown ->
                                    shown?.let { PageContent(it, sections, form) }
                                }
                            }
                        }
                        else -> PageTransition(stack.size, stack.lastOrNull(), pageStates, Modifier.weight(1f)) { shown ->
                            if (shown == null) FirstPage(sections, form, selected = null, onOpen = openFromFirst)
                            else PageContent(shown, sections, form)
                        }
                    }
                }
            }
        }

        if (confirmDiscard) {
            AlertDialog(
                onDismissRequest = { confirmDiscard = false },
                title = { Text("Discard your changes?") },
                text = { Text("What you changed in this config isn't saved yet.") },
                confirmButton = { TextButton(onClick = { confirmDiscard = false; onDismiss() }) { Text("Discard") } },
                dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } }
            )
        }
    }
}

// ---- Pages ----

/** A page of the form past the first: a section, an object inside the config, one endpoint, or the fields the app doesn't read. */
private sealed interface Page {
    val title: String
    /** Tells pages apart, for what each one remembers: how far it's scrolled, what's unfolded. */
    val key: String

    data class OfSection(val section: Section) : Page {
        override val title get() = section.title
        override val key get() = "section:${section.title}"
    }

    /** An object of its own, e.g. the sign-in request, at [path]. */
    data class OfGroup(val spec: FieldSpec, val fields: List<FieldSpec>, val path: List<String>) : Page {
        override val title get() = spec.label
        override val key get() = "group:${path.joinToString("/")}"
    }

    data class OfEndpoint(val name: String) : Page {
        override val title get() = name
        override val key get() = "endpoint:$name"
        val path get() = listOf("endpoints", name)
    }

    data object OtherFields : Page {
        override val title = "Other fields"
        override val key = "other"
    }
}

/** The pages under the one on top, as "Sign-in › Username and password". */
private fun List<Page>.trail(): String = dropLast(1).joinToString(" › ") { it.title }

/**
 * What every part of the form reaches: the config being edited, and the pages.
 *
 * @param stash the objects turned off, by their path, so turning one back on brings back what it held
 */
private class FormState(
    val root: JsonObject,
    private val onChange: (JsonObject) -> Unit,
    val open: (Page) -> Unit,
    val back: () -> Unit,
    private val stash: MutableMap<List<String>, JsonElement>
) {
    fun setObject(path: List<String>, obj: JsonObject) = onChange(if (path.isEmpty()) obj else root.withAt(path, obj))

    fun setAt(path: List<String>, value: JsonElement?) = onChange(root.withAt(path, value))

    /** Turns the object at [path] on (with what it held before, or what it needs) or off. */
    fun turn(path: List<String>, fields: List<FieldSpec>, on: Boolean) {
        if (on) {
            setAt(path, stash.remove(path) ?: startingObject(fields))
        } else {
            root.valueAt(path)?.let { stash[path] = it }
            setAt(path, null)
        }
    }
}

/** A new object of [fields]: the ones it can't do without, at their defaults. */
private fun startingObject(fields: List<FieldSpec>): JsonObject =
    JsonObject(fields.filter { it.required }.mapNotNull { spec -> spec.default?.let { spec.key to it } }.toMap(LinkedHashMap()))

/** What a section's page edits: the fields of its one object when that's all it holds (the sign-in, the details), at that object. */
private fun Section.content(): Pair<List<String>, List<FieldSpec>> {
    val only = fields.singleOrNull()
    val group = only?.kind as? FieldKind.Group
    return if (only != null && group != null) listOf(only.key) to group.fields else emptyList<String>() to fields
}

@Composable
private fun PageContent(page: Page, sections: List<Section>, form: FormState) {
    when (page) {
        is Page.OfSection -> SectionPage(page.section, form)
        is Page.OfGroup -> GroupPage(page, form)
        is Page.OfEndpoint -> EndpointPage(page, form)
        Page.OtherFields -> OtherFieldsPage(sections, form)
    }
}

/** Going deeper slides the new page in from the end, coming back from the start, as in the settings. */
@Composable
private fun PageTransition(
    depth: Int,
    page: Page?,
    states: SaveableStateHolder,
    modifier: Modifier,
    content: @Composable (Page?) -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    AnimatedContent(
        targetState = depth to page,
        transitionSpec = {
            val deeper = targetState.first > initialState.first
            val shallower = targetState.first < initialState.first
            val transition: ContentTransform = when {
                reduceMotion || (!deeper && !shallower) -> fadeIn(tween(150)) togetherWith fadeOut(tween(100))
                deeper -> (slideInHorizontally(tween(250)) { it / 4 } + fadeIn(tween(250))) togetherWith
                    (slideOutHorizontally(tween(250)) { -it / 4 } + fadeOut(tween(150)))
                else -> (slideInHorizontally(tween(250)) { -it / 4 } + fadeIn(tween(250))) togetherWith
                    (slideOutHorizontally(tween(250)) { it / 4 } + fadeOut(tween(150)))
            }
            transition.using(SizeTransform(clip = false) { _, _ -> snap() })
        },
        label = "configPage",
        modifier = modifier.fillMaxWidth()
    ) { (_, shown) ->
        states.SaveableStateProvider(shown?.key ?: FIRST_PAGE_KEY) { content(shown) }
    }
}

/** A page's scrolling column: its fields one under the other, no wider than reads well, centred on a wide screen. */
@Composable
private fun PageColumn(maxWidth: Dp = ContentMaxWidth, content: @Composable ColumnScope.() -> Unit) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize().imePadding()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = maxWidth).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content
            )
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
        DotScrollbar(state = scrollState)
    }
}

/**
 * The first page: the sections that matter first (the name, the address) as fields, then a row for each other part of
 * the config, with what it holds, that opens its page.
 *
 * @param selected the page open next to this one, with two panes, so its row shows it
 */
@Composable
private fun FirstPage(sections: List<Section>, form: FormState, selected: Page?, onOpen: (Page) -> Unit) {
    val others = form.root.keys - describedKeys(sections)
    PageColumn {
        sections.filter { it.startOpen }.forEach { section ->
            SectionTitle(section.title)
            FieldsEditor(section.fields, emptyList(), form)
        }
        val paged = sections.filterNot { it.startOpen }
        if (paged.isNotEmpty() || others.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
                paged.forEach { section ->
                    val page = Page.OfSection(section)
                    val (path, fields) = section.content()
                    val obj = form.root.at(path)
                    NavRow(
                        title = section.title,
                        summary = summaryOf(fields, obj).ifEmpty { "Nothing set yet" },
                        problem = hasProblem(fields, obj),
                        selected = selected == page,
                        onClick = { onOpen(page) }
                    )
                }
                if (others.isNotEmpty()) {
                    NavRow(
                        title = Page.OtherFields.title,
                        summary = others.joinToString(", "),
                        selected = selected == Page.OtherFields,
                        onClick = { onOpen(Page.OtherFields) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionPage(section: Section, form: FormState) {
    val (path, fields) = section.content()
    PageColumn {
        Intro(section.summary)
        FieldsEditor(fields, path, form)
    }
}

/** An object inside the config. One that can be left out altogether has a switch at the top, its fields under it while it's on. */
@Composable
private fun GroupPage(page: Page.OfGroup, form: FormState) {
    val spec = page.spec
    val on = form.root.valueAt(page.path) is JsonObject
    PageColumn {
        Intro(spec.help)
        if (spec.canTurnOff) MainSwitch(spec.label, on) { form.turn(page.path, page.fields, it) }
        if (on || !spec.canTurnOff) FieldsEditor(page.fields, page.path, form)
    }
}

@Composable
private fun EndpointPage(page: Page.OfEndpoint, form: FormState) {
    val info = KnownEndpoints.firstOrNull { it.name == page.name }
    var confirmRemove by remember { mutableStateOf(false) }
    PageColumn {
        Intro(info?.let { "${it.area}. ${it.help}" } ?: "Not a name this app uses: it's kept, but nothing calls it.")
        FieldsEditor(info?.fields ?: endpointFields(), page.path, form)
        OutlinedButton(
            onClick = { confirmRemove = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Remove endpoint")
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${page.name}?") },
            text = { Text(info?.let { "The app stops using it for: ${it.help.removeSuffix(".").lowercase()}." } ?: "It isn't used by the app.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    form.setAt(page.path, null)
                    form.back()
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Keep") } }
        )
    }
}

/** Fields the app doesn't read, kept as they are and editable as JSON. */
@Composable
private fun OtherFieldsPage(sections: List<Section>, form: FormState) {
    val keys = form.root.keys - describedKeys(sections)
    PageColumn {
        Intro("Fields this version of the app doesn't read, e.g. notes or fields for a newer version. They're kept as they are.")
        keys.forEach { key ->
            RawJsonField(key, form.root.getValue(key), onValue = { form.setObject(emptyList(), form.root.with(key, it)) })
        }
    }
}

@Composable
private fun JsonPage(text: String, onChange: (String) -> Unit, modifier: Modifier) {
    Box(modifier = modifier) {
        PageColumn(maxWidth = 960.dp) {
            OutlinedTextField(
                value = text,
                onValueChange = onChange,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                visualTransformation = rememberCodeHighlighting(remember { languageFor("config.json") }),
                modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp)
            )
            Text(
                "The config as JSON, as it's exported.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---- The editor's frame ----

@Composable
private fun EditorTopBar(
    back: Boolean,
    title: String,
    subtitle: String,
    onNavigate: () -> Unit,
    saveLabel: String,
    onSave: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
    ) {
        IconButton(onClick = onNavigate) {
            if (back) Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            else Icon(Icons.Filled.Close, contentDescription = "Close")
        }
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp, end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Button(onClick = onSave) { Text(saveLabel) }
    }
}

/** The title of the page open next to the first one, with two panes; with the way back when it's deeper than a section. */
@Composable
private fun PaneHeader(title: String, trail: String, canGoBack: Boolean, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = if (canGoBack) 4.dp else 16.dp, end = 16.dp)
    ) {
        if (canGoBack) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Spacer(Modifier.width(4.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (trail.isNotEmpty()) {
                Text(trail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ModeSwitch(mode: EditorMode, onSelect: (EditorMode) -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            EditorMode.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = mode == entry,
                    onClick = { if (entry != mode) onSelect(entry) },
                    shape = SegmentedButtonDefaults.itemShape(index, EditorMode.entries.size)
                ) { Text(entry.label) }
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(12.dp)
        )
    }
}

// ---- Building blocks ----

private val RowShape = RoundedCornerShape(16.dp)

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

/** What a page is for, at its top. */
@Composable
private fun Intro(text: String) {
    if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * A row that opens a page: its [title], and a [summary] of what the page holds, in the error colour when something in
 * it is missing ([problem]), as that's no longer in sight.
 */
@Composable
private fun NavRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    problem: Boolean = false,
    monospace: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RowShape)
            .background(if (selected) colors.secondaryContainer else colors.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = if (monospace) FontFamily.Monospace else null,
                color = if (selected) colors.onSecondaryContainer else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (summary.isNotEmpty()) {
                Text(
                    if (problem) "Incomplete · $summary" else summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (problem) colors.error else colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/** The switch at the top of a page for something that's either used or not, as in the system settings. */
@Composable
private fun MainSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(if (checked) colors.primaryContainer else colors.surfaceContainerHigh)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (checked) colors.onPrimaryContainer else colors.onSurface,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A short line for each set field of [obj], the first few, to show what a page holds without opening it. */
private fun summaryOf(fields: List<FieldSpec>, obj: JsonObject): String =
    fields.filter { it.shownWhen(obj) }.mapNotNull { spec ->
        val value = obj[spec.key]?.takeUnless { it is JsonNull }
            ?: return@mapNotNull if (spec.kind == FieldKind.Screens) "Every tab the host can back" else null
        when (val kind = spec.kind) {
            is FieldKind.Text -> (value as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && !kind.multiline }
            FieldKind.Number -> "${spec.label} ${value.displayText()}"
            FieldKind.Toggle -> spec.label.takeIf { (value as? JsonPrimitive)?.content == "true" }
            is FieldKind.Choice -> kind.options.firstOrNull { it.value.equals((value as? JsonPrimitive)?.content, ignoreCase = true) }?.label
            is FieldKind.Group -> spec.label
            is FieldKind.KeyValues -> "${spec.label} (${(value as? JsonObject)?.size ?: 0})"
            FieldKind.StatusCodes -> value.displayText()
            FieldKind.Screens -> (value as? JsonArray)?.mapNotNull { ScreenEntry.of(it)?.title() }?.joinToString(", ")
            FieldKind.Endpoints -> (value as? JsonObject)?.size?.let { if (it == 1) "1 endpoint" else "$it endpoints" }
        }
    }.distinct().take(4).joinToString(" · ")

/** Whether something [obj] can't do without is missing, in it or in an object inside it. */
private fun hasProblem(fields: List<FieldSpec>, obj: JsonObject): Boolean = fields.filter { it.shownWhen(obj) }.any { spec ->
    val value = obj[spec.key]?.takeUnless { it is JsonNull }
    when (val kind = spec.kind) {
        is FieldKind.Group -> (value != null || spec.required) && hasProblem(kind.fields, value as? JsonObject ?: JsonObject(emptyMap()))
        FieldKind.Endpoints -> {
            val endpoints = value as? JsonObject
            endpoints.isNullOrEmpty() || endpoints.any { (name, endpoint) -> endpointHasProblem(name, endpoint) }
        }
        // A field with a default can be left out, the default stands in
        else -> spec.required && spec.default == null && (value == null || (value as? JsonPrimitive)?.let { it.isString && it.content.isBlank() } == true)
    }
}

private fun endpointHasProblem(name: String, endpoint: JsonElement): Boolean =
    hasProblem(KnownEndpoints.firstOrNull { it.name == name }?.fields ?: endpointFields(), endpoint as? JsonObject ?: JsonObject(emptyMap()))

@Composable
private fun RawJsonField(key: String, value: JsonElement, onValue: (JsonElement?) -> Unit) {
    var text by remember(key) { mutableStateOf(value.toString()) }
    var invalid by remember(key) { mutableStateOf(false) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            val parsed = runCatching { lenientJson.parseToJsonElement(it) }.getOrNull()
            invalid = parsed == null
            parsed?.let(onValue)
        },
        label = { Text(key) },
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        isError = invalid,
        supportingText = if (invalid) ({ Text("Not valid JSON yet: the last valid value is kept") }) else null,
        trailingIcon = { IconButton(onClick = { onValue(null) }) { Icon(Icons.Filled.Delete, contentDescription = "Remove $key") } },
        modifier = Modifier.fillMaxWidth()
    )
}

// ---- Fields ----

/**
 * The fields of the object at [path]: the common ones, then the rest folded under "More options", unfolded from the
 * start when one of them is set. Every field is always there to fill in; one that's emptied is left out of the config,
 * so the app uses its default. A field that only means something given others (see [FieldSpec.shownWhen]) only shows
 * then.
 */
@Composable
private fun FieldsEditor(fields: List<FieldSpec>, path: List<String>, form: FormState) {
    val obj = form.root.at(path)
    val visible = fields.filter { it.shownWhen(obj) }
    val (main, more) = visible.partition { it.common }
    main.forEach { spec -> FieldEditor(spec, path, obj, form) }
    if (more.isNotEmpty()) MoreOptions(more, path, obj, form)
}

@Composable
private fun MoreOptions(fields: List<FieldSpec>, path: List<String>, obj: JsonObject, form: FormState) {
    val setCount = fields.count { obj[it.key] != null && obj[it.key] !is JsonNull }
    var open by rememberSaveable(path.joinToString("/")) { mutableStateOf(setCount > 0) }
    val reduceMotion = LocalReduceMotion.current
    Column(modifier = if (reduceMotion) Modifier else Modifier.animateContentSize()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { open = !open }
                .padding(vertical = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text("More options", style = MaterialTheme.typography.titleSmall)
                if (!open) {
                    Text(
                        listOfNotNull("$setCount set".takeIf { setCount > 0 }, fields.joinToString(", ") { it.label }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (open) "Fold" else "Unfold")
        }
        if (open) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 4.dp)) {
                fields.forEach { spec -> FieldEditor(spec, path, obj, form) }
            }
        }
    }
}

/** What a field starts as when it's added: its default, or an empty value of its kind. */
private fun initialValue(spec: FieldSpec): JsonElement = spec.default ?: when (val kind = spec.kind) {
    is FieldKind.Text -> JsonPrimitive("")
    FieldKind.Number -> JsonPrimitive(0)
    FieldKind.Toggle -> JsonPrimitive(false)
    is FieldKind.Choice -> JsonPrimitive(kind.options.first().value)
    is FieldKind.Group, is FieldKind.KeyValues, FieldKind.Endpoints -> JsonObject(emptyMap())
    FieldKind.StatusCodes -> JsonArray(listOf(JsonPrimitive(200)))
    FieldKind.Screens -> JsonArray(HostScreen.entries.map { JsonPrimitive(it.name) })
}

/** The help under a field, with its default when it has one. */
private fun helpText(spec: FieldSpec, defaultText: String? = spec.default?.displayText()): String =
    listOfNotNull(spec.help.takeIf { it.isNotEmpty() }, defaultText?.let { "Default: $it." }).joinToString(" ")

@Composable
private fun FieldEditor(spec: FieldSpec, path: List<String>, obj: JsonObject, form: FormState) {
    // A null (the templates write unset fields that way) is the same as leaving the field out
    val value = obj[spec.key]?.takeUnless { it is JsonNull }
    val onValue: (JsonElement?) -> Unit = { form.setObject(path, obj.with(spec.key, it)) }
    when (val kind = spec.kind) {
        is FieldKind.Text -> TextField(spec, value, kind.multiline, onValue)
        FieldKind.Number -> NumberField(spec, value, onValue)
        FieldKind.StatusCodes -> StatusCodesField(spec, value, onValue)
        FieldKind.Toggle -> ToggleField(spec, value, onValue)
        is FieldKind.Choice -> ChoiceField(spec, kind, value, onValue)
        is FieldKind.Group -> GroupRow(spec, kind, value, path + spec.key, form)
        is FieldKind.KeyValues -> KeyValuesField(spec, kind, value, onValue)
        FieldKind.Screens -> ScreensField(spec, value, onValue)
        FieldKind.Endpoints -> EndpointsField(value, form, onValue)
    }
}

@Composable
private fun TextField(spec: FieldSpec, value: JsonElement?, multiline: Boolean, onValue: (JsonElement?) -> Unit) {
    val text = (value as? JsonPrimitive)?.content.orEmpty()
    val missing = spec.required && text.isBlank()
    val defaultHint = spec.default?.displayText()?.takeIf { (spec.default as? JsonPrimitive)?.content?.isNotEmpty() == true }
    OutlinedTextField(
        value = text,
        // Emptied, a field that may be left out is: the app uses its default. One it can't do without stays, empty
        onValueChange = { onValue(if (it.isEmpty() && !spec.required) null else JsonPrimitive(it)) },
        label = { Text(spec.label) },
        placeholder = (spec.placeholder ?: defaultHint)?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        supportingText = { Text(if (missing) "Needed. ${spec.help}" else helpText(spec)) },
        isError = missing,
        singleLine = !multiline,
        minLines = if (multiline) 3 else 1,
        textStyle = if (multiline) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun NumberField(spec: FieldSpec, value: JsonElement?, onValue: (JsonElement?) -> Unit) {
    val current = (value as? JsonPrimitive)?.content.orEmpty()
    var text by remember(spec.key, current) { mutableStateOf(current) }
    val invalid = text.isNotEmpty() && text.toLongOrNull() == null
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            text = typed
            if (typed.isEmpty()) onValue(null) else typed.toLongOrNull()?.let { onValue(JsonPrimitive(it)) }
        },
        label = { Text(spec.label) },
        placeholder = spec.default?.displayText()?.let { { Text(it) } },
        supportingText = { Text(if (invalid) "A whole number" else helpText(spec)) },
        isError = invalid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun StatusCodesField(spec: FieldSpec, value: JsonElement?, onValue: (JsonElement?) -> Unit) {
    val current = (value as? JsonArray)?.joinToString(", ") { (it as? JsonPrimitive)?.content.orEmpty() }.orEmpty()
    var text by remember(spec.key, current) { mutableStateOf(current) }
    fun parse(typed: String) = typed.split(',', ' ').filter { it.isNotBlank() }.map { it.trim().toIntOrNull() }
    val invalid = parse(text).any { it == null }
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            text = typed
            val parsed = parse(typed)
            when {
                parsed.isEmpty() -> onValue(null)
                parsed.none { it == null } -> onValue(JsonArray(parsed.map { JsonPrimitive(it) }))
            }
        },
        label = { Text(spec.label) },
        placeholder = spec.default?.displayText()?.let { { Text(it) } },
        supportingText = { Text(if (invalid) "Numbers separated by commas, like 200, 201" else helpText(spec)) },
        isError = invalid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ToggleField(spec: FieldSpec, value: JsonElement?, onValue: (JsonElement?) -> Unit) {
    val checked = ((value ?: spec.default) as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = { onValue(JsonPrimitive(it)) })
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(spec.label, style = MaterialTheme.typography.bodyLarge)
            Text(helpText(spec), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A field's name over a control that has no label of its own. */
@Composable
private fun FieldTitle(label: String) {
    Text(label, style = MaterialTheme.typography.bodyLarge)
}

/**
 * One of a few choices: side by side while each has room for its name (less of it with larger text), otherwise, and
 * for longer lists, a dropdown with each choice's help.
 */
@Composable
private fun ChoiceField(spec: FieldSpec, kind: FieldKind.Choice, value: JsonElement?, onValue: (JsonElement?) -> Unit) {
    val currentValue = ((value ?: spec.default) as? JsonPrimitive)?.content
    val current = kind.options.firstOrNull { it.value.equals(currentValue, ignoreCase = true) }
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldTitle(spec.label)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val sideBySide = kind.options.size <= 4 && maxWidth / kind.options.size >= SegmentMinWidth * fontScale
            if (sideBySide) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    kind.options.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = option == current,
                            onClick = { onValue(JsonPrimitive(option.value)) },
                            shape = SegmentedButtonDefaults.itemShape(index, kind.options.size),
                            icon = {}
                        ) { Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            } else {
                var open by remember { mutableStateOf(false) }
                Box {
                    OutlinedTextField(
                        value = current?.label ?: currentValue.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Over the field, so a tap anywhere on it opens the list
                    Box(modifier = Modifier.matchParentSize().clip(RoundedCornerShape(4.dp)).clickable { open = true })
                    // As wide as the field, each choice with its help under it
                    AppMenu(expanded = open, onDismiss = { open = false }, matchAnchorWidth = true) {
                        kind.options.forEach { option ->
                            AppMenuItem(
                                text = option.label,
                                supportingText = option.help,
                                active = option == current,
                                onClick = {
                                    onValue(JsonPrimitive(option.value))
                                    open = false
                                }
                            )
                        }
                    }
                }
            }
        }
        // The default by the name it has here, not the one the config uses
        val defaultLabel = spec.default?.let { d -> kind.options.firstOrNull { it.value == (d as? JsonPrimitive)?.content }?.label ?: d.displayText() }
        val help = listOfNotNull(current?.help, helpText(spec, defaultLabel).takeIf { it.isNotEmpty() }).joinToString(" · ")
        if (help.isNotEmpty()) Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** An object of its own: a row with what it holds, opening its page. */
@Composable
private fun GroupRow(spec: FieldSpec, kind: FieldKind.Group, value: JsonElement?, path: List<String>, form: FormState) {
    val obj = value as? JsonObject
    NavRow(
        title = spec.label,
        summary = when {
            spec.canTurnOff && obj == null -> "Off"
            else -> summaryOf(kind.fields, obj ?: JsonObject(emptyMap())).ifEmpty { "Nothing set yet" }
        },
        problem = (obj != null || spec.required) && hasProblem(kind.fields, obj ?: JsonObject(emptyMap())),
        onClick = { form.open(Page.OfGroup(spec, kind.fields, path)) }
    )
}

// ---- Names and values ----

private fun JsonElement.asText(): String = (this as? JsonPrimitive)?.content ?: toString()

/**
 * Names with a value each, as a list that reads like the config: the name, its value under it. A tap edits one in a
 * dialog, where it can also be removed; "Add" offers the names the app reads.
 */
@Composable
private fun KeyValuesField(spec: FieldSpec, kind: FieldKind.KeyValues, value: JsonElement?, onValue: (JsonElement?) -> Unit) {
    val entries = value as? JsonObject ?: JsonObject(emptyMap())
    var editing by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    // A renamed entry keeps its place; the others keep their values as they are, even when they aren't text
    fun save(oldKey: String?, key: String, text: String) {
        val map = LinkedHashMap<String, JsonElement>()
        entries.forEach { (k, v) -> if (k == oldKey) map[key] = JsonPrimitive(text) else map[k] = v }
        if (oldKey == null) map[key] = JsonPrimitive(text)
        onValue(JsonObject(map))
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            FieldTitle(spec.label)
            if (spec.help.isNotEmpty()) Text(spec.help, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if (entries.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().clip(RowShape).background(colors.surfaceContainerLow)) {
                entries.entries.forEachIndexed { index, (key, entryValue) ->
                    if (index > 0) HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
                    val text = entryValue.asText()
                    Column(modifier = Modifier.fillMaxWidth().clickable { editing = key }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(key, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text.ifEmpty { "empty" },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = if (text.isEmpty()) null else FontFamily.Monospace,
                            color = colors.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        TextButton(onClick = { adding = true }, contentPadding = ButtonDefaults.TextButtonWithIconContentPadding) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add")
        }
    }

    if (adding) {
        KeyValueDialog(
            title = "Add to ${spec.label.lowercase()}",
            kind = kind,
            initialKey = null,
            initialValue = "",
            taken = entries.keys,
            onSave = { key, text -> save(null, key, text); adding = false },
            onRemove = null,
            onDismiss = { adding = false }
        )
    }
    editing?.let { key ->
        KeyValueDialog(
            title = spec.label,
            kind = kind,
            initialKey = key,
            initialValue = entries[key]?.asText().orEmpty(),
            taken = entries.keys,
            onSave = { newKey, text -> save(key, newKey, text); editing = null },
            onRemove = {
                val rest = JsonObject(entries - key)
                onValue(if (rest.isEmpty()) null else rest)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun KeyValueDialog(
    title: String,
    kind: FieldKind.KeyValues,
    initialKey: String?,
    initialValue: String,
    taken: Set<String>,
    onSave: (String, String) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var key by remember { mutableStateOf(initialKey.orEmpty()) }
    var text by remember { mutableStateOf(initialValue) }
    val trimmed = key.trim()
    val clash = trimmed != initialKey && trimmed in taken
    val known = kind.suggestions.firstOrNull { it.key == trimmed }
    val mono = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = clash,
                    supportingText = when {
                        clash -> ({ Text("Already in the list") })
                        known != null -> ({ Text(known.help) })
                        else -> null
                    },
                    textStyle = mono,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Value") },
                    singleLine = true,
                    supportingText = kind.valueHint.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
                    textStyle = mono,
                    modifier = Modifier.fillMaxWidth()
                )
                // The names the app reads, filled in with what usually goes with them
                val unused = kind.suggestions.filter { it.key !in taken }
                if (initialKey == null && unused.isNotEmpty()) {
                    Text("Names the app reads", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        unused.forEach { suggestion ->
                            FilterChip(
                                selected = trimmed == suggestion.key,
                                onClick = {
                                    key = suggestion.key
                                    // Only over what's empty or another suggestion's value, never over something typed
                                    if (text.isEmpty() || kind.suggestions.any { it.value == text }) text = suggestion.value
                                },
                                label = { Text(suggestion.key, fontFamily = FontFamily.Monospace) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(trimmed, text) }, enabled = trimmed.isNotEmpty() && !clash) {
                Text(if (initialKey == null) "Add" else "Save")
            }
        },
        dismissButton = {
            Row {
                onRemove?.let {
                    TextButton(onClick = it, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Remove") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

// ---- Screens ----

private fun HostScreen.label(): String = when (this) {
    HostScreen.UPLOAD -> "Upload"
    HostScreen.FILES -> "Files"
    HostScreen.LISTS -> "Lists"
    HostScreen.FILESYSTEM -> "Filesystem"
}

private fun HostScreen.help(): String = when (this) {
    HostScreen.UPLOAD -> "Picking files to upload"
    HostScreen.FILES -> "A flat list of every file on the account"
    HostScreen.LISTS -> "Shareable lists of files"
    HostScreen.FILESYSTEM -> "The folder browser"
}

/** The actions a screen can leave out, by the names a config uses for them. */
private val HideableActions = listOf("UPLOAD" to "Upload", "MKDIR" to "New folder", "RENAME" to "Rename and move", "DELETE" to "Delete")

/** One screen of the config, with what it may hold besides its name, which is kept as it is. */
private data class ScreenEntry(val screen: String, val name: String?, val disabled: List<String>, val extra: JsonObject) {
    fun toJson(): JsonElement {
        if (name.isNullOrBlank() && disabled.isEmpty() && extra.isEmpty()) return JsonPrimitive(screen)
        return JsonObject(LinkedHashMap<String, JsonElement>().apply {
            put("screen", JsonPrimitive(screen))
            if (!name.isNullOrBlank()) put("name", JsonPrimitive(name))
            if (disabled.isNotEmpty()) put("disabled_capabilities", JsonArray(disabled.map { JsonPrimitive(it) }))
            putAll(extra)
        })
    }

    /** What the tab is called: its own name, or the screen's. */
    fun title(): String = name?.takeIf { it.isNotBlank() } ?: HostScreen.entries.firstOrNull { it.name == screen }?.label() ?: screen

    companion object {
        fun of(element: JsonElement): ScreenEntry? = when {
            element is JsonPrimitive && element.isString -> ScreenEntry(element.content.trim().uppercase(), null, emptyList(), JsonObject(emptyMap()))
            element is JsonObject -> element.textOf("screen")?.let { screen ->
                ScreenEntry(
                    screen = screen.trim().uppercase(),
                    name = element.textOf("name"),
                    disabled = (element["disabled_capabilities"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.uppercase() }.orEmpty(),
                    extra = JsonObject(element - setOf("screen", "name", "disabled_capabilities"))
                )
            }
            else -> null
        }
    }
}

/**
 * The tabs: left to the app (every one the host can back), or chosen, each ticked one in the order shown. A tab's ⋮
 * menu (or a tap on it) gives its own name and the actions it leaves out, and moves it.
 */
@Composable
private fun ScreensField(spec: FieldSpec, value: JsonElement?, onValue: (JsonElement?) -> Unit) {
    val entries = (value as? JsonArray)?.mapNotNull { ScreenEntry.of(it) }
    val chosen = entries != null
    fun write(list: List<ScreenEntry>) = onValue(JsonArray(list.map { it.toJson() }))

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = chosen, role = Role.Switch, onValueChange = { on -> onValue(if (on) initialValue(spec) else null) })
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Choose the tabs myself", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (chosen) "Ticked tabs show in this order. A tab the host can't back is left out." else spec.help,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = chosen, onCheckedChange = null)
        }
        if (entries != null) {
            val unticked = HostScreen.entries.filter { screen -> entries.none { it.screen == screen.name } }
            Column(modifier = Modifier.fillMaxWidth().clip(RowShape).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                entries.forEachIndexed { index, entry ->
                    ScreenRow(
                        entry = entry,
                        ticked = true,
                        onTick = { write(entries - entry) },
                        onChange = { changed -> write(entries.toMutableList().also { it[index] = changed }) },
                        onUp = if (index > 0) ({ write(entries.toMutableList().also { java.util.Collections.swap(it, index, index - 1) }) }) else null,
                        onDown = if (index < entries.lastIndex) ({ write(entries.toMutableList().also { java.util.Collections.swap(it, index, index + 1) }) }) else null
                    )
                }
                unticked.forEach { screen ->
                    val entry = ScreenEntry(screen.name, null, emptyList(), JsonObject(emptyMap()))
                    ScreenRow(entry = entry, ticked = false, onTick = { write(entries + entry) }, onChange = {}, onUp = null, onDown = null)
                }
            }
        }
    }
}

@Composable
private fun ScreenRow(
    entry: ScreenEntry,
    ticked: Boolean,
    onTick: () -> Unit,
    onChange: (ScreenEntry) -> Unit,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?
) {
    val screen = HostScreen.entries.firstOrNull { it.name == entry.screen }
    var editing by remember(entry.screen) { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            // Ticked, a tap opens its name and actions; not ticked, it ticks it
            .clickable { if (ticked) editing = true else onTick() }
            .padding(end = 4.dp)
    ) {
        Checkbox(checked = ticked, onCheckedChange = { onTick() })
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                entry.title(),
                style = MaterialTheme.typography.bodyLarge,
                color = if (ticked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                listOfNotNull(
                    screen?.help(),
                    "${entry.disabled.size} left out".takeIf { entry.disabled.isNotEmpty() }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (ticked) {
            OverflowMenuButton(contentDescription = "${entry.title()} options") { close ->
                AppMenuItem("Name and actions", leadingIcon = Icons.Filled.Edit, onClick = { close(); editing = true })
                AppMenuItem("Move up", leadingIcon = Icons.Filled.ArrowUpward, enabled = onUp != null, onClick = { close(); onUp?.invoke() })
                AppMenuItem("Move down", leadingIcon = Icons.Filled.ArrowDownward, enabled = onDown != null, onClick = { close(); onDown?.invoke() })
            }
        }
    }
    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(screen?.label() ?: entry.screen) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = entry.name.orEmpty(),
                        onValueChange = { onChange(entry.copy(name = it)) },
                        label = { Text("Tab name") },
                        placeholder = { Text(screen?.label() ?: entry.screen) },
                        supportingText = { Text("Replaces the tab's label and title, e.g. Media for a share of films.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Leave out on this tab", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HideableActions.forEach { (capability, label) ->
                            val hidden = capability in entry.disabled
                            FilterChip(
                                selected = hidden,
                                onClick = { onChange(entry.copy(disabled = if (hidden) entry.disabled - capability else entry.disabled + capability)) },
                                label = { Text(label) }
                            )
                        }
                    }
                    Text(
                        "Only takes away: an action the host can't do isn't shown anyway.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = { editing = false }) { Text("Done") } }
        )
    }
}

// ---- Endpoints ----

/** A REST config's requests, by what they unlock, a row each that opens its page; the ones not set are offered in a list. */
@Composable
private fun EndpointsField(value: JsonElement?, form: FormState, onValue: (JsonElement?) -> Unit) {
    val endpoints = value as? JsonObject ?: JsonObject(emptyMap())
    val known = KnownEndpoints.associateBy { it.name }
    val byArea = endpoints.keys.groupBy { known[it]?.area ?: "Other" }
    val areas = KnownEndpoints.map { it.area }.distinct() + "Other"
    var adding by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (endpoints.isEmpty()) {
            Text(
                "None yet: a config needs at least one. Start with browse_list for a folder browser, or list for a flat file list.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        areas.forEach { area ->
            val names = byArea[area].orEmpty()
            if (names.isEmpty()) return@forEach
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionTitle(area)
                Spacer(Modifier.size(4.dp))
                names.forEach { name ->
                    val endpoint = endpoints[name] as? JsonObject ?: JsonObject(emptyMap())
                    NavRow(
                        title = name,
                        summary = listOfNotNull(endpoint.textOf("method"), endpoint.textOf("path")?.ifBlank { null }).joinToString(" ").ifEmpty { "No path yet" },
                        problem = endpointHasProblem(name, endpoint),
                        monospace = true,
                        onClick = { form.open(Page.OfEndpoint(name)) }
                    )
                }
            }
        }

        val missing = KnownEndpoints.filter { it.name !in endpoints }
        if (missing.isNotEmpty()) {
            OutlinedButton(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add an endpoint")
            }
        }
        if (adding) {
            AddEndpointDialog(
                missing = missing,
                onPick = { info ->
                    adding = false
                    onValue(endpoints.with(info.name, JsonObject(mapOf("method" to JsonPrimitive("GET"), "path" to JsonPrimitive("")))))
                    form.open(Page.OfEndpoint(info.name))
                },
                onDismiss = { adding = false }
            )
        }
    }
}

/** The endpoints a config doesn't have yet, by what they unlock, each with what it's for. */
@Composable
private fun AddEndpointDialog(missing: List<EndpointInfo>, onPick: (EndpointInfo) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add an endpoint") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                missing.groupBy { it.area }.forEach { (area, infos) ->
                    Text(
                        area,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                    infos.forEach { info ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(info) }
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(info.name, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
                            Text(info.help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
