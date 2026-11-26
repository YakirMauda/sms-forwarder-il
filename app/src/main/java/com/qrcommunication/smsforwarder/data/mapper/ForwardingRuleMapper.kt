package com.qrcommunication.smsforwarder.data.mapper

import com.qrcommunication.smsforwarder.data.local.entity.DestinationType
import com.qrcommunication.smsforwarder.data.local.entity.ForwardingRule
import com.qrcommunication.smsforwarder.domain.rules.Channel
import com.qrcommunication.smsforwarder.domain.rules.Condition
import com.qrcommunication.smsforwarder.domain.rules.ConditionGroup
import com.qrcommunication.smsforwarder.domain.rules.GroupLogic
import com.qrcommunication.smsforwarder.domain.rules.MatchOperator
import com.qrcommunication.smsforwarder.domain.rules.RegexValidator
import com.qrcommunication.smsforwarder.domain.rules.Rule
import com.qrcommunication.smsforwarder.domain.rules.RuleJson
import com.qrcommunication.smsforwarder.domain.rules.Target

/**
 * Room row -> domain Rule. Rows written by the new engine carry JSON columns; legacy rows
 * (old editor) are converted on the fly: pattern -> one REGEX condition (CONTAINS if the
 * pattern is not a valid regex), destination -> single target.
 */
fun ForwardingRule.toDomain(): Rule = Rule(
    id = id,
    name = name,
    enabled = isEnabled,
    priority = priority,
    sender = RuleJson.decodeGroup(senderGroupJson) ?: legacyGroup(senderPattern),
    content = RuleJson.decodeGroup(contentGroupJson) ?: legacyGroup(keywordPattern),
    targets = RuleJson.decodeTargets(targetsJson)
        ?: listOfNotNull(destination.takeIf { it.isNotBlank() }?.let { Target(DestinationType.fromValue(destinationType).toChannel(), it) }),
)

/** Domain Rule -> Room row (keeps stats/timestamps of [base] when updating). */
fun Rule.toEntity(base: ForwardingRule? = null, now: Long = System.currentTimeMillis()): ForwardingRule {
    val first = targets.firstOrNull()
    return (base ?: ForwardingRule(name = name, destination = "", createdAt = now, updatedAt = now)).copy(
        id = id,
        name = name,
        priority = priority,
        isEnabled = enabled,
        senderPattern = null,
        keywordPattern = null,
        destinationType = (first?.channel ?: Channel.SMS).toDestinationType().name,
        destination = first?.address.orEmpty(),
        senderGroupJson = RuleJson.encodeGroup(sender),
        contentGroupJson = RuleJson.encodeGroup(content),
        targetsJson = RuleJson.encodeTargets(targets),
    )
}

private fun legacyGroup(pattern: String?): ConditionGroup {
    val p = pattern?.takeIf { it.isNotBlank() } ?: return ConditionGroup.MATCH_ALL
    val op = if (RegexValidator.isValid(p)) MatchOperator.REGEX else MatchOperator.CONTAINS
    return ConditionGroup(GroupLogic.AND, listOf(Condition(op, p)))
}

fun DestinationType.toChannel(): Channel = when (this) {
    DestinationType.SMS -> Channel.SMS
    DestinationType.WEBHOOK -> Channel.WEBHOOK
}

/** Legacy column only knows SMS/WEBHOOK; Telegram rules always carry targets JSON. */
fun Channel.toDestinationType(): DestinationType = when (this) {
    Channel.WEBHOOK -> DestinationType.WEBHOOK
    else -> DestinationType.SMS
}
