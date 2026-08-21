package org.immortalwrt.manager.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.immortalwrt.manager.data.repository.PreferencesRepository
import org.immortalwrt.manager.data.repository.RouterRepository
import org.immortalwrt.manager.domain.model.FirewallRedirectRule
import org.immortalwrt.manager.domain.model.RouterCredentials
import org.immortalwrt.manager.domain.model.RouterNode

data class SettingsUiState(
    val currentCredentials: RouterCredentials = RouterCredentials(),
    val savedNodes: List<RouterNode> = emptyList(),
    val portForwardRules: List<FirewallRedirectRule> = emptyList(),
    val themeMode: Int = 0, // 0: Auto, 1: Light, 2: Dark
    val dynamicColor: Boolean = true,
    val isOperating: Boolean = false,
    val isLoadingRules: Boolean = false,
    val showAddNodeDialog: Boolean = false,
    val showAddPortForwardDialog: Boolean = false,
    val toastMessage: String? = null,
    val loggedOut: Boolean = false
)

class SettingsViewModel(
    private val routerRepository: RouterRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.credentialsFlow.collect { creds ->
                _uiState.value = _uiState.value.copy(currentCredentials = creds)
            }
        }
        viewModelScope.launch {
            preferencesRepository.savedNodesFlow.collect { nodes ->
                _uiState.value = _uiState.value.copy(savedNodes = nodes)
            }
        }
        viewModelScope.launch {
            preferencesRepository.themeModeFlow.collect { mode ->
                _uiState.value = _uiState.value.copy(themeMode = mode)
            }
        }
        viewModelScope.launch {
            preferencesRepository.dynamicColorFlow.collect { dyn ->
                _uiState.value = _uiState.value.copy(dynamicColor = dyn)
            }
        }
        loadPortForwardRules()
    }

    fun loadPortForwardRules() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingRules = true)
            val res = routerRepository.getPortForwardRules()
            _uiState.value = _uiState.value.copy(
                isLoadingRules = false,
                portForwardRules = res.getOrNull() ?: emptyList()
            )
        }
    }

    fun addPortForwardRule(name: String, proto: String, srcPort: String, destIp: String, destPort: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true)
            val newRule = FirewallRedirectRule(
                id = "",
                name = name,
                proto = proto,
                srcPort = srcPort,
                destIp = destIp,
                destPort = destPort.ifBlank { srcPort },
                isEnabled = true
            )
            val res = routerRepository.addPortForwardRule(newRule)
            _uiState.value = _uiState.value.copy(
                isOperating = false,
                showAddPortForwardDialog = false,
                toastMessage = if (res.isSuccess) "端口转发规则已添加并生效" else "添加规则失败"
            )
            loadPortForwardRules()
        }
    }

    fun deletePortForwardRule(sectionId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true)
            val res = routerRepository.deletePortForwardRule(sectionId)
            _uiState.value = _uiState.value.copy(
                isOperating = false,
                toastMessage = if (res.isSuccess) "规则已删除" else "删除规则失败"
            )
            loadPortForwardRules()
        }
    }

    fun saveNewNode(alias: String, host: String, port: Int, user: String, pwd: String, https: Boolean) {
        viewModelScope.launch {
            val node = RouterNode(
                alias = alias,
                credentials = RouterCredentials(host, port, user, pwd, https)
            )
            preferencesRepository.saveNode(node)
            _uiState.value = _uiState.value.copy(
                showAddNodeDialog = false,
                toastMessage = "路由器节点 [$alias] 已保存"
            )
        }
    }

    fun switchActiveNode(node: RouterNode) {
        viewModelScope.launch {
            preferencesRepository.saveCredentials(node.credentials, autoLogin = true)
            _uiState.value = _uiState.value.copy(
                toastMessage = "已切换活跃节点为 [${node.alias}]，正在连接..."
            )
            routerRepository.login(node.credentials)
        }
    }

    fun deleteNode(nodeId: String) {
        viewModelScope.launch {
            preferencesRepository.deleteNode(nodeId)
            _uiState.value = _uiState.value.copy(toastMessage = "节点已移除")
        }
    }

    fun setThemeMode(mode: Int) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDynamicColor(enabled)
        }
    }

    fun openAddNodeDialog() {
        _uiState.value = _uiState.value.copy(showAddNodeDialog = true)
    }

    fun closeAddNodeDialog() {
        _uiState.value = _uiState.value.copy(showAddNodeDialog = false)
    }

    fun openAddPortForwardDialog() {
        _uiState.value = _uiState.value.copy(showAddPortForwardDialog = true)
    }

    fun closeAddPortForwardDialog() {
        _uiState.value = _uiState.value.copy(showAddPortForwardDialog = false)
    }

    fun logout() {
        viewModelScope.launch {
            preferencesRepository.clearCredentials()
            _uiState.value = _uiState.value.copy(loggedOut = true)
        }
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }
}
