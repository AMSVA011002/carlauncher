package lk.vezel.console;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

/** One small, throttled weather request using rounded coordinates. No key or background service. */
final class WeatherClient {
    private static final long FRESH_MS = 25 * 60_000L, USABLE_MS = 2 * 60 * 60_000L;
    private static final double COLOMBO_LAT = 6.9271, COLOMBO_LON = 79.8612;
    private final SharedPreferences prefs;
    private final Runnable onChange;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ExecutorService worker;
    private volatile boolean closed;
    private long lastAttempt = -1;
    private double latitude = COLOMBO_LAT, longitude = COLOMBO_LON;
    private boolean localFix, busy, failed;
    private Reading reading;

    static final class Reading {
        final int temperature, code;
        final boolean day;
        final String place;
        final double lat, lon;
        final long at;

        Reading(int temperature, int code, boolean day, double lat, double lon, long at) {
            this.temperature = temperature;
            this.code = code;
            this.day = day;
            this.lat = lat;
            this.lon = lon;
            this.at = at;
            place = TripMeter.distanceMeters(lat, lon, COLOMBO_LAT, COLOMBO_LON) < 14_000 ? "Colombo" : "Near you";
        }

        String condition() {
            if (code == 0) return day ? "Sunny" : "Clear";
            if (code <= 2) return "Partly cloudy";
            if (code == 3) return "Overcast";
            if (code <= 48) return "Fog";
            if (code < 60) return "Drizzle";
            if (code <= 67 || (code >= 80 && code <= 82)) return "Rain";
            if (code < 80 || code <= 86) return "Snow";
            return "Thunderstorms";
        }
    }

    WeatherClient(SharedPreferences prefs, Runnable onChange) {
        this.prefs = prefs;
        this.onChange = onChange;
        long at = prefs.getLong("weather_at", -1);
        if (at > 0) reading = new Reading(prefs.getInt("weather_temp", 0), prefs.getInt("weather_code", 0),
                prefs.getBoolean("weather_day", true), prefs.getInt("weather_lat100", 693) / 100d,
                prefs.getInt("weather_lon100", 7986) / 100d, at);
    }

    void location(double lat, double lon) {
        if (Double.isNaN(lat) || Double.isNaN(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) return;
        localFix = true;
        latitude = Math.round(lat * 100d) / 100d;
        longitude = Math.round(lon * 100d) / 100d;
    }

    void clearLocation() {
        localFix = false;
        latitude = COLOMBO_LAT;
        longitude = COLOMBO_LON;
    }

    boolean enabled() { return prefs.getBoolean("weather_enabled", true); }
    boolean loading() { return busy; }
    boolean failed() { return failed; }
    boolean usingPreset() { return !localFix; }
    boolean cached(Reading value) { return failed || !isWithin(value, FRESH_MS); }

    Reading current() {
        if (!enabled() || !isWithin(reading, USABLE_MS)) return null;
        if (localFix && TripMeter.distanceMeters(latitude, longitude, reading.lat, reading.lon) > 35_000) return null;
        if (!localFix && TripMeter.distanceMeters(COLOMBO_LAT, COLOMBO_LON, reading.lat, reading.lon) > 14_000) return null;
        return reading;
    }

    void refresh(boolean force) {
        if (closed || busy || !enabled()) return;
        long now = SystemClock.elapsedRealtime();
        boolean moved = reading != null && TripMeter.distanceMeters(latitude, longitude, reading.lat, reading.lon) > 10_000;
        long pause = force ? 10_000L : (moved && !failed ? 30_000L : 5 * 60_000L);
        if (lastAttempt >= 0 && now - lastAttempt < pause) return;
        if (!force && !moved && isWithin(reading, FRESH_MS)) return;
        lastAttempt = now;
        busy = true;
        final double lat = Math.round(latitude * 100d) / 100d, lon = Math.round(longitude * 100d) / 100d;
        if (worker == null) worker = Executors.newSingleThreadExecutor();
        worker.execute(() -> {
            Reading value = load(lat, lon);
            main.post(() -> {
                if (closed) return;
                busy = false;
                failed = value == null;
                if (value != null && enabled()) {
                    reading = value;
                    prefs.edit().putInt("weather_temp", value.temperature).putInt("weather_code", value.code)
                            .putBoolean("weather_day", value.day).putInt("weather_lat100", (int) Math.round(lat * 100d))
                            .putInt("weather_lon100", (int) Math.round(lon * 100d)).putLong("weather_at", value.at).apply();
                }
                onChange.run();
            });
        });
        onChange.run();
    }

    void close() {
        closed = true;
        if (worker != null) worker.shutdownNow();
    }

    private static boolean isWithin(Reading value, long maxAge) {
        if (value == null) return false;
        long age = System.currentTimeMillis() - value.at;
        return age >= 0 && age <= maxAge;
    }

    private static Reading load(double lat, double lon) {
        HttpsURLConnection con = null;
        try {
            URL url = new URL(String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code,is_day&temperature_unit=celsius", lat, lon));
            con = (HttpsURLConnection) url.openConnection();
            con.setConnectTimeout(5_000);
            con.setReadTimeout(5_000);
            con.setInstanceFollowRedirects(false);
            con.setRequestProperty("Accept", "application/json");
            if (con.getResponseCode() != 200) return null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder json = new StringBuilder();
                char[] chars = new char[512];
                int count;
                while ((count = reader.read(chars)) != -1) {
                    if (json.length() + count > 32_768) return null;
                    json.append(chars, 0, count);
                }
                JSONObject current = new JSONObject(json.toString()).getJSONObject("current");
                double value = current.getDouble("temperature_2m");
                if (Double.isNaN(value) || value < -100 || value > 100) return null;
                return new Reading((int) Math.round(value), current.getInt("weather_code"), current.optInt("is_day", 1) == 1,
                        lat, lon, System.currentTimeMillis());
            }
        } catch (Exception ignored) { return null; }
        finally { if (con != null) con.disconnect(); }
    }
}
