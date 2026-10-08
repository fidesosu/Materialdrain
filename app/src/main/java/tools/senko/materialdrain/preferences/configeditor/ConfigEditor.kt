@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package tools.senko.materialdrain.preferences.configeditor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import tools.senko.materialdrain.provider.api.HostScreen
import tools.senko.materialdrain.provider.api.ProviderConfigCodec
import tools.senko.materialdrain.ui.components.AppMenu
import tools.senko.materialdrain.ui.components.AppMenuItem

/** The two ways of editing a config: field by field, or its text. */
private enum class EditorMode(val label: String) { FORM("Form"), JSON("JSON") }

/** [this] with [key] set to [value], or left out when it's null; a key that's already there keeps its place. */
internal fun JsonObject.with(key: String, value: JsonElement?): JsonObject =
    JsonObject(LinkedHashMap(this).apply { if (value == null) remove(key) else put(key, value) })

private val lenientJson = Json { isLenient = true }

/**
 * A config in a window of its own, the whole screen: a form with a field for each value, saying what goes where, or the
 * config's text. The form shows each kind's fields in sections, the important ones open and the rest a tap away under
 * "More options", so a config looks small but nothing is out of reach; a field the app doesn't know is kept and shown
 * under "Other fields". Both views edit the same config, and switching between them keeps what was typed.
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
        BackHandler(onBack = close)
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            // Down to the edge of the screen: the form scrolls on under the navigation bar, with room for it at its end
            Column(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp)
                ) {
                    IconButton(onClick = close) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                    Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(root.textOf("name")?.takeIf { it.isNotBlank() }, kindLabel(kind)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Button(onClick = { onSave(currentText()) }) { Text(saveLabel) }
                }

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    EditorMode.entries.forEachIndexed { index, entry ->
                        SegmentedButton(
                            selected = mode == entry,
                            onClick = {
                                if (entry == mode) return@SegmentedButton
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
                                            mode = entry
                                        }
                                    }
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, EditorMode.entries.size)
                        ) { Text(entry.label) }
                    }
                }

                (error ?: jsonError)?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(12.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (mode == EditorMode.JSON || sections == null) {
                        OutlinedTextField(
                            value = jsonText,
                            onValueChange = { jsonText = it; jsonError = null },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp)
                        )
                        Text(
                            "The config as JSON, as it's exported.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        sections.forEach { section ->
                            SectionCard(section, root, onChange = { root = it })
                        }
                        val others = root.keys - describedKeys(sections)
                        if (others.isNotEmpty()) OtherFieldsCard(root, others, onChange = { root = it })
                        Spacer(Modifier.height(24.dp))
                    }
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
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

// ---- Sections ----

private val CardShape = RoundedCornerShape(20.dp)

@Composable
private fun SectionCard(section: Section, root: JsonObject, onChange: (JsonObject) -> Unit) {
    var open by rememberSaveable(section.title) { mutableStateOf(section.startOpen) }
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            FoldHeader(section.title, section.summary, open, onToggle = { open = !open })
            if (open) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FieldsEditor(section.fields, root, onChange)
                }
            }
        }
    }
}

@Composable
private fun FoldHeader(title: String, summary: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (summary.isNotEmpty()) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (open) "Fold" else "Unfold")
    }
}

/** Fields the app doesn't read, kept as they are and editable as JSON. */
@Composable
private fun OtherFieldsCard(root: JsonObject, keys: Set<String>, onChange: (JsonObject) -> Unit) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Other fields", style = MaterialTheme.typography.titleMedium)
            Text(
                "Fields this version of the app doesn't read, e.g. notes or fields for a newer version. They're kept as they are.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            keys.forEach { key -> RawJsonField(key, root.getValue(key), onValue = { onChange(root.with(key, it)) }) }
        }
    }
}

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
 * The fields of one object: those that are set or common, each with its control, then the others under "More options",
 * added with a tap. A field that only means something given others (see [FieldSpec.shownWhen]) only shows then.
 */
