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
    var showRebootDialog by remember { mutableStateOf(false) }

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
                            text = "ImmortalWrt 仪表盘",
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

                            Divider(
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
                    text = "系统资源与连接",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                        icon = Icons.Default.Memory,
                        progress = cpuLoad / 100f,
                        progressColor = cpuColor,
                        modifier = Modifier.weight(1f)
                    )

                    val memUsed = state.overview?.memoryUsedMb ?: 0
                    val memTotal = state.overview?.memoryTotalMb ?: 1
                    val memPct = (memUsed.toFloat() / memTotal.toFloat())
                    StatCard(
                        title = "内存占用",
                        value = "${memUsed} MB",
                        subtitle = "共 ${memTotal} MB",
                        icon = Icons.Default.Storage,
                        progress = memPct,
                        progressColor = SecondaryCyan,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "在线设备",
                        value = "${state.overview?.onlineClientsCount ?: 0} 台",
                        icon = Icons.Default.Devices,
                        subtitle = "当前活跃终端",
                        modifier = Modifier.weight(1f)
                    )

                    StatCard(
                        title = "运行时长",
                        value = state.overview?.formattedUptime ?: "--",
                        icon = Icons.Default.AccessTime,
                        subtitle = "网关: ${state.overview?.host ?: "10.10.10.1"}",
                        modifier = Modifier.weight(1f)
                    )
                }

                // 4. 快捷系统控制
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
                        OutlinedButton(
                            onClick = { viewModel.dropCaches() },
                            enabled = !state.isOperating,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("释放内核缓存")
                        }

                        Button(
                            onClick = { showRebootDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                            enabled = !state.isOperating,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("重启路由")
                        }
                    }
                }

                // 5. 固件与网络状态信息
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "设备详情",
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
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("固件版本", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(state.overview?.firmwareVersion ?: "--", fontWeight = FontWeight.Medium)
                        }
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("外网 IPv4", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(state.overview?.wanIp ?: "未获取", fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (showRebootDialog) {
            AlertDialog(
                onDismissRequest = { showRebootDialog = false },
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = WarningOrange) },
                title = { Text("确认重启路由器？") },
                text = { Text("重启大约需要 1~2 分钟，期间所有连接将短暂中断。") },
                confirmButton = {
                    Button(
                        onClick = {
                            showRebootDialog = false
                            viewModel.rebootRouter()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                    ) {
                        Text("立即重启")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRebootDialog = false }) {
                        Text("取 消")
                    }
                }
            )
        }
    }
}
