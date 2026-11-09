package com.example.voxara.ui.design

import android.content.res.Resources
import com.example.voxara.core.situation.AutoStatus
import com.example.voxara.core.situation.SituationChoice
import com.example.voxara.core.situation.SituationMode
import com.example.voxara.designsystem.R

/**
 * The mode as the wearer reads it, honest about Auto (the watch's chip and the phone's pill):
 *  - a fixed choice: just the mode ("Street");
 *  - [AutoStatus.DETECTED]: "Auto · Street" (Concert too, also in builds without the scene model);
 *  - [AutoStatus.DETECTING]: "Auto · Detecting…", only in Auto's first settling window;
 *  - [AutoStatus.UNSURE] and [AutoStatus.NO_MODEL]: plain "Auto" (Street applies meanwhile, unsaid);
 *  - null under Auto: the status is not known (an older watch): "Auto · <mode>", as before it was said.
 * " + Headphones" is appended while headphones mode is on. [spoken] drops the ellipsis for TalkBack.
 */
fun situationLabel(
    res: Resources,
    choice: SituationChoice,
    mode: SituationMode,
    auto: AutoStatus?,
    layerActive: Boolean,
    spoken: Boolean = false,
): String {
    fun withLayer(name: String) = if (layerActive) res.getString(R.string.situation_plus_headphones, name) else name
    if (choice != SituationChoice.AUTO) return withLayer(res.getString(mode.labelRes()))
    return when (auto) {
        AutoStatus.UNSURE, AutoStatus.NO_MODEL -> withLayer(res.getString(R.string.situation_auto))
        AutoStatus.DETECTING -> res.getString(
            R.string.situation_auto_value,
            withLayer(res.getString(if (spoken) R.string.situation_detecting_spoken else R.string.situation_detecting)),
        )
        AutoStatus.DETECTED, null -> res.getString(R.string.situation_auto_value, withLayer(res.getString(mode.labelRes())))
    }
}
