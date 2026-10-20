package com.example.voxara.phone.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.example.voxara.core.design.Accent
import com.example.voxara.settings.SettingField
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings → Appearance in [PhoneStore]: defaults (Purple, Light), persistence across store
 * instances, unknown values falling back, and that appearance is phone-only (not a synced field,
 * kept by "Delete all data").
 *
 * The stores sit on an in-memory DataStore that several [PhoneStore] instances can share (the
 * "file"): the file-backed DataStore cannot replace its file on the Windows JVM these tests run on.
 */
class AppearanceStoreTest {

    /** A DataStore<Preferences> over shared in-memory "file" contents. */
    private class MemoryDataStore(private val file: MutableStateFlow<Preferences>) : DataStore<Preferences> {
        private val lock = Mutex()
        override val data: Flow<Preferences> = file
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            lock.withLock { transform(file.value).also { file.value = it } }
    }

    private fun newFile() = MutableStateFlow(emptyPreferences())

    private fun <T> withStore(file: MutableStateFlow<Preferences>, block: suspend (PhoneStore) -> T): T =
        runBlocking { block(PhoneStore(MemoryDataStore(file))) }

    @Test
    fun `defaults are purple and light`() {
        val st = withStore(newFile()) { it.read() }
        assertEquals(Accent.PURPLE, st.accent)
        assertEquals(ThemeMode.LIGHT, st.themeMode)
        assertEquals(Accent.PURPLE, PhoneStoreState().accent)
        assertEquals(ThemeMode.LIGHT, PhoneStoreState().themeMode)
    }

    @Test
    fun `accent and theme persist across store instances`() {
        val file = newFile()
        withStore(file) {
            it.setAccent(Accent.EMERALD)
            it.setThemeMode(ThemeMode.SYSTEM)
        }
        val st = withStore(file) { it.read() }
        assertEquals(Accent.EMERALD, st.accent)
        assertEquals(ThemeMode.SYSTEM, st.themeMode)
    }

    @Test
    fun `every choice round-trips`() {
        val file = newFile()
        withStore(file) { store ->
            for (a in Accent.entries) {
                store.setAccent(a)
                assertEquals(a, store.read().accent)
            }
            for (m in ThemeMode.entries) {
                store.setThemeMode(m)
                assertEquals(m, store.read().themeMode)
            }
        }
    }

    @Test
    fun `delete all data keeps the appearance`() {
        val file = newFile()
        val st = withStore(file) {
            it.setAccent(Accent.BLUE)
            it.setThemeMode(ThemeMode.DARK)
            it.clearData()
            it.read()
        }
        assertEquals(Accent.BLUE, st.accent)
        assertEquals(ThemeMode.DARK, st.themeMode)
    }

    @Test
    fun `appearance is phone only and unknown values fall back`() {
        val synced = SettingField.entries.map { it.name.lowercase() }
        assertFalse(synced.any { "accent" in it || "theme" in it })
        assertEquals(ThemeMode.LIGHT, ThemeMode.parse("NEON"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.parse(null))
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
    }
}
