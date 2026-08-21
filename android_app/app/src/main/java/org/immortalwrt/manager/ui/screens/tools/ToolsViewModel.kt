package org.immortalwrt.manager.ui.screens.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.immortalwrt.manager.data.repository.RouterRepository
import java.io.BufferedReader
import java.io.InputStreamReader

data class ToolsUiState(
    val isOperating: Boolean = false,
    val pingTarget: String = "223.5.5.5",
    val isPinging: Boolean = false,
    val pingResult: String? = null,
    val toastMessage: String? = null,
    val errorMessage: String? = null
)

class ToolsViewModel(
    private val routerRepository: RouterRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()

    fun onPingTargetChange(target: String) {
        _uiState.value = _uiState.value.copy(pingTarget = target)
    }

    fun rebootRouter() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true, errorMessage = null)
            val result = routerRepository.rebootRouter()
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isOperating = false,
                    toastMessage = "重启指令已发送，路由器正在重启..."
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isOperating = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "重启失败"
                )
            }
        }
    }

    fun dropCaches() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOperating = true, errorMessage = null)
            val result = routerRepository.dropCaches()
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isOperating = false,
                    toastMessage = "内存缓存清理完成"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isOperating = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "清理失败"
                )
            }
        }
    }

    fun runPing() {
        val target = _uiState.value.pingTarget.trim()
        if (target.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPinging = true, pingResult = "正在测试网络延迟...")
            val resultText = withContext(Dispatchers.IO) {
                try {
                    val process = Runtime.getRuntime().exec("ping -c 4 $target")
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    val output = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        output.append(line).append("\n")
                    }
                    process.waitFor()
                    output.toString().ifEmpty { "Ping 执行完成，无回显" }
                } catch (e: Exception) {
                    "Ping 测试异常: ${e.message}"
                }
            }
            _uiState.value = _uiState.value.copy(isPinging = false, pingResult = resultText)
        }
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }
}
