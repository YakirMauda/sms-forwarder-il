package com.qrcommunication.smsforwarder.ui.common

import androidx.annotation.StringRes
import com.qrcommunication.smsforwarder.R
import com.qrcommunication.smsforwarder.domain.rules.Channel
import com.qrcommunication.smsforwarder.domain.rules.GroupLogic
import com.qrcommunication.smsforwarder.domain.rules.MatchOperator

@StringRes
fun MatchOperator.labelRes(): Int = when (this) {
    MatchOperator.EQUALS -> R.string.op_equals
    MatchOperator.CONTAINS -> R.string.op_contains
    MatchOperator.NOT_CONTAINS -> R.string.op_not_contains
    MatchOperator.STARTS_WITH -> R.string.op_starts_with
    MatchOperator.ENDS_WITH -> R.string.op_ends_with
    MatchOperator.EXACT -> R.string.op_exact
    MatchOperator.REGEX -> R.string.op_regex
}

@StringRes
fun GroupLogic.labelRes(): Int = when (this) {
    GroupLogic.AND -> R.string.logic_and
    GroupLogic.OR -> R.string.logic_or
}

@StringRes
fun Channel.labelRes(): Int = when (this) {
    Channel.SMS -> R.string.channel_sms
    Channel.TELEGRAM -> R.string.channel_telegram
    Channel.WEBHOOK -> R.string.channel_webhook
}

@StringRes
fun Channel.hintRes(): Int = when (this) {
    Channel.SMS -> R.string.target_hint_sms
    Channel.TELEGRAM -> R.string.target_hint_telegram
    Channel.WEBHOOK -> R.string.target_hint_webhook
}
