package org.immortalwrt.manager.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.immortalwrt.manager.data.repository.PreferencesRepository
import org.immortalwrt.manager.data.repository.RouterRepository
import org.immortalwrt.manager.domain.model.RealtimeTraffic
import org.immortalwrt.manager.domain.model.RouterOverview

data class DashboardUiState(
    val isLoading: Boolean = true,
    val overview: RouterOverview? = null,
    val traffic: RealtimeTraffic? = null,
    val errorMessage: String? = null
)

class DashboardViewModel(
    private val routerRepository: RouterRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var currentHost: String = "10.10.10.1"

    init {
        viewModelScope.launch {
            preferencesRepository.credentialsFlow.collect { creds ->
                currentHost = creds.host
                startPolling()
            }
        }
    }

    fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(1500)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
    }

    fun refresh() {
        viewModelScope.launch {
            val overviewResult = routerRepository.getRouterOverview(currentHost)
            val trafficResult = routerRepository.getRealtimeTraffic()

            if (overviewResult.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    overview = overviewResult.getOrNull(),
                    traffic = trafficResult.getOrNull(),
                    errorMessage = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = overviewResult.exceptionOrNull()?.message
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
