package com.example.voxara.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.voxara.R
import com.example.voxara.core.alerts.Alert
import com.example.voxara.core.alerts.AlertKind
import com.example.voxara.core.alerts.Tier
import com.example.voxara.data.LocaleStore
import com.example.voxara.haptics.HapticConductor
import com.example.voxara.haptics.pattern
import com.example.voxara.presentation.MainActivity
import kotlin.math.roundToInt

/**
 * Delivers what the [com.example.voxara.core.alerts.AlertEngine] decided: the haptic first (the
 * wrist is the channel that always works), then a notification with text and actions on the
 * high-importance "Hearing alerts" channel, separate from the quiet monitoring channel.
 *
 * Actions: Got it (acknowledge) · Snooze 1 h (not on 100% alerts) · Open.
 */
class AlertNotifier(private val context: Context, private val haptics: HapticConductor) {

    companion object {
        const val CHANNEL_ID = "voxara_alerts"
        const val ACTION_ACK = "com.example.voxara.ALERT_ACK"
        const val ACTION_SNOOZE = "com.example.voxara.ALERT_SNOOZE"
        const val EXTRA_KIND = "kind"

        fun notificationId(kind: AlertKind) = 5_000 + kind.ordinal

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        LocaleStore.localized(context).getString(R.string.channel_alerts),
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply {
                        // The haptic is played by the app with a pattern per alert; the channel's
                        // own vibration would blur them together.
                        enableVibration(false)
                    }
                )
            }
        }
    }

    init { ensureChannel(context) }

    /** Delivers [alerts] (most severe first). Only the most severe one vibrates. */
    fun deliver(alerts: List<Alert>) {
        if (alerts.isEmpty()) return
        haptics.fire(alerts.first().kind.pattern())
        alerts.forEach { post(it) }
    }

    private fun post(alert: Alert) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val res = LocaleStore.localized(context)
        val kind = alert.kind
        val value = alert.value.roundToInt()
        val id = notificationId(kind)

        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun action(action: String) = PendingIntent.getBroadcast(
            context, id * 10 + action.length,
            Intent(context, AlertActionReceiver::class.java).setAction(action).putExtra(EXTRA_KIND, kind.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_voxara_status)
            // Titles take the value too, so "%%" in a title resolves to "%".
            .setContentTitle(res.getString(titleRes(kind), value))
            .setContentText(res.getString(bodyRes(kind), value))
            .setStyle(NotificationCompat.BigTextStyle().bigText(res.getString(bodyRes(kind), value)))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(
                if (kind.tier == Tier.LIMIT) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, res.getString(R.string.alert_action_ack), action(ACTION_ACK))
        if (kind.snoozable) builder.addAction(0, res.getString(R.string.alert_action_snooze), action(ACTION_SNOOZE))
        builder.addAction(0, res.getString(R.string.alert_action_open), open)

        context.getSystemService(NotificationManager::class.java).notify(id, builder.build())
    }

    @StringRes
    private fun titleRes(k: AlertKind): Int = when (k) {
        AlertKind.AMBIENT_LOUD -> R.string.alert_ambient_loud_title
        AlertKind.DAILY_50 -> R.string.alert_daily_50_title
        AlertKind.DAILY_80 -> R.string.alert_daily_80_title
        AlertKind.DAILY_100 -> R.string.alert_daily_100_title
        AlertKind.WEEK_80 -> R.string.alert_week_80_title
        AlertKind.WEEK_100 -> R.string.alert_week_100_title
        AlertKind.HEADPHONE_WEEK_80 -> R.string.alert_hp_week_80_title
        AlertKind.HEADPHONE_WEEK_100 -> R.string.alert_hp_week_100_title
        AlertKind.HEADPHONE_LOUD_NOW -> R.string.alert_hp_loud_title
    }

    @StringRes
    private fun bodyRes(k: AlertKind): Int = when (k) {
        AlertKind.AMBIENT_LOUD -> R.string.alert_ambient_loud_body
        AlertKind.DAILY_50 -> R.string.alert_daily_50_body
        AlertKind.DAILY_80 -> R.string.alert_daily_80_body
        AlertKind.DAILY_100 -> R.string.alert_daily_100_body
        AlertKind.WEEK_80 -> R.string.alert_week_80_body
        AlertKind.WEEK_100 -> R.string.alert_week_100_body
        AlertKind.HEADPHONE_WEEK_80 -> R.string.alert_hp_week_80_body
        AlertKind.HEADPHONE_WEEK_100 -> R.string.alert_hp_week_100_body
        AlertKind.HEADPHONE_LOUD_NOW -> R.string.alert_hp_loud_body
    }
}
