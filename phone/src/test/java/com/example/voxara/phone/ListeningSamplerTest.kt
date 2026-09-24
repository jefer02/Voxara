package com.example.voxara.phone

import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningSamplerTest {

    @Test
    fun `a span is the time since the previous sample, capped at 15 minutes`() {
        assertEquals(0.0, ListeningSampler.spanSeconds(0L, 1_000_000L), 0.0)       // first sample
        assertEquals(600.0, ListeningSampler.spanSeconds(1_000_000L, 1_600_000L), 1e-9)
        assertEquals(900.0, ListeningSampler.spanSeconds(1_000_000L, 9_000_000L), 1e-9)
        assertEquals(0.0, ListeningSampler.spanSeconds(2_000_000L, 1_000_000L), 0.0) // clock went back
    }

    @Test
    fun `the Data Layer contract matches the watch`() {
        // Must stay identical to app/.../core/headphones/PhoneListening.kt (PhoneSyncContract).
        assertEquals("/voxara/listening", ListeningSampler.PATH_PREFIX)
        assertEquals("end_ms", ListeningSampler.KEY_END_MS)
        assertEquals("seconds", ListeningSampler.KEY_SECONDS)
        assertEquals("volume_db", ListeningSampler.KEY_VOLUME_DB)
        assertEquals(900.0, ListeningSampler.MAX_SPAN_SECONDS, 0.0)
    }
}
