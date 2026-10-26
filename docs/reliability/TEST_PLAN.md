# Manual test plan: Galaxy Watch 7 + phone

For the things a JVM test cannot show. Each row is: steps → expected. "Phone" = the Voxara phone app, "watch" = Voxara on the
Galaxy Watch 7. Finding references (F#, S#) are in `AUDIT.md`. Record the result and the time next to each row.

**Setup.** Debug or (once signed with one key, S1) release builds of both apps installed on the paired pair. Phone: Voxara →
Settings → "Share headphone listening with the watch" **on**, notifications allowed, Bluetooth permission allowed. Watch: monitoring
on, microphone allowed. Keep a second set of headphones (Bluetooth earbuds) at hand. `adb logcat -s VoxaraPhoneSync:* VoxaraLink:* VoxaraCompanion:* VoxaraBridge:*`
on both devices helps (watch over wireless debugging).

## A. Install, signing, discovery

| # | Steps | Expected |
|---|---|---|
| T1 | Install **only the phone** app (watch has none). Open Voxara → Watch. | "Voxara isn't on your watch" with an Install button. Home shows no data, no error. |
| T2 | Install the watch app. Wait up to 30 s, or reopen the phone app. | The phone moves to Linked without a restart; first sync fills Home/Today. |
| T3 | **Signing (S1).** Install a release phone APK and a release watch APK signed with *different* keys. | Expected to fail: the phone says the watch has no Voxara (capability never matches). Then sign both with one key: Linked. If this passes with two keys, tell me, the audit is wrong. |
| T4 | Watch with **no phone installed** (uninstall the phone app). Use the watch for 10 min. | Everything works; the More page link line says not connected, no error, no battery drain spike. |

## B. Connectivity states

| # | Steps | Expected |
|---|---|---|
| T5 | **Phone out of range.** Walk away until the watch loses Bluetooth (or turn the watch's Bluetooth off). Keep the watch measuring 30 min. | Watch: keeps measuring and alerting; link line "reconnecting" then "disconnected since …" after 1 min. Phone: "Reconnecting", then "Disconnected since …"; Home numbers get "Last known" after 3 h. |
| T6 | Come back in range. Do **not** touch the phone. | Within ~1 min: Connected; the history for the away period appears on the phone by itself (catch-up); no duplicate minutes (compare Today's hourly bars with the watch's Dose page). |
| T7 | **Bluetooth off on the phone** during a sync (open Watch → Sync now, then switch Bluetooth off at once). | "Sync problem" (watch unreachable) with help text within ~20 s, spinner stops, toast says it failed (F4). Turn Bluetooth on: clears itself at the next sync. |
| T8 | **Airplane mode** on the phone for 10 min, change *Alert level* on the phone to a new value, turn airplane mode off. | Phone shows "Pending sync" for that setting, then it clears, and the watch Settings shows the new value without opening the watch app (F5). |
| T9 | **Long absence.** Leave the phone off for 6+ h while wearing the watch (some alerts, some headphones). Switch the phone on. | Catch-up finishes (watch logcat shows requests `/voxara/rpc/day`); phone callback does not get killed (S2: if the sync is cut short, the hourly worker finishes it; note how long it took). |
| T10 | **Phone reboot** while paired. | After boot: the phone app's headphone watcher restarts if sharing is on (notification "Watching headphones"); link returns without opening the app; `SyncWorker` still runs hourly (check Settings → Apps → Voxara → Battery / `adb shell dumpsys jobscheduler | grep voxara`). |
| T11 | **Watch reboot.** | Watch posts "Tap to resume" (monitoring cannot restart itself); phone shows Reconnecting then Connected; settings changed meanwhile on the phone are applied once the watch app is opened or the link refreshes (F5). |
| T12 | **App killed by the system.** Force-stop the watch app, then change a setting on the phone, then open the watch app. | The setting is on the watch after the app opens and the phone is reachable (F5b). Force-stop the *phone* app and walk out of range and back: the watch still alerts on its own; the phone resumes by itself on return (a Wear listener starts it). |
| T13 | **Samsung deep sleeping apps.** Put the phone app under Settings → Battery → Background usage limits → "Deep sleeping apps"; wear the watch for an hour. | Note whether the hourly sync and the watch's limit-alert copy still arrive; if not, the in-app battery guidance (Home/Watch) should be visible and its button should open the app's battery page. |
| T14 | **Doze.** Leave the phone idle, screen off, unplugged for 2 h; then open the app. | Last sync time no later than ~1 h 15 min; opening the app syncs (> 10 min rule); no crash. |

## C. Versions and clocks

| # | Steps | Expected |
|---|---|---|
| T15 | **Update one device only.** Install a newer phone build over the old one, keep the old watch build (and the other way round). | Linked either way; no "incompatible" unless the ledger format changed (then "Sync problem: incompatible" with a hint to update the watch app); settings still sync; status fields the other side lacks show as absent, not as zero. |
| T16 | **Clock difference.** On the watch, turn off automatic time and set the clock 20 s *ahead* of the phone. Change *Alert level* on the phone. | The watch applies it (F5) and the phone does not stay on "Pending sync". Set the watch 3 min behind: mirrored limit alerts and the phone-headphone live state still work (F9). Restore automatic time. |
| T17 | Change the phone's time zone (travel). | Phone's Today shows the new local day; no minute is lost or doubled at the boundary; the watch is unaffected (UTC days on the wire). |

## D. Idempotency, ordering, data

| # | Steps | Expected |
|---|---|---|
| T18 | Sync twice in a row (pull to refresh twice). | Second sync reports nothing updated; totals identical. |
| T19 | **Same minute, many volume changes (F1).** Play music in Bluetooth earbuds paired to the **phone**, change the volume 8 times within one minute, keep it playing 3 min, then wait for the next sync. | Phone Headphones/Today shows ≈ 3 min of listening (not ≈ 1 min); watch Dose → Week headphone share rose by a plausible amount. |
| T20 | **Duplicate span (F2).** (Debug) `adb shell am force-stop com.example.voxara` on the watch right after a span was sent, reopen. | The listening is not counted twice (compare minutes before/after). |
| T21 | **Delete all (F3).** Play headphones on the phone while the watch is out of range (spans queue), then Settings → Delete all data, then bring the watch back. | Both devices empty; no listening reappears on either after the next sync. Phone shows "deleted on this phone; the watch will clear when connected" if the watch was away. |
| T21b | **Delete all while monitoring runs.** Watch measuring in a loud room (the dose rising), headphones playing on the phone. Delete all data on the phone. Keep measuring for 2 minutes. | Both devices show 0 % and an empty history at once; afterwards only what was measured AFTER the delete appears (the first minute may hold under 60 s); no alert fires from the pre-delete level; the alert history stays empty until a new alert. |
| T22 | **Delete all with the watch away**, then reconnect. | Watch clears on the first sync; phone stays empty. |
| T23 | Both watch and phone headphones playing the same minute. | One minute counted (the louder), never the sum. |
| T24 | CSV export with 90+ days of history. | Share sheet opens without freezing the app (F8); two files, numbers only, decimal dot. |

## E. Headphones layer

| # | Steps | Expected |
|---|---|---|
| T25 | Phone earbuds: connect, play, raise the volume above ~80 dBA estimate, wait 8 s. | Watch shows headphones mode "from the phone" within a few seconds, then the over-safe alert with Lower/Dismiss; Lower works; with the phone out of range it says it could not reach the phone after 6 s. |
| T26 | Same, phone out of range, **watch dosimeter off**. | The alert still fires from the last saved figures (`PhoneListeningAlerts`) once the state arrives; nothing crashes. |
| T27 | Older phone app (before live state) + new watch: play music on the phone. | Watch headphones mode follows the span reports (up to ~16 min, as before); it never stops measuring the room or alerting while waiting. |

## F. Alerts and foreground rules

| # | Steps | Expected |
|---|---|---|
| T28 | Reach 100 % daily dose (debug bench or a loud source) with "Show 100 % alerts on this phone" on. | Watch vibrates and notifies first; phone shows one notification within seconds; out of range: the phone copy arrives on reconnect only if < 10 min old; never twice. |
| T29 | Revoke the watch microphone permission, then try to start monitoring. | No crash; clear message; no foreground service. |
| T30 | Start monitoring from a tile / complication while the app is closed. | Either it opens the app (visible start) or posts the resume prompt; never a crash (SDK 37 FGS rules). |
| T31 | Deny notification permission on both devices. | Apps work; limit alerts still vibrate on the watch; Settings/Home explain the missing permission. |

## G. Link screens (every state reachable)

Walk the phone's Watch screen through: Checking (cold start), no Wear OS services (use a phone without Google Play services if
available, else skip), No watch connected (watch Bluetooth off), Voxara isn't on your watch (uninstall on the watch), Linked
(fresh, then wait > 15 min for "At hh:mm", > 3 h for "Last known"), Sync problem (T7), Syncing (during Sync now), Connected /
Reconnecting / Disconnected since (T5/T6). Each must show the right text, the right help button, and survive rotation and font scale 2.0.
