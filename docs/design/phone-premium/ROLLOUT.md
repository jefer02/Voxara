# Premium rollout — whole phone app

Brief: roll the approved Home style (docs/design/PHONE_PREMIUM.md) out to every screen in one pass, reusing the theme and
components. Rules: no commits (read-only git), no subagents, captures in Spanish at 360 dp (light + dark at font 1.0, dark
at font 2.0; `PremiumCaptureTest`, `VOXARA_PREMIUM_DIR`), each image opened once, PRODUCT.md rules kept, "remove
animations" respected. Tick an item only when it is finished; resume from the first unticked one.

## Checklist

- [x] 0. Polish: a soft ~48 dp fade behind the floating tab bar; the same under the top bar once scrolled
- [x] 1. Today: shared element from Home's card; hour chart with press-and-drag scrubbing, value bubble, haptic ticks; predictive back
- [x] 2. Headphones: shared element from Home's "Audífonos" card; live estimate as the hero; alert actions as clear buttons
- [x] 3. Trends: large title, grouped sections, clean charts with the same scrubbing, segmented control with a spring indicator and tick
- [x] 4. Tono: calm chat, one clear input, replies in grouped bubbles, the AI and safety notices short and visible
- [x] 5. Reloj: honest, prominent connection state; battery guidance as one card with a button
- [x] 6. Ajustes: inset grouped lists, real switches, short labels
- [x] 7. Alerts, How it works, setup: same components, short text, one idea per screen
- [x] 8. Baseline Profile: baselineprofile plugin + macrobenchmark module (start, Home, Trends scroll, opening Today)
- [x] 9. Captures of every changed screen + contact sheet
- [x] 10. Verify: `:phone` tests, `assembleDebug`, `assembleRelease`, `lintDebug` (`--max-workers=2 -Proborazzi.test.record=false`)

## Notes (decisions and anything a resumed session needs)

- 0: `EdgeFade` (Components.kt) under the top bar (alpha follows the collapse; replaces the hairline) and above the capsule (PhoneApp.kt `CapsuleLayout`).
- 1: `scrub()` + value bubble in Charts.kt (HourColumns, DayColumns, LineChart via `bubbles`; RollingLine takes `dayLabels`, CumulativeLine `nowMinute`). Today's sections use `ChartCard`. Predictive back = manifest flag + NavHost (no code).
- 2: `onNotHeadphones` action wired to `PhoneListeningService.notHeadphones(speaker = false)`; strings hp_lower_action/hp_dismiss_action now "Bajar volumen"/"Entendido" (also on the notification); HeadphoneLive.kt rewritten (HeadphoneHero, alert with 3 stacked buttons, BatteryGuide as one card with a button). HeadphonesScreen: HeadphoneHero(sharedKey = HEADPHONES), alert, today rows (`ValueRow`), weekly tile, sharing row.
- 3: `SegmentedChoice` now draws a spring accent indicator (drawBehind) with a segment tick (also in Settings); Trends: hero in `HeroCard` (counting), charts in `ChartCard`, rolling line scrubbable by day, headphone share + score + freshness as one `Group`, "Ask Tono" as a row.
- 4: TonoScreen rewritten: one notice line (tono_notice_ai / tono_notice_offline), suggestion pills, question/reply bubbles (reply = one surface with its source), capsule input over a fade. tono_not_medical / tono_unavailable may now be unused (check lint).
- 5: WatchScreen LinkedCard is a HeroCard (state from linkHealth/problem as icon + word, why, details, two full-width buttons); the separate health banner is gone; BatteryGuide = one card with a button + "Ahora no".
- 6: Settings keyed items, "Alertas" header (sec_alerts), sampling switch without the redundant subtitle; the spring segmented control comes from item 3. 7: AlertRow on row tokens, How it works = one group of ExpandableRows + the medical card always visible, Setup titles in largeTitle and points/medical as grouped rows.
- 8: `:baselineprofile` (com.android.test + androidx.baselineprofile 1.5.0, benchmark-macro 1.5.0, uiautomator 2.3.0): BaselineProfileGenerator + PhoneBenchmarks (startup + journeys, None vs Partial(Require)); journeys find parts by test tag (testTagsAsResourceId in MainActivity; tab_<route>, list, dose_card, setup_continue). :phone applies the plugin + profileinstaller. The profile itself is NOT generated yet: it needs a connected device (`./gradlew :phone:generateBaselineProfile`).
- 9: `PremiumCaptureTest` now renders 10 screens (light/dark at 1.0, dark at 2.0) into docs/design/phone-premium/rollout/; contact sheet: docs/design/phone-premium/contact_sheet_rollout.png.
- 10: tests 95 (0 failed, 26 opt-in skipped), assembleDebug + assembleRelease OK, lintDebug 0 errors; the two strings Tono no longer used (tono_weekly_title, tono_not_medical) were removed afterwards.
