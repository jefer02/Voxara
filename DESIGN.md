---
name: Voxara Watch
description: A round, glanceable dosimeter. Three pages on pure black: the live level as a big rolling number on its dBA scale with Tono's one line and today's dose (Now), the share of today's or this week's limit on one progress ring (Dose), and three actions (More).
colors:
  amoled-black: "#000000"
  card: "#131722"
  card-subtle: "#1B2030"
  text: "#F8FAFC"
  text-secondary: "#AEB6C4"
  text-muted: "#8A94A6"
  voxara-violet: "#8B5CF6"
  voxara-violet-ink: "#A78BFA"
  loud-amber: "#FFB800"
  danger-crimson: "#FF2A6D"
  danger-crimson-ink: "#FF6B93"
typography:
  hero:
    fontFamily: "Plus Jakarta Sans"
    fontSize: "52sp"
    fontWeight: 800
    letterSpacing: "-0.03em"
  display:
    fontFamily: "Plus Jakarta Sans"
    fontSize: "36sp"
    fontWeight: 800
    letterSpacing: "-0.02em"
  title:
    fontFamily: "Plus Jakarta Sans"
    fontSize: "19sp"
    fontWeight: 800
    letterSpacing: "-0.02em"
  body:
    fontFamily: "Plus Jakarta Sans"
    fontSize: "14sp"
    fontWeight: 500
  label:
    fontFamily: "Plus Jakarta Sans"
    fontSize: "13sp"
    fontWeight: 600
  caption:
    fontFamily: "Plus Jakarta Sans"
    fontSize: "12sp"
    fontWeight: 500
rounded:
  card: "22dp"
  pill: "999dp"
spacing:
  round-inset: "14dp"
  touch: "48dp"
  ring-rim-inset: "4% of the display (the ring's outer edge, every page)"
  ring-stroke: "2.7% of the display"
components:
  glance-hero:
    backgroundColor: "{colors.amoled-black}"
    textColor: "{colors.text}"
    typography: "{typography.hero}"
  zone-badge:
    textColor: "{colors.loud-amber}"
    rounded: "{rounded.pill}"
    typography: "{typography.label}"
  metric-card:
    backgroundColor: "{colors.card}"
    textColor: "{colors.text}"
    rounded: "{rounded.card}"
    height: "48dp"
---

# Design System: Voxara Watch

## Overview

**Creative North Star: "The Orbit"**

