package com.example.airpodsbattery

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

/**
 * 화면이 꺼져 있어도 계속 BLE 신호를 스캔하기 위한 포그라운드 서비스.
 * 상태 표시줄 알림에 항상 최신 배터리 정보를 띄웁니다.
 */
class AirPodsScanService : Service() {

    companion object {
        private const val CHANNEL_ID = "airpods_battery_channel"
        private const val NOTIFICATION_ID = 1001
        private const val STALE_TIMEOUT_MS = 45_000L
        private const val APPLE_MANUFACTURER_ID = 76
    }

    private val handler = Handler(Looper.getMainLooper())

    private val staleCheckRunnable = object : Runnable {
        override fun run() {
            val status = AirPodsStatusHolder.status.value
            if (status != null && System.currentTimeMillis() - status.timestamp > STALE_TIMEOUT_MS) {
                AirPodsStatusHolder.update(null)
                updateNotification(null)
            }
            handler.postDelayed(this, 10_000L)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord ?: return
            val data = record.getManufacturerSpecificData(APPLE_MANUFACTURER_ID) ?: return
            val status = AirPodsParser.parse(data) ?: return
            AirPodsStatusHolder.update(status)
            updateNotification(status)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        startScanning()
        handler.postDelayed(staleCheckRunnable, 10_000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        stopScanning()
        handler.removeCallbacks(staleCheckRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startScanning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf(); return
        }
        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter ?: run { stopSelf(); return }
        val scanner = adapter.bluetoothLeScanner ?: run { stopSelf(); return }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner.startScan(null, settings, scanCallback)
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private fun stopScanning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED
        ) return
        try {
            val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            manager.adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            // 무시
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(status: AirPodsStatus?): Notification {
        val contentText = if (status == null) {
            getString(R.string.waiting_for_airpods)
        } else buildString {
            append(status.model).append(" · ")
            if (status.leftBattery >= 0) append("L ${status.leftBattery}%${if (status.leftCharging) "⚡" else ""} ")
            if (status.rightBattery >= 0) append("R ${status.rightBattery}%${if (status.rightCharging) "⚡" else ""} ")
            if (status.caseBattery >= 0) append("Case ${status.caseBattery}%${if (status.caseCharging) "⚡" else ""}")
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(status: AirPodsStatus?) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(status))
    }
}
