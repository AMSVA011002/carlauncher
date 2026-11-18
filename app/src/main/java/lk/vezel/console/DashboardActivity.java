package lk.vezel.console;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.res.Configuration;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DashboardActivity extends Activity implements LocationListener {
    private static final int LOCATION_REQUEST = 31, HOME_REQUEST = 32;
    static final String SETTINGS_NAME = "vezel_settings", BT_AUDIO = "shortcut_bt_audio",
            LIMIT = "speed_limit", ALERT = "speed_alert",
            ACC_ACTION = "ignition_action", ACC_EXTRA = "ignition_extra", ACC_PERMISSION = "ignition_permission",
            POWER_TONES = "power_tones";
    private static final String DIAL_GAUGE = "dial_gauge", REGISTRATION = "registration_label";
    final Handler main = new Handler(Looper.getMainLooper());
    SharedPreferences prefs;
    AppShortcuts apps;
    MediaBridge media;
    WeatherClient weather;
    HeadlightSync lights;
    private final Runnable appearanceChanged = this::appearance;
    private LocationManager locations;
    private DashboardView dashboard;
    private ConsolePanels panels;
    private TripMeter trip;
    private boolean started, working, gpsListening, providerEnabled, screenRegistered, destroyed;
    private long offAt = -1, wakeUntil = -1, saveAt = -1, clockMinute = -1;
    private int previousSpeed = -2;
    private boolean previousFix, clock24, homeSelected;
    private BroadcastReceiver ignitionReceiver;
    private DriveAudio audio;
    private final PowerCue ignition = new PowerCue();
    private boolean welcomePending, shuttingDown;
    private long lastWelcome = -5_000L, lastGoodbye = -5_000L;
    private boolean weatherCached;
    private long weatherStamp = -1;
    private final Runnable resumeAfterWelcome = () -> {
        if (working && !shuttingDown && homeLaunch()) {
            media.requestResume();
            media.refresh();
        }
    };
    private final Runnable pulse = new Runnable() {
        @Override public void run() {
            if (!working || destroyed) return;
            long now = SystemClock.elapsedRealtime();
            media.tick();
            weather.refresh(false);
            WeatherClient.Reading current = weather.current();
            if (clockMinute != System.currentTimeMillis() / 60_000L || previousSpeed != trip.speed(now)
                    || previousFix != trip.hasFix(now) || weatherStamp != (current == null ? -1 : current.at)
                    || weatherCached != (current != null && weather.cached(current))) render();
            main.postDelayed(this, 1_000L);
        }
    };
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                offAt = SystemClock.elapsedRealtime();
                dashboard.hidePowerScreen();
                setWorking(false);
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                long now = SystemClock.elapsedRealtime();
                if (offAt >= 0 && now - offAt >= 8_000L) wakeUntil = now + 15_000L;
                offAt = -1;
                if (started) { handleWake(); setWorking(true); }
            } else if (Intent.ACTION_SHUTDOWN.equals(action)) {
                shuttingDown = true;
                beginGoodbye();
            } else if (LocationManager.PROVIDERS_CHANGED_ACTION.equals(action) && working) {
                startGps(); render();
            }
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = getSharedPreferences(SETTINGS_NAME, MODE_PRIVATE);
        apps = new AppShortcuts(this);
        lights = HeadlightSync.get(this);
        audio = new DriveAudio(this);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        welcomePending = saved == null;
        homeSelected = apps.home();
        trip = new TripMeter(prefs.getFloat("trip_meters", 0f), prefs.getLong("trip_moving", 0L));
        trip.alert(limit(), alertEnabled());
        locations = getSystemService(LocationManager.class);
        dashboard = new DashboardView(this, this::dispatch);
        dashboard.data.dialGauge = prefs.getBoolean(DIAL_GAUGE, true);
        dashboard.data.registration = prefs.contains(REGISTRATION)
                ? VehicleRegistration.normalize(prefs.getString(REGISTRATION, "")) : bundledRegistration();
        media = new MediaBridge(this, prefs, this::render);
        weather = new WeatherClient(prefs, this::render);
        panels = new ConsolePanels(this);
        setContentView(dashboard);
        clock24 = DateFormat.is24HourFormat(this);
        immersive();
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SHUTDOWN);
        filter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
        try {
            registerPrivate(screen, filter);
            screenRegistered = true;
        } catch (RuntimeException ignored) { /* Full cold startup still works. */ }
        bindIgnition();
        appearance();
        render();
    }

    private void registerPrivate(BroadcastReceiver receiver, IntentFilter filter) {
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, filter);
    }

    @Override protected void onStart() {
        super.onStart();
        started = true;
        if (interactive()) { handleWake(); setWorking(true); }
    }

    @Override protected void onResume() {
        super.onResume();
        immersive();
        clock24 = DateFormat.is24HourFormat(this);
        clockMinute = -1;
        homeSelected = apps.home();
        if (working) {
            startGps();
            media.refresh();
        }
        render();
    }

    @Override protected void onStop() {
        started = false;
        dashboard.hidePowerScreen();
        setWorking(false);
        super.onStop();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        lights.unwatch(appearanceChanged);
        if (panels != null) panels.dismiss();
        if (screenRegistered) unregisterQuietly(screen);
        if (ignitionReceiver != null) unregisterQuietly(ignitionReceiver);
        main.removeCallbacksAndMessages(null);
        audio.stop();
        if (weather != null) weather.close();
        super.onDestroy();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        homeSelected = apps.home();
        if (intent.hasCategory(Intent.CATEGORY_HOME) && panels != null) panels.dismiss();
        immersive();
        render();
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        clockMinute = -1;
        appearance();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (dashboard != null && level >= TRIM_MEMORY_RUNNING_LOW) dashboard.trimCarCache();
    }

    @Override public void onBackPressed() {
        if (dashboard.hidePowerScreen()) { audio.stopGreeting(); return; }
        if (homeLaunch()) return;
        super.onBackPressed();
    }

    boolean homeSelected() { return homeSelected; }
    private boolean homeLaunch() { return (getIntent() != null && getIntent().hasCategory(Intent.CATEGORY_HOME)) || homeSelected; }

    private boolean interactive() {
        PowerManager power = getSystemService(PowerManager.class);
        return power == null || power.isInteractive();
    }

    private void handleWake() {
        long now = SystemClock.elapsedRealtime();
        boolean woke = now <= wakeUntil && homeLaunch();
        wakeUntil = -1;
        if (shuttingDown || (!welcomePending && !woke)) return;
        welcomePending = false;
        if (woke) ignition.reset();
        if (now - lastWelcome > 3_000L || dashboard.showingGoodbye()) {
            replayWelcome();
            main.removeCallbacks(resumeAfterWelcome);
            if (homeLaunch()) main.postDelayed(resumeAfterWelcome, 2_600L);
        }
    }

    private void setWorking(boolean on) {
        if (working == on || (on && shuttingDown)) return;
        working = on;
        if (on) {
            lights.watch(appearanceChanged);
            media.start();
            startGps();
            weather.refresh(false);
            main.removeCallbacks(pulse);
            main.postDelayed(pulse, 1_000L);
        } else {
            lights.unwatch(appearanceChanged);
            main.removeCallbacks(pulse);
            main.removeCallbacks(resumeAfterWelcome);
            media.stop();
            media.cancelResume();
            stopGps();
            persistTrip();
            audio.stop();
        }
    }

    private void immersive() { ConsoleWindow.immersive(this); }

    boolean preciseGps() { return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED; }
    private boolean coarseGps() { return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED; }

    void requestGps() {
        if (preciseGps()) {
            if (!providerEnabled && !apps.start(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))) toast("Android location settings are not available.");
            else if (providerEnabled) toast("Waiting for a GPS fix. Clear sky helps.");
            return;
        }
        if (prefs.getBoolean("asked_gps", false) && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            if (apps.start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())))) return;
        }
        prefs.edit().putBoolean("asked_gps", true).apply();
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST);
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] result) {
        super.onRequestPermissionsResult(request, permissions, result);
        if (request == LOCATION_REQUEST && working) { startGps(); weather.refresh(false); render(); }
    }

    @SuppressWarnings("MissingPermission")
    private void startGps() {
        if (locations == null || !working) return;
        boolean fine = preciseGps();
        if (!fine && !coarseGps()) weather.clearLocation();
        else useLastWeatherLocation();
        try { providerEnabled = locations.isProviderEnabled(LocationManager.GPS_PROVIDER); }
        catch (RuntimeException ignored) { providerEnabled = false; }
        if (!fine || !providerEnabled) { stopGps(); return; }
        if (gpsListening) return;
        try {
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, this, Looper.getMainLooper());
            gpsListening = true;
        } catch (RuntimeException ignored) { gpsListening = false; }
    }

    private void useLastWeatherLocation() {
        if (!weather.enabled()) return;
        Location best = null;
        for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
            try {
                Location fix = locations.getLastKnownLocation(provider);
                if (fix != null && (best == null || fix.getElapsedRealtimeNanos() > best.getElapsedRealtimeNanos())) best = fix;
            } catch (RuntimeException ignored) { /* Optional provider/interface. */ }
        }
        if (best != null && SystemClock.elapsedRealtime() - fixTime(best) < 60 * 60_000L) {
            weather.location(best.getLatitude(), best.getLongitude());
        } else weather.clearLocation();
    }

    private void stopGps() {
        if (gpsListening && locations != null) {
            try { locations.removeUpdates(this); } catch (RuntimeException ignored) {}
        }
        gpsListening = false;
        trip.pause();
    }

    @Override public void onLocationChanged(Location fix) {
        if (!working || shuttingDown || !gpsListening || fix == null) return;
        long now = SystemClock.elapsedRealtime(), time = fixTime(fix);
        if (time <= 0 || time > now || now - time > TripMeter.STALE_MS) return;
        if (weather.enabled()) {
            weather.location(fix.getLatitude(), fix.getLongitude());
            weather.refresh(false);
        }
        float speed = fix.hasSpeed() ? fix.getSpeed() : -1;
        if (Build.VERSION.SDK_INT >= 26 && fix.hasSpeedAccuracy() && fix.getSpeedAccuracyMetersPerSecond() > 3f) speed = -1;
        if (trip.accept(new TripMeter.Fix(time, fix.getLatitude(), fix.getLongitude(), fix.hasAccuracy() ? fix.getAccuracy() : 100,
                speed, fix.hasBearing() ? fix.getBearing() : -1)) && !dashboard.showingGoodbye()) previewWarning();
        if (saveAt < 0 || now - saveAt > 20_000L) persistTrip();
        render();
    }

    private static long fixTime(Location fix) { return fix.getElapsedRealtimeNanos() / 1_000_000L; }

    @Override public void onProviderEnabled(String provider) { if (working) { startGps(); render(); } }
    @Override public void onProviderDisabled(String provider) { if (working) { startGps(); render(); } }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { /* Legacy LocationListener. */ }

    void previewWarning() { audio.play(DriveAudio.Cue.WARNING); }

    private void persistTrip() {
        saveAt = SystemClock.elapsedRealtime();
        prefs.edit().putFloat("trip_meters", (float) trip.meters()).putLong("trip_moving", trip.movingMs()).apply();
    }

    void resetTrip() { trip.reset(); persistTrip(); render(); toast("Trip distance and drive time reset."); }
    int limit() { return Math.max(20, Math.min(140, prefs.getInt(LIMIT, 60))); }
    boolean alertEnabled() { return prefs.getBoolean(ALERT, true); }
    void setAlert(int limit, boolean enabled) {
        prefs.edit().putInt(LIMIT, limit).putBoolean(ALERT, enabled).apply();
        trip.alert(limit, enabled);
        if (!enabled) audio.stopWarning();
        render();
    }

    void notificationSetup() {
        if (!apps.start(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))) toast("Notification access is not available on this firmware.");
    }

    boolean notificationsAllowed() { return DriveNotifications.isAllowed(this); }

    void setAsHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager roles = getSystemService(RoleManager.class);
            try {
                if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                    startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), HOME_REQUEST);
                    return;
                }
            } catch (RuntimeException ignored) { /* Fall through to older head-unit settings. */ }
        }
        if (!apps.start(new Intent(Settings.ACTION_HOME_SETTINGS))) toast("Choose Vezel Drive Console in Android Default apps → Home app, if supported.");
    }

    private void unregisterQuietly(BroadcastReceiver receiver) {
        try { unregisterReceiver(receiver); } catch (RuntimeException ignored) { }
    }

    private void appearance() {
        if (dashboard == null || destroyed) return;
        dashboard.data.night = lights.night();
        dashboard.data.lamps = lights.lamps();
        if (panels != null) panels.refreshLightStatus();
        render();
    }

    private String bundledRegistration() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getAssets().open("vehicle/registration.txt"), StandardCharsets.US_ASCII))) {
            return VehicleRegistration.normalize(reader.readLine());
        } catch (IOException ignored) { return ""; }
    }

    boolean dialGauge() { return dashboard.data.dialGauge; }
    String registration() { return dashboard.data.registration; }

    void setDialGauge(boolean enabled) {
        prefs.edit().putBoolean(DIAL_GAUGE, enabled).apply();
        dashboard.data.dialGauge = enabled;
        dashboard.refresh();
    }

    void setRegistration(String label) {
        String cleaned = VehicleRegistration.normalize(label);
        prefs.edit().putString(REGISTRATION, cleaned).apply();
        dashboard.data.registration = cleaned;
        dashboard.refresh();
    }

    boolean powerTones() { return prefs.getBoolean(POWER_TONES, true); }
    void setPowerTones(boolean enabled) {
        prefs.edit().putBoolean(POWER_TONES, enabled).apply();
        if (!enabled) audio.stopGreeting();
    }

    void replayWelcome() {
        lastWelcome = SystemClock.elapsedRealtime();
        dashboard.showPowerScreen(false);
        if (powerTones()) audio.play(DriveAudio.Cue.WELCOME);
    }

    void replayGoodbye() {
        dashboard.showPowerScreen(true);
        if (powerTones()) audio.play(DriveAudio.Cue.GOODBYE);
    }

    private void beginGoodbye() {
        welcomePending = false;
        main.removeCallbacks(resumeAfterWelcome);
        media.cancelResume();
        persistTrip();
        if (!started || !interactive() || isFinishing()) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastGoodbye < 3_200L) return;
        lastGoodbye = now;
        panels.dismiss();
        replayGoodbye();
    }

    void setIgnition(String action, String extra, String permission) {
        prefs.edit().putString(ACC_ACTION, action).putString(ACC_EXTRA, extra).putString(ACC_PERMISSION, permission).apply();
        bindIgnition();
    }

    String ignitionStatus() {
        if (ignitionReceiver != null) return "Listening for the configured ACC state. Android controls when the unit shuts down.";
        if (!prefs.getString(ACC_ACTION, "").isEmpty()) return "ACC signal is not registered. Check the action and sender permission.";
        return "No ACC signal configured. A real Android shutdown notice can also start Good Bye if the dashboard is still visible.";
    }

    @SuppressWarnings("deprecation")
    private void bindIgnition() {
        if (ignitionReceiver != null) { unregisterQuietly(ignitionReceiver); ignitionReceiver = null; }
        ignition.reset();
        String action = prefs.getString(ACC_ACTION, ""), key = prefs.getString(ACC_EXTRA, "");
        String permission = prefs.getString(ACC_PERMISSION, "");
        if (!PowerCue.validAction(action) || !PowerCue.validExtra(key) || Intent.ACTION_SHUTDOWN.equals(action)
                || Intent.ACTION_SCREEN_OFF.equals(action) || Intent.ACTION_SCREEN_ON.equals(action)) return;
        if (!permission.isEmpty()) {
            try {
                PermissionInfo info = getPackageManager().getPermissionInfo(permission, 0);
                int protection = info.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE;
                if (protection != PermissionInfo.PROTECTION_SIGNATURE && protection != PermissionInfo.PROTECTION_SIGNATURE_OR_SYSTEM) return;
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) { return; }
        }
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (shuttingDown || intent == null || !action.equals(intent.getAction()) || isInitialStickyBroadcast()) return;
                try {
                    Bundle extras = intent.getExtras();
                    PowerCue.Event event = ignition.update(extras == null ? null : LightSignal.read(extras.get(key)));
                    if (event == PowerCue.Event.GOODBYE) beginGoodbye();
                    else if (event == PowerCue.Event.WELCOME) {
                        welcomePending = true;
                        if (started && interactive()) { panels.dismiss(); handleWake(); }
                    }
                } catch (RuntimeException ignored) { /* Malformed advisory input cannot change system power. */ }
            }
        };
        try {
            IntentFilter filter = new IntentFilter(action);
            if (!permission.isEmpty()) {
                if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, permission, main, Context.RECEIVER_EXPORTED);
                else registerReceiver(receiver, filter, permission, main);
            } else if (Build.VERSION.SDK_INT >= 33) registerPrivate(receiver, filter);
            else registerReceiver(receiver, filter, "android.permission.DEVICE_POWER", main);
            ignitionReceiver = receiver;
        } catch (RuntimeException ignored) { /* Firmware may not expose a usable signal. */ }
    }

    private void render() {
        if (destroyed || dashboard == null || media == null || weather == null) return;
        DashboardView.Data d = dashboard.data;
        long now = SystemClock.elapsedRealtime();
        long minute = System.currentTimeMillis() / 60_000L;
        if (clockMinute != minute) {
            clockMinute = minute;
            Date date = new Date();
            d.clock = new SimpleDateFormat(clock24 ? "HH:mm" : "h:mm", Locale.getDefault()).format(date);
            d.period = clock24 ? "" : new SimpleDateFormat("a", Locale.getDefault()).format(date).toUpperCase(Locale.getDefault());
            d.date = new SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(date).toUpperCase(Locale.getDefault());
        }
        d.speed = previousSpeed = trip.speed(now);
        previousFix = trip.hasFix(now);
        boolean precise = preciseGps();
        d.gps = !precise ? "ENABLE GPS" : !providerEnabled ? "GPS OFF" : previousFix ? "GPS FIX" : "AWAITING GPS";
        d.setupNeeded = !homeSelected || !precise || !media.allowed();
        d.heading = trip.heading(now);
        d.trip = String.format(Locale.getDefault(), trip.meters() < 100_000 ? "%.1f" : "%.0f", trip.meters() / 1_000d);
        long sec = trip.movingMs() / 1000L, minutes = sec / 60;
        d.drive = minutes >= 60 ? String.format(Locale.getDefault(), "%dh %02dm", minutes / 60, minutes % 60)
                : String.format(Locale.getDefault(), "%02d:%02d", minutes, sec % 60);
        d.limit = limit();
        d.speedAlert = alertEnabled();
        d.over = trip.limitReached(now);
        boolean playing = media.playing();
        d.track = !media.title().isEmpty() ? media.title() : media.packageName() != null ? apps.label(media.packageName()) : "No media playing";
        d.artist = !media.artist().isEmpty() ? media.artist() : !media.allowed() ? "Tap to set up media" : "Start music on your player";
        d.playing = playing;
        d.art = media.albumArt();
        WeatherClient.Reading current = weather.current();
        weatherCached = current != null && weather.cached(current);
        weatherStamp = current == null ? -1 : current.at;
        d.temperature = current == null ? "—" : current.temperature + "°";
        d.place = current != null ? current.place + (weatherCached ? " · saved" : "") : !weather.enabled() ? "Weather off"
                : weather.loading() ? "Updating…" : weather.failed() ? "Offline" : weather.usingPreset() ? "Colombo" : "Near you";
        d.weatherIcon = weatherIcon(current);
        dashboard.refresh();
    }

    private static int weatherIcon(WeatherClient.Reading value) {
        if (value == null) return R.drawable.ic_weather;
        if (value.code == 0) return value.day ? R.drawable.ic_sun : R.drawable.ic_moon;
        if (value.code <= 2) return R.drawable.ic_weather;
        if (value.code < 50) return R.drawable.ic_cloud;
        return R.drawable.ic_rain;
    }

    private void openBluetoothAudio() {
        if (!apps.launchBluetooth(prefs.getString(BT_AUDIO, ""))) panels.bluetoothAudio(true);
    }

    void openMusic() {
        if (apps.launch(prefs.getString("shortcut_music", "")) || apps.launch(media.packageName())
                || apps.start(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC))) return;
        panels.drawer("Assign music shortcut", "shortcut_music");
    }

    private void openPhone() {
        if (apps.launch(prefs.getString("shortcut_phone", "")) || apps.start(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))) return;
        panels.drawer("Assign phone shortcut", "shortcut_phone");
    }

    private void openRadio() {
        if (!apps.launch(prefs.getString("shortcut_radio", ""))) panels.drawer("Assign radio shortcut", "shortcut_radio");
    }

    void toggleWeather(boolean enabled) {
        prefs.edit().putBoolean("weather_enabled", enabled).apply();
        if (enabled) { if (locations != null && (preciseGps() || coarseGps())) useLastWeatherLocation(); weather.refresh(false); }
        render();
    }

    private void dispatch(int action) {
        switch (action) {
            case DashboardView.STARTUP: replayWelcome(); break;
            case DashboardView.SETUP: panels.setup(); break;
            case DashboardView.WEATHER: panels.weather(); break;
            case DashboardView.NIGHT:
                if (lights.mode() == 0) toast("Dashboard colors are now manual. Restore Auto in Settings.");
                lights.setMode(dashboard.data.night ? 2 : 1); break;
            case DashboardView.LAMPS:
                if (lights.lampMode() == 0) toast("Photo lights are now manual. Restore Auto in Settings.");
                lights.setLampMode(dashboard.data.lamps ? 2 : 1); break;
            case DashboardView.GPS: requestGps(); break;
            case DashboardView.LIMIT: panels.speed(); break;
            case DashboardView.BT_AUDIO: openBluetoothAudio(); break;
            case DashboardView.MEDIA: panels.media(); break;
            case DashboardView.PREVIOUS: mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS); break;
            case DashboardView.PLAY: mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE); break;
            case DashboardView.NEXT: mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT); break;
            case DashboardView.MUSIC: openMusic(); break;
            case DashboardView.PHONE: openPhone(); break;
            case DashboardView.RADIO: openRadio(); break;
            case DashboardView.SETTINGS: panels.settings(); break;
            case DashboardView.APPS:
                if (!apps.start(new Intent(this, AppsActivity.class)))
                    toast("Apps could not be opened.");
                break;
            default: break;
        }
    }

    private void mediaKey(int key) { if (!media.press(key)) panels.media(); }
    void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
}
