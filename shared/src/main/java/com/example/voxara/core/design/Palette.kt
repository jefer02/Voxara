package com.example.voxara.core.design

import com.example.voxara.core.risk.RiskZone
import kotlin.math.pow

/**
 * VOXARA PALETTE — raw ARGB values, pure so contrast is checked by unit tests (WCAG 2.x).
 * The style comes from docs/design/STYLE.md: deep blue-black and paper-white neutrals, one
 * user-chosen accent (four options) and two fixed tiers (caution amber, danger crimson) that the
 * level-reactive accent switches to. The watch is pure black and always Purple.
 */
object Palette {
    /** The watch background (AMOLED), and black for the ambient view. */
    const val BLACK = 0xFF000000

    // ------------------------------------------------------------------ dark neutrals
    const val DARK_BG = 0xFF090B10
    const val DARK_CARD = 0xFF131722
    const val DARK_CARD_SUBTLE = 0xFF1B2030
    const val DARK_TEXT = 0xFFF8FAFC
    const val DARK_TEXT_2 = 0xFFAEB6C4
    const val DARK_MUTED = 0xFF8A94A6
    /** 1 dp card border: white at 8 %. */
    const val DARK_BORDER = 0x14FFFFFF
    /** Inactive capsules and ring tracks: white at 12 %. */
    const val DARK_TRACK = 0x1FFFFFFF
    /** The watch bezel track: white at 7 %. */
    const val WATCH_TRACK = 0x12FFFFFF

    // ------------------------------------------------------------------ light neutrals (phone)
    const val LIGHT_BG = 0xFFF3F5F9
    const val LIGHT_CARD = 0xFFFFFFFF
    const val LIGHT_CARD_SUBTLE = 0xFFEBF0F7
    const val LIGHT_TEXT = 0xFF0F172A
    const val LIGHT_TEXT_2 = 0xFF334155
    /** The template's #64748B is below 4.5:1 on the light background; darkened to pass. */
    const val LIGHT_MUTED = 0xFF5B677A
    const val LIGHT_BORDER = 0x120F172A
    const val LIGHT_TRACK = 0x1A0F172A

    // ------------------------------------------------------------------ fixed hues
    const val SECONDARY = 0xFF6366F1
    const val SECONDARY_INK_DARK = 0xFF818CF8
    const val SECONDARY_INK_LIGHT = 0xFF4F46E5
    const val WARNING = 0xFFFFB800
    const val DANGER = 0xFFFF2A6D

    /** Text on any accent or tier fill. */
    const val ON_FILL = 0xFF05080D

    /** Soft tint (badge and icon-tile backgrounds) and glow alphas over a fill. */
    const val SOFT_ALPHA = 0.15f
    const val GLOW_ALPHA = 0.35f

    /** One colour role in both forms: [fill] for graphics, [ink] for text and icons, [onFill] for text on the fill. */
    data class Tone(val fill: Long, val ink: Long, val onFill: Long = ON_FILL)

    /** The user's accent in a theme. The watch always uses [Accent.PURPLE] dark. */
    fun accent(a: Accent, dark: Boolean): Tone = when (a) {
        Accent.PURPLE -> if (dark) Tone(0xFF8B5CF6, 0xFFA78BFA) else Tone(0xFF8B5CF6, 0xFF6D28D9)
        Accent.TURQUOISE -> if (dark) Tone(0xFF00E5BF, 0xFF00E5BF) else Tone(0xFF009E87, 0xFF00695C)
        Accent.BLUE -> if (dark) Tone(0xFF3B82F6, 0xFF60A5FA) else Tone(0xFF3B82F6, 0xFF1D4ED8)
        Accent.EMERALD -> if (dark) Tone(0xFF34D399, 0xFF34D399) else Tone(0xFF059669, 0xFF065F46)
    }

    /** Caution tier (zone LOUD). */
    fun caution(dark: Boolean): Tone = if (dark) Tone(WARNING, WARNING) else Tone(0xFFB7791F, 0xFF92400E)

    /** Danger tier (zone DANGEROUS). */
    fun danger(dark: Boolean): Tone = if (dark) Tone(DANGER, 0xFFFF6B93) else Tone(DANGER, 0xFF9F1239)

    /**
     * THE LEVEL-REACTIVE ACCENT — OK and MODERATE keep the chosen accent (nothing alerts there),
     * LOUD is caution, DANGEROUS is danger. Voxara's zone boundaries, never the template's.
     */
    fun level(zone: RiskZone, a: Accent, dark: Boolean): Tone = when (zone) {
        RiskZone.OK, RiskZone.MODERATE -> accent(a, dark)
        RiskZone.LOUD -> caution(dark)
        RiskZone.DANGEROUS -> danger(dark)
    }

    /** Neutral surfaces of a theme, for the contrast tests and non-Compose surfaces (widget, tiles). */
    fun surfaces(dark: Boolean): List<Long> =
        if (dark) listOf(DARK_BG, DARK_CARD, DARK_CARD_SUBTLE) else listOf(LIGHT_BG, LIGHT_CARD, LIGHT_CARD_SUBTLE)

    /** [fg] at [alpha] over the opaque [bg] (straight alpha), as an opaque colour. */
    fun over(fg: Long, alpha: Float, bg: Long): Long {
        fun ch(c: Long, shift: Int) = ((c shr shift) and 0xFF).toInt()
        fun mix(shift: Int) = Math.round(alpha * ch(fg, shift) + (1 - alpha) * ch(bg, shift)).toLong()
        return 0xFF000000 or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
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

/** The four accent colours of the template's colour picker. Purple is the default (and the watch's). */
enum class Accent {
    PURPLE, TURQUOISE, BLUE, EMERALD;

    companion object {
        val DEFAULT = PURPLE

        fun parse(name: String?): Accent = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
