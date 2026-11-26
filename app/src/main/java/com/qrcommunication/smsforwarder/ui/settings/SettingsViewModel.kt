package com.qrcommunication.smsforwarder.ui.settings

import android.app.Application
import android.telephony.SubscriptionManager
import androidx.lifecycle.AndroidViewModel
import com.qrcommunication.smsforwarder.data.preferences.PreferencesManager
import com.qrcommunication.smsforwarder.ui.permissions.SystemState
import com.qrcommunication.smsforwarder.util.LocaleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SettingsState(
    val language: String = LocaleManager.LANGUAGE_SYSTEM,
    val defaultNumberEnabled: Boolean = false,
    val defaultNumber: String = "",
    val simSetting: Int = -1,
    val simCount: Int = 1,
    val batteryExempt: Boolean = false,
    val smsPermissions: Boolean = false,
    val telegramToken: String = "",
    val version: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val app: Application,
    private val prefs: PreferencesManager,
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Re-read everything; called on resume so changes made in Android settings show up immediately. */
    fun refresh() {
        _state.value = SettingsState(
            language = prefs.appLanguage,
            defaultNumberEnabled = prefs.isDefaultNumberEnabled,
            defaultNumber = prefs.destinationNumber,
            simSetting = prefs.selectedSimSlot,
            simCount = simCount(),
            batteryExempt = SystemState.isIgnoringBatteryOptimizations(app),
            smsPermissions = SystemState.hasSmsPermissions(app),
            telegramToken = prefs.telegramBotToken,
            version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull().orEmpty(),
        )
    }

    /** @return true if the language changed (the activity must be recreated). */
    fun setLanguage(code: String): Boolean {
        if (code == prefs.appLanguage) return false
        prefs.appLanguage = code
        _state.update { it.copy(language = code) }
        return true
    }

    fun setDefaultNumberEnabled(enabled: Boolean) {
        prefs.isDefaultNumberEnabled = enabled
        _state.update { it.copy(defaultNumberEnabled = enabled) }
    }

    fun setDefaultNumber(value: String) {
        prefs.destinationNumber = value.trim()
        _state.update { it.copy(defaultNumber = value) }
    }

    fun setSim(setting: Int) {
        prefs.selectedSimSlot = setting
        _state.update { it.copy(simSetting = setting) }
    }

    fun setTelegramToken(value: String) {
        prefs.telegramBotToken = value
        _state.update { it.copy(telegramToken = value) }
    }

    private fun simCount(): Int = try {
        app.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoCount ?: 1
    } catch (_: SecurityException) {
        1
    }
}
