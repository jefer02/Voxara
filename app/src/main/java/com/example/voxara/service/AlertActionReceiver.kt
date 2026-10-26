package com.example.voxara.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.voxara.R
import com.example.voxara.audio.HeadphoneControl
import com.example.voxara.core.alerts.AlertEngine
import com.example.voxara.core.alerts.AlertKind
import com.example.voxara.core.alerts.AlertPolicy
import com.example.voxara.core.headphones.LayerSource
import com.example.voxara.data.ExposureRepository
import com.example.voxara.data.LocaleStore
import com.example.voxara.data.VoxaraStore
import com.example.voxara.sync.LowerOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The actions on an alert notification.
 *  - "Got it" / "Dismiss": acknowledge (recorded in the alert history). Nothing is muted: the engine's tiers
 *    and cooldown decide the next alert.
 *  - "Snooze": never on 100% alerts and never longer than 2 h (AlertPolicy).
 *  - "Lower to safe volume" (the headphone-volume alert): brings the music volume to the recommended level,
 *    on the watch or, for headphones on the phone, by asking the phone; the notification then says what
 *    happened (including "could not").
 */
class AlertActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val kind = runCatching { AlertKind.valueOf(intent.getStringExtra(AlertNotifier.EXTRA_KIND).orEmpty()) }
            .getOrNull() ?: return
        val nm = context.getSystemService(NotificationManager::class.java)
        val app = context.applicationContext

        if (intent.action == AlertNotifier.ACTION_LOWER && kind == AlertKind.HEADPHONE_OVER_SAFE) {
            val source = runCatching { LayerSource.valueOf(intent.getStringExtra(AlertNotifier.EXTRA_SOURCE).orEmpty()) }
                .getOrDefault(LayerSource.WATCH).takeIf { it == LayerSource.PHONE } ?: LayerSource.WATCH
            val pending = goAsync()
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val r = HeadphoneControl.lowerToSafe(app, source)
                    AlertNotifier.historyWriter(app).resolve(kind, acknowledged = true, snoozed = false)
                    showResult(app, nm, AlertNotifier.notificationId(kind), r.outcome, r.estimatedDba)
                } finally {
                    pending.finish()
                }
            }
            return
        }

        nm.cancel(AlertNotifier.notificationId(kind))
        val snooze = intent.action == AlertNotifier.ACTION_SNOOZE && kind.snoozable
        if (!snooze && intent.action != AlertNotifier.ACTION_ACK) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (snooze) {
                    val store = VoxaraStore(app)
                    val minutes = store.read().snoozeMinutes
                    store.updateAlertMemory {
                        AlertEngine.snooze(it, kind, System.currentTimeMillis(), AlertPolicy.snoozeMs(minutes))
                    }
                }
                // Queued behind the alert's own history row, so a tap that lands before the row is written is not lost;
                // the broadcast finishes once the write has run.
                AlertNotifier.historyWriter(app).resolve(kind, acknowledged = !snooze, snoozed = snooze, onDone = { pending.finish() })
                // "Dismiss" on the headphone alert also closes its panel if the app is open.
                if (kind == AlertKind.HEADPHONE_OVER_SAFE) ExposureRepository.dismissHeadphoneAlert()
            } catch (t: Throwable) {
                pending.finish()
                throw t
            }
        }
    }

    /** Replaces the alert with a short, quiet result that goes away by itself. */
    private fun showResult(app: Context, nm: NotificationManager, id: Int, outcome: LowerOutcome, dba: Double?) {
        val res = LocaleStore.localized(app).resources
        val text = hpLowerText(res, outcome, dba)
        val ok = outcome == LowerOutcome.LOWERED || outcome == LowerOutcome.ALREADY_SAFE
        if (ok) { nm.cancel(id); return }
        nm.notify(
            id,
            NotificationCompat.Builder(app, AlertNotifier.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_voxara_status)
                .setContentTitle(res.getString(R.string.alert_hp_safe_failed_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setOnlyAlertOnce(true)
                .setTimeoutAfter(20_000)
                .setAutoCancel(true)
                .build(),
        )
    }
}

/** What happened to a "lower" request, in words (the panel and the notification both use it). */
fun hpLowerText(res: android.content.res.Resources, outcome: LowerOutcome, dba: Double?): String = when (outcome) {
    LowerOutcome.LOWERED -> if (dba != null) res.getString(R.string.hp_lower_lowered_db, dba.roundToInt()) else res.getString(R.string.hp_lower_lowered)
    LowerOutcome.ALREADY_SAFE -> res.getString(R.string.hp_lower_already)
    LowerOutcome.FIXED_VOLUME -> res.getString(R.string.hp_lower_fixed)
    LowerOutcome.NOT_PLAYING -> res.getString(R.string.hp_lower_not_playing)
    LowerOutcome.UNREACHABLE -> res.getString(R.string.hp_lower_unreachable)
    LowerOutcome.FAILED -> res.getString(R.string.hp_lower_failed)
}
