package com.qrcommunication.smsforwarder.domain.usecase

import com.qrcommunication.smsforwarder.data.local.entity.ForwardingRule
import com.qrcommunication.smsforwarder.data.preferences.PreferencesManager
import com.qrcommunication.smsforwarder.data.repository.ForwardingRuleRepository
import com.qrcommunication.smsforwarder.domain.rules.Channel
import com.qrcommunication.smsforwarder.domain.rules.ResolvedTarget
import com.qrcommunication.smsforwarder.domain.rules.RuleEvaluator
import com.qrcommunication.smsforwarder.domain.rules.Target
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ResolveTargetsUseCaseTest {

    private val repo: ForwardingRuleRepository = mock()
    private val prefs: PreferencesManager = mock()
    private val useCase = ResolveTargetsUseCase(repo, RuleEvaluator(), prefs)

    private fun legacyRule(senderPattern: String?, destination: String) = ForwardingRule(
        id = 3, name = "legacy", senderPattern = senderPattern, destination = destination, createdAt = 0, updatedAt = 0,
    )

    @Test
    fun noMatch_defaultNumberOff_forwardsNothing() = runTest {
        whenever(repo.getEnabledOrdered()).thenReturn(emptyList())
        whenever(prefs.isDefaultNumberEnabled).thenReturn(false)
        whenever(prefs.destinationNumber).thenReturn("0501234567")

        assertTrue(useCase("Leumi", "code 1").targets.isEmpty())
    }

    @Test
    fun noMatch_defaultNumberOn_goesToDefaultNumber() = runTest {
        whenever(repo.getEnabledOrdered()).thenReturn(listOf(legacyRule("Leumi", "0521112233")))
        whenever(prefs.isDefaultNumberEnabled).thenReturn(true)
        whenever(prefs.destinationNumber).thenReturn("0501234567")

        assertEquals(
            listOf(ResolvedTarget(Target(Channel.SMS, "0501234567"), null)),
            useCase("Hapoalim", "code 1").targets,
        )
    }

    @Test
    fun ruleMatches_defaultNumberOn_onlyRuleTargets() = runTest {
        whenever(repo.getEnabledOrdered()).thenReturn(listOf(legacyRule("Leumi", "0521112233")))
        whenever(prefs.isDefaultNumberEnabled).thenReturn(true)
        whenever(prefs.destinationNumber).thenReturn("0501234567")

        assertEquals(listOf("0521112233"), useCase("Leumi", "code 1").targets.map { it.target.address })
    }

    @Test
    fun defaultNumberOn_butBlank_forwardsNothing() = runTest {
        whenever(repo.getEnabledOrdered()).thenReturn(emptyList())
        whenever(prefs.isDefaultNumberEnabled).thenReturn(true)
        whenever(prefs.destinationNumber).thenReturn("")

        assertTrue(useCase("Leumi", "code 1").targets.isEmpty())
    }

    @Test
    fun ruleTargetIsSender_isLoopSkipped_andNoCatchAll() = runTest {
        // Rule with no sender pattern (matches anyone) whose only target is the sender's own number.
        whenever(repo.getEnabledOrdered()).thenReturn(listOf(legacyRule(null, "+972521112233")))
        whenever(prefs.isDefaultNumberEnabled).thenReturn(true)
        whenever(prefs.destinationNumber).thenReturn("0501234567")

        val result = useCase("052-111-2233", "hi")

        assertTrue(result.targets.isEmpty())
        assertEquals(listOf("+972521112233"), result.loopSkipped.map { it.target.address })
    }

    @Test
    fun legacyRow_isConvertedAndMatches() = runTest {
        whenever(repo.getEnabledOrdered()).thenReturn(listOf(legacyRule("leumi", "0521112233")))

        assertEquals(
            listOf(ResolvedTarget(Target(Channel.SMS, "0521112233"), 3L)),
            useCase("LEUMI", "code 1").targets,
        )
    }
}
