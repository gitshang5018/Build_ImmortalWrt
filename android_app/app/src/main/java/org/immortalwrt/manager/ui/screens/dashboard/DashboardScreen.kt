package org.immortalwrt.manager.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.immortalwrt.manager.ui.components.StatCard
import org.immortalwrt.manager.ui.theme.PrimaryBlue
import org.immortalwrt.manager.ui.theme.SecondaryCyan
import org.immortalwrt.manager.ui.theme.SuccessGreen
import org.immortalwrt.manager.ui.theme.WarningOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel
) {
    val state by viewModel.uiState.collectAsState()

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
                // 1. 实时网速卡片 (双色渐变现代卡片)
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

                // 2. 硬件资源占用情况
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
                    StatCard(
                        title = "CPU 负载",
                        value = "${String.format("%.1f", cpuLoad)}%",
                        icon = Icons.Default.Memory,
                        progress = cpuLoad / 100f,
                        progressColor = if (cpuLoad > 80) Color.Red else PrimaryBlue,
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
                        subtitle = "当前活跃连接",
                        modifier = Modifier.weight(1f)
                    )

                    StatCard(
                        title = "运行时长",
                        value = state.overview?.formattedUptime ?: "--",
                        icon = Icons.Default.AccessTime,
                        subtitle = "WAN: ${state.overview?.wanIp ?: "已连接"}",
                        modifier = Modifier.weight(1f)
                    )
                }

                // 3. 固件与网络状态信息
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
    }
}
