package com.example.voxara.sync

import android.util.Log
import com.example.voxara.audio.HeadphoneControl
import com.example.voxara.data.ExposureRepository
import com.example.voxara.service.PhoneListeningAlerts
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking

/**
 * THE PHONE'S HEADPHONES, ARRIVING (Wearable Data Layer, same app id and signature only). GMS starts this
 * service when something arrives, so it works with the watch app closed:
 *
 *  - [HeadphoneWire.PATH_LIVE] (MessageClient): the phone's state the moment it changes (real time);
 *  - [HeadphoneWire.PATH_STATE] (DataClient): the same state as ONE overwritten item, so a watch that was off
 *    or out of range reads the latest on reconnect (the sequence drops anything older than what it has);
 *  - [HeadphoneWire.PATH_CMD_LOWERED] (MessageClient): the phone's answer to "lower to safe volume";
 *  - the phone app's capability appearing or going: the link state, and the catch-up when it is back.
 *
 * While the dosimeter runs it wakes its listening loop (estimate, ledger, alert). When it does not, a phone
 * level over the recommended one still alerts, from the saved ledger figures ([PhoneListeningAlerts]).
 */
class HeadphoneSyncService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            HeadphoneWire.PATH_LIVE -> PhoneListeningState.decode(event.data)?.let { onState(it) }
            HeadphoneWire.PATH_CMD_LOWERED -> LowerVolumeResult.decode(event.data)?.let { HeadphoneControl.onResult(it) }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            if (event.dataItem.uri.path != HeadphoneWire.PATH_STATE) return@forEach
            val bytes = runCatching {
                DataMapItem.fromDataItem(event.dataItem).dataMap.getByteArray(HeadphoneWire.KEY_PAYLOAD)
            }.getOrNull() ?: return@forEach
            PhoneListeningState.decode(bytes)?.let { onState(it) }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        if (capabilityInfo.name != WearContract.CAPABILITY_PHONE) return
        // Reachable now, or gone: record it, and when it is back catch it up (settings, status, the outbox).
        runCatching { runBlocking { PhoneLink.refresh(this@HeadphoneSyncService) } }
            .onFailure { Log.w(TAG, "link refresh failed: ${it.javaClass.simpleName}") }
    }

    private fun onState(state: PhoneListeningState) {
        // A state arriving is also proof the phone is reachable.
        PhoneLink.observeSend(ok = true)
        if (!ExposureRepository.onPhoneListening(state)) return
        if (!ExposureRepository.serviceActive) {
            runCatching { runBlocking { PhoneListeningAlerts.evaluate(this@HeadphoneSyncService, state) } }
                .onFailure { Log.w(TAG, "phone listening alert check failed", it) }
        }
    }

    private companion object { const val TAG = "VoxaraHpSync" }
}
