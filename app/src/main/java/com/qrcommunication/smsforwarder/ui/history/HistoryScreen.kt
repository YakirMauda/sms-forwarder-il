package com.qrcommunication.smsforwarder.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.qrcommunication.smsforwarder.R
import com.qrcommunication.smsforwarder.data.local.entity.SmsRecord
import com.qrcommunication.smsforwarder.data.local.entity.SmsStatus
import com.qrcommunication.smsforwarder.service.ForwardingProcessor
import com.qrcommunication.smsforwarder.ui.common.LtrText
import com.qrcommunication.smsforwarder.ui.common.Pill
import com.qrcommunication.smsforwarder.ui.common.SectionCard
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(snackbar: SnackbarHostState, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val msg = when (event) {
                RetryEvent.Resent -> context.getString(R.string.retry_sent)
                is RetryEvent.Failed -> context.getString(R.string.retry_failed, event.error)
            }
            snackbar.showSnackbar(msg)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { StatsGrid(state.stats) }
        if (state.records.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.history_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        items(state.records, key = { it.id }) { record ->
            RecordCard(
                record = record,
                ruleName = record.ruleId?.let { state.ruleNames[it] },
                onRetry = { viewModel.retry(record) },
            )
        }
    }
}

@Composable
private fun StatsGrid(stats: HistoryStats) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stats.today.toString(), stringResource(R.string.stat_today), Modifier.weight(1f))
            StatTile(stats.month.toString(), stringResource(R.string.stat_month), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                stats.successRatePercent?.let { "$it%" } ?: "—",
                stringResource(R.string.stat_rate),
                Modifier.weight(1f),
                progress = stats.successRatePercent?.div(100f),
            )
            StatTile(
                stats.failed.toString(),
                stringResource(R.string.stat_failed),
                Modifier.weight(1f),
                valueColor = if (stats.failed > 0) MaterialTheme.colorScheme.error else null,
            )
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier, progress: Float? = null, valueColor: Color? = null) {
    SectionCard(modifier) {
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (progress != null) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.errorContainer,
            )
        }
    }
}

@Composable
private fun RecordCard(record: SmsRecord, ruleName: String?, onRetry: () -> Unit) {
    val (label, container, content) = statusStyle(record)
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(record.sender, style = LtrText, fontWeight = FontWeight.Bold)
            Text("  ←  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(record.destination, style = LtrText, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            Pill(label, container, content)
        }
        Text(
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(record.receivedAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(record.content, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 4.dp))
        Text(
            stringResource(
                R.string.history_rule,
                when {
                    ruleName != null -> ruleName
                    record.ruleId != null -> stringResource(R.string.history_deleted_rule) // rule removed since
                    else -> stringResource(R.string.history_default_rule)
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (record.status == SmsStatus.FILTERED.value && record.errorMessage == ForwardingProcessor.REASON_LOOP) {
            Text(
                stringResource(R.string.history_reason_loop),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (record.status == SmsStatus.FAILED.value) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text(
                    record.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }
    }
}

@Composable
private fun statusStyle(record: SmsRecord): Triple<String, Color, Color> {
    val c = MaterialTheme.colorScheme
    return when {
        record.status == SmsStatus.FAILED.value -> Triple(stringResource(R.string.status_failed), c.errorContainer, c.onErrorContainer)
        record.status == SmsStatus.SENT.value && record.deliveredAt != null ->
            Triple(stringResource(R.string.status_delivered), c.primaryContainer, c.onPrimaryContainer)
        record.status == SmsStatus.SENT.value -> Triple(stringResource(R.string.status_sent), c.secondaryContainer, c.onSecondaryContainer)
        record.status == SmsStatus.FILTERED.value -> Triple(stringResource(R.string.status_filtered), c.surfaceVariant, c.onSurfaceVariant)
        else -> Triple(stringResource(R.string.status_pending), c.surfaceVariant, c.onSurfaceVariant)
    }
}
