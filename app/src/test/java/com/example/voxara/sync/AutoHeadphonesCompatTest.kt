package com.example.voxara.sync

import com.example.voxara.core.headphones.HeadphoneAlert
import com.example.voxara.core.headphones.HeadphoneModeChoice
import com.example.voxara.core.headphones.LayerSource
import com.example.voxara.core.headphones.LowerUi
import com.example.voxara.core.headphones.OutputKind
import com.example.voxara.data.ExposureRepository
import com.example.voxara.data.VoxaraStore
import com.example.voxara.service.hpLowerText
import com.example.voxara.settings.LanguageChoice
import com.example.voxara.settings.SettingField
import com.example.voxara.settings.SettingsDoc
import com.example.voxara.settings.SettingsMerge
import com.example.voxara.settings.Stamped
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * HEADPHONES MODE IS AUTOMATIC NOW — what an older watch or phone may still send must be ignored without
 * breaking anything, and the pieces the headphone alert's screen stands on (dismiss, the phone's live state,
 * "lower" progress, the words of its results) behave.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutoHeadphonesCompatTest {

    private val store by lazy { VoxaraStore(RuntimeEnvironment.getApplication()) }

    // ------------------------------------------------------------------ the old manual field

    @Test
    fun `an older phone's manual On or Off is ignored, the mode stays Auto, and the older app settles on Auto`() = runBlocking<Unit> {
        val t = System.currentTimeMillis() - 500
        for (manual in listOf("ON", "OFF")) {
            val own = VoxaraStore.settingsDoc(store.read(), LanguageChoice.SYSTEM)
            val phone = SettingsDoc().with(SettingField.HEADPHONE_MODE, manual, t)
            val merged = SettingsMerge.watchReceives(own, phone, System.currentTimeMillis()).merged
            store.applySynced(merged.fields.filter { (f, v) -> own[f] != v })
            val p = store.read()
            assertEquals(HeadphoneModeChoice.AUTO, p.headphoneMode)
            // What the watch publishes back is Auto, and the older phone, taking the watch's document by its own
            // merge rule, settles on Auto too (the watch wins ties, so a manual value cannot linger there).
            val echoed = VoxaraStore.settingsDoc(p, LanguageChoice.SYSTEM)
            assertEquals("AUTO", echoed[SettingField.HEADPHONE_MODE]!!.value)
            val oldPhone = SettingsMerge.phoneReceives(phone, echoed, System.currentTimeMillis()).merged
            assertEquals("$manual: the older phone settles", "AUTO", oldPhone[SettingField.HEADPHONE_MODE]!!.value)
        }
    }

    @Test
    fun `a stored manual value from an older version is read as Auto`() = runBlocking<Unit> {
        store.applySynced(mapOf(SettingField.HEADPHONE_MODE to Stamped("ON", System.currentTimeMillis())))
        assertEquals(HeadphoneModeChoice.AUTO, store.read().headphoneMode)
    }

    @Test
    fun `the field is still part of the document, so an older app does not break`() {
        val doc = VoxaraStore.settingsDoc(VoxaraStore.Persisted(), LanguageChoice.SYSTEM)
        assertTrue(SettingField.HEADPHONE_MODE in doc.fields)
        assertEquals("AUTO", doc[SettingField.HEADPHONE_MODE]!!.value)
    }

    // ------------------------------------------------------------------ the alert's screen state

    private fun alert(tier: Int = 1, source: LayerSource = LayerSource.WATCH) = HeadphoneAlert(tier, 85.0, 1_000L, source)

    @Test
    fun `dismiss closes the panel for this tier and writes nothing else`() {
        ExposureRepository.update { it.copy(headphoneAlert = alert(), lowerVolume = LowerUi.Idle) }
        ExposureRepository.dismissHeadphoneAlert()
        val s = ExposureRepository.state.value
        assertTrue(s.headphoneAlert!!.dismissed)
        // A higher tier is a new alert: the panel is back (the engine's tiers decide, not a mute).
        ExposureRepository.update { it.copy(headphoneAlert = alert(tier = 2)) }
        assertFalse(ExposureRepository.state.value.headphoneAlert!!.dismissed)
    }

    @Test
    fun `lowering shows progress and what came of it, and dismiss leaves a running request alone`() {
        ExposureRepository.update { it.copy(headphoneAlert = alert(), lowerVolume = LowerUi.Working) }
        ExposureRepository.dismissHeadphoneAlert()
        assertEquals(LowerUi.Working, ExposureRepository.state.value.lowerVolume)
        ExposureRepository.setLowerVolume(LowerUi.Done(LowerOutcome.FIXED_VOLUME, null))
        ExposureRepository.dismissHeadphoneAlert()
        assertEquals(LowerUi.Idle, ExposureRepository.state.value.lowerVolume)
    }

    @Test
    fun `the phone's live state is ordered by its sequence, an old or repeated one is dropped`() {
        fun st(seq: Long, at: Long) = PhoneListeningState(at, seq, true, true, OutputKind.BLUETOOTH_A2DP, false, -21.0, 8, 15)
        val base = System.currentTimeMillis() + 10_000_000
        assertTrue(ExposureRepository.onPhoneListening(st(base + 5, base + 5)))
        assertFalse("a repeat", ExposureRepository.onPhoneListening(st(base + 5, base + 5)))
        assertFalse("older news", ExposureRepository.onPhoneListening(st(base + 2, base + 2)))
        assertTrue(ExposureRepository.onPhoneListening(st(base + 9, base + 9)))
        assertEquals(base + 9, ExposureRepository.phoneListening!!.sequence)
    }

    // ------------------------------------------------------------------ what the wearer is told

    @Test
    fun `every outcome of lowering has words, and a failure never reads as success`() {
        val res = RuntimeEnvironment.getApplication().resources
        val texts = LowerOutcome.entries.associateWith { hpLowerText(res, it, 79.0) }
        assertTrue(texts.values.all { it.isNotBlank() })
        assertEquals(LowerOutcome.entries.size, texts.values.toSet().size)
        assertTrue(texts.getValue(LowerOutcome.LOWERED).contains("79"))
        assertNotEquals(texts.getValue(LowerOutcome.LOWERED), texts.getValue(LowerOutcome.FAILED))
        assertTrue(texts.getValue(LowerOutcome.UNREACHABLE).contains("phone", ignoreCase = true))
        assertTrue(texts.getValue(LowerOutcome.FIXED_VOLUME).contains("fixed", ignoreCase = true))
    }

    @Test
    @Config(qualifiers = "es")
    fun `and in Spanish`() {
        val res = RuntimeEnvironment.getApplication().resources
        val texts = LowerOutcome.entries.associateWith { hpLowerText(res, it, 79.0) }
        assertEquals(LowerOutcome.entries.size, texts.values.toSet().size)
        assertTrue(texts.getValue(LowerOutcome.UNREACHABLE).contains("teléfono"))
        assertTrue(texts.getValue(LowerOutcome.FIXED_VOLUME).contains("fijo"))
    }

    // ------------------------------------------------------------------ the watch's pending queue

    @Test
    fun `the same alert queued twice is one item, and a stale copy is dropped`() {
        val app = RuntimeEnvironment.getApplication()
        val box = PhoneOutbox
        val a = com.example.voxara.sync.MirroredAlert(com.example.voxara.core.alerts.AlertKind.DAILY_100, 100.0, 100, 1_700_000_000_000L)
        val base = box.size(app)
        box.enqueueMirror(app, a, nowMs = a.atMs)
        box.enqueueMirror(app, a, nowMs = a.atMs + 1_000)
        assertEquals(base + 1, box.size(app))
        // Older than ten minutes: the phone would refuse it, so it is not kept.
        box.enqueueMirror(app, a.copy(atMs = a.atMs + 1), nowMs = a.atMs + 11 * 60_000)
        assertEquals(base + 1, box.size(app))
    }
}
