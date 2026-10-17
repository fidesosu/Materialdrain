@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package tools.senko.materialdrain.settings.ui.configeditor

import tools.senko.materialdrain.ui.theme.AppShapes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import tools.senko.materialdrain.ui.icons.filled.Add
import tools.senko.materialdrain.ui.icons.filled.ArrowDownward
import tools.senko.materialdrain.ui.icons.filled.ArrowDropDown
import tools.senko.materialdrain.ui.icons.filled.ArrowUpward
import tools.senko.materialdrain.ui.icons.filled.Close
import tools.senko.materialdrain.ui.icons.filled.Delete
import tools.senko.materialdrain.ui.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import tools.senko.materialdrain.provider.api.HostScreen
import tools.senko.materialdrain.provider.api.ScreenConfig
import tools.senko.materialdrain.provider.api.screenProblems
import tools.senko.materialdrain.ui.components.AppMenu
import tools.senko.materialdrain.ui.components.AppMenuItem
import tools.senko.materialdrain.ui.components.OverflowMenuButton
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ThemePreviews

/*
 * The editors of the config editor's form, one per kind of field (see FieldKind): text, numbers, switches, choices,
 * key/value maps, the screens and the endpoints. ConfigEditor.kt has the editor itself, its pages and navigation.
 */

@Composable
internal fun FieldEditor(spec: FieldSpec, path: List<String>, obj: JsonObject, form: FormState) {
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
                    Box(modifier = Modifier.matchParentSize().clip(AppShapes.ExtraSmall).clickable { open = true })
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
private val HideableActions = listOf(
    "UPLOAD" to "Upload", "MKDIR" to "New folder", "RENAME" to "Rename and move", "DELETE" to "Delete",
    "ARCHIVE_BROWSE" to "Look inside archives"
)

/** One screen of the config, with what it may hold besides its name, which is kept as it is. */
internal data class ScreenEntry(
    val screen: String,
    val name: String?,
    val disabled: List<String>,
    val extra: JsonObject,
    val id: String? = null,
    val root: String? = null
) {
    fun toJson(): JsonElement {
        if (id.isNullOrBlank() && name.isNullOrBlank() && root.isNullOrBlank() && disabled.isEmpty() && extra.isEmpty()) return JsonPrimitive(screen)
        return JsonObject(LinkedHashMap<String, JsonElement>().apply {
            put("screen", JsonPrimitive(screen))
            if (!id.isNullOrBlank()) put("id", JsonPrimitive(id))
            if (!name.isNullOrBlank()) put("name", JsonPrimitive(name))
            if (!root.isNullOrBlank()) put("root", JsonPrimitive(root))
            if (disabled.isNotEmpty()) put("disabled_capabilities", JsonArray(disabled.map { JsonPrimitive(it) }))
            putAll(extra)
        })
    }

    /** What the tab is called: its own name, or the screen's. */
    fun title(): String = name?.takeIf { it.isNotBlank() } ?: HostScreen.entries.firstOrNull { it.name == screen }?.label() ?: screen

    /** The tab as the app reads it, for its id and checks (see screenProblems); null for a kind the app doesn't know. */
    fun toScreenConfig(): ScreenConfig? = HostScreen.entries.firstOrNull { it.name == screen }?.let { ScreenConfig(it, id = id, root = root) }

    companion object {
        fun of(element: JsonElement): ScreenEntry? = when {
            element is JsonPrimitive && element.isString -> ScreenEntry(element.content.trim().uppercase(), null, emptyList(), JsonObject(emptyMap()))
            element is JsonObject -> element.textOf("screen")?.let { screen ->
                ScreenEntry(
                    screen = screen.trim().uppercase(),
                    name = element.textOf("name"),
                    disabled = (element["disabled_capabilities"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.uppercase() }.orEmpty(),
                    extra = JsonObject(element - setOf("screen", "id", "name", "root", "disabled_capabilities")),
                    id = element.textOf("id"),
                    root = element.textOf("root")
                )
            }
            else -> null
        }
    }
}

/** What's wrong with the screens of a config's [value] (see screenProblems): two tabs with one id, a root where it does nothing. */
internal fun screenEntryProblems(value: JsonElement?): List<String> =
    screenProblems((value as? JsonArray)?.mapNotNull { ScreenEntry.of(it)?.toScreenConfig() })

/** An id for another tab of [screen]'s kind that none of [entries] has yet: "filesystem-2", "filesystem-3"... */
private fun freeTabId(screen: String, entries: List<ScreenEntry>): String {
    val taken = entries.mapNotNull { it.toScreenConfig()?.tabId }.toSet()
    val base = screen.lowercase()
    return generateSequence(2) { it + 1 }.map { "$base-$it" }.first { it !in taken }
}

/**
 * The tabs: left to the app (every one the host can back), or chosen, each ticked one in the order shown. A tab's ⋮
 * menu (or a tap on it) gives its own name, id, root and the actions it leaves out, moves it, and for a Filesystem tab
 * adds another one (with an id of its own, so both show). What's wrong with a tab is said on its row.
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
            // The ids taken more than once: those tabs say so, as only the first of them shows
            val ids = entries.map { it.toScreenConfig()?.tabId }
            Column(modifier = Modifier.fillMaxWidth().clip(RowShape).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                entries.forEachIndexed { index, entry ->
                    ScreenRow(
                        entry = entry,
                        ticked = true,
                        repeatedId = ids[index] != null && ids.count { it == ids[index] } > 1,
                        onTick = { write(entries.filterIndexed { i, _ -> i != index }) },
                        onChange = { changed -> write(entries.toMutableList().also { it[index] = changed }) },
                        onUp = if (index > 0) ({ write(entries.toMutableList().also { java.util.Collections.swap(it, index, index - 1) }) }) else null,
                        onDown = if (index < entries.lastIndex) ({ write(entries.toMutableList().also { java.util.Collections.swap(it, index, index + 1) }) }) else null,
                        // Right under this one, with its own id so both show. Only the filesystem: another tab of the other
                        // kinds would show the very same thing, while a Filesystem tab can have a root of its own
                        onAddAnother = if (entry.screen == HostScreen.FILESYSTEM.name) ({
                            val another = ScreenEntry(entry.screen, null, emptyList(), JsonObject(emptyMap()), id = freeTabId(entry.screen, entries))
                            write(entries.toMutableList().also { it.add(index + 1, another) })
                        }) else null
                    )
                }
                unticked.forEach { screen ->
                    val entry = ScreenEntry(screen.name, null, emptyList(), JsonObject(emptyMap()))
                    ScreenRow(entry = entry, ticked = false, repeatedId = false, onTick = { write(entries + entry) }, onChange = {}, onUp = null, onDown = null, onAddAnother = null)
                }
            }
        }
    }
}

@Composable
private fun ScreenRow(
    entry: ScreenEntry,
    ticked: Boolean,
    repeatedId: Boolean,
    onTick: () -> Unit,
    onChange: (ScreenEntry) -> Unit,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    onAddAnother: (() -> Unit)?
) {
    val screen = HostScreen.entries.firstOrNull { it.name == entry.screen }
    var editing by remember(entry.screen) { mutableStateOf(false) }
    val tabId = entry.toScreenConfig()?.tabId ?: entry.screen.lowercase()
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
                    // A root or an id of its own says more than what the kind of tab is
                    entry.root?.takeIf { it.isNotBlank() }?.let { "In /${it.trim('/')}" } ?: screen?.help(),
                    "id $tabId".takeIf { !entry.id.isNullOrBlank() },
                    "${entry.disabled.size} left out".takeIf { entry.disabled.isNotEmpty() }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // What's wrong with the tab, on the tab itself (see screenProblems)
            val problem = when {
                repeatedId -> "Another tab has the id \"$tabId\": only the first one shows."
                screen != HostScreen.FILESYSTEM && !entry.root.isNullOrBlank() -> "A root folder does nothing here, only on a Filesystem tab."
                else -> null
            }
            problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        if (ticked) {
            OverflowMenuButton(contentDescription = "${entry.title()} options") { close ->
                AppMenuItem("Name, id and actions", leadingIcon = Icons.Filled.Edit, onClick = { close(); editing = true })
                AppMenuItem("Move up", leadingIcon = Icons.Filled.ArrowUpward, enabled = onUp != null, onClick = { close(); onUp?.invoke() })
                AppMenuItem("Move down", leadingIcon = Icons.Filled.ArrowDownward, enabled = onDown != null, onClick = { close(); onDown?.invoke() })
                if (onAddAnother != null) {
                    AppMenuItem("Another ${screen?.label() ?: entry.screen} tab", leadingIcon = Icons.Filled.Add, onClick = { close(); onAddAnother() })
                }
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
                    OutlinedTextField(
                        value = entry.id.orEmpty(),
                        // An id is a word: no spaces, so it reads the same in the config
                        onValueChange = { onChange(entry.copy(id = it.replace(Regex("\\s"), "-").ifEmpty { null })) },
                        label = { Text("Id") },
                        placeholder = { Text(entry.screen.lowercase()) },
                        isError = repeatedId,
                        supportingText = {
                            Text(
                                if (repeatedId) "Another tab has this id: give each its own, or only the first one shows."
                                else "Tells this tab apart from others of its kind. Needed for a second one; left empty, it's ${entry.screen.lowercase()}."
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (screen == HostScreen.FILESYSTEM) {
                        OutlinedTextField(
                            value = entry.root.orEmpty(),
                            onValueChange = { onChange(entry.copy(root = it.ifEmpty { null })) },
                            label = { Text("Root folder") },
                            placeholder = { Text("The whole host") },
                            supportingText = { Text("The folder this tab opens at, under the host's own top, e.g. Media/Films. It can't go above it.") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
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
                                .clip(AppShapes.Medium)
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

// ---- Previews ----

/** Changing a header of a request: its name and value, and Remove. */
@ThemePreviews
@Composable
private fun KeyValueDialogPreview() {
    PreviewTheme {
        KeyValueDialog(
            title = "Header",
            kind = FieldKind.KeyValues(valueHint = "{placeholder} or a fixed value"),
            initialKey = "Authorization",
            initialValue = "Bearer {api_key}",
            taken = setOf("Accept"),
            onSave = { _, _ -> },
            onRemove = {},
            onDismiss = {}
        )
    }
}

/** The endpoints a config hasn't got yet, by what they unlock. */
@ThemePreviews
@Composable
private fun AddEndpointDialogPreview() {
    PreviewTheme { AddEndpointDialog(missing = KnownEndpoints.take(6), onPick = {}, onDismiss = {}) }
}
