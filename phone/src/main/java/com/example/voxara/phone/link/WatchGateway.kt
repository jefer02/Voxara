package com.example.voxara.phone.link

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.voxara.link.WatchNode
import com.example.voxara.sync.WearContract
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import androidx.wear.remote.interactions.RemoteActivityHelper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The Wear OS / Play services layer is missing or too old on this phone. */
class WearApiUnavailable(cause: Throwable? = null) : Exception("Wearable API unavailable", cause)

/**
 * Everything the phone does over the Wearable Data Layer, behind one interface so the sync and
 * link logic is tested with a fake. Voxara never pairs Bluetooth devices: these calls only use the
 * connection the Galaxy Wearable (or Wear OS) app already made.
 */
interface WatchGateway {
    /** Watches connected right now. Throws [WearApiUnavailable]. */
    suspend fun connectedWatches(): List<WatchNode>

    /** Ids of connected watches that have the Voxara watch app (declared capability). */
    suspend fun watchesWithApp(): Set<String>

    /** One request/response exchange (MessageClient.sendRequest). Throws when unreachable. */
    suspend fun request(nodeId: String, path: String, body: ByteArray): ByteArray

    /** Publishes the phone's settings document (DataClient, delivered when the watch reconnects). */
    suspend fun publishSettings(json: String)

    /** The latest JSON payload a watch published at [path], or null. */
    suspend fun latestPayload(nodeId: String, path: String): String?

    /** Opens [uri] on the watch (RemoteActivityHelper). Throws when it cannot. */
    suspend fun openOnWatch(nodeId: String, uri: String)
}

class DataLayerGateway(context: Context) : WatchGateway {

    private val app = context.applicationContext
    private val executor by lazy { Executors.newSingleThreadExecutor() }

    private fun checkPlayServices() {
        val code = runCatching { GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(app) }
            .getOrDefault(ConnectionResult.SERVICE_MISSING)
        if (code != ConnectionResult.SUCCESS) throw WearApiUnavailable()
    }

    private suspend fun <T> wear(block: suspend () -> T): T {
        checkPlayServices()
        return try {
            block()
        } catch (e: ApiException) {
            // No Wear OS / Galaxy Wearable companion on this phone: the Wearable API is absent.
            if (e.statusCode == CommonStatusCodes.API_NOT_CONNECTED) throw WearApiUnavailable(e) else throw e
        }
    }

    override suspend fun connectedWatches(): List<WatchNode> = wear {
        Wearable.getNodeClient(app).connectedNodes.await().map { WatchNode(it.id, it.displayName, it.isNearby) }
    }

    override suspend fun watchesWithApp(): Set<String> = wear {
        Wearable.getCapabilityClient(app)
            .getCapability(WearContract.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE)
            .await().nodes.map { it.id }.toSet()
    }

    override suspend fun request(nodeId: String, path: String, body: ByteArray): ByteArray = wear {
        try {
            withTimeout(REQUEST_TIMEOUT_MS) { Wearable.getMessageClient(app).sendRequest(nodeId, path, body).await() }
        } catch (e: TimeoutCancellationException) {
            // Not a cancellation of the caller: the watch did not answer (asleep, out of range, app killed).
            throw IOException("the watch did not answer within ${REQUEST_TIMEOUT_MS / 1000} s", e)
        }
    }

    override suspend fun publishSettings(json: String) {
        wear {
            val request = PutDataMapRequest.create(WearContract.PATH_SETTINGS_PHONE).apply {
                dataMap.putByteArray(WearContract.KEY_PAYLOAD, json.toByteArray(Charsets.UTF_8))
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(app).putDataItem(request).await()
        }
    }

    override suspend fun latestPayload(nodeId: String, path: String): String? = wear {
        val uri = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).authority(nodeId).path(path).build()
        val buffer = Wearable.getDataClient(app).getDataItems(uri).await()
        try {
            buffer.firstOrNull()?.let { item ->
                DataMapItem.fromDataItem(item).dataMap.getByteArray(WearContract.KEY_PAYLOAD)?.toString(Charsets.UTF_8)
            }
        } finally {
            buffer.release()
        }
    }

    override suspend fun openOnWatch(nodeId: String, uri: String) {
        checkPlayServices()
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse(uri))
        val future = RemoteActivityHelper(app, executor).startRemoteActivity(intent, nodeId)
        suspendCancellableCoroutine { cont ->
            future.addListener({
                runCatching { future.get() }
                    .onSuccess { cont.resume(Unit) }
                    .onFailure { cont.resumeWithException(it.cause ?: it) }
            }, executor)
            cont.invokeOnCancellation { future.cancel(true) }
        }
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 20_000L
    }
}
