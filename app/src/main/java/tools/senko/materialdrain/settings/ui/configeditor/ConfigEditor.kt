@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package tools.senko.materialdrain.settings.ui.configeditor

import tools.senko.materialdrain.ui.theme.AppShapes
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import tools.senko.materialdrain.ui.icons.automirrored.filled.ArrowBack
import tools.senko.materialdrain.ui.icons.automirrored.filled.KeyboardArrowRight
import tools.senko.materialdrain.ui.icons.filled.Add
import tools.senko.materialdrain.ui.icons.filled.Close
import tools.senko.materialdrain.ui.icons.filled.Delete
import tools.senko.materialdrain.ui.icons.filled.Edit
import tools.senko.materialdrain.ui.icons.filled.ExpandLess
import tools.senko.materialdrain.ui.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
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
import tools.senko.materialdrain.ui.components.DotScrollbar
import tools.senko.materialdrain.ui.components.PAGE_TRANSITION_MS
import tools.senko.materialdrain.ui.components.pageTransition
import tools.senko.materialdrain.ui.components.rememberCodeHighlighting
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ScreenPreviews
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import tools.senko.materialdrain.R

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
internal val SegmentMinWidth = 80.dp
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
    val schema = configSchema()
    val original = remember(initialText) { ProviderConfigCodec.parseObject(initialText) }
    var root by remember(initialText) { mutableStateOf(original ?: JsonObject(emptyMap())) }
    var jsonText by rememberSaveable(initialText) { mutableStateOf(initialText) }
    var jsonError by remember { mutableStateOf<String?>(null) }
    val kind = root.textOf("kind")
    val sections = schema.sectionsFor(kind)
    // A config that isn't JSON yet, or of a kind the form doesn't know, opens as text
    var mode by rememberSaveable(initialText) { mutableStateOf(if (original != null && schema.sectionsFor(original.textOf("kind")) != null) EditorMode.FORM else EditorMode.JSON) }
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
                        else listOfNotNull(configName, schema.kindLabel(kind)).joinToString(" · "),
                        onNavigate = goBack,
                        saveLabel = saveLabel,
                        onSave = { onSave(currentText()) }
                    )

                    // Folds away as a page slides in, rather than the page jumping up by its height
                    val reduceMotion = LocalReduceMotion.current
                    AnimatedVisibility(
                        visible = fullPage == null,
                        enter = if (reduceMotion) EnterTransition.None else expandVertically(tween(PAGE_TRANSITION_MS)) + fadeIn(tween(PAGE_TRANSITION_MS)),
                        exit = if (reduceMotion) ExitTransition.None else shrinkVertically(tween(PAGE_TRANSITION_MS)) + fadeOut(tween(150))
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
                                    schema.sectionsFor(parsed.textOf("kind")) == null ->
                                        jsonError = "Set \"kind\" to generic_rest, webdav, s3 or smb to use the form."
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
                title = { Text(stringResource(R.string.settings_discard_your_changes)) },
                text = { Text(stringResource(R.string.settings_what_you_changed_in_this)) },
                confirmButton = { TextButton(onClick = { confirmDiscard = false; onDismiss() }) { Text(stringResource(R.string.settings_discard)) } },
                dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.settings_keep_editing)) } }
            )
        }
    }
}

// ---- Pages ----

/** A page of the form past the first: a section, an object inside the config, one endpoint, or the fields the app doesn't read. */
internal sealed interface Page {
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
internal class FormState(
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
            val forward = when {
                targetState.first > initialState.first -> true
                targetState.first < initialState.first -> false
                // Another section next to the first page, with two panes
                else -> null
            }
            pageTransition(forward, reduceMotion)
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
    val schema = configSchema()
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
                        summary = summaryOf(fields, obj).ifEmpty { stringResource(R.string.settings_nothing_set_yet) },
                        problem = hasProblem(fields, obj, schema),
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
    val schema = configSchema()
    val info = schema.KnownEndpoints.firstOrNull { it.name == page.name }
    var confirmRemove by remember { mutableStateOf(false) }
    PageColumn {
        Intro(info?.let { "${it.area}. ${it.help}" } ?: stringResource(R.string.settings_unknown_name_kept_but_unused))
        FieldsEditor(info?.fields ?: schema.endpointFields(), page.path, form)
        OutlinedButton(
            onClick = { confirmRemove = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_remove_endpoint))
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.settings_remove_2, page.name)) },
            text = { Text(info?.let { stringResource(R.string.settings_the_app_stops_using_it, it.help.removeSuffix(".").lowercase()) } ?: stringResource(R.string.settings_it_isn_t_used_by)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    form.setAt(page.path, null)
                    form.back()
                }) { Text(stringResource(R.string.settings_remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.settings_keep)) } }
        )
    }
}

