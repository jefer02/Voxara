package com.example.voxara.core

import com.example.voxara.core.headphones.Confidence
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.core.headphones.ORIGIN_PHONE
import com.example.voxara.core.headphones.OutputKind
import com.example.voxara.core.headphones.PhoneListeningSample
import com.example.voxara.core.headphones.PhoneSyncContract
import com.example.voxara.core.headphones.phoneSampleToMinutes
import com.example.voxara.core.headphones.toJson
import com.example.voxara.core.headphones.resolvePhoneMinute
import com.example.voxara.core.ledger.ExposureKind
import com.example.voxara.core.ledger.MinuteRecord
import com.example.voxara.core.ledger.energyOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phone companion spans on the watch: conversion, capping, and the double-count rule. */
class PhoneListeningTest {

    private fun sample(seconds: Double, output: OutputKind = OutputKind.BLUETOOTH_A2DP, index: Int = 8) =
        PhoneListeningSample(endMs = 30 * 60_000L, seconds = seconds, volumeDb = -20.0, index = index, max = 15, output = output)

    @Test
    fun `a 15 minute span becomes 15 phone-origin headphone minutes`() {
        val minutes = phoneSampleToMinutes(sample(900.0), HeadphoneCategory.EARBUDS)
        assertEquals(15, minutes.size)
        assertEquals(900.0, minutes.sumOf { it.measuredSeconds }, 1e-6)
        assertTrue(minutes.all { it.kind == ExposureKind.HEADPHONE && it.origin == ORIGIN_PHONE })
        assertTrue(minutes.all { it.confidence == Confidence.VERY_LOW.code })
        assertTrue(minutes.all { it.category == HeadphoneCategory.EARBUDS.code })
        assertEquals(80.0, minutes[0].laeq!!, 1e-9)                 // 100 dBA assumed - 20 dB volume
    }

    @Test
    fun `a span cut at the change itself keeps the estimate's own confidence`() {
        val exact = phoneSampleToMinutes(sample(120.0).copy(coarse = false), HeadphoneCategory.EARBUDS)
        assertTrue(exact.all { it.confidence == Confidence.LOW.code })            // a dB curve from the platform
        val noCurve = phoneSampleToMinutes(sample(120.0).copy(coarse = false, volumeDb = null), null)
        assertTrue(noCurve.all { it.confidence == Confidence.VERY_LOW.code })      // index mapped linearly
        // The default (and an older phone, which sends no flag) is the coarse, VERY LOW one.
        assertTrue(phoneSampleToMinutes(sample(120.0), null).all { it.confidence == Confidence.VERY_LOW.code })
    }

    @Test
    fun `a span survives the outbox text form`() {
        val s = sample(300.0).copy(coarse = false)
        assertEquals(s, com.example.voxara.core.headphones.phoneSampleFromJson(s.toJson()))
        assertEquals(sample(300.0), com.example.voxara.core.headphones.phoneSampleFromJson(sample(300.0).toJson()))
    }

    @Test
    fun `one sample never claims more than the sampling interval`() {
        assertEquals(900.0, phoneSampleToMinutes(sample(5_000.0), null).sumOf { it.measuredSeconds }, 1e-6)
    }

    @Test
    fun `speaker output or muted volume is not headphone listening`() {
        assertTrue(phoneSampleToMinutes(sample(600.0, OutputKind.OTHER), null).isEmpty())
        assertTrue(phoneSampleToMinutes(sample(600.0, index = 0), null).isEmpty())
    }

    @Test
    fun `a phone minute replaces a quieter ambient minute, never adds to it`() {
        val phone = MinuteRecord(5L, ExposureKind.HEADPHONE, 60.0, energyOf(80.0, 60.0), 0.0,
            category = HeadphoneCategory.EARBUDS.code, origin = ORIGIN_PHONE)
        val quietRoom = MinuteRecord(5L, ExposureKind.AMBIENT, 60.0, energyOf(60.0, 60.0), 0.0)
        val loudRoom = MinuteRecord(5L, ExposureKind.AMBIENT, 60.0, energyOf(100.0, 60.0), 0.0)

        val a = resolvePhoneMinute(phone, quietRoom)
        assertTrue(a.keepHeadphone); assertTrue(a.dropAmbient)
        val b = resolvePhoneMinute(phone, loudRoom)
        assertFalse(b.keepHeadphone); assertFalse(b.dropAmbient)
        val c = resolvePhoneMinute(phone, null)
        assertTrue(c.keepHeadphone); assertFalse(c.dropAmbient)
    }

    @Test
    fun `the Data Layer contract matches the phone companion`() {
        // Must stay identical to phone/.../ListeningSampler.kt.
        assertEquals("/voxara/listening", PhoneSyncContract.PATH_PREFIX)
        assertEquals("end_ms", PhoneSyncContract.KEY_END_MS)
        assertEquals("seconds", PhoneSyncContract.KEY_SECONDS)
        assertEquals("volume_db", PhoneSyncContract.KEY_VOLUME_DB)
        assertEquals(900.0, PhoneSyncContract.MAX_SPAN_SECONDS, 0.0)
        // The phone sends OutputKind names.
        listOf("WIRED_HEADPHONES", "USB_HEADSET", "BLUETOOTH_A2DP", "BLE_HEADSET", "BLE_BROADCAST")
            .forEach { OutputKind.valueOf(it) }
    }
}
