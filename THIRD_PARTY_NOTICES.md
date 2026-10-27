# Third-party notices

Voxara's own code is © 2026 Jeferson Manuel Morillo Vallejo, all rights reserved (see [LICENSE](LICENSE)). It uses the third-party components below, each under its own licence.

## Fonts (bundled in the app)

| Font | Copyright | Licence | Text |
|---|---|---|---|
| [Plus Jakarta Sans](https://github.com/tokotype/PlusJakartaSans) | © 2020 The Plus Jakarta Sans Project Authors | SIL Open Font License 1.1 | [`PlusJakartaSans-OFL.txt`](shared-ui/src/main/assets/licenses/PlusJakartaSans-OFL.txt) |
| [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) | © 2020 The JetBrains Mono Project Authors | SIL Open Font License 1.1 | [`JetBrainsMono-OFL.txt`](shared-ui/src/main/assets/licenses/JetBrainsMono-OFL.txt) |

## Libraries (linked into the apps)

| Library | Licence |
|---|---|
| AndroidX: Compose (UI, Foundation, Material 3), Wear Compose, Wear Tiles, ProtoLayout, Watch Face Complications, Wear, Wear Ongoing, Wear Remote Interactions, Glance, Activity, Core, Core Splashscreen, Fragment, Lifecycle, Navigation, DataStore, WorkManager, Concurrent Futures, Profile Installer | Apache License 2.0 |
| [Kotlin standard library](https://github.com/JetBrains/kotlin) and [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) | Apache License 2.0 |
| [Guava](https://github.com/google/guava) | Apache License 2.0 |
| [Haze](https://github.com/chrisbanes/haze) | Apache License 2.0 |
| Google Play services Wearable | [Android Software Development Kit License](https://developer.android.com/studio/terms) |

## Build and test tools (not shipped)

| Tool | Licence |
|---|---|
| Android Gradle Plugin, AndroidX Benchmark, UI Automator, AndroidX Test | Apache License 2.0 |
| [Robolectric](https://github.com/robolectric/robolectric) | MIT License |
| [Roborazzi](https://github.com/takahirom/roborazzi) | Apache License 2.0 |
| [JUnit 4](https://github.com/junit-team/junit4) | Eclipse Public License 1.0 |

## Training data (debug builds only)

The scene model weights in `shared/src/debug` were trained on **TUT Acoustic Scenes 2017** (DCASE 2017 Task 1, Zenodo 400515 + 1040168), © Tampere University of Technology. Its EULA allows only internal, non-commercial and scientific research use. **The weights are not included in release builds and may not be used commercially** (see the licence gate in the [README](README.md#5-automatic-situation-detection-auto-mode)).
