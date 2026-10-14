package com.example.voxara.sync

import com.example.voxara.core.alerts.AlertSettings
import com.example.voxara.core.alerts.HapticStrength
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.data.VoxaraStore
import com.example.voxara.settings.LanguageChoice
import com.example.voxara.settings.SettingField
import com.example.voxara.settings.SettingsDoc
import com.example.voxara.settings.SettingsMerge
import com.example.voxara.settings.VoxaraSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The watch's end of the settings sync: its document, applying the phone's, and its own stamps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WatchSettingsTest {

    private val store by lazy { VoxaraStore(RuntimeEnvironment.getApplication()) }

    @Test
    fun `a fresh watch publishes every field, with time 0 and the defaults`() {
        val doc = VoxaraStore.settingsDoc(VoxaraStore.Persisted(), LanguageChoice.SYSTEM)
        assertEquals(SettingField.entries.toSet(), doc.fields.keys)
        assertTrue(doc.fields.values.all { it.atMs == 0L })
        assertEquals(VoxaraSettings.DEFAULTS, VoxaraSettings.from(doc))
    }

    @Test
    fun `phone settings are applied clamped, keep the phone's time and show as applied`() = runBlocking<Unit> {
        val t = System.currentTimeMillis() - 1_000
        val phone = SettingsDoc()
            .with(SettingField.ALERT_THRESHOLD, "90", t)
            .with(SettingField.SNOOZE_MINUTES, "30", t)
            .with(SettingField.TONO_ENABLED, "false", t)
            .with(SettingField.HAPTIC_STRENGTH, "STRONG", t)
            .with(SettingField.ADVISORY_HAPTICS, "false", t)
            .with(SettingField.HEADPHONE_TYPE, "OVER_EAR", t)
        // A tampered value: the watch clamps it again on the way in.
        val tampered = SettingsDoc.fromJson(phone.toJson().replace("\"30\"", "\"600\""))

        val own = VoxaraStore.settingsDoc(store.read(), LanguageChoice.SYSTEM)
        val merged = SettingsMerge.watchReceives(own, tampered, System.currentTimeMillis()).merged
        store.applySynced(merged.fields.filter { (f, v) -> own[f] != v })

        val p = store.read()
        assertEquals(90, p.alertSettings.thresholdDba)
        assertEquals(120, p.snoozeMinutes)
        assertEquals(false, p.tonoEnabled)
        assertEquals(HapticStrength.STRONG, p.hapticPrefs.strength)
        assertEquals(false, p.hapticPrefs.advisoryHaptics)
        assertEquals(HeadphoneCategory.OVER_EAR, p.usualHeadphones)
        assertEquals(t, p.settingStamps[SettingField.ALERT_THRESHOLD])
        val echoed = VoxaraStore.settingsDoc(p, LanguageChoice.SYSTEM)
        assertTrue(SettingsMerge.pending(tampered, echoed).isEmpty())
    }

    @Test
    fun `a change made on the watch stamps only the fields that changed`() = runBlocking<Unit> {
        val before = store.read()
        val next = before.alertSettings.copy(quietHoursEnabled = !before.alertSettings.quietHoursEnabled)
        store.saveAlertSettings(next)
        val after = store.read()
        val changed = SettingField.entries.filter { after.settingStamps[it] != before.settingStamps[it] }
        assertEquals(listOf(SettingField.QUIET_ENABLED), changed)
        store.saveAlertSettings(AlertSettings(quietHoursEnabled = before.alertSettings.quietHoursEnabled))
    }
}