The watch is a round instrument, not a list of rectangles. Every glance page is one picture drawn on the same 250 degree ring (a 110 degree gap at the bottom), its outer edge 4 % of the display in from the glass: the same rim on every page, like a Wear system progress indicator. Every proportion on a page (ring radius and stroke, wave rings, centre column, foot, figure and word sizes) is a fraction of the display, so the same layout fills a 192 dp case and a 240 dp one. Inside it sit a big figure and a zone tag, and in the gap just above the page dots the one thing the page lets you do or read next. There are three pages, each answering one question. **Now**: how loud is it, and is today fine (the live level on a dBA scale whose four zones light up, Tono's one line as a tag, today's dose). **Dose**: how much of my limit is used, today or this week (one progress ring that starts at 12 o'clock and fills clockwise; a faint track is what is left; full = 100 %). **More**: Tono, Mode, Settings. Swiping is a change of picture, not only of position: the new page's picture draws itself in while the old one retracts, and both turn a little with the finger.

Behind the picture, wave rings and a halo fill the ring's inside and breathe with the live level; a dark scrim under the figure keeps text legible, and the rings drift slowly when it is quiet.

**Headphones mode** is a listening layer over the situation, not a page. When it is on, Now becomes its picture: the scale opens at the top into two ear-cup arcs (indigo) that rise with the estimated listening level, the waves go stereo, the figure is that level (with ≈), and the outside level becomes a small moon in the gap at the top. The way in is the signature moment of the app (see Motion). What the old Headphones page showed lives where it belongs: the live estimate here, the weekly share in Dose / Week, the settings in Mode.

The world stays black (AMOLED, one accent) and calm. Colour is information: violet is safe, amber is loud, crimson is the limit, indigo is headphones, and none of them is ever the only carrier: the zone is always a word and an icon too. Neutral notes (an uncalibrated estimate) are muted grey with an info icon, never amber. Motion explains state changes and is absent in ambient mode and with reduced motion.

**Key Characteristics:**
- One picture per page on one ring; the ring is concentric with the display and its outer edge is the same on every page.
- The system clock steps aside on the three glance pages (the ring owns the top edge on Now and Dose; More's three pills fill the face) and stays on every list screen and in ambient.
- Pager of three pages (Now, Dose, More); the system page indicator hugs the bezel. Pages are not captioned with their name: the figure and its unit, the caption under it and the Today | Week switch say what each is; TalkBack hears "Page n of 3, name".
- **What looks tappable is tappable.** A card, a pill or a chevron always does something useful; information is plain text with an icon (no card, no fill, no chevron). `MetricCard` enforces it: with an action it is a card with a chevron, without one it is a plain row.
- **Text budget.** A glance page: one big number or word plus about six words. A list row: label and value up to three words each. An alert: a title up to four words, one short sentence, actions up to three words. Required notices (estimate, not medical) in their shortest honest form; long explanations behind a tap. Nothing is ever cut with an ellipsis: list text wraps whole.
- No uppercase labels above things.
- Numbers are drawn one fixed-width cell per digit, in Plus Jakarta Sans (plain zero, plain one: no `tnum`, whose tabular alternates give "1" a slab foot); they roll, they never jitter. Decimals follow the language the app shows.
- Nothing is clipped at 192, 216, 227 or 240 dp, at font scale 1.3 or 2.0, in English or Spanish: at large sizes the page drops its caption and foot (shown as the first detail), caps the figure and Tono's line at 1.3 times and the other hero words at 1.5 times.
- Tono's line on the hero is a small tag holding one whole sentence or nothing: two lines when the gap has room, one when it is tight (a step or two smaller if needed, then fewer whole sentences), and the first detail row (wrapping whole) when not even one sentence fits. Never a clause or a few words ("A calm day for"), never an ellipsis; the full text is on the Tono screen.

## Colors

Pure black ground, one accent family, two tier colours. Values and the contrast rules are in `docs/design/STYLE.md` and are enforced by `DesignSystemTest` and `AccentPaletteTest`.

### Primary
- **Voxara Violet** (#8B5CF6 fill, #A78BFA ink): the watch accent. Arcs and wave rings while the zone is OK or Moderate, the primary button, the check marks.

### Secondary
- **Headphones Indigo** (#818CF8 ink, #6366F1 fill): the headphones page (name, scale, confidence band).
- **Loud Amber** (#FFB800): the LOUD tier. Arc, rings, halo and zone badge when the level or dose is 85 to 94 dBA / 80 to 99 %.
- **Danger Crimson** (#FF2A6D fill, #FF6B93 ink): the DANGEROUS tier and the limit alert border.

### Neutral
- **Amoled Black** (#000000): every page background.
- **Card** (#131722) and **Card Subtle** (#1B2030): list rows and chips below the fold; never behind a hero.
- **Text** (#F8FAFC), **Text Secondary** (#AEB6C4), **Text Muted** (#8A94A6): all at least 4.5:1 on black and on the cards.

### Named Rules
**The Colour Is Never Alone Rule.** A tier colour always comes with the zone word and its icon.
**The Concentric Rule.** The arc and the rings share the centre of the display; nothing is offset to make room except the text stack inside, which sits 8 % above centre to leave the arc's gap to the foot.

## Typography

**Display and Body Font:** Plus Jakarta Sans (variable, bundled). **Mono:** JetBrains Mono is no longer used for numerals on the watch: its zero carries a dot that no OpenType setting removes.

**Character:** Extra-bold, tightly tracked figures against medium-weight words. Numerals are laid out per digit cell (`TabularNumber`, `RollingNumber`), with a smaller companion sign ("%") never below 12 sp.

### Hierarchy
- **Hero** (800, 52sp, 54): the live level or the dose percentage in a hero.
- **Display** (800, 36sp, 40): the same figure in the compact hero.
- **Title** (800, 19sp, 23): a screen title or a Tono sentence.
- **Body** (500, 14sp, 19): explanations in lists.
- **Label** (600, 13sp): zone word, Tono's one line, group headings.
- **Caption** (500, 12sp): units and foot lines. Nothing is smaller.

### Named Rules
**The Plain Zero Rule.** A figure never shows a dotted zero. **The dBA Rule.** The unit is written `dBA`, never in capitals.

## Layout

A page is a `TransformingLazyColumn` inside a `ScreenScaffold` (scroll indicator handled by the scaffold; the clock only on lists). A **glance page** (`HeroList` + `PageStage`) makes its first item one screen tall (it grows only when very large text needs more room): the wave rings and scrim, the page's picture on the ring, then one column from 15 % down: the centre (figure, caption, zone tag) at the top and, in the ring's gap, the foot and the page's one control (the headphones chip, the Today | Week switch). When a foot is given the page is exactly one screen and the foot is handed the room the centre leaves, so it shows what fits (Now: Tono's tag in two lines, in one, or not at all, over a short dose line) and whatever it leaves out is the first detail. The last line of the gap must be short: the display is a circle, and at 91 % of its height a 203 dp watch is only 115 dp wide. All of it is `Face` (`ui/design/Face.kt`): fractions of the display, never fixed dp. Details scroll below. A **list page** (`VoxList`) pads the column by the round inset (14 dp). Items scale and fade toward the screen edges, and black **edge scrims** hide content under the clock and, in the pager, under the page indicator; list pages keep a bottom inset so the last item rests above the dots. Pages sit in a `HorizontalPager` inside `HorizontalPagerScaffold`; swiping right on the first page leaves the app. The bottom scrim is solid black where the page dots sit and fades in above them, so content never shows through the dots; lists keep a bottom inset so the last item rests above them. The Dose view (Today or Week) is held by the pager's screen, so leaving the page and coming back finds the wearer where they were.

Large text: from font scale 1.15, or on screens narrower than 200 dp, the page shows only figure, tag and name; the caption and foot become the first detail row.

## Elevation & Depth

Flat. Depth is tone (card on black) and light (a translucent halo and a wide translucent stroke under the arc). No shadows, no blur (minSdk 30).

## Shapes

A circle and its arc. Rows and chips are 22 dp rounded (the card) or fully rounded (pills, badges, buttons). The arc has round caps, 2.7 % of the display wide (6.5 dp at 240), on a white 7 % track; its glow is a 3 times wider stroke at 18 % alpha.

## Components

### Page stage (`PageStage`, `OrbitStage`, `PageName`)
Waves + scrim, the page's picture, a centre column and a foot group (`foot`, told the `room` the centre leaves, plus one `nameSlot` control). Spoken as one description prefixed with "Page n of 3, name"; the name is spoken, not drawn.

### Pictures (`Orbit.kt`)
`NowRing` (the dBA scale, and its split into two arcs in headphones mode) and `DoseArc` (one progress arc on the shared rim: it starts at 12 o'clock and fills clockwise over a faint full track, full = 100 % of the limit and full above it; between Today and Week it runs from one share to the other, `mix` 0..1 read in the draw phase). Nothing else is drawn on the Dose ring (no hour marks, markers or day segments; the hours and the days are lists below), and the Dose page leaves out the breathing waves, which follow the live sound. Draw-in driven by the pager's reveal, cached strokes (no per-frame allocation).

### Headphones layer (`LayerTransition.kt`, `HomeScreen.kt`, `Orbit.kt`)
`NowRing` (the scale opens at the top into two arcs), `Moon` (the outside level), `HeadphonesLabel` (curved "Headphones mode"), `HeadphonesChip` (48 dp indigo pill: "Headphones" over "Auto", "From phone" or "Possible speaker"; a tap opens **Not headphones?**; a plain label on small screens or large text) and the "Not headphones" button, Now's first detail while the mode is on. Detection stays automatic: **Not headphones?** ignores the output in use until it disconnects, or remembers a Bluetooth device as a speaker (only a salted digest is stored; Mode lists them and forgets them). The room's alerts are never affected; headphone alerts stop only for that output. When the mode is off, Now shows it as one line of information, not a switch. One `LayerProgress` holds the stages (ring, waves, figure, moon, label) so each part reads only its own value in the draw phase.

### Tono's tag (`TonoTag`, `FittedStatus`)
A small tag under the zone: an icon (a sparkle when the wording came from the cloud AI) and Tono's sentence in the Label size, on a black 55 % scrim with a hairline border. One tap opens Ask. `FittedStatus` fits it (never an ellipsis); it may take 76 % of the display's width, because it sits higher in the gap than the last line. Below the fold it is a full-width 48 dp row.

### Dose caption and its explanation
Under the figure, what it is a share of, with an info icon: "of today's limit" / "of this week's limit". The figure and caption are one tap target that opens **What the ring shows** (two sentences: it fills from the top as the limit is used; full is 100 %). The caption stays at every size.

### Today | Week switch (`DoseSwitch`)
Two segments in one 48 dp pill in the gap: the selected one is filled (accent tint) and extra-bold, the other plain and secondary; each is a tab for TalkBack ("Today, tab, selected"). The selected segment is the view's name, so label and state are one thing. On small displays and at large fonts the gap carries only the view's name and the switch is the first detail row. The crown does the same thing (see Motion).

### Zone tag (`ZoneTag`)
Icon and word in the tier colour on a tier tint over a dark scrim: a status tag, not a button (no outline, 8 dp corners). Colour crossfades and icon/word pop when the zone changes.

### Rows and buttons
`MetricCard` (tinted icon, muted label, value: a card with a chevron when it has an action, a plain row when it is information), `PillButton` (Wear Material 3 filled / tonal / outlined), `ModeChip` (morphs its label when Auto concludes), `ResumePill`. Every tap target is at least 48 dp and gives press feedback (`pressable`: 96 % for 100 ms).

### More (`MenuPage`)
One screen that fits at every size and font scale: three 48 dp pills stacked in the middle of the face (Tono, Mode, Settings), the top and bottom ones 70 % of the display wide and the middle one 84 %, labels held at 1.3 times the font size. Below the fold, the phone link as a plain line and the one-line disclaimer.

### Limit alert (`LimitAlertScreen`)
A round dark panel with a crimson border; icon, title and the acknowledge button fit the first screen; the border pulses once per haptic beat.

### Type roles for small text
Name and zone tag: Label (13 sp, 600; larger displays scale it up to 1.12 times, large fonts hold it at 1.5 times). Caption and foot lines: Caption (12 sp, 500). Companions to a figure ("dBA", "%", "≈"): 0.42 times the figure, never below 12 sp. Tono's line: Title small (15 sp, 800).

### Motion (see `VMotion`; headphones mode in `LayerProgress`)
Picture draw-in 650 ms decelerating (retract 250 ms); the picture turns up to 40 degrees with the swipe; zone crossfade 400 ms; zone tag swap 150 ms; digit roll 220 ms; mode chip morph 220 ms; press 100 ms; limit pulse 420 ms per beat; wave rings follow the level with a spring. **Today to Week** (Dose), 520 ms: the arc runs from the day's share to the week's (its colour follows the zone), the figure rolls, the caption cross-fades, the switch's fill moves and the zone tag swaps if the zone differs. Reduced motion: the arc jumps to the other share and the captions swap. The crown flips the view while the page rests at its top (clockwise to Week, back to Today), with a tick; once the page has scrolled, or when it is on Week and the crown keeps turning clockwise, it scrolls the details as on any list, so it never fights the pager (a swipe) or the scrolling. **Headphones mode in**, about 900 ms staged: the scale opens at the top into two arcs with a spring, slims and fades (0 to 400 ms; the arcs are indigo) · waves morph to stereo (120 to 600) · the centre swaps and the digits roll from the outside level to the listening one (240 to 540) · the outside level flies out as the moon in a half orbit (300 to 750) · the curved label appears, holds and fades (280 to 740) and the chip appears. **Out**, about 700 ms: the moon returns, the figure becomes the outside level again, the arcs rejoin. Reduced motion: a cross-fade of the centre only; ambient or screen not resumed: none; an alert delivered during (or within a minute before) it: the alert wins and the picture jumps to its end. All gated by `LocalAnimate` / `LocalReduceMotion`; none in ambient; no per-frame allocation; no blur.

## Do's and Don'ts

### Do:
- **Do** give each page a picture only it can draw, on the shared ring.
- **Do** keep the last line in the ring's gap short: the circle narrows there, and a line as wide as the foot is cut by the glass.
- **Do** let a hero's foot show less (or nothing) when it has no room, and put the rest in the first detail, instead of crowding the figure.
- **Do** write a zone as word + icon next to any tier colour.
- **Do** cap the figure at 1.3 times its size and let the words around it keep growing (to 1.5 times).
- **Do** size every part of a page as a fraction of the display (`Face`), so one layout fills every watch.
- **Do** let headphones mode only add: a layer on the situation, never a lower threshold, never less measuring.
- **Do** move optional lines below the fold at large font sizes instead of shrinking them under 12 sp.

### Don't:
- **Don't** reuse one hero for several pages, or stack identical full-width cards on a glance page.
- **Don't** use amber or crimson for anything but a zone warning.
- **Don't** add uppercase labels above headings, or more than one label per screen.
- **Don't** use JetBrains Mono for numerals or draw a dotted zero.
- **Don't** animate in ambient, or without `LocalAnimate`, or allocate per frame.
- **Don't** use `tnum` or any font feature on numerals (the digit cells make them tabular).
- **Don't** put the system clock under the ring: it steps aside on the glance pages.

---

# Voxara Phone (the other half of the watch)

The phone is the watch's picture made phone-sized and given somewhere to put the days. Same black-and-violet world (light by default, dark and follow-system available), same Orbit ring, same Plus Jakarta Sans digits in fixed-width cells, same zone words and icons. One question per screen; the first viewport of Home answers *how is my hearing today?* with a figure, a word and a sentence.

**Surface and mode.** Operate (a companion app people open for a few seconds to check, then for a few minutes to understand). Brief: `docs/design/phone-redesign/SURFACE_BRIEF.md`. Direction chosen at checkpoint B: concept A (Orbit, enlarged) for Home, concept B's charts (the Ledger) for Trends, in Orbit's language (zone colours, rounded caps, the watch's stroke weights and motion). Evidence: `docs/design/phone-redesign/` (before, concepts, after, critiques).

**Structure.** Four tabs (Home, Trends, Watch, Settings). Home: header (the watch and when it last reported, the sync button, the watch's situation with its headphones layer), the ring, Tono's sentence (opens Tono), the uncalibrated note, two tiles (the last 7 days, headphones), the day hour by hour, the last alert. The ring opens Today (a shared element): the cumulative "where the day went", the hours, the minutes, the numbers and today's alerts. Trends: a Day / Week / Month segmented filter, one hero and one chart per range, the rolling weekly allowance, the headphones' share, the Hearing Care Score as a supporting line, "Ask Tono". Watch: the device, the checklist (calibration and battery open in place), troubleshooting as one row. Settings: Watch and alerts, Headphones, Tono and privacy, Appearance, Data, About.

**Numbers.** Four names, one source (`HearingFigures`): today, surroundings last 7 days, headphones last 7 days, all sound last 7 days (compared with the 7 days before). Never a calendar week; never two different numbers for the same name.

### Do:
- **Do** answer the screen's one question first and put the rest below.
- **Do** write a zone as an icon and a word beside any tier colour, in charts too (a legend of icon + word).
- **Do** draw charts with thin capped marks, solid hairline guides labelled beside the line, direct labels only on the extremes (or on all seven columns), and a spoken twin.
- **Do** hold the figure at 1.3 times the font scale and let the words grow; move the caption and the zone word out of the ring before they touch the arc; stack tiles and buttons rather than break a word.
- **Do** keep the "uncalibrated estimate" and "not a medical device" messages visible wherever they apply.

### Don't:
- **Don't** draw shadows, dashed guides, uppercase labels, Mono numerals or a dotted zero.
- **Don't** invent a number: a figure is the watch's, the ledger's or derived from them (the cumulative line ends exactly on the headline).
- **Don't** show the same share three times on one screen.
- **Don't** let the accent carry a zone alone (OK and Moderate are the accent: the word and the icon say which).
