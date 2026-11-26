package com.qrcommunication.smsforwarder.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real-life scenarios, written the way a user who doesn't know the app would try things.
 * Numbering matches docs/SCENARIOS.md. Pure JVM: no Android needed.
 */
class UserScenariosTest {

    private val engine = RuleEvaluator()
    private val me = Target(Channel.SMS, "052-1234567")
    private val dad = "+972541112233"

    private fun cond(op: MatchOperator, v: String) = Condition(op, v)
    private fun or(vararg c: Condition) = ConditionGroup(GroupLogic.OR, c.toList())
    private fun and(vararg c: Condition) = ConditionGroup(GroupLogic.AND, c.toList())
    private fun rule(
        sender: ConditionGroup = ConditionGroup.MATCH_ALL,
        content: ConditionGroup = ConditionGroup.MATCH_ALL,
        targets: List<Target> = listOf(me),
        id: Long = 1,
        enabled: Boolean = true,
        priority: Int = 0,
    ) = Rule(id, "r$id", enabled, priority, sender, content, targets)

    private fun sends(rules: List<Rule>, from: String, body: String) = engine.resolve(rules, from, body)

    // ---------- senders: numbers ----------

    @Test fun s01_numberTypedWithDashes_matchesInternationalSender() {
        val r = rule(sender = or(cond(MatchOperator.EQUALS, "054-111-2233")))
        assertTrue(engine.matches(r, dad, "x"))
    }

    @Test fun s02_numberTypedWithCountryCodeNoPlus_matchesLocalSender() {
        val r = rule(sender = or(cond(MatchOperator.EQUALS, "972541112233")))
        assertTrue(engine.matches(r, "0541112233", "x"))
    }

    @Test fun s03_numberTypedWithSpacesAndParens_matches() {
        val r = rule(sender = or(cond(MatchOperator.EQUALS, "(054) 111 22 33")))
        assertTrue(engine.matches(r, dad, "x"))
    }

    @Test fun s04_partOfNumber_contains_matches() {
        val r = rule(sender = or(cond(MatchOperator.CONTAINS, "1112233")))
        assertTrue(engine.matches(r, dad, "x"))
    }

    @Test fun s05_prefix05_startsWith_matchesAnyIsraeliMobile() {
        val r = rule(sender = or(cond(MatchOperator.STARTS_WITH, "05")))
        assertTrue(engine.matches(r, dad, "x"))
        assertFalse(engine.matches(r, "+97231234567", "x")) // landline 03
    }

    @Test fun s06_foreignNumberWith00_matchesPlusForm() {
        val r = rule(sender = or(cond(MatchOperator.EQUALS, "0012025550123")))
        assertTrue(engine.matches(r, "+1 202-555-0123", "x"))
    }

    @Test fun s07_shortCodeSender_matches() {
        val r = rule(sender = or(cond(MatchOperator.EQUALS, "8888")))
        assertTrue(engine.matches(r, "8888", "x"))
    }

    @Test fun s08_wrongNumberByOneDigit_doesNotMatch() {
        val r = rule(sender = or(cond(MatchOperator.EQUALS, "0541112234")))
        assertFalse(engine.matches(r, dad, "x"))
    }

    // ---------- senders: names ----------

    @Test fun s09_contactName_neverMatches_becauseSmsCarriesOnlyTheNumber() {
        // The user typed the name dad saved him as. The SMS arrives from the number.
        val r = rule(sender = or(cond(MatchOperator.EQUALS, "יקיר הבן")))
        assertFalse(engine.matches(r, "+972521234567", "x"))
        // ...and the editor explains why (sender, not content).
        assertEquals(MatchExplanation(senderMatches = false, contentMatches = true), engine.explain(r, "+972521234567", "x"))
    }

    @Test fun s10_companyName_anyCase_matches() {
        val r = rule(sender = or(cond(MatchOperator.EQUALS, "leumi")))
        assertTrue(engine.matches(r, "LEUMI", "x"))
    }

    @Test fun s11_hebrewCompanyName_matches() {
        val r = rule(sender = or(cond(MatchOperator.STARTS_WITH, "כללית")))
        assertTrue(engine.matches(r, "כללית מושלם", "x"))
    }

