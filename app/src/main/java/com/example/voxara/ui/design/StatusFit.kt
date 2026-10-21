package com.example.voxara.ui.design

/**
 * FIT A SENTENCE WITHOUT AN ELLIPSIS. The hero shows Tono's line in at most two lines; when the
 * words do not fit even at the smallest type step, this returns a shorter, complete piece of the same
 * text (whole clauses first, whole words after), never a cut-off one: the full text is on Ask.
 *
 * [fits] answers whether a candidate fits the space (the caller measures it). Pure, so it is
 * unit-tested without a screen.
 */
internal fun shortenToFit(text: String, fits: (String) -> Boolean): String {
    val t = text.trim()
    if (t.isEmpty() || fits(t)) return t
    // Clause ends: after . ! ? ; : , (followed by a space) and before a dash that stands for a pause.
    val clauseEnds = Regex("""[.!?;:,…](?=\s)|(?=\s[—–]\s)""").findAll(t).map { it.range.last + 1 }
        .filter { it < t.length }.toList()
    for (end in clauseEnds.sortedDescending()) {
        val cand = tidy(t.substring(0, end))
        if (cand.isNotEmpty() && fits(cand)) return cand
    }
    // Whole words, longest first.
    val wordEnds = Regex("""\s+""").findAll(t).map { it.range.first }.filter { it > 0 }.toList()
    for (end in wordEnds.sortedDescending()) {
        val cand = tidy(t.substring(0, end))
        if (cand.isNotEmpty() && fits(cand)) return cand
    }
    return t.split(Regex("""\s+""")).first()
}

/** No trailing comma, semicolon, colon or dash: a clause that ends the line should end like a sentence would. */
private fun tidy(s: String): String = s.trim().trimEnd(',', ';', ':', '—', '–', ' ')
