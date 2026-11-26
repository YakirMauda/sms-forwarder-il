package com.qrcommunication.smsforwarder.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SwitchDefaults
import com.qrcommunication.smsforwarder.ui.common.ConditionTag
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.qrcommunication.smsforwarder.R
import com.qrcommunication.smsforwarder.domain.rules.ConditionGroup
import com.qrcommunication.smsforwarder.ui.common.SectionCard
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
                SectionCard(
                    container = if (forwarding) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                ) {
                    val onCard = if (forwarding) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.forwarding_master_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = onCard,
                            )
                            Text(
                                stringResource(if (forwarding) R.string.forwarding_master_on else R.string.forwarding_master_off),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (forwarding) onCard.copy(alpha = 0.85f) else MaterialTheme.colorScheme.error,
                            )
                        }
                        Switch(
                            checked = forwarding,
                            onCheckedChange = viewModel::setForwardingEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.onPrimary,
                                checkedBorderColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
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
    SectionCard(Modifier.alpha(if (rule.enabled) 1f else 0.62f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(36.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Sms, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Text(rule.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GroupLine(stringResource(R.string.rule_sender), rule.sender, stringResource(R.string.rule_any_sender))
            GroupLine(stringResource(R.string.rule_content), rule.content, stringResource(R.string.rule_any_content))
            LabeledRow(stringResource(R.string.rule_to)) {
                Text(
                    rule.targets.joinToString(", ") { "\u2066${it.address}\u2069" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        HorizontalDivider(Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.surfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit), fontWeight = FontWeight.SemiBold) }
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabeledRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp).padding(top = 2.dp),
        )
        Box(Modifier.weight(1f)) { content() }
    }
}

/** Conditions as tags joined by the group's AND/OR word. Values are isolated so numbers stay LTR in Hebrew. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupLine(label: String, group: ConditionGroup, anyText: String) {
    LabeledRow(label) {
        if (group.isEmpty) {
            Text(anyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val joiner = stringResource(group.logic.labelRes())
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                group.conditions.forEachIndexed { i, c ->
                    if (i > 0) {
                        Text(
                            joiner,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                    ConditionTag(stringResource(c.operator.labelRes()), "\u2068${c.value}\u2069")
                }
            }
        }
    }
}
