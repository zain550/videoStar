package com.example.videograb.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.videograb.AppSettings
import com.example.videograb.Engine
import com.example.videograb.R
import com.example.videograb.UpdateResult
import com.example.videograb.cleanError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updating by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.headlineMedium,
        )

        SwitchRow(
            title = stringResource(R.string.settings_wifi_only),
            description = stringResource(R.string.settings_wifi_only_desc),
            checked = AppSettings.wifiOnly,
            onChange = { AppSettings.setWifiOnly(it) },
        )
        SwitchRow(
            title = stringResource(R.string.settings_auto_update),
            description = stringResource(R.string.settings_auto_update_desc),
            checked = AppSettings.autoUpdate,
            onChange = { AppSettings.setAutoUpdate(it) },
        )

        HorizontalDivider()

        Button(
            onClick = {
                scope.launch {
                    updating = true
                    updateMessage = null
                    updateMessage = try {
                        when (Engine.update(context)) {
                            UpdateResult.UPDATED -> context.getString(R.string.update_done)
                            UpdateResult.UP_TO_DATE -> context.getString(R.string.update_latest)
                            UpdateResult.UNKNOWN -> context.getString(R.string.update_done)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        context.getString(R.string.update_failed, cleanError(e.message) ?: e.javaClass.simpleName)
                    } finally {
                        updating = false
                    }
                }
            },
            enabled = !updating,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (updating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.updating))
            } else {
                Text(stringResource(R.string.settings_update_now))
            }
        }
        updateMessage?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }

        HorizontalDivider()

        Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.save_location),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.legal_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
