package org.immortalwrt.manager.ui.screens.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.immortalwrt.manager.data.repository.RouterRepository
import org.immortalwrt.manager.domain.model.*

enum class DiagnosticMode {
    PING,
    NSLOOKUP,
    TRACEROUTE
}

data class ToolsUiState(
    val isOperating: Boolean = false,
    val isLoadingPlugins: Boolean = true,
    val isLoadingLogs: Boolean = false,
    val plugins: List<PluginServiceInfo> = emptyList(),
    val selectedPluginCategory: PluginCategory? = null,
    val activeConfigPlugin: PluginServiceInfo? = null,
    val passwallConfig: PasswallConfig? = null,
    val openclashConfig: OpenClashConfig? = null,
    val mosdnsConfig: MosdnsConfig? = null,
    val logs: List<LogEntry> = emptyList(),
    val logFilterLevel: String = "ALL",
    val logSearchQuery: String = "",
    val diagTarget: String = "223.5.5.5",
    val diagMode: DiagnosticMode = DiagnosticMode.PING,
    val isDiagnosing: Boolean = false,
    val diagResult: String? = null,
    val toastMessage: String? = null,
    val errorMessage: String? = null
) {
    val filteredPlugins: List<PluginServiceInfo>
        get() = if (selectedPluginCategory == null) plugins else plugins.filter { it.category == selectedPluginCategory }

    val filteredLogs: List<LogEntry>
        get() = logs.filter { entry ->
            val matchLevel = when (logFilterLevel) {
                "ALL" -> true
                "ERR" -> entry.level.contains("err", ignoreCase = true)
                "WARN" -> entry.level.contains("warn", ignoreCase = true) || entry.level.contains("err", ignoreCase = true)
                else -> true
            }
            val matchSearch = logSearchQuery.isBlank() || entry.message.contains(logSearchQuery, ignoreCase = true)
            matchLevel && matchSearch
        }
}

class ToolsViewModel(
    private val routerRepository: RouterRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()

    init {
        loadPlugins()
        loadLogs()
    }

    fun loadPlugins() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingPlugins = true)
            val res = routerRepository.getPluginServices()
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoadingPlugins = false,
                    plugins = res.getOrNull() ?: emptyList()
                )
            } else {
                _uiState.value = _uiState.value.copy(isLoadingPlugins = false)
            }
        }
    }

    fun selectPluginCategory(cat: PluginCategory?) {
        _uiState.value = _uiState.value.copy(selectedPluginCategory = cat)
    }

    fun controlPlugin(serviceName: String, action: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true)
            val res = routerRepository.controlPluginService(serviceName, action)
            _uiState.value = _uiState.value.copy(
                isOperating = false,
                toastMessage = if (res.isSuccess) "服务 $serviceName 指令 [$action] 已下发" else "指令下发失败"
            )
            loadPlugins()
        }
    }

    fun openPluginConfig(plugin: PluginServiceInfo) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true, activeConfigPlugin = plugin)
            when (plugin.id) {
                "passwall" -> {
                    val cfg = routerRepository.getPasswallConfig().getOrNull()
                    _uiState.value = _uiState.value.copy(isOperating = false, passwallConfig = cfg)
                }
                "openclash" -> {
                    val cfg = routerRepository.getOpenClashConfig().getOrNull()
                    _uiState.value = _uiState.value.copy(isOperating = false, openclashConfig = cfg)
                }
                "mosdns" -> {
                    val cfg = routerRepository.getMosdnsConfig().getOrNull()
                    _uiState.value = _uiState.value.copy(isOperating = false, mosdnsConfig = cfg)
                }
                else -> {
                    _uiState.value = _uiState.value.copy(isOperating = false)
                }
            }
        }
    }

    fun closePluginConfig() {
        _uiState.value = _uiState.value.copy(activeConfigPlugin = null)
    }

    fun savePasswallConfig(config: PasswallConfig) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true)
            val res = routerRepository.updatePasswallConfig(config)
            _uiState.value = _uiState.value.copy(
                isOperating = false,
                activeConfigPlugin = null,
                toastMessage = if (res.isSuccess) "PassWall 配置已更新并重启服务" else "保存失败"
            )
            loadPlugins()
        }
    }

    fun saveOpenClashConfig(config: OpenClashConfig) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true)
            val res = routerRepository.updateOpenClashConfig(config)
            _uiState.value = _uiState.value.copy(
                isOperating = false,
                activeConfigPlugin = null,
                toastMessage = if (res.isSuccess) "OpenClash 配置已更新并重启服务" else "保存失败"
            )
            loadPlugins()
        }
    }

    fun saveMosdnsConfig(config: MosdnsConfig) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true)
            val res = routerRepository.updateMosdnsConfig(config)
            _uiState.value = _uiState.value.copy(
                isOperating = false,
                activeConfigPlugin = null,
                toastMessage = if (res.isSuccess) "MosDNS 配置已更新并重启服务" else "保存失败"
            )
            loadPlugins()
        }
    }

    fun loadLogs() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingLogs = true)
            val res = routerRepository.getSystemLogs()
            _uiState.value = _uiState.value.copy(
                isLoadingLogs = false,
                logs = res.getOrNull() ?: emptyList()
            )
        }
    }

    fun onLogFilterLevelChange(lvl: String) {
        _uiState.value = _uiState.value.copy(logFilterLevel = lvl)
    }

    fun onLogSearchChange(query: String) {
        _uiState.value = _uiState.value.copy(logSearchQuery = query)
    }

    fun onDiagTargetChange(target: String) {
        _uiState.value = _uiState.value.copy(diagTarget = target)
    }

    fun onDiagModeChange(mode: DiagnosticMode) {
        _uiState.value = _uiState.value.copy(diagMode = mode)
    }

    fun runDiagnostics() {
        val target = _uiState.value.diagTarget.trim()
        if (target.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDiagnosing = true, diagResult = "正在执行网络诊断，请稍候...")
            val result = when (_uiState.value.diagMode) {
                DiagnosticMode.PING -> routerRepository.runPing(target)
                DiagnosticMode.NSLOOKUP -> routerRepository.runNslookup(target)
                DiagnosticMode.TRACEROUTE -> routerRepository.runTraceroute(target)
            }
            _uiState.value = _uiState.value.copy(
                isDiagnosing = false,
                diagResult = result.getOrNull() ?: "执行失败: ${result.exceptionOrNull()?.message}"
            )
        }
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }
}

