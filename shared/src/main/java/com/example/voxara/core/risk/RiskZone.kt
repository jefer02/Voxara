package com.example.voxara.core.risk

/**
 * THE ZONES — one definition for every surface (gauge, tile, complications, notifications,
 * Tono). Each zone always shows a WORD and an ICON too, so colour never carries it alone.
 *
 *   OK         < 70 dBA   everyday sound
 *   MODERATE   70-84      adds up over long periods
 *   LOUD       85-94      the NIOSH daily limit is reached in 8 h at 85, 1 h at 94
 *   DANGEROUS  >= 95      minutes, not hours
 */
enum class RiskZone(val fromDba: Double) {
    OK(0.0),
    MODERATE(70.0),
    LOUD(85.0),
    DANGEROUS(95.0);

    companion object {
        fun of(dba: Double): RiskZone = when {
            dba >= DANGEROUS.fromDba -> DANGEROUS
            dba >= LOUD.fromDba -> LOUD
            dba >= MODERATE.fromDba -> MODERATE
            else -> OK
        }

        /** Zone for a dose or budget percentage (daily or weekly). */
        fun ofPercent(percent: Double): RiskZone = when {
            percent >= 100.0 -> DANGEROUS
            percent >= 80.0 -> LOUD
            percent >= 50.0 -> MODERATE
            else -> OK
        }
    }
}
