package com.example.voxara.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.voxara.audio.DeviceAudioInfo
import com.example.voxara.audio.PowerPolicy
import com.example.voxara.core.calibration.MicSource
import com.example.voxara.core.calibration.appliesTo
import com.example.voxara.core.dose.WHO_WEEKLY_PA2H_ADULT
import com.example.voxara.core.dose.WHO_WEEKLY_PA2H_SENSITIVE
import com.example.voxara.core.ledger.localEpochDay
import com.example.voxara.core.monitoring.monitoringStatus
import com.example.voxara.core.situation.Situation
import com.example.voxara.data.AppLanguage
import com.example.voxara.data.ExposureDb
import com.example.voxara.data.ExposureRepository
import com.example.voxara.data.LocaleStore
import com.example.voxara.data.VoxaraStore
import com.example.voxara.settings.LanguageChoice
import com.example.voxara.settings.SettingField
import com.example.voxara.settings.SettingsDoc
import com.example.voxara.settings.SettingsMerge
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.runBlocking

/**
 * The watch's side of the phone companion (Wearable Data Layer), outside the UI:
 *  - publishes the watch's settings (with change times) and its status;
 *  - applies settings the phone sent, through the shared conflict rule, after validating and
 *    clamping every value (the alert engine then picks them up from the store as always);
 *  - answers the phone's history requests ([WatchSyncResponder]) and "delete all data".
 *
 * Nothing here touches alert decisions: the watch keeps evaluating alerts from its own store,
 * offline, whether or not a phone is around.
 */
object CompanionLink {

    private const val TAG = "VoxaraCompanion"

    @Volatile private var lastSettingsJson: String? = null

    fun language(context: Context): LanguageChoice =
        runCatching { LanguageChoice.valueOf(LocaleStore.read(context).name) }.getOrDefault(LanguageChoice.SYSTEM)

    /** Publishes the watch's settings document when it changed since the last publish. */
    fun publishSettings(context: Context, p: VoxaraStore.Persisted) {
        val json = VoxaraStore.settingsDoc(p, language(context)).toJson()
        if (json == lastSettingsJson) return
        lastSettingsJson = json
        put(context, WearContract.PATH_SETTINGS_WATCH, json)
    }

    fun publishStatus(context: Context, status: WatchStatus) = put(context, WearContract.PATH_STATUS, status.toJson())

    /** One JSON byte array per item, so the payload is exactly what the shared tests encode. */
    private fun put(context: Context, path: String, json: String) {
        // No phone, no Play services, no Data Layer: the watch simply works on its own.
        runCatching {
            val request = PutDataMapRequest.create(path).apply {
                dataMap.putByteArray(WearContract.KEY_PAYLOAD, json.toByteArray(Charsets.UTF_8))
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request)
                .addOnFailureListener { Log.w(TAG, "publish $path failed", it) }
        }.onFailure { Log.w(TAG, "Data Layer unavailable; $path not published", it) }
    }

    /** Applies the phone's settings document. Returns the fields whose value changed. */
    suspend fun applyPhoneSettings(context: Context, json: String): Set<SettingField> {
        val app = context.applicationContext
        val store = VoxaraStore(app)
        val before = store.read()
        val own = VoxaraStore.settingsDoc(before, language(app))
        val result = SettingsMerge.watchReceives(own, SettingsDoc.fromJson(json), System.currentTimeMillis())
        if (result.refusedFuture.isNotEmpty()) Log.w(TAG, "refused future-dated settings: ${result.refusedFuture}")
        val changed = result.merged.fields.filter { (f, v) -> own[f] != v }
        if (changed.isNotEmpty()) {
            store.applySynced(changed)
            changed[SettingField.LANGUAGE]?.let { LocaleStore.set(app, AppLanguage.valueOf(it.value)) }
        }
        val after = store.read()
        if (ExposureRepository.isInitialised()) ExposureRepository.applySettings(after)
        publishSettings(app, after)
        return result.accepted
    }

