package com.example.voxara.phone.sync

import android.util.Log
import com.example.voxara.phone.MirroredAlerts
import com.example.voxara.phone.PhoneGraph
import com.example.voxara.phone.widget.DoseWidget
import com.example.voxara.phone.listening.PhoneListeningService
import com.example.voxara.phone.listening.WatchBridge
import com.example.voxara.sync.HeadphoneWire
import com.example.voxara.sync.LowerVolumeRequest
import com.example.voxara.sync.NotHeadphonesRequest
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
        when (event.path) {
            WearContract.MSG_LIMIT_ALERT ->
                runCatching { runBlocking { MirroredAlerts.onLimitAlert(this@PhoneListenerService, event.data) } }
                    .onFailure { Log.w(TAG, "mirrored alert failed", it) }
            // The watch asks for the phone's music volume to come down: do it, read it back, and say what happened.
            HeadphoneWire.PATH_CMD_LOWER -> {
                val request = LowerVolumeRequest.decode(event.data) ?: return
                val node = event.sourceNodeId
                runCatching {
                    runBlocking {
                        PhoneGraph.sync.observeReachable(true)
                        val result = PhoneListeningService.lower(this@PhoneListenerService)
                        WatchBridge.reply(this@PhoneListenerService, node, result.copy(id = request.id))
                    }
                }.onFailure { Log.w(TAG, "lower command failed: ${it.javaClass.simpleName}") }
            }
            // The wearer said on the watch that the phone's output is not headphones: leave it out (and remember a speaker).
            HeadphoneWire.PATH_CMD_NOT_HEADPHONES -> {
                val request = NotHeadphonesRequest.decode(event.data) ?: return
                runCatching {
                    runBlocking {
                        PhoneGraph.sync.observeReachable(true)
                        PhoneListeningService.notHeadphones(this@PhoneListenerService, request.speaker)
                    }
                }.onFailure { Log.w(TAG, "not-headphones command failed: ${it.javaClass.simpleName}") }
            }
        }
    }

    /**
     * The watch's Voxara app appeared or went. Looked at at once, and when it is back: a sync and the queued
     * headphone listening, with no action from the wearer (this service is started by Google Play services,
     * so it works with the app closed).
     */
    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        runCatching {
            runBlocking {
                PhoneGraph.sync.onWatchEvent {
                    WatchBridge.flushSpans(this@PhoneListenerService)
                    PhoneListeningService.resendState()
                }
            }
        }.onFailure { Log.w(TAG, "link change not handled: ${it.javaClass.simpleName}") }
    }

    private companion object {
        const val TAG = "VoxaraPhone"
    }
}
