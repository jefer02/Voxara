package com.example.voxara.phone.sync

import android.util.Log
import com.example.voxara.phone.MirroredAlerts
import com.example.voxara.phone.PhoneGraph
import com.example.voxara.phone.widget.DoseWidget
import com.example.voxara.sync.WearContract
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking

/**
 * What the watch pushes to the phone: its status and settings documents (DataClient), the
 * optional copy of a 100% alert (MessageClient), and capability changes (the watch app installed
 * or removed). Runs on a background thread.
 */
class PhoneListenerService : WearableListenerService() {

    override fun onCreate() {
        super.onCreate()
        PhoneGraph.init(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            val path = event.dataItem.uri.path ?: return@forEach
            val json = runCatching {
                DataMapItem.fromDataItem(event.dataItem).dataMap.getByteArray(WearContract.KEY_PAYLOAD)?.toString(Charsets.UTF_8)
            }.getOrNull() ?: return@forEach
            runCatching {
                runBlocking {
                    when (path) {
                        WearContract.PATH_SETTINGS_WATCH -> PhoneGraph.sync.onWatchSettings(json)
                        WearContract.PATH_STATUS -> {
                            PhoneGraph.sync.onStatus(json)
                            DoseWidget.refresh(this@PhoneListenerService)
                        }
                    }
                }
            }.onFailure { Log.w(TAG, "could not read $path", it) }
        }
    }

    /** The watch's optional copy of a 100% alert (shown only if the wearer turned it on). */
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearContract.MSG_LIMIT_ALERT) return
        runCatching { runBlocking { MirroredAlerts.onLimitAlert(this@PhoneListenerService, event.data) } }
            .onFailure { Log.w(TAG, "mirrored alert failed", it) }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        runCatching { runBlocking { PhoneGraph.sync.refreshLink() } }
    }

    private companion object {
        const val TAG = "VoxaraPhone"
    }
}
