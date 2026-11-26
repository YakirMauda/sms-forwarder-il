package com.qrcommunication.smsforwarder.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.qrcommunication.smsforwarder.R
import com.qrcommunication.smsforwarder.domain.rules.ConditionGroup
import com.qrcommunication.smsforwarder.ui.common.SectionCard
import com.qrcommunication.smsforwarder.ui.common.SectionTitle
import com.qrcommunication.smsforwarder.ui.common.labelRes
import kotlinx.coroutines.launch

@Composable
fun RulesScreen(
    snackbar: SnackbarHostState,
    onEditRule: (Long) -> Unit,
    onNewRule: () -> Unit,
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val rules by viewModel.rules.collectAsState()
    val forwarding by viewModel.forwardingEnabled.collectAsState()
    val scope = rememberCoroutineScope()
    val deletedMsg = stringResource(R.string.rule_deleted)
    val undoLabel = stringResource(R.string.action_undo)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            SectionTitle(stringResource(R.string.forwarding_master_title))
                            Text(
                                stringResource(if (forwarding) R.string.forwarding_master_on else R.string.forwarding_master_off),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (forwarding) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }
                        Switch(checked = forwarding, onCheckedChange = viewModel::setForwardingEnabled)
                    }
                }
            }
            if (rules.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.rules_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(rules, key = { it.rule.id }) { item ->
                RuleCard(
                    item = item,
                    onToggle = { viewModel.setRuleEnabled(item.rule.id, it) },
                    onEdit = { onEditRule(item.rule.id) },
                    onDelete = {
                        viewModel.delete(item)
                        scope.launch {
                            val result = snackbar.showSnackbar(deletedMsg, actionLabel = undoLabel)
                            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
                        }
                    },
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = onNewRule,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.rules_add)) },
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
        )
    }
}

@Composable
private fun RuleCard(item: RuleItem, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val rule = item.rule
    SectionCard(Modifier.alpha(if (rule.enabled) 1f else 0.6f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(rule.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
        }
        Spacer(Modifier.height(6.dp))
        SummaryLine(stringResource(R.string.rule_sender), groupSummary(rule.sender, stringResource(R.string.rule_any_sender)))
        SummaryLine(stringResource(R.string.rule_content), groupSummary(rule.content, stringResource(R.string.rule_any_content)))
        SummaryLine(
            stringResource(R.string.rule_to),
            AnnotatedString(rule.targets.joinToString(", ") { "\u2066${it.address}\u2069" }),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: AnnotatedString) {
    Text(
        buildAnnotatedString {
            append("$label: ")
            append(value)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** "equals ‪Leumi‬ OR contains ‪Discount‬" — values are isolated so numbers stay LTR inside Hebrew. */
@Composable
private fun groupSummary(group: ConditionGroup, anyText: String): AnnotatedString {
    if (group.isEmpty) return AnnotatedString(anyText)
    val joiner = stringResource(group.logic.labelRes())
    val ops = group.conditions.map { stringResource(it.operator.labelRes()) }
    val accent = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        group.conditions.forEachIndexed { i, c ->
            if (i > 0) withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) { append(" $joiner ") }
            append(ops[i])
            append(" ")
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) {
                append("\u2068${c.value}\u2069")
            }
        }
    }
}
