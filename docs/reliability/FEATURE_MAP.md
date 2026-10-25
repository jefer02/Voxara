# Voxara feature map: watch (`:app`), phone (`:phone`), shared (`:shared`, `:shared-ui`)

Written during the reliability audit (October 2026). Paths are relative to the repo root; `…/voxara/` is
`src/main/java/com/example/voxara/` of the module named. "WL" = `WearableListenerService` (started by Google Play
services, works with the app closed). Everything below was read from the code; nothing was measured on a device.

## 0. The shape of the system

- Two APKs with the **same applicationId `com.example.voxara`** (`app/build.gradle.kts:37`, `phone/build.gradle.kts:30`).
  The Data Layer connects them only if they are also signed with the **same key** (see AUDIT.md, S1).
- The watch is **standalone** (`com.google.android.wearable.standalone = true`): it measures, accumulates, alerts and
  renders with no phone. The phone is a companion: history, settings, Tono chat, headphone listening on the phone.
- **The watch is the single source of truth for the dose ledger.** The phone *pulls* it (digest, then whole UTC days),
  never pushes minutes. The only things that flow phone → watch are: settings, listening spans from phone headphones,
  the live headphone state, the "lower volume" command, and the delete-all request.
- Discovery: capabilities `voxara_watch_app` (`app/src/main/res/values/wear.xml`) and `voxara_phone_app`
  (`phone/src/main/res/values/wear.xml`), looked up with `CapabilityClient.FILTER_REACHABLE`.
- Time: ledger minutes are **UTC epoch minutes**; days on the wire are UTC days (`LedgerCodec.utcDay`), so no payload
  depends on either device's time zone. Each device regroups minutes into its own local days.

## 1. Cross-device data (one row per kind of data)

| Data | Owner (truth) | Direction | Transport / path | Payload and version | Sent when | If the other device is unreachable | Retry / catch-up |
|---|---|---|---|---|---|---|---|
| Minute ledger (ambient + headphone) | Watch DB (`ExposureDb`) | watch → phone | MessageClient request/response `/voxara/rpc/digest`, `/voxara/rpc/day` | gzip binary `LedgerCodec` FORMAT 1, ≤ 1000 records per part (≈ 31 KB < 90 KB cap); digest = JSON `count:crc32` per (UTC day, ledger) | phone pulls: app open (> 10 min since last), pull-to-refresh, hourly `SyncWorker`, watch back (capability event) | request fails after 20 s → recorded as `SyncProblem.UNREACHABLE`; phone keeps its last copy | next trigger; each day is replaced atomically, so any retry/interruption is safe; self-heals when a day changed mid-fetch |
| Alert history | Watch DB `alerts` | watch → phone | `/voxara/rpc/alerts` (pages of 300) | JSON | same pull | same | replaced from the window start each sync (acknowledgements change after the fact) |
| Watch status (dose %, weekly %, monitoring, permissions, mode, hp mode…) | Watch | watch → phone | request `/voxara/rpc/status` **and** DataItem `/voxara/status` | JSON, `v:1`, additive; unknown/missing fields fall back (`WatchStatus.fromJson`) | on every status RPC; on reconnect catch-up (`PhoneLink.catchUp`) | phone shows the last status with its own time ("Last known") | overwritten item: a resend is never a duplicate |
| Synced settings (16 fields, `SettingField`) | Per field: the newest change; ties → watch | both | DataItems `/voxara/settings/phone` (phone → watch) and `/voxara/settings/watch` (watch → phone) | `SettingsDoc` JSON `{v:1, fields:{key:{value, at}}}`, every value `normalize()`d (clamped) by the receiver | on each change; watch republishes after applying | item waits in the Data Layer; phone shows "Pending sync" | phone: reads the watch's item on every sync. Watch: **now also reads the phone's item whenever the phone becomes reachable** (`CompanionLink.pullPhoneSettings`) |
| Listening spans from phone headphones | Phone measures; **watch ledger owns the result** | phone → watch | DataItem `/voxara/listening/<endMs>` (one item per span) | item map: end_ms, seconds, volume_db, index, max, output, coarse | `PhoneListeningService` cuts a span at every connect/play/pause/volume event; `ListeningSampler` fallback every 15 min | span waits in the phone's persisted `Outbox` (500 items, 3 days) | flushed on every successful send and on reconnect. Watch applies each span **once** (`SeenKeys`) and **adds** it to the minute |
| Live headphone state | Phone | phone → watch | MessageClient `/voxara/hp/live` + one DataItem `/voxara/hp/state` | `PhoneListeningState` JSON (sequence + time), repeated every 60 s while connected | on every change, ≥ 0.4 s apart | message lost (by design); the item is read on reconnect | `sequence` only grows; watch drops anything not newer; "fresh" = 3 min |
| "Lower to safe volume" | Watch asks, phone acts | watch → phone, answer back | MessageClient `/voxara/hp/cmd/lower` → `/voxara/hp/cmd/lowered` | JSON with an id | on tap | 6 s without an answer = "could not reach your phone"; never queued | none (a late volume drop would surprise) |
| Mirrored 100 % alert (optional, off by default) | Watch alerts on its own; phone shows a courtesy copy | watch → phone | MessageClient `/voxara/alert/limit` through the persisted `PhoneOutbox` | JSON kind/value/level/at | after the watch vibrated and notified | queued ≤ 10 min | flushed on reconnect; phone shows once per alert, never older than 10 min |
| Delete all data | Phone requests, watch executes | phone → watch | request `/voxara/rpc/delete` | `{ok:true}` | Settings → Delete all data | phone clears at once and keeps `deletePending`; **also drops the listening still queued for the watch** (new) | sent first on the next successful sync |
| Open on watch / install | Phone | phone → watch | `RemoteActivityHelper` deep links `voxara://watch/open|calibration`, Play Store page | n/a | buttons on the Watch screen | button reports failure | user retries |
| Phone-only | Phone | none | DataStore / SharedPreferences | n/a | n/a | n/a | Appearance (accent, theme), setup done, retention, preferred watch, mirror switch, headphone sharing opt-in, Tono chat |

