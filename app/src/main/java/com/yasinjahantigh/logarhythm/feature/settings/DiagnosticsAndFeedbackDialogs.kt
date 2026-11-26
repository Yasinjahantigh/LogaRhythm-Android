package com.yasinjahantigh.logarhythm.feature.settings

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yasinjahantigh.logarhythm.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShakeBugReportSheet(
    recentLogsCount: Int,
    onSubmit: suspend (category: String, description: String, contact: String?, includeLogs: Boolean) -> Result<Unit>,
    onDismiss: () -> Unit,
    onOpenLogViewer: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val categories = listOf(
        "crash" to stringResource(R.string.report_cat_crash),
        "playback" to stringResource(R.string.report_cat_playback),
        "download" to stringResource(R.string.report_cat_download),
        "lyrics" to stringResource(R.string.report_cat_lyrics),
        "other" to stringResource(R.string.report_cat_other)
    )
    var selectedCategory by remember { mutableStateOf("crash") }
    var description by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var includeLogs by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { if (!sending) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.bug_report_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.bug_report_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { if (!sending) onDismiss() }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.btn_close))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { (key, label) ->
                    FilterChip(
                        selected = selectedCategory == key,
                        onClick = { selectedCategory = key },
                        label = { Text(label) }
                    )
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.bug_report_desc_label)) },
                placeholder = { Text(stringResource(R.string.bug_report_desc_hint)) },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bug_report_description")
            )

            OutlinedTextField(
                value = contact,
                onValueChange = { contact = it },
                label = { Text(stringResource(R.string.feedback_contact_label)) },
                placeholder = { Text(stringResource(R.string.feedback_contact_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.bug_report_attach_logs),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.bug_report_attach_logs_desc, recentLogsCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = includeLogs, onCheckedChange = { includeLogs = it })
                }
            }

            if (onOpenLogViewer != null) {
                OutlinedButton(
                    onClick = onOpenLogViewer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bug_report_view_logs_button")
                ) {
                    Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.diagnostic_logs_view_export_btn))
                }
            }

            Button(
                enabled = !sending && (description.trim().length >= 5 || includeLogs),
                onClick = {
                    if (sending) return@Button
                    sending = true
                    scope.launch {
                        val res = onSubmit(
                            selectedCategory,
                            description.ifBlank { "(Logs attached)" },
                            contact.takeIf { it.isNotBlank() },
                            includeLogs
                        )
                        sending = false
                        res.onSuccess {
                            Toast.makeText(context, context.getString(R.string.bug_report_sent_toast), Toast.LENGTH_LONG).show()
                            onDismiss()
                        }.onFailure {
                            Toast.makeText(context, context.getString(R.string.feedback_send_failed_toast), Toast.LENGTH_LONG).show()
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("bug_report_submit_button")
            ) {
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.bug_report_submit_btn))
                }
            }
        }
    }
}

@Composable
fun SuggestionsDialog(
    onSubmit: suspend (category: String, text: String, contact: String?) -> Result<Unit>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val categories = listOf(
        "feature" to stringResource(R.string.suggestions_cat_feature),
        "design" to stringResource(R.string.suggestions_cat_design),
        "player" to stringResource(R.string.suggestions_cat_player),
        "download" to stringResource(R.string.suggestions_cat_download),
        "other" to stringResource(R.string.report_cat_other)
    )
    var selectedCategory by remember { mutableStateOf("feature") }
    var text by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        icon = { Icon(Icons.Filled.Feedback, contentDescription = null) },
        title = { Text(stringResource(R.string.settings_suggestions_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.suggestions_dialog_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedCategory == key,
                            onClick = { selectedCategory = key },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.suggestions_input_label)) },
                    placeholder = { Text(stringResource(R.string.suggestions_input_hint)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("suggestions_input_field")
                )
                OutlinedTextField(
                    value = contact,
                    onValueChange = { contact = it },
                    label = { Text(stringResource(R.string.feedback_contact_label)) },
                    placeholder = { Text(stringResource(R.string.feedback_contact_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !sending && text.trim().length >= 5,
                onClick = {
                    if (sending) return@TextButton
                    sending = true
                    scope.launch {
                        val res = onSubmit(selectedCategory, text, contact.takeIf { it.isNotBlank() })
                        sending = false
                        res.onSuccess {
                            Toast.makeText(context, context.getString(R.string.suggestions_sent_toast), Toast.LENGTH_LONG).show()
                            onDismiss()
                        }.onFailure {
                            Toast.makeText(context, context.getString(R.string.feedback_send_failed_toast), Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.testTag("suggestions_send_button")
            ) {
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.suggestions_send_btn))
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !sending, onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceLicensesSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val tabs = listOf(
        "licenses/LICENSE.txt" to "GPL-3.0",
        "licenses/THIRD_PARTY_NOTICES.txt" to stringResource(R.string.licenses_tab_third_party),
        "licenses/NOTICE.txt" to "NOTICE",
        "OFL.txt" to stringResource(R.string.licenses_tab_fonts)
    )
    var selectedTab by remember { mutableIntStateOf(0) }
    var loadedText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedTab) {
        loadedText = null
        val path = tabs[selectedTab].first
        loadedText = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(path).bufferedReader().use { it.readText() }
            }.getOrElse { "Unable to load $path" }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Gavel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.settings_licenses_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.btn_close))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEachIndexed { idx, (_, label) ->
                    FilterChip(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 14.dp)
            ) {
                val text = loadedText
                if (text == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp))
                    }
                } else {
                    SelectionContainer {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticLogViewerSheet(
    lineCountProvider: () -> Int,
    sizeBytesProvider: () -> Long,
    reportLoader: suspend () -> String,
    onShareFile: suspend (chooserTitle: String) -> Unit,
    onClearLogs: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var reportText by remember { mutableStateOf<String?>(null) }
    var lineCount by remember { mutableIntStateOf(lineCountProvider()) }
    var sizeBytes by remember { mutableStateOf(sizeBytesProvider()) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        reportText = null
        reportText = withContext(Dispatchers.IO) { reportLoader() }
        lineCount = lineCountProvider()
        sizeBytes = sizeBytesProvider()
    }

    val sizeLabel = remember(sizeBytes) {
        if (sizeBytes < 1024L) "$sizeBytes B"
        else String.format(java.util.Locale.US, "%.1f KB", sizeBytes / 1024.0)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .testTag("diagnostic_log_viewer_sheet")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.diagnostic_logs_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.diagnostic_logs_stats_fmt, lineCount, sizeLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.btn_close))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    onShareFile(context.getString(R.string.diagnostic_logs_title))
                                }
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.diagnostic_logs_export_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag("diagnostic_logs_share_button")
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.diagnostic_logs_share_file))
                }

                OutlinedButton(
                    onClick = {
                        val txt = reportText ?: return@OutlinedButton
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(txt))
                        Toast.makeText(context, context.getString(R.string.diagnostic_logs_copied_toast), Toast.LENGTH_SHORT).show()
                    },
                    enabled = reportText != null,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.diagnostic_logs_copy))
                }

                IconButton(
                    onClick = {
                        onClearLogs()
                        reloadKey++
                        Toast.makeText(context, context.getString(R.string.diagnostic_logs_cleared_toast), Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = stringResource(R.string.diagnostic_logs_clear),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 14.dp)
            ) {
                val text = reportText
                if (text == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp))
                    }
                } else {
                    val scrollState = rememberScrollState()
                    LaunchedEffect(text) {
                        scrollState.scrollTo(scrollState.maxValue)
                    }
                    SelectionContainer {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(14.dp)
                        )
                    }
                }
            }
        }
    }
}
