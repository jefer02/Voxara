# Voxara — unattended work log

All paths are relative to the project root `C:\Users\jefer\OneDrive\Mac\REPOSITORIOS\Voxara`.
Kotlin sources live under `app/src/main/java/com/example/voxara/` (abbreviated `…/voxara/`).
No git commands were used. The API key exists only in `local.properties`.

Status legend: ✅ done · 🟡 in progress · ⛔ blocked / skipped

---

## Environment
- Connected device: **Wear OS emulator only** (`emulator-5554`, API 37 / Android 17, 480×480 px @ 320 dpi = 240 dp).
  No physical Galaxy Watch 7 was connected, so nothing was measured on real hardware.
  Only the Voxara debug app was installed/updated on the emulator; no settings were changed.
- Layout targets assumed: Galaxy Watch 7 44 mm ≈ 480 px (~240 dp), 40 mm ≈ 432 px (~216 dp),
  plus the Wear OS small-round minimum (192 dp).

---

## Phase 0 — correctness and safety ✅ (previous session)
Files created:
- `…/voxara/core/calibration/MicSource.kt`
- `…/voxara/core/ledger/Ledger.kt`
- `…/voxara/core/dose/Accounting.kt`
- `…/voxara/service/Notifications.kt`
- `…/voxara/service/BootReceiver.kt`
- `…/voxara/ui/screens/CalibrationProbePanel.kt`
- `app/src/test/java/com/example/voxara/core/Phase0FixesTest.kt`

Files modified:
- `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- `…/voxara/audio/AudioBurstSampler.kt`, `…/voxara/core/risk/RiskStateMachine.kt`
- `…/voxara/data/ExposureRepository.kt`, `…/voxara/data/VoxaraStore.kt`
- `…/voxara/presentation/MainActivity.kt`
- `…/voxara/service/DosimeterService.kt`, `…/voxara/service/MidnightRollupWorker.kt`
- `…/voxara/ui/VoxaraApp.kt`, `…/voxara/ui/gauge/NeonReactiveGauge.kt`, `…/voxara/ui/screens/ModesScreen.kt`
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-es/strings.xml`

Removed features (intentional): `BODY_SENSORS` permission, `specialUse` FGS type, in-service level simulator
(replaced by a debug-only, display-only bench driver in `MainActivity`).

---
## Phase 0b — guided calibration + Galaxy Watch robustness ✅
What was done:
- **Provisional offsets in one place:** `ProvisionalMicOffsets` (`UNPROCESSED_DB = 36.0`, `VOICE_RECOGNITION_DB = 18.0`),
  CDD-derived, clearly marked NOT measured. `MicSource.provisionalOffsetDb` reads from it.
- **Guided calibration** (Modes/Settings page → "Calibration" panel → *Calibrate*): steps in plain language, reference type
  (sound level meter / phone app ±3–5 dB), ~10 s steady capture (1.5 s settle + 10 s) through the active mic path,
  steadiness check (std-dev ≤ 2 dB, ≥ 24 readings), reference entry with the crown or −/+ in 0.5 dB steps,
  too-quiet (< 50 dBA) / too-loud (> 90 dBA, advice ≤ 85) checks, offset = reference − measured,
  optional second measurement with a > 3 dB inconsistency warning (save average anyway / start over).
  Stored per source with date, device model (`Build.MANUFACTURER + Build.MODEL`) and reference type.
  A calibration made on a different model is ignored (uncalibrated again). Saving resets the fine trim to 0.
- Offset range widened: source offsets −60…+60 dB, fine trim −40…+40 dB (was ±24).
- **Calibration status:** "Calibrated (date)" vs "Uncalibrated estimate" in the settings panel; "≈ UNCALIBRATED ESTIMATE"
  badge on the Live (home) screen until the active path is calibrated.
- **Debug readout** now also shows manufacturer, model, API level/release, UNPROCESSED support (runtime property) and active source.
- **Runtime capability detection:** `DeviceAudioInfo` (model, API, UNPROCESSED property), `PowerPolicy`
  (power-save mode, battery-optimisation exemption). The UNPROCESSED/VOICE_RECOGNITION fallback from Phase 0 is kept.
- **Paused state:** heartbeat saved every 30 s; `monitoringStatus()` = RUNNING / PAUSED / OFF. Live screen shows
  "Monitoring paused · Resume" (one tap), settings panel explains it, tile shows it. `MonitoringWatchdogWorker`
  (every 15 min) posts one tap-to-resume notification per stale episode. The service loop now survives a failing
  iteration (logs, backs off 2 s) instead of dying silently; a loop that ends without the user stopping it reports PAUSED.
- **Keep-running guidance** (Settings panel): power-save warning when on (detected), phone Galaxy Wearable battery →
  Unrestricted (verified Samsung path), and a *Battery optimization* button only when the watch actually resolves
  `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (watch-side OEM path NOT verified — could not find official docs).
- `Capsule` buttons now have a 48 dp minimum touch height.

Files created:
- `…/voxara/core/calibration/GuidedCalibration.kt`
- `…/voxara/core/monitoring/MonitoringHealth.kt`
- `…/voxara/audio/DeviceAudioInfo.kt`
- `…/voxara/service/MonitoringWatchdogWorker.kt`
- `…/voxara/ui/screens/CalibrationScreen.kt`
- `app/src/test/java/com/example/voxara/core/GuidedCalibrationTest.kt`

Files modified:
- `…/voxara/core/calibration/MicSource.kt` (ProvisionalMicOffsets, ranges; `cddStartOffsetDb` → `provisionalOffsetDb` + `cddDerivedOffset()`)
- `…/voxara/data/VoxaraStore.kt` (calibration records, heartbeat; removed the Phase 0 `source_offset_*` keys)
- `…/voxara/data/ExposureRepository.kt` (calibrations, device, power, monitoringStatus, probe readings flow)
- `…/voxara/service/DosimeterService.kt` (probe readings, heartbeat, device info, iteration guard, PAUSED on unexpected end)
- `…/voxara/service/Notifications.kt` (resume prompt reason)
- `…/voxara/presentation/MainActivity.kt` (save calibration, health refresh, battery settings, watchdog scheduling, resume)
- `…/voxara/ui/VoxaraApp.kt` (calibration overlay), `…/voxara/ui/screens/LiveScreen.kt` (paused + uncalibrated badge),
  `…/voxara/ui/screens/ModesScreen.kt` (keep-running + calibration panels), `…/voxara/ui/screens/CalibrationProbePanel.kt`
  (device rows), `…/voxara/ui/screens/Common.kt` (48 dp capsule), `…/voxara/tile/VoxaraTileService.kt` (paused text)
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-es/strings.xml` (+53 strings each)
- `app/src/test/java/com/example/voxara/core/Phase0FixesTest.kt` (renamed API)

Tests: `GuidedCalibrationTest` (13): synthetic 1 kHz sines through the real A-weighting filter, offset reproduces the
reference, two levels 10 dB apart agree, fluctuating signal rejected, jitter accepted, too-few readings, reference bounds,
±40 dB offsets storable, > 3 dB inconsistency, 0.5 dB snapping, per-device binding, paused-state rules.
Build: debug ✅ · unit tests 45/45 ✅


### Layout verification harness (added during Phase 0b/1) ✅
The only connected device was an overloaded Wear emulator: the debug app hit repeated **startup ANRs**
("failed to complete startup", main thread inside AndroidX class init, emulator at ~290% system CPU) — an emulator
performance problem, not a Voxara crash. Layouts are therefore verified on the JVM instead:
Robolectric 4.17 + Roborazzi 1.75.0 render each screen at **192 / 216 / 240 dp** × font scale **1.0 / 1.3**
into `app/build/screenshots/` (review images, not golden files).
- Created: `app/src/test/java/com/example/voxara/ui/ScreenshotHarness.kt`, `app/src/test/java/com/example/voxara/ui/ScreenLayoutTest.kt`
- Modified: `gradle/libs.versions.toml` (robolectric, roborazzi, roborazzi-compose), `app/build.gradle.kts`
  (test deps, `isIncludeAndroidResources`, record property, 2 GB test heap)
- Added `LocalReduceMotion` (`…/voxara/ui/theme/VoxaraTheme.kt`): free-running animations stop when the system
  "remove animations" setting is on (and in the harness). `…/voxara/ui/gauge/Gauges.kt`, `NeonReactiveGauge.kt` honour it;
  `MainActivity` reads `Settings.Global.ANIMATOR_DURATION_SCALE`.

---

## Phase 1 — data and alert foundation ✅
What was done:
- **Minute ledger** (framework SQLite, no new dependency): one row per (minute, kind, origin) with measured seconds,
  energy (Pa²·s) and the exact NIOSH dose per burst. 35-day retention. Numbers only — no audio, names or titles.
  Pure logic in `core/ledger` over a `MinuteStore` interface (SQLite in the app, a map in tests).
- **Bug #9 fixed:** hourly profile is now a true energy average from minute records (the old pairwise running average
  over-weighted the newest burst). Day screen shows ledger-derived dose per day and **coverage** ("MEASURED 3h 12m").
- **Rolling 7-day budget** (`RollingWeek`) replaces the Monday reset from Phase 0 (your decision 2). Ambient and headphone
  ledgers are separate (`ExposureKind`). A minute reported by two devices counts once — the louder (`mergeByMinute`).
- **AlertEngine** (pure, deterministic, offline, AI-independent):
  - Ambient level alert on a **sliding 3-minute Leq** (needs ≥ 80% window coverage), default **85 dBA**, configurable
    **80–95**; re-arms 5 dB below; 30-min cooldown.
  - Daily NIOSH 50% (advisory), 80% (warning), **100% (limit)**; weekly rolling 80% / **100%**; headphone tiers ready
    for Phase 2. **Limit alerts are never rate-limited** (no cap, snooze, quiet hours or cooldown) and **re-alert at
    each further +100%**.
  - Daily cap (4) applies to advisories only; lifted in Concert mode. Snooze 1 h (not on limits). Quiet hours hold
    minor alerts only. State persisted (`AlertMemoryCodec`).
- **Delivery:** new high-importance "Hearing alerts" channel; each alert has text + **Got it / Snooze 1 h / Open**
  (no Snooze on 100% alerts); one distinct haptic per tier (soft tick, two soft, long-short-long, quick triple, …).
- **Settings (current style, Modes page):** alert threshold stepper, sensitive listener (0.51 Pa²h) toggle, quiet hours
  toggle + start/end in 30-min steps.
- Ongoing notification is rebuilt on a risk change or at most once a minute (was every 5 s).
- "Reset dose" now also clears today's ambient minutes so dose, history and weekly budget stay consistent.

Files created:
- `…/voxara/core/ledger/MinuteLedger.kt`, `…/voxara/core/ledger/LedgerBook.kt`
- `…/voxara/core/alerts/AlertEngine.kt`
- `…/voxara/data/ExposureDb.kt`
- `…/voxara/haptics/AlertHaptics.kt`
- `…/voxara/service/AlertNotifier.kt`, `…/voxara/service/AlertActionReceiver.kt`
- `app/src/test/java/com/example/voxara/core/AlertEngineTest.kt`, `app/src/test/java/com/example/voxara/core/MinuteLedgerTest.kt`

Files modified:
- `…/voxara/core/ledger/Ledger.kt` (rollover = daily counters only; `dayStartMinute`, `localMinuteOfDay`, `DaySummary`, `summarizeDays`)
- `…/voxara/core/risk/RiskStateMachine.kt` (**interrupting alerts removed** → AlertEngine; keeps ring state, relief, impulse)
- `…/voxara/data/VoxaraStore.kt` (rewritten: no CSV profiles; alert settings + memory; headphone weekly cache)
- `…/voxara/data/ExposureRepository.kt` (`days`, `sustainedDba`, `alertSettings`)
- `…/voxara/service/DosimeterService.kt` (ledger, rolling budget, sustained level, AlertEngine, reset handling)
- `…/voxara/haptics/HapticConductor.kt` (4 new patterns)
- `…/voxara/presentation/MainActivity.kt`, `…/voxara/ui/VoxaraApp.kt`, `…/voxara/ui/screens/ModesScreen.kt` (alert settings),
  `…/voxara/ui/screens/DayScreen.kt` (ledger history + coverage), `…/voxara/ui/screens/LiveScreen.kt` (paused layout),
  `…/voxara/ui/screens/CalibrationScreen.kt` (side insets)
