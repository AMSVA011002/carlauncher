# Vezel Drive Console

A native, landscape Android **Home launcher** for a **2014 Honda Vezel Hybrid RU3 in Wine Red**. The dashboard is designed at **1024 × 600** for the pictured **Allwinner T3 / K2001N** head unit. It uses Java and Android Canvas, with the supplied vehicle photograph instead of a 3D engine. No native libraries, WebView, account, ads, or root access are required. Minimum Android version: **6.0 (API 23)**; Android 13 and ARMv7 are supported by the package format.

## Install on the head unit

This source builds **Vezel Drive Console 1.6.2 — Daylight Edition**. In the Live Preview, use **Download APK** in the footer (also available in preview **Settings**).

1. **While parked**, copy `Vezel-Drive-Console.apk` to the head unit or use `adb install Vezel-Drive-Console.apk`. Open it from the apps list before making it the default. This is a development-signed build for sideloading, not a Play Store release.
2. Tap **Setup** at the top of the dashboard and choose **Vezel Drive Console** as the **Home app**. The same checklist is under **Settings → Home app, GPS and notification access**. You can switch back in Android’s **Default apps → Home app**. If your firmware locks this selection, you can still open the launcher manually.
3. Tap **Allow precise location** in Setup or **Enable GPS** on the dashboard. Grant *precise* location while using the app. Turn on Android location and wait for a clear-sky GPS fix. **Track trips in other apps** is on by default. On Android 13+, **Show GPS tracking notification** is optional; it is separate from the media notification access below.
4. Tap **Allow notification access** in Setup and enable **Vezel media controls**. Start a track in your usual app, then return Home. If a trusted sideload is marked *Restricted* on Android 13, Android may require **App info → ⋮ → Allow restricted settings** before you can grant access. Low-RAM Android 10 and older builds may block notification listeners entirely.
5. Tap **BT Audio** on the dock to open the head unit’s Bluetooth music app. If it does not find a unique match, select the installed app. Change the assignment under **Settings → App shortcuts → BT Audio**. If Bluetooth music is a tab inside the factory Bluetooth app, select that app and open its music tab. Use the head unit’s Bluetooth app to pair your phone.
6. Tap **Apps** for the full-screen, searchable list of launchable apps, with three large icons per row and scrolling. Maps and Waze still appear if installed. There is no turn guidance on the dashboard.

## What the dashboard uses