Size limits: every DataItem is < 3 KB (limit 100 KB); the largest message is a ledger part (< 40 KB gzip, cap 90 KB, asserted by
`SyncCompatibilityTest` for a full 33-day window of full days from both origins). Assets and ChannelClient are not used.

Settings fields with their single source of truth: ALERT_THRESHOLD, HEADPHONE_LOUD, SENSITIVE, QUIET_ENABLED/START/END,
DAILY_CAP, HAPTIC_STRENGTH, ADVISORY_HAPTICS, SNOOZE_MINUTES, HEADPHONE_TYPE, LANGUAGE, TONO_ENABLED, CLOUD_AI,
SITUATION_MODE, HEADPHONE_MODE (legacy: the watch answers `AUTO` with a newer time, so an older app settles).
Accent and theme are **not** here (phone-only on purpose). *No ping-pong*: an accepted value keeps the sender's time (or,
when that time was in the watch's future, the watch's own now); equal value + time is not a change and the Data Layer does not
notify an identical item; `SettingsMerge` is idempotent (`SettingsMergeTest`).

## 2. Watch (`:app`) features

| Feature | Where | Runs in | Touches the phone? |
|---|---|---|---|
| Dosimeter (burst sampler → NIOSH dose, rolling weekly, minute ledger) | `service/DosimeterService.kt`, `audio/AudioBurstSampler.kt`, `shared core/dose,ledger` | foreground service, type `microphone`, started from a visible surface only | no |
| Alerts (daily 50/80/100 %, weekly, level, headphone over-safe) | `shared core/alerts/AlertEngine.kt`, `service/AlertNotifier.kt`, `AlertActionReceiver.kt` | dosimeter loop; headphone alert also from `PhoneListeningAlerts` when the dosimeter is off | mirrored LIMIT copy (optional) |
| Orbit UI: Now / Dose / More pages, Mode, Ask (Tono), Settings, Calibration, Onboarding, limit-alert and headphone-alert overlays | `ui/`, `presentation/MainActivity.kt` | activity | Mode and settings sync; Tono cloud needs the watch's own network |
| Situation modes + Auto detection (scene model, debug-only weights) | `shared core/situation`, `audio/SceneClassifier.kt` | dosimeter | `SITUATION_MODE` synced |
| Headphones layer (watch's own headphones, phone's live state, estimate, weekly share) | `shared audio/HeadphoneWatcher.kt`, `core/headphones/*`, `ui/screens/HeadphonesLayer.kt` | dosimeter listening loop | receives `/voxara/hp/*`, sends `lower` |
| Tono (AI coach, offline fallback) | `ai/Tono.kt`, `shared ai/TonoCore.kt` | UI | `TONO_ENABLED`, `CLOUD_AI` synced |
| Tiles (dose, listening), complications (dose, listening) | `tile/*`, `complication/*` | system-bound services, read the store/DB | no |
| Midnight rollup, monitoring watchdog | `service/MidnightRollupWorker.kt`, `MonitoringWatchdogWorker.kt` (WorkManager periodic, `KEEP`) | WorkManager | no |
| Boot / resume prompt | `service/BootReceiver.kt` | `BOOT_COMPLETED` → notification only (a microphone FGS cannot start from boot) | no |
| Companion endpoint (answers the phone) | `sync/CompanionService.kt`, `CompanionLink.kt` | WL: `REQUEST_RECEIVED /voxara/rpc*`, `DATA_CHANGED /voxara/settings/phone` | yes |
| Phone listening spans | `sync/ListeningSyncService.kt` | WL: `DATA_CHANGED /voxara/listening*` | yes |
| Headphone state, lower answer, phone capability | `sync/HeadphoneSyncService.kt` | WL: `MESSAGE_RECEIVED /voxara/hp*`, `DATA_CHANGED /voxara/hp/state`, `CAPABILITY_CHANGED voxara_phone_app` | yes |
| Link health + outbox + catch-up | `sync/PhoneLink.kt` (`PhoneLink`, `PhoneOutbox`) | dosimeter loop (30 s), activity (15 s), WL | yes |
| Debug-only bench (broadcast) | `src/debug/.../Bench.kt` | debug builds | no |

