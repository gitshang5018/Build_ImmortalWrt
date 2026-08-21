package org.immortalwrt.manager.ui.screens.wireless

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.immortalwrt.manager.data.repository.RouterRepository
import org.immortalwrt.manager.domain.model.WifiInterfaceConfig

data class WirelessUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val wifiConfigs: List<WifiInterfaceConfig> = emptyList(),
    val editingConfig: WifiInterfaceConfig? = null,
    val message: String? = null,
    val errorMessage: String? = null
)

class WirelessViewModel(
    private val routerRepository: RouterRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WirelessUiState())
    val uiState: StateFlow<WirelessUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = routerRepository.getWifiConfigs()
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    wifiConfigs = result.getOrNull() ?: emptyList(),
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

    fun openEditDialog(config: WifiInterfaceConfig) {
        _uiState.value = _uiState.value.copy(editingConfig = config)
    }

    fun closeEditDialog() {
        _uiState.value = _uiState.value.copy(editingConfig = null)
    }

    fun saveWifiConfig(radio: String, newSsid: String, newKey: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            val result = routerRepository.updateWifiConfig(radio, newSsid, newKey)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    editingConfig = null,
                    message = "Wi-Fi 配置已更新并应用"
                )
                refresh()
            } else {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "修改失败"
                )
            }
        }
    }
}
