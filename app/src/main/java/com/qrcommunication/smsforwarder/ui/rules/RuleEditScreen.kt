package com.qrcommunication.smsforwarder.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.qrcommunication.smsforwarder.R
import com.qrcommunication.smsforwarder.domain.rules.Channel
import com.qrcommunication.smsforwarder.domain.rules.ConditionGroup
import com.qrcommunication.smsforwarder.domain.rules.GroupLogic
import com.qrcommunication.smsforwarder.domain.rules.MatchOperator
import com.qrcommunication.smsforwarder.domain.rules.RegexValidator
import com.qrcommunication.smsforwarder.ui.common.LtrText
import com.qrcommunication.smsforwarder.ui.common.SectionCard
import com.qrcommunication.smsforwarder.ui.common.SectionTitle
import com.qrcommunication.smsforwarder.ui.common.hintRes
import com.qrcommunication.smsforwarder.ui.common.labelRes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RuleEditScreen(onDone: () -> Unit, viewModel: RuleEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val defaultName = stringResource(R.string.rules_add)

    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.id == 0L) R.string.rules_add else R.string.rule_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
                // Save lives in the top bar so it never fights with the keyboard.
                actions = {
                    Button(onClick = { viewModel.save(defaultName) }, modifier = Modifier.padding(end = 8.dp)) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding() // keyboard insets counted once (activity uses adjustResize)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.rule_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.rule_both_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            GroupEditor(GroupKind.SENDER, state.sender, MatchOperator.SENDER, viewModel)
            Text(
                "— ${stringResource(R.string.logic_and)} —",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            GroupEditor(GroupKind.CONTENT, state.content, MatchOperator.CONTENT, viewModel)

            TargetsEditor(state, viewModel)

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.rule_enabled), modifier = Modifier.weight(1f))
                    Switch(checked = state.enabled, onCheckedChange = viewModel::setEnabled)
                }
            }

            TryItBox(state, viewModel, defaultName)

            state.error?.let {
                Text(
                    stringResource(
                        when (it) {
                            RuleEditError.NO_TARGET -> R.string.rule_error_no_target
                            RuleEditError.INVALID_REGEX -> R.string.rule_error_regex
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GroupEditor(kind: GroupKind, group: ConditionGroup, operators: List<MatchOperator>, vm: RuleEditViewModel) {
    SectionCard {
        SectionTitle(stringResource(if (kind == GroupKind.SENDER) R.string.group_sender else R.string.group_content))
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            GroupLogic.entries.forEachIndexed { i, logic ->
                SegmentedButton(
                    selected = group.logic == logic,
                    onClick = { vm.setLogic(kind, logic) },
                    shape = SegmentedButtonDefaults.itemShape(i, GroupLogic.entries.size),
                ) {
                    Text(stringResource(if (logic == GroupLogic.AND) R.string.logic_and_long else R.string.logic_or_long))
                }
            }
        }
        group.conditions.forEachIndexed { index, condition ->
            if (index > 0) {
                Text(
                    stringResource(group.logic.labelRes()),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                OperatorPicker(condition.operator, operators) { vm.setOperator(kind, index, it) }
                Spacer(Modifier.width(6.dp))
                val invalid = condition.operator == MatchOperator.REGEX &&
                    condition.value.isNotBlank() && !RegexValidator.isValid(condition.value)
                OutlinedTextField(
                    value = condition.value,
                    onValueChange = { vm.setValue(kind, index, it) },
                    placeholder = { Text(stringResource(R.string.condition_value)) },
                    isError = invalid,
                    supportingText = { if (invalid) Text(stringResource(R.string.regex_invalid)) },
                    textStyle = if (condition.operator == MatchOperator.REGEX) LtrText else MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.removeCondition(kind, index) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_remove))
                }
            }
        }
        if (group.conditions.isEmpty()) {
            Text(
                stringResource(if (kind == GroupKind.SENDER) R.string.group_sender_empty else R.string.group_content_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        TextButton(onClick = { vm.addCondition(kind) }) { Text(stringResource(R.string.condition_add)) }
    }
}

@Composable
private fun OperatorPicker(selected: MatchOperator, options: List<MatchOperator>, onSelect: (MatchOperator) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { open = true }) {
            Text(stringResource(selected.labelRes()), maxLines = 1)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { op ->
                DropdownMenuItem(text = { Text(stringResource(op.labelRes())) }, onClick = { onSelect(op); open = false })
            }
        }
    }
}

@Composable
private fun TargetsEditor(state: RuleEditState, vm: RuleEditViewModel) {
    SectionCard {
        SectionTitle(stringResource(R.string.targets_title))
        state.targets.forEachIndexed { index, target ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                ChannelPicker(target.channel) { vm.setTargetChannel(index, it) }
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(
                    value = target.address,
                    onValueChange = { vm.setTargetAddress(index, it) },
                    placeholder = { Text(stringResource(target.channel.hintRes()), style = LtrText) },
                    textStyle = LtrText,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (target.channel) {
                            Channel.SMS -> KeyboardType.Phone
                            Channel.WEBHOOK -> KeyboardType.Uri
                            Channel.TELEGRAM -> KeyboardType.Ascii // group chat ids start with "-"
                        },
                    ),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.removeTarget(index) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_remove))
                }
            }
            if (target.channel == Channel.TELEGRAM && !state.telegramConfigured) {
                Text(
                    stringResource(R.string.telegram_token_missing_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        TextButton(onClick = vm::addTarget) { Text(stringResource(R.string.target_add)) }
    }
}

@Composable
private fun ChannelPicker(selected: Channel, onSelect: (Channel) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { open = true }) {
            Text(stringResource(selected.labelRes()), maxLines = 1)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Channel.entries.forEach { ch ->
                DropdownMenuItem(text = { Text(stringResource(ch.labelRes())) }, onClick = { onSelect(ch); open = false })
            }
        }
    }
}

@Composable
private fun TryItBox(state: RuleEditState, vm: RuleEditViewModel, defaultName: String) {
    SectionCard {
        SectionTitle(stringResource(R.string.test_title))
        OutlinedTextField(
            value = state.testSender,
            onValueChange = vm::setTestSender,
            label = { Text(stringResource(R.string.rule_sender)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = state.testBody,
            onValueChange = vm::setTestBody,
            label = { Text(stringResource(R.string.test_body)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        // Recomputed on every edit because `state` changes.
        val result = vm.testMatches(defaultName)
        if (result != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(if (result) R.string.test_match else R.string.test_no_match),
                fontWeight = FontWeight.Bold,
                color = if (result) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
