package com.jhaiian.clint.vpn

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
import androidx.core.app.ServiceCompat
import com.jhaiian.clint.R
import com.jhaiian.clint.browser.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the process, and with it the in-process WireGuard tunnel, alive while the VPN is on, and
 * gives the user a notification to see and end it from.
 */
class VpnForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            BrowserVpn.disconnect()
            return START_NOT_STICKY
        }
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(BrowserVpn.state.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else 0
        )
        stateJob?.cancel()
        stateJob = scope.launch {
            BrowserVpn.state.collect { state ->
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(state))
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(state: BrowserVpnState): android.app.Notification {
        val text = when (state) {
            is BrowserVpnState.Connected -> getString(R.string.vpn_notification_connected, state.country.name)
            is BrowserVpnState.Connecting -> getString(R.string.vpn_status_connecting)
            BrowserVpnState.Disconnecting -> getString(R.string.vpn_status_disconnecting)
            else -> getString(R.string.vpn_status_off)
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val disconnect = PendingIntent.getService(
            this, 1,
            Intent(this, VpnForegroundService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_24)
            .setContentTitle(getString(R.string.vpn_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.vpn_disconnect), disconnect)
            .build()
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.vpn_title), NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        private const val CHANNEL_ID = "aethernet_vpn"
        private const val NOTIFICATION_ID = 7_100
        private const val ACTION_DISCONNECT = "com.jhaiian.clint.vpn.DISCONNECT"

        fun startIntent(context: Context) = Intent(context, VpnForegroundService::class.java)

        fun stop(context: Context) {
            context.stopService(Intent(context, VpnForegroundService::class.java))
        }
    }
}
