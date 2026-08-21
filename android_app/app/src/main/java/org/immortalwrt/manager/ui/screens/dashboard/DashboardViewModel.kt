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
    val isRefreshing: Boolean = false,
    val isOperating: Boolean = false,
    val overview: RouterOverview? = null,
    val traffic: RealtimeTraffic? = null,
    val rxHistory: List<Long> = emptyList(),
    val txHistory: List<Long> = emptyList(),
    val errorMessage: String? = null,
    val toastMessage: String? = null
)

class DashboardViewModel(
    private val routerRepository: RouterRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var currentHost: String = "10.10.10.1"
    private val rxRingBuffer = ArrayDeque<Long>(30)
    private val txRingBuffer = ArrayDeque<Long>(30)

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
                pollMetrics()
                delay(1200)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            pollMetrics()
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    private suspend fun pollMetrics() {
        val overviewResult = routerRepository.getRouterOverview(currentHost)
        val trafficResult = routerRepository.getRealtimeTraffic()

        val traffic = trafficResult.getOrNull()
        if (traffic != null) {
            if (rxRingBuffer.size >= 30) rxRingBuffer.removeFirst()
            rxRingBuffer.addLast(traffic.downloadSpeedBps)

            if (txRingBuffer.size >= 30) txRingBuffer.removeFirst()
            txRingBuffer.addLast(traffic.uploadSpeedBps)
        }

        if (overviewResult.isSuccess) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                overview = overviewResult.getOrNull(),
                traffic = traffic,
                rxHistory = rxRingBuffer.toList(),
                txHistory = txRingBuffer.toList(),
                errorMessage = null
            )
        } else if (_uiState.value.overview == null) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = overviewResult.exceptionOrNull()?.message
            )
        }
    }

    fun dropCaches() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true)
            val res = routerRepository.dropCaches()
            _uiState.value = _uiState.value.copy(
                isOperating = false,
                toastMessage = if (res.isSuccess) "内存与系统缓存已成功释放" else "释放缓存失败"
            )
        }
    }

    fun rebootRouter() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true)
            val res = routerRepository.rebootRouter()
            _uiState.value = _uiState.value.copy(
                isOperating = false,
                toastMessage = if (res.isSuccess) "路由器重启指令已下发" else "重启失败"
            )
        }
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
