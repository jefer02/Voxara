package com.example.voxara.bench

import android.content.Context

/** Release builds: the debug bench driver does not exist. Same API, no behaviour. */
object Bench {
    @Volatile var target = 68.0

    @Suppress("UNUSED_PARAMETER")
    suspend fun run(context: Context) = Unit
}
