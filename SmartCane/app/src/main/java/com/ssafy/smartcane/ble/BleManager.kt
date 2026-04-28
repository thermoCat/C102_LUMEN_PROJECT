package com.ssafy.smartcane.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import java.util.UUID

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "BleManager"
        val SERVICE_UUID: UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")
        private const val TARGET_DEVICE_NAME = "SmartCane_ESP32"
    }

    private val bluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var isConnected = false
    private var isScanning = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (result.device.name == TARGET_DEVICE_NAME) {
                Log.d(TAG, "ESP32 found, connecting...")
                stopScan()
                connectToDevice(result.device)
            }
        }
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed: $errorCode")
            isScanning = false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "BLE connected")
                    isConnected = true
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "BLE disconnected")
                    isConnected = false
                    txCharacteristic = null
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    startScan()
                }
            }
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                txCharacteristic = gatt.getService(SERVICE_UUID)
                    ?.getCharacteristic(CHARACTERISTIC_UUID)
                Log.d(TAG, "Services discovered, characteristic: ${txCharacteristic != null}")
            } else {
                Log.e(TAG, "Service discovery failed: $status")
            }
        }
    }

    fun startScan() {
        if (isScanning || bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder().setDeviceName(TARGET_DEVICE_NAME).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        isScanning = true
        scanner.startScan(listOf(filter), settings, scanCallback)
        Log.d(TAG, "BLE scan started")
    }

    private fun stopScan() {
        if (!isScanning) return
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
    }

    private fun connectToDevice(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    object VibCmd {
        const val CENTER: Byte = 0x01
        const val LEFT:   Byte = 0x02
        const val RIGHT:  Byte = 0x03
        const val STOP:   Byte = 0x04
    }

    fun sendState(state: String) {
        val cmd = when (state) {
            "CENTER" -> VibCmd.CENTER
            "LEFT"   -> VibCmd.LEFT
            "RIGHT"  -> VibCmd.RIGHT
            "STOP"   -> VibCmd.STOP
            else     -> return
        }
        sendBytes(byteArrayOf(cmd))
    }

    private fun sendBytes(bytes: ByteArray) {
        val gatt = bluetoothGatt ?: return
        val char = txCharacteristic ?: return
        if (!isConnected) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        } else {
            @Suppress("DEPRECATION")
            char.value = bytes
            @Suppress("DEPRECATION")
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(char)
        }
    }

    fun disconnect() {
        stopScan()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }
}
