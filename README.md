<p align="center">
  <img src="docs/design/icons/store/play-icon-512.png" width="112" alt="Voxara icon">
</p>

<h1 align="center">Voxara</h1>

<p align="center">
  <b>A hearing-protection dosimeter for your wrist.</b><br>
  Wear OS app + Android phone companion, built with Kotlin and Jetpack Compose.
</p>

<p align="center">
  <img src="docs/screenshots/watch/now.png" width="200" alt="Watch, Now page">
  &nbsp;
  <img src="docs/screenshots/watch/dose-week.png" width="200" alt="Watch, Dose page (week)">
  &nbsp;
  <img src="docs/screenshots/watch/headphones.png" width="200" alt="Watch, headphones mode">
</p>

---

Voxara measures the sound level around you on the watch (in **dBA**), adds it up into a daily and a rolling-weekly **noise dose** based on NIOSH, estimates how loud your **headphones** are, and warns you *before* you reach the limit. One glance answers one question: *are my ears safe right now, and how much of today's allowance is left?*

A small AI coach, **Tono**, explains your status in plain language. Alerts and numbers never depend on the AI: they are computed on the watch, offline and deterministic. Tono only puts them into words.

> [!IMPORTANT]
> Voxara is **not a medical device**. Until you calibrate it against a reference meter, the microphone reading is an **uncalibrated estimate**. The app always says so on screen.

## Contents

