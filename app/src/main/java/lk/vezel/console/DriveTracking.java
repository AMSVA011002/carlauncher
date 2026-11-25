package lk.vezel.console;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

/** App-process GPS and trip state. All callers and location callbacks are on the main thread. */
final class DriveTracking implements LocationListener {
    private static final String BACKGROUND = "background_trip_tracking";
    private static final String METERS = "trip_meters", MOVING = "trip_moving";
    private static DriveTracking instance;

    interface Observer { void changed(Location fix, boolean warning); }

    private final Context app;
    private final SharedPreferences prefs;
    private final LocationManager locations;
    private final PowerManager power;
    private final TrackingSession session;
    final TripMeter trip;
    private Observer dashboard;
    private boolean serviceActive, providerEnabled, shuttingDown;
    private long savedAt = -1;

    static DriveTracking get(Context context) {
        if (instance == null) instance = new DriveTracking(context.getApplicationContext());
        return instance;
    }

    private DriveTracking(Context app) {
        this.app = app;
        prefs = app.getSharedPreferences(DashboardActivity.SETTINGS_NAME, Context.MODE_PRIVATE);
        power = app.getSystemService(PowerManager.class);
        locations = app.getSystemService(LocationManager.class);
        trip = new TripMeter(prefs.getFloat(METERS, 0f), prefs.getLong(MOVING, 0L));
        trip.alert(prefs.getInt(DashboardActivity.LIMIT, 60), prefs.getBoolean(DashboardActivity.ALERT, true));
        session = new TrackingSession(trip, new TrackingSession.Source() {
            @Override public boolean start() { return subscribe(); }
            @Override public void stop() { unsubscribe(); }
        });
        providerEnabled = readProvider();
    }

    boolean precise() { return app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED; }
    boolean interactive() { return power == null || power.isInteractive(); }
    boolean providerEnabled() { return providerEnabled; }
    boolean sampling() { return session.sampling(); }
    boolean backgroundEnabled() { return prefs.getBoolean(BACKGROUND, true); }
    boolean canRunBackground() { return backgroundEnabled() && !shuttingDown && precise(); }
    boolean needsService() { return !serviceActive && canRunBackground() && providerEnabled && interactive(); }

    void observe(Observer observer) {
        dashboard = observer;
        refresh();
    }

    void unobserve(Observer observer) {
        if (dashboard != observer) return;
        dashboard = null;
        refresh();
        checkpoint();
    }

    void serviceActive(boolean active) {
        serviceActive = active;
        refresh();
    }

    void setBackgroundEnabled(boolean enabled) {
        prefs.edit().putBoolean(BACKGROUND, enabled).apply();
        refresh();
    }

    void refresh() {
        providerEnabled = readProvider();
        boolean wasSampling = session.sampling();
        session.update(!shuttingDown && interactive() && precise() && providerEnabled,
                dashboard != null, serviceActive && backgroundEnabled(), SystemClock.elapsedRealtime());
        if (wasSampling && !session.sampling()) checkpoint();
        publish(null, false);
    }

    private boolean readProvider() {
        try { return locations != null && locations.isProviderEnabled(LocationManager.GPS_PROVIDER); }
        catch (RuntimeException ignored) { return false; }
    }

    @SuppressWarnings("MissingPermission") // Registration is gated by refresh(); handle changes during the call too.
    private boolean subscribe() {
        try {
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, this, Looper.getMainLooper());
            return true;
        } catch (RuntimeException unavailable) {
            Log.w("VezelTracking", "GPS updates unavailable", unavailable);
            return false;
        }
    }

    private void unsubscribe() {
        try { locations.removeUpdates(this); } catch (RuntimeException ignored) { /* Provider already gone. */ }
    }

    @Override public void onLocationChanged(Location fix) {
        if (fix == null || !session.sampling()) return;
        if (!precise()) { refresh(); return; }
        long now = SystemClock.elapsedRealtime();
        float speed = fix.hasSpeed() ? fix.getSpeed() : -1;
        if (Build.VERSION.SDK_INT >= 26 && fix.hasSpeedAccuracy() && fix.getSpeedAccuracyMetersPerSecond() > 3f) speed = -1;
        TrackingSession.Sample sample = session.accept(new TripMeter.Fix(fixTime(fix), fix.getLatitude(), fix.getLongitude(),
                fix.hasAccuracy() ? fix.getAccuracy() : 100, speed, fix.hasBearing() ? fix.getBearing() : -1), now);
        if (sample == TrackingSession.Sample.IGNORED) return;
        if (savedAt < 0 || now - savedAt >= 20_000L) checkpoint();
        publish(fix, sample == TrackingSession.Sample.WARNING);
    }

    static long fixTime(Location fix) { return fix.getElapsedRealtimeNanos() / 1_000_000L; }

    @Override public void onProviderEnabled(String provider) { if (LocationManager.GPS_PROVIDER.equals(provider)) refresh(); }
    @Override public void onProviderDisabled(String provider) { if (LocationManager.GPS_PROVIDER.equals(provider)) refresh(); }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) { /* Required on older Android. */ }

    private void publish(Location fix, boolean warning) {
        if (dashboard != null) dashboard.changed(fix, warning);
    }

    void resetTrip() {
        trip.reset();
        checkpoint();
        publish(null, false);
    }

    void checkpoint() { writeTotals().apply(); }

    void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
        refresh();
        // The unit may cut power soon after this broadcast. Flush rather than queue this write.
        writeTotals().commit();
    }

    private SharedPreferences.Editor writeTotals() {
        savedAt = SystemClock.elapsedRealtime();
        return prefs.edit().putFloat(METERS, (float) trip.meters()).putLong(MOVING, trip.movingMs());
    }
}
