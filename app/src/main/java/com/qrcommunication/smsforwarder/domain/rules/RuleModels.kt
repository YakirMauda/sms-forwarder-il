package com.qrcommunication.smsforwarder.domain.rules

/** How the conditions inside one group are combined. */
enum class GroupLogic { AND, OR }

/**
 * Match operators. Sender groups use EQUALS/CONTAINS/STARTS_WITH/ENDS_WITH/REGEX,
 * content groups use CONTAINS/NOT_CONTAINS/STARTS_WITH/ENDS_WITH/EXACT/REGEX.
 * EQUALS and EXACT behave the same; they exist so each editor shows its own wording.
 */
enum class MatchOperator {
    EQUALS, CONTAINS, NOT_CONTAINS, STARTS_WITH, ENDS_WITH, EXACT, REGEX;

    companion object {
        val SENDER = listOf(EQUALS, CONTAINS, STARTS_WITH, ENDS_WITH, REGEX)
        val CONTENT = listOf(CONTAINS, NOT_CONTAINS, STARTS_WITH, ENDS_WITH, EXACT, REGEX)

        fun fromName(name: String?): MatchOperator? = entries.firstOrNull { it.name == name }
    }
}

data class Condition(val operator: MatchOperator, val value: String)

/** An empty group matches everything. */
data class ConditionGroup(
    val logic: GroupLogic = GroupLogic.AND,
    val conditions: List<Condition> = emptyList(),
) {
    val isEmpty: Boolean get() = conditions.isEmpty()

    companion object {
        val MATCH_ALL = ConditionGroup()
    }
}

enum class Channel { SMS, TELEGRAM, WEBHOOK }

/** Where a matching message is forwarded: a phone number, a Telegram chat id or a URL. */
data class Target(val channel: Channel, val address: String)

/** Fires iff `sender` matches AND `content` matches. */
data class Rule(
    val id: Long,
    val name: String,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val sender: ConditionGroup = ConditionGroup.MATCH_ALL,
    val content: ConditionGroup = ConditionGroup.MATCH_ALL,
    val targets: List<Target> = emptyList(),
)

/** A target to send to, plus the (first, highest-priority) rule that produced it. */
data class ResolvedTarget(val target: Target, val ruleId: Long?)
