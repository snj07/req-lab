package com.reqlab.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reqlab.core.model.BodyType
import com.reqlab.core.model.FormEntryType
import com.reqlab.editor.core.EditorEngine
import com.reqlab.editor.core.InlineEditorError
import com.reqlab.editor.core.LanguageMode
import com.reqlab.ui.shared.platform.pickBinaryFileForRequest
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.MutableFormDataRow
import com.reqlab.ui.shared.state.RequestTabState
import com.reqlab.ui.shared.theme.ReqLabColors

private const val BINARY_ATTACHMENT_PREFIX = "reqlab-binary:"

/**
 * Returns true when [contentLength] exceeds the inline-validation threshold (20 MB).
 * Extracted as a package-level function so it can be unit-tested independently of
 * the Compose composable that uses it (issue M-5).
 */
internal fun shouldPauseValidation(contentLength: Int): Boolean = contentLength > 20_000_000

// ── Body categories (top-level chips) ──────────────────────────

private enum class BodyCategory { NONE, RAW, FORM, URL_ENCODED, BINARY, GRAPHQL }

private fun BodyType.category(): BodyCategory = when (this) {
    BodyType.NONE                  -> BodyCategory.NONE
    BodyType.JSON,
    BodyType.XML,
    BodyType.HTML,
    BodyType.JAVASCRIPT,
    BodyType.RAW_TEXT              -> BodyCategory.RAW
    BodyType.FORM_DATA             -> BodyCategory.FORM
    BodyType.X_WWW_FORM_URLENCODED -> BodyCategory.URL_ENCODED
    BodyType.BINARY                -> BodyCategory.BINARY
    BodyType.GRAPHQL               -> BodyCategory.GRAPHQL
}

private val RAW_SUBTYPES = listOf(
    BodyType.JSON, BodyType.XML, BodyType.HTML, BodyType.JAVASCRIPT, BodyType.RAW_TEXT
)

private fun BodyType.rawLabel(): String = when (this) {
    BodyType.JSON       -> "JSON"
    BodyType.XML        -> "XML"
    BodyType.HTML       -> "HTML"
    BodyType.JAVASCRIPT -> "JS"
    BodyType.RAW_TEXT   -> "Text"
    else                -> name
}

private val CATEGORY_CHIPS = listOf(
    BodyCategory.NONE,
    BodyCategory.RAW,
    BodyCategory.FORM,
    BodyCategory.URL_ENCODED,
    BodyCategory.BINARY,
    BodyCategory.GRAPHQL,
)

private fun BodyCategory.defaultType(): BodyType = when (this) {
    BodyCategory.NONE        -> BodyType.NONE
    BodyCategory.RAW         -> BodyType.JSON
    BodyCategory.FORM        -> BodyType.FORM_DATA
    BodyCategory.URL_ENCODED -> BodyType.X_WWW_FORM_URLENCODED
    BodyCategory.BINARY      -> BodyType.BINARY
    BodyCategory.GRAPHQL     -> BodyType.GRAPHQL
}

private fun BodyCategory.chipLabel(activeType: BodyType, lastRawSubtype: BodyType): String = when (this) {
    BodyCategory.NONE        -> "None"
    BodyCategory.RAW         -> {
        val subtype = if (activeType.category() == BodyCategory.RAW) activeType else lastRawSubtype
        "Raw \u25B8 ${subtype.rawLabel()}"
    }
    BodyCategory.FORM        -> "Form"
    BodyCategory.URL_ENCODED -> "URL Encoded"
    BodyCategory.BINARY      -> "Binary"
    BodyCategory.GRAPHQL     -> "GraphQL"
}

// ── Language mapping ────────────────────────────────────────────

/** Maps a body type to the appropriate syntax highlighting language. */
private fun bodyTypeToLanguage(bodyType: BodyType): SyntaxLanguage = when (bodyType) {
    BodyType.JSON       -> SyntaxLanguage.JSON
    BodyType.XML        -> SyntaxLanguage.XML
    BodyType.HTML       -> SyntaxLanguage.HTML
    BodyType.JAVASCRIPT -> SyntaxLanguage.JAVASCRIPT
    BodyType.GRAPHQL    -> SyntaxLanguage.GRAPHQL
    else                -> SyntaxLanguage.PLAIN
}