## 3. Phone (`:phone`) features

| Feature | Where | Notes |
|---|---|---|
| Home (dose, zone strip, sync freshness, "situation + headphones"), Today, Alerts, Trends | `ui/screens/HomeScreen.kt`, `DetailScreens.kt`, `TrendsScreen.kt`, `model/History.kt` | numbers are the watch's own while from today, else ledger-derived with the sync time; never "live" |
| Watch screen: link states, install/open/calibrate on watch, pending settings, battery guidance | `ui/screens/WatchScreen.kt`, `link/WatchGateway.kt`, `sync/SyncCoordinator.kt` | states: Checking, WearApiMissing, NoWatch, NotInstalled, Linked, Problem + `LinkHealth` (connected / reconnecting / disconnected since) |
| Headphones screen + live listening + alert panel | `ui/screens/HeadphonesScreen.kt`, `HeadphoneLive.kt`, `listening/*` | opt-in sharing switch (off by default) |
| Tono chat | `ui/screens/TonoScreen.kt`, `tono/PhoneTono.kt` | cloud only with consent; symptom guard answers offline |
| Settings: synced settings, retention, mirror switch, Appearance, Export CSV, Delete all | `ui/screens/SettingsScreen.kt`, `export/CsvExport.kt` | export now builds files off the main thread |
| Setup wizard, How Voxara works | `ui/screens/SetupAndInfo.kt` | |
| Glance widget "Today's sound" | `widget/DoseWidget.kt` | refreshed after each sync (worker, app, listener), never on a timer |
| Hourly sync worker | `sync/SyncWorker.kt` | `KEEP`, scheduled in `VoxaraPhoneApp.onCreate` |
| Watch listener | `sync/PhoneListenerService.kt` | WL: `DATA_CHANGED /voxara/status`, `/voxara/settings/watch`; `MESSAGE_RECEIVED /voxara/alert/limit`, `/voxara/hp/cmd/lower`; `CAPABILITY_CHANGED voxara_watch_app` |
| Phone headphone service | `listening/PhoneListeningService.kt` (FGS `connectedDevice`) + `HeadphoneConnectionReceiver`, `PhoneBootReceiver` | started from the visible app, boot, update, Bluetooth broadcast (best effort); fallback `ListeningSampler` |
| Mirrored 100 % alerts | `MirroredAlerts.kt` | `setLocalOnly(true)` so Wear never bridges it back |

## 4. Link states on the phone and what produces each

| State shown | Produced by | Reachable in tests |
|---|---|---|
| Checking | first `refreshLink()` pending | `SyncCoordinatorTest` link states |
| Wear OS services missing | Play services absent / `API_NOT_CONNECTED` | yes |
| No watch connected | `connectedNodes` empty (never paired, Bluetooth off, out of range, watch off: **the API cannot tell these apart**) | yes |
| Voxara isn't on your watch | connected node without capability `voxara_watch_app` | yes |
| Linked (+ last sync, freshness RECENT / EARLIER / STALE) | capability present, last sync fine | yes |
| Sync problem (UNREACHABLE / INCOMPATIBLE / NO_WEAR_API) | last sync failed after the last success. **A 20 s timeout is now recorded** (it used to be rethrown and shown as nothing) | yes (`a watch that does not answer in time…`) |
| Syncing | `SyncCoordinator.syncing` | yes |
| Connected / Reconnecting (≤ 60 s) / Disconnected since X | `LinkMonitor` fed by every observation | yes |
