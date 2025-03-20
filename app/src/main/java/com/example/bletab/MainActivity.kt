package com.example.bletab

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.bletab.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bleScanner: BluetoothLeScanner
    private lateinit var scanCallback: ScanCallback

    private val devicesList = mutableListOf<String>()
    private val handler = Handler(Looper.getMainLooper())
    private val SCAN_PERIOD: Long = 10000 // 10 seconds
    private val devicesMap = mutableMapOf<String, BluetoothDevice>()
    private var bluetoothGatt: BluetoothGatt? = null

    private val TAG = "MainActivity"
    private val BLUETOOTH_PERMISSIONS_REQUEST_CODE = 1001

    // Required permissions based on Android version
    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Bluetooth components
        try {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothAdapter = bluetoothManager.adapter

            // Initialize scanner only if permissions are granted
            if (checkPermissions()) {
                bleScanner = bluetoothAdapter.bluetoothLeScanner
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Bluetooth: ${e.message}")
            Toast.makeText(this, "Bluetooth not available", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Setup ListView adapter
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, devicesList)
        binding.bleDeviceList.adapter = adapter

        // Scan Callback
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                result?.device?.let { device ->
                    try {
                        val deviceName = device.name
                        if (!deviceName.isNullOrBlank()) {  // Only add devices with valid names
                            val deviceInfo = "$deviceName - ${device.address}"
                            Log.d(TAG, "Found device: $deviceInfo")
                            if (!devicesList.contains(deviceInfo)) {
                                devicesList.add(deviceInfo)
                                devicesMap[deviceInfo] = device
                                runOnUiThread { adapter.notifyDataSetChanged() }
                            }
                            else{

                            }
                        }
                        else{

                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Security exception accessing device: ${e.message}")
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.e(TAG, "Scan failed with error code: $errorCode")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Scan failed: $errorCode", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Scan Button Click Listener
        binding.scanButton.setOnClickListener {
            if (checkPermissions()) {
                scanLeDevice()
            } else {
                requestPermissions()
            }
        }

        // List Item Click Listener (For Device Connection)
        binding.bleDeviceList.setOnItemClickListener { _, _, position, _ ->
            if (checkPermissions()) {
                try {
                    val selectedDeviceInfo = devicesList[position]
                    val deviceAddress = selectedDeviceInfo.split(" - ")[1] // Extract BLE address

                    val intent = Intent(this, ReadDeviceActivity::class.java)
                    intent.putExtra("DEVICE_ADDRESS", deviceAddress)
                    startActivity(intent)
                } catch (e: SecurityException) {
                    Log.e(TAG, "Security exception on list item click: ${e.message}")
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                }
            } else {
                requestPermissions()
            }
        }
    }

    private fun checkPermissions(): Boolean {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        return missingPermissions.isEmpty()
    }

    private fun requestPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions,
                BLUETOOTH_PERMISSIONS_REQUEST_CODE
            )
        }
    }

    private fun scanLeDevice() {
        if (!checkPermissions()) {
            Log.e(TAG, "Cannot scan: missing permissions")
            Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_SHORT).show()
            requestPermissions()
            return
        }

        try {
            if (!::bleScanner.isInitialized) {
                bleScanner = bluetoothAdapter.bluetoothLeScanner
            }

            // Clear previous results
            devicesList.clear()
            devicesMap.clear()
            runOnUiThread { (binding.bleDeviceList.adapter as ArrayAdapter<*>).notifyDataSetChanged() }

            // Stop scan after SCAN_PERIOD
            handler.postDelayed({
                try {
                    bleScanner.stopScan(scanCallback)
                    Toast.makeText(this, "Scan Completed", Toast.LENGTH_SHORT).show()
                } catch (e: SecurityException) {
                    Log.e(TAG, "Security exception stopping scan: ${e.message}")
                }
            }, SCAN_PERIOD)

            // Start scanning
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            bleScanner.startScan(null, scanSettings, scanCallback)
            Toast.makeText(this, "Scanning...", Toast.LENGTH_SHORT).show()

        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception during scan: ${e.message}")
            Toast.makeText(this, "Bluetooth permission denied", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning: ${e.message}")
            Toast.makeText(this, "Error starting scan", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == BLUETOOTH_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                scanLeDevice()
            } else {
                Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::bleScanner.isInitialized && checkPermissions()) {
                bleScanner.stopScan(scanCallback)
            }
            bluetoothGatt?.close()
            bluetoothGatt = null
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception in onDestroy: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy: ${e.message}")
        }
    }
}
