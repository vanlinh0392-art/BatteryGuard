package com.pin.batteryguard.ui.screen.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pin.batteryguard.data.db.entity.ForceStopLog
import com.pin.batteryguard.data.repository.BatteryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class HistoryUiState(
    val logs: Map<String, List<ForceStopLog>> = emptyMap(),
    val totalStopped: Int = 0,
    val totalSavedPercent: Float = 0f,
    val isLoading: Boolean = true
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val batteryRepository: BatteryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            batteryRepository.getAllForceStopLogs().collectLatest { list ->
                val grouped = groupLogsByDate(list)
                val totalStopped = list.size
                val actualSum = list.filter { it.success || it.verified }.sumOf {
                    if (it.drainPercent > 0f) it.drainPercent.toDouble()
                    else if (it.ratePercentPerHour > 0.0) it.ratePercentPerHour
                    else 0.5
                }.toFloat()

                val totalSaved = if (actualSum > 0f) actualSum else totalStopped * 0.5f

                _uiState.update {
                    it.copy(
                        logs = grouped,
                        totalStopped = totalStopped,
                        totalSavedPercent = totalSaved,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun groupLogsByDate(logs: List<ForceStopLog>): Map<String, List<ForceStopLog>> {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val today = sdf.format(Date())
        val yesterday = sdf.format(Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000L))

        return logs.groupBy { log ->
            val dateStr = sdf.format(Date(log.timestamp))
            when (dateStr) {
                today -> "Hôm nay"
                yesterday -> "Hôm qua"
                else -> dateStr
            }
        }
    }
}
