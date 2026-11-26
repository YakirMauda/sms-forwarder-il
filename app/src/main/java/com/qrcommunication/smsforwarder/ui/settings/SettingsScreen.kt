package com.qrcommunication.smsforwarder.ui.settings

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.qrcommunication.smsforwarder.R
import com.qrcommunication.smsforwarder.service.SimChoice
import com.qrcommunication.smsforwarder.ui.common.LtrText
import com.qrcommunication.smsforwarder.ui.common.SectionCard
import com.qrcommunication.smsforwarder.ui.common.SectionTitle
import com.qrcommunication.smsforwarder.ui.permissions.RestrictedSettingsSteps
import com.qrcommunication.smsforwarder.ui.permissions.SystemState
import com.qrcommunication.smsforwarder.util.LocaleManager

@Composable
fun SettingsScreen(onFixBattery: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Language
        SectionCard {
            SectionTitle(stringResource(R.string.settings_language))
            Spacer(Modifier.height(8.dp))
            val options = listOf(
                LocaleManager.LANGUAGE_SYSTEM to stringResource(R.string.lang_system),
                LocaleManager.LANGUAGE_HEBREW to stringResource(R.string.lang_he),
                LocaleManager.LANGUAGE_ENGLISH to stringResource(R.string.lang_en),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, (code, label) ->
                    SegmentedButton(
                        selected = state.language == code,
                        onClick = { if (viewModel.setLanguage(code)) (context as? Activity)?.recreate() },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    ) { Text(label) }
                }
            }
        }

        // Optional default number
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionTitle(stringResource(R.string.settings_default_number))
                    Text(
                        stringResource(R.string.settings_default_number_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.defaultNumberEnabled, onCheckedChange = viewModel::setDefaultNumberEnabled)
            }
            if (state.defaultNumberEnabled) {
                OutlinedTextField(
                    value = state.defaultNumber,
                    onValueChange = viewModel::setDefaultNumber,
                    placeholder = { Text(stringResource(R.string.target_hint_sms), style = LtrText) },
                    textStyle = LtrText,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }

        // Dual SIM (hidden on single-SIM phones)
        if (state.simCount > 1) {
            SectionCard {
                SectionTitle(stringResource(R.string.settings_sim))
                val options = buildList {
                    add(SimChoice.DEFAULT to stringResource(R.string.sim_default))
                    add(SimChoice.SAME_AS_INCOMING to stringResource(R.string.sim_same))
                    for (slot in 0 until state.simCount) add(slot to stringResource(R.string.sim_slot, slot + 1))
                }
                options.forEach { (value, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.simSetting == value, onClick = { viewModel.setSim(value) })
                        Text(label)
                    }
                }
            }
        }

        // Battery
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionTitle(stringResource(R.string.settings_battery))
                    Text(
                        stringResource(if (state.batteryExempt) R.string.battery_ok else R.string.battery_not_ok),
                        color = if (state.batteryExempt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (!state.batteryExempt) Button(onClick = onFixBattery) { Text(stringResource(R.string.action_fix)) }
            }
        }

        // Permissions + restricted settings guide
        SectionCard {
            SectionTitle(stringResource(R.string.settings_permissions))
            Text(
                stringResource(if (state.smsPermissions) R.string.perm_ok else R.string.perm_missing),
                color = if (state.smsPermissions) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
            RestrictedSettingsSteps()
            Button(
                onClick = { context.startActivity(SystemState.appDetailsIntent(context)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(stringResource(R.string.action_open_app_info)) }
        }

        // Telegram (optional)
        SectionCard {
            SectionTitle(stringResource(R.string.settings_telegram))
            Text(
                stringResource(R.string.telegram_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.telegramToken,
                onValueChange = viewModel::setTelegramToken,
                label = { Text(stringResource(R.string.telegram_token)) },
                textStyle = LtrText,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        // About
        SectionCard {
            SectionTitle(stringResource(R.string.settings_version))
            Text(state.version, style = LtrText)
            Text(stringResource(R.string.settings_updates), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_credits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
