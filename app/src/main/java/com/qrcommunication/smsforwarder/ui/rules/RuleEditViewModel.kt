package com.qrcommunication.smsforwarder.ui.rules

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qrcommunication.smsforwarder.data.local.entity.ForwardingRule
import com.qrcommunication.smsforwarder.data.mapper.toDomain
import com.qrcommunication.smsforwarder.data.mapper.toEntity
import com.qrcommunication.smsforwarder.data.preferences.PreferencesManager
import com.qrcommunication.smsforwarder.data.repository.ForwardingRuleRepository
import com.qrcommunication.smsforwarder.domain.rules.Channel
import com.qrcommunication.smsforwarder.domain.rules.Condition
import com.qrcommunication.smsforwarder.domain.rules.ConditionGroup
import com.qrcommunication.smsforwarder.domain.rules.GroupLogic
import com.qrcommunication.smsforwarder.domain.rules.MatchExplanation
import com.qrcommunication.smsforwarder.domain.rules.MatchOperator
import com.qrcommunication.smsforwarder.domain.rules.RegexValidator
import com.qrcommunication.smsforwarder.domain.rules.Rule
import com.qrcommunication.smsforwarder.domain.rules.RuleEvaluator
import com.qrcommunication.smsforwarder.domain.rules.Target
import com.qrcommunication.smsforwarder.domain.rules.TargetValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class GroupKind { SENDER, CONTENT }

enum class RuleEditError { NO_TARGET, INVALID_REGEX, INVALID_TARGET }

data class RuleEditState(
    val id: Long = 0,
    val name: String = "",
    val enabled: Boolean = true,
    val sender: ConditionGroup = ConditionGroup(GroupLogic.OR, listOf(Condition(MatchOperator.EQUALS, ""))),
    // Owner's choice: both groups start as OR ("any of these").
    val content: ConditionGroup = ConditionGroup(GroupLogic.OR, listOf(Condition(MatchOperator.CONTAINS, ""))),
    val targets: List<Target> = listOf(Target(Channel.SMS, "")),
    val testSender: String = "",
    val testBody: String = "",
    val error: RuleEditError? = null,
    val saved: Boolean = false,
    val telegramConfigured: Boolean = false,
) {
    fun group(kind: GroupKind) = if (kind == GroupKind.SENDER) sender else content

    /** Rule as it would be saved: blank conditions/targets dropped, default name if empty. */
    fun toRule(defaultName: String): Rule = Rule(
        id = id,
        name = name.trim().ifEmpty { defaultName },
        enabled = enabled,
        sender = sender.copy(conditions = sender.conditions.filter { it.value.isNotBlank() }),
        content = content.copy(conditions = content.conditions.filter { it.value.isNotBlank() }),
        targets = targets.map { it.copy(address = it.address.trim()) }.filter { it.address.isNotEmpty() },
    )

    /** No condition at all: the rule would forward EVERY incoming SMS. */
    fun matchesEverything(defaultName: String): Boolean = toRule(defaultName).let { it.sender.isEmpty && it.content.isEmpty }

    val hasInvalidRegex: Boolean
        get() = (sender.conditions + content.conditions).any {
            it.operator == MatchOperator.REGEX && it.value.isNotBlank() && !RegexValidator.isValid(it.value)
        }
}