// ── Fixed column widths (header & rows must match exactly) ──────

private val TOGGLE_WIDTH = 22.dp
private val TYPE_WIDTH   = 72.dp
private val REMOVE_WIDTH = 22.dp

// ── Main composable ─────────────────────────────────────────────

/**
 * Editor panel for the request body. Shows a category chip row, an optional
 * Raw sub-type selector, validation banners, and the appropriate content panel.
 */
@kotlinx.serialization.ExperimentalSerializationApi
@Composable
fun BodyEditor(tab: RequestTabState, state: AppState, onDirty: () -> Unit) {
    val activeCategory = tab.bodyType.category()

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {

        // ── Category chip row ───────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CATEGORY_CHIPS.forEach { cat ->
                val selected = cat == activeCategory
                Text(
                    text = cat.chipLabel(tab.bodyType, tab.lastRawSubtype),
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) ReqLabColors.Primary else ReqLabColors.OnSurfaceDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selected) ReqLabColors.SelectedItem else Color.Transparent)
                        .clickable {
                            if (!selected) {
                            if (cat == BodyCategory.RAW) {
                                // Restore the remembered RAW sub-type
                                tab.bodyType = tab.lastRawSubtype
                            } else {
                                tab.bodyType = cat.defaultType()
                            }
                            tab.syncSystemHeaders()
                            onDirty()
                        }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        // ── Raw sub-type selector ───────────────────────────────
        if (activeCategory == BodyCategory.RAW) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RAW_SUBTYPES.forEach { subType ->
                    val subSelected = subType == tab.bodyType
                    Text(
                        text = subType.rawLabel(),
                        fontSize = 10.sp,
                        fontWeight = if (subSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (subSelected) ReqLabColors.Primary else ReqLabColors.OnSurfaceDim,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (subSelected) ReqLabColors.SelectedItem else Color.Transparent)
                            .border(
                                1.dp,
                                if (subSelected) ReqLabColors.Primary.copy(alpha = 0.4f)
                                else ReqLabColors.Border,
                                RoundedCornerShape(3.dp),
                            )
                            .clickable {
                                if (!subSelected) {
                                    tab.bodyType = subType
                                    tab.lastRawSubtype = subType
                                    tab.syncSystemHeaders()
                                    onDirty()
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }

        // ── Inline validation error banners ─────────────────────
        // Validation errors are surfaced as inline underlines in the
        // CodeEditor rather than banner overlays above the editor.
        // See: buildHighlightedWithErrors in CodeEditor.kt.

        // ── Content panels ──────────────────────────────────────
        when (activeCategory) {

            BodyCategory.NONE -> NoneBodyPanel()

            BodyCategory.FORM -> {
                FormDataTableEditor(
                    rows = tab.formRows,
                    onAdd = { tab.formRows.add(MutableFormDataRow()); onDirty() },
                    onRowChange = { onDirty() },
                    onRemove = { row -> tab.formRows.remove(row); onDirty() },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            BodyCategory.URL_ENCODED -> {
                UrlencodedTableEditor(
                    rows = tab.urlencodedRows,
                    onAdd = { tab.urlencodedRows.add(MutableFormDataRow()); onDirty() },
                    onRowChange = { onDirty() },
                    onRemove = { row -> tab.urlencodedRows.remove(row); onDirty() },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            BodyCategory.BINARY -> {
                BinaryAttachmentEditor(tab = tab, onDirty = onDirty, modifier = Modifier.fillMaxWidth())
            }

            else -> {
                // RAW and GRAPHQL → unified inline code editor with
                // syntax highlighting, code folding, search, and format
                val language = bodyTypeToLanguage(tab.bodyType)

                // Compute inline diagnostics to underline in the editor.
                // JSON: exact line/col from the parser.
                // XML:  no line info from the lightweight validator — underline line 1.
                // Route all validation through EditorEngine from editor-core so that
                // inline error logic is centralised and testable independently of the UI.
                val editorEngine = remember { EditorEngine() }
                var inlineErrors by remember { mutableStateOf<List<InlineEditorError>>(emptyList()) }
                var validationPaused by remember { mutableStateOf(false) }
                var bodyPopupVariable by remember { mutableStateOf<String?>(null) }
                // Compute the set of all defined variable names from all active layers
                // (env → collection → globals). Used to colour tokens orange (resolved)
                // vs red (unresolved) inside the code editor.
                val definedVarNames: Set<String> = state?.activeVariableLayers()
                    ?.flatMap { it.keys }?.toSet() ?: emptySet()

                // Debounce validation — avoid calling EditorEngine.validate() on
                // every keystroke. For large files validation runs on Dispatchers.Default
                // so the main/UI thread is NEVER blocked by O(n) validation work.
                // We keep validation enabled up to 20 MB with a larger debounce window.
                LaunchedEffect(tab.bodyContent, tab.bodyType) {
                    val content = tab.bodyContent
                    val bodyType = tab.bodyType
                    if (content.isBlank()) {
                        inlineErrors = emptyList()
                        validationPaused = false
                        return@LaunchedEffect
                    }
                    if (shouldPauseValidation(content.length)) {
                        // Clear stale errors immediately and surface a paused indicator
                        inlineErrors = emptyList()
                        validationPaused = true
                        return@LaunchedEffect
                    }
                    validationPaused = false
                    val delayMs = when {
                        content.length > 5_000_000 -> 1200L
                        content.length > 1_000_000 -> 900L
                        content.length > 100_000 -> 600L
                        else -> 300L
                    }
                    kotlinx.coroutines.delay(delayMs)
                    // Re-read after delay — user may have continued typing
                    if (content != tab.bodyContent) return@LaunchedEffect
                    val langMode = when (bodyType) {
                        BodyType.JSON       -> LanguageMode.JSON
                        BodyType.XML        -> LanguageMode.XML
                        BodyType.HTML       -> LanguageMode.HTML
                        BodyType.JAVASCRIPT -> LanguageMode.JAVASCRIPT
                        else                -> LanguageMode.PLAIN_TEXT
                    }
                    // Run the actual O(n) validation off the main thread
                    val result = withContext(Dispatchers.Default) {
                        editorEngine.validate(content, langMode)
                    }
                    // Guard: only apply if still current content
                    if (content == tab.bodyContent) inlineErrors = result
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    if (validationPaused) {
                        Text(
                            text = "\u26A0 Validation paused \u2014 file too large (> 20 MB)",
                            fontSize = 11.sp,
                            color = ReqLabColors.OnSurfaceDim,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        CodeEditor(
                            text = tab.bodyContent,
                            onTextChange = { tab.bodyContent = it; onDirty() },
                            language = language,
                            modifier = Modifier.fillMaxSize(),
                            showToolbar = true,
                            enableFolding = language != SyntaxLanguage.PLAIN,
                            enableSearch = true,
                            enableFormat = language != SyntaxLanguage.PLAIN,
                            enableWordWrap = true,
                            enableCopy = true,
                            enableDownload = false,
                            inlineErrors = inlineErrors,
                            placeholder = when (tab.bodyType) {
                                BodyType.JSON       -> "{\n  \n}"
                                BodyType.XML        -> "<root>\n  \n</root>"
                                BodyType.HTML       -> "<html>\n  <body></body>\n</html>"
                                BodyType.JAVASCRIPT -> "// script\n"
                                BodyType.GRAPHQL    -> "query {\n  \n}"
                                else                -> "Enter request body\u2026"
                            },
                            testTagPrefix = "body-editor",
                            onCursorTap = { offset ->
                                bodyPopupVariable = variableNameAtOffset(tab.bodyContent, offset)
                            },
                            lineVariableSpans = if (tab.bodyContent.contains("{{")) {
                                { line, _ -> variableRangesForLine(line, definedVarNames) }
                            } else null,
                        )

                        bodyPopupVariable?.let { variableName ->
                            VariableEditorPopup(
                                variableName = variableName,
                                state = state,
                                onDismiss = { bodyPopupVariable = null },
                            )
                        }
                    }

                }
            }
        }
    }
}

// ── "No body" panel ─────────────────────────────────────────────

@Composable
private fun NoneBodyPanel() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "This request does not have a body",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = ReqLabColors.OnSurfaceDim,
            )
            Text(
                text = "Select a body type above to add content.",
                fontSize = 11.sp,
                color = ReqLabColors.OnSurfaceDim.copy(alpha = 0.6f),
            )
        }
    }
}

// ── Form-Data table editor ──────────────────────────────────────

@Composable
private fun FormDataTableEditor(
    rows: List<MutableFormDataRow>,
    onAdd: () -> Unit,
    onRowChange: () -> Unit,
    onRemove: (MutableFormDataRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        AddRowButton(
            onAdd = onAdd,
            modifier = Modifier
                .padding(bottom = 6.dp)
                .testTag("body-form-add-row"),
        )
        FormDataTableHeader(
            showTypeColumn = true,
            modifier = Modifier.testTag("body-form-table-header"),
        )
        LazyColumn(modifier = Modifier.weight(1f).testTag("body-form-table-list")) {
            items(rows, key = { it.uid }) { row ->
                FormDataRowItem(
                    row = row,
                    showTypeColumn = true,
                    onRowChange = onRowChange,
                    onRemove = { onRemove(row) },
                )
            }
        }
    }
}

// ── URL-Encoded table editor ────────────────────────────────────

@Composable
private fun UrlencodedTableEditor(
    rows: List<MutableFormDataRow>,
    onAdd: () -> Unit,
    onRowChange: () -> Unit,
    onRemove: (MutableFormDataRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        AddRowButton(
            onAdd = onAdd,
            modifier = Modifier
                .padding(bottom = 6.dp)
                .testTag("body-urlencoded-add-row"),
        )
        FormDataTableHeader(
            showTypeColumn = false,
            modifier = Modifier.testTag("body-urlencoded-table-header"),
        )
        LazyColumn(modifier = Modifier.weight(1f).testTag("body-urlencoded-table-list")) {
            items(rows, key = { it.uid }) { row ->
                FormDataRowItem(
                    row = row,
                    showTypeColumn = false,
                    onRowChange = onRowChange,
                    onRemove = { onRemove(row) },
                )
            }
        }
    }
}

// ── Table header (fixed widths mirror row layout exactly) ───────

@Composable
private fun FormDataTableHeader(showTypeColumn: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ReqLabColors.Surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(TOGGLE_WIDTH))  // aligns with enabled toggle
        Text(
            text = "Key",
            fontSize = 10.sp,
            color = ReqLabColors.OnSurfaceDim,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(2f),
        )
        if (showTypeColumn) {
            Text(
                text = "Type",
                fontSize = 10.sp,
                color = ReqLabColors.OnSurfaceDim,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(TYPE_WIDTH),
            )
        }
        Text(
            text = "Value",
            fontSize = 10.sp,
            color = ReqLabColors.OnSurfaceDim,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(2f),
        )
        Text(
            text = "Description",
            fontSize = 10.sp,
            color = ReqLabColors.OnSurfaceDim,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1.5f),
        )
        Spacer(Modifier.width(REMOVE_WIDTH))  // aligns with remove icon
    }
}

