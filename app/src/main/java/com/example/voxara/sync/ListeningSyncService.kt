package com.example.voxara.sync

import android.util.Log
import com.example.voxara.core.headphones.OutputKind
import com.example.voxara.core.headphones.PhoneListeningSample
import com.example.voxara.core.headphones.PhoneSyncContract
import com.example.voxara.core.headphones.phoneSampleToMinutes
import com.example.voxara.core.headphones.resolvePhoneMinute
import com.example.voxara.core.ledger.ExposureKind
import com.example.voxara.data.ExposureDb
import com.example.voxara.data.ExposureRepository
import com.example.voxara.data.VoxaraStore
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking

/**
 * Receives listening spans from the phone companion (Wearable Data Layer, same app id and
 * signature only). Each span becomes headphone-ledger minutes with origin = phone, the double-count
 * rule is applied against the ambient minutes the watch recorded, and the data item is deleted
 * once stored so nothing accumulates in the Data Layer.
 */
class ListeningSyncService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val db = ExposureDb.get(this)
        val usual = runBlocking { VoxaraStore(this@ListeningSyncService).read().usualHeadphones }
        var changed = false
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            val item = event.dataItem
            if (item.uri.path?.startsWith(PhoneSyncContract.PATH_PREFIX) != true) return@forEach
            val map = DataMapItem.fromDataItem(item).dataMap
            val sample = PhoneListeningSample(
                endMs = map.getLong(PhoneSyncContract.KEY_END_MS),
                seconds = map.getDouble(PhoneSyncContract.KEY_SECONDS),
                volumeDb = map.getDouble(PhoneSyncContract.KEY_VOLUME_DB, Double.NaN).takeIf { it.isFinite() },
                index = map.getInt(PhoneSyncContract.KEY_INDEX),
                max = map.getInt(PhoneSyncContract.KEY_MAX),
                output = runCatching { OutputKind.valueOf(map.getString(PhoneSyncContract.KEY_OUTPUT).orEmpty()) }
                    .getOrDefault(OutputKind.OTHER),
            )
            for (minute in phoneSampleToMinutes(sample, usual)) {
                val ambient = db.range(minute.minute, minute.minute + 1, ExposureKind.AMBIENT).firstOrNull()
                val decision = resolvePhoneMinute(minute, ambient)
                if (decision.dropAmbient) db.clear(minute.minute, minute.minute + 1, ExposureKind.AMBIENT)
                if (decision.keepHeadphone) db.put(minute)
                changed = changed || decision.keepHeadphone || decision.dropAmbient
            }
            // Acknowledge: the phone's copy is no longer needed.
            runCatching { Tasks.await(Wearable.getDataClient(this).deleteDataItems(item.uri)) }
                .onFailure { Log.w("VoxaraSync", "could not delete synced item", it) }
        }
        if (changed) ExposureRepository.ledgerGeneration.incrementAndGet()
    }
}
