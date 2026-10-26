# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users
Commuters, concert-goers and headphone users. They glance at the wrist for a second, in loud places, often moving, sometimes with the other hand busy. The watch is the primary surface; a phone companion holds history and settings.

## Product Purpose
Voxara is a hearing-protection dosimeter. It measures the surrounding sound level on the watch (dBA), accumulates a NIOSH-based daily and rolling-weekly dose, estimates headphone listening, and warns before the limit. Success: the wearer knows at a glance whether their ears are safe right now and how much of today's allowance is left.

## Positioning
Live level, dose and headphone estimate on the wrist, with an AI coach (Tono) that explains status in plain language. Alerts and numbers never depend on the AI; Tono only words them.

## Operating Context
Wear OS (round displays 192 to 240 dp, Galaxy Watch class), always-on ambient mode, a foreground monitoring service, tiles and complications. Offline-first: dose, ledger and AlertEngine are deterministic and local.

## Capabilities and Constraints
- Not a medical device; the microphone estimate is uncalibrated until the user calibrates. Both messages must stay visible in the product.
- Zones: OK < 70, Moderate 70-84, Loud 85-94, Dangerous >= 95 dBA; dose tiers 50 / 80 / 100 %.
- Screens follow a (state, actions) contract; navigation destinations and features are fixed for the watch redesign.
- Strings are resources in English and Spanish. Unit is written `dBA`.
- Battery: animation only while interactive and visible; none in ambient; minSdk 30, no blur.

## Brand Commitments
Name Voxara; coach Tono. Pure black AMOLED ground with the purple default accent and amber / crimson zone tiers (docs/design/STYLE.md). The animated wave rings and halo behind the level are kept. Fonts: Plus Jakarta Sans and JetBrains Mono (mono only where its numerals can be rendered without the dotted zero).

## Evidence on Hand
docs/design/STYLE.md, docs/design/voxara_template.html, PROGRESS.md, Roborazzi screenshot harness (app/src/test/.../ui). No real-device measurements exist; do not fabricate any.

## Product Principles
1. One glance, one answer: level, zone and dose before anything else.
2. Safety statements are never hidden or softened by styling.
3. Colour never carries a zone alone: word and icon always accompany it.
4. Calm by default; motion and haptics are feedback for state changes.
5. Honest about uncertainty: estimates are labelled as estimates.

## Accessibility & Inclusion
Touch targets >= 48 dp, text >= 12 sp, layouts fit at font scale 1.3 and 2.0, WCAG AA contrast, TalkBack labels and heading semantics, reduced-motion support.
