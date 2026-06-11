package cn.diveplan.importer.ui.scan

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.diveplan.importer.ble.BlePermissions
import cn.diveplan.importer.ble.BleScanError
import cn.diveplan.importer.ble.DiscoveredBleDevice
import cn.diveplan.importer.ble.VendorMatch

/**
 * 扫描设备列表屏。
 *
 * 流程：
 *   1. 进入屏幕 → 检查 BLE 权限 / 蓝牙开关
 *   2. 用户点「开始扫描」→ ViewModel.startScan
 *   3. 列表实时更新，每行显示「📍 设备名 · vendor badge · RSSI · 地址」
 *   4. 点击一条设备 → onDeviceSelected（P3 加抓 dump 逻辑）
 *
 * 错误态处理：
 *   - 缺权限 → 弹权限请求 launcher
 *   - 蓝牙关 → 引导跳系统设置（ACTION_REQUEST_ENABLE）
 *   - 其它失败 → 显示 errorCode 和重试按钮
 */
@Composable
fun ScanScreen(
    onDeviceSelected: (DiscoveredBleDevice) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasPermissions by remember { mutableStateOf(BlePermissions.hasAllPermissions(context)) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermissions = result.values.all { it }
        if (hasPermissions) viewModel.startScan()
    }
    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (hasPermissions) viewModel.startScan()
    }

    LaunchedEffect(Unit) {
        if (hasPermissions) viewModel.startScan() else permLauncher.launch(BlePermissions.requiredPermissions())
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶部状态条
        ScanStatusBar(
            scanning = state.scanning,
            count = state.devices.size,
            onRescan = {
                if (!hasPermissions) { permLauncher.launch(BlePermissions.requiredPermissions()); return@ScanStatusBar }
                if (state.scanning) viewModel.stopScan() else viewModel.startScan()
            },
        )

        // 错误态
        state.error?.let { err ->
            ErrorCard(
                err = err,
                onEnableBluetooth = {
                    enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                },
                onRequestPermission = { permLauncher.launch(BlePermissions.requiredPermissions()) },
            )
        }

        // 列表
        if (state.devices.isEmpty() && state.scanning) {
            EmptyState(scanning = true)
        } else if (state.devices.isEmpty()) {
            EmptyState(scanning = false)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.devices, key = { it.address }) { device ->
                    DeviceRow(device = device, onClick = { onDeviceSelected(device) })
                }
            }
        }
    }
}

@Composable
private fun ScanStatusBar(scanning: Boolean, count: Int, onRescan: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (scanning) "🔍 扫描中…" else "扫描已停止",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "已发现 $count 台设备",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onRescan) {
            Text(if (scanning) "停止" else "↻ 扫描")
        }
    }
}

@Composable
private fun DeviceRow(device: DiscoveredBleDevice, onClick: () -> Unit) {
    val hit = device.vendorMatch as? VendorMatch.Hit
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧 vendor badge
            VendorBadge(hit = hit)
            Spacer(Modifier.width(12.dp))

            // 中间：名字 + 产品 + 地址
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "(无广播名)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (hit != null) {
                    val label = "${hit.vendor} ${hit.product}".trim() +
                        if (hit.ambiguous) "（请确认型号）" else ""
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (hit.weak) {
                        Text(
                            text = hit.hint ?: "需要走特殊通道",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                } else {
                    Text(
                        text = "未识别（点击手动选）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 右侧 RSSI
            RssiBadge(rssi = device.rssi)
        }
    }
}

@Composable
private fun VendorBadge(hit: VendorMatch.Hit?) {
    val (label, bg) = when {
        hit == null -> "?" to MaterialTheme.colorScheme.surfaceVariant
        hit.weak    -> hit.vendor.first().uppercase() to MaterialTheme.colorScheme.tertiary
        else        -> hit.vendor.first().uppercase() to MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun RssiBadge(rssi: Int) {
    // RSSI 通常 -40 强 → -100 弱
    val strength = when {
        rssi > -55 -> "●●●●"
        rssi > -70 -> "●●●○"
        rssi > -85 -> "●●○○"
        else       -> "●○○○"
    }
    Column(horizontalAlignment = Alignment.End) {
        Text(strength, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        Text("$rssi dBm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(scanning: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (scanning) "等待发现潜水电脑…\n请确认电脑表已开机并进入配对模式"
                   else          "未发现设备\n点击右上「扫描」开始",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorCard(err: BleScanError, onEnableBluetooth: () -> Unit, onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = when (err) {
                    BleScanError.NoAdapter            -> "此设备不支持低功耗蓝牙"
                    BleScanError.BluetoothOff         -> "蓝牙未开启"
                    is BleScanError.MissingPermission -> "缺少蓝牙权限"
                    is BleScanError.Failed            -> "扫描失败（错误码 ${err.code}）"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(8.dp))
            when (err) {
                BleScanError.BluetoothOff ->
                    Button(onClick = onEnableBluetooth) { Text("打开蓝牙") }
                is BleScanError.MissingPermission ->
                    Button(onClick = onRequestPermission) { Text("授权蓝牙") }
                else -> {}
            }
        }
    }
}