    /** What the watch reports about itself right now. */
    suspend fun status(context: Context): WatchStatus {
        val app = context.applicationContext
        val p = VoxaraStore(app).read()
        val now = System.currentTimeMillis()
        val live = ExposureRepository.state.value
        val active = ExposureRepository.serviceActive
        val budget = if (p.sensitiveListener) WHO_WEEKLY_PA2H_SENSITIVE else WHO_WEEKLY_PA2H_ADULT
        val device = DeviceAudioInfo.read(app)
        val source = live.micSource ?: if (device.unprocessedSupported == true) MicSource.UNPROCESSED else MicSource.VOICE_RECOGNITION
        val calibration = p.calibrations[source]?.takeIf { it.appliesTo(device.deviceKey) }
        val power = PowerPolicy.read(app)
        val last = runCatching { ExposureDb.get(app).alerts.latest() }.getOrNull()
        fun granted(permission: String) = app.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        return WatchStatus(
            atMs = now,
            appVersion = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull().orEmpty(),
            monitoring = monitoringStatus(p.monitoringEnabled, active, p.heartbeatMs, now),
            micGranted = granted(Manifest.permission.RECORD_AUDIO),
            notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(Manifest.permission.POST_NOTIFICATIONS),
            calibrated = calibration != null,
            calibratedAtMs = calibration?.calibratedAtMs,
            // The live figures while the dosimeter runs in this process; otherwise its last save.
            dosePercent = when {
                active -> live.dosePercent
                p.dayEpoch == localEpochDay(now) -> p.dosePercent
                else -> 0.0
            },
            weeklyPercent = if (active) live.weeklyFraction * 100 else 100.0 * p.weeklyPa2h / budget,
            headphoneWeeklyPercent = if (active) live.headphoneWeeklyFraction * 100 else 100.0 * p.headphoneWeeklyPa2h / budget,
            lastDba = (if (active) live.dba else p.lastDba).takeIf { it > 0.0 && !live.simulated },
            lastReadingAtMs = if (active) now else p.heartbeatMs.takeIf { it > 0L },
            sensitiveListener = p.sensitiveListener,
            powerSaveMode = power.powerSaveMode,
            ignoringBatteryOptimizations = power.ignoringBatteryOptimizations,
            tonoEnabled = p.tonoEnabled,
            retentionDays = ExposureDb.RETENTION_DAYS.toInt(),
            lastAlertKind = last?.kind,
            lastAlertAtMs = last?.atMs,
            lastAlertValue = last?.value,
            // The mode in force (Auto resolved). While the dosimeter runs, its live copy.
            situationMode = if (active) live.mode else Situation.resolve(p.situationChoice),
        )
    }

    /**
     * "Delete all data" asked by the phone: every minute, every alert and the running counters.
     * Settings, calibration and onboarding stay. A running dosimeter reloads its (now empty)
     * ledgers and re-arms the daily alerts.
     */
    fun deleteAll(context: Context) {
        val app = context.applicationContext
        ExposureDb.get(app).clearAll()
        runBlocking { VoxaraStore(app).clearExposure() }
        ExposureRepository.resetDay()
        ExposureRepository.ledgerGeneration.incrementAndGet()
        ExposureRepository.update {
            it.copy(
                days = emptyList(), headphoneDays = emptyList(), weeklyFraction = 0.0,
                headphoneWeeklyFraction = 0.0, headphoneWeeklyPa2h = 0.0,
            )
        }
        Log.i(TAG, "all exposure data deleted at the phone's request")
    }

    /**
     * Optional mirrored 100% alert: after the watch has vibrated and notified, a copy is offered
     * to a reachable Voxara phone app (which shows it only if the wearer turned that on).
     * Fire-and-forget: a failure here can never delay or affect the watch's own alert.
     */
    fun mirrorLimit(context: Context, alert: MirroredAlert) {
        val app = context.applicationContext
        runCatching {
            Wearable.getCapabilityClient(app)
                .getCapability(WearContract.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                .addOnSuccessListener { info ->
                    info.nodes.forEach { node ->
                        Wearable.getMessageClient(app).sendMessage(node.id, WearContract.MSG_LIMIT_ALERT, alert.encode())
                    }
                }
        }.onFailure { Log.w(TAG, "Data Layer unavailable; limit alert not mirrored", it) }
    }

    fun responder(context: Context): WatchSyncResponder {
        val app = context.applicationContext
        val db = ExposureDb.get(app)
        return WatchSyncResponder(
            minutes = db,
            alerts = db.alerts,
            nowMs = System::currentTimeMillis,
            status = { runBlocking { status(app) }.also { publishStatus(app, it) } },
            deleteAll = { deleteAll(app) },
        )
    }
}
