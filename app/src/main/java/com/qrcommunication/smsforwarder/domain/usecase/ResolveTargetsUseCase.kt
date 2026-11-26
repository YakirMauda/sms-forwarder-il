package com.qrcommunication.smsforwarder.domain.usecase

import com.qrcommunication.smsforwarder.data.mapper.toDomain
import com.qrcommunication.smsforwarder.data.preferences.PreferencesManager
import com.qrcommunication.smsforwarder.data.repository.ForwardingRuleRepository
import com.qrcommunication.smsforwarder.domain.rules.Channel
import com.qrcommunication.smsforwarder.domain.rules.PhoneNumbers
import com.qrcommunication.smsforwarder.domain.rules.ResolvedTarget
import com.qrcommunication.smsforwarder.domain.rules.RuleEvaluator
import com.qrcommunication.smsforwarder.domain.rules.Target
import javax.inject.Inject

/**
 * Where should this SMS go? The app is rule-driven:
 * - the targets of every enabled rule that matches;
 * - if none matches and the optional "default number" is switched on in Settings,
 *   the default number; otherwise the SMS is not forwarded.
 */
class ResolveTargetsUseCase @Inject constructor(
    private val ruleRepository: ForwardingRuleRepository,
    private val evaluator: RuleEvaluator,
    private val preferencesManager: PreferencesManager,
) {
    suspend operator fun invoke(sender: String, body: String): List<ResolvedTarget> {
        val rules = ruleRepository.getEnabledOrdered().map { it.toDomain() }
        val matched = evaluator.resolveTargets(rules, sender, body)
        if (matched.isNotEmpty() || !preferencesManager.isDefaultNumberEnabled) return matched

        val fallback = preferencesManager.destinationNumber
        if (fallback.isBlank() || PhoneNumbers.same(fallback, sender)) return emptyList()
        return listOf(ResolvedTarget(Target(Channel.SMS, fallback), ruleId = null))
    }
}
