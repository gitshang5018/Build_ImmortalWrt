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
    WIFI_ONLY,
    WIRED_ONLY
}

data class ClientsUiState(
    val isLoading: Boolean = true,
    val clients: List<ConnectedClient> = emptyList(),
    val searchQuery: String = "",
    val filter: ClientFilter = ClientFilter.ALL,
    val errorMessage: String? = null
) {
    val filteredClients: List<ConnectedClient>
        get() = clients.filter { client ->
            val matchSearch = client.hostname.contains(searchQuery, ignoreCase = true) ||
                    client.ipAddress.contains(searchQuery, ignoreCase = true) ||
                    client.macAddress.contains(searchQuery, ignoreCase = true)

            val matchFilter = when (filter) {
                ClientFilter.ALL -> true
                ClientFilter.WIFI_ONLY -> client.connectionType != ConnectionType.WIRED_LAN
                ClientFilter.WIRED_ONLY -> client.connectionType == ConnectionType.WIRED_LAN
            }

            matchSearch && matchFilter
        }
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
