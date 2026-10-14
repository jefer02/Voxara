package com.example.voxara.phone.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.voxara.link.SyncProblem
import com.example.voxara.link.SyncRecord
import com.example.voxara.settings.SettingsDoc
import com.example.voxara.sync.WatchStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.phoneStore: DataStore<Preferences> by preferencesDataStore("voxara_phone")

/** Everything the phone keeps besides the history: settings documents, watch status, sync record. */
data class PhoneStoreState(
    /** The phone's view of the synced settings (its own changes plus what it took from the watch). */
    val phoneSettings: SettingsDoc = SettingsDoc(),
    /** The last settings document the watch published; null before the first one arrives. */
    val watchSettings: SettingsDoc? = null,
    /** The last status the watch reported (with its own time); null before the first sync. */
    val status: WatchStatus? = null,
    val sync: SyncRecord = SyncRecord(),
    /** The watch the wearer chose when several have the app. Local only, never exported. */
    val preferredWatchId: String? = null,
    val setupDone: Boolean = false,
    val retentionDays: Int = DEFAULT_RETENTION_DAYS,
    /** Mirror 100% alerts from the watch as phone notifications. OFF by default. */
    val mirrorAlerts: Boolean = false,
    /** "Delete all data" could not reach the watch yet; retried on the next sync. */
    val deletePending: Boolean = false,
    /** Newest alert time already mirrored, so each alert notifies at most once. */
    val lastMirroredAlertMs: Long = 0L,
) {
    companion object {
        const val DEFAULT_RETENTION_DAYS = 90
        val RETENTION_CHOICES = listOf(30, 90, 180, 365)
    }
}

class PhoneStore(private val ds: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.phoneStore)

    private object Keys {
        val phoneSettings = stringPreferencesKey("phone_settings")
        val watchSettings = stringPreferencesKey("watch_settings")
        val status = stringPreferencesKey("watch_status")
        val lastSuccess = longPreferencesKey("sync_last_success")
        val lastAttempt = longPreferencesKey("sync_last_attempt")
        val lastProblem = stringPreferencesKey("sync_last_problem")
        val preferred = stringPreferencesKey("preferred_watch")
        val setupDone = booleanPreferencesKey("setup_done")
        val retention = intPreferencesKey("retention_days")
        val mirror = booleanPreferencesKey("mirror_alerts")
        val deletePending = booleanPreferencesKey("delete_pending")
        val lastMirrored = longPreferencesKey("last_mirrored_alert")
    }

    val flow: Flow<PhoneStoreState> = ds.data.map { p ->
        PhoneStoreState(
            phoneSettings = SettingsDoc.fromJson(p[Keys.phoneSettings]),
            watchSettings = p[Keys.watchSettings]?.let { SettingsDoc.fromJson(it) },
            status = WatchStatus.fromJson(p[Keys.status]),
            sync = SyncRecord(
                lastSuccessMs = p[Keys.lastSuccess],
                lastAttemptMs = p[Keys.lastAttempt],
                lastProblem = p[Keys.lastProblem]?.let { runCatching { SyncProblem.valueOf(it) }.getOrNull() },
            ),
            preferredWatchId = p[Keys.preferred],
            setupDone = p[Keys.setupDone] ?: false,
            retentionDays = (p[Keys.retention] ?: PhoneStoreState.DEFAULT_RETENTION_DAYS).coerceIn(7, 3650),
            mirrorAlerts = p[Keys.mirror] ?: false,
            deletePending = p[Keys.deletePending] ?: false,
            lastMirroredAlertMs = p[Keys.lastMirrored] ?: 0L,
        )
    }

    suspend fun read(): PhoneStoreState = flow.first()

    suspend fun setPhoneSettings(doc: SettingsDoc) = ds.edit { it[Keys.phoneSettings] = doc.toJson() }

    /** Read-modify-write, so two quick edits never lose one another. */
    suspend fun updatePhoneSettings(transform: (SettingsDoc) -> SettingsDoc) = ds.edit {
        it[Keys.phoneSettings] = transform(SettingsDoc.fromJson(it[Keys.phoneSettings])).toJson()
    }

    suspend fun setWatchSettings(doc: SettingsDoc) = ds.edit { it[Keys.watchSettings] = doc.toJson() }
    suspend fun setStatus(status: WatchStatus) = ds.edit { it[Keys.status] = status.toJson() }

    suspend fun syncSucceeded(atMs: Long) = ds.edit {
        it[Keys.lastSuccess] = atMs
        it[Keys.lastAttempt] = atMs
        it.remove(Keys.lastProblem)
    }

    suspend fun syncFailed(atMs: Long, problem: SyncProblem) = ds.edit {
        it[Keys.lastAttempt] = atMs
        it[Keys.lastProblem] = problem.name
    }

    suspend fun setPreferredWatch(id: String?) = ds.edit { if (id == null) it.remove(Keys.preferred) else it[Keys.preferred] = id }
    suspend fun setSetupDone(v: Boolean) = ds.edit { it[Keys.setupDone] = v }
    suspend fun setRetentionDays(v: Int) = ds.edit { it[Keys.retention] = v }
    suspend fun setMirrorAlerts(v: Boolean) = ds.edit { it[Keys.mirror] = v }
    suspend fun setDeletePending(v: Boolean) = ds.edit { it[Keys.deletePending] = v }
    suspend fun setLastMirroredAlert(ms: Long) = ds.edit { it[Keys.lastMirrored] = ms }

    /** "Delete all data": the watch's reports go with the history; settings and setup stay. */
    suspend fun clearData() = ds.edit {
        it.remove(Keys.status)
        it.remove(Keys.lastMirrored)
    }
}
