package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.DropLinkApp
import com.example.MainActivity
import com.example.R
import com.example.network.HashUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TransferForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "droplink_transfers"
        const val NOTIFICATION_ID = 4001
        const val ACTION_START_OR_UPDATE = "com.example.action.TRANSFER_SERVICE_START"
        const val ACTION_CANCEL_ALL = "com.example.action.TRANSFER_CANCEL_ALL"

        fun start(context: Context) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_START_OR_UPDATE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_ALL) {
            (application as? DropLinkApp)?.coordinator?.cancelAllActiveTransfers()
            stopSelf()
            return START_NOT_STICKY
        }

        val initial = buildNotification("DropLink", "جارٍ تجهيز النقل المحلي...", 0, indeterminate = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initial,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, initial)
        }

        observeTransfers()
        return START_NOT_STICKY
    }

    private fun observeTransfers() {
        observerJob?.cancel()
        val coordinator = (application as? DropLinkApp)?.coordinator ?: return
        observerJob = serviceScope.launch {
            coordinator.activeTasks.collectLatest { tasks ->
                val running = tasks.filter { it.status == com.example.model.TransferStatus.IN_PROGRESS }
                if (running.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val current = running.first()
                    val pct = (current.progress * 100).toInt().coerceIn(0, 100)
                    val title = if (running.size > 1) {
                        "${current.fileName} (+${running.size - 1})"
                    } else {
                        current.fileName
                    }
                    val body = "${pct}% • ${HashUtils.formatSpeed(current.speedBps)} • ${current.peerName}"
                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, buildNotification(title, body, pct, indeterminate = false))
                }
            }
        }
    }

    private fun buildNotification(
        title: String,
        body: String,
        progressPct: Int,
        indeterminate: Boolean
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val cancelIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TransferForegroundService::class.java).apply { action = ACTION_CANCEL_ALL },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressPct, indeterminate)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إلغاء / Cancel", cancelIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_transfers),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_transfers_desc)
            }
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        observerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
