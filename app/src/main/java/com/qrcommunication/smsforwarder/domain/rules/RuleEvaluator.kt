package com.qrcommunication.smsforwarder.domain.rules

/**
 * Pure rule engine (no Android): decides which rules fire for an SMS and which targets
 * to send it to.
 *
 * - A rule fires iff its sender group AND its content group match.
 * - Sender comparisons normalize phone numbers (+972 / 972 / 0, spaces, dashes) when
 *   both the sender and the condition value look like numbers; alphanumeric IDs
 *   ("Leumi", "כללית") are compared as text.
 * - Targets from all matching rules are de-duplicated, highest priority first.
 * - Loop guard: an SMS target equal to the sender is dropped.
 */
class RuleEvaluator(private val registry: MatcherRegistry = MatcherRegistry.DEFAULT) {

    fun matchingRules(rules: List<Rule>, sender: String, body: String): List<Rule> =
        rules.filter { it.enabled }
            .sortedByDescending { it.priority }
            .filter { matches(it, sender, body) }

    fun resolveTargets(rules: List<Rule>, sender: String, body: String): List<ResolvedTarget> =
        resolve(rules, sender, body).targets

    fun resolve(rules: List<Rule>, sender: String, body: String): Resolution {
        val seen = mutableSetOf<Pair<Channel, String>>()
        val result = mutableListOf<ResolvedTarget>()
        val loopSkipped = mutableListOf<ResolvedTarget>()
        for (rule in matchingRules(rules, sender, body)) {
            for (target in rule.targets) {
                if (target.address.isBlank()) continue
                if (!seen.add(target.channel to dedupKey(target))) continue
                if (target.channel == Channel.SMS && PhoneNumbers.same(target.address, sender)) {
                    loopSkipped += ResolvedTarget(target, rule.id)
                } else {
                    result += ResolvedTarget(target, rule.id)
                }
            }
        }
        return Resolution(result, loopSkipped)
    }

    fun matches(rule: Rule, sender: String, body: String): Boolean = explain(rule, sender, body).matches

    /** Which half of the rule matched; lets the editor say *why* a sample would not be forwarded. */
    fun explain(rule: Rule, sender: String, body: String) = MatchExplanation(
        senderMatches = groupMatches(rule.sender, sender, isSender = true),
        contentMatches = groupMatches(rule.content, body, isSender = false),
    )

    private fun groupMatches(group: ConditionGroup, input: String, isSender: Boolean): Boolean {
        if (group.isEmpty) return true
        val results = group.conditions.asSequence().map { conditionMatches(it, input, isSender) }
        return when (group.logic) {
            GroupLogic.AND -> results.all { it }
            GroupLogic.OR -> results.any { it }
        }
    }

    private fun conditionMatches(condition: Condition, input: String, isSender: Boolean): Boolean {
        // Keyboards often add a trailing space ("Leumi "); it is never meant. Regex is taken as typed.
        val condition = if (condition.operator == MatchOperator.REGEX) condition else condition.copy(value = condition.value.trim())
        if (condition.value.isEmpty()) return true
        val matcher = registry.get(condition.operator)
        val useNumbers = isSender && condition.operator != MatchOperator.REGEX &&
            PhoneNumbers.isPhoneLike(input) && PhoneNumbers.isPhoneLike(condition.value)
        return if (useNumbers) {
            matcher.matches(PhoneNumbers.normalize(input), PhoneNumbers.normalize(condition.value))
        } else {
            matcher.matches(input, condition.value)
        }
    }

    private fun dedupKey(target: Target): String = when (target.channel) {
        Channel.SMS -> if (PhoneNumbers.isPhoneLike(target.address)) PhoneNumbers.normalize(target.address) else target.address.trim()
        else -> target.address.trim()
    }
}
