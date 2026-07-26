package com.example.airpodsbattery

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.airpodsbattery.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) startScanService()
        else binding.tvStatus.text = getString(R.string.permission_denied)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnStart.setOnClickListener { checkPermissionsAndStart() }
        binding.btnStop.setOnClickListener {
            stopService(Intent(this, AirPodsScanService::class.java))
            binding.tvStatus.text = getString(R.string.stopped)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AirPodsStatusHolder.status.collect { updateUi(it) }
            }
        }

        checkPermissionsAndStart()
    }

    private fun checkPermissionsAndStart() {
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            binding.tvStatus.text = getString(R.string.bluetooth_off)
            return
        }
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed += Manifest.permission.BLUETOOTH_SCAN
            needed += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        val toRequest = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isEmpty()) startScanService() else permissionLauncher.launch(toRequest.toTypedArray())
    }

    private fun startScanService() {
        ContextCompat.startForegroundService(this, Intent(this, AirPodsScanService::class.java))
        binding.tvStatus.text = getString(R.string.scanning)
    }

    private fun updateUi(status: AirPodsStatus?) {
        if (status == null) {
            binding.tvModel.text = getString(R.string.not_detected)
            binding.tvLeft.text = "-"
            binding.tvRight.text = "-"
            binding.tvCase.text = "-"
            return
        }
        binding.tvModel.text = status.model
        binding.tvLeft.text = if (status.leftBattery >= 0) "${status.leftBattery}%" else "-"
        binding.tvRight.text = if (status.rightBattery >= 0) "${status.rightBattery}%" else "-"
        binding.tvCase.text = if (status.caseBattery >= 0) "${status.caseBattery}%" else "-"
        binding.tvStatus.text = getString(R.string.connected)
    }
}