/** Fields the app doesn't read, kept as they are and editable as JSON. */
@Composable
private fun OtherFieldsPage(sections: List<Section>, form: FormState) {
    val keys = form.root.keys - describedKeys(sections)
    PageColumn {
        Intro(stringResource(R.string.settings_fields_this_version_doesn_t))
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
            if (back) Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.files_back))
            else Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.settings_close))
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
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.files_back)) }
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
                .clip(AppShapes.Medium)
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(12.dp)
        )
    }
}

// ---- Building blocks ----

internal val RowShape = AppShapes.Large

@Composable
internal fun SectionTitle(title: String) {
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
internal fun NavRow(
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
                    if (problem) stringResource(R.string.settings_incomplete, summary) else summary,
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
            .clip(AppShapes.ExtraLarge)
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
internal fun summaryOf(fields: List<FieldSpec>, obj: JsonObject): String =
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
internal fun hasProblem(fields: List<FieldSpec>, obj: JsonObject, schema: ConfigSchema): Boolean = fields.filter { it.shownWhen(obj) }.any { spec ->
    val value = obj[spec.key]?.takeUnless { it is JsonNull }
    when (val kind = spec.kind) {
        is FieldKind.Group -> (value != null || spec.required) && hasProblem(kind.fields, value as? JsonObject ?: JsonObject(emptyMap()), schema)
        FieldKind.Endpoints -> {
            val endpoints = value as? JsonObject
            endpoints.isNullOrEmpty() || endpoints.any { (name, endpoint) -> endpointHasProblem(name, endpoint, schema) }
        }
        FieldKind.Screens -> screenEntryProblems(value).isNotEmpty()
        // A field with a default can be left out, the default stands in
        else -> spec.required && spec.default == null && (value == null || (value as? JsonPrimitive)?.let { it.isString && it.content.isBlank() } == true)
    }
}

internal fun endpointHasProblem(name: String, endpoint: JsonElement, schema: ConfigSchema): Boolean =
    hasProblem(schema.KnownEndpoints.firstOrNull { it.name == name }?.fields ?: schema.endpointFields(), endpoint as? JsonObject ?: JsonObject(emptyMap()), schema)

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
        supportingText = if (invalid) ({ Text(stringResource(R.string.settings_invalid_json_the_last_valid)) }) else null,
        trailingIcon = { IconButton(onClick = { onValue(null) }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.settings_remove_3, key)) } },
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
                .clip(AppShapes.Medium)
                .clickable { open = !open }
                .padding(vertical = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(stringResource(R.string.common_more_options), style = MaterialTheme.typography.titleSmall)
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
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (open) stringResource(R.string.settings_fold) else stringResource(R.string.settings_unfold))
        }
        if (open) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 4.dp)) {
                fields.forEach { spec -> FieldEditor(spec, path, obj, form) }
            }
        }
    }
}

/** What a field starts as when it's added: its default, or an empty value of its kind. */
internal fun initialValue(spec: FieldSpec): JsonElement = spec.default ?: when (val kind = spec.kind) {
    is FieldKind.Text -> JsonPrimitive("")
    FieldKind.Number -> JsonPrimitive(0)
    FieldKind.Toggle -> JsonPrimitive(false)
    is FieldKind.Choice -> JsonPrimitive(kind.options.first().value)
    is FieldKind.Group, is FieldKind.KeyValues, FieldKind.Endpoints -> JsonObject(emptyMap())
    FieldKind.StatusCodes -> JsonArray(listOf(JsonPrimitive(200)))
    FieldKind.Screens -> JsonArray(HostScreen.entries.map { JsonPrimitive(it.name) })
}

/** The help under a field, with its default when it has one. */
internal fun helpText(spec: FieldSpec, defaultText: String? = spec.default?.displayText()): String =
    listOfNotNull(spec.help.takeIf { it.isNotEmpty() }, defaultText?.let { "Default: $it." }).joinToString(" ")