- `app/src/main/AndroidManifest.xml` (AlertActionReceiver)
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-es/strings.xml` (+35 each)
- Tests migrated: `NoiseDoseEngineTest.kt` (cap test → relief test; cap now in AlertEngineTest),
  `Phase0FixesTest.kt` (DoseFull tests → AlertEngineTest; profile/Monday-reset rollover tests → MinuteLedgerTest)

Removed (intentional, replaced): `RiskStateMachine.Event.ThresholdCrossed/DoseFull` and `dailyAlertCap`;
DataStore CSV keys `today_profile`, `week_profiles`, `week_epoch` (no longer read or written); `DayScreen.dosePercentOf()`
and `dayLeq()` (the bug-#9 approximation). Existing on-device CSV history is not migrated (debug data only).

Known limits: after a process restart the minute in progress restarts from zero (≤ 1 min of history detail).
At 192 dp × 1.3 font the Live screen still pushes the dose line off-screen → redesigned in Phase 6.

Build: debug ✅ release ✅ · unit tests 68/68 ✅ (incl. 5 screenshot tests, 30 images)

---

## Phase 2 — headphones on the watch ✅
What was done:
- **Detection** of headphones connected to THIS watch via `AudioManager.getDevices(GET_DEVICES_OUTPUTS)` +
  `registerAudioDeviceCallback`: wired, USB headset, Bluetooth A2DP, BLE headset (API 31), BLE broadcast (API 33).
  Hearing aids, speakers and the watch speaker are excluded. **Debounced** (a change must hold 3 s).
- **Category only** (earbuds / over-ear / wired / unknown). Earbuds vs over-ear is not exposed by any public API, so
  it comes from a new setting "My headphones are usually…" (else "unknown"). **No names or addresses are stored.**
- **BLUETOOTH_CONNECT** declared and requested **in context** (Headphones screen → "Allow Bluetooth details", API 31+),
  used only to read the Bluetooth device class so a speaker/car kit is not counted as headphones; the address is read
  transiently and never kept. Without it, Bluetooth outputs count as "unknown headphones".
- **Estimate** (public APIs only; microphone never used): `100 dBA` assumed at full volume (EN 50332 EU limit for music
  players) + `AudioManager.getStreamVolumeDb()` attenuation for the current index; fallback linear map flagged
  VERY LOW confidence. Always labelled "Estimated · low confidence" with a ±10 dB range and a "Why estimated?" note.
  Assumptions live in one place: `HeadphoneProfile`.
- **Headphone ledger** (separate `ExposureKind.HEADPHONE`), rolling 7-day H.870 dose; **time left this week at this
  volume**; listening is still recorded when the mic cannot measure (no microphone needed).
- **Double-count rule** (`attribute()`): while listening, each span belongs to exactly one ledger — the louder at the
  ear: headphone estimate vs room level minus passive attenuation (earbuds 10 dB, over-ear 15 dB, wired/unknown 0).
  The other ledger gets nothing. Two devices reporting the same minute: the louder counts (Phase 1 `mergeByMinute`).
- **Alerts** (AlertEngine, Phase 1): weekly 80% (Got it / Snooze 1 h / Open, two soft ticks), weekly 100% (Got it /
  Open, **no snooze, never rate-limited**, re-alerts at each further +100%, long-short-long), very loud listening
  (sliding 5-min estimate ≥ 100 dBA, configurable 95–105, 30-min cooldown, quick triple).
- **Surfaces:** new Headphones page (2nd page) with estimated live level, weekly ring, time left, 7-day bars, why-estimated;
  headphone chip on the Live screen; **Listening tile**; **Listening complication** (RANGED_VALUE + SHORT_TEXT).
- Settings: usual headphone type, very-loud-listening threshold.

Files created:
- `…/voxara/core/headphones/Headphones.kt`
- `…/voxara/audio/HeadphoneMonitor.kt`
- `…/voxara/ui/screens/HeadphonesScreen.kt`
- `…/voxara/tile/ListeningTileService.kt`
- `…/voxara/complication/ListeningComplicationService.kt`
- `app/src/test/java/com/example/voxara/core/HeadphonesTest.kt`

Files modified:
- `…/voxara/service/DosimeterService.kt` (headphone monitor, headphone ledger, attribution, alerts inputs, refreshes)
- `…/voxara/data/ExposureRepository.kt`, `…/voxara/data/VoxaraStore.kt` (headphone state, usual-headphones setting)
- `…/voxara/presentation/MainActivity.kt` (BLUETOOTH_CONNECT in context, usual headphones)
- `…/voxara/ui/VoxaraApp.kt` (6 pages), `…/voxara/ui/screens/ModesScreen.kt`, `…/voxara/ui/screens/LiveScreen.kt`
- `app/src/main/AndroidManifest.xml` (BLUETOOTH_CONNECT, Listening tile + complication)
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-es/strings.xml` (+33 each)
- `app/src/test/java/com/example/voxara/ui/ScreenLayoutTest.kt` (headphones screenshots)

Limits (honest): headphone tracking runs inside the dosimeter service, so it needs monitoring on (and the mic
permission, which the service requires). "Playing to headphones" = media active while headphones are connected
(Android routes media to the newest headphones by default; the exact route is a system API). The estimate cannot
know the real headphone sensitivity or the music's loudness.

Build: debug ✅ · unit tests 70/70 ✅ (+6 headphone screenshots)

---

## Phase 3 — phone companion v1 ✅
What was done:
- New Gradle module **`:phone`** (Android app, **same applicationId `com.example.voxara`** as the watch — required by
  the Wearable Data Layer; namespace `com.example.voxara.phone`; minSdk 28). Plain views, no Compose, tiny.
- **Consent screen** (`ConsentActivity`): what it does, exactly what is sent, what is never sent, limits, opt-in
  button (**OFF by default**), status "last sent to your watch". Asks BLUETOOTH_CONNECT (API 31+) only on opt-in.
- **Sampling** (`ListeningSampler`, WorkManager periodic 15 min + immediate samples at Bluetooth connect/disconnect via
  **manifest-exempt** broadcasts `BluetoothA2dp/BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED`,
  `ACL_CONNECTED/DISCONNECTED`). No foreground service, no persistent notification.
- **What is sent** (DataClient, urgent data item `/voxara/listening/<time>`): end time, seconds, media volume dB +
  index/max, output type. **Never:** audio, titles, app names, device names, addresses. Nothing leaves the user's devices.
- **Watch side** (`ListeningSyncService`, WearableListenerService, `/voxara/listening` prefix): converts each span with
  the SAME estimator (`estimateListening`) into headphone minutes with `origin = phone`, confidence VERY LOW;
  applies the **double-count rule** per minute against the ambient minute (`resolvePhoneMinute`: the louder source
  keeps the minute; if headphones win the ambient minute is removed, if the room wins the phone minute is dropped);
  deletes the data item after storing. The dosimeter reloads both ledgers and re-derives today's dose from them.

Files created:
- `settings.gradle.kts` → **modified** (`include(":phone")`)
- `phone/build.gradle.kts`
- `phone/src/main/AndroidManifest.xml`
- `phone/src/main/java/com/example/voxara/phone/ConsentActivity.kt`
- `phone/src/main/java/com/example/voxara/phone/ListeningSampler.kt`
- `phone/src/main/java/com/example/voxara/phone/PhonePrefs.kt`
- `phone/src/main/java/com/example/voxara/phone/HeadphoneConnectionReceiver.kt`
- `phone/src/main/res/values/strings.xml`, `phone/src/main/res/values-es/strings.xml`
- `phone/src/main/res/drawable/ic_voxara.xml` (copy of the watch status icon)
- `phone/src/test/java/com/example/voxara/phone/ListeningSamplerTest.kt`
- `…/voxara/core/headphones/PhoneListening.kt` (watch: contract, conversion, double-count resolution)
- `…/voxara/sync/ListeningSyncService.kt` (watch)
- `app/src/test/java/com/example/voxara/core/PhoneListeningTest.kt`

Files modified:
- `…/voxara/data/ExposureRepository.kt` (`ledgerGeneration`)
- `…/voxara/service/DosimeterService.kt` (`syncLedgerChanges()`)
- `app/src/main/AndroidManifest.xml` (ListeningSyncService)

Limits (v1, honest): 15-minute sampling cannot see pauses or volume changes between samples; wired headphones on the
phone are only seen when a sample happens to run (the wired-plug broadcast cannot be registered in the manifest);
the phone's volume curve differs by phone. v2 (Companion Device Manager) was not built, per your decision 6.
Needs the phone app and the watch app signed with the same key (debug builds are, by default on one machine).

Build: watch debug ✅ · phone debug ✅ · unit tests 77/77 ✅ (watch core 70 + phone 2 + … see final report)

---

## Phase 4 — AI engine: Tono ✅
What was done (the deterministic engine decides; Tono only explains, coaches and converses):
- **Name:** "Tono", one string resource `ai_name` (EN/ES) — rename there.
- **Privacy gate:** `CoachSnapshots.build()` is the only builder of `AggregateSnapshot`; its `toWire()` is a fixed
  whitelist (tested): dose %, rolling weekly % (ambient, headphone), rounded dBA, minutes, loud hours per coarse day
  part, headphone category, ambient category, calibrated flag, score, streak, projection. Never audio, titles, app
  or device names, location, identifiers or fine timestamps.
- **Deterministic figures** (tested): **Hearing Care Score** 0–100 (listening-habits score: weekly load penalty above
  50%, −8 per day over the daily limit; not a hearing test), weekly-limit **projection** ("in N days" from the recent
  daily pace), **gentle streak** (one slip per 7 days forgiven).
- **Guardrails:** `SymptomGuard` (EN/ES: tinnitus, ringing, muffled, ear pain, zumbido, oído tapado…) → fixed reply
  recommending a hearing professional, **never sent to any model**. System prompt forbids diagnosis/medical claims.
- **Reply validation:** strict JSON `{status≤60, insight≤140, why≤120, suggestion≤80, chips≤3×20}`, banned
  medical/alarm vocabulary (word-boundary, EN/ES), and **every number in the reply must exist in the data (±1)**.
  Invalid → offline template.
- **Client seam:** `CoachClient` interface; `OpenAiCompatibleCoachClient` (DeepSeek `/chat/completions`, base URL
  and model from BuildConfig/`local.properties`, default `deepseek-flash` checked against DeepSeek's current docs,
  JSON mode, `max_tokens` 250, temperature 0.3, 8 s connect / 12 s read timeouts) over an `HttpTransport` (fake in
  tests; **no test calls the real endpoint**). A server proxy can replace it later without touching the watch UI.
- **Reliability:** cache (24 entries, per task/day/coarse data), budget (≤ 1 automatic insight per hour, ≤ 20 calls a
  day), loading and failure states, **offline templates** (`OfflineCoach`, EN/ES) whenever cloud AI is off, offline,
  over budget or invalid — the app always works.
- **Consent:** cloud AI **OFF by default**; explicit toggle with a plain explanation of exactly what is sent
  (settings panel now; onboarding step in Phase 6). Release builds have **no API key and no INTERNET permission**
  (`app/src/debug/AndroidManifest.xml` adds INTERNET to debug only) — verified with `aapt dump permissions` and a
  byte search of both APKs.
- Minimal settings panel: consent toggle, "Ask Tono for today", reply with its source (CLOUD AI / BUILT-IN / SAFETY).

Files created:
- `…/voxara/core/ai/MiniJson.kt`, `…/voxara/core/ai/Coach.kt`
- `…/voxara/ai/CoachSnapshots.kt`, `…/voxara/ai/CoachClient.kt`, `…/voxara/ai/Tono.kt`
- `app/src/debug/AndroidManifest.xml`
- `app/src/test/java/com/example/voxara/core/CoachTest.kt`

Files modified:
- `…/voxara/data/VoxaraStore.kt` (`ai_cloud_consent`, `onboarding_done`), `…/voxara/data/ExposureRepository.kt` (`aiConsent`)
- `…/voxara/ui/screens/ModesScreen.kt`, `…/voxara/ui/VoxaraApp.kt`, `…/voxara/presentation/MainActivity.kt`
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-es/strings.xml` (+33 each)

Build: debug ✅ release ✅ · unit tests 90/90 ✅ (app) + 2/2 (phone)

---

## Phase 5 — design system ✅
What was done:
- **Tokens** (`ui/design/Tokens.kt`, raw values in `core/design/Palette.kt`): true-black base, tonal surfaces
  #121316 / #1C1D21 / #26282D, text inks #FFFFFF / #B9BEC8 / #8E94A0, zones OK #3DB8F5 · Moderate #F2C94C ·
  Loud #FF8C42 · Dangerous #FF4F8B, brand gradient #35E8FF→#8B7CFF (brand/AI accents only, never state).
  Type scale on **Inter** (SIL OFL 1.1, bundled variable font; tabular numerals; minimum 12 sp; all sp so the
  system font size scales it), 4-pt spacing, 26 dp card radius, full pills, 48 dp touch minimum, motion springs.
- **One zone model** `core/risk/RiskZone.kt` (OK < 70, Moderate 70–84, Loud 85–94, Dangerous ≥ 95; percent zones
  50/80/100) — every zone has a **word and its own icon shape** (check, info, triangle, octagon).
- **Icons** (`ui/design/VoxIcons.kt`): Voxara's own rounded 2-px stroke set drawn in code (no third-party/platform
  icon assets, nothing from Apple).
- **Components** (`ui/design/Components.kt`): ZoneGauge (270° ring 30–110 dBA, zone bands, per-zone fill, pointer;
  ambient = hairline), ProgressRing, ZoneBadge, HeroStatus, SectionLabel, BodyText, PillButton (Wear M3 Button /
  FilledTonal / Outlined, ≥ 48 dp), ReplyChip, MetricCard, Panel, WeekBars (with spoken description), AlertSheet,
  EmptyState. All respect ambient mode and reduced motion.
- Material 3 theme mapped to the tokens; `Typography(Inter)` for every M3 component.
- **Debug bench moved out of main:** `app/src/debug/.../bench/Bench.kt` (real) and `app/src/release/.../bench/Bench.kt`
  (no-op) — the simulator code no longer ships in release.
- **Tests:** WCAG contrast of every text token on every surface (≥ 4.5:1), zone colours as graphics (≥ 3:1) and
  dark text on zones (≥ 4.5:1), zone model; component gallery screenshots at 192/216/240 dp × 1.0/1.3.

Files created:
- `…/voxara/core/design/Palette.kt`, `…/voxara/core/risk/RiskZone.kt`
- `…/voxara/ui/design/Tokens.kt`, `…/voxara/ui/design/VoxIcons.kt`, `…/voxara/ui/design/Components.kt`
- `app/src/main/res/font/inter_variable.ttf` (Inter, downloaded from github.com/google/fonts, OFL)
- `app/src/main/assets/licenses/Inter-OFL.txt` (its licence, shipped with the app)
- `app/src/debug/java/com/example/voxara/bench/Bench.kt`, `app/src/release/java/com/example/voxara/bench/Bench.kt`
- `app/src/test/java/com/example/voxara/core/DesignSystemTest.kt`, `app/src/test/java/com/example/voxara/ui/DesignSystemScreenshotTest.kt`

Files modified:
- `…/voxara/ui/theme/VoxaraTheme.kt` (M3 colour roles → tokens, Inter typography)
- `…/voxara/presentation/MainActivity.kt` (bench moved out)
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-es/strings.xml` (zone words)

