package com.qrcommunication.smsforwarder.domain.usecase

import com.qrcommunication.smsforwarder.data.local.entity.ForwardingRule
import com.qrcommunication.smsforwarder.data.mapper.toDomain
import com.qrcommunication.smsforwarder.data.repository.ForwardingRuleRepository
import com.qrcommunication.smsforwarder.domain.rules.RuleEvaluator
import javax.inject.Inject

/**
 * First enabled rule (highest priority) that matches a sample SMS, evaluated by the new
 * rule engine. Used by the rule editor's "test" button.
 */
class MatchForwardingRuleUseCase @Inject constructor(
    private val ruleRepository: ForwardingRuleRepository,
    private val evaluator: RuleEvaluator,
) {
    suspend operator fun invoke(sender: String, content: String): ForwardingRule? =
        ruleRepository.getEnabledOrdered().firstOrNull { evaluator.matches(it.toDomain(), sender, content) }
}
