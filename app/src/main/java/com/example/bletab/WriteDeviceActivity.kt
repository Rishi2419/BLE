package com.example.bletab

import android.bluetooth.*
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.bletab.databinding.ActivityWriteDeviceBinding
import java.util.*

class WriteDeviceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWriteDeviceBinding

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothGatt: BluetoothGatt? = null
    private var deviceAddress: String? = null

    private val TAG = "WriteDeviceActivity"

    // BLE UUIDs - make sure these match the ones in DeviceActivity
    private val SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
    private val ADMIN_KEY_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    private val SEARCH_TEXT_UUID = UUID.fromString("00002a1a-0000-1000-8000-00805f9b34fb")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWriteDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get device address from intent
        deviceAddress = intent.getStringExtra("DEVICE_ADDRESS")
        if (deviceAddress == null) {
            Toast.makeText(this, "No device address provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize Bluetooth
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Connect to the device
        try {
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            connectToDevice(device)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception connecting to device: ${e.message}")
            Toast.makeText(this, "Bluetooth permission denied", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to device: ${e.message}")
            Toast.makeText(this, "Error connecting to device", Toast.LENGTH_SHORT).show()
            finish()
        }

        // Set up Save button
        binding.saveBtn.setOnClickListener {
            val adminKey = binding.adminKeyEdit.text.toString()
            val searchText = binding.searchTextEdit.text.toString()

            if (adminKey.isBlank() || searchText.isBlank()) {
                Toast.makeText(this, "Please enter both values", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            writeValuesToDevice(adminKey, searchText)
        }

        // Set up Back button
        binding.backBtn.setOnClickListener {
            disconnectAndGoBack()
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        try {
            Toast.makeText(this, "Connecting to ${device.address}...", Toast.LENGTH_SHORT).show()
            bluetoothGatt = device.connectGatt(this, false, gattCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception: ${e.message}")
            Toast.makeText(this, "Bluetooth permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread {
                    Toast.makeText(this@WriteDeviceActivity, "Connected to device", Toast.LENGTH_SHORT).show()
                }
                try {
                    gatt.discoverServices()
                } catch (e: SecurityException) {
                    Log.e(TAG, "Security exception discovering services: ${e.message}")
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread {
                    Toast.makeText(this@WriteDeviceActivity, "Disconnected from device", Toast.LENGTH_SHORT).show()
                }
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                try {
                    // Read current values if possible
                    readCharacteristic(gatt, ADMIN_KEY_UUID)
                    // We'll read the search text after admin key is read
                } catch (e: SecurityException) {
                    Log.e(TAG, "Security exception reading characteristics: ${e.message}")
                }
            } else {
                Log.e(TAG, "Service discovery failed: $status")
                runOnUiThread {
                    Toast.makeText(this@WriteDeviceActivity, "Failed to discover services", Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            // For Android 13+ (API 33+)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val stringValue = String(value)
                handleCharacteristicRead(gatt, characteristic, stringValue)
            }
        }

        // For older Android versions
        @Deprecated("Deprecated in API level 33")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            // For Android 12 and below
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val stringValue = characteristic.getStringValue(0)
                handleCharacteristicRead(gatt, characteristic, stringValue ?: "")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val charName = when(characteristic.uuid) {
                ADMIN_KEY_UUID -> "Admin Key"
                SEARCH_TEXT_UUID -> "Search Text"
                else -> "Unknown"
            }
            Log.d("rishi_writeCharacteristic", "Hello")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("rishi_writeCharacteristic", "$charName written successfully")
                runOnUiThread {
                    Toast.makeText(this@WriteDeviceActivity, "$charName updated", Toast.LENGTH_SHORT).show()
                }

                // If we wrote the admin key, write the search text next
                if (characteristic.uuid == ADMIN_KEY_UUID) {
                    try {
                        val searchText = binding.searchTextEdit.text.toString()
                        writeCharacteristic(gatt, SEARCH_TEXT_UUID, searchText)
                    } catch (e: SecurityException) {
                        Log.e("rishi_writeCharacteristic", "Security exception writing search text: ${e.message}")
                    }
                } else if (characteristic.uuid == SEARCH_TEXT_UUID) {
                    // Both values written successfully
                    runOnUiThread {
                        Toast.makeText(this@WriteDeviceActivity, "Values saved successfully", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Log.e("rishi_writeCharacteristic", "Failed to write $charName: $status")
                runOnUiThread {
                    Toast.makeText(this@WriteDeviceActivity, "Failed to update $charName", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: String) {
        when (characteristic.uuid) {
            ADMIN_KEY_UUID -> {
                Log.d(TAG, "Read Admin Key: $value")
                runOnUiThread {
                    binding.adminKeyEdit.setText(value)
                    // After reading admin key, read search text
                    readCharacteristic(gatt, SEARCH_TEXT_UUID)
                }
            }
            SEARCH_TEXT_UUID -> {
                Log.d(TAG, "Read Search Text: $value")
                runOnUiThread {
                    binding.searchTextEdit.setText(value)
                }
            }
        }
    }

    private fun readCharacteristic(gatt: BluetoothGatt, characteristicUUID: UUID) {
        try {
            val service = gatt.getService(SERVICE_UUID)
            val characteristic = service?.getCharacteristic(characteristicUUID)

            if (characteristic != null) {
                gatt.readCharacteristic(characteristic)
            } else {
                Log.e(TAG, "Characteristic $characteristicUUID not found")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception: ${e.message}")
        }
    }

    private fun writeValuesToDevice(adminKey: String, searchText: String) {
        try {
            bluetoothGatt?.let { gatt ->
                // Write Admin Key first, then in the callback we'll write Search Text
                writeCharacteristic(gatt, ADMIN_KEY_UUID, adminKey)
            } ?: run {
                Toast.makeText(this, "Not connected to device", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing values: ${e.message}")
            Toast.makeText(this, "Error writing values to device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun writeCharacteristic(gatt: BluetoothGatt, characteristicUUID: UUID, value: String) {
        try {
            val service = gatt.getService(SERVICE_UUID)
            val characteristic = service?.getCharacteristic(characteristicUUID)

            if (characteristic != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // API 33+ method

                    gatt.writeCharacteristic(characteristic, value.toByteArray(), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                    Log.d("rishi_writeCharacteristic", "Writing to $characteristicUUID: $value")
                } else {
                    // Legacy method
                    characteristic.setValue(value)
                    gatt.writeCharacteristic(characteristic)
                }

            } else {
                Log.e("rishi_writeCharacteristic", "Characteristic $characteristicUUID not found")
                Toast.makeText(this, "Characteristic not found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Log.e("rishi_writeCharacteristic", "Security exception: ${e.message}")
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun disconnectAndGoBack() {
        try {
            bluetoothGatt?.let { gatt ->
                gatt.disconnect()
                gatt.close()
                bluetoothGatt = null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception disconnecting: ${e.message}")
        } finally {
            // Return to MainActivity
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            bluetoothGatt?.close()
            bluetoothGatt = null
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception in onDestroy: ${e.message}")
        }
    }
}