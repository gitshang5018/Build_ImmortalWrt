package org.immortalwrt.manager.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.immortalwrt.manager.data.repository.PreferencesRepository
import org.immortalwrt.manager.data.repository.RouterRepository
import org.immortalwrt.manager.domain.model.*

data class SettingsUiState(
    val currentCredentials: RouterCredentials = RouterCredentials(),
    val savedNodes: List<RouterNode> = emptyList(),
    val wanConfig: WanNetworkConfig = WanNetworkConfig(),
    val lanConfig: LanNetworkConfig = LanNetworkConfig(),
    val dhcpConfig: DhcpServerConfig = DhcpServerConfig(),
    val staticLeases: List<StaticDhcpLease> = emptyList(),
    val systemSettings: SystemSettings = SystemSettings(),
    val firewallAdvanced: FirewallAdvancedSettings = FirewallAdvancedSettings(),
    val portForwardRules: List<FirewallRedirectRule> = emptyList(),
    val themeMode: Int = 0, // 0: Auto, 1: Light, 2: Dark
    val dynamicColor: Boolean = true,
    val isOperating: Boolean = false,
    val isLoadingRules: Boolean = false,
    val isLoadingWebSettings: Boolean = false,
    val showAddNodeDialog: Boolean = false,
    val showEditWanDialog: Boolean = false,
    val showEditLanDialog: Boolean = false,
    val showEditDhcpDialog: Boolean = false,
    val showAddStaticLeaseDialog: Boolean = false,
    val showEditSystemDialog: Boolean = false,
    val showEditFirewallAdvDialog: Boolean = false,
    val showAddPortForwardDialog: Boolean = false,
    val showChangePwdDialog: Boolean = false,
    val showRebootDialog: Boolean = false,
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
        loadWebSettings()
    }

    fun loadWebSettings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingWebSettings = true)
            val wanRes = routerRepository.getWanConfig().getOrNull() ?: WanNetworkConfig()
            val lanRes = routerRepository.getLanConfig().getOrNull() ?: LanNetworkConfig()
            val dhcpRes = routerRepository.getDhcpConfig().getOrNull() ?: DhcpServerConfig()
            val sLeasesRes = routerRepository.getStaticDhcpLeases().getOrNull() ?: emptyList()
            val sysRes = routerRepository.getSystemSettings().getOrNull() ?: SystemSettings()
            val fwAdvRes = routerRepository.getFirewallAdvanced().getOrNull() ?: FirewallAdvancedSettings()

            _uiState.value = _uiState.value.copy(
                isLoadingWebSettings = false,
                wanConfig = wanRes,
                lanConfig = lanRes,
                dhcpConfig = dhcpRes,
                staticLeases = sLeasesRes,
                systemSettings = sysRes,
                firewallAdvanced = fwAdvRes
            )
        }
    }

    fun updateWanConfig(config: WanNetworkConfig) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true)
            val res = routerRepository.updateWanConfig(config)
            _uiState.value = _uiState.value.copy(
                isOperating = false,
                showEditWanDialog = false,
                toastMessage = if (res.isSuccess) "WAN 接口配置已更新并重新连接" else "修改 WAN 失败"
            )
            loadWebSettings()
        }
    }

    fun reconnectWan() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true)
            val res = routerRepository.reconnectWan()
            _uiState.value = _uiState.value.copy(
                isOperating = false,
                toastMessage = if (res.isSuccess) "WAN 重新拨号/连接指令已执行" else "操作失败"
            )
        }
    }

    fun updateLanConfig(ipaddr: String, netmask: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true)
            val res = routerRepository.updateLanConfig(LanNetworkConfig(ipaddr, netmask))
            _uiState.value = _uiState.value.copy(
                isOperating = false,
                showEditLanDialog = false,
                toastMessage = if (res.isSuccess) "LAN 网络配置已更新并下发生效" else "修改 LAN 失败"
            )
            loadWebSettings()
        }
    }

    fun updateDhcpConfig(start: Int, limit: Int, leasetime: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true)
            val res = routerRepository.updateDhcpConfig(DhcpServerConfig(true, start, limit, leasetime))
            _uiState.value = _uiState.value.copy(
                isOperating = false,
                showEditDhcpDialog = false,
                toastMessage = if (res.isSuccess) "DHCP 服务配置已更新" else "修改 DHCP 失败"
            )
            loadWebSettings()
        }
    }

    fun addStaticDhcpLease(hostname: String, mac: String, ip: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true)
            val res = routerRepository.addStaticDhcpLease(hostname, mac, ip)
            _uiState.value = _uiState.value.copy(
                isOperating = false,
                showAddStaticLeaseDialog = false,
                toastMessage = if (res.isSuccess) "已新增静态 DHCP 绑定: $hostname -> $ip" else "添加失败"
            )
            loadWebSettings()
        }
    }

    fun deleteStaticDhcpLease(sectionId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true)
            val res = routerRepository.deleteStaticDhcpLease(sectionId)
            _uiState.value = _uiState.value.copy(
                isOperating = false,
                toastMessage = if (res.isSuccess) "静态绑定规则已删除" else "删除失败"
            )
            loadWebSettings()
        }
    }

    fun updateFirewallAdvanced(config: FirewallAdvancedSettings) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true)
            val res = routerRepository.updateFirewallAdvanced(config)
            _uiState.value = _uiState.value.copy(
                isOperating = false,
                showEditFirewallAdvDialog = false,
                firewallAdvanced = config,
                toastMessage = if (res.isSuccess) "高级防火墙配置已生效" else "配置失败"
            )
        }
    }

    fun updateSystemSettings(hostname: String, zonename: String, timezone: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true)
            val res = routerRepository.updateSystemSettings(SystemSettings(hostname, timezone, zonename))
            _uiState.value = _uiState.value.copy(
                isOperating = false,
                showEditSystemDialog = false,
                toastMessage = if (res.isSuccess) "系统属性已保存" else "保存失败"
            )
            loadWebSettings()
        }
    }

    fun syncSystemTime() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true)
            val currentSec = System.currentTimeMillis() / 1000
            val res = routerRepository.syncSystemTime(currentSec)
            _uiState.value = _uiState.value.copy(
                isOperating = false,
                toastMessage = if (res.isSuccess) "已将路由器时间与手机时间精准同步" else "时间同步失败"
            )
        }
    }

    fun changeAdminPassword(newPassword: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true)
            val res = routerRepository.changeAdminPassword(newPassword)
            _uiState.value = _uiState.value.copy(
                isOperating = false,
                showChangePwdDialog = false,
                toastMessage = if (res.isSuccess) "路由器管理员 root 密码已成功修改" else "修改密码失败"
            )
        }
    }

    fun rebootRouter() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true)
            val res = routerRepository.rebootRouter()
            _uiState.value = _uiState.value.copy(
                isOperating = false,
                showRebootDialog = false,
                toastMessage = if (res.isSuccess) "路由器重启指令已下发，正在重启..." else "重启失败"
            )
        }
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
            loadWebSettings()
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

    fun openAddNodeDialog() { _uiState.value = _uiState.value.copy(showAddNodeDialog = true) }
    fun closeAddNodeDialog() { _uiState.value = _uiState.value.copy(showAddNodeDialog = false) }
    fun openEditWanDialog() { _uiState.value = _uiState.value.copy(showEditWanDialog = true) }
    fun closeEditWanDialog() { _uiState.value = _uiState.value.copy(showEditWanDialog = false) }
    fun openEditLanDialog() { _uiState.value = _uiState.value.copy(showEditLanDialog = true) }
    fun closeEditLanDialog() { _uiState.value = _uiState.value.copy(showEditLanDialog = false) }
    fun openEditDhcpDialog() { _uiState.value = _uiState.value.copy(showEditDhcpDialog = true) }
    fun closeEditDhcpDialog() { _uiState.value = _uiState.value.copy(showEditDhcpDialog = false) }
    fun openAddStaticLeaseDialog() { _uiState.value = _uiState.value.copy(showAddStaticLeaseDialog = true) }
    fun closeAddStaticLeaseDialog() { _uiState.value = _uiState.value.copy(showAddStaticLeaseDialog = false) }
    fun openEditFirewallAdvDialog() { _uiState.value = _uiState.value.copy(showEditFirewallAdvDialog = true) }
    fun closeEditFirewallAdvDialog() { _uiState.value = _uiState.value.copy(showEditFirewallAdvDialog = false) }
    fun openAddPortForwardDialog() { _uiState.value = _uiState.value.copy(showAddPortForwardDialog = true) }
    fun closeAddPortForwardDialog() { _uiState.value = _uiState.value.copy(showAddPortForwardDialog = false) }
    fun openEditSystemDialog() { _uiState.value = _uiState.value.copy(showEditSystemDialog = true) }
    fun closeEditSystemDialog() { _uiState.value = _uiState.value.copy(showEditSystemDialog = false) }
    fun openChangePwdDialog() { _uiState.value = _uiState.value.copy(showChangePwdDialog = true) }
    fun closeChangePwdDialog() { _uiState.value = _uiState.value.copy(showChangePwdDialog = false) }
    fun openRebootDialog() { _uiState.value = _uiState.value.copy(showRebootDialog = true) }
    fun closeRebootDialog() { _uiState.value = _uiState.value.copy(showRebootDialog = false) }

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


