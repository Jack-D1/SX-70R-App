package com.jack.sx_70r_app
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*
//@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity()  {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothGatt: BluetoothGatt? = null

    private val scannedDevices = mutableStateListOf<BluetoothDevice>()
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false

    companion object {
        const val REQUEST_ENABLE_BT = 1
        private const val SCAN_PERIOD: Long = 10000 // 10 seconds scan period
    }

    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allPermissionsGranted = true
            permissions.entries.forEach {
                if (!it.value) allPermissionsGranted = false
            }
            if (!allPermissionsGranted) {
                Toast.makeText(this, "Permissions not granted.", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                initializeBluetooth()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions()

        setContent {
            var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }

            if (selectedDevice == null) {
                DeviceScanUI(
                    scannedDevices = scannedDevices,
                    onDeviceSelected = { device ->
                        selectedDevice = device
                        connectToDevice(device.address)
                    },
                    onScanRequested = { scanForDevices() }
                )
            } else {
                ShutterControlUI(onShutterSpeedSelected = { command ->
                    sendShutterSpeedCommand(command)
                })
            }
        }
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestMultiplePermissions.launch(permissionsToRequest.toTypedArray())
        } else {
            initializeBluetooth()
        }
    }

    private fun initializeBluetooth() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }
    }


    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.i("BluetoothGattCallback", "Connected to GATT server.")
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return
                }
                gatt.discoverServices()
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.i("BluetoothGattCallback", "Disconnected from GATT server.")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(UUID.fromString("0000FFF0-0000-1000-8000-00805F9B34FB"))
                val writeCharacteristic = service.getCharacteristic(UUID.fromString("0000FFF2-0000-1000-8000-00805F9B34FB"))
                val readCharacteristic = service.getCharacteristic(UUID.fromString("0000FFF1-0000-1000-8000-00805F9B34FB"))

                // Enable notifications for the read characteristic
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return
                }
                gatt.setCharacteristicNotification(readCharacteristic, true)
                val descriptor = readCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            } else {
                Log.w("BluetoothGattCallback", "onServicesDiscovered received: $status")
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val data = characteristic.value
                val dataString = data?.let { String(it) }
                Log.i("onCharacteristicRead", "Characteristic Read: $dataString")

                runOnUiThread {
                    // Update UI with read data if needed
                }
            } else {
                Log.w("onCharacteristicRead", "Characteristic Read Failed with status: $status")
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("onCharacteristicWrite", "Characteristic Write Success")
            } else {
                Log.w("onCharacteristicWrite", "Characteristic Write Failed with status: $status")
            }
        }
    }

    private fun connectToDevice(deviceAddress: String) {
        val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private fun sendShutterSpeedCommand(command: ByteArray) {
        val service = bluetoothGatt?.getService(UUID.fromString("0000FFF0-0000-1000-8000-00805F9B34FB"))
        val writeCharacteristic = service?.getCharacteristic(UUID.fromString("0000FFF2-0000-1000-8000-00805F9B34FB"))
        writeCharacteristic?.let {
            it.value = command
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            bluetoothGatt?.writeCharacteristic(it)
        }
    }

    private fun scanForDevices() {
        if (isScanning) return

        handler.postDelayed({
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return@postDelayed
            }
            bluetoothAdapter.bluetoothLeScanner.stopScan(leScanCallback)
            isScanning = false
        }, SCAN_PERIOD)

        isScanning = true
        scannedDevices.clear()
        bluetoothAdapter.bluetoothLeScanner.startScan(leScanCallback)
    }

    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (!scannedDevices.contains(device)) {
                scannedDevices.add(device)
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            for (result in results) {
                val device = result.device
                if (!scannedDevices.contains(device)) {
                    scannedDevices.add(device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("MainActivity", "BLE Scan Failed with code $errorCode")
        }
    }

    @Composable
    fun ShutterControlUI(onShutterSpeedSelected: (ByteArray) -> Unit) {
        var exposureMode by remember { mutableStateOf("Manual") }
        var shutterSpeed by remember { mutableStateOf(10) } // Default shutter speed value

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "Select Exposure Mode:")
            Spacer(modifier = Modifier.height(8.dp))

            Row {
                RadioButton(
                    selected = exposureMode == "Auto",
                    onClick = { exposureMode = "Auto" }
                )
                Text(text = "Auto")
            }

            Row {
                RadioButton(
                    selected = exposureMode == "T",
                    onClick = { exposureMode = "T" }
                )
                Text(text = "T (Time)")
            }

            Row {
                RadioButton(
                    selected = exposureMode == "Manual",
                    onClick = { exposureMode = "Manual" }
                )
                Text(text = "Manual")
            }

            if (exposureMode == "Manual") {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Select Shutter Speed (ms):")
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = shutterSpeed.toFloat(),
                    onValueChange = { newValue ->
                        shutterSpeed = newValue.toInt()
                    },
                    valueRange = 1f..1000f,
                    steps = 9
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Selected Shutter Speed: $shutterSpeed ms")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                val command = when (exposureMode) {
                    "Auto" -> byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x00, 0x02) // Example command for Auto
                    "T" -> byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x00, 0x03) // Example command for T (Time)
                    else -> byteArrayOf(0xFF.toByte(), 0xFF.toByte(), (shutterSpeed shr 8).toByte(), shutterSpeed.toByte())
                }
                onShutterSpeedSelected(command)
            }) {
                Text(text = "Send Command")
            }
        }
    }

    @Composable
    fun DeviceScanUI(
        scannedDevices: List<BluetoothDevice>,
        onDeviceSelected: (BluetoothDevice) -> Unit,
        onScanRequested: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(text = "BLE Devices", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { onScanRequested() }) {
                Text(text = "Scan for Devices")
            }
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn {
                items(scannedDevices) { device ->
                    DeviceItem(device, onDeviceSelected)
                }
            }
        }
    }

    @Composable
    fun DeviceItem(device: BluetoothDevice, onDeviceSelected: (BluetoothDevice) -> Unit) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clickable { onDeviceSelected(device) },
            elevation = CardDefaults.cardElevation(
                defaultElevation = 4.dp
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return@Card
                }
                Text(text = device.name ?: "Unnamed Device")
                Text(text = device.address)
            }
        }
    }


}
