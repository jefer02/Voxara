package com.example.voxara.phone.listening

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.voxara.core.alerts.Alert
import com.example.voxara.core.alerts.AlertEngine
import com.example.voxara.core.alerts.AlertInputs
import com.example.voxara.core.alerts.AlertKind
import com.example.voxara.core.alerts.AlertMemoryCodec
import com.example.voxara.core.alerts.AlertPolicy
import com.example.voxara.core.alerts.AlertSettings
import com.example.voxara.core.alerts.HapticPrefs
import com.example.voxara.core.ledger.localEpochDay
import com.example.voxara.core.ledger.localMinuteOfDay
import com.example.voxara.phone.MainActivity
import com.example.voxara.phone.PhoneGraph
import com.example.voxara.phone.PhoneLocale
import com.example.voxara.phone.PhonePrefs
import com.example.voxara.phone.R
import com.example.voxara.settings.VoxaraSettings
import com.example.voxara.sync.LowerOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * THE HEADPHONE-VOLUME ALERT ON THE PHONE, for headphones connected to the phone. It is the SAME AlertEngine
 * and AlertPolicy the watch uses (a WARNING: always vibrates, held in quiet hours, its tiers escalate and its
 * cooldown is the engine's), fed only the estimated listening level, with the wearer's own synced settings.
 * The watch alerts too, from the phone's live state: this is the phone's half.
 *
 * Only the output is the phone's own: a notification with the same two actions as on the watch, and the
 * phone's vibration. The numbers are an estimate, and the notification says so.
 */
object PhoneAlerts {

    const val CHANNEL_ID = "voxara_phone_hp_alerts"
    const val ACTION_DISMISS = "com.example.voxara.phone.HP_DISMISS"
    const val ACTION_LOWER = "com.example.voxara.phone.HP_LOWER"
    const val NOTIFICATION_ID = 7_201

    private val lock = Any()

    /** The settings the engine runs with: the wearer's own (synced from the watch), never anything lower. */
    suspend fun settings(): Pair<AlertSettings, HapticPrefs> {
        val s = VoxaraSettings.from(PhoneGraph.store.read().phoneSettings)
        return AlertSettings(
            thresholdDba = s.alertThresholdDba, headphoneLoudDba = s.headphoneLoudDba,
            quietHoursEnabled = s.quietHoursEnabled, quietStartMin = s.quietStartMin, quietEndMin = s.quietEndMin,
            dailyCap = s.dailyCap,
        ) to HapticPrefs(s.hapticStrength, s.advisoryHaptics)
    }

    /**
     * Looks at the level (null = nothing playing, or it has not yet stayed over for a few seconds) and
     * delivers the alert if the engine says so. Returns the alert it delivered, if any.
     */
    suspend fun evaluate(context: Context, levelDba: Double?, nowMs: Long = System.currentTimeMillis()): Alert? {
        val app = context.applicationContext
        val (settings, prefs) = settings()
        val alert = synchronized(lock) {
            val memory = AlertMemoryCodec.decode(PhonePrefs.alertMemory(app))
            val (alerts, next) = AlertEngine.evaluate(
                AlertInputs(
                    nowMs = nowMs, localDay = localEpochDay(nowMs), localMinuteOfDay = localMinuteOfDay(nowMs),
                    sustainedAmbientDba = null, dailyDosePercent = 0.0, weeklyAmbientPercent = 0.0,
                    headphoneNowDba = levelDba,
                ),
                settings, memory,
            )
            if (next != memory) PhonePrefs.setAlertMemory(app, AlertMemoryCodec.encode(next))
            alerts.firstOrNull { it.kind == AlertKind.HEADPHONE_OVER_SAFE }
        } ?: return null
        vibrate(app, AlertPolicy.repeats(alert.kind, prefs))
        post(app, alert)
        return alert
    }

    private fun vibrate(app: Context, times: Int) {
        if (times <= 0) return
        val vibrator = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) app.getSystemService(VibratorManager::class.java)?.defaultVibrator
            else @Suppress("DEPRECATION") app.getSystemService(Vibrator::class.java)
        }.getOrNull() ?: return
        // The quick triple, the same family as the watch's headphone-volume haptic; STRONG plays it twice.
        val one = longArrayOf(0, 60, 80, 60, 80, 60)
        val pattern = if (times <= 1) one else one + longArrayOf(600) + one.drop(1).toLongArray()
        runCatching { vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
    }

    private fun ensureChannel(app: Context) {
        val nm = app.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, PhoneLocale.localized(app).resources.getString(R.string.hp_alert_channel), NotificationManager.IMPORTANCE_HIGH)
                    .apply { enableVibration(false) },   // the app plays its own pattern
            )
        }
    }

    private fun post(app: Context, alert: Alert) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(app)
        val res = PhoneLocale.localized(app).resources
        val level = alert.value.roundToInt()
        fun action(a: String) = PendingIntent.getBroadcast(
            app, a.length, Intent(app, PhoneAlertActionReceiver::class.java).setAction(a).setPackage(app.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            app, 0, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        app.getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_voxara_status)
                .setContentTitle(res.getString(R.string.hp_alert_title))
                .setContentText(res.getString(R.string.hp_alert_text, level))
                .setStyle(NotificationCompat.BigTextStyle().bigText(res.getString(R.string.hp_alert_text, level)))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .setAutoCancel(true)
                .addAction(0, res.getString(R.string.hp_lower_action), action(ACTION_LOWER))
                .addAction(0, res.getString(R.string.hp_dismiss_action), action(ACTION_DISMISS))
                .build(),
        )
    }

    /** What happened to "lower", in words, for the notification and the screen. */
    fun lowerText(res: android.content.res.Resources, outcome: LowerOutcome, dba: Double?): String = when (outcome) {
        LowerOutcome.LOWERED -> if (dba != null) res.getString(R.string.hp_lower_lowered_db, dba.roundToInt()) else res.getString(R.string.hp_lower_lowered)
        LowerOutcome.ALREADY_SAFE -> res.getString(R.string.hp_lower_already)
        LowerOutcome.FIXED_VOLUME -> res.getString(R.string.hp_lower_fixed)
        LowerOutcome.NOT_PLAYING -> res.getString(R.string.hp_lower_not_playing)
        LowerOutcome.UNREACHABLE, LowerOutcome.FAILED -> res.getString(R.string.hp_lower_failed)
    }

    fun cancelNotification(app: Context) = app.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
}

/** "Dismiss" and "Lower to safe volume" on the phone's alert notification. Dismiss mutes nothing. */
class PhoneAlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            PhoneAlerts.ACTION_DISMISS -> {
                PhoneAlerts.cancelNotification(app)
                PhoneListeningRuntime.dismissAlert()
            }
            PhoneAlerts.ACTION_LOWER -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val r = PhoneListeningService.lower(app)
                        if (r.outcome == LowerOutcome.LOWERED || r.outcome == LowerOutcome.ALREADY_SAFE) PhoneAlerts.cancelNotification(app)
                        else {
                            val res = PhoneLocale.localized(app).resources
                            app.getSystemService(NotificationManager::class.java).notify(
                                PhoneAlerts.NOTIFICATION_ID,
                                NotificationCompat.Builder(app, PhoneAlerts.CHANNEL_ID)
                                    .setSmallIcon(R.drawable.ic_voxara_status)
                                    .setContentTitle(res.getString(R.string.hp_alert_title))
                                    .setContentText(PhoneAlerts.lowerText(res, r.outcome, r.estimatedDba))
                                    .setOnlyAlertOnce(true).setTimeoutAfter(20_000).setAutoCancel(true).build(),
                            )
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
