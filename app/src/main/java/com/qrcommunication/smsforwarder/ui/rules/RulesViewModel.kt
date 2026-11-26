package com.qrcommunication.smsforwarder.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qrcommunication.smsforwarder.data.local.entity.ForwardingRule
import com.qrcommunication.smsforwarder.data.mapper.toDomain
import com.qrcommunication.smsforwarder.data.preferences.PreferencesManager
import com.qrcommunication.smsforwarder.data.repository.ForwardingRuleRepository
import com.qrcommunication.smsforwarder.domain.rules.Rule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RuleItem(val rule: Rule, val entity: ForwardingRule)

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val repository: ForwardingRuleRepository,
    private val preferences: PreferencesManager,
) : ViewModel() {

    val rules: StateFlow<List<RuleItem>> = repository.observeAll()
        .map { list -> list.map { RuleItem(it.toDomain(), it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _forwardingEnabled = MutableStateFlow(preferences.isForwardingEnabled)
    val forwardingEnabled: StateFlow<Boolean> = _forwardingEnabled.asStateFlow()

    /** Last deleted rule, kept so "Undo" can put it back. */
    private var lastDeleted: ForwardingRule? = null

    fun setForwardingEnabled(enabled: Boolean) {
        preferences.isForwardingEnabled = enabled
        _forwardingEnabled.value = enabled
    }

    fun setRuleEnabled(id: Long, enabled: Boolean) = viewModelScope.launch { repository.setEnabled(id, enabled) }

    fun delete(item: RuleItem) = viewModelScope.launch {
        lastDeleted = item.entity
        repository.delete(item.entity)
    }

    fun undoDelete() = viewModelScope.launch {
        lastDeleted?.let { repository.upsert(it.copy(id = 0)) }
        lastDeleted = null
    }
}
