package com.example.voxara.core.alerts

import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Writes the alert history OFF the caller's thread. An alert is delivered from the dosimeter's loop (the main
 * thread): its haptic and its notification go first and must not wait for SQLite, so the history row is queued here
 * and written by one background thread, in the order the alerts were delivered.
 *
 * An acknowledgement ([resolve]) goes through the same queue, so it can never overtake the row it acknowledges.
 *
 * A row for an alert delivered before the last "delete all" ([wipedAtMs]) is dropped: a write that was still queued
 * when the data was deleted must not put it back. A failed write is reported to [onFailure] and never thrown.
 */
class AlertHistoryWriter(
    private val log: () -> AlertLog,
    private val wipedAtMs: () -> Long = { 0L },
    private val onFailure: (Throwable) -> Unit = {},
    private val executor: Executor = SHARED,
) {
    /** Queues one row per alert, all stamped [atMs]. Returns at once. */
    fun record(alerts: List<Alert>, atMs: Long) {
        if (alerts.isEmpty()) return
        executor.execute {
            // The check and the writes are one step with respect to [wipe]: a row cannot slip in after the delete.
            synchronized(LOCK) {
                if (atMs <= wipedAtMs()) return@synchronized
                val target = try { log() } catch (t: Throwable) { onFailure(t); return@synchronized }
                for (a in alerts) {
                    try { target.add(a.kind, a.value, a.level, atMs) } catch (t: Throwable) { onFailure(t) }
                }
            }
        }
    }

    /**
     * "Got it", "Snooze" or "Dismiss" on an alert: marks the latest unresolved row of [kind], queued BEHIND the rows
     * still waiting to be written. A tap that lands before its alert's row exists therefore finds it: the row is
     * written first, then resolved, on the same thread. Idempotent: a second tap finds nothing unresolved and changes nothing. [onDone] runs after the write (a receiver finishes its broadcast there).
     */
    fun resolve(kind: AlertKind, acknowledged: Boolean, snoozed: Boolean, onDone: () -> Unit = {}) {
        executor.execute {
            try {
                synchronized(LOCK) {
                    try { log().resolveLatest(kind, acknowledged, snoozed) } catch (t: Throwable) { onFailure(t) }
                }
            } finally {
                onDone()
            }
        }
    }

    companion object {
        private val LOCK = Any()

        /**
         * "Delete all": [fence] records the instant (what [wipedAtMs] reads) and [clear] empties the history; no
         * queued row is written between the two, so none from before the delete survives.
         */
        fun wipe(fence: () -> Unit, clear: () -> Unit) = synchronized(LOCK) { fence(); clear() }

        /** One thread for the whole process: history rows keep their order, and the thread never keeps the process alive. */
        private val SHARED: Executor = Executors.newSingleThreadExecutor { r -> Thread(r, "voxara-alert-history").apply { isDaemon = true } }
    }
}
