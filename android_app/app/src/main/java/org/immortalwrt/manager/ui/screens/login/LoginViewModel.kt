package org.immortalwrt.manager.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.immortalwrt.manager.data.repository.PreferencesRepository
import org.immortalwrt.manager.data.repository.RouterRepository
import org.immortalwrt.manager.domain.model.RouterCredentials

data class LoginUiState(
    val host: String = "10.10.10.1",
    val port: String = "80",
    val username: String = "root",
    val password: String = "",
    val useHttps: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoginSuccess: Boolean = false
)

class LoginViewModel(
    private val routerRepository: RouterRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.credentialsFlow.collect { creds ->
                _uiState.value = _uiState.value.copy(
                    host = creds.host,
                    port = creds.port.toString(),
                    username = creds.username,
                    password = creds.password,
                    useHttps = creds.useHttps
                )
            }
        }
    }

    fun onHostChange(host: String) { _uiState.value = _uiState.value.copy(host = host, errorMessage = null) }
    fun onPortChange(port: String) { _uiState.value = _uiState.value.copy(port = port, errorMessage = null) }
    fun onUsernameChange(u: String) { _uiState.value = _uiState.value.copy(username = u, errorMessage = null) }
    fun onPasswordChange(p: String) { _uiState.value = _uiState.value.copy(password = p, errorMessage = null) }
    fun onHttpsChange(https: Boolean) {
        val defaultPort = if (https) "443" else "80"
        _uiState.value = _uiState.value.copy(useHttps = https, port = defaultPort, errorMessage = null)
    }

    fun login() {
        val state = _uiState.value
        val portInt = state.port.toIntOrNull() ?: if (state.useHttps) 443 else 80
        val credentials = RouterCredentials(
            host = state.host.trim(),
            port = portInt,
            username = state.username.trim(),
            password = state.password,
            useHttps = state.useHttps
        )

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            val result = routerRepository.login(credentials)
            if (result.isSuccess) {
                preferencesRepository.saveCredentials(credentials)
                _uiState.value = _uiState.value.copy(isLoading = false, isLoginSuccess = true)
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "登录失败，请检查网络与密码"
                )
            }
        }
    }
}
