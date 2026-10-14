package com.example.voxara.sync

import com.example.voxara.core.ledger.ExposureKind
import com.example.voxara.core.ledger.MinuteRecord
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * One UTC day of one ledger (or a part of it). Days are UTC (minute / 1440) so the batch key never
 * depends on either device's time zone; each device regroups minutes into its own local days.
 */
data class DayBatch(
    val day: Long,
    val kind: ExposureKind,
    val part: Int,
    val parts: Int,
    val records: List<MinuteRecord>,
)

/**
 * The minute-ledger wire format: gzip over a compact binary layout, doubles kept bit-exact so the
 * phone stores exactly the numbers the watch has (and both compute the same digest).
 *
 * Size: a record is [RECORD_BYTES] bytes and a part holds at most [RECORDS_PER_PART] of them
 * (≈31 KB before compression), so a response is always under [WearContract.MAX_PAYLOAD_BYTES]
 * even uncompressed. A full day is at most 1440 minutes × 2 origins per ledger = 3 parts.
 */
object LedgerCodec {
    const val RECORDS_PER_PART = 1000
    const val RECORD_BYTES = 4 + 8 + 8 + 8 + 3
    private const val FORMAT = 1
    const val EMPTY_DIGEST = "0:0"

    fun utcDay(minute: Long): Long = Math.floorDiv(minute, 1440L)
    fun dayStartMinute(utcDay: Long): Long = utcDay * 1440L

    /** Records in their canonical order (the order both digests and batches use). */
    fun canonical(records: List<MinuteRecord>): List<MinuteRecord> =
        records.sortedWith(compareBy({ it.minute }, { it.origin }))

    /** "count:crc32" over the canonical bytes: equal digests mean equal records. */
    fun digest(records: List<MinuteRecord>): String {
        if (records.isEmpty()) return EMPTY_DIGEST
        val crc = CRC32()
        val buf = ByteArrayOutputStream()
        DataOutputStream(buf).use { out -> canonical(records).forEach { writeRecord(out, it, base = 0L) } }
        crc.update(buf.toByteArray())
        return "${records.size}:${crc.value}"
    }

    /** Splits one day of one ledger into parts; an empty day is one empty part (so it can clear). */
    fun split(day: Long, kind: ExposureKind, records: List<MinuteRecord>): List<DayBatch> {
        val sorted = canonical(records)
        val chunks = if (sorted.isEmpty()) listOf(emptyList()) else sorted.chunked(RECORDS_PER_PART)
        return chunks.mapIndexed { i, c -> DayBatch(day, kind, i, chunks.size, c) }
    }

    fun parts(recordCount: Int): Int = maxOf(1, (recordCount + RECORDS_PER_PART - 1) / RECORDS_PER_PART)

    fun encode(batch: DayBatch): ByteArray {
        require(batch.records.size <= RECORDS_PER_PART) { "part too large" }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(GZIPOutputStream(bytes)).use { out ->
            out.writeByte(FORMAT)
            out.writeLong(batch.day)
            out.writeByte(batch.kind.code)
            out.writeShort(batch.part)
            out.writeShort(batch.parts)
            out.writeInt(batch.records.size)
            val base = dayStartMinute(batch.day)
            batch.records.forEach { writeRecord(out, it, base) }
        }
        return bytes.toByteArray()
    }

    /** Throws on a malformed payload (the caller treats it as a failed sync and retries). */
    fun decode(bytes: ByteArray): DayBatch =
        DataInputStream(GZIPInputStream(ByteArrayInputStream(bytes))).use { inp ->
            val format = inp.readUnsignedByte()
            require(format == FORMAT) { "unknown ledger format $format" }
            val day = inp.readLong()
            val kind = ExposureKind.of(inp.readUnsignedByte())
            val part = inp.readUnsignedShort()
            val parts = inp.readUnsignedShort()
            val count = inp.readInt()
            require(count in 0..RECORDS_PER_PART) { "bad record count $count" }
            val base = dayStartMinute(day)
            val records = List(count) {
                val minute = base + inp.readInt()
                require(utcDay(minute) == day) { "minute outside its day" }
                MinuteRecord(
                    minute = minute,
                    kind = kind,
                    measuredSeconds = inp.readDouble(),
                    energyPa2s = inp.readDouble(),
                    nioshDosePercent = inp.readDouble(),
                    category = inp.readUnsignedByte(),
                    confidence = inp.readUnsignedByte(),
                    origin = inp.readUnsignedByte(),
                )
            }
            DayBatch(day, kind, part, parts, records)
        }

    private fun writeRecord(out: DataOutputStream, r: MinuteRecord, base: Long) {
        out.writeInt((r.minute - base).toInt())
        out.writeDouble(r.measuredSeconds)
        out.writeDouble(r.energyPa2s)
        out.writeDouble(r.nioshDosePercent)
        out.writeByte(r.category)
        out.writeByte(r.confidence)
        out.writeByte(r.origin)
    }
}
