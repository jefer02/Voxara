package com.example.voxara.core.monitoring

/**
 * Is monitoring actually happening? Pure, so the "paused" decision is unit-tested.
 *
 * The dosimeter writes a heartbeat with every ledger save (about every 30 s). If the wearer left
 * monitoring on but the heartbeat has gone stale, the system (or an OEM battery manager) stopped
 * the service: that is PAUSED, and the UI offers a one-tap resume instead of failing silently.
 */
enum class MonitoringStatus { RUNNING, PAUSED, OFF }

/** Two missed saves plus slack. */
const val HEARTBEAT_STALE_MS = 3L * 60 * 1000

fun monitoringStatus(
    enabled: Boolean,
    serviceActive: Boolean,
    lastHeartbeatMs: Long,
    nowMs: Long,
): MonitoringStatus = when {
    !enabled -> MonitoringStatus.OFF
    serviceActive -> MonitoringStatus.RUNNING
    lastHeartbeatMs > 0 && nowMs - lastHeartbeatMs in 0..HEARTBEAT_STALE_MS -> MonitoringStatus.RUNNING
    else -> MonitoringStatus.PAUSED
}