- [Features](#features)
- [Screenshots](#screenshots)
- [How it works](#how-it-works)
- [Project structure](#project-structure)
- [Tech stack](#tech-stack)
- [Getting started](#getting-started)
- [Running the apps](#running-the-apps)
- [Testing](#testing)
- [Design](#design)
- [Documentation](#documentation)
- [Privacy](#privacy)

## Features

### On the watch (Wear OS)

- **Live level**: the sound around you in dBA, on a 250° ring with four zones: OK (< 70), Moderate (70–84), Loud (85–94) and Dangerous (≥ 95). The zone is always shown as a word and an icon, never by colour alone.
- **Daily and weekly dose**: one progress ring for the share of today's limit or of the rolling last 7 days (NIOSH, 85 dBA for 8 h, 3 dB exchange rate). Calm haptic alerts at 50 %, 80 % and 100 %.
- **Headphones mode**: switches on by itself when headphones are connected to the watch or the phone. It shows the estimated listening level, warns above a safe volume and offers **Lower volume**. It never lowers thresholds and never stops measuring the room.
- **Situation modes**: Auto, Quiet, Street, Transport, Work and Concert. Auto detects the scene with a small on-watch model, and concerts with a deterministic rule.
- **Tono**: a short coaching line and a conversation screen (voice or text) that explains your numbers.
- **Guided calibration** against a sound level meter or a phone app, saved per microphone path and device model.
- **Always-on**: ambient mode, a foreground monitoring service, tiles and watch-face complications (dose and listening).
- **Accessible**: works on round screens from 192 to 240 dp, at font scale 1.3 and 2.0, with TalkBack labels and reduced motion.

### On the phone (Android)

- **Home**: today's ring, Tono's sentence, the last alert and quick tiles for the last 7 days and headphones.
- **Trends**: day, week and month charts, a heat calendar, the rolling weekly allowance and a hearing-care score.
- **Watch**: link status, a setup checklist, battery tips, calibration help and troubleshooting.
- **Headphones**: optional detection of the phone's own audio output, with your consent (off by default).
- **Settings** that sync with the watch, in light or dark theme with several accents, in English or Spanish.
- **CSV export**, delete-all-data and a home-screen **widget**.

## Screenshots

### Wear OS

Taken on the `Wear_OS_XL_Round` emulator (480 × 480 px, 240 dp). The level is the emulator's real microphone input.

| Now | Now (loud) | Dose: today | Dose: week | More |
|:---:|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/watch/now.png" width="150"> | <img src="docs/screenshots/watch/now-loud.png" width="150"> | <img src="docs/screenshots/watch/dose-today.png" width="150"> | <img src="docs/screenshots/watch/dose-week.png" width="150"> | <img src="docs/screenshots/watch/more.png" width="150"> |

| Headphones mode | Volume alert | Tono | Mode | Settings |
|:---:|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/watch/headphones.png" width="150"> | <img src="docs/screenshots/watch/volume-alert.png" width="150"> | <img src="docs/screenshots/watch/tono.png" width="150"> | <img src="docs/screenshots/watch/mode.png" width="150"> | <img src="docs/screenshots/watch/settings.png" width="150"> |

### Android phone

The phone's screens, rendered from the real Compose code with sample data by `PhoneRenderMatrixTest` (Roborazzi, phone size 360 × 800 dp):

| Home | Today | Trends | Tono |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/phone/home.png" width="190"> | <img src="docs/screenshots/phone/today.png" width="190"> | <img src="docs/screenshots/phone/trends.png" width="190"> | <img src="docs/screenshots/phone/tono.png" width="190"> |

| Headphones | Watch | Home (dark) | Trends (dark) |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/phone/headphones.png" width="190"> | <img src="docs/screenshots/phone/watch.png" width="190"> | <img src="docs/screenshots/phone/home-dark.png" width="190"> | <img src="docs/screenshots/phone/trends-dark.png" width="190"> |

Taken on an Android 15 emulator (1080 × 2400 px): the first-run setup and Settings, and the Watch tab when no watch is linked (the emulator has no Wear OS connection).

| Welcome | Privacy | Tono and cloud AI | Settings | No watch linked |
|:---:|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/phone/onboarding-welcome.png" width="150"> | <img src="docs/screenshots/phone/onboarding-privacy.png" width="150"> | <img src="docs/screenshots/phone/onboarding-tono.png" width="150"> | <img src="docs/screenshots/phone/settings.png" width="150"> | <img src="docs/screenshots/phone/watch-not-linked.png" width="150"> |

## How it works

```
 ┌─────────────────────────── Watch (:app) ───────────────────────────┐
 │ Microphone bursts ─► dBA level ─► NoiseDoseEngine ─► Ledger (35 d) │
 │                         │                │                         │
 │                    Situation /      AlertEngine ─► haptics,        │
 │                    scene model           │          notifications  │
 │                                     Tono (words only)              │
 └──────────────────────────────┬─────────────────────────────────────┘
                                │ Wearable Data Layer (status, ledger,
                                │ settings, headphone spans, commands)
 ┌──────────────────────────────▼─────────────────────────────────────┐
 │ Phone (:phone): history, trends, settings, headphone detection,    │
 │ Tono, widget, CSV export                                           │
 └────────────────────────────────────────────────────────────────────┘
```

- **Measurement**: `DosimeterService` (a foreground service of type microphone) samples short audio bursts, turns them into dBA with the calibrated offset of the active microphone path, and keeps going in ambient mode.
- **Dose**: `NoiseDoseEngine` (in `:shared`) gives the NIOSH permitted time at each level; minutes go into a ledger that feeds today's dose and the rolling 7-day dose.
- **Alerts**: `AlertEngine` decides deterministically and locally. Modes and headphones mode can never weaken an alert; tests such as `ModesNeverWeakenAlertsTest` and `AlertFloorsTest` guard this.
- **Headphones**: one `HeadphoneWatcher` reacts to audio-output events on both devices. The phone sends idempotent listening spans to the watch through a persisted outbox, so nothing is counted twice.
- **Tono**: an OpenAI-compatible chat client (DeepSeek by default) that receives a snapshot of the numbers and only writes the sentence. Cloud AI is enabled in **debug builds only**.

## Project structure

| Module | What it is |
|---|---|
| [`app/`](app) | The **Wear OS** app: dosimeter service, glance pages (Now, Dose, More), tiles, complications, Tono, calibration. `minSdk 30`. |
| [`phone/`](phone) | The **phone companion**: Home, Trends, Watch, Settings, headphones, widget, export. Same `applicationId` and signing key as the watch app. `minSdk 28`. |
| [`shared/`](shared) | Pure Kotlin logic used by both apps: dose, ledger, alerts, risk, calibration, situation detection, headphones, settings sync, link protocol. |
| [`shared-ui/`](shared-ui) | Design pieces both apps share: fonts, level colours, wave visuals, rolling digits. |
| [`baselineprofile/`](baselineprofile) | Baseline Profile generator and macrobenchmarks for the phone. |
| [`docs/`](docs) | Design system, redesign records, reliability audit and test plan, screenshots. |

## Tech stack

- **Kotlin** 2.2, **Android Gradle Plugin** 9.3, compile/target SDK 37
- **Jetpack Compose**: Wear Compose Material 3 on the watch, Compose Material 3 on the phone
- **Wear OS**: Tiles and ProtoLayout, watch-face complications, Ongoing Activity
- **Play Services Wearable** (Data Layer) for watch ↔ phone sync
- **DataStore**, **WorkManager**, **Kotlin Coroutines**, **Navigation Compose**, **Glance** (widget)
- **Haze** for the phone's translucent bars
- **Testing**: JUnit 4, Robolectric, **Roborazzi** screenshot tests, Baseline Profiles and Macrobenchmark

## Getting started

### Requirements

- **Android Studio** (a recent version that supports AGP 9.3)
- Android SDK 37
- A Wear OS device or emulator (round), and optionally an Android phone or emulator

### Clone and build

```bash
git clone <repository-url>
cd Voxara
./gradlew :app:assembleDebug :phone:assembleDebug
```

> [!TIP]
> If Gradle fails at `JdkImageTransform` / `jlink`, your default JDK has no `jlink`. Point `JAVA_HOME` to the JDK that ships with Android Studio:
>
> ```bash
> export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"   # Git Bash on Windows
> ./gradlew --stop
> ```

### Optional: Tono (AI coach)

Tono is optional. Without a key (or with cloud AI turned off) the apps work fully, and Tono uses built-in answers offline. To enable the cloud AI in **debug** builds, add these to `local.properties` (never commit this file):

```properties
AI_API_KEY=your-key-here
# Optional, these are the defaults:
AI_BASE_URL=https://api.deepseek.com
AI_MODEL=deepseek-flash
```

Release builds never contain the key.

## Running the apps

```bash
# Watch
./gradlew :app:installDebug
# Phone
./gradlew :phone:installDebug
```

With both a watch and a phone connected, pick the device with `adb -s <serial>`, or run each configuration from Android Studio.

To use a real watch over Wi-Fi, turn on **Wireless debugging** on the watch, then:

```bash
adb mdns services          # find the watch's ip:port
adb connect <ip>:<port>
```

On first launch the watch asks for the microphone and notification permissions, and the phone runs a short setup.

## Testing

```bash
./gradlew :shared:testDebugUnitTest      # dose, alerts, ledger, sync, situation detection
./gradlew :app:testDebugUnitTest         # watch UI, layout and screenshot tests
./gradlew :phone:testDebugUnitTest       # phone UI, figures, charts and font-scale tests
./gradlew :app:lintDebug
```

Screenshot tests use **Roborazzi** on Robolectric. The watch renders go to `app/build/screenshots/` (every screen at 192, 203, 216, 227 and 240 dp, and font scale 1.0, 1.3 and 2.0). The phone render matrix is opt-in because it is slow:

```bash
VOXARA_SHOTS_DIR=/abs/path/to/out ./gradlew :phone:testDebugUnitTest --tests '*PhoneRenderMatrixTest*'
```

Tests check, among other things:

- no text is cut or broken mid-word on any screen size or font scale;
- WCAG AA contrast for every accent, in light and dark;
- alert floors that no mode can lower;
- Home, Today and Trends all show the same figures.

### Debug-only controls

Debug builds of the watch app accept display-only commands over adb. This lets you see states the emulator cannot produce. They write no dose, no ledger and no alerts:

```bash
adb shell am broadcast -a com.example.voxara.DEBUG --ei listen 85          # headphones playing at ~85 dBA (-1 = off)
adb shell am broadcast -a com.example.voxara.DEBUG --ez phone true         # the phone reported headphones
adb shell am broadcast -a com.example.voxara.DEBUG --es hpalert 2:93.0     # show the headphone-volume alert
adb shell am broadcast -a com.example.voxara.DEBUG --ef crown 3            # a real rotary (crown) event
adb shell am broadcast -a com.example.voxara.DEBUG --ed dba 72.0           # room level (used when the mic is denied)
```

None of this code ships: release builds get a no-op.

## Design

Both apps share one visual language, **"Orbit"**: a pure black AMOLED background on the watch, a violet accent, amber and crimson for the loud and dangerous tiers, and the fonts *Plus Jakarta Sans* and *JetBrains Mono*. The watch is a pager of three round "heroes" (Now, Dose, More). The phone is built to feel like the watch's other half.

Design rules the code follows:

1. One glance, one answer: level, zone and dose before anything else.
2. Safety statements are never hidden or softened by styling.
3. Colour never carries a zone alone: a word and an icon always go with it.
4. Calm by default: motion and haptics only mark state changes. No animation in ambient mode, and reduced motion is respected.
5. Estimates are labelled as estimates.

All strings are resources in **English** and **Spanish**.

## Documentation

| Document | Content |
|---|---|
| [`PRODUCT.md`](PRODUCT.md) | Users, purpose, constraints and principles |
| [`DESIGN.md`](DESIGN.md) | Design tokens and components of the watch and the phone |
| [`docs/design/STYLE.md`](docs/design/STYLE.md) | Colours, type, shapes and where each value lives in code |
| [`docs/design/watch-redesign/`](docs/design/watch-redesign) | The watch "Orbit" redesign, pass by pass |
| [`docs/design/phone-redesign/`](docs/design/phone-redesign) | The phone redesign and where every piece of information went |
| [`docs/reliability/`](docs/reliability) | Reliability audit, feature map and test plan |
| [`PROGRESS.md`](PROGRESS.md) | Detailed development log, phase by phase |

## Privacy

- Sound is turned into levels on the watch. **No audio is recorded or stored.**
- Levels per minute (35 days), settings and calibration stay on your devices and sync only between your watch and your phone.
- Tono's cloud AI asks for your consent first, and then sends only numbers (dose percentages, rounded dBA, minutes, rough time of day). It never sends audio, song or app names, or location. With it off, Tono uses built-in answers and works offline.
- The phone's headphone detection is opt-in and off by default.
