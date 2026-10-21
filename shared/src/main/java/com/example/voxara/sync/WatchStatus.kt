package com.example.voxara.sync

import com.example.voxara.core.ai.MiniJson
import com.example.voxara.core.alerts.AlertKind
import com.example.voxara.core.headphones.LayerSource
import com.example.voxara.core.situation.SituationMode
import com.example.voxara.core.monitoring.MonitoringStatus

/**
 * What the watch reports about itself, as of [atMs]. The phone shows these numbers exactly as the
 * watch computed them (never recomputed), always with their time — nothing here is "live".
 */
data class WatchStatus(
    val atMs: Long,
    val appVersion: String,
    val monitoring: MonitoringStatus,
    val micGranted: Boolean,
    val notificationsGranted: Boolean,
    /** The active capture path has a calibration made on this watch model. */
    val calibrated: Boolean,
    val calibratedAtMs: Long?,
    val dosePercent: Double,
    val weeklyPercent: Double,
    val headphoneWeeklyPercent: Double,
    /** Last measured level and when the dosimeter last saved (its heartbeat); null before any. */
    val lastDba: Double?,
    val lastReadingAtMs: Long?,
    val sensitiveListener: Boolean,
    val powerSaveMode: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val tonoEnabled: Boolean,
    val retentionDays: Int,
    val lastAlertKind: AlertKind? = null,
    val lastAlertAtMs: Long? = null,
    val lastAlertValue: Double? = null,
    /** The situation mode the watch is in right now (Auto already resolved); null from older watches. */
    val situationMode: SituationMode? = null,
    /** Headphones mode (the listening layer over the situation) is on right now; false from older watches. */
    val headphonesMode: Boolean = false,
    /** Why it is on: the watch's own detection, the phone's report or the wearer's switch ([LayerSource.NONE] when off). */
    val headphonesSource: LayerSource = LayerSource.NONE,
) {
    fun toJson(): String = MiniJson.write(
        linkedMapOf(
            "v" to WearContract.PROTOCOL,
            "at" to atMs,
            "app_version" to appVersion,
            "monitoring" to monitoring.name,
            "mic" to micGranted,
            "notifications" to notificationsGranted,
            "calibrated" to calibrated,
            "calibrated_at" to calibratedAtMs,
            "dose_pct" to dosePercent,
            "weekly_pct" to weeklyPercent,
            "hp_weekly_pct" to headphoneWeeklyPercent,
            "last_dba" to lastDba,
            "last_reading_at" to lastReadingAtMs,
            "sensitive" to sensitiveListener,
            "power_save" to powerSaveMode,
            "battery_exempt" to ignoringBatteryOptimizations,
            "tono" to tonoEnabled,
            "retention_days" to retentionDays,
            "last_alert_kind" to lastAlertKind?.name,
            "last_alert_at" to lastAlertAtMs,
            "last_alert_value" to lastAlertValue,
            "situation_mode" to situationMode?.name,
            "hp_mode" to headphonesMode,
            "hp_source" to headphonesSource.name,
        )
    )

    companion object {
        /** Null when the payload is unreadable; unknown or missing values fall back safely. */
        fun fromJson(json: String?): WatchStatus? {
            val m = runCatching { MiniJson.parse(json ?: return null) as? Map<*, *> }.getOrNull() ?: return null
            fun d(k: String) = (m[k] as? Number)?.toDouble()
            fun l(k: String) = (m[k] as? Number)?.toLong()
            fun b(k: String) = m[k] as? Boolean ?: false
            val at = l("at") ?: return null
            return WatchStatus(
                atMs = at,
                appVersion = m["app_version"] as? String ?: "",
                monitoring = runCatching { MonitoringStatus.valueOf(m["monitoring"] as String) }.getOrDefault(MonitoringStatus.OFF),
                micGranted = b("mic"),
                notificationsGranted = b("notifications"),
                calibrated = b("calibrated"),
                calibratedAtMs = l("calibrated_at"),
                dosePercent = d("dose_pct") ?: 0.0,
                weeklyPercent = d("weekly_pct") ?: 0.0,
                headphoneWeeklyPercent = d("hp_weekly_pct") ?: 0.0,
                lastDba = d("last_dba"),
                lastReadingAtMs = l("last_reading_at"),
                sensitiveListener = b("sensitive"),
                powerSaveMode = b("power_save"),
                ignoringBatteryOptimizations = b("battery_exempt"),
                tonoEnabled = m["tono"] as? Boolean ?: true,
                retentionDays = l("retention_days")?.toInt() ?: 35,
                lastAlertKind = runCatching { AlertKind.valueOf(m["last_alert_kind"] as String) }.getOrNull(),
                lastAlertAtMs = l("last_alert_at"),
                lastAlertValue = d("last_alert_value"),
                situationMode = runCatching { SituationMode.valueOf(m["situation_mode"] as String) }.getOrNull(),
                headphonesMode = b("hp_mode"),
                headphonesSource = runCatching { LayerSource.valueOf(m["hp_source"] as String) }.getOrDefault(LayerSource.NONE),
            )
        }
    }
}
