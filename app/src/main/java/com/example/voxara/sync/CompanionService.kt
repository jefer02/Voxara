package com.example.voxara.sync

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking

/**
 * The Voxara phone app talking to this watch (same app id and signature only):
 *  - requests under /voxara/rpc (status, history digests and days, alerts, delete) are answered
 *    from the watch's own ledger and alert log;
 *  - the phone's settings document (/voxara/settings/phone) is merged and applied.
 *
 * Callbacks run on a background thread, so the database and store reads here are fine. A bad
 * request fails that request only; it never reaches the dosimeter or the alert engine.
 */
class CompanionService : WearableListenerService() {

    override fun onRequest(nodeId: String, path: String, request: ByteArray): Task<ByteArray>? {
        if (!path.startsWith(WearContract.RPC_PREFIX)) return null
        return try {
            val answer = CompanionLink.responder(this).handle(path, request)
                ?: return Tasks.forException(IllegalArgumentException("unknown path $path"))
            Tasks.forResult(answer)
        } catch (e: Exception) {
            Log.w(TAG, "request $path failed", e)
            Tasks.forException(e)
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            if (event.dataItem.uri.path != WearContract.PATH_SETTINGS_PHONE) return@forEach
            val bytes = DataMapItem.fromDataItem(event.dataItem).dataMap.getByteArray(WearContract.KEY_PAYLOAD)
                ?: return@forEach
            runCatching { runBlocking { CompanionLink.applyPhoneSettings(this@CompanionService, bytes.toString(Charsets.UTF_8)) } }
                .onSuccess { if (it.isNotEmpty()) Log.i(TAG, "applied settings from the phone: $it") }
                .onFailure { Log.w(TAG, "could not apply the phone's settings", it) }
        }
    }

    private companion object {
        const val TAG = "VoxaraCompanion"
    }
}
