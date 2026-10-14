package com.example.voxara.phone

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.voxara.phone.ui.titleRes
import com.example.voxara.sync.MirroredAlert
import java.text.DateFormat
import java.util.Date

/**
 * OPTIONAL COPY OF THE WATCH'S 100% ALERTS on the phone (off by default). The watch has already
 * vibrated and notified; this is a courtesy for when the phone is in your hand.
 *
 * No duplicates: one notification per alert kind and level (a repeat replaces it), each alert at
 * most once, stale deliveries dropped ([MirroredAlert.shouldShow]), and the copy is local-only so
 * Wear OS never bridges it back to the watch as a second alert.
 */
object MirroredAlerts {

    const val CHANNEL_ID = "voxara_mirrored_limits"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                PhoneLocale.localized(context).getString(R.string.channel_mirror),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
    }

    suspend fun onLimitAlert(context: Context, bytes: ByteArray) {
        val alert = MirroredAlert.decode(bytes) ?: return
        PhoneGraph.init(context)
        val store = PhoneGraph.store
        val s = store.read()
        if (!MirroredAlert.shouldShow(alert, s.mirrorAlerts, s.lastMirroredAlertMs, System.currentTimeMillis())) return
        store.setLastMirroredAlert(alert.atMs)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val res = PhoneLocale.localized(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(alert.atMs))
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_voxara)
            .setContentTitle(res.getString(alert.kind.titleRes()))
            .setContentText(res.getString(R.string.mirror_body, time))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setLocalOnly(true)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(alert.notificationKey, NOTIFICATION_ID, notification)
    }

    private const val NOTIFICATION_ID = 7_100
}
