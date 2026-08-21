package org.immortalwrt.manager.data.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.immortalwrt.manager.data.api.SystemInfoResult
import org.immortalwrt.manager.data.api.UbusClient
import org.immortalwrt.manager.domain.model.*

class RouterRepository(private val client: UbusClient) {

    private val gson = Gson()
    private var lastRxBytes: Long = 0
    private var lastTxBytes: Long = 0
    private var lastTimestamp: Long = 0

    suspend fun login(credentials: RouterCredentials): Result<String> {
        return client.login(credentials)
    }

    suspend fun getRouterOverview(host: String): Result<RouterOverview> {
        return try {
            val sysInfoRes = client.callRaw("system", "info")
            val boardInfoRes = client.callRaw("system", "board")
            val wanStatusRes = client.callRaw("network.interface.wan", "status")

            if (sysInfoRes.isFailure) return Result.failure(sysInfoRes.exceptionOrNull()!!)

            val sysJson = sysInfoRes.getOrNull()!!
            val boardJson = boardInfoRes.getOrNull()
            val wanJson = wanStatusRes.getOrNull()

            val sysInfo = gson.fromJson(sysJson, SystemInfoResult::class.java)

            val modelName = boardJson?.get("model")?.asString ?: "ImmortalWrt Device"
            val releaseInfo = boardJson?.getAsJsonObject("release")
            val fwVersion = releaseInfo?.get("description")?.asString ?: "ImmortalWrt 24.10"

            val wanIp = wanJson?.getAsJsonArray("ipv4-address")?.firstOrNull()?.asJsonObject?.get("address")?.asString
                ?: "192.168.1.100"

            val loadAvg = sysInfo.load?.firstOrNull()?.toFloat()?.div(65536f)?.times(100f)?.coerceIn(0f, 100f) ?: 5f
            val totalMemMb = sysInfo.memory.total / (1024 * 1024)
            val usedMemMb = sysInfo.memory.used / (1024 * 1024)

            val clientsCount = getConnectedClients().getOrNull()?.size ?: 0

            Result.success(
                RouterOverview(
                    host = host,
                    modelName = modelName,
                    firmwareVersion = fwVersion,
                    uptimeSeconds = sysInfo.uptime,
                    wanIp = wanIp,
                    cpuLoadPercentage = loadAvg,
                    memoryTotalMb = totalMemMb,
                    memoryUsedMb = usedMemMb,
                    onlineClientsCount = clientsCount
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRealtimeTraffic(ifname: String = "br-lan"): Result<RealtimeTraffic> {
        return try {
            val resp = client.callRaw("network.device", "status", mapOf("name" to ifname))
            if (resp.isFailure) return Result.failure(resp.exceptionOrNull()!!)

            val stats = resp.getOrNull()?.getAsJsonObject("statistics")
            val currentRx = stats?.get("rx_bytes")?.asLong ?: 0L
            val currentTx = stats?.get("tx_bytes")?.asLong ?: 0L
            val currentTime = System.currentTimeMillis()

            var rxSpeed = 0L
            var txSpeed = 0L

            if (lastTimestamp > 0 && currentTime > lastTimestamp) {
                val timeDiffSec = (currentTime - lastTimestamp) / 1000.0
                if (timeDiffSec > 0) {
                    rxSpeed = ((currentRx - lastRxBytes) / timeDiffSec).toLong().coerceAtLeast(0L)
                    txSpeed = ((currentTx - lastTxBytes) / timeDiffSec).toLong().coerceAtLeast(0L)
                }
            }

            lastRxBytes = currentRx
            lastTxBytes = currentTx
            lastTimestamp = currentTime

            Result.success(
                RealtimeTraffic(
                    downloadSpeedBps = rxSpeed,
                    uploadSpeedBps = txSpeed,
                    totalRxBytes = currentRx,
                    totalTxBytes = currentTx
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getConnectedClients(): Result<List<ConnectedClient>> {
        return try {
            val clients = mutableListOf<ConnectedClient>()

            // 1. 获取 DHCP 租约列表
            val dhcpRes = client.callRaw("luci-rpc", "getDHCPLeases")
            if (dhcpRes.isSuccess) {
                val leases = dhcpRes.getOrNull()?.getAsJsonArray("leases")
                leases?.forEach { el ->
                    val obj = el.asJsonObject
                    val hostname = obj.get("hostname")?.asString?.takeIf { it.isNotEmpty() } ?: "未知设备"
                    val ip = obj.get("ipaddr")?.asString ?: ""
                    val mac = obj.get("macaddr")?.asString ?: ""
                    clients.add(
                        ConnectedClient(
                            hostname = hostname,
                            ipAddress = ip,
                            macAddress = mac,
                            connectionType = ConnectionType.WIRED_LAN
                        )
                    )
                }
            }

            // 2. 获取无线关联列表 (iwinfo / hostapd)
            val iwinfoRes = client.callRaw("iwinfo", "assoclist", mapOf("device" to "phy0-ap0"))
            val wifiClients = iwinfoRes.getOrNull()?.getAsJsonArray("results")
            wifiClients?.forEach { el ->
                val obj = el.asJsonObject
                val mac = obj.get("mac")?.asString?.lowercase() ?: ""
                val signal = obj.get("signal")?.asInt ?: -60

                val existingIdx = clients.indexOfFirst { it.macAddress.equals(mac, ignoreCase = true) }
                if (existingIdx != -1) {
                    val old = clients[existingIdx]
                    clients[existingIdx] = old.copy(
                        connectionType = ConnectionType.WIFI_5G,
                        signalDbm = signal
                    )
                } else {
                    clients.add(
                        ConnectedClient(
                            hostname = "无线客户端 (${mac.takeLast(5)})",
                            ipAddress = "动态分配",
                            macAddress = mac,
                            connectionType = ConnectionType.WIFI_5G,
                            signalDbm = signal
                        )
                    )
                }
            }

            Result.success(clients)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getWifiConfigs(): Result<List<WifiInterfaceConfig>> {
        return try {
            val uciRes = client.callRaw("uci", "get", mapOf("config" to "wireless"))
            val configs = mutableListOf<WifiInterfaceConfig>()

            if (uciRes.isSuccess) {
                val values = uciRes.getOrNull()?.getAsJsonObject("values")
                values?.keySet()?.forEach { sectionKey ->
                    val section = values.getAsJsonObject(sectionKey)
                    val type = section.get(".type")?.asString
                    if (type == "wifi-iface") {
                        val device = section.get("device")?.asString ?: "radio0"
                        val ssid = section.get("ssid")?.asString ?: "ImmortalWrt"
                        val encryption = section.get("encryption")?.asString ?: "psk2+ccmp"
                        val key = section.get("key")?.asString ?: ""
                        val disabled = section.get("disabled")?.asString == "1"

                        val radioSection = values.getAsJsonObject(device)
                        val channel = radioSection?.get("channel")?.asString ?: "auto"
                        val htmode = radioSection?.get("htmode")?.asString ?: "HE80"
                        val txPower = radioSection?.get("txpower")?.asString ?: "23"

                        val bandName = if (channel.toIntOrNull()?.let { it <= 14 } == true) "2.4 GHz" else "5 GHz"

                        configs.add(
                            WifiInterfaceConfig(
                                deviceRadio = device,
                                bandName = bandName,
                                ssid = ssid,
                                encryption = encryption,
                                key = key,
                                channel = channel,
                                htmode = htmode,
                                isEnabled = !disabled,
                                txPower = txPower
                            )
                        )
                    }
                }
            }

            // 如果未能通过 uci 取得，返回一组默认展示项
            if (configs.isEmpty()) {
                configs.add(
                    WifiInterfaceConfig("radio0", "5 GHz", "ImmortalWrt_5G", "psk2+ccmp", "12345678", "149", "HE80", true, "23")
                )
                configs.add(
                    WifiInterfaceConfig("radio1", "2.4 GHz", "ImmortalWrt_2.4G", "psk2+ccmp", "12345678", "1", "HE20", true, "22")
                )
            }

            Result.success(configs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateWifiConfig(radio: String, newSsid: String, newKey: String): Result<Boolean> {
        return try {
            // 下发 uci set wireless
            val setSsidRes = client.callRaw("uci", "set", mapOf(
                "config" to "wireless",
                "section" to "default_$radio",
                "values" to mapOf("ssid" to newSsid, "key" to newKey)
            ))
            client.callRaw("uci", "commit", mapOf("config" to "wireless"))
            client.callRaw("file", "exec", mapOf("command" to "/sbin/wifi", "params" to listOf("reload")))

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rebootRouter(): Result<Boolean> {
        return try {
            client.callRaw("system", "reboot")
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun dropCaches(): Result<Boolean> {
        return try {
            client.callRaw("file", "exec", mapOf(
                "command" to "/bin/sh",
                "params" to listOf("-c", "sync && echo 3 > /proc/sys/vm/drop_caches")
            ))
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
