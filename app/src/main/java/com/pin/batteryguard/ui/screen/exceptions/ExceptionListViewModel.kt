package com.pin.batteryguard.ui.screen.exceptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pin.batteryguard.data.db.entity.ExceptionApp
import com.pin.batteryguard.data.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExceptionListUiState(
    val exceptions: List<ExceptionApp> = emptyList(),
    val showAddDialog: Boolean = false,
    val availableApps: List<Pair<String, String>> = emptyList(), // packageName -> appName
    val filteredAvailableApps: List<Pair<String, String>> = emptyList(),
    val searchQuery: String = ""
)

@HiltViewModel
class ExceptionListViewModel @Inject constructor(
    private val appRepository: AppRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExceptionListUiState())
    val uiState: StateFlow<ExceptionListUiState> = _uiState.asStateFlow()

    init {
        loadExceptions()
        loadAvailableApps()
    }

    private fun loadExceptions() {
        viewModelScope.launch {
            appRepository.getExceptions().collectLatest { list ->
                _uiState.update { it.copy(exceptions = list) }
            }
        }
    }

    private fun loadAvailableApps() {
        viewModelScope.launch {
            val allApps = appRepository.getInstalledUserApps()
            _uiState.update {
                it.copy(
                    availableApps = allApps,
                    filteredAvailableApps = allApps
                )
            }
        }
    }

    fun showAddDialog(show: Boolean) {
        _uiState.update { it.copy(showAddDialog = show, searchQuery = "") }
        if (show) {
            filterAvailableApps("")
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        filterAvailableApps(query)
    }

    private fun filterAvailableApps(query: String) {
        val state = _uiState.value
        val currentExceptions = state.exceptions.map { it.packageName }.toSet()
        
        var list = state.availableApps.filter { (pkg, _) -> !currentExceptions.contains(pkg) }
        if (query.isNotEmpty()) {
            list = list.filter { (_, name) -> name.contains(query, ignoreCase = true) }
        }
        
        _uiState.update { it.copy(filteredAvailableApps = list) }
    }

    fun addException(packageName: String, appName: String, reason: String) {
        viewModelScope.launch {
            appRepository.addException(packageName, appName, reason)
            showAddDialog(false)
        }
    }

    fun removeException(packageName: String) {
        viewModelScope.launch {
            appRepository.removeException(packageName)
        }
    }
}
