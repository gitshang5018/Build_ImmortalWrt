package org.immortalwrt.manager.data.api

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * Standard OpenWrt/ImmortalWrt ubus JSON-RPC 2.0 Request
 */
data class UbusRequest(
    @SerializedName("jsonrpc") val jsonrpc: String = "2.0",
    @SerializedName("id") val id: Int = 1,
    @SerializedName("method") val method: String = "call",
    @SerializedName("params") val params: List<Any>
) {
    companion object {
        fun create(sessionToken: String, module: String, func: String, args: Any = emptyMap<String, Any>()): UbusRequest {
            return UbusRequest(
                params = listOf(sessionToken, module, func, args)
            )
        }
    }
}

/**
 * Standard ubus JSON-RPC 2.0 Response
 * result format: [return_code (0 is success), payload_object]
 */
data class UbusResponse<T>(
    @SerializedName("jsonrpc") val jsonrpc: String?,
    @SerializedName("id") val id: Int?,
    @SerializedName("result") val result: List<JsonElement>?,
    @SerializedName("error") val error: UbusError?
) {
    val returnCode: Int
        get() = result?.firstOrNull()?.asInt ?: -1

    val isSuccess: Boolean
        get() = returnCode == 0 && error == null
}

data class UbusError(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String
)

/**
 * Session Login Response payload
 */
data class LoginResult(
    @SerializedName("ubus_rpc_session") val sessionToken: String,
    @SerializedName("timeout") val timeout: Long?,
    @SerializedName("expires") val expires: Long?
)

/**
 * System info payload (ubus call system info)
 */
data class SystemInfoResult(
    @SerializedName("localtime") val localtime: Long?,
    @SerializedName("uptime") val uptime: Long,
    @SerializedName("load") val load: List<Long>?,
    @SerializedName("memory") val memory: MemoryInfo,
    @SerializedName("swap") val swap: SwapInfo?
)

data class MemoryInfo(
    @SerializedName("total") val total: Long,
    @SerializedName("free") val free: Long,
    @SerializedName("shared") val shared: Long?,
    @SerializedName("buffered") val buffered: Long?,
    @SerializedName("cached") val cached: Long?,
    @SerializedName("available") val available: Long?
) {
    // 真实可用内存 (与 LuCI 网页端对齐：优先使用 available，无则使用 free + buffered + cached)
    val realAvailable: Long
        get() = if (available != null && available > 0) {
            available
        } else {
            val buff = buffered ?: 0L
            val cach = cached ?: 0L
            free + buff + cach
        }

    // 真实已使用内存 (与 LuCI 网页端对齐：总内存 - 真实可用内存)
    val used: Long
        get() = (total - realAvailable).coerceAtLeast(0L)

    val usedPercentage: Float
        get() = if (total > 0) (used.toFloat() / total.toFloat()) * 100f else 0f
}

data class SwapInfo(
    @SerializedName("total") val total: Long,
    @SerializedName("free") val free: Long
)

/**
 * Network device status (ubus call network.device status '{"name":"..."}')
 */
data class NetworkDeviceStatusResult(
    @SerializedName("name") val name: String?,
    @SerializedName("up") val up: Boolean,
    @SerializedName("statistics") val statistics: DeviceStatistics?
)

data class DeviceStatistics(
    @SerializedName("rx_bytes") val rxBytes: Long,
    @SerializedName("tx_bytes") val txBytes: Long,
    @SerializedName("rx_packets") val rxPackets: Long?,
    @SerializedName("tx_packets") val txPackets: Long?,
    @SerializedName("rx_errors") val rxErrors: Long?,
    @SerializedName("tx_errors") val txErrors: Long?
)
