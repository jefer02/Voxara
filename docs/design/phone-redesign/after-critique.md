# Critique after the redesign: phone app

Method: design review only (detector not applicable, native platform), same as `baseline-critique.md`. Evidence: the 305 renders in `after/`
(phone 360 dp, foldable 673 dp, tablet 1280 dp; font 1.0 / 1.3 / 2.0; light and dark; every state of Home, the Watch screen and the
headphones; four accents), `FontScaleLayoutTest`, `FiguresConsistencyTest`, `ChartColorsTest`. Fake data (`PhoneFixtures`: a Tuesday afternoon that
reached LOUD, built from a minute ledger so every number agrees). Not yet seen on a real phone (see the final report).

## Design Health Score

| # | Heuristic | Before | After | What changed / what is left |
|---|-----------|:-----:|:-----:|---|
| 1 | Visibility of system status | 3 | 4 | One status line (the watch and when it last reported; a problem replaces it with the state in words and its action), a sync button that turns while syncing, the uncalibrated note on Home for as long as it applies, stale and disconnected states with their numbers marked "as of". |
| 2 | Match system / real world | 2 | 4 | The invented score left the hero (a supporting line in Trends). Four numbers, four names, one source (`HearingFigures`); the week-over-week sentence is always the two numbers beside it. |
| 3 | User control and freedom | 3 | 3 | Back everywhere, pull to refresh, tapping the tab you are in returns to its first screen, confirmation before delete. No undo anywhere (unchanged), no tap-to-read on charts. |
| 4 | Consistency and standards | 2 | 4 | One language with the watch: the 250° ring, Plus Jakarta Sans digit cells, zone word + icon, no uppercase labels, no shadows, no dashed guides. Left: Settings and Watch are still inset-grouped lists (iOS-flavoured, not Material `ListItem`s). |
| 5 | Error prevention | 3 | 3 | Unchanged: bounded steppers, confirmed deletes, opt-ins off. |
| 6 | Recognition rather than recall | 3 | 4 | Four tabs instead of five; the "see more" rows are gone because the ring, the tiles and the last-alert row are the links; Settings went from ten groups to six. |
| 7 | Flexibility and efficiency | 2 | 3 | The verdict is one tap from its cause (ring → Today); two panes from 840 dp on Home and Trends. Today, Settings and the others are still one column on a tablet. |
| 8 | Aesthetic and minimalist design | 2 | 4 | Home went from nine same-weight cards to one ring, one sentence, two quiet tiles, one chart and one row. Flat surfaces, colour only where it means a zone. |
| 9 | Error recovery | 3 | 3 | Unchanged: each link state has its plain explanation and its one action. |
| 10 | Help and documentation | 3 | 3 | How it works gained the headphone-sharing explanation (moved out of the Headphones screen); calibration and battery tips now open in the checklist row they belong to. |
| | **Total** | **26/40** | **35/40** | **Good** |

## Design specificity verdict

**LLM assessment.** The phone is now recognisably the watch's other half: put them side by side and the same ring (same 110° gap), the
same figure (same digits, no dotted zero), the same word and icon for a zone, the same amber and crimson reserved for warnings. What the
phone adds is the thing only it can show, the days: the dose building up through the day, the week's columns with every value on them,
a calendar of the month. The composition is no longer the generic "health dashboard"; the ring-to-Today shared element and the draw-in
charts give it a point of view that is the product's, not a template's.

**Deterministic scan.** Not applicable (native platform).

## Overall impression

Home now answers "how is my hearing today?" in the first screen at every font size tested (a figure, a word, a sentence), and the rest of
the app follows from that one idea. The biggest remaining opportunity is the second level: Settings and the Watch checklist are still a
long grouped list, and the charts cannot yet be explored by touch (the values are printed or spoken, not tappable).

## What's working

- **The first viewport.** At 360 × 800 dp: status line, ring with 83 %, "Loud", "of today's allowance", Tono's sentence, the uncalibrated note and the top of the tiles. At font 2.0 the ring keeps its figure and the words move under it.
- **The numbers agree.** `FiguresConsistencyTest` renders Home, Today and Trends from one state and compares what a screen reader would hear.
- **Charts follow the rules the product wrote for itself:** thin capped marks, solid hairline guides with the label beside the line, direct labels only where they help, a quiet future, every zone also an icon and a word, a spoken twin; the calendar marks today and names the month on its first day.
- **Large text is tested, not hoped for:** no broken word, no cut-off line, no overlap on 16 screens at 1.3 and 2.0.

## Priority issues

**[P2] Charts have no touch readout.** Values are labelled (week columns), named (loudest hour) or spoken (every chart has a table-style description), but a finger cannot ask a point for its value. *Fix:* tap or drag to read a column, a day or a point of the line (dataviz interaction layer). *Command:* `$impeccable delight` with the dataviz hover rules.

**[P2] Settings is still the longest screen.** Six sections instead of ten, but about 4,200 dp of grouped rows at 360 dp. *Fix:* a master-detail on large windows (Material list-detail) and quicker ways to the three things people change (alert level, accent, theme). *Command:* `$impeccable distill` then `adapt`.

**[P3] Inset-grouped lists on Settings and Watch.** They read as iOS-style groups, not Material `ListItem`s. Harmless, consistent with the rest, and a rewrite of every row for a look the product did not ask for. Left on purpose; recorded.

**[P3] Today, Alerts, Watch and Settings use one column on a tablet.** Home and Trends have two panes. Their content is lists, where one centred column is a defensible reading width.

## Persona red flags

**Casey (distracted mobile user)**: glances once: "83 %", "Loud", "A busy afternoon for your ears". No red flag. The primary action (sync) is a button at the top as well as a pull.

**Sam (accessibility-dependent user)**: at font 2.0 nothing is cut or broken; the ring is one TalkBack item ("83 percent of today's allowance, Loud", "Today in detail"); charts are described value by value. Remaining: no touch-explore on a single point of a chart.

**Jordan (first-timer)**: "today", "last 7 days", "headphones", "all sound" are four distinct names and each is explained by the line under its figure. "Allowance" is still the one word to learn; Tono's sentence and How it works explain it.

## Questions to consider

- Should the month calendar open that day (Today's layout for a past day)?
- Does the rolling-week line deserve its own screen on a tablet, next to the calendar?
