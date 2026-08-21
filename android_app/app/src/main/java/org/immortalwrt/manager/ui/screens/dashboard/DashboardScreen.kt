package org.immortalwrt.manager.ui.screens.dashboard

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.immortalwrt.manager.ui.components.StatCard
import org.immortalwrt.manager.ui.components.TrafficWaveformChart
import org.immortalwrt.manager.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "路由管家",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        state.overview?.let {
                            Text(
                                text = "在线: ${it.host} · ${it.modelName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading && state.overview == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. 实时网速速率卡片 (双色渐变卡片)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(PrimaryBlue, SecondaryCyan)
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("实时下载", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = state.traffic?.formattedDownloadSpeed ?: "0.0 KB/s",
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier
                                    .height(48.dp)
                                    .width(1.dp),
                                color = Color.White.copy(alpha = 0.3f)
                            )

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("实时上传", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = state.traffic?.formattedUploadSpeed ?: "0.0 KB/s",
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 2. 实时贝塞尔波形图
                TrafficWaveformChart(
                    rxHistory = state.rxHistory,
                    txHistory = state.txHistory,
                    currentTraffic = state.traffic
                )

                // 3. 硬件资源占用与在线状态
                Text(
                    text = "系统资源与运行状态",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val cpuLoad = state.overview?.cpuLoadPercentage ?: 0f
                    val cpuColor = when {
                        cpuLoad > 85f -> ErrorRed
                        cpuLoad > 60f -> WarningOrange
                        else -> PrimaryBlue
                    }

                    StatCard(
                        title = "CPU 负载",
                        value = "${String.format("%.1f", cpuLoad)}%",
                        subtitle = "平均负载: ${state.overview?.cpuLoadAverage ?: "--"}",
                        icon = Icons.Default.Memory,
                        progress = cpuLoad / 100f,
                        progressColor = cpuColor,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    val memUsed = state.overview?.memoryUsedMb ?: 0
                    val memTotal = state.overview?.memoryTotalMb ?: 1
                    val memPct = (memUsed.toFloat() / memTotal.toFloat())
                    StatCard(
                        title = "内存占用",
                        value = "${memUsed} MB",
                        subtitle = "共 ${memTotal} MB (${String.format("%.0f", memPct * 100)}%)",
                        icon = Icons.Default.Storage,
                        progress = memPct,
                        progressColor = SecondaryCyan,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }

                // 硬件温度监控区 (有几个 Wi-Fi 频段就展示几个独立 Wi-Fi 温度)
                val cpuTempStr = state.overview?.cpuTemperature
                val wifiBandTemps = state.overview?.wifiBandTemperatures ?: emptyList()

                if (cpuTempStr != null || wifiBandTemps.isNotEmpty()) {
                    if (wifiBandTemps.isEmpty()) {
                        // 0 个无线频段 (有线主路由 / X86 软路由)
                        if (cpuTempStr != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val cpuTempVal = cpuTempStr.replace("°C", "").toIntOrNull() ?: 45
                                val cpuTempColor = when {
                                    cpuTempVal > 80 -> ErrorRed
                                    cpuTempVal > 65 -> WarningOrange
                                    else -> SuccessGreen
                                }

                                StatCard(
                                    title = "CPU 温度",
                                    value = cpuTempStr,
                                    subtitle = "处理器核心温度",
                                    icon = Icons.Default.DeviceThermostat,
                                    progress = (cpuTempVal / 100f).coerceIn(0f, 1f),
                                    progressColor = cpuTempColor,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )

                                StatCard(
                                    title = "无线硬件",
                                    value = if (state.overview?.hasWireless == true) "无温控探头" else "无无线网卡",
                                    subtitle = if (state.overview?.hasWireless == true) "未暴露温度传感器" else "有线主路由 / X86 架构",
                                    icon = Icons.Default.WifiOff,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        }
                    } else if (wifiBandTemps.size == 1) {
                        // 1 个无线频段：CPU 温度 与该频段 Wi-Fi 温度并排
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (cpuTempStr != null) {
                                val cpuTempVal = cpuTempStr.replace("°C", "").toIntOrNull() ?: 45
                                val cpuTempColor = when {
                                    cpuTempVal > 80 -> ErrorRed
                                    cpuTempVal > 65 -> WarningOrange
                                    else -> SuccessGreen
                                }

                                StatCard(
                                    title = "CPU 温度",
                                    value = cpuTempStr,
                                    subtitle = "处理器核心温度",
                                    icon = Icons.Default.DeviceThermostat,
                                    progress = (cpuTempVal / 100f).coerceIn(0f, 1f),
                                    progressColor = cpuTempColor,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }

                            val singleBand = wifiBandTemps[0]
                            val wTempVal = singleBand.temperature.replace("°C", "").toIntOrNull() ?: 48
                            val wTempColor = when {
                                wTempVal > 80 -> ErrorRed
                                wTempVal > 65 -> WarningOrange
                                else -> PrimaryBlue
                            }

                            StatCard(
                                title = "${singleBand.bandName} 温度",
                                value = singleBand.temperature,
                                subtitle = "${singleBand.radioDevice} 射频芯片",
                                icon = Icons.Default.WifiTethering,
                                progress = (wTempVal / 100f).coerceIn(0f, 1f),
                                progressColor = wTempColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    } else {
                        // 2个 / 3个 / 多个无线频段：为每一个频段独立生成温度卡片
                        val cardList = mutableListOf<@Composable (Modifier) -> Unit>()
                        if (cpuTempStr != null) {
                            val cpuTempVal = cpuTempStr.replace("°C", "").toIntOrNull() ?: 45
                            val cpuTempColor = when {
                                cpuTempVal > 80 -> ErrorRed
                                cpuTempVal > 65 -> WarningOrange
                                else -> SuccessGreen
                            }
                            cardList.add { mod ->
                                StatCard(
                                    title = "CPU 温度",
                                    value = cpuTempStr,
                                    subtitle = "处理器核心温度",
                                    icon = Icons.Default.DeviceThermostat,
                                    progress = (cpuTempVal / 100f).coerceIn(0f, 1f),
                                    progressColor = cpuTempColor,
                                    modifier = mod
                                )
                            }
                        }

                        wifiBandTemps.forEachIndexed { index, band ->
                            val wTempVal = band.temperature.replace("°C", "").toIntOrNull() ?: 48
                            val wTempColor = when {
                                wTempVal > 80 -> ErrorRed
                                wTempVal > 65 -> WarningOrange
                                index % 2 == 0 -> PrimaryBlue
                                else -> SecondaryCyan
                            }
                            cardList.add { mod ->
                                StatCard(
                                    title = "${band.bandName} 温度",
                                    value = band.temperature,
                                    subtitle = "${band.radioDevice} 射频芯片",
                                    icon = Icons.Default.WifiTethering,
                                    progress = (wTempVal / 100f).coerceIn(0f, 1f),
                                    progressColor = wTempColor,
                                    modifier = mod
                                )
                            }
                        }

                        cardList.chunked(2).forEach { rowPair ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowPair[0](Modifier.weight(1f).fillMaxHeight())
                                if (rowPair.size > 1) {
                                    rowPair[1](Modifier.weight(1f).fillMaxHeight())
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "在线设备",
                        value = "${state.overview?.onlineClientsCount ?: 0} 台",
                        icon = Icons.Default.Devices,
                        subtitle = "当前活跃终端",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    StatCard(
                        title = "运行时长",
                        value = state.overview?.formattedUptime ?: "--",
                        icon = Icons.Default.AccessTime,
                        subtitle = "网关: ${state.overview?.lanIp ?: state.overview?.host ?: "10.10.10.1"}",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }

                // 4. 快捷释放内存
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("内核缓存优化", fontWeight = FontWeight.SemiBold)
                            Text("释放 Linux PageCache / Slab 临时缓存", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        OutlinedButton(
                            onClick = { viewModel.dropCaches() },
                            enabled = !state.isOperating,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("释放缓存")
                        }
                    }
                }

                // 5. 固件与双栈网络状态信息
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "设备详情与外网双栈",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("设备型号", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(state.overview?.modelName ?: "--", fontWeight = FontWeight.Medium)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("固件版本", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(state.overview?.firmwareVersion ?: "--", fontWeight = FontWeight.Medium)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("局域网网关", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(state.overview?.lanIp ?: "--", fontWeight = FontWeight.Medium)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("外网 IPv4", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(state.overview?.wanIpv4 ?: "未连接", fontWeight = FontWeight.Bold, color = PrimaryBlue)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("外网 IPv6", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(state.overview?.wanIpv6 ?: "未分配", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
