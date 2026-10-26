# Surface brief: Voxara phone companion

Written for the phone redesign (impeccable `init` → `shape`). Product truth lives in `PRODUCT.md` and the visual world in
`DESIGN.md` / `docs/design/STYLE.md`; this file is only what is specific to the phone surface. Mode: **Operate**.

## Mode

**Operate.** The visitor completes a task: they open the phone to know where their ears stand, to find out what happened, or to
change something. Scanability, consistency, Material 3 expectations and the real usage scene outrank expression. The brand lives in
precise details: the ring, the numerals, the one accent, the motion that explains a change. Platform: Android, Jetpack Compose,
Material 3 (BOM 2025.12.00 → `material3` 1.4.x with `material3-adaptive-navigation-suite`).

## The one question

> **How is my hearing today?**

Home's first viewport (360 × 800 dp, font 1.0, no scrolling) answers it with one figure, one word, one sentence:
today's share of the daily allowance, the zone as a word and an icon, Tono's line. The week and the headphones are the
next two glances, not the next five cards.

## Scenes (what the person is doing, with the phone in hand)

1. **The glance.** Standing in a queue, on a train, between meetings: unlock, look for two seconds, put it away. Wants a verdict,
   not a dashboard. Often one-handed, often at font scale 1.3+. Light theme is the default; many use dark at night.
2. **After a buzz.** The watch vibrated (or the phone did, for headphones). Wants to know what it was, how loud, at what time and
   what to do. The alert history and the hour it happened in.
3. **The week.** A calm moment: how did this week go against the allowance, is it better than last week, how much of it came from
   headphones? Reads a chart, wants one sentence about it.
4. **Before leaving.** Is the watch connected and measuring? Is anything stopping it (microphone, battery, calibration)? Wants a yes,
   or the one thing to fix.
5. **Tuning and housekeeping.** Alert level, quiet hours, retention, export, delete everything. Rare, deliberate, may be done
   sitting down at font 2.0.
6. **Headphones on the phone.** Music playing: an estimated listening level and, if it is high, the way to lower it.

## What the phone is not

It is not the dosimeter: it never shows a live microphone level (the watch measures; the phone pulls the ledger). Numbers are
"as of the last sync" and say so. It is not a medical device: "uncalibrated estimate" and "not a medical device" stay visible.
Tono words what the deterministic engine decided; alerts and figures never depend on the AI.

## Hard constraints (carried over, not up for redesign)

- Destinations and features stay: Home, Today, Alerts, Trends, Tono, Watch (all link states), Headphones, Settings, Setup,
  How it works, CSV export, delete all, the Glance widget, headphones mode, mirrored limit alerts.
- Four accents (Purple `#8B5CF6` default, Turquoise, Blue, Emerald), Light / Dark / Follow system (Light default), zone-reactive
  accent (amber and crimson only for zone warnings), word + icon with every tier colour.
- Strings in English and Spanish, `dBA` as the unit, locale-aware numbers (Spanish decimal comma), no uppercase labels above things.
- Numerals as on the watch: Plus Jakarta Sans in fixed-width digit cells, plain zero, no `tnum`.
- Touch targets ≥ 48 dp, text ≥ 12 sp, nothing clipped at font 2.0, WCAG AA for every accent × theme, TalkBack names and heading
  semantics, reduced motion respected, edge-to-edge with insets, predictive Back.
- Adaptive: bottom navigation on compact width, rail on medium and expanded; two-pane where it pays on tablets and unfolded foldables.

## What success looks like

- Home answers the question without scrolling, in the zone's colour and word, at font 1.3 as well.
- Fewer places to look: every piece of information that moves keeps a recorded home (see the information map in the README of the
  chosen concept).
- One visual language with the watch: the ring, the numerals, the calm black-and-one-accent attitude, translated to a phone
  (light first).
- Charts that read at a glance and say their number in words (dataviz rules: one idea per chart, direct labels, zone as colour
  AND position, no chart junk).
