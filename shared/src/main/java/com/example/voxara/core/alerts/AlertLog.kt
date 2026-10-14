package com.example.voxara.core.alerts

/**
 * ALERT HISTORY — one row per delivered alert, numbers only: when, which kind (so its tier), the
 * value that triggered it, the 100% level, and what the wearer did with it. Kept on the watch
 * with the minute ledger and synced to the phone.
 */
data class AlertRecord(
    val id: Long,
    val atMs: Long,
    val kind: AlertKind,
    /** Dose/budget percent, or dBA for the level alerts. */
    val value: Double,
    /** Dose multiple for LIMIT alerts (100, 200, ...), 0 otherwise. */
    val level: Int,
    val acknowledged: Boolean,
    val snoozed: Boolean,
) {
    val tier: Tier get() = kind.tier
}

/** Where alert records live: SQLite on each device, a list in tests. */
interface AlertLog {
    /** Stores a delivered alert; returns its id. */
    fun add(kind: AlertKind, value: Double, level: Int, atMs: Long): Long

    /** "Got it" / "Snooze" on the newest open alert of [kind]. */
    fun resolveLatest(kind: AlertKind, acknowledged: Boolean, snoozed: Boolean)

    /** Records with atMs >= [fromMs] and id > [afterId], oldest first, at most [limit]. */
    fun page(fromMs: Long, afterId: Long, limit: Int): List<AlertRecord>

    fun latest(): AlertRecord?

    fun prune(beforeMs: Long)

    fun clear()
}
