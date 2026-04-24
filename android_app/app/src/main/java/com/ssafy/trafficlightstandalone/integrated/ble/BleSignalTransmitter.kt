package com.ssafy.trafficlightstandalone.integrated.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.ssafy.trafficlightstandalone.integrated.model.InferenceResult
import com.ssafy.trafficlightstandalone.integrated.model.TrafficLightState
import java.util.UUID

/**
 * BLE transmitter that sends a one-byte signal ("G" / "R" / "N") to an ESP32
 * whenever the detected traffic-light state changes.
 *
 * ESP32 Arduino sketch must advertise:
 *   Service UUID      : 12345678-1234-1234-1234-123456789abc
 *   Characteristic UUID: 87654321-4321-4321-4321-cba987654321  (WRITE)
 *   Device name       : ESP32_Traffic
 *
 * Signals: "G" = green, "R" = red, "N" = none detected, "C" = connected
 */
@SuppressLint("MissingPermission")
class BleSignalTransmitter(
    private val context: Context,
    private val onStatusChanged: (String) -> Unit,
    private val onError: (Throwable) -> Unit,
) {
    companion object {
        const val TARGET_DEVICE_NAME = "ESP32_Traffic"
        val SERVICE_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("87654321-4321-4321-4321-cba987654321")
        private const val SIGNAL_GREEN = "G"
        private const val SIGNAL_RED = "R"
        private const val SIGNAL_NONE = "N"
        private const val SIGNAL_CONNECTED = "C"
        private const val SCAN_TIMEOUT_MS = 12_000L
        private const val RECONNECT_DELAY_MS = 3_000L
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter = bluetoothManager?.adapter

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var isScanning = false
    private var lastSignal = ""
    private val mainHandler = Handler(Looper.getMainLooper())

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun start() {
        if (gatt != null) return
        startScan()
    }

    fun onInferenceResult(result: InferenceResult) {
        val signal = when (result.trafficLightState) {
            TrafficLightState.GREEN -> SIGNAL_GREEN
            TrafficLightState.RED -> SIGNAL_RED
            TrafficLightState.NONE -> SIGNAL_NONE
        }
        if (signal != lastSignal) {
            lastSignal = signal
            sendSignal(signal)
        }
    }

    fun close() {
        mainHandler.removeCallbacksAndMessages(null)
        stopScan()
        gatt?.close()
        gatt = null
        writeChar = null
    }

    private fun startScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: run {
            notifyStatus("BLE 스캐너를 사용할 수 없습니다")
            return
        }
        isScanning = true
        notifyStatus("ESP32 장치 스캔 중… ($TARGET_DEVICE_NAME)")

        mainHandler.postDelayed({
            if (isScanning) {
                stopScan()
                notifyStatus("장치를 찾지 못했습니다. 재시도 중…")
                mainHandler.postDelayed({ startScan() }, RECONNECT_DELAY_MS)
            }
        }, SCAN_TIMEOUT_MS)

        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(null, settings, scanCallback)
    }

    private fun stopScan() {
        if (!isScanning) return
        isScanning = false
        try { bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback) } catch (_: Exception) {}
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: return
            if (name == TARGET_DEVICE_NAME) {
                stopScan()
                connect(result.device)
            }
        }
        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            notifyStatus("BLE 스캔 실패 (코드 $errorCode)")
        }
    }

    private fun connect(device: BluetoothDevice) {
        notifyStatus("ESP32 연결 중…")
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    notifyStatus("ESP32 연결됨 – 서비스 검색 중…")
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    writeChar = null
                    gatt?.close()
                    gatt = null
                    lastSignal = ""
                    notifyStatus("ESP32 연결 끊김 – ${RECONNECT_DELAY_MS / 1000}초 후 재시도")
                    mainHandler.postDelayed({ startScan() }, RECONNECT_DELAY_MS)
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                notifyStatus("서비스 검색 실패 (상태 $status)")
                return
            }
            writeChar = g.getService(SERVICE_UUID)?.getCharacteristic(CHARACTERISTIC_UUID)
            if (writeChar != null) {
                sendSignal(SIGNAL_CONNECTED)
                notifyStatus("ESP32 준비 완료 ✓ 신호 전송 대기 중")
            } else {
                notifyStatus("특성을 찾을 수 없습니다 – UUID를 확인하세요")
            }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {}
    }

    private fun sendSignal(signal: String) {
        val g = gatt ?: return
        val ch = writeChar ?: return
        val data = signal.toByteArray(Charsets.UTF_8)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(ch, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            ch.value = data
            @Suppress("DEPRECATION")
            g.writeCharacteristic(ch)
        }
    }

    private fun notifyStatus(message: String) {
        mainHandler.post { onStatusChanged(message) }
    }
}
