package com.example.voxara.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.example.voxara.R
import com.example.voxara.core.format.formatClock
import com.example.voxara.link.LinkHealth
import com.example.voxara.ui.design.MetricCard
import com.example.voxara.ui.design.VColor
import com.example.voxara.ui.design.VoxIcons
import kotlinx.coroutines.delay

/**
 * THE PHONE LINK, HONESTLY — "Phone connected", "Reconnecting to your phone…" or "Phone not connected for 12 min",
 * on the More page. The watch works the same with or without a phone; this only says which it is. It reconnects
 * by itself (no action from the wearer), and anything waiting to be sent goes out then.
 */
@Composable
fun PhoneLinkCard(health: LinkHealth, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    // "Reconnecting" turns into "not connected for…" and the minutes tick on while the page is open.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(health) { while (true) { delay(15_000); now = System.currentTimeMillis() } }
    val value = phoneLinkText(health, now)
    val ok = health is LinkHealth.Connected
    MetricCard(
        icon = VoxIcons.Phone,
        label = stringResource(R.string.link_phone_label),
        value = value,
        accent = if (ok) VColor.AccentInk else VColor.TextSecondary,
        modifier = modifier,
    )
}

@Composable
fun phoneLinkText(health: LinkHealth, nowMs: Long): String = when (health) {
    LinkHealth.Unknown -> stringResource(R.string.link_unknown)
    is LinkHealth.Connected -> stringResource(R.string.link_connected)
    is LinkHealth.Reconnecting -> stringResource(R.string.link_reconnecting)
    is LinkHealth.Disconnected -> stringResource(R.string.link_disconnected, formatClock(((nowMs - health.sinceMs) / 60_000.0).coerceAtLeast(1.0)))
}
