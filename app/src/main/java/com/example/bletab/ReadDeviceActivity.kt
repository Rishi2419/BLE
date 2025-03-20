package com.example.bletab

import android.Manifest
import android.bluetooth.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.bletab.databinding.ActivityReadDeviceBinding
import java.util.*

class ReadDeviceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReadDeviceBinding
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothGatt: BluetoothGatt? = null

    private val SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
    private val ADMIN_KEY_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    private val SEARCH_TEXT_UUID = UUID.fromString("00002a1a-0000-1000-8000-00805f9b34fb")
    private val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val TAG = "ReadDeviceActivity"
    private val BLUETOOTH_PERMISSIONS_REQUEST_CODE = 1001

    private val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Enable ViewBinding
        binding = ActivityReadDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val deviceAddress = intent.getStringExtra("DEVICE_ADDRESS") ?: return
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        updatePatientDetailButtonState()

        binding.editBtn.setOnClickListener{
            val intent = Intent(this, WriteDeviceActivity::class.java)
            intent.putExtra("DEVICE_ADDRESS", deviceAddress)
            startActivity(intent)
        }

        binding.patientDetail.setOnClickListener{
            val intent = Intent(this, PatientdetailActivity::class.java)
            intent.putExtra("DEVICE_ADDRESS", deviceAddress)
            startActivity(intent)
        }

        // ✅ Check & Request Bluetooth Permissions
        if (checkAndRequestBluetoothPermissions()) {
            try {
                val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
                connectToDevice(device)
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException: ${e.message}")
                Toast.makeText(this, "Permission denied for Bluetooth access", Toast.LENGTH_SHORT).show()
            }
        }

        binding.disconnectButton.setOnClickListener {
            disconnectFromDevice()
        }
    }

    private fun checkAndRequestBluetoothPermissions(): Boolean {
        val missingPermissions = bluetoothPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        return if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions, BLUETOOTH_PERMISSIONS_REQUEST_CODE)
            false
        } else {
            true
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (checkPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            try {
                bluetoothGatt = device.connectGatt(this, false, gattCallback)
                Log.d(TAG, "Attempting to connect to device: ${device.address}")
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception connecting to device: ${e.message}")
                Toast.makeText(this, "Bluetooth permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread { Toast.makeText(this@ReadDeviceActivity, "Connected!", Toast.LENGTH_SHORT).show() }
                try {
                    gatt.discoverServices()
                } catch (e: SecurityException) {
                    Log.e(TAG, "Security exception discovering services: ${e.message}")
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                try {
                    disableNotifications(gatt, ADMIN_KEY_UUID)
                    disableNotifications(gatt, SEARCH_TEXT_UUID)
                    gatt.close()
                    bluetoothGatt = null
                } catch (e: SecurityException) {
                    Log.e(TAG, "Security exception during disconnect: ${e.message}")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    // Only enable the first characteristic notification here
                    enableNotifications(gatt, ADMIN_KEY_UUID)
                    // The second will be enabled in onDescriptorWrite
                } else {
                    Log.e("rishi_ble", "Service $SERVICE_UUID not found!")
                }
            }
        }

        // Add this callback to handle descriptor write completion
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val characteristicUUID = descriptor.characteristic.uuid
                Log.d("rishi_ble", "Descriptor write successful for $characteristicUUID")

                // After successfully enabling ADMIN_KEY notifications, enable SEARCH_TEXT notifications
                if (characteristicUUID == ADMIN_KEY_UUID) {
                    enableNotifications(gatt, SEARCH_TEXT_UUID)
                }
            } else {
                Log.e("rishi_ble", "Descriptor write failed for ${descriptor.characteristic.uuid}, status: $status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicChanged(characteristic, value)
        }

        @Deprecated("Deprecated in API level 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            handleCharacteristicChanged(characteristic, characteristic.value)
        }
    }

    private fun handleCharacteristicChanged(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        val stringValue = String(value)
        Log.d(TAG, "Received from ${characteristic.uuid}: $stringValue")

        // Save values to SharedPreferences as they're received
        val sharedPreferences = getSharedPreferences("BLEData", MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        runOnUiThread {
            when (characteristic.uuid) {
//                ADMIN_KEY_UUID -> binding.adminKeyText.text = "Admin Key: $stringValue"
//                SEARCH_TEXT_UUID -> binding.searchText.text = "Search Text: $stringValue"

                ADMIN_KEY_UUID -> {
                    binding.adminKeyText.text = "Admin Key: $stringValue"
                    editor.putString("ADMIN_KEY", stringValue)
                    editor.apply()
                    updatePatientDetailButtonState()
                }
                SEARCH_TEXT_UUID -> {
                    binding.searchText.text = "Search Text: $stringValue"
                    editor.putString("SEARCH_TEXT", stringValue)
                    editor.apply()
                    updatePatientDetailButtonState()
                }
            }
        }
    }

    private fun updatePatientDetailButtonState() {
        val sharedPreferences = getSharedPreferences("BLEData", MODE_PRIVATE)
        val adminKey = sharedPreferences.getString("ADMIN_KEY", null)
        val searchText = sharedPreferences.getString("SEARCH_TEXT", null)

        // Enable the Patient Detail button only if both values are present
        binding.patientDetail.isEnabled = !adminKey.isNullOrEmpty() && !searchText.isNullOrEmpty()
    }

    private fun enableNotifications(gatt: BluetoothGatt, characteristicUUID: UUID) {
        try {
            val characteristic = gatt.getService(SERVICE_UUID)?.getCharacteristic(characteristicUUID)
            characteristic?.let {
                gatt.setCharacteristicNotification(it, true)
                val descriptor = it.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                descriptor?.let {
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(it)
                    Log.d(TAG, "Enabled notifications for $characteristicUUID")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception enabling notifications: ${e.message}")
        }
    }

    private fun disableNotifications(gatt: BluetoothGatt, characteristicUUID: UUID) {
        try {
            val characteristic = gatt.getService(SERVICE_UUID)?.getCharacteristic(characteristicUUID)
            characteristic?.let {
                gatt.setCharacteristicNotification(it, false)
                val descriptor = it.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                descriptor?.let {
                    it.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(it)
                    Log.d(TAG, "Disabled notifications for $characteristicUUID")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception disabling notifications: ${e.message}")
        }
    }

    private fun disconnectFromDevice() {
        bluetoothGatt?.let { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
                bluetoothGatt = null
                Toast.makeText(this, "Disconnected!", Toast.LENGTH_SHORT).show()
                navigateToMainActivity()
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception disconnecting: ${e.message}")
            }
        }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }


}
