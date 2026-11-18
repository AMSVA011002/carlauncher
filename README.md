# Vezel Drive Console

A native, landscape Android **Home launcher** for a **2014 Honda Vezel Hybrid RU3 in Wine Red**. The dashboard is designed at **1024 × 600** for the pictured **Allwinner T3 / K2001N** head unit. It uses Java and Android Canvas, two small compressed vehicle images, and public Android APIs. No native libraries, WebView, account, ads, or root access are required. Minimum Android version: **6.0 (API 23)**; Android 13 and ARMv7 are supported by the package format.

## Install on the head unit

The current build is **Vezel Drive Console 1.0.1**. In the Live Preview, use **Download APK** in the footer (also available in preview **Settings**).

1. **While parked**, copy `Vezel-Drive-Console.apk` to the head unit or use `adb install Vezel-Drive-Console.apk`. Open it from the apps list before making it the default. This is a development-signed build for sideloading, not a Play Store release.
2. Tap **Setup** at the top of the dashboard and choose **Vezel Drive Console** as the **Home app**. The same checklist is under **Settings → Home app, GPS and notification access**. You can switch back in Android’s **Default apps → Home app**. If your firmware locks this selection, you can still open the launcher manually.
3. Tap **Allow precise location** in Setup or **Enable GPS** on the dashboard. Grant *precise* location while using the app. Turn on Android location and wait for a clear-sky GPS fix.
4. Tap **Allow notification access** in Setup and enable **Vezel media and directions**. Start a track or a route in your usual app, then return Home. If a trusted sideload is marked *Restricted* on Android 13, Android may require **App info → ⋮ → Allow restricted settings** before you can grant access. Low-RAM Android 10 and older builds may block notification listeners entirely.
5. In **Settings → App shortcuts**, assign the installed music, Bluetooth phone, and radio apps you want. Use the **Apps** dock button to open other installed apps.

## What the dashboard uses

| Feature | Behavior and dependencies |
| --- | --- |
| **Gold Honda startup** | Shows the gold Honda emblem when the launcher opens from a cold start. When selected as Home, it also replays after a supported screen-off/wake cycle. Tap the Honda badge to replay it any time. It does **not** replace the manufacturer’s boot logo shown before Android loads. |
| **Headlights and night display** | The vehicle artwork switches between matched unlit and lit images, including the lamps and the low beam on the floor. Auto follows Android night mode by default. For a real light-switch sync, enter the firmware’s **documented broadcast action and extra key** in **Settings → Configure OEM illumination signal**. It accepts boolean, `0`/`1`, or `on`/`off` values with `1` meaning lights on. The exact K2001N signal has not been verified; if the firmware doesn’t expose it, tap **Headlights** or choose **On/Off** in Settings. The moon toggles dim display manually; choose **Auto** to restore automatic dimming. These controls never change the physical lamps. |
| **Speed, heading and trip** | Uses fresh GPS fixes, not the vehicle bus or canned values. After two valid readings over the selected threshold, the launcher sounds a quiet chime once until speed drops below it. Trip and moving time persist locally until reset. The launcher samples GPS only while the dashboard is visible and the screen is on, so it won’t add distance while another full-screen app is open. The alert threshold is not a posted road limit. |
| **Music** | Shows title, artist and art from the active Android media session when provided, with play/pause and track controls. On startup it attempts to resume the last *playing* session if that same player restores a resumable Android session. It cannot force a player that doesn’t restore a session after power loss to play. Disable resume in Settings if desired. |
| **Google Maps / Waze** | Mirrors recognizable turn and distance text in active guidance notifications. Start navigation in Google Maps or Waze first. If the app version/language doesn’t expose recognizable English turn text, the tile remains an app shortcut rather than inventing guidance. The navigation app remains the source of truth. |
| **Weather** | Requests current conditions from [Open-Meteo](https://open-meteo.com/) over HTTPS using location rounded to two decimal places. Without a location fix it uses Colombo. Recent saved readings are marked as saved when needed; no live values are fabricated when offline. You can turn weather off in Settings. |

The unit in the photos reports **1 GB RAM**, so the launcher stops its GPS and media UI work when it is not visible and does not run a permanent tracking service. Check the actual OS API on firmware that relabels Android versions: `adb shell getprop ro.build.version.sdk`. A value below `23` cannot install this build. Firmware integration and memory behavior should be checked on the actual head unit before relying on it on the road.

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

Visit `http://localhost:3000`. Click the Honda badge for startup, the headlight chip to test the lighting, and the speed alert badge or **Settings** for simulated GPS. The browser has sample weather, directions and original short audio loops. It is **not** the Android launcher and never reads device telemetry. The APK’s dependencies and permissions are independent of the preview.

## Artwork and type

- The Wine Red RU3 studio image is an illustration. Both matched lighting states use the same composition; the low-beam effects are baked into the light-on asset to avoid live GPU effects on the head unit.
- The Honda automobile mark is from the [Simple Icons Honda vector](https://github.com/simple-icons/simple-icons/blob/develop/icons/honda.svg), recolored gold for the requested startup. Honda marks belong to Honda; this is an independent launcher.
- Font license notices for Space Grotesk, Manrope, and IBM Plex Mono are included with their assets.