Build: debug ✅ release ✅ · unit tests 105/105 ✅

---

## Phase 6 — full redesign ✅
Navigation (Wear Compose Navigation 1.5.6, swipe right = back):
**Onboarding** (first run) → **Home** (root: Tono's status line as the hero, Hearing Care Score, cards) →
**Now** · **Headphones** · **Today** · **Week** · **Ask** (edge button on Home) · **Settings** → **Calibration**.
Ambient mode shows one always-on view; a 100% limit shows a full-screen alert while the app is open.

Screens (all built on the Phase 5 design system, every one checked at 192/216/240 dp × 1.0/1.3):
- **Onboarding** (7 steps, 6 in release): what Voxara does · levels never recordings · estimates not diagnoses
  (not a medical device) · permissions asked **in context with their reason** (mic required, notifications,
  Bluetooth optional) · alert level (80/85/90/95, 85 recommended) · Tono cloud-AI opt-in (only when the build has a
  key) · keeping monitoring running on Galaxy Watch · calibrate later. Nothing is requested at app launch any more.
- **Home:** Tono status line (hourly, rate-limited), score ring (listening-habits wording), paused card with one-tap
  resume, cards for Now / Headphones / Today / Week / Settings, "Ask Tono" edge button, not-a-medical-device note.
- **Now:** zone gauge with the level inside, zone word + icon, scene/band, dose and time left, Concert mode
  start/stop (the old Concert page), uncalibrated note.
- **Headphones:** status by category, estimated level + zone + confidence range, weekly ring, time left at this
  volume, 7-day bars, Bluetooth-details prompt, "Why estimated?", phone-companion hint.
- **Today:** daily dose ring, 8-hour equivalent, 24 hourly bars (height AND colour by level), coverage.
- **Week:** rolling 7-day ring (worst of both ledgers), ambient and headphone cards, 7 daily bars, pace projection,
  gentle streak, score explanation, "Weekly summary from Tono".
- **Ask:** speak (on-device recognition) or tap quick replies; symptoms → fixed safety answer; cloud Tono only with
  consent, otherwise the existing deterministic voice intents answer; spoken reply; source label.
- **Settings:** monitoring (+ paused/resume, power-save warning, keep-running tips, battery button when available),
  alerts (level, quiet hours, sensitive listener), headphones (usual type, very-loud level), Tono consent,
  calibration (status → guided flow, fine trim), language, privacy & about (Inter licence), advanced
  (**Reset today's dose now needs a confirmation dialog**), debug tools (debug builds only: calibration readout,
  bench scenarios).
- **Limit alert** (daily / weekly / headphone 100%): zone icon, title, body, Got it, "Why?" (Tono explains).
- **Ambient:** hairline gauge, outline numeral, zone badge; no animation.
- **Calibration** screens restyled to the design system.
- **Tile** redesigned: dose ring, number, zone word (or time left), "Updated HH:MM" / paused; ≥ 12 sp.
  **Listening tile** and both **complications** use the shared zone palette and words.
- Status icon tint changed from neon green to the brand cyan.

Files created:
- `…/voxara/ui/VoxaraUi.kt` (routes, `UiEnv`, `VoxaraActions`)
- `…/voxara/ui/design/Layout.kt` (VoxList, ValueStepper, ChoiceList, InfoRow)
- `…/voxara/ui/screens/HomeScreen.kt`, `NowScreen.kt`, `TodayWeekScreens.kt`, `AskScreen.kt`, `SettingsScreen.kt`,
  `OnboardingScreen.kt`, `OverlayScreens.kt` (limit alert, ambient)

Files rewritten: `…/voxara/ui/VoxaraApp.kt`, `…/voxara/presentation/MainActivity.kt`, `…/voxara/ui/theme/VoxaraTheme.kt`,
`…/voxara/ui/screens/HeadphonesScreen.kt`, `…/voxara/ui/screens/CalibrationProbePanel.kt`, `…/voxara/tile/VoxaraTileService.kt`,
`app/src/test/java/com/example/voxara/ui/ScreenLayoutTest.kt`

Files modified: `…/voxara/ui/screens/CalibrationScreen.kt` (restyle), `…/voxara/voice/VoiceTurn.kt` (hand-off to Tono,
`speak()`), `…/voxara/ui/design/Components.kt`, `…/voxara/tile/ListeningTileService.kt`,
`…/voxara/complication/DoseComplicationService.kt`, `…/voxara/complication/ListeningComplicationService.kt`,
`app/src/main/res/drawable/ic_voxara_status.xml` (tint), `gradle/libs.versions.toml` + `app/build.gradle.kts`
(compose-navigation), strings EN/ES (+101 each)

**Files deleted (replaced by the redesign — features kept, see mapping):**
- `…/voxara/ui/screens/LiveScreen.kt` → Now + Home (live meter, dose, time left, paused, uncalibrated badge)
- `…/voxara/ui/screens/DayScreen.kt` → Today + Week (daily history; the polar clock became hourly bars; the crown
  scrub through past days became the 7-day bars on Week)
- `…/voxara/ui/screens/ConcertScreen.kt` → Concert mode start/stop on Now (the spectrum visual was dropped)
- `…/voxara/ui/screens/VoiceScreen.kt` → Ask (same voice turn, plus Tono)
- `…/voxara/ui/screens/ModesScreen.kt` → Settings (mode cards: Concert is on Now, Urban is the default, "Voice mode"
  is Ask)
- `…/voxara/ui/screens/Common.kt` → design-system components
- `…/voxara/ui/gauge/Gauges.kt`, `…/voxara/ui/gauge/NeonReactiveGauge.kt` → ZoneGauge / ProgressRing
  (the animated neon aura was dropped: no scale, constant redraw)

Tests: ScreenLayoutTest (12 screens × 3 sizes × 2 font scales = 72 images) + design gallery (24 images).
Build: debug ✅ release ✅ phone ✅ · unit tests all passing (see final report)

### Lint (after Phase 6)
`./gradlew :app:lintDebug`: **0 errors** after one fix (`today_note` had a bare "%", reworded EN/ES).
Remaining warnings, none blocking: 47 UnusedResources (strings of the deleted screens — kept so nothing
translated is lost; safe to prune), 22 GradleDependency / 3 NewerVersionAvailable (version bumps not applied
unattended), 11 InlinedApi (AudioDeviceInfo TYPE_BLE_* constants from API 31/33 inlined at compile time — safe on
API 30), 21 VisibleForTests, 18 PluralsCandidate, 2 UseKtx, 1 ObsoleteSdkInt, 1 AppBundleLocaleChanges,
1 SquareAndRoundTilePreviews.

---

# FINAL REPORT

## 1. Phase status
| Phase | Status | Key result |
|---|---|---|
| 0 correctness & safety | ✅ | CDD offsets, no simulated data in the dose, uncapped 100% alert, real-time accounting, safe lifecycle |
| 0b guided calibration + Galaxy robustness | ✅ | Settings → Calibration flow; paused state + one-tap resume; watchdog |
| 1 data & alert foundation | ✅ | SQLite minute ledger, rolling 7-day budget, deterministic AlertEngine, bug #9 fixed |
| 2 headphones on the watch | ✅ | detection, estimate, separate ledger, double-count rule, alerts, tile, complication |
| 3 phone companion v1 | ✅ | `:phone` module, Data Layer sync, consent screen |
| 4 AI engine (Tono) | ✅ | privacy gate, validation, offline fallback, consent, debug-only key |
| 5 design system | ✅ | tokens, Inter, icons, components, contrast tests |
| 6 full redesign | ✅ | onboarding, Home with Tono, all screens, tile/complications, ambient |
| 7 later items | ⛔ not started (by plan) | server proxy, phone companion v2 (CDM), TFLite scene model |

Tests at the end: every app unit test (pure core + Robolectric screenshot renders) and the phone unit tests pass;
debug and release build; the release APK contains **no API key and no INTERNET permission** (checked with `aapt`
and a byte search).

## 2. How to verify on the Galaxy Watch 7
Install: `./gradlew :app:installDebug` (watch) and, for the phone companion, `./gradlew :phone:installDebug` on the
paired phone (built on the same machine → same debug signing key, which the Data Layer requires).

**Calibration first — nothing else is accurate until this is done:**
1. Home → Settings card → Calibration card → *Calibrate*. Choose the reference type.
2. Point the watch mic at a steady sound (fan / vacuum / steady speaker noise), reference meter at the same
   distance, at or below 85 dB. *Start measuring*; hold still about 12 s.
3. Dial the reference reading with the crown (0.5 dB steps) → *Use this value* → optionally *Second measurement*
   at a level at least 6 dB different → *Save*. Settings shows "Calibrated (date)"; the "uncalibrated" notes go away.
4. Debug readout (Settings → Debug tools, debug build): check which mic path the watch really uses, the UNPROCESSED
   property, the API level and raw dBFS. **Please send me the saved offsets and the watch model** so the
   provisional constants in `ProvisionalMicOffsets` can be replaced.

**Then, by phase:**
- *Onboarding:* fresh install → 6 or 7 steps; the mic is asked on the permissions step only; "Next" stays disabled
  until the mic is allowed; finishing starts monitoring.
- *Paused state:* Settings → Stop is OFF (not paused). To see PAUSED, start monitoring, then force-stop Voxara from its
  app info → reopen: Home shows "Monitoring paused · Resume"; within about 15 min a "Voxara stopped listening"
  notification appears (watchdog). After a reboot: a "tap to resume" notification.
- *Galaxy battery:* turn watch Power saving on → Settings shows the power-save warning. On the phone: Galaxy
  Wearable → battery Unrestricted (path from Samsung's phone documentation).
- *Alerts:* Settings → alert level e.g. 80 dBA; steady noise above it for more than 3 minutes → two rising taps and a
  "Loud around you" notification with Got it / Snooze 1 h / Open. Daily 100%: on a test watch temporarily raise the
  fine trim so a room reads about 100 dBA → the 100% alert fires even after 4 other alerts, has no Snooze and fires
  again at 200%. Set the trim back to 0 and reset today's dose (Settings → Advanced, with confirmation).
- *Rolling week:* the Week screen says "last 7 days" and no longer drops to 0 on Mondays.
- *Headphones:* pair Bluetooth earbuds to the watch and play music from the watch → Home card "≈ NN dBA · estimated";
  the Headphones screen shows range, confidence, weekly ring and time left; the estimate follows the volume.
  Settings → "My headphones are usually" → Earbuds. "Allow Bluetooth details" appears (API 31+).
  Tile "Listening"; complication "Listening this week".
- *Phone companion:* open "Voxara companion" on the phone → Turn on → listen with headphones 15 min or more → the
  watch Headphones/Week numbers grow (very-low-confidence minutes). Turning it off stops sampling.
- *Tono:* the Home status line is always there (built-in answers). Debug build with a key: Settings → Tono → Cloud
  AI On → Ask → "How is today going?" or speak a question → the source label reads "CLOUD AI". Say "I have ringing in
  my ears" → the fixed SAFETY answer (nothing is sent). Airplane mode → built-in answer plus "Cloud AI did not answer".
- *Design/accessibility:* largest font size → layouts still fit (see the 1.3× renders in `app/build/screenshots/`);
  Remove animations → gauges snap, no free-running animation.
- *Ambient:* always-on display, wrist down → grey hairline gauge, level and zone word only.

## 3. Decisions made on your behalf (please review)
- **Provisional mic offsets** 36.0 / 18.0 dB (CDD-derived) in `core/calibration/MicSource.kt` → `ProvisionalMicOffsets`.
  Nothing about the Galaxy Watch 7 mic is assumed; readings are labelled "uncalibrated estimate" until you calibrate.
  **Calibration is the first thing to verify.**
- Calibration rules: steady = std-dev at most 2 dB over at least 24 readings; reference 50–90 dBA accepted (advice
  at most 85, above 90 refused); stored offsets −60…+60 dB; fine trim ±40 dB and reset to 0 when a calibration is
  saved; a calibration made on another watch model is ignored.
- Storage: framework SQLite instead of Room (no annotation processor / KSP risk with AGP 9).
- Ambient sustained window **3 min** (needs 80% coverage); headphone "very loud" window **5 min**, default 100 dBA
  (95–105); 30-min cooldown; re-arm 5 dB below; the daily cap (4) applies to advisories only; quiet hours OFF by default.
- Headphone estimate: **100 dBA at full volume** (EN 50332 EU cap) plus the platform volume curve; ±10 dB; LOW
  confidence (VERY LOW for the linear fallback and phone samples). Passive attenuation for the double-count rule:
  earbuds 10 dB, over-ear 15 dB, wired/unknown 0 dB. Earbuds vs over-ear comes from a user setting.
- Two devices reporting the same minute: the louder one counts, never the sum.
- Tono: model `deepseek-flash` (DeepSeek docs list `deepseek-flash` and `deepseek-v4-pro`), JSON mode, 250 max tokens,
  at most 20 calls a day, automatic status at most hourly. Hearing Care Score formula in `core/ai/Coach.kt` and on
  the Week screen.
- Design: Inter (OFL) bundled; zones OK < 70 · Moderate 70–84 · Loud 85–94 · Dangerous ≥ 95 dBA; percent zones
  50/80/100. Layout targets assumed: GW7 40 mm ≈ 216 dp, 44 mm ≈ 240 dp (verify on the device).
- 100% alerts (daily, weekly, headphone) have Got it + Open and no Snooze (your decision 3, applied to all three).

## 4. Skipped, blocked or not verifiable here
- ⛔ **No physical watch was connected.** Nothing was measured on real hardware; calibration, One UI Watch battery
  behaviour, the real mic path / UNPROCESSED support, Bluetooth headphone detection, TTS/ASR and the Data Layer sync
  are unverified on a device.
- ⛔ **Emulator:** the only device (`emulator-5554`) hit repeated *startup* ANRs inside AndroidX library
  initialisation (WorkManager / lifecycle class init) at about 290% system CPU — before and after the redesign — so
  on-emulator screenshots were not possible. Layouts were verified with Robolectric/Roborazzi renders instead.
- ⛔ Watch-side per-app battery-optimisation path on One UI Watch: not in official docs; the app only offers the
  system screen when the watch resolves it.
- ⛔ Phase 7 (by plan): server-side AI proxy (release AI stays off), phone companion v2 (Companion Device Manager),
  TFLite scene model (no model file), a "current zone" complication (would show stale data; left out).
- Release signing of watch and phone apps must match for the Data Layer; signing configs were not touched.
- Pre-Phase-1 CSV history (debug data only) is not migrated.
- No git commands were used. `local.properties` was not modified in this session.

---

## Watch fixes made before companion v2 (same session) ✅
Root causes found on the emulator; all watch unit tests passed after each fix.
- `app/src/main/java/com/example/voxara/ai/CoachClient.kt` — the cloud request ran on the main thread
  (`NetworkOnMainThreadException` crashed every launch once AI consent was on). Now on `Dispatchers.IO`; any transport
  error becomes a soft failure (offline answer). DeepSeek requests send `"thinking": {"type": "disabled"}`: the model's
  hidden reasoning used the whole 250-token cap and every reply came back empty.
- `app/src/main/java/com/example/voxara/ui/VoxaraApp.kt` — screens inside the nav host kept the state they were first
  composed with, so taps worked but nothing on screen changed ("buttons do nothing"). Destinations now read the
  env through `rememberUpdatedState`.
- `app/src/main/java/com/example/voxara/ai/Tono.kt` — cache checked before the rate limit; a rate-limited automatic
  refresh keeps the last cloud reply; failure reasons logged (tag `Tono`, never the key or the question).
- `app/src/main/java/com/example/voxara/voice/VoiceTurn.kt`, `presentation/MainActivity.kt` — a tapped question
  clears the previous voice answer so Tono's reply is shown.
- Tests: `app/src/test/java/com/example/voxara/ui/NavigationStateTest.kt` (new; fails on the old wiring),
  `app/src/test/java/com/example/voxara/core/CoachTest.kt` (+2 tests).

---

## Phone companion v2 ✅
Paths are relative to the project root. `…/voxara/` = `src/main/java/com/example/voxara/` of the module named.

### Step log (W1 module move: watch built and all watch unit tests run after every step)
- **Step 1** ✅ new module `shared/` (`shared/build.gradle.kts`, `shared/src/main/AndroidManifest.xml`); `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml` (library plugin), `app/build.gradle.kts` (`implementation(project(":shared"))`). Moved unchanged (same packages, no import edits):
  - `app/src/main/java/com/example/voxara/core/dose/Accounting.kt` → `shared/src/main/java/com/example/voxara/core/dose/Accounting.kt`
  - `app/src/main/java/com/example/voxara/core/dose/NoiseDoseEngine.kt` → `shared/src/main/java/com/example/voxara/core/dose/NoiseDoseEngine.kt`
  - `app/src/main/java/com/example/voxara/core/format/Format.kt` → `shared/src/main/java/com/example/voxara/core/format/Format.kt`
  - `app/src/main/java/com/example/voxara/core/risk/RiskStateMachine.kt` → `shared/src/main/java/com/example/voxara/core/risk/RiskStateMachine.kt`
  - `app/src/main/java/com/example/voxara/core/risk/RiskZone.kt` → `shared/src/main/java/com/example/voxara/core/risk/RiskZone.kt`
  - `app/src/main/java/com/example/voxara/core/monitoring/MonitoringHealth.kt` → `shared/src/main/java/com/example/voxara/core/monitoring/MonitoringHealth.kt`
  - `app/src/main/java/com/example/voxara/core/scene/Scene.kt` → `shared/src/main/java/com/example/voxara/core/scene/Scene.kt`
  - Watch: assembleDebug ✅, 112/112 unit tests ✅
- **Step 2** ✅ moved unchanged:
  - `app/src/main/java/com/example/voxara/core/ledger/Ledger.kt` → `shared/src/main/java/com/example/voxara/core/ledger/Ledger.kt`
  - `app/src/main/java/com/example/voxara/core/ledger/LedgerBook.kt` → `shared/src/main/java/com/example/voxara/core/ledger/LedgerBook.kt`
  - `app/src/main/java/com/example/voxara/core/ledger/MinuteLedger.kt` → `shared/src/main/java/com/example/voxara/core/ledger/MinuteLedger.kt`
  - `app/src/main/java/com/example/voxara/core/headphones/Headphones.kt` → `shared/src/main/java/com/example/voxara/core/headphones/Headphones.kt`
  - `app/src/main/java/com/example/voxara/core/headphones/PhoneListening.kt` → `shared/src/main/java/com/example/voxara/core/headphones/PhoneListening.kt`
  - `app/src/main/java/com/example/voxara/core/calibration/GuidedCalibration.kt` → `shared/src/main/java/com/example/voxara/core/calibration/GuidedCalibration.kt`
  - `app/src/main/java/com/example/voxara/core/calibration/MicSource.kt` → `shared/src/main/java/com/example/voxara/core/calibration/MicSource.kt`
  - `app/src/main/java/com/example/voxara/core/design/Palette.kt` → `shared/src/main/java/com/example/voxara/core/design/Palette.kt`
  - `app/src/main/java/com/example/voxara/core/advice/AdviceGrammar.kt` → `shared/src/main/java/com/example/voxara/core/advice/AdviceGrammar.kt`
  - Watch: assembleDebug ✅, 112/112 unit tests ✅
- **Step 3** ✅ moved unchanged (`app/src/main/java/com/example/voxara/core/` is now empty and removed):
  - `app/src/main/java/com/example/voxara/core/alerts/AlertEngine.kt` → `shared/src/main/java/com/example/voxara/core/alerts/AlertEngine.kt`
  - `app/src/main/java/com/example/voxara/core/ai/Coach.kt` → `shared/src/main/java/com/example/voxara/core/ai/Coach.kt`
  - `app/src/main/java/com/example/voxara/core/ai/MiniJson.kt` → `shared/src/main/java/com/example/voxara/core/ai/MiniJson.kt`
  - Watch: assembleDebug ✅, 112/112 unit tests ✅
- **Step 4** ✅ moved unchanged:
  - `app/src/main/java/com/example/voxara/ai/CoachClient.kt` → `shared/src/main/java/com/example/voxara/ai/CoachClient.kt`
  - Tono's built-in answers: class `OfflineCoach` cut from `app/…/voxara/ai/Tono.kt` → `shared/…/voxara/ai/OfflineCoach.kt` (now takes already-localized `Resources`; the watch passes `LocaleStore.localized(context).resources`, so its output is unchanged). Its 20 `tono_*` template strings moved `app/src/main/res/values{,-es}/strings.xml` → `shared/src/main/res/values{,-es}/strings.xml` (used only by OfflineCoach).
  - Watch: assembleDebug ✅, 112/112 unit tests ✅
- **Step 5** ✅ new module `shared-ui/` (`shared-ui/build.gradle.kts`, `shared-ui/src/main/AndroidManifest.xml`, Compose UI only); `settings.gradle.kts`, `app/build.gradle.kts` (`implementation(project(":shared-ui"))`). Moved unchanged:
  - `app/src/main/java/com/example/voxara/ui/design/VoxIcons.kt` → `shared-ui/src/main/java/com/example/voxara/ui/design/VoxIcons.kt`
  - `app/src/main/res/font/inter_variable.ttf` → `shared-ui/src/main/res/font/inter_variable.ttf`
  - `app/src/main/assets/licenses/Inter-OFL.txt` → `shared-ui/src/main/assets/licenses/Inter-OFL.txt`
  - `InterFamily` cut from `app/…/voxara/ui/design/Tokens.kt` → `shared-ui/…/voxara/ui/design/SharedDesign.kt` (same package, so no import changes). Zone words `zone_ok|moderate|loud|dangerous` moved `app/src/main/res/values{,-es}/strings.xml` → `shared-ui/src/main/res/values{,-es}/strings.xml`; new shared mapping `RiskZone.wordRes()`.
  - Watch edits: `app/…/voxara/ui/design/Components.kt` (`zoneWord` uses `wordRes()`), `app/…/voxara/tile/VoxaraTileService.kt` (`zoneWordRes` uses `wordRes()`).
  - Watch: assembleDebug ✅ (font and licence still packaged once), 112/112 unit tests ✅
- **Step 6 (W2)** ✅ Tono's decisions moved into the shared module so the phone uses the same flow:
  - `CoachUi` and `ReplySource` cut from `app/…/voxara/ai/Tono.kt` → `shared/…/voxara/ai/TonoCore.kt` (same package);
    new `TonoCore` class (symptom guard, consent, cache, rate limit, fallback; same logic as the watch had, including
    this session's fixes) and `BuiltInCoach` interface (implemented by `OfflineCoach`).
  - `app/…/voxara/ai/Tono.kt` rewritten as a thin wrapper with the same public API (`ui`, `cloudAvailable`, `ask`).
  - Snapshot building moved `app/…/voxara/ai/CoachSnapshots.kt` → `shared/…/voxara/core/ai/Snapshots.kt`
    (`SnapshotBuilder`, `SnapshotInputs`); the watch file is now an adapter from `ExposureState`.
  - New test `shared/src/test/java/com/example/voxara/ai/TonoCoreTest.kt` (6 tests, fake client).
  - Watch: assembleDebug ✅, 112/112 unit tests ✅ (the existing CoachTest snapshot tests now run through the adapter).

### Shared logic (new, pure Kotlin, 33 new unit tests) ✅
- `shared/…/voxara/core/alerts/AlertPolicy.kt` — the floors: `HapticPrefs`/`HapticStrength`, snooze 15–120 min,
  `vibrates()` (warnings and limits always), `repeats()` (STRONG plays the pattern twice).
- `shared/…/voxara/core/alerts/AlertEngine.kt` (moved file, modified) — `snooze()` takes an optional duration,
  clamped to 15 min–2 h; default unchanged (1 h); LIMIT still never snoozable.
- `shared/…/voxara/core/alerts/AlertLog.kt` — `AlertRecord` + `AlertLog` interface (alert history).
- `shared/…/voxara/settings/VoxaraSettings.kt` — 14 synced fields, `normalize()` (validate + clamp), `SettingsDoc`
  (value + timestamp per field, JSON), `VoxaraSettings` (typed, defaults), `SettingsMerge` (conflict rule, pending).
- `shared/…/voxara/sync/WearContract.kt` — paths, capabilities, deep links, size limit, sync window.
- `shared/…/voxara/sync/WatchStatus.kt` — the watch's self-report (JSON).
- `shared/…/voxara/sync/LedgerCodec.kt` — bit-exact gzip binary day batches, ≤1000 records/part, digests.
- `shared/…/voxara/sync/HistorySync.kt` — `WatchSyncResponder` (watch) and `HistorySyncEngine` (phone).
- `shared/…/voxara/link/LinkState.kt` — `LinkResolver`: no watch / not installed / linked / problem / Wear API missing.
- Tests: `shared/src/test/java/com/example/voxara/settings/SettingsMergeTest.kt`,
  `…/core/alerts/AlertFloorsTest.kt`, `…/link/LinkResolverTest.kt`, `…/sync/HistorySyncTest.kt` (fake Data Layer
  loopback asserting every response ≤ 90 KB).

### Watch changes W3–W8 (each needed for a requested feature; alerts stay deterministic and offline) ✅
| Change | File | Why |
|---|---|---|
| W3 capability `voxara_watch_app` | `app/src/main/res/values/wear.xml` (new) | The phone finds the watch app (CapabilityClient) |
| W4 companion endpoint | `app/…/voxara/sync/CompanionService.kt` (new), `app/…/voxara/sync/CompanionLink.kt` (new), `app/src/main/AndroidManifest.xml` (service) | Answers history/status/delete requests, applies the phone's settings, publishes the watch's settings and status |
| W5 synced settings | `app/…/voxara/data/VoxaraStore.kt` | New keys (haptic strength, advisory haptics, snooze length, Tono on/off), a change time per synced field (only fields that actually change are stamped), `settingsDoc()`, `applySynced()` (re-normalizes every value), `clearExposure()` |
| W5 live state | `app/…/voxara/data/ExposureRepository.kt`, `app/…/voxara/service/DosimeterService.kt` | New settings in `ExposureState`; `applySettings()` so edits from the phone reach an open screen |
| W6 alert history | `app/…/voxara/data/ExposureDb.kt` | DB v2: `alerts` table (upgrade only adds it), `clearAll()`, same 35-day retention |
| W6 delivery | `app/…/voxara/service/AlertNotifier.kt` | Logs each delivered alert (a failed write never blocks delivery); haptics through `AlertPolicy` (warnings/limits always vibrate, STRONG repeats); snooze label shows the length |
| W6 actions | `app/…/voxara/service/AlertActionReceiver.kt` | Got it / Snooze recorded in the history; snooze uses the setting, clamped 15–120 min; never on LIMIT |
| W6 haptics | `app/…/voxara/haptics/HapticConductor.kt` | `fire(pattern, times)` for the STRONG preference |
| W7 Tono on/off | `app/…/voxara/ui/VoxaraUi.kt`, `ui/screens/HomeScreen.kt`, `ui/screens/AskScreen.kt`, `ui/screens/OverlayScreens.kt`, `ui/screens/TodayWeekScreens.kt`, `ui/screens/SettingsScreen.kt`, `presentation/MainActivity.kt` | Off hides Tono (status line, Ask, "Why?", weekly summary); the edge button becomes "Speak"; voice commands keep their on-device answers and symptom questions still get the fixed safe answer. New switch in watch Settings |
| W8 deep links | `app/src/main/AndroidManifest.xml`, `presentation/MainActivity.kt`, `ui/VoxaraApp.kt` | `voxara://watch/open` and `voxara://watch/calibration` for the phone's "Open on watch" buttons |
| Backup (decision 4) | `app/src/main/res/xml/data_extraction_rules.xml` (new), `app/src/main/res/xml/backup_rules.xml` (new), manifest attributes | Exposure DB and the DataStore file (calibration offsets + counters + settings) excluded from backup and device transfer; settings return from the phone |
| Strings | `app/src/main/res/values{,-es}/strings.xml` | `alert_action_snooze_min`, `tono_enabled_on/off/note`, `voice_title` |
- New watch test: `app/src/test/java/com/example/voxara/sync/WatchSettingsTest.kt` (3 tests). Watch: 115/115 ✅.
- Decisions: haptic strength and snooze length are set from the phone only (no new watch screens for them, to
  keep the watch UI unchanged); Tono on/off has a switch on both. Language changes from the phone apply the next
  time the watch app comes to the foreground (existing recreate-on-resume).
- Watch DB upgrade: `app/src/test/java/com/example/voxara/data/ExposureDbUpgradeTest.kt` (new) builds a real v1 database
  and opens it with the new code: all minutes kept, alert history added. (The emulator disconnected before the
  on-device upgrade check could run; see "Not tested on hardware".)

### Phone app (:phone) ✅
Build: `phone/build.gradle.kts` (rewritten: Compose, Material 3 1.4 + adaptive navigation suite, navigation-compose
2.9.8, wear-remote-interactions 1.2.0, DataStore, coroutines-play-services, Glance; AI BuildConfig fields from
`local.properties`, key only in debug), `gradle/libs.versions.toml` (new aliases). Manifest `phone/src/main/AndroidManifest.xml`
(rewritten: `VoxaraPhoneApp`, `MainActivity`, `PhoneListenerService`, FileProvider, `<queries>` for the pairing apps,
POST_NOTIFICATIONS, backup exclusion), `phone/src/debug/AndroidManifest.xml` (new, INTERNET debug-only, same as the watch).

Deleted: `phone/src/main/java/com/example/voxara/phone/ConsentActivity.kt` — replaced by the Compose app; its content
lives on in the setup (step 5) and the Headphones screen, with the same strings. No feature removed.

Created (`phone/src/main/java/com/example/voxara/phone/`):
- `PhoneGraph.kt` (instances + `VoxaraPhoneApp`), `MainActivity.kt`, `PhoneLocale.kt`
- `data/PhoneDb.kt` (framework SQLite: minutes + alerts, `PhoneHistoryStore`, retention prune, clear),
  `data/PhoneStore.kt` (DataStore: settings documents, status, sync record, preferences)
- `link/WatchGateway.kt` (`WatchGateway` interface + `DataLayerGateway`: NodeClient, CapabilityClient, MessageClient
  `sendRequest`, DataClient, RemoteActivityHelper; Play-services check)
- `sync/SyncCoordinator.kt` (link, status, settings both ways, history, retention, delete-all with retry),
  `sync/SyncWorker.kt` (hourly), `sync/PhoneListenerService.kt` (status/settings DataItems, capability changes)
- `tono/PhoneTono.kt` (shared `TonoCore` + privacy gate; cloud client behind `CoachClient`)
- `model/History.kt` (local days via the shared ledger functions, zone strip, rolling budget, week compare, headline,
  Tono inputs), `model/Freshness.kt`
- `export/CsvExport.kt` (two numbers-only CSVs through the share sheet)
- `ui/PhoneUi.kt` (state, actions, wording helpers), `ui/PhoneApp.kt` (NavigationSuiteScaffold: bottom bar / rail),
  `ui/PhoneViewModel.kt`, `ui/theme/PhoneTheme.kt`, `ui/components/Components.kt`, `ui/components/Charts.kt`,
  `ui/screens/HomeScreen.kt`, `ui/screens/DetailScreens.kt` (Today, Alerts), `ui/screens/TrendsScreen.kt`,
  `ui/screens/HeadphonesScreen.kt`, `ui/screens/TonoScreen.kt`, `ui/screens/WatchScreen.kt`,
  `ui/screens/SettingsScreen.kt`, `ui/screens/SetupAndInfo.kt` (setup wizard, How Voxara works)
Modified: `PhonePrefs.kt` (+ language, read synchronously). Unchanged: `ListeningSampler.kt`,
`HeadphoneConnectionReceiver.kt` (v1 sampling and its Data Layer contract).
Resources: `phone/src/main/res/values{,-es}/strings.xml` (rewritten, EN/ES, v1 strings kept), `values/wear.xml`
(capability), `values{,-night}/themes.xml`, `values{,-night}/colors.xml`, `xml/data_extraction_rules.xml`,
`xml/export_paths.xml`.
Shared additions for the phone: `shared/…/core/design/Palette.kt` (light theme + zone inks, contrast-tested in
`shared/src/test/java/com/example/voxara/core/design/LightPaletteTest.kt`), `shared-ui/…/ui/design/VoxIcons.kt`
(added icons only: Forward, Home, Watch, Phone, Refresh, Share, Trash, Bell, Link, Globe, Lock, Battery, Moon, Plus,
Minus, Clock, Vibrate).

Tests (`phone/src/test/java/com/example/voxara/phone/`): `sync/SyncCoordinatorTest.kt` (9: link states incl. 0/1/several
watches and no Wear API, sync, settings pending/conflicts/clamping, delete-all retry — fake Data Layer wired to the real
shared responder), `model/HistoryModelTest.kt` (8: freshness, days, zone strip, headline, Tono never sees a stale level,
phone/watch snapshot parity, rolling budget, CSV numbers only), `ui/PhoneScreenshotTest.kt` (12 tests, 71 images in
`phone/build/screenshots/`: phone 360 dp / foldable 673 dp / tablet 1280 dp, font 1.0 / 1.3 / 2.0, light and dark,
loading / empty / stale / problem states).
Result at that point: watch 116/116, shared 41/41, phone 31/31 ✅; both debug APKs build.

### Optional items (built after everything above passed) ✅
- **Mirrored 100% alerts** (off by default):
  - `shared/…/voxara/sync/MirroredAlert.kt` (new: message + `shouldShow` rule: enabled, LIMIT only, once per alert,
    at most 10 min old) and `shared/src/test/java/com/example/voxara/sync/MirroredAlertTest.kt` (new, 2 tests).
  - Watch change **W9**: `app/…/voxara/service/AlertNotifier.kt` and `app/…/voxara/sync/CompanionLink.kt`. After the
    haptic, the history write and the notification, a LIMIT alert is offered to a reachable phone (fire-and-forget
    MessageClient; a failure cannot delay or change the watch's alert).
  - Phone: `phone/…/voxara/phone/MirroredAlerts.kt` (new), `sync/PhoneListenerService.kt` (MESSAGE_RECEIVED), manifest
    filter, Settings switch, strings. De-duplication: one notification per alert kind and level (a repeat replaces
    it), each alert at most once, stale deliveries dropped, and the copy is `setLocalOnly(true)` so Wear OS never
    bridges it back to the watch as a second alert. (Wear OS bridges phone to watch, not watch to phone, so the
    watch's own notification never appears on the phone to collide with.)
- **Glance widget** "Today's sound": `phone/…/voxara/phone/widget/DoseWidget.kt` (new), `res/xml/dose_widget_info.xml`
  (new), manifest receiver, strings. Shows the watch's dose only when its status is from today, with "As of HH:MM" and
  the zone word; otherwise "No data today". Refreshed after each sync (worker, app, listener), never on a timer. Not
  covered by an automated test (Glance rendering needs a device).
- Build: `gradle/libs.versions.toml` and `phone/build.gradle.kts` pin Fragment 1.8.9 (Play services brings 1.1
  transitively; release lint requires 1.3+ with the Activity Result APIs).

### Final checks ✅
- `:app:assembleDebug :phone:assembleDebug :app:assembleRelease :phone:assembleRelease` ✅
- Unit tests: watch **116/116**, shared **43/43**, phone **31/31** ✅ (watch baseline before v2: 112, all still pass).
- Release APKs: the API key is absent from both (checked against local.properties without printing it) and neither
  declares INTERNET. The debug APKs contain the key (debug-only by design, as before).
- Lint: no NewApi errors in any module at minSdk (phone 28, watch 30). Pre-existing and unchanged: one WrongConstant
  error in `phone/…/ListeningSampler.kt:46` (v1 code; the type is always an output type there) and InlinedApi
  warnings in the watch's HapticConductor / MainActivity (guarded at runtime).
- Strings: every English string has a Spanish counterpart in all four modules.

### How to verify each change
1. **Module move (W1/W2)**: `./gradlew :app:testDebugUnitTest`: the same tests as before plus the new ones. Install the
   watch debug APK over the current one: history, calibration and settings are kept; Tono answers as before.
2. **Link states**: phone Watch tab. Watch off or out of range: "No watch connected" + "Open the Galaxy Wearable app".
   Watch app uninstalled: "Voxara isn't on your watch" + "Install on the watch" (release: Play Store on the watch;
   debug: adb instructions). Both installed: device card with last sync and version. Bluetooth off mid-sync:
   "Sync problem" with help, cleared by the next successful sync.
3. **Settings sync**: change the alert level on the phone: "Pending sync" until the watch applies it; watch Settings
   then shows the new value. Change it on the watch afterwards: the phone takes the watch's value. Set snooze to
   30 min: the watch notification says "Snooze 30 min"; 100% alerts still have no Snooze.
4. **Floors**: "Vibrate for advisories" off: a 50% or level advisory is silent, an 80% or 100% alert still vibrates;
   "Strong" plays each pattern twice. Quiet hours on: 100% alerts still arrive.
5. **Tono on/off**: off (on either device): the watch Home shows "Taking care of your ears", the edge button says
   "Speak", voice answers stay, a symptom question still gets the safe answer. The phone's Tono tab shows "Tono is off".
6. **Open on watch**: Watch tab, "Open Voxara on the watch" / checklist Calibration: the watch opens (the second one on
   the calibration screen). Without a phone: `adb shell am start -a android.intent.action.VIEW -d voxara://watch/calibration`.
7. **History**: after a sync, Home / Today / Trends / Alerts show the watch's minutes and alerts with "Updated … ago";
   pull to refresh. Leave the watch away for 3 h: numbers are marked "Last known".
8. **Delete all**: Settings, Delete all data, confirm. Watch reachable: both cleared. Watch away: the phone is cleared
   now and the watch on its next connection.
9. **Export**: Settings, Export CSV: share sheet with `voxara_minutes.csv` and `voxara_alerts.csv`.
10. **Backup**: rules in `app/src/main/res/xml/` (watch) and `phone/src/main/res/xml/data_extraction_rules.xml`.
11. **Optional**: turn on "Show 100% alerts on this phone", reach 100% on the watch: one phone notification, not
    bridged back. Add the "Today's sound" widget: it updates after each sync.
12. **Screens**: `phone/build/screenshots/*.png` (71 renders after `:phone:testDebugUnitTest`).

### Decisions made on your behalf
- Transport: history is **pulled** with MessageClient request/response, one UTC day and ledger per exchange,
  at most 1000 records (about 31 KB) per response; DataClient for the small state (status, settings documents);
  ChannelClient is not needed. Sync window: 33 UTC days, fully inside the watch's 35-day retention, so a partly pruned
  day never overwrites the phone's copy.
- Settings conflict rule (your decision 2): per field, newest wins; changes less than 5 s apart are ties and go to the
  watch; a phone timestamp more than 5 s in the watch's future is refused. Accepted values keep the phone's timestamp
  so "applied" can be shown exactly.
- Synced fields: the 14 in `VoxaraSettings.kt` (including the existing advisory cap and very-loud-listening level).
  Phone-side headphone sampling stays phone-only (the watch has no use for it).
- Haptic strength "Strong" = the pattern played twice, 1.6 s apart. Snooze choices 15 / 30 / 60 / 120 min (default 60).
  Haptics and snooze are set from the phone only (no new watch screens); Tono on/off has a switch on both.
- Tono off still gives the fixed symptom answer (a safety reply, not AI) on both devices.
- Delete all on the watch clears minutes, alert history, dose counters and alert memory; settings, calibration and
  onboarding stay.
- Watch backup: the whole DataStore file is excluded because it holds the calibration offsets and counters together
  with the settings; the settings come back from the phone.
- Phone retention default 90 days (choices 30 / 90 / 180 / 365). The phone app's language follows the synced setting.
- The phone shows the watch's own numbers (its status) when they are from today, otherwise ledger-derived numbers
  with the last-sync time. The phone never presents a current level: Tono on the phone gets a level only if the watch
  measured it in the last 5 minutes.
- Dynamic colour off; light and dark themes; zone text and icons use darker "inks" in the light theme (contrast-tested).

### Release: Data Layer requirements (configs NOT changed)
Both apps already share `applicationId = "com.example.voxara"`. For release builds to talk over the Data Layer they
must also be signed with the **same key**: create one signing key, add the same `signingConfigs.release` to
`app/build.gradle.kts` and `phone/build.gradle.kts` (or use Play App Signing with one app signing key for both), and
publish the watch APK as the Wear OS form factor of the same Play listing. The watch keeps
`com.google.android.wearable.standalone = true`: it works without the phone.

### Not tested on real hardware
- No phone was ever connected, and the Wear emulator disconnected before the on-device upgrade check. Untested on
  devices: phone and watch discovery (NodeClient / CapabilityClient), MessageClient requests, DataClient settings
  delivery, RemoteActivityHelper (open on watch, Play Store install), capability-change broadcasts, the share sheet,
  the Glance widget, mirrored notifications, the phone's POST_NOTIFICATIONS / BLUETOOTH_CONNECT prompts, One UI Watch
  battery behaviour, and backup exclusion on a real restore.
- Covered instead by JVM / Robolectric tests with fake Data Layers (link states, settings, sync, delete, DB upgrade)
  and the 71 rendered screenshots.
- No git commands were used. `local.properties` was read (never printed) and not modified.

---

## Situation modes and Auto detection ✅ (not yet tested on a real Galaxy Watch)

Before this, "Auto" was a stub: `Situation.resolve(choice, detected)` was always called without
`detected`, so Auto always meant Street. Auto now detects **Quiet / Street / Transport / Work** with a
small on-watch model trained on a public dataset, and **Concert** with a deterministic rule. All of
it reuses the bursts the dosimeter already captures. No git commands were used.

### Dataset and licence (full report: `C:\dataset_voxara\training\README.md`)
- **Chosen: TUT Acoustic Scenes 2017** (DCASE 2017 Task 1; dev Zenodo 400515, eval 1040168). It
  covers 15 everyday *scenes*, 10 s binaural segments, and an official evaluation split recorded at
  109 locations never used in development.
  The event datasets (UrbanSound8K, URBAN-SED, Isolated urban sound) describe what made a sound,
  not where the wearer is. USotW and the realistic mixtures are outdoor public spaces only.
- **Licence: NOT shippable as is.** The EULA shipped with every TUT 2016/2017 record allows
  "internal and non-commercial use and … scientific research" and "does not include the right to use
  the Licensed Materials … in connection with the creation of commercial products" (a separate
  agreement with Tampere University is possible). The TUT 2016 evaluation record is tagged
  "other-open" on Zenodo but ships the identical EULA. UrbanSound8K is CC BY-NC 4.0. URBAN-SED, the
  realistic mixtures, the isolated sounds and USotW are CC BY 4.0 (commercial-friendly) but cannot
  teach Transport, Work or indoor Quiet.
  → `SceneModelWeights.COMMERCIAL_USE_ALLOWED = false`, pinned by `SceneModelTest`. **Before a Play
  Store release:** license TUT, or record or buy scene data with a commercial licence and re-run the
  kit. The pipeline does not change.
- **Class → mode:** Quiet = home, library, forest path, park, lakeside beach. Street = city centre,
  residential area, **cafe/restaurant, grocery store** (public, busy, often >70 dBA, so the live
  level should lead, not the week or the day's dose). Transport = bus, tram, train, car, metro
  station. Work = office.
- **Download:** the full set is 14.3 GB. Zenodo throttles, so `fetch_tut2017.py` pulls single WAVs
  out of the remote zips with range requests: the full eval set (1620 segments) plus every other dev
  segment (2396, keeping all 248 locations and the official folds). About 8.9 GB over the wire,
  10 GB on disk in `C:\dataset_voxara\tut2017`. Free space on C: was 50 GB before and is 41 GB now.

### Features (Python `voxfeat.py` ≡ Kotlin `SceneFeatures.kt`)
54 numbers per 0.98 s of 48 kHz mono PCM (the sampler's own rate; TUT is resampled 44.1 → 48 kHz).
Non-overlapping 1024-point Hann FFT frames; 24 mel bands 50 Hz–12 kHz → spectral **shape**
(gain-invariant) and per-band **temporal std**; centroid, flatness, frame-level std and step,
positive spectral flux, and **local tonality** (flatness inside 8-bin blocks, 234 Hz–4.7 kHz; the
Concert rule reads it). No absolute level is in the vector; the level comes from the calibrated dBA
chain. Training augmentation imitates a wrist MEMS mic: one ear or the mix, high-pass 40–300 Hz,
low-pass 6–16 kHz, port resonance 2–8 kHz, ±3 dB/oct tilt, wrong gain and self-noise 12–50 dB down.
**Parity:** `SceneFeaturesParityTest` compares Kotlin against vectors generated by Python on the same
deterministic signal (whole and in 100 ms / odd / 250 ms chunks). They agree to ≤1e-7 relative.

### Model (no TFLite)
The model was selected by the official 4-fold location cross-validation on dev only; the eval split
was used once, for the final numbers.

| candidate | CV balanced accuracy (mode, 10 s) |
|---|---|
| MLP 64 hidden, 15 scenes, uniform mode prior | 0.831 |
| **MLP 32 hidden, 15 scenes, uniform mode prior (chosen)** | **0.825** |
| logistic regression, 15 scenes, uniform prior | 0.788 |
| logistic regression, 4 modes directly | 0.749 |
| decision tree depth 10 | 0.673 |

The rule is the smallest exportable model within one point of the best: **54 → 32 → 15, 2,363 float32
parameters, 2,208 multiply-adds**, generated into `SceneModelWeights.kt` (Kotlin constants,
~25 KB of source). Class probabilities are summed into modes with a uniform mode prior (Quiet has 5
classes, Work 1).
TFLite is not worth it here. A small CNN on log-mel typically gains a few points on TUT 2017 but
needs the TFLite runtime (~1 MB) plus a model file, full log-mel frames per vote, and far more CPU.
The detector's 5-minute voting and level vetoes already recover most of what the extra points would
buy.

### Metrics — official evaluation split (109 unseen locations, mode level)

| data | 1 s vote | 10 s segment | whole recording (~3 min) |
|---|---|---|---|
| clean | 66.8% (bal. 61.5%) | **72.3% (bal. 67.4%)** | 73.4% (bal. 68.9%) |
| watch-like (augmented) | 60.3% (bal. 56.2%) | 65.3% (bal. 60.7%) | 76.1% (bal. 72.2%) |

The 15-scene accuracy is 58.5% per segment (the DCASE 2017 baseline is 61.0%; the TUT 2017
dev→eval drop is well known, and here CV was 82.5% vs 67.4% on eval, both balanced).

Confusion, clean, per 10 s segment (rows = truth):

| true \ predicted | Quiet | Street | Transport | Work | recall |
|---|---|---|---|---|---|
| Quiet | 329 | 139 | 69 | 3 | 61% |
| Street | 10 | 333 | 88 | 1 | 77% |
| Transport | 11 | 63 | 460 | 6 | 85% |
| Work | 26 | 0 | 32 | 50 | 46% |
| precision | 88% | 62% | 71% | 83% | |

Per whole recording, watch-like: Quiet 24/41, Street 24/27, Transport 32/35, Work 3/6.
Full tables are in `C:\dataset_voxara\training\out\report.md`.

### Concert (no dataset has it): deterministic rule
- Concert is entered when the last 3 minutes have a **median level ≥ 90 dBA**, **≥ 35% of the votes
  music-like** (local tonality ≤ `MUSIC_TONALITY_MAX_DB`, now **−3.12 dB derived from freely
  licensed music only**; see "Concert threshold re-derived" below), and **no headphones
  playing on the watch**, and all of this holds without a break for 2 minutes.
- Entering skips the dwell, since it only adds protection. Leaving takes **20 min below 80 dBA**,
  the same rule as a manual Concert.
- **Calibration and check (SUPERSEDED, see "Concert threshold re-derived"):** the threshold was the
  0.5th percentile of loud non-music TUT scenes (vehicles, city centre), so about 0.5% of their
  votes were flagged. The worst TUT location reaches
  39% (a lakeside beach: waves and wind), but beaches and cars never sustain 90 dBA.
- **Real music (CORRECTED later: the set had two accidental duplicates, so it was 8 distinct
  files, 7 decodable; rebuilt with 13 verified clips in "Concert threshold re-derived"):** 10 live
  or concert recordings from Wikimedia Commons (CC BY, BY-SA, FAL or public
  domain; evaluation only, never trained on or copied into the repo; `music_eval_clips.py`). With
  crowd noise at 10 dB SNR plus the watch-like chain, 34–84% of votes are music-like for 8 of 9
  usable clips. **Heavily distorted rock is missed** (3–7%). A peak-share measure was also tried; the
  OR with it gained only 3–8 points, so it was not added.
- Concert is mostly decided by the 90 dBA level. A false Concert only adds protection (no advisory
  cap, stronger haptics).

### The detector (`shared/.../core/situation/AutoModeDetector.kt`, pure, injectable clock)
1. **Level vetoes:** below 38 dBA it is Quiet. Quiet is impossible above 70 dBA, Work above 80 dBA,
   and Transport below 50 dBA.
2. **Priors:** headphones playing on the watch boost Transport ×1.3, and night (22–06) halves Work.
3. **Voting:** a 5-minute soft vote (≥3 votes, mean probability ≥0.5, top spot in ≥50% of votes),
   at most one vote per 5 s.
4. **Hysteresis and dwell:** the generic `ConfidenceHysteresis` (`SceneHysteresis` now extends it)
   requires 2 evaluations in a row, then a **4-minute minimum dwell**.
5. **Doubt:** the current mode is **kept** while in doubt. After 10 min without support, or 15 min
   without any vote, it returns null, which means **Street**.
6. **Haptics:** a change of the mode in force goes through `ModeChangeHapticGate`.
7. **Safety:** the detector never sees or returns an alert setting, and a fixed choice ignores it.
   `AutoModeDetectorTest` fuzzes 3,000 random votes and checks that every resulting mode leaves the
   wearer's settings untouched or stronger (cap ≥, haptic repeats ≥, advisory switch unchanged).

### Watch integration
- **Sampler:** `AudioBurstSampler` feeds the **unweighted** frames to a `SceneFeatureExtractor`, but
  only while the choice is Auto. It does this before the in-place A-weighting. The `Burst` carries
  only the 54-number vectors (`sceneFeatures`). A continuous session keeps a partial vector across
  250 ms reads. Silenced or failed bursts drop it.
- **No disk:** PCM never leaves the sampler, and vectors are dropped right after the vote. The
  detector keeps 4 probabilities and a level per vote (≤60 votes), in memory only. Nothing is
  written to disk.
- **Service:** in `DosimeterService` each burst gives at most one vote; unmeasured bursts only run
  the staleness check. `ExposureState.detectedMode` is new (null = not sure / fixed choice), and
  `mode = Situation.resolve(choice, detected)` everywhere: the service, `ExposureRepository`
  (`applySettings`/`hydrate`) and `MainActivity.selectSituation`. An Auto-detected Concert applies
  Concert's alert behaviour in the same iteration.
- **Concert sampling:** continuous capture and the 6 h / 20 min housekeeping stay tied to a
  **chosen** Concert. An Auto Concert does not open the mic more often.
- **Cadence:** the classifier runs at the ladder's own pace and adds no microphone time. That is
  **1 vote/min idle (<65 dBA), 2/min AMBIENT, 6/min ACCRUING (2 s burst / 10 s), ≤12/min
  continuous**, enough for a 5-minute vote and slow enough to be free. Situations change over
  minutes.
- **Cost:** on a desktop JVM, features take **0.66 ms per vote** and inference 0.011 ms. Assuming a
  10–20× slower Cortex-A55 core on ART, that is roughly **7–15 ms of CPU per vote**. That is about
  0.02% of one core when idle and ≤0.3% when continuous (an estimate, not measured on the watch;
  the debug detection log below records the real `feature_ms` and `inference_ms` per vote).
- **UI:**
  - The chip shows "Mode: Auto · Street" / "Modo: Auto · Calle" with the detected mode (Now,
    Settings, and the phone's Watch tab via `WatchStatus.situationMode`).
  - ModeScreen adds "Detected from the sound around you: Transport." / "Detectado por el sonido a tu
    alrededor: Transporte." or "Still listening: Street until Voxara is sure." / "Escuchando: Calle
    hasta que Voxara esté seguro."
  - Now shows its own note for an Auto Concert (it does not measure continuously).
  - `situation_auto_blurb` was rewritten in EN and ES.
  - New strings: `mode_auto_detected`, `mode_auto_listening`, `mode_auto_concert_note` (EN + ES).

### Files
Created (`shared/` is untracked):
- `shared/…/core/scene/SceneFeatures.kt`
- `shared/…/core/scene/SceneModel.kt`
- `shared/…/core/scene/SceneModelWeights.kt` (GENERATED)
- `shared/…/core/situation/AutoModeDetector.kt`
- Tests in `shared/src/test/java/com/example/voxara/core/`:
  - `scene/SceneFeaturesParityTest.kt`
  - `scene/SceneModelTest.kt`
  - `scene/SceneFeaturesReference.kt` (GENERATED)
  - `scene/SceneModelReference.kt` (GENERATED)
  - `situation/AutoModeDetectorTest.kt`

Modified:
- `shared/…/core/scene/Scene.kt` (`ConfidenceHysteresis`)
- `shared-ui/src/main/res/values{,-es}/strings.xml`
- `app/…/audio/AudioBurstSampler.kt`
- `app/…/service/DosimeterService.kt`
- `app/…/data/ExposureRepository.kt`
- `app/…/presentation/MainActivity.kt`
- `app/…/ui/screens/ModeScreen.kt`
- `app/…/ui/screens/NowScreen.kt`
- `app/src/main/res/values{,-es}/strings.xml`
- `PROGRESS.md`

Outside the repo, `C:\dataset_voxara`:
- `venv/`
- `training/` (README, requirements.txt, fetch, voxfeat, extract, train, export_kotlin,
  make_reference, music_eval, music_eval_clips, `out/`)
- `tut2017/`, `features/`, `music_eval/`, `meta/`

Build note: the first `:app:assembleDebug` failed in dexing because Gradle's execution history still
held the project's old OneDrive path. I cleared the regenerable, git-ignored
`.gradle/9.5.0/executionHistory`, and the build passed.

### Retrain / regenerate
`cd C:\dataset_voxara\training`, then `..\venv\Scripts\python extract.py` and
`..\venv\Scripts\python train.py`. These regenerate `SceneModelWeights.kt` (including the music
threshold) and both test references. Then run `./gradlew :shared:testDebugUnitTest`. If you change
a feature, change `voxfeat.py` and `SceneFeatures.kt` together.

### Results
`:shared:testDebugUnitTest` **89/89**, `:app:testDebugUnitTest` **121/121**,
`:phone:testDebugUnitTest` **31/31**; `:app:assembleDebug` and `:phone:assembleDebug` ✅.

### Limitations (honest)
- **Licensing:** the weights must not go to the Play Store (see Licence). Release builds keep the
  model off (see "Scene model licence gate" below) and, since "Scene model weights out of release",
  do not contain the weights at all.
- **Accuracy:** about 72% mode accuracy per 10 s on unseen locations. Quiet is often taken for Street
  (61% recall), and Work is weak (46%, and eval has only 6 office recordings). The detector's
  voting, dwell and level vetoes make a wrong mode rare and slow, not impossible.
- **Domain:** all recordings are from Finland, with an in-ear binaural microphone and no wrist,
  sleeve or pocket. The augmentation is only a guess at the Galaxy Watch microphone path
  (UNPROCESSED vs VOICE_RECOGNITION).
- **Concert:** validated only on 10 internet recordings with simulated crowd noise, not at a real
  venue. Distorted rock is not recognised as music. It relies on calibrated levels, and the watch
  is uncalibrated by default (±10 dB).
- **No persistence:** the detection is not persisted. After a restart, Auto is Street until about
  3–5 minutes of agreeing votes.
- Half of the dev segments were used (all locations). `--all-dev` doubles them.

### How to validate on the Galaxy Watch 7
1. **Calibrate.** Install the debug build and calibrate (guided calibration) so the level vetoes and
   the 90 dBA gate are meaningful.
2. **Log the decisions.** The debug detection log (below) records each vote. Clear it, then do a
   scripted day, ≥15 minutes each:
   home → walk on a street → bus/metro → office → café. Compare against a handwritten timeline:
   time to first correct detection, share of time correct, number of mode changes.
3. **Concert.** Go to a real concert or club, or play loud music at ≥90 dBA from a PA, with the
   watch on the wrist. Check entry within about 3–5 minutes and exit 20 minutes after leaving. Then
   ride a loud metro (≥85 dBA) and confirm it does **not** enter Concert.
4. **Battery.** Compare 8 h with Auto against 8 h with a fixed mode, same routine (the ladder is
   identical, so the difference should be within noise). Also measure CPU time per burst with a
   Perfetto trace.
5. **Haptics.** Count MODE_CHANGE haptics over a day (the gate allows ≤6, ≥30 min apart, none in
   the first 5 min).
6. **Use the data.** Use the logs, numbers only, to re-tune the vetoes and thresholds, and to decide
   whether to collect your own commercially usable training data.

---

## Scene model licence gate + debug detection log ✅ (no watch connected: on-device timing still pending)

### 1. Licence gate
- **BuildConfig `SCENE_MODEL_ENABLED`** (`app/build.gradle.kts`): debug = `true`. Release = `false`
  unless `local.properties` has `SCENE_MODEL_ENABLED=true` (read with the existing `localProp`).
  Verified: the generated release `BuildConfig` has `false` and debug has `true`.
- **When false:** `DosimeterService` builds `AutoModeDetector(Config(sceneModel = false))`. Features
  are still computed, but the scene model is never called. Auto stays Street (`detected` null).
  **The Concert rule still runs**: it uses only the level, the local tonality and the headphone
  state, none of the licensed data. Even the "below 38 dBA → Quiet" veto is model-path only.
- **ModeScreen** under Auto, without a detection, says "Automatic detection isn't available in this
  version, so Auto uses Street. It still switches to Concert for loud live music." / "La detección
  automática no está disponible en esta versión, así que Auto usa Calle. Sí cambia a Concierto con
  música en directo muy alta." (`mode_auto_unavailable`, EN + ES).
- **Two checks on licensed use:**
  - `app/src/test/java/com/example/voxara/SceneModelLicenceGateTest.kt` fails the unit-test check
    when the release value (passed from Gradle as system properties) is `true` while
    `SceneModel.commercialUseAllowed` is false, unless `ALLOW_NONCOMMERCIAL_MODEL=true`. It also
    checks that debug always enables the model, and that the properties actually reach the test.
  - The Gradle task `:app:checkSceneModelLicence` (runs before `preReleaseBuild`, configuration-cache
    safe) reads `COMMERCIAL_USE_ALLOWED` from the generated `SceneModelWeights.kt`. It **stops
    `assembleRelease`** under the same condition, so a release build cannot skip the check by not
    running unit tests. I confirmed it is wired into the release graph and passes with the defaults.
    I did not force the failing case, since that means editing your `local.properties` (it holds the
    API key).
- **Shared:** `AutoModeDetector.Config.sceneModel`; Concert-only path; new tests in
  `AutoModeDetectorTest`:
  - with the model off, an hour of strong votes stays Street and Concert is still detected;
  - with the model off, the vote is built without calling the model.

### 2. Debug-only detection log
- **Twins:** `app/src/debug/.../debug/DetectionLog.kt` (real) and `app/src/release/.../debug/DetectionLog.kt`
  (no-op, same API), like `DebugTools`/`Bench`. The record type `DetectionTrace` lives in main and
  holds numbers and labels only.
- **One CSV row per classification:**
  - `epoch_ms`, `dba`;
  - `feature_ms` (CPU time inside the feature extractor for that vote's vectors, measured in
    `AudioBurstSampler`, `vectors` = how many), `inference_ms` (scene model);
  - top-3 scene classes with probabilities;
  - the vote over Quiet/Street/Transport/Work after vetoes and priors;
  - `music_like`, `tonality_db`;
  - `vetoes` (`ONLY_QUIET`, `NO_QUIET`, `NO_WORK`, `NO_TRANSPORT`, `HEADPHONE_PRIOR`, `NIGHT_PRIOR`,
    `ALL_RULED_OUT`);
  - `detected`, `mode` (in force after the decision), `confidence`, `headphones`, `scene_model`.
  - Never audio, never the feature vector. `AutoModeDetector.Decision` now carries the vote after
    vetoes and the vetoes applied; `Vote` carries the class probabilities and the tonality.
- **Storage:** written on a dedicated thread to `filesDir/detection_log/` with two rotating files
  of ~1 MB (~2 MB in all).
- **Settings → Debug tools** (debug builds only) shows the row count and size, plus **"Export
  detection log"** and "Clear detection log" (EN + ES strings in `src/debug/res`). Export merges
  both files into `Android/data/com.example.voxara/files/voxara_detection_log.csv`. The screen
  shows the path; on the computer, run `adb pull <that path>`. Fallback:
  `adb exec-out run-as com.example.voxara cat files/detection_log/detection.csv`.
- **Measuring the real cost on the watch:** install debug, set Auto, let it run 30+ min across the
  ladder states, export, then take the median and p95 of `feature_ms / vectors` and `inference_ms`.
  Those numbers replace the 7–15 ms estimate above. **Not done yet: no watch or emulator was
  connected** (`adb devices` was empty).

### Files
New:
- `app/src/main/java/com/example/voxara/debug/DetectionTrace.kt`
- `app/src/debug/java/com/example/voxara/debug/DetectionLog.kt`
- `app/src/release/java/com/example/voxara/debug/DetectionLog.kt`
- `app/src/test/java/com/example/voxara/SceneModelLicenceGateTest.kt`

Modified:
- `app/build.gradle.kts`
- `app/…/audio/AudioBurstSampler.kt` (`sceneFeatureNanos`)
- `app/…/service/DosimeterService.kt` (gate, timing, log)
- `app/…/ui/screens/ModeScreen.kt`
- `app/…/ui/screens/SettingsScreen.kt`
- `app/src/main/res/values{,-es}/strings.xml`
- `app/src/debug/res/values{,-es}/strings.xml`
- `shared/…/core/situation/AutoModeDetector.kt`
- `shared/…/core/scene/SceneModel.kt` (`modesOf`, `classes`)
- `shared/src/test/…/core/situation/AutoModeDetectorTest.kt`

### Results
`:shared:testDebugUnitTest` **92/92**, `:app:testDebugUnitTest` **124/124**,
`:phone:testDebugUnitTest` **31/31**, `:app:assembleDebug` ✅, `:phone:assembleDebug` ✅,
`:app:assembleRelease` ✅ (scene model off). AGP 9 here has no `testReleaseUnitTest` task, so the
release side of the gate is covered by the Gradle task and the system-property test. No git
commands were used.

---

## Scene model weights out of release APKs ✅

### Where the weights live, and why there
In **`:shared`'s own variant source sets**, not in `:app`:
- `:shared` is an Android library. `:app` debug consumes its debug variant and `:app` release
  consumes its release variant, so `shared/src/debug` vs `shared/src/release` gives exactly
  "debug has weights, release has none". No Gradle wiring is needed, and `:phone` gets the same
  split automatically.
- `SceneModel`, `AutoModeDetector` and their JVM tests (parity, model reference, detector) all live
  in `:shared` and run as `:shared:testDebugUnitTest`, which sees the debug weights. Moving the
  weights to `:app` would mean injecting them into `:shared` from the app and moving or duplicating
  those tests.

The layout follows the Bench / DebugTools twin pattern:

| file | what it is |
|---|---|
| `shared/src/main/.../core/scene/SceneWeights.kt` (new) | interface with the weights' shape (`FEATURE_VERSION`, `TRAINING_DATA`, `COMMERCIAL_USE_ALLOWED`, `CLASSES`, `CLASS_MODE`, `MODE_WEIGHT`, `MEAN`, `SCALE`, `SIZES`, `WEIGHTS`, `BIASES`) |
| `shared/src/debug/.../core/scene/SceneModelWeights.kt` (GENERATED, moved from `src/main`) | the real weights, `internal object SceneModelWeights : SceneWeights` |
| `shared/src/debug/.../core/scene/SceneModelSource.kt` (GENERATED) | debug twin: `weights = SceneModelWeights`, `COMMERCIAL_USE_ALLOWED`, `MUSIC_TONALITY_MAX_DB` |
| `shared/src/release/.../core/scene/SceneModelSource.kt` (GENERATED) | release twin, same API: `weights = null`, `COMMERCIAL_USE_ALLOWED = false`, `MUSIC_TONALITY_MAX_DB` |

- **No weights class in release.** The release twin has a different body rather than an empty
  `SceneModelWeights`, so the weights class does not exist in release at all.
- **`SceneModel`** reads `SceneModelSource.weights`:
  - `available` is false without weights, and the model refuses to run (`checkNotNull`);
  - `classes` is empty, `trainingData` is "none (no scene model in this build)",
    `commercialUseAllowed` is false, `multiplyAdds` is 0.
- **`AutoModeDetector`** runs the model only when `Config.sceneModel && SceneModel.available`.
  Otherwise it runs the Concert rule only (Auto = Street).
- **ModeScreen** shows the "not available in this version" line when the flag is off OR the model
  is absent.
- **The Concert threshold** `MUSIC_TONALITY_MAX_DB` (−3.19 dB) is in both twins, because the Concert
  rule runs in release. It is one summary number calibrated on TUT recordings (0.5th percentile of
  loud non-music votes); see Limitations if even that should be re-derived before a commercial
  release.
- **Internal-testing opt-in** (unchanged meaning): with `SCENE_MODEL_ENABLED=true` and
  `ALLOW_NONCOMMERCIAL_MODEL=true` in `local.properties`, `shared/build.gradle.kts` builds release
  from `src/debug/java`, so the weights are included on purpose. `:app:checkSceneModelLicence` now
  reads the licence flag from the new debug path.

### Checks that release carries no weight data
1. **`:app:verifyReleaseHasNoSceneWeights`** (Gradle, finalizes `assembleRelease`). It opens every
   `classes*.dex` of the release APK and fails if it finds the descriptor
   `Lcom/example/voxara/core/scene/SceneModelWeights;` or the training-data marker `Zenodo 400515`.
   It is skipped only under the internal-testing opt-in (with a warning).
   Output on this build: "app-release-unsigned.apk: 4 dex files, no scene model weights".
2. **`shared/src/test/.../core/scene/SceneWeightsLayoutTest.kt`** (4 tests):
   - `SceneModelWeights.kt` exists only in `src/debug`, and no file in `src/main` or `src/release`
     defines or references the weights object;
   - the release twin is `weights = null`, `COMMERCIAL_USE_ALLOWED = false`, with no arrays and no
     dataset marker;
   - both twins expose the same members;
   - the debug test variant has the model.
3. **Manual inspection** (SDK `cmdline-tools/latest/apkanalyzer dex packages --defined-only` and
   `build-tools/36.0.0/dexdump` on the unzipped dex):
   - **Release APK:** under `core.scene` only `SceneModel`, `SceneWeights` (interface) and
     `SceneModelSource` (3 fields: `weights`, `COMMERCIAL_USE_ALLOWED`, `MUSIC_TONALITY_MAX_DB`), plus
     the feature, hysteresis and scene classes. **No `SceneModelWeights`.**
   - **Debug APK** (positive control): contains `SceneModelWeights`, with a 19,304-byte `<clinit>`,
     i.e. the weight arrays.

### Training kit (`C:\dataset_voxara\training`)
- `export_kotlin.py` writes the weights to `shared/src/debug/.../SceneModelWeights.kt`
  (`override val`s implementing `SceneWeights`). It writes both `SceneModelSource` twins
  (`write_sources`) and deletes any stale `shared/src/main/.../SceneModelWeights.kt`. The current
  weights were regenerated this way from `out/model.npz`, with identical numbers.
- `train.py` docstring and `README.md` updated with the new paths.

### Files
New:
- `shared/src/main/.../core/scene/SceneWeights.kt`
- `shared/src/debug/.../core/scene/SceneModelSource.kt`
- `shared/src/release/.../core/scene/SceneModelSource.kt`
- `shared/src/test/.../core/scene/SceneWeightsLayoutTest.kt`

Moved and regenerated: `SceneModelWeights.kt`, from `shared/src/main` to `shared/src/debug`.

Modified:
- `shared/.../core/scene/SceneModel.kt`
- `shared/.../core/situation/AutoModeDetector.kt`
- `shared/build.gradle.kts` (opt-in source swap)
- `app/build.gradle.kts` (new path, `verifyReleaseHasNoSceneWeights`)
- `app/.../ui/screens/ModeScreen.kt`
- `C:\dataset_voxara\training\export_kotlin.py`, `train.py`, `README.md`

### Results
`:shared:testDebugUnitTest` **96/96**, `:app:testDebugUnitTest` **124/124**,
`:phone:testDebugUnitTest` **31/31**, `:app:assembleDebug` ✅, `:phone:assembleDebug` ✅,
`:app:assembleRelease` ✅ with `verifyReleaseHasNoSceneWeights` passing. No git commands were used.

### Limitations
- `MUSIC_TONALITY_MAX_DB` (one number) is still derived from TUT and ships in release with the
  Concert rule. If legal review treats even a summary statistic as a derived work, re-derive it from
  commercially usable recordings (`music_eval_clips.py` already runs on CC/PD music) and regenerate.
- The APK check matches a class descriptor and a dataset marker. A future weights object under
  another name would need adding to `markers` in `app/build.gradle.kts`; `SceneWeightsLayoutTest`
  guards the source side.
- `:phone` also depends on `:shared`. Its release variant gets the weightless twin the same way, but
  only the watch APK is scanned.

---

## Concert threshold re-derived from freely licensed music + :phone release scan ✅

### 1. The Concert threshold no longer comes from TUT
`MUSIC_TONALITY_MAX_DB` ships in release, because the Concert rule runs without the scene model.
It was the 0.5th percentile of loud TUT scenes, a value derived from non-commercial data. It is now
derived **only from freely licensed live music**:
- **Music set rebuilt.** The earlier set had two byte-identical duplicates, caused by a bug in my
  previous download loop that wrote stale data after HTTP 429 errors: `PopMix.ogg` was the Marine
  Band file, and `Sebkha_Chott….ogg` was Reggaetomix. So it held 8 distinct files, not 10. The new
  `fetch_music.py`:
  - records title, author, licence + licence URL, Commons page, SHA-256, length and whether the
    file is partial;
  - rejects duplicates and files that don't decode.
  Result: **13 clips** (CC BY 3.0, CC BY-SA 3.0/4.0, Free Art License, public domain). One public-
  domain file ("Festival concert people crowd") was rejected because it does not decode. Each
  clip's licence and source is listed in `C:\dataset_voxara\training\README.md` (table) and
  `C:\dataset_voxara\music_eval\manifest.json`.
- **Derivation** (`derive_concert_threshold.py`, music only):
  - every clip → the watch-like mic chain + crowd-like noise at 10 dB SNR;
  - take each clip's **median** vote tonality;
  - threshold = **median over clips** → **−3.12 dB** (old TUT value −3.19 dB).
  - Leaving each clip out moves it only between −3.05 and −3.13 dB.
- **Evaluation:** the full rule is simulated: a vote every 10 s, 3-min window, ≥ 6 votes, ≥ 35%
  music-like, held 2 min, recordings looped to 6 min, level gate assumed passed (worst case).

| | old −3.19 dB (TUT) | **new −3.12 dB (music only)** |
|---|---|---|
| clips → Concert, crowd at 20 dB SNR | 7/13 | **8/13** |
| clips → Concert, crowd at 10 dB SNR (reference) | 6/13 | **8/13** (leave-one-out: 8/13) |
| clips → Concert, crowd at 3 dB SNR | 2/13 | 2/13 |
| **loud non-music → false Concert** (142 TUT recordings: metro station, train, tram, bus, car, city centre; dev + eval; clean + watch-like = 284 runs) | 0 | **0** |
| share of those votes music-like | 0.76% | 0.97% |
| worst recording (needs 35% to trigger) | 21.2% (car) | 24.7% (car), then train 16.7%, tram 8.5%; every metro station below 8.5% |

- **Decision:** the new value is **better, not worse**. It catches 2 more clips at the reference
  condition (Sebkha-Chott "Café La Main Verte" and "Illusions") and still gives no false Concert on
  any loud transport recording; **the loud-metro case does not trigger**. So it replaces the old
  value. TUT was used only to check, never to set it.
- **Never detected, at any threshold:** heavily distorted rock (The Armed, 3 clips: 0–4%
  music-like), the short, noisy bar recording (Tercer Tipo, 14%) and the brass march (39% overall
  but never held for 2 minutes).
- **Release source is free of TUT values:**
  - `shared/src/release/…/SceneModelSource.kt` now holds only `weights = null`,
    `COMMERCIAL_USE_ALLOWED = false` and `MUSIC_TONALITY_MAX_DB = -3.12`, with a comment stating the
    music-only derivation. `grep` finds no TUT / Zenodo / −3.19 in `shared/src/main` or
    `shared/src/release`.
  - The detector KDoc was rewritten.
  - The new `SceneWeightsLayoutTest` case fails if the release twin mentions TUT, Acoustic Scenes or
    DCASE, lacks the music-derivation note, or differs from the debug twin's threshold.
- **Pipeline:**
  - `train.py` now takes the shipped threshold from `out/concert_threshold.json` and refuses to
    export without it. Its TUT percentile stays in the report as "reference, not shipped".
  - `export_kotlin.py` requires the value and writes its provenance into both twins.
  - `derive_concert_threshold.py --write-sources` regenerates the twins without retraining.
- **One design constant to note:** the rule's vote share **0.35** (and the other rule constants:
  90 dBA, 3 min, 2 min) are hand-set design parameters, not fitted statistics. When 0.35 was chosen,
  I looked at both the music results and TUT false-positive margins. Today's check shows the music
  set alone supports it: caught clips sit at 40–84% music-like at 10 dB, and distorted rock near 0%.

### 2. :phone release APK scanned too
- The dex scan moved into one script plugin, `gradle/scene-weights-check.gradle.kts`, applied by
  both `app/build.gradle.kts` and `phone/build.gradle.kts`. The inline copy in the app was removed.
- Each module gets `verifyReleaseHasNoSceneWeights`, finalizing its own `assembleRelease`. The
  markers and the internal-testing opt-in skip are the same as before.
- Results: ":phone phone-release-unsigned.apk: 3 dex files, no scene model weights" and
  ":app app-release-unsigned.apk: 4 dex files, no scene model weights".
- **Positive control** (same byte scan in Python): **both debug APKs contain the markers**
  (`classes4.dex`: `SceneModelWeights` and the dataset marker); **both release APKs are clean**.

### Files
- **New:** `gradle/scene-weights-check.gradle.kts`; `C:\dataset_voxara\training\fetch_music.py`,
  `derive_concert_threshold.py`, `out/concert_threshold.json`;
  `C:\dataset_voxara\music_eval\manifest.json` (clips re-downloaded).
- **Modified:**
  - `app/build.gradle.kts`, `phone/build.gradle.kts`
  - `shared/src/{debug,release}/…/SceneModelSource.kt` (regenerated, −3.12)
  - `shared/…/core/situation/AutoModeDetector.kt` (KDoc)
  - `shared/src/test/…/core/scene/SceneWeightsLayoutTest.kt` (+1 test)
  - `C:\dataset_voxara\training\train.py`, `export_kotlin.py`, `README.md`
  - this file (earlier Concert section corrected)

### Results
- Unit tests: `:shared:testDebugUnitTest` **97/97**, `:app:testDebugUnitTest` **124/124**,
  `:phone:testDebugUnitTest` **31/31**.
- Builds: `:app:assembleDebug` ✅, `:phone:assembleDebug` ✅, `:app:assembleRelease` ✅,
  `:phone:assembleRelease` ✅, both release scans pass.
- No git commands were used.

### Limitations
- 13 clips is a small set, and 3 of them come from one distorted-rock band. With crowd noise at
  3 dB SNR only 2/13 reach Concert, so in a very loud, chatty crowd the rule depends on the music
  being clearly louder than the crowd.
- The metro check uses TUT recordings (Finland, binaural, uncalibrated) with the level gate assumed
  passed. A real loud metro on the watch is still untested.
