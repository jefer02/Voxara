package com.example.voxara.link

/**
 * THE LINK — Voxara never pairs Bluetooth devices. The watch is paired with the phone by the
 * Galaxy Wearable app (or Wear OS app); Voxara's "link" is only its phone app finding its watch
 * app over that existing connection. Pure, so every state is unit-tested.
 */

/** A connected watch as the Data Layer reports it. [name] is shown on screen only, never stored in exports. */
data class WatchNode(val id: String, val name: String, val nearby: Boolean)

enum class SyncProblem {
    /** The watch app did not answer (asleep, out of range, app killed). */
    UNREACHABLE,
    /** The watch answered with something the phone cannot read (version mismatch). */
    INCOMPATIBLE,
    /** Wear OS / Google Play services are missing or out of date on the phone. */
    NO_WEAR_API,
}

/** The last sync attempt, as the phone remembers it. */
data class SyncRecord(
    val lastSuccessMs: Long? = null,
    val lastAttemptMs: Long? = null,
    val lastProblem: SyncProblem? = null,
)

sealed interface LinkState {
    /** First query still running. */
    data object Checking : LinkState

    /** Wear OS services unavailable: nothing to link to until they are installed / updated. */
    data object WearApiMissing : LinkState

    /** No watch connected right now (not paired, Bluetooth off, out of range). */
    data object NoWatch : LinkState

    /** Watches connected, none with the Voxara watch app. */
    data class NotInstalled(val watches: List<WatchNode>) : LinkState

    /** The Voxara watch app is reachable. [others] are further watches that have it. */
    data class Linked(
        val watch: WatchNode,
        val others: List<WatchNode>,
        val lastSyncMs: Long?,
    ) : LinkState

    /** Linked, but the last sync failed and nothing newer succeeded. */
    data class Problem(
        val watch: WatchNode,
        val others: List<WatchNode>,
        val problem: SyncProblem,
        val lastSyncMs: Long?,
    ) : LinkState
}

object LinkResolver {

    /**
     * @param connected every watch the Data Layer reports as connected
     * @param withApp ids of the connected watches that declare the Voxara capability
     * @param preferredId the watch the wearer chose last time, if any
     */
    fun resolve(
        connected: List<WatchNode>,
        withApp: Set<String>,
        preferredId: String?,
        sync: SyncRecord,
    ): LinkState {
        if (connected.isEmpty()) return LinkState.NoWatch
        val candidates = connected.filter { it.id in withApp }
        if (candidates.isEmpty()) return LinkState.NotInstalled(connected)
        // The chosen watch if it is here, otherwise a nearby one, otherwise the first.
        val watch = candidates.firstOrNull { it.id == preferredId }
            ?: candidates.firstOrNull { it.nearby }
            ?: candidates.first()
        val others = candidates.filter { it.id != watch.id }
        val failedSinceSuccess = sync.lastProblem != null &&
            (sync.lastSuccessMs == null || (sync.lastAttemptMs ?: 0L) > sync.lastSuccessMs)
        return if (failedSinceSuccess) {
            LinkState.Problem(watch, others, sync.lastProblem!!, sync.lastSuccessMs)
        } else {
            LinkState.Linked(watch, others, sync.lastSuccessMs)
        }
    }
}
