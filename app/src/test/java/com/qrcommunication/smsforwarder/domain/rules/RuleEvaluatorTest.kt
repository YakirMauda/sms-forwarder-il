package com.qrcommunication.smsforwarder.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEvaluatorTest {

    private val evaluator = RuleEvaluator()
    private val me = Target(Channel.SMS, "0501234567")

    private fun rule(
        id: Long = 1,
        sender: ConditionGroup = ConditionGroup.MATCH_ALL,
        content: ConditionGroup = ConditionGroup.MATCH_ALL,
        targets: List<Target> = listOf(me),
        priority: Int = 0,
        enabled: Boolean = true,
    ) = Rule(id, "r$id", enabled, priority, sender, content, targets)

    private fun group(logic: GroupLogic, vararg c: Pair<MatchOperator, String>) =
        ConditionGroup(logic, c.map { Condition(it.first, it.second) })

    // --- group semantics ---

    @Test
    fun emptyGroups_matchEverything() {
        assertTrue(evaluator.matches(rule(), "anyone", "anything"))
    }

    @Test
    fun firesOnlyWhenSenderAndContentBothMatch() {
        val r = rule(
            sender = group(GroupLogic.OR, MatchOperator.EQUALS to "Leumi"),
            content = group(GroupLogic.OR, MatchOperator.CONTAINS to "קוד"),
        )
        assertTrue(evaluator.matches(r, "Leumi", "קוד האימות: 1234"))
        assertFalse(evaluator.matches(r, "Leumi", "יתרה בחשבון"))
        assertFalse(evaluator.matches(r, "Hapoalim", "קוד האימות: 1234"))
    }

    @Test
    fun andGroup_needsAll_orGroup_needsAny() {
        val and = group(GroupLogic.AND, MatchOperator.CONTAINS to "code", MatchOperator.NOT_CONTAINS to "promo")
        val or = group(GroupLogic.OR, MatchOperator.CONTAINS to "code", MatchOperator.CONTAINS to "קוד")
        assertTrue(evaluator.matches(rule(content = and), "x", "Your code 1234"))
        assertFalse(evaluator.matches(rule(content = and), "x", "promo code 1234"))
        assertTrue(evaluator.matches(rule(content = or), "x", "הקוד שלך 1234"))
        assertFalse(evaluator.matches(rule(content = or), "x", "שלום"))
    }

    // --- sender: alphanumeric IDs, English and Hebrew ---

    @Test
    fun sender_alphanumeric_caseInsensitive() {
        val r = rule(sender = group(GroupLogic.OR, MatchOperator.EQUALS to "leumi"))
        assertTrue(evaluator.matches(r, "LEUMI", ""))
        assertTrue(evaluator.matches(r, " Leumi ", ""))
    }

    @Test
    fun sender_hebrewId() {
        val r = rule(sender = group(GroupLogic.OR, MatchOperator.STARTS_WITH to "כללית"))
        assertTrue(evaluator.matches(r, "כללית מושלם", ""))
        assertFalse(evaluator.matches(r, "מכבי", ""))
    }

    // --- sender: phone normalization ---

    @Test
    fun sender_phoneEquals_ignoresPrefixAndFormatting() {
        val r = rule(sender = group(GroupLogic.OR, MatchOperator.EQUALS to "050-123-4567"))
        assertTrue(evaluator.matches(r, "+972501234567", ""))
        assertTrue(evaluator.matches(r, "972501234567", ""))
        assertTrue(evaluator.matches(r, "0501234567", ""))
        assertFalse(evaluator.matches(r, "0501234568", ""))
    }

    @Test
    fun sender_phoneStartsWith_usesNormalizedForm() {
        val r = rule(sender = group(GroupLogic.OR, MatchOperator.STARTS_WITH to "+97252"))
        assertTrue(evaluator.matches(r, "052-9999999", ""))
        assertFalse(evaluator.matches(r, "0549999999", ""))
    }

    @Test
    fun sender_regex_isAppliedToRawSender() {
        val r = rule(sender = group(GroupLogic.OR, MatchOperator.REGEX to "^\\+9725"))
        assertTrue(evaluator.matches(r, "+972501234567", ""))
        assertFalse(evaluator.matches(r, "0501234567", ""))
    }

    // --- content operators ---

    @Test
    fun content_operators() {
        fun m(op: MatchOperator, v: String, body: String) =
            evaluator.matches(rule(content = group(GroupLogic.AND, op to v)), "x", body)

        assertTrue(m(MatchOperator.STARTS_WITH, "your", "Your code is 1"))
        assertTrue(m(MatchOperator.ENDS_WITH, "1234", "code 1234"))
        assertTrue(m(MatchOperator.EXACT, "hello", "HELLO"))
        assertFalse(m(MatchOperator.EXACT, "hello", "hello there"))
        assertTrue(m(MatchOperator.REGEX, "\\b\\d{6}\\b", "קוד: 123456"))
        assertTrue(m(MatchOperator.NOT_CONTAINS, "פרסומת", "קוד אימות"))
    }

    @Test
    fun invalidRegex_neverMatches_andIsReported() {
        assertFalse(RegexValidator.isValid("([unclosed"))
        assertFalse(evaluator.matches(rule(content = group(GroupLogic.AND, MatchOperator.REGEX to "([unclosed")), "x", "([unclosed"))
    }

    // --- targets ---

    @Test
    fun disabledRules_areIgnored() {
        assertTrue(evaluator.resolveTargets(listOf(rule(enabled = false)), "Leumi", "x").isEmpty())
    }

    @Test
    fun targets_dedupedAcrossRules_highestPriorityFirst() {
        val mom = Target(Channel.SMS, "052-7654321")
        val rules = listOf(
            rule(id = 1, priority = 1, targets = listOf(Target(Channel.SMS, "+972501234567"))),
            rule(id = 2, priority = 5, targets = listOf(me, mom)),
        )
        val resolved = evaluator.resolveTargets(rules, "Leumi", "x")
        assertEquals(listOf(me, mom), resolved.map { it.target })
        assertEquals(listOf(2L, 2L), resolved.map { it.ruleId })
    }

    @Test
    fun loopGuard_dropsSmsTargetEqualToSender() {
        val resolved = evaluator.resolveTargets(listOf(rule()), "+972-50-123-4567", "x")
        assertTrue(resolved.isEmpty())
    }

    @Test
    fun loopGuard_doesNotApplyToWebhook() {
        val hook = Target(Channel.WEBHOOK, "https://example.com/h")
        val resolved = evaluator.resolveTargets(listOf(rule(targets = listOf(hook))), "0501234567", "x")
        assertEquals(listOf(hook), resolved.map { it.target })
    }
}