| Feature | Behavior and dependencies |
| --- | --- |
| **Gold Honda Welcome and Good Bye** | A cold dashboard opening shows the supplied beveled gold Honda emblem and **Welcome** silently so returning Home cannot interrupt a player. A recognized power wake with the dashboard already visible can play the welcome tone if no playback is detected. **GOOD BYE** with a different tone is a best-effort key-off cue, not a shutdown command. Both can be previewed and their tones can be muted in **Settings → Start and key-off cues**. The same badge is used on the dashboard, Apps page, launcher icon, and Android splash. |
| **Photo dashboard** | Day mode uses the supplied outdoor Wine Red photo. Night mode retains the original studio photo. The photo lights have an aligned on/off frame in both palettes. **Settings → Dashboard appearance** switches between the default metallic speedometer and an optional large number with speed bar, and lets you show, edit, or hide a registration label beside the car. This edition does not rotate. |
| **Headlight Sync and day/night display** | Leave actual brightness under the head unit’s control. **Settings → Headlight sync** can learn the head unit's two brightness readings when the car lights turn off/on. In Auto, lights off selects the light palette and unlit outdoor photo; lights on selects the dark palette and illuminated studio photo. The gauge and Apps page also follow the palette. Manual color and photo controls are still available and never change the car’s lights. |
| **Speed, heading and trip** | Uses fresh GPS fixes, not the vehicle bus or canned values. The red needle and blue scale show 0–180 km/h; the numeric readout shows the measured speed, or a dash if no fresh reading is available. The warning triangle appears when the displayed GPS speed reaches the selected threshold. The offline female warning plays after two valid readings at or above it, then re-arms once the speed drops at least 4 km/h below it. Trip distance and moving time persist locally until reset. The tracking service continues to count while another app is open; the audible warning still plays only from the visible dashboard. GPS pauses when the screen turns off or location is unavailable. Missing GPS segments are not estimated. The alert threshold is not a posted road limit. |
| **Music** | Shows title, artist and art from the active Android media session when provided, with play/pause and track controls. Returning Home does not send play, pause or stop to other apps. After a recognized power wake, the launcher can try to resume the last *playing* session if nothing is already playing and that player exposes a resumable session. Disable resume in Settings if desired. |
| **Weather** | Requests current conditions from [Open-Meteo](https://open-meteo.com/) over HTTPS using location rounded to two decimal places. Without a location fix it uses Colombo. Recent saved readings are marked as saved when needed; no live values are fabricated when offline. You can turn weather off in Settings. |

The unit in the photos reports **1 GB RAM**. The launcher keeps the current photo and dial face bitmaps, and does not initialize a 3D graphics session. Dashboard rendering, media observation, and new weather requests stop when the dashboard is hidden. The trip service keeps a single GPS subscription when needed and does not hold a wake lock or read media sessions. Check the actual OS API on firmware that relabels Android versions: `adb shell getprop ro.build.version.sdk`. A value below `23` cannot install this build. Firmware integration and memory behavior should be checked on the actual head unit before relying on it on the road.

### Trip tracking and playback in other apps

- Grant **precise location while using the app** and keep Android GPS on. The console starts its location foreground service from the visible dashboard; it does not request **Allow all the time** or start tracking at boot before the dashboard opens. Only totals are saved, not a GPS route.
- **Settings → Driving → Track trips in other apps** controls background tracking. The silent **GPS trip tracking** notification's **Dashboard only** action also switches it off. GPS still works on the visible dashboard if background tracking is off, blocked by firmware, or the service can't start. While the display sleeps or GPS is off, the service pauses sampling and saves the trip. Android can stop the service; reopen the dashboard to start it again.
- Radio, BT Audio, Music and Apps shortcuts open external activities outside the Home task, without resetting the other app's task. Pressing **Home** leaves those apps' playback and tasks alone. An OEM radio can still stop itself when it loses the foreground, or the head unit can reclaim it; the launcher cannot keep other apps running against their own behavior or the firmware. If only the factory radio still stops, compare pressing Home with the factory launcher and check the unit's radio/background playback settings.

**Head-unit check:** Start from a valid GPS fix and note **Trip distance** and **Drive time**. Start Radio or Music, then compare those totals after a known drive and returning Home when parked (e.g. 96 km + 5 km should be about 101 km). Check that speed is current, the sound continues on Home, and reopening the player brings back its existing screen. Repeat with **Track trips in other apps** off to check the dashboard-only mode, and test sleep/wake while parked. The browser preview cannot validate GPS, Android tasks, or OEM radio playback.

### Sync with the factory dimmer

1. **While parked**, keep your head unit's existing option to dim when the headlights or parking lights come on enabled. The launcher does not write brightness settings or set its own backlight level, including in the Apps screen and setup dialogs.
2. Open **Settings → Headlight sync · Factory dimmer**. With the physical lights off and the screen at its normal level, tap **Capture lights OFF**.
3. Turn on the parking lights or headlights. After the screen finishes dimming, tap **Capture lights ON & save**. Saving selects **Auto** for both **Dashboard colors** and **Lights on the car photo**. Back at the dashboard, toggle the vehicle lights off and on again to verify both light/dark palettes and unlit/lit photos. Existing calibration carries over when updating from 1.5 with the same signer.

This works if the firmware reports two repeatable values in Android's brightness setting. Values stay on the unit. It does not use the camera, read the vehicle bus, or treat any drop in brightness as a headlight event. A manual or adaptive brightness change that lands on the same level cannot be distinguished from factory dimming. Relearn after changing the head unit’s brightness settings. Tap **Restore Auto for colors and photo** in **Headlight sync** if you used the manual controls and want to resume sync.

If the brightness reading is missing or does not change, the factory backlight can still dim independently. This launcher cannot see dimming done only by the unit's hardware or stock launcher. With no learned levels, Auto follows the Android day/night theme unless a documented OEM signal is available. If the vendor supplies a signal for your firmware, enter its action and extra under **Headlight sync → Advanced: documented OEM signal**. Otherwise use the **Lights + Fog** switch or the manual color options. Automatic sync on the K2001N still needs this parked test; no unverified firmware signal is assumed.

### Engine off and Android power

Use the head unit’s **own power/ACC settings** to select full shutdown rather than sleep, if the firmware offers it. A standard launcher can’t switch off Android, intercept hard power loss, or replace the bootloader graphic. Good Bye can start on the system shutdown notice or on a documented OEM ACC off signal **while the dashboard is visible and the screen still has power**. Allow about three seconds before the screen switches off if the unit supports a delay. A late broadcast or immediate loss of power can cut off the animation or sound.

In **Settings → Start and key-off cues**, only configure an ACC action and ignition-state extra if the vendor documents them (`0`/`off` or `1`/`on`). Dispatch from a separate OEM app additionally requires a configured signature-level sender permission. Leave the fields empty if the signal is unknown. The K2001N-specific ACC signal and full-shutdown setting have not been verified on hardware. The key-off preview only plays the screen and tone.

## Build and preview

Install **JDK 17**, Android SDK Platform **35**, and Android Build Tools **35.0.0**. Set `ANDROID_HOME` to the SDK location.

```sh
bash tests/run.sh
bash tools/build-apk.sh
```

The build script runs the Gradle wrapper, checks the APK signature, alignment, Android API levels and bundled assets, then writes `artifacts/Vezel-Drive-Console.apk` and its `.sha256` checksum. GitHub Actions runs the SDK-only build and saves the APK and checksum as a workflow artifact. To publish a new version, run the **Android** workflow from the desired branch with **Publish the verified APK to GitHub Releases** enabled. The version from `app/build.gradle` is used as the release tag; existing releases are not overwritten. You can also build normally with `./gradlew :app:assembleDebug` or Android Studio.

For this dependency-free launcher, `bash tools/build-apk.sh --sdk-only` can package directly using the installed Android SDK and JDK, without downloading Gradle dependencies. It uses the standard local Android development key (`~/.android/debug.keystore`), or an existing **development** key at `VEZEL_DEBUG_KEYSTORE`. Do not commit signing keys. Android requires the same signer to install an update; a differently signed build requires uninstalling the previous build, clearing its settings and trip. Install a same-signer update with `adb install -r artifacts/Vezel-Drive-Console.apk` if using ADB.

For a browser-only interactive simulation (after building, the download link appears automatically):

```sh
python3 tools/preview.py --port 3000
```

Visit `http://localhost:3000`. Click **DEMO · LIGHTS OFF/ON** (or **Settings → Headlight sync** in a narrow portrait window) to simulate the factory light switch, which changes both the photo and the dashboard palette. **Lights + Fog** only toggles the photo. Click the Honda badge for Welcome, or use **Settings** for simulated GPS and the Good Bye preview. The Apps page in the browser uses sample entries, not a list from your device. The browser has sample weather and original short audio loops. Its BT Audio shortcut explains the Android setup; it cannot pair with or open a Bluetooth app. It is **not** the Android launcher and never reads device telemetry. The APK’s dependencies and permissions are independent of the preview. The browser and Android share both photo pairs, dial faces, and badge artwork. Settings also demonstrates both speed styles and a registration label saved in the browser.

### Personal registration (optional)

All builds let you enter, edit, or hide the registration in **Settings → Dashboard appearance**, without modifying the photograph. To prefill it in a private APK build:

```sh
python3 tools/personalize_vehicle.py --province LK --plate 'ABC 0123'  # Replace with your own registration
bash tools/build-apk.sh --personal                                    # Add --sdk-only if needed
```

This creates a Git-ignored label under `private-assets/photo/` and a Git-ignored APK in `artifacts/private/`. The default build and GitHub Actions omit the label. The standard browser preview does not serve the private APK or label. Run `python3 tools/preview.py --personal --port 3000` **only** in a private environment if you intend to serve that APK by its download route.

## Artwork and type

- `shared/vezel-photo-day.webp` uses the supplied 1024×572 outdoor photo. With Pillow installed, `python3 tools/prepare_vehicle.py --day /path/to/image-4.png` regenerates this day pair.
- `shared/vezel-photo.webp` remains the original night reference. `python3 tools/prepare_vehicle.py shared/vezel-photo.webp` regenerates its lit counterpart. Both pairs use matching framing within each palette.
- The selected warning voice is bundled as a short offline MP3. The original ascending and descending power tones can be regenerated with `python3 tools/make_cues.py`.
- `app/src/main/res/drawable-nodpi/honda_badge.webp` is the supplied beveled gold emblem with its white background removed. Honda marks belong to Honda; this is an independent launcher.
- `python3 tools/prepare_instruments.py` (requires Pillow) regenerates the two metallic dial faces. Optionally pass `--badge /path/to/reference.png` to extract the badge from the supplied reference. Only the needle, progress arc, and live reading are drawn at runtime.
- Font license notices for Space Grotesk, Manrope, and IBM Plex Mono are included with their assets.