@HiltViewModel
class RuleEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ForwardingRuleRepository,
    private val evaluator: RuleEvaluator,
    preferences: PreferencesManager,
) : ViewModel() {

    private val ruleId: Long = savedStateHandle.get<Long>(ARG_RULE_ID) ?: 0L
    private var original: ForwardingRule? = null

    private val _state = MutableStateFlow(RuleEditState(telegramConfigured = preferences.telegramBotToken.isNotBlank()))
    val state: StateFlow<RuleEditState> = _state.asStateFlow()

    init {
        if (ruleId > 0) viewModelScope.launch {
            val entity = repository.getById(ruleId) ?: return@launch
            original = entity
            val rule = entity.toDomain()
            _state.update {
                it.copy(
                    id = rule.id,
                    name = rule.name,
                    enabled = rule.enabled,
                    sender = rule.sender,
                    content = rule.content,
                    targets = rule.targets.ifEmpty { listOf(Target(Channel.SMS, "")) },
                )
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }
    fun setEnabled(value: Boolean) = _state.update { it.copy(enabled = value) }
    fun setTestSender(value: String) = _state.update { it.copy(testSender = value) }
    fun setTestBody(value: String) = _state.update { it.copy(testBody = value) }

    fun setLogic(kind: GroupKind, logic: GroupLogic) = updateGroup(kind) { it.copy(logic = logic) }

    fun addCondition(kind: GroupKind) = updateGroup(kind) {
        val op = if (kind == GroupKind.SENDER) MatchOperator.EQUALS else MatchOperator.CONTAINS
        it.copy(conditions = it.conditions + Condition(op, ""))
    }

    fun removeCondition(kind: GroupKind, index: Int) = updateGroup(kind) {
        it.copy(conditions = it.conditions.filterIndexed { i, _ -> i != index })
    }

    fun setOperator(kind: GroupKind, index: Int, op: MatchOperator) = updateCondition(kind, index) { it.copy(operator = op) }
    fun setValue(kind: GroupKind, index: Int, value: String) = updateCondition(kind, index) { it.copy(value = value) }

    fun addTarget() = _state.update { it.copy(targets = it.targets + Target(Channel.SMS, "")) }
    fun removeTarget(index: Int) = _state.update { s -> s.copy(targets = s.targets.filterIndexed { i, _ -> i != index }) }
    fun setTargetAddress(index: Int, value: String) = updateTarget(index) { it.copy(address = value) }
    fun setTargetChannel(index: Int, channel: Channel) = updateTarget(index) { it.copy(channel = channel) }

    /** Live answer for the tester: would THIS rule (as currently edited) forward the sample, and if not, why? */
    fun testResult(defaultName: String): MatchExplanation? {
        val s = _state.value
        if (s.testSender.isBlank() && s.testBody.isBlank()) return null
        return evaluator.explain(s.toRule(defaultName).copy(enabled = true), s.testSender, s.testBody)
    }

    /** True when the sample matches but every target is the sender itself (loop guard: nothing would be sent). */
    fun testOnlyLoops(defaultName: String): Boolean {
        val s = _state.value
        val r = evaluator.resolve(listOf(s.toRule(defaultName).copy(enabled = true)), s.testSender, s.testBody)
        return r.targets.isEmpty() && r.loopSkipped.isNotEmpty()
    }

    fun save(defaultName: String) {
        val s = _state.value
        val rule = s.toRule(defaultName)
        val error = when {
            s.hasInvalidRegex -> RuleEditError.INVALID_REGEX
            rule.targets.isEmpty() -> RuleEditError.NO_TARGET
            rule.targets.any { !TargetValidator.isValid(it) } -> RuleEditError.INVALID_TARGET
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        viewModelScope.launch {
            repository.upsert(rule.toEntity(base = original))
            _state.update { it.copy(saved = true, error = null) }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun updateGroup(kind: GroupKind, transform: (ConditionGroup) -> ConditionGroup) = _state.update {
        if (kind == GroupKind.SENDER) it.copy(sender = transform(it.sender)) else it.copy(content = transform(it.content))
    }

    private fun updateCondition(kind: GroupKind, index: Int, transform: (Condition) -> Condition) = updateGroup(kind) { g ->
        g.copy(conditions = g.conditions.mapIndexed { i, c -> if (i == index) transform(c) else c })
    }

    private fun updateTarget(index: Int, transform: (Target) -> Target) = _state.update { s ->
        s.copy(targets = s.targets.mapIndexed { i, t -> if (i == index) transform(t) else t })
    }

    companion object {
        const val ARG_RULE_ID = "ruleId"
    }
}
