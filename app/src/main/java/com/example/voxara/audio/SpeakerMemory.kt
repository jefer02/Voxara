package com.example.voxara.audio

import com.example.voxara.data.ExposureRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The watch's "Not headphones" memory: loads this install's salt and the remembered speakers into the process-wide
 * list every [HeadphoneWatcher] reads, and writes the list back whenever it changes. Local to the watch (never
 * synced, never sent); only salted digests are stored, never an address or a name.
 */
object SpeakerMemory {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    @Volatile private var loaded = false

    /** Loads once per process (the service and the activity both ask; whichever comes first does it). */
    suspend fun ensureLoaded() = lock.withLock {
        if (loaded) return@withLock
        val store = ExposureRepository.store
        val (salt, keys) = store.speakerMemory()
        HeadphoneWatcher.salt = salt
        HeadphoneWatcher.ignored.loadSpeakers(keys)
        HeadphoneWatcher.onSpeakersChanged = { now ->
            ExposureRepository.update { it.copy(rememberedSpeakers = now.size) }
            scope.launch { store.setSpeakers(now) }
        }
        ExposureRepository.update { it.copy(rememberedSpeakers = keys.size) }
        loaded = true
    }
}
