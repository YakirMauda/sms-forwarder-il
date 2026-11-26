package com.qrcommunication.smsforwarder.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleJsonTest {

    @Test
    fun group_roundTrip_keepsHebrewAndRegex() {
        val group = ConditionGroup(
            GroupLogic.OR,
            listOf(Condition(MatchOperator.CONTAINS, "קוד אימות"), Condition(MatchOperator.REGEX, "\\b\\d{6}\\b")),
        )
        assertEquals(group, RuleJson.decodeGroup(RuleJson.encodeGroup(group)))
    }

    @Test
    fun targets_roundTrip() {
        val targets = listOf(Target(Channel.SMS, "0501234567"), Target(Channel.TELEGRAM, "123456"))
        assertEquals(targets, RuleJson.decodeTargets(RuleJson.encodeTargets(targets)))
    }

    @Test
    fun nullOrBlankOrGarbage_decodesToNull() {
        assertNull(RuleJson.decodeGroup(null))
        assertNull(RuleJson.decodeGroup(""))
        assertNull(RuleJson.decodeGroup("{not json"))
        assertNull(RuleJson.decodeTargets("  "))
    }

    @Test
    fun unknownOperatorsAndChannels_areSkipped() {
        val group = RuleJson.decodeGroup("""{"logic":"OR","conditions":[{"op":"FUTURE_OP","value":"x"},{"op":"CONTAINS","value":"y"}]}""")
        assertEquals(ConditionGroup(GroupLogic.OR, listOf(Condition(MatchOperator.CONTAINS, "y"))), group)
        val targets = RuleJson.decodeTargets("""[{"channel":"PIGEON","address":"x"},{"channel":"SMS","address":"050"}]""")
        assertEquals(listOf(Target(Channel.SMS, "050")), targets)
    }
}
