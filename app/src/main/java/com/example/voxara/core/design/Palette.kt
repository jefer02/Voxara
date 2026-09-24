package com.example.voxara.core.design

import com.example.voxara.core.risk.RiskZone
import kotlin.math.pow

/**
 * VOXARA PALETTE — raw ARGB values, pure so contrast is checked by unit tests (WCAG 2.x).
 * True-black OLED base, tonal surfaces, three text inks, four zone colours and the brand
 * gradient. Voxara's own values (not copied from any platform palette).
 */
object Palette {
    const val BLACK = 0xFF000000
    const val SURFACE_1 = 0xFF121316
    const val SURFACE_2 = 0xFF1C1D21
    const val SURFACE_3 = 0xFF26282D
    const val OUTLINE = 0xFF3A3D44

    const val TEXT_PRIMARY = 0xFFFFFFFF
    const val TEXT_SECONDARY = 0xFFB9BEC8
    const val TEXT_TERTIARY = 0xFF8E94A0

    const val ZONE_OK = 0xFF3DB8F5
    const val ZONE_MODERATE = 0xFFF2C94C
    const val ZONE_LOUD = 0xFFFF8C42
    const val ZONE_DANGEROUS = 0xFFFF4F8B

    /** Brand gradient: Voxara's signal cyan to violet. For brand/AI accents only, never state. */
    const val BRAND_START = 0xFF35E8FF
    const val BRAND_END = 0xFF8B7CFF

    /** Text on zone-coloured fills. */
    const val ON_ZONE = 0xFF000000

    fun zone(z: RiskZone): Long = when (z) {
        RiskZone.OK -> ZONE_OK
        RiskZone.MODERATE -> ZONE_MODERATE
        RiskZone.LOUD -> ZONE_LOUD
        RiskZone.DANGEROUS -> ZONE_DANGEROUS
    }

    /** WCAG relative luminance of an opaque ARGB colour. */
    fun luminance(argb: Long): Double {
        fun ch(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)
    }

    /** WCAG contrast ratio between two opaque colours (1..21). */
    fun contrast(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
