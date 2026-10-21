package com.example.voxara.core.headphones

import com.example.voxara.core.alerts.AlertSettings

/**
 * HEADPHONES MODE — a LISTENING LAYER on top of the situation mode, not a sixth situation.
 *
 * The situation (Quiet, Street, Transport, Concert, Work) says where the wearer is; the layer says
 * that they are also listening through headphones. On a bus both exposures matter, so both stay.
 *
 * THE RULE (the same one the situation modes follow): the layer can only ADD protection. It never
 * lowers an environmental threshold, never stops environmental sampling, never skips a ledger write
 * or an alert, and keeps the double-count rule between the headphone and the environmental dose
 * ([attribute]). What it may change is what the watch SHOWS (the hero leads with the estimated
 * listening level, the Headphones page moves up) and how it says so (tile, notification, haptic).
 * [ListeningLayerPolicy] is the one place that spells this out, and its tests pin it.
 */

/** Headphones mode as the setting stores it: follow the detection, or the wearer's own switch. */
enum class HeadphoneModeChoice {
    AUTO, ON, OFF;

    companion object {
        val DEFAULT = AUTO

        /** Null when [raw] is not a choice name. */
        fun parse(raw: String?): HeadphoneModeChoice? = entries.firstOrNull { it.name == raw?.trim() }
    }
}

/** Why the layer is (or is not) on. [PHONE] and [WATCH] are detections; [MANUAL] is the wearer's switch. */
enum class LayerSource { NONE, WATCH, PHONE, MANUAL }

/**
 * What the layer is doing now.
 *
 * @property active the layer is on (what every surface reads)
 * @property source why: the wearer's switch, or where the detection came from
 * @property detected headphones are detected (after hysteresis) whatever the choice is: Auto would be on
 * @property detectedBy where that detection comes from ([LayerSource.NONE] when nothing is detected)
 */
data class ListeningLayerState(
    val active: Boolean = false,
    val source: LayerSource = LayerSource.NONE,
    val detected: Boolean = false,
    val detectedBy: LayerSource = LayerSource.NONE,
) {
    /** This state after the wearer picked [choice]: immediate, from what is already detected. */
    fun withChoice(choice: HeadphoneModeChoice): ListeningLayerState = when (choice) {
        HeadphoneModeChoice.ON -> copy(active = true, source = LayerSource.MANUAL)
        HeadphoneModeChoice.OFF -> copy(active = false, source = LayerSource.MANUAL)
        HeadphoneModeChoice.AUTO ->
            if (detected) copy(active = true, source = detectedBy.takeIf { it != LayerSource.NONE } ?: LayerSource.WATCH)
            else copy(active = false, source = LayerSource.NONE)
    }

    companion object {
        val OFF = ListeningLayerState()
    }
}

/**
 * THE LAYER'S STATE MACHINE — pure and clock-free (times are passed in).
 *
 * Detection has hysteresis, so a Bluetooth link that flaps does not replay the transition: headphones
 * must be seen for [enterMs] to turn it on and gone for [leaveMs] to turn it off. A manual choice
 * is immediate (the wearer just touched the watch) but detection keeps being tracked underneath, so
 * going back to Auto continues from what is really connected.
 */
class ListeningLayer(
    private val enterMs: Long = ENTER_MS,
    private val leaveMs: Long = LEAVE_MS,
) {
    private var detected = false
    private var detectedBy = LayerSource.NONE
    private var pendingSince: Long? = null

    /** A detection change is waiting out its hold time: the caller should poll quickly meanwhile. */
    val pending: Boolean get() = pendingSince != null

    /**
     * @param watchHeadphones headphones connected to this watch (raw, not debounced)
     * @param phoneHeadphones the phone's opt-in listening sync reported headphones recently
     */
    fun update(
        nowMs: Long,
        choice: HeadphoneModeChoice,
        watchHeadphones: Boolean,
        phoneHeadphones: Boolean,
    ): ListeningLayerState {
        val raw = watchHeadphones || phoneHeadphones
        if (raw == detected) {
            pendingSince = null
        } else {
            val since = pendingSince ?: nowMs.also { pendingSince = it }
            if (nowMs - since >= (if (raw) enterMs else leaveMs)) {
                detected = raw
                pendingSince = null
            }
        }
        if (raw) detectedBy = if (watchHeadphones) LayerSource.WATCH else LayerSource.PHONE
        if (!detected && !raw) detectedBy = LayerSource.NONE
        return ListeningLayerState(detected = detected, detectedBy = if (detected) detectedBy else LayerSource.NONE).withChoice(choice)
    }

    /** Forgets everything (the dosimeter stopped): the next update starts from "no headphones". */
    fun reset() {
        detected = false
        detectedBy = LayerSource.NONE
        pendingSince = null
    }

    companion object {
        /** Headphones must be seen this long before the layer turns on. */
        const val ENTER_MS = 3_000L
        /** ...and gone this long before it turns off (a dropped Bluetooth link usually comes back). */
        const val LEAVE_MS = 10_000L

        /**
         * How long a phone report counts: the phone samples every 15 minutes (and at connect edges),
         * so one report stands for about that long. Coarse by nature: the layer says "from the
         * phone" and the wearer can always switch it off.
         */
        const val PHONE_FRESH_MS = 16L * 60_000L

        /** True when the phone's last listening span (ending at [lastEndMs], 0 = none) is recent enough. */
        fun phoneReports(nowMs: Long, lastEndMs: Long): Boolean =
            lastEndMs > 0L && nowMs - lastEndMs in -60_000L..PHONE_FRESH_MS
    }
}

/**
 * The quick switch on Overview: the choice that turns the layer to [wantOn], going back to Auto when
 * Auto would give the same answer (headphones really are, or are not, [detected]) so a manual
 * override never lingers longer than it has to.
 */
fun quickToggleChoice(wantOn: Boolean, detected: Boolean): HeadphoneModeChoice = when {
    wantOn == detected -> HeadphoneModeChoice.AUTO
    wantOn -> HeadphoneModeChoice.ON
    else -> HeadphoneModeChoice.OFF
}

/**
 * WHAT THE LAYER MAY AND MAY NOT TOUCH. Every function takes the layer as an input on purpose and
 * returns the same answer for every layer state: the tests walk all of them.
 */
object ListeningLayerPolicy {

    /** The alert settings the engine runs with under the layer: exactly the wearer's own, never lower. */
    @Suppress("UNUSED_PARAMETER")
    fun alertSettings(layer: ListeningLayerState, base: AlertSettings): AlertSettings = base

    /**
     * What one measured burst does. The room is always measured, always feeds the alert window, and
     * alerts are always evaluated. Only the double-count rule decides which ledger the span belongs
     * to (the louder source at the ear): never both, never neither.
     *
     * @param headphoneDba estimate while playing to headphones, null when not listening
     * @param roomDba the measured room level
     */
    data class BurstPlan(
        val foldAmbient: Boolean,
        val foldHeadphone: Boolean,
        /** The room level reaches the sustained-level alert whoever owns the dose (a loud room is still a loud room). */
        val feedSustained: Boolean,
        val evaluateAlerts: Boolean,
    )

    @Suppress("UNUSED_PARAMETER")
    fun planBurst(
        layer: ListeningLayerState,
        headphoneDba: Double?,
        roomDba: Double,
        category: HeadphoneCategory,
    ): BurstPlan {
        val owner = attribute(headphoneDba, roomDba, category)
        return BurstPlan(
            foldAmbient = owner == Attribution.AMBIENT,
            foldHeadphone = owner == Attribution.HEADPHONE,
            feedSustained = true,
            evaluateAlerts = true,
        )
    }
}