@Composable
private fun FormDataRowItem(
    row: MutableFormDataRow,
    showTypeColumn: Boolean,
    onRowChange: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Enabled toggle
        Box(modifier = Modifier.width(TOGGLE_WIDTH), contentAlignment = Alignment.Center) {
            Checkbox(
                checked = row.enabled,
                onCheckedChange = {
                    row.enabled = it
                    onRowChange()
                },
                modifier = Modifier
                    .size(16.dp)
                    .testTag("body-table-enabled-${row.uid}"),
            )
        }

        // Key
        FormCellTextField(
            value = row.key,
            onValueChange = { row.key = it; onRowChange() },
            placeholder = "key",
            modifier = Modifier.weight(2f),
        )

        // Type dropdown (form-data only)
        if (showTypeColumn) {
            TypeDropdown(
                type = row.type,
                onTypeChange = { row.type = it; onRowChange() },
                modifier = Modifier.width(TYPE_WIDTH),
            )
        }

        // Value (text or file picker)
        if (showTypeColumn && row.type == FormEntryType.FILE) {
            FilePickerCell(
                fileName = row.value.ifBlank { null },
                onFilePicked = { name, _ -> row.value = name; onRowChange() },
                modifier = Modifier.weight(2f),
            )
        } else {
            FormCellTextField(
                value = row.value,
                onValueChange = { row.value = it; onRowChange() },
                placeholder = "value",
                modifier = Modifier.weight(2f),
            )
        }

        // Description
        FormCellTextField(
            value = row.description,
            onValueChange = { row.description = it; onRowChange() },
            placeholder = "description",
            modifier = Modifier.weight(1.5f),
        )

        // Remove
        Box(
            modifier = Modifier.width(REMOVE_WIDTH),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove row",
                tint = ReqLabColors.OnSurfaceDim,
                modifier = Modifier
                    .size(14.dp)
                    .clickable { onRemove() },
            )
        }
    }
}

