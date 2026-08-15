package com.wulisu.licenseoverlay.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import com.wulisu.licenseoverlay.MainActivity
import com.wulisu.licenseoverlay.R
import com.wulisu.licenseoverlay.overlay.OverlayController

class OverlayService : Service() {
    private lateinit var overlay: OverlayController
    private lateinit var powerManager: PowerManager
    private var receiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!::overlay.isInitialized) return
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> overlay.setScreenActive(true)
                Intent.ACTION_SCREEN_OFF -> overlay.setScreenActive(false)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        createNotificationChannel()
        startAsForeground()

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        powerManager = getSystemService(PowerManager::class.java)
        overlay = OverlayController(this)
        registerScreenReceiver()
        overlay.setScreenActive(powerManager.isInteractive)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (::overlay.isInitialized && ::powerManager.isInitialized) {
            overlay.setScreenActive(powerManager.isInteractive)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (receiverRegistered) runCatching { unregisterReceiver(screenReceiver) }
        receiverRegistered = false
        if (::overlay.isInitialized) overlay.destroy()
        INSTANCE = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerScreenReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun startAsForeground() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = android.app.Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("激活助手·悬浮窗版")
            .setContentText("悬浮球运行中；亮屏时常驻所有页面")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "license_overlay_only_service"
        private const val NOTIFICATION_ID = 2101
        @Volatile var INSTANCE: OverlayService? = null
            private set
    }
}
