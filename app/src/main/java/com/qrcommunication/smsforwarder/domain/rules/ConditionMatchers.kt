package com.qrcommunication.smsforwarder.domain.rules

/**
 * One class per operator (Open/Closed): adding an operator = adding a class + a registry entry,
 * without touching the evaluator. All text comparisons are case-insensitive.
 */
fun interface ConditionMatcher {
    fun matches(input: String, value: String): Boolean
}

object EqualsMatcher : ConditionMatcher {
    override fun matches(input: String, value: String) = input.trim().equals(value.trim(), ignoreCase = true)
}

object ContainsMatcher : ConditionMatcher {
    override fun matches(input: String, value: String) = input.contains(value, ignoreCase = true)
}

object NotContainsMatcher : ConditionMatcher {
    override fun matches(input: String, value: String) = !input.contains(value, ignoreCase = true)
}

object StartsWithMatcher : ConditionMatcher {
    override fun matches(input: String, value: String) = input.trim().startsWith(value.trim(), ignoreCase = true)
}

object EndsWithMatcher : ConditionMatcher {
    override fun matches(input: String, value: String) = input.trim().endsWith(value.trim(), ignoreCase = true)
}

/** Invalid pattern = no match (the editor shows the error via [RegexValidator]). */
object RegexMatcher : ConditionMatcher {
    override fun matches(input: String, value: String): Boolean =
        RegexValidator.compile(value)?.containsMatchIn(input) ?: false
}

object RegexValidator {
    fun compile(pattern: String): Regex? =
        runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull()

    fun isValid(pattern: String): Boolean = compile(pattern) != null
}

class MatcherRegistry(private val matchers: Map<MatchOperator, ConditionMatcher>) {
    fun get(operator: MatchOperator): ConditionMatcher =
        matchers[operator] ?: error("No matcher registered for $operator")

    companion object {
        val DEFAULT = MatcherRegistry(
            mapOf(
                MatchOperator.EQUALS to EqualsMatcher,
                MatchOperator.EXACT to EqualsMatcher,
                MatchOperator.CONTAINS to ContainsMatcher,
                MatchOperator.NOT_CONTAINS to NotContainsMatcher,
                MatchOperator.STARTS_WITH to StartsWithMatcher,
                MatchOperator.ENDS_WITH to EndsWithMatcher,
                MatchOperator.REGEX to RegexMatcher,
            ),
        )
    }
}
