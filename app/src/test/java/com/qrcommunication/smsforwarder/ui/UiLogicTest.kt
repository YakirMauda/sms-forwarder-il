package com.qrcommunication.smsforwarder.ui

import com.qrcommunication.smsforwarder.data.local.entity.SmsRecord
import com.qrcommunication.smsforwarder.data.local.entity.SmsStatus
import com.qrcommunication.smsforwarder.domain.rules.Channel
import com.qrcommunication.smsforwarder.domain.rules.Condition
import com.qrcommunication.smsforwarder.domain.rules.ConditionGroup
import com.qrcommunication.smsforwarder.domain.rules.GroupLogic
import com.qrcommunication.smsforwarder.domain.rules.MatchOperator
import com.qrcommunication.smsforwarder.domain.rules.Target
import com.qrcommunication.smsforwarder.ui.history.HistoryStats
import com.qrcommunication.smsforwarder.ui.rules.RuleEditState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class UiLogicTest {

    // --- History stats ---

    private fun record(status: SmsStatus, at: Long) =
        SmsRecord(sender = "x", content = "y", receivedAt = at, status = status.value, destination = "050")

    @Test
    fun stats_countTodayMonthRateFailed() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 26, 15, 0, 0) }.timeInMillis
        val hour = 3_600_000L
        val records = listOf(
            record(SmsStatus.SENT, now - hour), // today
            record(SmsStatus.SENT, now - 3 * 24 * hour), // this month
            record(SmsStatus.SENT, now - 40 * 24 * hour), // last month
            record(SmsStatus.FAILED, now - hour),
            record(SmsStatus.PENDING, now),
        )

        val stats = HistoryStats.from(records, now)

        assertEquals(1, stats.today)
        assertEquals(2, stats.month)
        assertEquals(75, stats.successRatePercent) // 3 sent / (3 sent + 1 failed)
        assertEquals(1, stats.failed)
    }

    @Test
    fun stats_noFinishedSends_rateIsNull() {
        assertNull(HistoryStats.from(emptyList(), 0L).successRatePercent)
    }

    // --- Rule editor ---

    @Test
    fun toRule_dropsBlankConditionsAndTargets_andDefaultsName() {
        val state = RuleEditState(
            name = "  ",
            sender = ConditionGroup(GroupLogic.OR, listOf(Condition(MatchOperator.EQUALS, "Leumi"), Condition(MatchOperator.EQUALS, " "))),
            content = ConditionGroup(GroupLogic.AND, listOf(Condition(MatchOperator.CONTAINS, ""))),
            targets = listOf(Target(Channel.SMS, " 0501234567 "), Target(Channel.SMS, "")),
        )

        val rule = state.toRule(defaultName = "New rule")

        assertEquals("New rule", rule.name)
        assertEquals(listOf(Condition(MatchOperator.EQUALS, "Leumi")), rule.sender.conditions)
        assertTrue(rule.content.isEmpty)
        assertEquals(listOf(Target(Channel.SMS, "0501234567")), rule.targets)
    }

    @Test
    fun newRule_bothGroupsDefaultToOr() {
        val state = RuleEditState()
        assertEquals(GroupLogic.OR, state.sender.logic)
        assertEquals(GroupLogic.OR, state.content.logic)
    }

    @Test
    fun hasInvalidRegex_onlyForNonBlankRegexConditions() {
        val bad = RuleEditState(content = ConditionGroup(GroupLogic.AND, listOf(Condition(MatchOperator.REGEX, "([x"))))
        val blank = RuleEditState(content = ConditionGroup(GroupLogic.AND, listOf(Condition(MatchOperator.REGEX, ""))))
        val good = RuleEditState(content = ConditionGroup(GroupLogic.AND, listOf(Condition(MatchOperator.REGEX, "\\d{6}"))))

        assertTrue(bad.hasInvalidRegex)
        assertFalse(blank.hasInvalidRegex)
        assertFalse(good.hasInvalidRegex)
    }
}
