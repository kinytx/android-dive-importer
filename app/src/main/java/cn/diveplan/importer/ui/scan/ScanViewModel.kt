package cn.diveplan.importer.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.diveplan.importer.ble.BleScanError
import cn.diveplan.importer.ble.BleScanner
import cn.diveplan.importer.ble.DiscoveredBleDevice
import cn.diveplan.importer.ble.VendorMatch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 扫描页 VM：把 BleScanner 的 raw [DiscoveredBleDevice] 合成 [ScanUiState]，并对列表排序：
 *   1. 已识别 vendor 的优先（vendorMatch is Hit）
 *   2. RSSI 大的优先（信号好的设备先尝试连）
 *   3. firstSeenAt 早的优先（同 RSSI 时稳定顺序）
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val bleScanner: BleScanner,
) : ViewModel() {

    val uiState: StateFlow<ScanUiState> = combine(
        bleScanner.devices,
        bleScanner.scanning,
        bleScanner.error,
    ) { devices, scanning, error ->
        ScanUiState(
            devices = devices.sortedWith(deviceComparator),
            scanning = scanning,
            error = error,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ScanUiState(),
    )

    fun startScan() = bleScanner.start()
    fun stopScan()  = bleScanner.stop()

    override fun onCleared() {
        super.onCleared()
        bleScanner.stop()
    }

    private val deviceComparator = Comparator<DiscoveredBleDevice> { a, b ->
        val aHit = a.vendorMatch is VendorMatch.Hit
        val bHit = b.vendorMatch is VendorMatch.Hit
        when {
            aHit && !bHit -> -1
            !aHit && bHit ->  1
            else -> compareValues(b.rssi, a.rssi).let { if (it != 0) it else compareValues(a.firstSeenAt, b.firstSeenAt) }
        }
    }
}

data class ScanUiState(
    val devices: List<DiscoveredBleDevice> = emptyList(),
    val scanning: Boolean = false,
    val error: BleScanError? = null,
)
