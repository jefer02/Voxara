package com.example.voxara.sync

import com.example.voxara.core.ai.MiniJson
import com.example.voxara.core.alerts.AlertKind
import com.example.voxara.core.alerts.Tier

/**
 * OPTIONAL MIRRORED 100% ALERTS — the watch tells the phone a LIMIT alert just fired
 * ([WearContract.MSG_LIMIT_ALERT]); the phone may show a copy. The watch has already vibrated and
 * notified: the copy is a courtesy, never the alert itself, so nothing on the watch waits for it.
 */
data class MirroredAlert(val kind: AlertKind, val value: Double, val level: Int, val atMs: Long) {

    fun encode(): ByteArray = MiniJson.write(
        linkedMapOf("kind" to kind.name, "value" to value, "level" to level, "at" to atMs)
    ).toByteArray(Charsets.UTF_8)

    /** One notification per alert kind and level, so a repeat replaces instead of stacking. */
    val notificationKey: String get() = "${kind.name}:$level"

    companion object {
        /** A copy that arrives later than this (the phone was away) is not shown: it is no longer news. */
        const val MAX_AGE_MS = 10 * 60_000L

        fun decode(bytes: ByteArray): MirroredAlert? = runCatching {
            val m = MiniJson.parse(bytes.toString(Charsets.UTF_8)) as Map<*, *>
            MirroredAlert(
                kind = AlertKind.valueOf(m["kind"] as String),
                value = (m["value"] as Number).toDouble(),
                level = (m["level"] as Number).toInt(),
                atMs = (m["at"] as Number).toLong(),
            )
        }.getOrNull()

        /**
         * Whether the phone shows this copy: only when the wearer turned mirroring on (off by
         * default), only for LIMIT alerts, never twice for the same alert, never when stale.
         */
        fun shouldShow(alert: MirroredAlert, enabled: Boolean, lastShownAtMs: Long, nowMs: Long): Boolean =
            enabled &&
                alert.kind.tier == Tier.LIMIT &&
                alert.atMs > lastShownAtMs &&
                nowMs - alert.atMs in -60_000L..MAX_AGE_MS
    }
}
