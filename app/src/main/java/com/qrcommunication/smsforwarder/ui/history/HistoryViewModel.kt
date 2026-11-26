package com.qrcommunication.smsforwarder.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qrcommunication.smsforwarder.data.local.entity.SmsRecord
import com.qrcommunication.smsforwarder.data.local.entity.SmsStatus
import com.qrcommunication.smsforwarder.data.repository.ForwardingRuleRepository
import com.qrcommunication.smsforwarder.data.repository.SmsRepository
import com.qrcommunication.smsforwarder.service.SmsRetryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/** Pure, unit-tested numbers for the stats tiles. */
data class HistoryStats(val today: Int, val month: Int, val successRatePercent: Int?, val failed: Int) {
    companion object {
        fun from(records: List<SmsRecord>, now: Long): HistoryStats {
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val dayStart = cal.timeInMillis
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val monthStart = cal.timeInMillis
            val sent = records.filter { it.status == SmsStatus.SENT.value }
            val failed = records.count { it.status == SmsStatus.FAILED.value }
            val done = sent.size + failed
            return HistoryStats(
                today = sent.count { it.receivedAt >= dayStart },
                month = sent.count { it.receivedAt >= monthStart },
                successRatePercent = if (done == 0) null else sent.size * 100 / done,
                failed = failed,
            )
        }
    }
}

data class HistoryState(
    val records: List<SmsRecord> = emptyList(),
    val ruleNames: Map<Long, String> = emptyMap(),
    val stats: HistoryStats = HistoryStats(0, 0, null, 0),
)

sealed class RetryEvent {
    data object Resent : RetryEvent()
    data class Failed(val error: String) : RetryEvent()
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val smsRepository: SmsRepository,
    ruleRepository: ForwardingRuleRepository,
    private val retryManager: SmsRetryManager,
) : ViewModel() {

    val state: StateFlow<HistoryState> = combine(smsRepository.getAllRecords(), ruleRepository.observeAll()) { records, rules ->
        HistoryState(
            records = records.sortedByDescending { it.receivedAt },
            ruleNames = rules.associate { it.id to it.name },
            stats = HistoryStats.from(records, System.currentTimeMillis()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    private val _events = MutableSharedFlow<RetryEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<RetryEvent> = _events

    fun retry(record: SmsRecord) = viewModelScope.launch {
        val ok = retryManager.retryNow(record)
        val updated = smsRepository.getRecordById(record.id)
        _events.emit(if (ok) RetryEvent.Resent else RetryEvent.Failed(updated?.errorMessage.orEmpty()))
    }
}
