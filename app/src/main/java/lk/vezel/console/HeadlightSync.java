package lk.vezel.console;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/** Shares the read-only factory dimmer state across the dashboard and Apps. Main thread only. */
final class HeadlightSync {
    static final String MODE = "display_mode", LAMPS = "light_mode";
    static final String OEM_ACTION = "illumination_action", OEM_EXTRA = "illumination_extra";
    private static final String BRIGHT = "illumination_bright_level", DIM = "illumination_dim_level";
    private static final long SETTLE_MS = 300L, RECHECK_MS = 2_500L;
    private static HeadlightSync instance;

    static HeadlightSync get(Context context) {
        if (instance == null) instance = new HeadlightSync(context.getApplicationContext());
        return instance;
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Runnable> listeners = new LinkedHashSet<>();
    private final Runnable sample = this::sampleBrightness;
    private final ContentObserver brightnessObserver = new ContentObserver(main) {
        @Override public void onChange(boolean selfChange) {
            if (!listeners.isEmpty()) schedule(SETTLE_MS);
        }
    };
    private int level;
    private boolean observing, showingStatus;
    private BroadcastReceiver receiver;
    private Boolean firmwareLamps;

    private HeadlightSync(Context context) {
        this.context = context;
        prefs = context.getSharedPreferences(DashboardActivity.SETTINGS_NAME, Context.MODE_PRIVATE);
        level = readBrightness();
        bindOem();
    }

    void watch(Runnable listener) {
        if (!listeners.add(listener)) return;
        if (listeners.size() == 1) {
            try {
                context.getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
                        false, brightnessObserver);
                observing = true;
            } catch (RuntimeException ignored) { /* Some providers allow reads but miss change callbacks. */ }
            level = readBrightness();
            if (hasFactoryDim() || showingStatus) schedule(RECHECK_MS);
        }
        listener.run();
    }

    void unwatch(Runnable listener) {
        if (!listeners.remove(listener) || !listeners.isEmpty()) return;
        main.removeCallbacks(sample);
        if (observing) {
            try { context.getContentResolver().unregisterContentObserver(brightnessObserver); }
            catch (RuntimeException ignored) { }
            observing = false;
        }
    }

    void showStatus(boolean show) {
        if (showingStatus == show) return;
        showingStatus = show;
        if (show && !listeners.isEmpty()) sampleBrightness();
    }

    private void schedule(long delay) {
        main.removeCallbacks(sample);
        main.postDelayed(sample, delay);
    }

    private void sampleBrightness() {
        if (listeners.isEmpty()) return;
        int current = readBrightness();
        if (current != level) { level = current; changed(); }
        if (hasFactoryDim() || showingStatus) schedule(RECHECK_MS);
    }

    int readBrightness() {
        try {
            int brightness = Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, LightSignal.UNAVAILABLE);
            return LightSignal.validLevel(brightness) ? brightness : LightSignal.UNAVAILABLE;
        } catch (RuntimeException unavailable) { return LightSignal.UNAVAILABLE; }
    }

    int level() { return level; }
    int brightLevel() { return prefs.getInt(BRIGHT, LightSignal.UNAVAILABLE); }
    int dimLevel() { return prefs.getInt(DIM, LightSignal.UNAVAILABLE); }
    boolean hasFactoryDim() { return LightSignal.validCalibration(brightLevel(), dimLevel()); }
    int mode() { return prefs.getInt(MODE, 0); }
    int lampMode() { return prefs.getInt(LAMPS, 0); }
    boolean hasOverrides() { return mode() != 0 || lampMode() != 0; }
    void setMode(int selection) { prefs.edit().putInt(MODE, selection).apply(); changed(); }
    void setLampMode(int selection) { prefs.edit().putInt(LAMPS, selection).apply(); changed(); }
    void followLighting() { prefs.edit().putInt(MODE, 0).putInt(LAMPS, 0).apply(); changed(); }

    boolean learnFactoryDim(int bright, int dim) {
        if (!LightSignal.validCalibration(bright, dim)) return false;
        prefs.edit().putInt(BRIGHT, bright).putInt(DIM, dim).putInt(MODE, 0).putInt(LAMPS, 0).apply();
        level = readBrightness();
        bindOem();
        if (!listeners.isEmpty()) schedule(RECHECK_MS);
        changed();
        return true;
    }

    void forgetFactoryDim() {
        prefs.edit().remove(BRIGHT).remove(DIM).apply();
        bindOem();
        changed();
    }

    boolean setOem(String action, String key) {
        prefs.edit().putString(OEM_ACTION, action).putString(OEM_EXTRA, key)
                .remove(BRIGHT).remove(DIM).putInt(MODE, 0).putInt(LAMPS, 0).apply();
        bindOem();
        changed();
        return action.isEmpty() || receiver != null;
    }

    /** Keep OEM state for the process, including the transition between dashboard and Apps. */
    private void bindOem() {
        if (receiver != null) {
            try { context.unregisterReceiver(receiver); } catch (RuntimeException ignored) { }
            receiver = null;
        }
        firmwareLamps = null;
        if (hasFactoryDim()) return;
        String action = prefs.getString(OEM_ACTION, ""), key = prefs.getString(OEM_EXTRA, "");
        if (action.isEmpty() || key.isEmpty()) return;
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context target, Intent intent) {
                if (receiver != this || intent == null || !action.equals(intent.getAction())) return;
                try {
                    Bundle extras = intent.getExtras();
                    Boolean value = extras == null ? null : LightSignal.read(extras.get(key));
                    if (value != null && !value.equals(firmwareLamps)) { firmwareLamps = value; changed(); }
                } catch (RuntimeException ignored) { /* Advisory input; ignore unexpected extras. */ }
            }
        };
        try {
            IntentFilter filter = new IntentFilter(action);
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            else context.registerReceiver(receiver, filter);
        } catch (RuntimeException ignored) { receiver = null; }
    }

    private boolean systemNight() {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private Boolean factoryState() { return LightSignal.factoryState(brightLevel(), dimLevel(), level); }

    String source() {
        if (hasFactoryDim()) {
            Boolean dimmed = factoryState();
            if (dimmed == null) return "Factory dim · current reading does not match either learned level.";
            return dimmed ? "Factory dim · lights-on level detected." : "Factory dim · lights-off level detected.";
        }
        if (firmwareLamps != null) return firmwareLamps ? "OEM signal · lights on." : "OEM signal · lights off.";
        if (receiver != null) return "Waiting for the OEM signal. Following the Android theme for now.";
        return systemNight() ? "Android theme · night. Factory dim has not been learned." : "Android theme · day. Factory dim has not been learned.";
    }

    private boolean automatic() {
        boolean learned = hasFactoryDim();
        return LightSignal.automatic(learned ? factoryState() : firmwareLamps, learned, systemNight());
    }

    boolean night() { return LightSignal.enabled(mode(), automatic()); }
    boolean lamps() { return LightSignal.enabled(lampMode(), automatic()); }

    private void changed() { for (Runnable listener : new ArrayList<>(listeners)) listener.run(); }
}