@Composable
private fun FieldsEditor(fields: List<FieldSpec>, obj: JsonObject, onChange: (JsonObject) -> Unit) {
    // A null (the templates write unset fields that way) is the same as leaving the field out
    fun valueOf(spec: FieldSpec) = obj[spec.key]?.takeUnless { it is JsonNull }
    val visible = fields.filter { it.shownWhen(obj) }
    val shown = visible.filter { valueOf(it) != null || it.common }
    val more = visible - shown.toSet()
    shown.forEach { spec ->
        FieldEditor(spec, valueOf(spec), onValue = { onChange(obj.with(spec.key, it)) })
    }
    if (more.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("More options", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                more.forEach { spec ->
                    AssistChip(
                        onClick = { onChange(obj.with(spec.key, initialValue(spec))) },
                        label = { Text(spec.label) },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
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
private fun helpText(spec: FieldSpec): String =
    listOfNotNull(spec.help.takeIf { it.isNotEmpty() }, spec.default?.let { "Default: ${it.displayText()}." }).joinToString(" ")

@Composable
private fun FieldEditor(spec: FieldSpec, value: JsonElement?, onValue: (JsonElement?) -> Unit) {
    // A field that's set but may be left out can be taken out again, going back to its default
    val removable = value != null && !spec.required
    when (val kind = spec.kind) {
        is FieldKind.Text -> TextField(spec, value, kind.multiline, removable, onValue)
        FieldKind.Number -> NumberField(spec, value, removable, onValue)
        FieldKind.StatusCodes -> StatusCodesField(spec, value, removable, onValue)
        FieldKind.Toggle -> ToggleField(spec, value, onValue)
        is FieldKind.Choice -> ChoiceField(spec, kind, value, removable, onValue)
        is FieldKind.Group -> GroupField(spec, kind, value, removable, onValue)
        is FieldKind.KeyValues -> KeyValuesField(spec, kind, value, removable, onValue)
        FieldKind.Screens -> ScreensField(spec, value, onValue)
        FieldKind.Endpoints -> EndpointsField(value, onValue)
    }
}

@Composable
private fun RemoveButton(label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.Filled.Close, contentDescription = "Remove $label, use the default") }
}

@Composable
private fun TextField(spec: FieldSpec, value: JsonElement?, multiline: Boolean, removable: Boolean, onValue: (JsonElement?) -> Unit) {
    val text = (value as? JsonPrimitive)?.content.orEmpty()
    val missing = spec.required && text.isBlank()
    OutlinedTextField(
        value = text,
        onValueChange = { onValue(JsonPrimitive(it)) },
        label = { Text(spec.label) },
        placeholder = (spec.placeholder ?: spec.default?.displayText()?.takeIf { value == null })?.let { { Text(it) } },
        supportingText = { Text(if (missing) "Needed. ${spec.help}" else helpText(spec)) },
        isError = missing,
        singleLine = !multiline,
        minLines = if (multiline) 3 else 1,
        textStyle = if (multiline) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
        trailingIcon = if (removable) ({ RemoveButton(spec.label) { onValue(null) } }) else null,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun NumberField(spec: FieldSpec, value: JsonElement?, removable: Boolean, onValue: (JsonElement?) -> Unit) {
    val current = (value as? JsonPrimitive)?.content.orEmpty()
    var text by remember(spec.key, current) { mutableStateOf(current) }
    val invalid = text.isNotEmpty() && text.toLongOrNull() == null
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            text = typed
            typed.toLongOrNull()?.let { onValue(JsonPrimitive(it)) }
        },
        label = { Text(spec.label) },
        placeholder = spec.default?.displayText()?.let { { Text(it) } },
        supportingText = { Text(if (invalid) "A whole number" else helpText(spec)) },
        isError = invalid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        trailingIcon = if (removable) ({ RemoveButton(spec.label) { onValue(null) } }) else null,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun StatusCodesField(spec: FieldSpec, value: JsonElement?, removable: Boolean, onValue: (JsonElement?) -> Unit) {
    val current = (value as? JsonArray)?.joinToString(", ") { (it as? JsonPrimitive)?.content.orEmpty() }.orEmpty()
    var text by remember(spec.key, current) { mutableStateOf(current) }
    val codes = text.split(',', ' ').filter { it.isNotBlank() }.map { it.trim().toIntOrNull() }
    val invalid = codes.any { it == null }
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            text = typed
            val parsed = typed.split(',', ' ').filter { it.isNotBlank() }.map { it.trim().toIntOrNull() }
            if (parsed.none { it == null }) onValue(JsonArray(parsed.map { JsonPrimitive(it) }))
        },
        label = { Text(spec.label) },
        placeholder = spec.default?.displayText()?.let { { Text(it) } },
        supportingText = { Text(if (invalid) "Numbers separated by commas, like 200, 201" else helpText(spec)) },
        isError = invalid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        trailingIcon = if (removable) ({ RemoveButton(spec.label) { onValue(null) } }) else null,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ToggleField(spec: FieldSpec, value: JsonElement?, onValue: (JsonElement?) -> Unit) {
    val checked = ((value ?: spec.default) as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onValue(JsonPrimitive(!checked)) }) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(spec.label, style = MaterialTheme.typography.bodyLarge)
            Text(helpText(spec), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = { onValue(JsonPrimitive(it)) })
    }
}

/** A field's name above a control that has no label of its own, with the button that takes it out. */
@Composable
private fun FieldLabel(spec: FieldSpec, removable: Boolean, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(spec.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (removable) RemoveButton(spec.label, onRemove)
    }
}

@Composable
private fun ChoiceField(spec: FieldSpec, kind: FieldKind.Choice, value: JsonElement?, removable: Boolean, onValue: (JsonElement?) -> Unit) {
    val currentValue = ((value ?: spec.default) as? JsonPrimitive)?.content
    val current = kind.options.firstOrNull { it.value.equals(currentValue, ignoreCase = true) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(spec, removable) { onValue(null) }
        if (kind.options.size <= 4) {
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
        val help = listOfNotNull(current?.help, helpText(spec).takeIf { it.isNotEmpty() }).joinToString(" ")
        if (help.isNotEmpty()) Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** An object of its own: its fields in an inset card, under its name. */
@Composable
private fun GroupField(spec: FieldSpec, kind: FieldKind.Group, value: JsonElement?, removable: Boolean, onValue: (JsonElement?) -> Unit) {
    val obj = value as? JsonObject ?: JsonObject(emptyMap())
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)
    ) {
        Column {
            FieldLabel(spec, removable) { onValue(null) }
            if (spec.help.isNotEmpty()) {
                Text(spec.help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
            }
        }
        Column(modifier = Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Left out, the object is only written once one of its fields is set
            FieldsEditor(kind.fields, obj, onChange = { onValue(it) })
        }
    }
}

/** One name and its value, while it's being edited; a row with no name yet isn't written. */
private data class KeyValueRow(val key: String, val value: String)

private fun JsonObject?.toRows(): List<KeyValueRow> =
    this?.map { (key, value) -> KeyValueRow(key, (value as? JsonPrimitive)?.content ?: value.toString()) }.orEmpty()

private fun List<KeyValueRow>.toJson(): JsonObject {
    val map = LinkedHashMap<String, JsonElement>()
    filter { it.key.isNotBlank() }.forEach { map[it.key] = JsonPrimitive(it.value) }
    return JsonObject(map)
}

@Composable
private fun KeyValuesField(spec: FieldSpec, kind: FieldKind.KeyValues, value: JsonElement?, removable: Boolean, onValue: (JsonElement?) -> Unit) {
    val incoming = value as? JsonObject
    var rows by remember(spec.key) { mutableStateOf(incoming.toRows()) }
    // Changed from outside (the JSON view, a reset): start from that
    LaunchedEffect(incoming) { if ((incoming ?: JsonObject(emptyMap())) != rows.toJson()) rows = incoming.toRows() }
    fun update(newRows: List<KeyValueRow>) {
        rows = newRows
        onValue(newRows.toJson())
    }
    val known = kind.suggestions.associateBy { it.key }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(spec, removable) { onValue(null) }
        if (spec.help.isNotEmpty()) Text(spec.help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        rows.forEachIndexed { index, row ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = row.key,
                    onValueChange = { key -> update(rows.toMutableList().also { it[index] = row.copy(key = key) }) },
                    label = { Text("Name") },
                    singleLine = true,
                    supportingText = known[row.key]?.let { { Text(it.help) } },
                    modifier = Modifier.weight(0.4f)
                )
                OutlinedTextField(
                    value = row.value,
                    onValueChange = { text -> update(rows.toMutableList().also { it[index] = row.copy(value = text) }) },
                    label = { Text("Value") },
                    placeholder = kind.valueHint.takeIf { it.isNotEmpty() }?.let { { Text(it, maxLines = 1) } },
                    singleLine = true,
                    modifier = Modifier.weight(0.6f)
                )
                IconButton(onClick = { update(rows.toMutableList().also { it.removeAt(index) }) }, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove ${row.key}")
                }
            }
        }
        val unused = kind.suggestions.filter { s -> rows.none { it.key == s.key } }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            unused.forEach { suggestion ->
                AssistChip(
                    onClick = { update(rows + KeyValueRow(suggestion.key, suggestion.value)) },
                    label = { Text(suggestion.key) },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }
            AssistChip(
                onClick = { rows = rows + KeyValueRow("", "") },
                label = { Text(if (unused.isEmpty()) "Add" else "Other") },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
    }
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
 * The tabs: left to the app (every one the host can back), or chosen, each ticked one in the order shown, which can be
 * changed, with an optional name of its own and actions it leaves out.
 */
@Composable
private fun ScreensField(spec: FieldSpec, value: JsonElement?, onValue: (JsonElement?) -> Unit) {
    val entries = (value as? JsonArray)?.mapNotNull { ScreenEntry.of(it) }
    val chosen = entries != null
    fun write(list: List<ScreenEntry>) = onValue(JsonArray(list.map { it.toJson() }))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Choose the tabs myself", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (chosen) "Ticked tabs show in this order. A tab the host can't back is left out." else spec.help,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = chosen,
                onCheckedChange = { on -> if (on) onValue(initialValue(spec)) else onValue(null) }
            )
        }
        if (entries != null) {
            val unticked = HostScreen.entries.filter { screen -> entries.none { it.screen == screen.name } }
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
                ScreenRow(
                    entry = ScreenEntry(screen.name, null, emptyList(), JsonObject(emptyMap())),
                    ticked = false,
                    onTick = { write(entries + ScreenEntry(screen.name, null, emptyList(), JsonObject(emptyMap()))) },
                    onChange = {},
                    onUp = null,
                    onDown = null
                )
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
    var open by remember(entry.screen) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (ticked) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow)
            .animateContentSize()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = ticked, onCheckedChange = { onTick() })
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.name?.takeIf { it.isNotBlank() } ?: screen?.label() ?: entry.screen, style = MaterialTheme.typography.bodyLarge)
                Text(
                    listOfNotNull(screen?.help(), "${entry.disabled.size} hidden".takeIf { entry.disabled.isNotEmpty() }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (ticked) {
                IconButton(onClick = { onUp?.invoke() }, enabled = onUp != null) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up") }
                IconButton(onClick = { onDown?.invoke() }, enabled = onDown != null) { Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down") }
                IconButton(onClick = { open = !open }) {
                    Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = "Name and actions")
                }
            }
        }
        if (ticked && open) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        }
    }
}

// ---- Endpoints ----

/** A REST config's requests, by what they unlock, each folded to one line; the ones not set are offered below. */
@Composable
private fun EndpointsField(value: JsonElement?, onValue: (JsonElement?) -> Unit) {
    val endpoints = value as? JsonObject ?: JsonObject(emptyMap())
    val known = KnownEndpoints.associateBy { it.name }
    val byArea = endpoints.keys.groupBy { known[it]?.area ?: "Other" }
    val areas = KnownEndpoints.map { it.area }.distinct() + "Other"
    var newlyAdded by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Text(area, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
            names.forEach { name ->
                EndpointCard(
                    name = name,
                    info = known[name],
                    endpoint = endpoints[name] as? JsonObject ?: JsonObject(emptyMap()),
                    startOpen = name == newlyAdded,
                    onChange = { onValue(endpoints.with(name, it)) }
                )
            }
        }

        val missing = KnownEndpoints.filter { it.name !in endpoints }
        if (missing.isNotEmpty()) {
            var adding by rememberSaveable { mutableStateOf(false) }
            TextButton(onClick = { adding = !adding }) {
                Icon(if (adding) Icons.Filled.ExpandLess else Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add an endpoint (${missing.size} more)")
            }
            if (adding) {
                missing.groupBy { it.area }.forEach { (area, infos) ->
                    Text(area, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        infos.forEach { info ->
                            AssistChip(
                                onClick = {
                                    newlyAdded = info.name
                                    onValue(endpoints.with(info.name, JsonObject(mapOf("method" to JsonPrimitive("GET"), "path" to JsonPrimitive("")))))
                                },
                                label = { Text(info.name, fontFamily = FontFamily.Monospace) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EndpointCard(name: String, info: EndpointInfo?, endpoint: JsonObject, startOpen: Boolean, onChange: (JsonElement?) -> Unit) {
    var open by rememberSaveable(name) { mutableStateOf(startOpen) }
    var confirmRemove by remember { mutableStateOf(false) }
    val summary = listOfNotNull(endpoint.textOf("method"), endpoint.textOf("path")?.ifBlank { null }).joinToString(" ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .animateContentSize()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace)
                Text(
                    info?.help ?: "Not a name this app uses: it's kept, but nothing calls it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    summary.ifEmpty { "No path yet" },
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (summary.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { confirmRemove = true }) { Icon(Icons.Filled.Delete, contentDescription = "Remove $name") }
            Icon(
                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.padding(end = 12.dp)
            )
        }
        if (open) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FieldsEditor(info?.fields ?: endpointFields(), endpoint, onChange = { onChange(it) })
            }
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove $name?") },
            text = { Text(info?.let { "The app stops using it for: ${it.help.removeSuffix(".").lowercase()}." } ?: "It isn't used by the app.") },
            confirmButton = { TextButton(onClick = { confirmRemove = false; onChange(null) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Keep") } }
        )
    }
}
