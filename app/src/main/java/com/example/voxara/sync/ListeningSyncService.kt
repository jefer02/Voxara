package com.example.voxara.sync

import android.util.Log
import com.example.voxara.core.headphones.OutputKind
import com.example.voxara.core.headphones.PhoneListeningSample
import com.example.voxara.core.headphones.PhoneSyncContract
import com.example.voxara.core.headphones.PhoneSpanReceiver
import com.example.voxara.link.SeenKeys
import com.example.voxara.data.ExposureDb
import com.example.voxara.data.ExposureRepository
import com.example.voxara.data.VoxaraStore
import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking

/**
 * Receives listening spans from the phone companion (Wearable Data Layer, same app id and
 * signature only). Each span becomes headphone-ledger minutes with origin = phone, ADDED to what the phone
 * already reported for those minutes (a minute can arrive as several spans), the double-count rule is applied
 * against the ambient minutes the watch recorded, and the data item is deleted once stored so nothing
 * accumulates in the Data Layer.
 *
 * Adding is not idempotent, so each span is applied ONCE: its path (which carries the span's end time) is
 * remembered in [SeenKeys], persisted, and a second delivery of the same span (the phone re-put it after this
 * service had already consumed and deleted it) is deleted without being counted.
 */
class ListeningSyncService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val db = ExposureDb.get(this)
        val usual = runBlocking { VoxaraStore(this@ListeningSyncService).read().usualHeadphones }
        var changed = false
        synchronized(LOCK) {
            val seen = SeenKeys.decode(prefs().getString(KEY_SEEN, null))
            val receiver = PhoneSpanReceiver(db, seen)
            val now = System.currentTimeMillis()
            dataEvents.forEach { event ->
                if (event.type != DataEvent.TYPE_CHANGED) return@forEach
                val item = event.dataItem
                val path = item.uri.path ?: return@forEach
                if (!path.startsWith(PhoneSyncContract.PATH_PREFIX)) return@forEach
                val map = DataMapItem.fromDataItem(item).dataMap
                val sample = PhoneListeningSample(
                    endMs = map.getLong(PhoneSyncContract.KEY_END_MS),
                    seconds = map.getDouble(PhoneSyncContract.KEY_SECONDS),
                    volumeDb = map.getDouble(PhoneSyncContract.KEY_VOLUME_DB, Double.NaN).takeIf { it.isFinite() },
                    index = map.getInt(PhoneSyncContract.KEY_INDEX),
                    max = map.getInt(PhoneSyncContract.KEY_MAX),
                    output = runCatching { OutputKind.valueOf(map.getString(PhoneSyncContract.KEY_OUTPUT).orEmpty()) }
                        .getOrDefault(OutputKind.OTHER),
                    coarse = map.getBoolean(PhoneSyncContract.KEY_COARSE, true),
                )
                // A repeat (null) is deleted without being counted.
                // A failing write (database busy or full) costs this one span, never the rest of the batch: its key is
                // already recorded, so counting it later could only count it twice.
                val result = try {
                    receiver.receive(path, sample, usual, now) { prefs().edit().putString(KEY_SEEN, seen.encode()).commit() }
                } catch (e: Exception) {
                    Log.w("VoxaraSync", "span not stored: ${e.javaClass.simpleName}")
                    null
                }
                if (result != null) {
                    changed = changed || result.changed
                    // Headphones were playing on the phone: headphones mode reads this (labelled "from the phone").
                    if (result.heard) ExposureRepository.phoneHeadphonesAtMs = maxOf(ExposureRepository.phoneHeadphonesAtMs, minOf(sample.endMs, now))
                }
                // Acknowledge: the phone's copy is no longer needed.
                runCatching { Tasks.await(Wearable.getDataClient(this).deleteDataItems(item.uri)) }
                    .onFailure { Log.w("VoxaraSync", "could not delete synced item", it) }
            }
        }
        if (changed) ExposureRepository.ledgerGeneration.incrementAndGet()
    }

    private fun prefs() = getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private companion object {
        const val FILE = "voxara_listening_seen"
        const val KEY_SEEN = "keys"
        val LOCK = Any()
    }
}