    @Test fun s12_trailingSpaceFromKeyboard_isIgnored() {
        val r = rule(sender = or(cond(MatchOperator.CONTAINS, "Leumi ")), content = or(cond(MatchOperator.CONTAINS, " קוד ")))
        assertTrue(engine.matches(r, "Leumi", "הקוד שלך: 1234"))
    }

    @Test fun s13_alphanumericSenderWithDigits_isText() {
        val r = rule(sender = or(cond(MatchOperator.EQUALS, "Bank123")))
        assertTrue(engine.matches(r, "BANK123", "x"))
    }

    // ---------- content ----------

    @Test fun s14_otpInHebrew_containsKod_matches() {
        val r = rule(content = or(cond(MatchOperator.CONTAINS, "קוד")))
        assertTrue(engine.matches(r, "x", "קוד האימות שלך הוא 482913"))
    }

    @Test fun s15_promoWithCode_excludedByNotContains() {
        val r = rule(content = and(cond(MatchOperator.CONTAINS, "קוד"), cond(MatchOperator.NOT_CONTAINS, "פרסומת")))
        assertFalse(engine.matches(r, "x", "פרסומת: קוד הטבה 1234"))
        assertTrue(engine.matches(r, "x", "קוד אימות 1234"))
    }

    @Test fun s16_englishOtp_caseInsensitive() {
        val r = rule(content = or(cond(MatchOperator.CONTAINS, "code")))
        assertTrue(engine.matches(r, "x", "Your verification CODE is 7781"))
    }

    @Test fun s17_sixDigitRegex_matchesOnlySixDigitCodes() {
        val r = rule(content = or(cond(MatchOperator.REGEX, "\\b\\d{6}\\b")))
        assertTrue(engine.matches(r, "x", "קוד: 482913"))
        assertFalse(engine.matches(r, "x", "קוד: 4829"))
    }

    @Test fun s18_brokenRegex_neverMatches_andIsFlagged() {
        assertFalse(RegexValidator.isValid("(\\d{6}"))
        val r = rule(content = or(cond(MatchOperator.REGEX, "(\\d{6}")))
        assertFalse(engine.matches(r, "x", "(123456"))
    }

    @Test fun s19_orGroup_anyKeywordIsEnough() {
        val r = rule(content = or(cond(MatchOperator.CONTAINS, "קוד"), cond(MatchOperator.CONTAINS, "code"), cond(MatchOperator.CONTAINS, "סיסמה")))
        assertTrue(engine.matches(r, "x", "הסיסמה החד פעמית: 99"))
    }

    @Test fun s20_andGroup_conflictingConditions_neverMatch_explained() {
        val r = rule(content = and(cond(MatchOperator.CONTAINS, "קוד"), cond(MatchOperator.NOT_CONTAINS, "קוד")))
        assertFalse(engine.explain(r, "x", "קוד 1").contentMatches)
    }

    @Test fun s21_multipartLongMessage_isMatchedAsOneText() {
        val longBody = "שלום, ".repeat(30) + "קוד האימות: 123456"
        val r = rule(content = or(cond(MatchOperator.CONTAINS, "123456")))
        assertTrue(engine.matches(r, "x", longBody))
    }

    @Test fun s22_emojiAndNewlines_doNotBreakMatching() {
        val r = rule(content = or(cond(MatchOperator.CONTAINS, "קוד")))
        assertTrue(engine.matches(r, "x", "🔐\nקוד: 1234\n🙂"))
    }

    // ---------- loop guard (the "why didn't my test work?" family) ----------

    @Test fun s23_testFromMyOwnNumber_toRuleTargetingMe_isLoopSkipped() {
        // Exactly the owner's test: he texted dad's phone, and the rule forwards to him.
        val result = sends(listOf(rule()), "+972521234567", "קוד 1234")
        assertTrue(result.targets.isEmpty())
        assertEquals(listOf(me), result.loopSkipped.map { it.target })
    }

    @Test fun s24_loopSkip_onlyDropsTheSender_othersStillGetIt() {
        val mom = Target(Channel.SMS, "053-9998877")
        val result = sends(listOf(rule(targets = listOf(me, mom))), "0521234567", "x")
        assertEquals(listOf(mom), result.targets.map { it.target })
    }

