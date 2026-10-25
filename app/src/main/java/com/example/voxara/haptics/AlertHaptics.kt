package com.example.voxara.haptics

import com.example.voxara.core.alerts.AlertKind

/** One distinct, learnable haptic per alert, so the wrist says what happened before the screen. */
fun AlertKind.pattern(): Pattern = when (this) {
    AlertKind.AMBIENT_LOUD -> Pattern.THRESHOLD
    AlertKind.DAILY_50 -> Pattern.SOFT_TICK
    AlertKind.DAILY_80, AlertKind.WEEK_80, AlertKind.HEADPHONE_WEEK_80 -> Pattern.TWO_FIRM
    AlertKind.DAILY_100 -> Pattern.DOSE_FULL
    AlertKind.WEEK_100, AlertKind.HEADPHONE_WEEK_100 -> Pattern.LONG_SHORT_LONG
    // The headphone-volume family: the quick triple (above the recommended level, and very loud listening).
    AlertKind.HEADPHONE_LOUD_NOW, AlertKind.HEADPHONE_OVER_SAFE -> Pattern.QUICK_TRIPLE
}
