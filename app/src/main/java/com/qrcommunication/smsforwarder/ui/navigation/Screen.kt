package com.qrcommunication.smsforwarder.ui.navigation

import com.qrcommunication.smsforwarder.ui.rules.RuleEditViewModel

object Routes {
    const val RULES = "rules"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val RULE_EDIT = "rule_edit/{${RuleEditViewModel.ARG_RULE_ID}}"

    /** id 0 = new rule. */
    fun ruleEdit(id: Long) = "rule_edit/$id"
}
