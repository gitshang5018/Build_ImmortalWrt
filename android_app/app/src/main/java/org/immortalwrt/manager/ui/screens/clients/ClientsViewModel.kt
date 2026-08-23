package org.immortalwrt.manager.ui.screens.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.immortalwrt.manager.data.repository.RouterRepository
import org.immortalwrt.manager.domain.model.ConnectedClient
import org.immortalwrt.manager.domain.model.ConnectionType

enum class ClientFilter {
    ALL,
    ONLINE_ONLY,
    OFFLINE_ONLY,
    WIFI_ONLY,
    WIRED_ONLY
}

data class ClientsUiState(
    val isLoading: Boolean = true,
    val isOperating: Boolean = false,
    val clients: List<ConnectedClient> = emptyList(),
    val customAliases: Map<String, String> = emptyMap(),
    val selectedClient: ConnectedClient? = null,
    val searchQuery: String = "",
    val filter: ClientFilter = ClientFilter.ALL,
    val errorMessage: String? = null,
    val toastMessage: String? = null
) {
    val onlineCount: Int get() = clients.count { it.isOnline }
    val offlineCount: Int get() = clients.count { !it.isOnline }
    val totalCount: Int get() = clients.size

    val filteredClients: List<ConnectedClient>
        get() = clients.map { client ->
            val alias = customAliases[client.macAddress.lowercase()]
            if (alias != null) client.copy(customAlias = alias) else client
        }.filter { client ->
            val searchTarget = "${client.displayName} ${client.ipAddress} ${client.macAddress} ${client.vendor ?: ""}"
            val matchSearch = searchTarget.contains(searchQuery, ignoreCase = true)

            val matchFilter = when (filter) {
                ClientFilter.ALL -> true
                ClientFilter.ONLINE_ONLY -> client.isOnline
                ClientFilter.OFFLINE_ONLY -> !client.isOnline
                ClientFilter.WIFI_ONLY -> client.connectionType != ConnectionType.WIRED_LAN
                ClientFilter.WIRED_ONLY -> client.connectionType == ConnectionType.WIRED_LAN
            }

            matchSearch && matchFilter
        }.sortedWith(
            compareByDescending<ConnectedClient> { it.isOnline }
                .thenBy { it.displayName }
        )
}

class ClientsViewModel(
    private val routerRepository: RouterRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClientsUiState())
    val uiState: StateFlow<ClientsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun onFilterChange(filter: ClientFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
    }

    fun selectClient(client: ConnectedClient?) {
        _uiState.value = _uiState.value.copy(selectedClient = client)
    }

    fun updateClientAlias(mac: String, alias: String) {
        val updated = _uiState.value.customAliases.toMutableMap()
        if (alias.isBlank()) {
            updated.remove(mac.lowercase())
        } else {
            updated[mac.lowercase()] = alias.trim()
        }
        _uiState.value = _uiState.value.copy(
            customAliases = updated,
            selectedClient = null,
            toastMessage = "设备备注已保存"
        )
    }

    fun bindStaticDhcp(hostname: String, mac: String, ip: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true)
            val res = routerRepository.addStaticDhcpLease(hostname, mac, ip)
            _uiState.value = _uiState.value.copy(
                isOperating = false,
                selectedClient = null,
                toastMessage = if (res.isSuccess) "已成功为 $hostname 绑定静态 IP: $ip" else "静态绑定失败"
            )
        }
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = routerRepository.getConnectedClients()
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    clients = result.getOrNull() ?: emptyList(),
                    errorMessage = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }
}
