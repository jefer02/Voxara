package com.example.voxara.ui.design

/**
 * FIT TONO'S LINE ON A GLANCE, OR NOT AT ALL. The hero shows one short sentence of Tono's in at most two lines.
 * This returns the whole text when it fits; otherwise the longest run of WHOLE sentences from its start that fits
 * ("All good today." out of "All good today. Your week is calm."); otherwise null, and the caller shows nothing
 * on the hero and the full line as the first row under it. It never returns a piece of a sentence: a clause or a
 * few words ("A calm day for") read as a different, broken message, and an ellipsis hides what was meant.
 *
 * [fits] answers whether a candidate fits the space (the caller measures it). Pure, so it is unit-tested without
 * a screen.
 */
internal fun wholeSentencesToFit(text: String, fits: (String) -> Boolean): String? {
    val t = text.trim()
    if (t.isEmpty()) return null
    if (fits(t)) return t
    // Sentence ends: . ! ? (or …) followed by a space; the text's own end is handled above.
    val ends = Regex("""[.!?…](?=\s)""").findAll(t).map { it.range.last + 1 }.toList()
    for (end in ends.sortedDescending()) {
        val cand = t.substring(0, end).trim()
        if (cand.isNotEmpty() && fits(cand)) return cand
    }
    return null
}
