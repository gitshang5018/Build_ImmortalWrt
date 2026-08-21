package org.immortalwrt.manager.ui.screens.clients

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.immortalwrt.manager.domain.model.ConnectedClient
import org.immortalwrt.manager.domain.model.ConnectionType
import org.immortalwrt.manager.ui.theme.PrimaryBlue
import org.immortalwrt.manager.ui.theme.SecondaryCyan
import org.immortalwrt.manager.ui.theme.SuccessGreen
import org.immortalwrt.manager.ui.theme.WarningOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsScreen(
    viewModel: ClientsViewModel
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
                    Text(
                        text = "在线终端 (${state.filteredClients.size})",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                placeholder = { Text("搜索设备名称 / IP / MAC / 厂商") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.filter == ClientFilter.ALL,
                    onClick = { viewModel.onFilterChange(ClientFilter.ALL) },
                    label = { Text("全部 (${state.clients.size})") }
                )
                FilterChip(
                    selected = state.filter == ClientFilter.WIFI_ONLY,
                    onClick = { viewModel.onFilterChange(ClientFilter.WIFI_ONLY) },
                    label = { Text("Wi-Fi 无线") },
                    leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = state.filter == ClientFilter.WIRED_ONLY,
                    onClick = { viewModel.onFilterChange(ClientFilter.WIRED_ONLY) },
                    label = { Text("有线 LAN") },
                    leadingIcon = { Icon(Icons.Default.Cable, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.filteredClients.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "暂无匹配的在线设备",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.filteredClients) { client ->
                        ClientItemCard(
                            client = client,
                            onClick = { viewModel.selectClient(client) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }

        state.selectedClient?.let { client ->
            ClientDetailDialog(
                client = client,
                isOperating = state.isOperating,
                onDismiss = { viewModel.selectClient(null) },
                onSaveAlias = { alias -> viewModel.updateClientAlias(client.macAddress, alias) },
                onBindStaticDhcp = {
                    viewModel.bindStaticDhcp(client.displayName, client.macAddress, client.ipAddress)
                }
            )
        }
    }
}

@Composable
fun ClientItemCard(
    client: ConnectedClient,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (icon, iconBg) = getDeviceIconAndColor(client)

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(iconBg.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconBg)
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = client.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = iconBg.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        val typeText = when (client.connectionType) {
                            ConnectionType.WIRED_LAN -> "LAN"
                            ConnectionType.WIFI_2G -> "2.4G"
                            ConnectionType.WIFI_5G -> "5G"
                            ConnectionType.WIFI_6G -> "6G"
                        }
                        Text(
                            text = typeText,
                            color = iconBg,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    if (client.isStaticLease) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            color = SuccessGreen.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "静态",
                                color = SuccessGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${client.ipAddress} · ${client.macAddress}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                client.vendor?.let { v ->
                    Text(
                        text = "厂商: $v",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (client.connectionType != ConnectionType.WIRED_LAN) {
                Column(horizontalAlignment = Alignment.End) {
                    Icon(
                        imageVector = Icons.Default.SignalWifi4Bar,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "${client.signalDbm} dBm",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun ClientDetailDialog(
    client: ConnectedClient,
    isOperating: Boolean,
    onDismiss: () -> Unit,
    onSaveAlias: (String) -> Unit,
    onBindStaticDhcp: () -> Unit
) {
    var aliasText by remember { mutableStateOf(client.customAlias ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("终端详情与绑定")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = aliasText,
                    onValueChange = { aliasText = it },
                    label = { Text("设备备注名称 (别名)") },
                    placeholder = { Text(client.hostname) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("原始主机名", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(client.hostname, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("分配 IP", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(client.ipAddress, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                        }
                        client.ipv6Address?.let { ip6 ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("IPv6 地址", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(ip6, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("物理 MAC", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(client.macAddress, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                        client.vendor?.let { v ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("设备制造商", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(v, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                if (!client.isStaticLease) {
                    Button(
                        onClick = onBindStaticDhcp,
                        enabled = !isOperating && client.ipAddress.isNotBlank() && client.ipAddress != "动态分配",
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("固定绑定此 IP (静态 DHCP)")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveAlias(aliasText) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("保存备注")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

private fun getDeviceIconAndColor(client: ConnectedClient): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> {
    val h = client.hostname.lowercase()
    val m = client.macAddress.lowercase()
    val v = client.vendor?.lowercase() ?: ""
    return when {
        client.connectionType == ConnectionType.WIRED_LAN -> Icons.Default.Computer to PrimaryBlue
        h.contains("iphone") || h.contains("ipad") || v.contains("apple") -> Icons.Default.Smartphone to SecondaryCyan
        h.contains("android") || v.contains("xiaomi") || v.contains("huawei") || v.contains("honor") || v.contains("oppo") || v.contains("vivo") -> Icons.Default.PhoneAndroid to SecondaryCyan
        h.contains("tv") || h.contains("box") || h.contains("media") || v.contains("sony") -> Icons.Default.Tv to SuccessGreen
        v.contains("espressif") || v.contains("iot") -> Icons.Default.Sensors to WarningOrange
        else -> Icons.Default.DevicesOther to PrimaryBlue
    }
}


