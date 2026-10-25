package com.example.voxara.core.headphones

import com.example.voxara.sync.LowerOutcome

/**
 * "Your volume is above the recommended level" — what the in-view alert (and nothing else) reads. The alert
 * itself (haptic, notification, history) was decided and delivered by the AlertEngine; this is only its
 * screen. [dismissed] hides the panel for this tier: a higher tier or the cooldown's next alert is a new one.
 */
data class HeadphoneAlert(
    val tier: Int,
    val dba: Double,
    val atMs: Long,
    /** Whose headphones: the watch's own, or the phone's (the lowering command goes there). */
    val source: LayerSource,
    val dismissed: Boolean = false,
)

/** Where "Lower to safe volume" is: nothing asked, waiting for the system or the phone, or what came of it. */
sealed interface LowerUi {
    data object Idle : LowerUi
    data object Working : LowerUi
    data class Done(val outcome: LowerOutcome, val dba: Double?) : LowerUi
}

