package com.example.voxara.phone.model

/**
 * How old a figure is. The phone never shows anything as live: every number carries the time it
 * was true, and older figures are marked so they cannot be mistaken for the present.
 */
enum class Freshness {
    /** Under [RECENT_MS]: "Updated 4 min ago". */
    RECENT,
    /** Under [STALE_MS]: "At 14:05", no warning. */
    EARLIER,
    /** Older: shown dimmed with "Last known · yesterday 18:20" and a sync prompt. */
    STALE,
    /** Never received. */
    NONE;

    companion object {
        const val RECENT_MS = 15 * 60_000L
        const val STALE_MS = 3 * 3_600_000L

        fun of(atMs: Long?, nowMs: Long): Freshness = when {
            atMs == null || atMs <= 0L -> NONE
            nowMs - atMs < RECENT_MS -> RECENT
            nowMs - atMs < STALE_MS -> EARLIER
            else -> STALE
        }
    }
}