// ---- Previews ----

/** The bundled example config of each kind the form knows (docs/provider-configs, which are the app's assets). */
private val ExampleConfigFiles = mapOf("webdav" to "webdav.json", "s3" to "s3.json", "smb" to "smb.json", "generic_rest" to "rest.json")

/** The text of the example [file]; an empty object when the preview can't read it. */
@Composable
private fun exampleConfigText(file: String): String {
    val context = LocalContext.current
    return remember(file) { runCatching { context.assets.open(file).bufferedReader().use { it.readText() } }.getOrDefault("{}") }
}

@Composable
private fun ConfigEditorPreview(kind: String, error: String? = null) {
    PreviewTheme {
        ConfigEditor(
            title = "Edit host",
            initialText = exampleConfigText(ExampleConfigFiles.getValue(kind)),
            saveLabel = "Save",
            error = error,
            onSave = {},
            onDismiss = {}
        )
    }
}

/** The first page of a WebDAV host's config: its basics, and a row for each of its other parts. */
@ScreenPreviews
@Composable
private fun ConfigEditorWebDavPreview() = ConfigEditorPreview("webdav")

@ScreenPreviews
@Composable
private fun ConfigEditorS3Preview() = ConfigEditorPreview("s3")

@ScreenPreviews
@Composable
private fun ConfigEditorSmbPreview() = ConfigEditorPreview("smb")

/** A REST host (the example API), with why the last save was refused at the top. */
@ScreenPreviews
@Composable
private fun ConfigEditorRestPreview() = ConfigEditorPreview("generic_rest", error = "The base URL has to use https.")

/** Text which isn't a config the form can show opens as text. */
@ScreenPreviews
@Composable
private fun ConfigEditorJsonPreview() {
    PreviewTheme {
        ConfigEditor(title = "Add host", initialText = "{\n  \"kind\": \"ftp\",\n  \"name\": \"Not a kind this app knows\"\n}", saveLabel = "Add", error = null, onSave = {}, onDismiss = {})
    }
}

/**
 * How many parts each kind's form has besides the one open at first (Sign-in, Endpoints, ...): the pages of the preview
 * below, by their place, as their titles are in the app's language, which a parameter provider can't read.
 */
private val PreviewSectionCounts = mapOf("webdav" to 3, "s3" to 2, "smb" to 3, "generic_rest" to 4)

/** Every page the form opens, for each example config: each part of it (Sign-in, Endpoints, ...), and an endpoint. */
private class ConfigPageProvider : PreviewParameterProvider<Pair<String, String>> {
    override val values = ExampleConfigFiles.keys.asSequence().flatMap { kind ->
        (0 until PreviewSectionCounts.getValue(kind)).map { kind to "section:$it" }
    } + sequenceOf("generic_rest" to "endpoint:upload", "generic_rest" to "other")
}

/**
 * One page of the form, [page] being a config's kind and the page: "section:" and its place among the parts folded at
 * first, "endpoint:" and an endpoint's name, or "other" for the fields the form doesn't know.
 */
@ScreenPreviews
@Composable
private fun ConfigEditorPagePreview(@PreviewParameter(ConfigPageProvider::class) page: Pair<String, String>) {
    val schema = configSchema()
    PreviewTheme {
        val (kind, title) = page
        val text = exampleConfigText(ExampleConfigFiles.getValue(kind))
        // The example, and a field the form doesn't know, for Other fields
        val root = remember(text) {
            (ProviderConfigCodec.parseObject(text) ?: JsonObject(emptyMap())).with("comment", JsonPrimitive("A field the form doesn't know"))
        }
        val sections = schema.sectionsFor(kind).orEmpty()
        val form = FormState(root = root, onChange = {}, open = {}, back = {}, stash = remember { mutableMapOf() })
        val shown: Page = when {
            title.startsWith("endpoint:") -> Page.OfEndpoint(title.removePrefix("endpoint:"))
            title == "other" -> Page.OtherFields
            else -> Page.OfSection(sections.filterNot { it.startOpen }[title.removePrefix("section:").toInt()])
        }
        Column {
            PaneHeader(shown.title, trail = root.textOf("name").orEmpty(), canGoBack = true, onBack = {})
            Box(Modifier.weight(1f)) { PageContent(shown, sections, form) }
        }
    }
}
