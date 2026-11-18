# Vezel Drive Console

A native, landscape Android **Home launcher** for a **2014 Honda Vezel Hybrid RU3 in Wine Red**. The dashboard is designed at **1024 × 600** for the pictured **Allwinner T3 / K2001N** head unit. It uses Java and Android Canvas, with the supplied vehicle photograph instead of a 3D engine. No native libraries, WebView, account, ads, or root access are required. Minimum Android version: **6.0 (API 23)**; Android 13 and ARMv7 are supported by the package format.

## Install on the head unit

This source builds **Vezel Drive Console 1.3.0 — Photo Edition**. In the Live Preview, use **Download APK** in the footer (also available in preview **Settings**).

1. **While parked**, copy `Vezel-Drive-Console.apk` to the head unit or use `adb install Vezel-Drive-Console.apk`. Open it from the apps list before making it the default. This is a development-signed build for sideloading, not a Play Store release.
2. Tap **Setup** at the top of the dashboard and choose **Vezel Drive Console** as the **Home app**. The same checklist is under **Settings → Home app, GPS and notification access**. You can switch back in Android’s **Default apps → Home app**. If your firmware locks this selection, you can still open the launcher manually.
3. Tap **Allow precise location** in Setup or **Enable GPS** on the dashboard. Grant *precise* location while using the app. Turn on Android location and wait for a clear-sky GPS fix.
4. Tap **Allow notification access** in Setup and enable **Vezel media and directions**. Start a track or a route in your usual app, then return Home. If a trusted sideload is marked *Restricted* on Android 13, Android may require **App info → ⋮ → Allow restricted settings** before you can grant access. Low-RAM Android 10 and older builds may block notification listeners entirely.
5. In **Settings → App shortcuts**, assign the installed music, Bluetooth phone, and radio apps you want. Use **Apps** on the dock to open the full-screen, searchable app grid. It lists enabled Android launchable apps; background services do not appear.

## What the dashboard uses

| Feature | Behavior and dependencies |
| --- | --- |
| **Gold Honda Welcome and Good Bye** | When Android opens the launcher at boot, it shows the glowing gold Honda emblem, **Welcome**, and a short musical tone. When set as Home, it can also greet after a supported wake. **GOOD BYE** with a different tone is a best-effort key-off cue, not a shutdown command. Both can be previewed and their tones can be muted in **Settings → Start and key-off cues**. |
| **Photo dashboard** | The fixed-angle vehicle image is the supplied Wine Red reference, framed so navigation and controls stay out of the picture. The unlit asset is unchanged. **Settings → Dashboard appearance** switches between the large number with speed bar and the round dial, and lets you show, edit, or hide a registration label beside the car. This edition does not rotate. |
| **Headlights and night display** | **Lights + Fog** switches to a matching version of the photograph with the visible headlamp, fog lamp and rear light illuminated. Night display can be set separately. For real light-switch sync, enter the firmware’s **documented broadcast action and extra key** in **Settings → Configure OEM illumination signal**. It accepts a boolean, `0`/`1`, or `on`/`off`. The exact K2001N signal has not been verified; if unavailable, use the on-screen toggle or choose **On/Off** in Settings. These controls never change the physical lamps. |
| **Speed, heading and trip** | Uses fresh GPS fixes, not the vehicle bus or canned values. The warning triangle appears when the displayed GPS speed reaches the selected threshold. The offline female warning plays after two valid readings at or above it, then re-arms once the speed drops at least 4 km/h below it. Trip and moving time persist locally until reset. The launcher samples GPS only while the dashboard is visible and the screen is on, so it won’t add distance while another full-screen app is open. The alert threshold is not a posted road limit. |
| **Music** | Shows title, artist and art from the active Android media session when provided, with play/pause and track controls. On startup it attempts to resume the last *playing* session if that same player restores a resumable Android session. It cannot force a player that doesn’t restore a session after power loss to play. Disable resume in Settings if desired. |
| **Google Maps / Waze** | Mirrors recognizable turn and distance text in active guidance notifications. Start navigation in Google Maps or Waze first. If the app version/language doesn’t expose recognizable English turn text, the tile remains an app shortcut rather than inventing guidance. The navigation app remains the source of truth. |
| **Weather** | Requests current conditions from [Open-Meteo](https://open-meteo.com/) over HTTPS using location rounded to two decimal places. Without a location fix it uses Colombo. Recent saved readings are marked as saved when needed; no live values are fabricated when offline. You can turn weather off in Settings. |

The unit in the photos reports **1 GB RAM**. The launcher keeps only the current photo bitmap, and does not initialize a 3D graphics session. GPS and media UI work also stop when the dashboard is not visible. Check the actual OS API on firmware that relabels Android versions: `adb shell getprop ro.build.version.sdk`. A value below `23` cannot install this build. Firmware integration and memory behavior should be checked on the actual head unit before relying on it on the road.

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

Visit `http://localhost:3000`. Click the Honda badge for Welcome, **Lights + Fog** to test the lighting, and the speed alert badge or **Settings** for simulated GPS and the Good Bye preview. The Apps page in the browser uses sample entries, not a list from your device. The browser has sample weather, directions and original short audio loops. It is **not** the Android launcher and never reads device telemetry. The APK’s dependencies and permissions are independent of the preview. The browser and Android use the same two local photo assets. Settings also demonstrates both speed styles and a registration label saved in the browser.

### Personal registration (optional)

All builds let you enter, edit, or hide the registration in **Settings → Dashboard appearance**, without modifying the photograph. To prefill it in a private APK build:

```sh
python3 tools/personalize_vehicle.py --province LK --plate 'ABC 0123'  # Replace with your own registration
bash tools/build-apk.sh --personal                                    # Add --sdk-only if needed
```

This creates a Git-ignored label under `private-assets/photo/` and a Git-ignored APK in `artifacts/private/`. The default build and GitHub Actions omit the label. The standard browser preview does not serve the private APK or label. Run `python3 tools/preview.py --personal --port 3000` **only** in a private environment if you intend to serve that APK by its download route.

## Artwork and type

- `shared/vezel-photo.webp` is the supplied Wine Red reference photo. `tools/prepare_vehicle.py shared/vezel-photo.webp` (requires Pillow) regenerates the lit photo from the same source. The dashboard crops some empty sky to fit the head-unit layout.
- The selected warning voice is bundled as a short offline MP3. The original ascending and descending power tones can be regenerated with `python3 tools/make_cues.py`.
- The Honda automobile mark is from the [Simple Icons Honda vector](https://github.com/simple-icons/simple-icons/blob/develop/icons/honda.svg), recolored gold for the requested startup. Honda marks belong to Honda; this is an independent launcher.
- Font license notices for Space Grotesk, Manrope, and IBM Plex Mono are included with their assets.
