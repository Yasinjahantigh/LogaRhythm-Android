package com.yasinjahantigh.logarhythm.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.data.ai.AiProvider
import com.yasinjahantigh.logarhythm.data.ai.AiSettings
import com.yasinjahantigh.logarhythm.feature.ai.messageRes

@Composable
fun AiSettingsDialog(
    settings: AiSettings,
    testState: AiTestState,
    onTest: (AiProvider, String, String, String?) -> Unit,
    onSave: (AiProvider, String, String, String?) -> Unit,
    onClearKey: () -> Unit,
    onDismiss: () -> Unit
) {
    var provider by remember(settings.provider) { mutableStateOf(settings.provider) }
    var baseUrl by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var model by remember(settings.model) { mutableStateOf(settings.model) }
    var apiKey by remember { mutableStateOf("") }
    var showUrlError by remember { mutableStateOf(false) }

    fun selectProvider(next: AiProvider) {
        if (next == provider) return
        val baseWasDefault = baseUrl.trim().trimEnd('/') == AiSettings.defaultBase(provider)
        val modelWasDefault = model.trim() == AiSettings.defaultModel(provider)
        provider = next
        if (baseWasDefault) baseUrl = AiSettings.defaultBase(next)
        if (modelWasDefault) model = AiSettings.defaultModel(next)
    }

    fun validUrl(): Boolean {
        val value = baseUrl.trim()
        val valid = value.startsWith("https://") && value.length > "https://".length
        showUrlError = !valid
        return valid
    }

    val keyProvided = apiKey.isNotBlank() || settings.hasKey
    val canSave = keyProvided && model.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_ai_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.settings_ai_provider),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ProviderOption(
                    label = stringResource(R.string.settings_ai_provider_openai),
                    selected = provider == AiProvider.OPENAI_COMPATIBLE,
                    onClick = { selectProvider(AiProvider.OPENAI_COMPATIBLE) }
                )
                ProviderOption(
                    label = stringResource(R.string.settings_ai_provider_gemini),
                    selected = provider == AiProvider.GEMINI,
                    onClick = { selectProvider(AiProvider.GEMINI) }
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it
                        showUrlError = false
                    },
                    label = { Text(stringResource(R.string.settings_ai_base_url)) },
                    placeholder = { Text(AiSettings.defaultBase(provider)) },
                    singleLine = true,
                    isError = showUrlError,
                    supportingText = if (showUrlError) {
                        { Text(stringResource(R.string.settings_ai_invalid_url)) }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_ai_base_url")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text(stringResource(R.string.settings_ai_model)) },
                    placeholder = { Text(AiSettings.defaultModel(provider)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_ai_model")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.settings_ai_api_key)) },
                    placeholder = {
                        Text(
                            stringResource(
                                if (settings.hasKey) R.string.settings_ai_key_keep
                                else R.string.settings_ai_key_missing
                            )
                        )
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_ai_api_key")
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_ai_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            if (validUrl()) {
                                onTest(provider, baseUrl.trim(), model, apiKey.takeIf { it.isNotBlank() })
                            }
                        },
                        enabled = !testState.testing,
                        modifier = Modifier.testTag("settings_ai_test_button")
                    ) {
                        Text(
                            stringResource(
                                if (testState.testing) R.string.settings_ai_testing
                                else R.string.settings_ai_test
                            )
                        )
                    }
                    if (settings.hasKey) {
                        TextButton(onClick = onClearKey) {
                            Text(
                                text = stringResource(R.string.settings_ai_remove_key),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                when {
                    testState.success -> Text(
                        text = stringResource(R.string.settings_ai_test_ok),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    testState.failure != null -> Text(
                        text = stringResource(testState.failure.messageRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (validUrl()) {
                        onSave(provider, baseUrl.trim(), model, apiKey.takeIf { it.isNotBlank() })
                    }
                },
                enabled = canSave,
                modifier = Modifier.testTag("settings_ai_save_button")
            ) {
                Text(stringResource(R.string.settings_ai_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@Composable
private fun ProviderOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
