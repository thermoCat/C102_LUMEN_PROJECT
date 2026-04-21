package com.ssafy.smartcane.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

@SuppressLint("MissingPermission")
class BleNusManager(private val context: Context) {

    companion object {
        private const val TAG = "BleNusManager"
        private const val PREF_NAME = "ble_nus_prefs"
        private const val KEY_DEVICE_ID = "saved_device_id"

        val SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val RX_UUID: UUID      = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        val TX_UUID: UUID      = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        val CCCD_UUID: UUID    = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        private const val DEVICE_NAME = "ESP32C3-LR-Control"
        private const val SCAN_TIMEOUT_MS = 8_000L
    }

    enum class ConnectionState { DISCONNECTED, SCANNING, CONNECTING, CONNECTED }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedName = MutableStateFlow("")
    val connectedName: StateFlow<String> = _connectedName.asStateFlow()

    private val _log = MutableStateFlow("앱 시작")
    val log: StateFlow<String> = _log.asStateFlow()

    private val bluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var gatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var scanTimeoutRunnable: Runnable? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun saveDeviceId(id: String) = prefs.edit().putString(KEY_DEVICE_ID, id).apply()
    fun getSavedDeviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)
    fun clearSavedDeviceId() {
        prefs.edit().remove(KEY_DEVICE_ID).apply()
        appendLog("저장된 deviceId 삭제 완료")
    }

    private fun appendLog(msg: String) {
        Log.d(TAG, msg)
        _log.value = msg
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    appendLog("GATT 연결됨, 서비스 탐색 중...")
                    _connectionState.value = ConnectionState.CONNECTING
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    appendLog("연결 해제됨 (status=$status)")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _connectedName.value = ""
                    rxCharacteristic = null
                    gatt?.close()
                    gatt = null
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                appendLog("서비스 탐색 실패: $status"); return
            }
            rxCharacteristic = g.getService(SERVICE_UUID)?.getCharacteristic(RX_UUID)
            val txChar = g.getService(SERVICE_UUID)?.getCharacteristic(TX_UUID)
            if (rxCharacteristic == null || txChar == null) {
                appendLog("NUS 특성을 찾을 수 없음"); return
            }
            g.setCharacteristicNotification(txChar, true)
            val cccd = txChar.getDescriptor(CCCD_UUID)
            if (cccd != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(cccd)
                }
            }
            val name = g.device.name ?: g.device.address
            _connectedName.value = name
            _connectionState.value = ConnectionState.CONNECTED
            saveDeviceId(g.device.address)
            appendLog("연결 성공: $name")
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray
        ) { appendLog("ESP 응답: ${value.toString(Charsets.UTF_8)}") }

        @Suppress("DEPRECATION")
        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                val decoded = characteristic.value?.toString(Charsets.UTF_8) ?: return
                appendLog("ESP 응답: $decoded")
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) appendLog("쓰기 실패: $status")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val matched = result.device.name == DEVICE_NAME ||
                result.scanRecord?.deviceName == DEVICE_NAME ||
                result.scanRecord?.serviceUuids?.any {
                    it.uuid.toString().uppercase() == SERVICE_UUID.toString().uppercase()
                } == true
            if (!matched) return
            appendLog("기기 발견: ${result.device.name ?: result.device.address}")
            stopScan()
            connectToDevice(result.device)
        }
        override fun onScanFailed(errorCode: Int) {
            appendLog("스캔 실패: $errorCode")
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    private fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            appendLog("블루투스가 꺼져 있습니다"); return
        }
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: run {
            appendLog("BLE 스캐너를 가져올 수 없습니다"); return
        }
        _connectionState.value = ConnectionState.SCANNING
        appendLog("BLE 스캔 시작...")
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(listOf(filter), settings, scanCallback)
        scanTimeoutRunnable = Runnable {
            stopScan()
            if (_connectionState.value == ConnectionState.SCANNING) {
                _connectionState.value = ConnectionState.DISCONNECTED
                appendLog("스캔 시간 초과 (8초)")
            }
        }.also { mainHandler.postDelayed(it, SCAN_TIMEOUT_MS) }
    }

    private fun stopScan() {
        scanTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        scanTimeoutRunnable = null
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    private fun connectToDevice(device: BluetoothDevice) {
        appendLog("연결 시도: ${device.address}")
        _connectionState.value = ConnectionState.CONNECTING
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun connect() {
        if (_connectionState.value != ConnectionState.DISCONNECTED) return
        val savedId = getSavedDeviceId()
        if (savedId != null) {
            appendLog("저장된 기기 재연결 시도: $savedId")
            val device = try { bluetoothAdapter?.getRemoteDevice(savedId) } catch (e: Exception) { null }
            if (device != null) { connectToDevice(device); return }
        }
        startScan()
    }

    fun sendCommand(cmd: String) {
        val g = gatt; val char = rxCharacteristic
        if (g == null || char == null || _connectionState.value != ConnectionState.CONNECTED) {
            appendLog("연결되지 않음 — 재연결 후 다시 시도하세요"); return
        }
        val bytes = cmd.toByteArray(Charsets.UTF_8)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        } else {
            @Suppress("DEPRECATION")
            char.value = bytes
            @Suppress("DEPRECATION")
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            g.writeCharacteristic(char)
        }
        appendLog("명령 전송: $cmd")
    }

    fun disconnect() {
        stopScan()
        gatt?.disconnect(); gatt?.close(); gatt = null
        rxCharacteristic = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedName.value = ""
        appendLog("연결 해제 완료")
    }
}