@Composable
private fun FormCellTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = 11.sp, color = ReqLabColors.OnSurface),
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(ReqLabColors.SurfaceVariant)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(placeholder, fontSize = 11.sp, color = ReqLabColors.OnSurfaceDim)
            }
            inner()
        },
    )
}

@Composable
private fun TypeDropdown(
    type: FormEntryType,
    onTypeChange: (FormEntryType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Text(
            text = type.name.lowercase(),
            fontSize = 11.sp,
            color = ReqLabColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(ReqLabColors.SurfaceVariant)
                .clickable { expanded = true }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
        // Uses a popup overlay so it does not shift the surrounding row layout
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            FormEntryType.entries.forEach { t ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = t.name.lowercase(),
                            fontSize = 11.sp,
                            color = if (t == type) ReqLabColors.Primary else ReqLabColors.OnSurface,
                        )
                    },
                    onClick = { onTypeChange(t); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun FilePickerCell(
    fileName: String?,
    onFilePicked: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(ReqLabColors.SurfaceVariant)
            .clickable {
                pickBinaryFileForRequest { file ->
                    onFilePicked(file.name, file.base64Content)
                }
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = "Pick file",
            tint = ReqLabColors.OnSurfaceDim,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = fileName ?: "Choose file…",
            fontSize = 11.sp,
            color = if (fileName != null) ReqLabColors.OnSurface else ReqLabColors.OnSurfaceDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AddRowButton(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ReqLabColors.Surface)
            .clickable { onAdd() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add row",
            tint = ReqLabColors.Primary,
            modifier = Modifier.size(14.dp),
        )
        Text(text = "Add row", fontSize = 11.sp, color = ReqLabColors.Primary)
    }
}

// ── Binary attachment editor ────────────────────────────────────

@Composable
private fun BinaryAttachmentEditor(
    tab: RequestTabState,
    onDirty: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val attachedName = attachedBinaryFileName(tab.bodyContent)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Attach File",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = ReqLabColors.Primary,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(ReqLabColors.SelectedItem)
                .clickable {
                    pickBinaryFileForRequest { file ->
                        tab.bodyContent = encodeBinaryAttachment(file.name, file.base64Content)
                        onDirty()
                    }
                }
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
        Text(
            text = attachedName?.let { "Attached: $it" } ?: "No file attached",
            color = ReqLabColors.OnSurfaceDim,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── Private helpers ─────────────────────────────────────────────

private fun encodeBinaryAttachment(fileName: String, base64Content: String): String =
    "$BINARY_ATTACHMENT_PREFIX$fileName\n$base64Content"

private fun attachedBinaryFileName(content: String): String? {
    if (!content.startsWith(BINARY_ATTACHMENT_PREFIX)) return null
    val firstNewLine = content.indexOf('\n')
    if (firstNewLine <= BINARY_ATTACHMENT_PREFIX.length) return null
    return content.substring(BINARY_ATTACHMENT_PREFIX.length, firstNewLine)
}