    @Test fun s25_twoPhonesForwardingEverythingToEachOther_stopAfterOneHop() {
        // Dad's phone forwards to me; my phone forwards everything to dad. The copy coming
        // back from dad is skipped on my phone because the target (dad) IS the sender.
        val myRules = listOf(rule(targets = listOf(Target(Channel.SMS, dad))))
        assertTrue(sends(myRules, dad, "forwarded text").targets.isEmpty())
    }

    @Test fun s26_webhookTarget_isNeverLoopSkipped() {
        val hook = Target(Channel.WEBHOOK, "https://example.com/h")
        assertEquals(listOf(hook), sends(listOf(rule(targets = listOf(hook))), "0521234567", "x").targets.map { it.target })
    }

    // ---------- several rules ----------

    @Test fun s27_twoRulesSameTarget_sentOnce() {
        val rules = listOf(rule(id = 1), rule(id = 2, targets = listOf(Target(Channel.SMS, "+972521234567"))))
        assertEquals(1, sends(rules, "Leumi", "קוד").targets.size)
    }

    @Test fun s28_disabledRule_isIgnoredEvenIfItMatches() {
        assertTrue(sends(listOf(rule(enabled = false)), "Leumi", "קוד").targets.isEmpty())
    }

    @Test fun s29_higherPriorityRuleIsCreditedForSharedTarget() {
        val rules = listOf(rule(id = 1, priority = 0), rule(id = 2, priority = 9))
        assertEquals(2L, sends(rules, "Leumi", "קוד").targets.single().ruleId)
    }

    @Test fun s30_ruleWithNoConditions_matchesEverything() {
        // The editor shows a red "forwards every SMS" warning for this.
        assertTrue(engine.matches(rule(), "anyone", "private message"))
    }

    @Test fun s31_blankTargetsAreIgnored() {
        val result = sends(listOf(rule(targets = listOf(Target(Channel.SMS, "  "), me))), "Leumi", "x")
        assertEquals(listOf(me), result.targets.map { it.target })
    }

    // ---------- targets (validation before saving) ----------

    @Test fun s32_targetValidation() {
        assertTrue(TargetValidator.isValid(Target(Channel.SMS, "052-123 45 67")))
        assertTrue(TargetValidator.isValid(Target(Channel.SMS, "+972521234567")))
        assertFalse(TargetValidator.isValid(Target(Channel.SMS, "אבא")))       // a name, not a number
        assertFalse(TargetValidator.isValid(Target(Channel.SMS, "12")))        // too short
        assertTrue(TargetValidator.isValid(Target(Channel.TELEGRAM, "-1001234567890")))
        assertTrue(TargetValidator.isValid(Target(Channel.TELEGRAM, "@my_channel")))
        assertFalse(TargetValidator.isValid(Target(Channel.TELEGRAM, "my chat")))
        assertTrue(TargetValidator.isValid(Target(Channel.WEBHOOK, "https://example.com/hook")))
        assertFalse(TargetValidator.isValid(Target(Channel.WEBHOOK, "example.com/hook"))) // no https://
    }

    @Test fun s33_smsTargetIsCleanedBeforeSending() {
        assertEquals("0521234567", TargetValidator.cleanSmsNumber(" 052-123 45 67 "))
        assertEquals("+972521234567", TargetValidator.cleanSmsNumber("+972 (52) 123-4567"))
    }

    // ---------- storage robustness ----------

    @Test fun s34_rulesSurviveRoundTripThroughStorage_withHebrewAndRegex() {
        val g = or(cond(MatchOperator.CONTAINS, "קוד \"אימות\""), cond(MatchOperator.REGEX, "\\d{4,8}"))
        assertEquals(g, RuleJson.decodeGroup(RuleJson.encodeGroup(g)))
    }

    @Test fun s35_corruptStoredRule_doesNotCrash() {
        assertEquals(null, RuleJson.decodeGroup("{\"logic\":"))
        assertEquals(null, RuleJson.decodeTargets("[{]"))
    }
}
