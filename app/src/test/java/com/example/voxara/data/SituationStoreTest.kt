package com.example.voxara.data

import com.example.voxara.core.situation.Situation
import com.example.voxara.core.situation.SituationChoice
import com.example.voxara.core.situation.SituationMode
import com.example.voxara.settings.LanguageChoice
import com.example.voxara.settings.SettingField
import com.example.voxara.settings.SettingsDoc
import com.example.voxara.settings.Stamped
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The watch store's situation mode: migration from the old "mode" key, Concert in and out, sync. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SituationStoreTest {

    private val store by lazy { VoxaraStore(RuntimeEnvironment.getApplication()) }

    @Test
    fun `an old URBAN or VOICE install becomes Street, before and after the one-time move`() = runBlocking<Unit> {
        for (legacy in listOf("URBAN", "VOICE")) {
            store.seedLegacyMode(legacy)
            // Read through the mapping even before migrateSituation() has run.
            assertEquals(SituationChoice.STREET, store.read().situationChoice)
            store.migrateSituation()
            val p = store.read()
            assertEquals(SituationChoice.STREET, p.situationChoice)
            assertEquals(SituationMode.STREET, Situation.resolve(p.situationChoice))
            // A migration is not a change the wearer made: a phone choice must still win.
            assertNull(p.settingStamps[SettingField.SITUATION_MODE])
            // Idempotent.
            store.migrateSituation()
            assertEquals(SituationChoice.STREET, store.read().situationChoice)
        }
    }

    @Test
    fun `an old CONCERT install stays Concert, with its 6 h cap counting from the move`() = runBlocking<Unit> {
        store.seedLegacyMode("CONCERT")
        val before = System.currentTimeMillis()
        store.migrateSituation()
        val p = store.read()
        assertEquals(SituationChoice.CONCERT, p.situationChoice)
        val started = p.concertStartedAtMs
        assertNotNull(started)
        assertTrue(started!! >= before)
    }

    @Test
    fun `a fresh install is Auto`() = runBlocking<Unit> {
        store.seedLegacyMode(null)
        store.migrateSituation()
        assertEquals(SituationChoice.AUTO, store.read().situationChoice)
    }

    @Test
    fun `leaving Concert returns to the choice before it, stamped so the phone follows`() = runBlocking<Unit> {
        store.seedLegacyMode(null)
        store.setSituationChoice(SituationChoice.WORK)
        store.setSituationChoice(SituationChoice.CONCERT)
        val inConcert = store.read()
        assertNotNull(inConcert.concertStartedAtMs)
        assertEquals(SituationChoice.WORK, inConcert.choiceBeforeConcert)
        // Choosing Concert again keeps the original start (the 6 h cap is not reset).
        store.setSituationChoice(SituationChoice.CONCERT)
        assertEquals(inConcert.concertStartedAtMs, store.read().concertStartedAtMs)

        assertEquals(SituationChoice.WORK, store.leaveConcert())
        val after = store.read()
        assertEquals(SituationChoice.WORK, after.situationChoice)
        assertNull(after.concertStartedAtMs)
        assertNull(after.choiceBeforeConcert)
        assertNotNull(after.settingStamps[SettingField.SITUATION_MODE])
        // Not in Concert: nothing to leave.
        assertNull(store.leaveConcert())
    }

    @Test
    fun `Concert chosen on the phone starts the cap on the watch and echoes back`() = runBlocking<Unit> {
        store.seedLegacyMode(null)
        val t = System.currentTimeMillis() - 1_000
        store.applySynced(mapOf(SettingField.SITUATION_MODE to Stamped("CONCERT", t)))
        val p = store.read()
        assertEquals(SituationChoice.CONCERT, p.situationChoice)
        assertNotNull(p.concertStartedAtMs)
        assertEquals(t, p.settingStamps[SettingField.SITUATION_MODE])
        val doc: SettingsDoc = VoxaraStore.settingsDoc(p, LanguageChoice.SYSTEM)
        assertEquals(Stamped("CONCERT", t), doc[SettingField.SITUATION_MODE])
        // And back to Auto from the phone clears the Concert state.
        store.applySynced(mapOf(SettingField.SITUATION_MODE to Stamped("AUTO", t + 10_000)))
        assertNull(store.read().concertStartedAtMs)
    }
}
