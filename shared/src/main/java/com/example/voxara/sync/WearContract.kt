package com.example.voxara.sync

/**
 * THE WEARABLE DATA LAYER CONTRACT between the Voxara watch app and the Voxara phone app. Both
 * apps must have the same applicationId and signing key or the Data Layer never connects them.
 *
 * Transport choices (Android docs: MessageClient cannot carry more than 100 KB; DataClient items
 * are for small state; ChannelClient is for files and streams):
 *  - DataClient (small, persistent, delivered when the devices reconnect):
 *      [PATH_STATUS] (watch → phone), [PATH_SETTINGS_PHONE] (phone → watch),
 *      [PATH_SETTINGS_WATCH] (watch → phone). Payload: one JSON byte array under [KEY_PAYLOAD].
 *  - MessageClient request/response (both devices connected): history is pulled by the phone,
 *      one UTC day and one ledger at a time, each response kept under [MAX_PAYLOAD_BYTES].
 *  - ChannelClient is not needed: no single payload can exceed the limit (see [LedgerCodec]).
 *  - The v1 listening spans keep their own path (`/voxara/listening`, PhoneSyncContract).
 *
 * Only numbers travel: minute energy/dose/seconds, alert tier and value, settings. No audio, no
 * titles, no app names, no device names or addresses.
 */
object WearContract {
    const val PROTOCOL = 1

    /** Declared in the watch app's res/values/wear.xml. */
    const val CAPABILITY_WATCH = "voxara_watch_app"
    /** Declared in the phone app's res/values/wear.xml. */
    const val CAPABILITY_PHONE = "voxara_phone_app"

    const val PATH_STATUS = "/voxara/status"
    const val PATH_SETTINGS_PHONE = "/voxara/settings/phone"
    const val PATH_SETTINGS_WATCH = "/voxara/settings/watch"
    const val KEY_PAYLOAD = "payload"

    /** Request/response paths (MessageClient.sendRequest). */
    const val RPC_PREFIX = "/voxara/rpc"
    const val RPC_STATUS = "$RPC_PREFIX/status"
    const val RPC_DIGEST = "$RPC_PREFIX/digest"
    const val RPC_DAY = "$RPC_PREFIX/day"
    const val RPC_ALERTS = "$RPC_PREFIX/alerts"
    const val RPC_DELETE = "$RPC_PREFIX/delete"

    /** One-way watch → phone: a LIMIT alert just fired (for the optional mirrored notification). */
    const val MSG_LIMIT_ALERT = "/voxara/alert/limit"

    /** Deep links the phone opens on the watch with RemoteActivityHelper. */
    const val LINK_SCHEME = "voxara"
    const val LINK_HOST = "watch"
    const val LINK_OPEN = "voxara://watch/open"
    const val LINK_CALIBRATION = "voxara://watch/calibration"

    /** Play Store page used for "Install on watch" (release builds). */
    const val STORE_URI = "market://details?id=com.example.voxara"

    /** Responses stay under this, with room for framing, below the 100 KB message limit. */
    const val MAX_PAYLOAD_BYTES = 90 * 1024

    /**
     * Days of history the phone asks for. The watch keeps 35 days; only days fully inside that
     * window are compared, so a day the watch has partly pruned never overwrites the phone's copy.
     */
    const val SYNC_WINDOW_DAYS = 33
}
