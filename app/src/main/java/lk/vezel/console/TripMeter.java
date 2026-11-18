package lk.vezel.console;

/** GPS-only telemetry. All timestamps are monotonic milliseconds from the location fix. */
final class TripMeter {
    static final long STALE_MS = 8_000L;
    private static final double MOVING_KMH = 3;
    private static final double MAX_MPS = 90;
    private Fix previous;
    private double kmh = -1;
    private int heading = -1;
    private long lastFix = -1;
    private double meters;
    private long movingMs;
    private int limit = 60;
    private boolean chimeEnabled = true;
    private boolean latched;
    private int overSamples;

    static final class Fix {
        final long timeMs;
        final double lat, lon, accuracy, speedMps, bearing;

        Fix(long timeMs, double lat, double lon, double accuracy, double speedMps, double bearing) {
            this.timeMs = timeMs;
            this.lat = lat;
            this.lon = lon;
            this.accuracy = accuracy;
            this.speedMps = speedMps;
            this.bearing = bearing;
        }
    }

    TripMeter(double meters, long movingMs) {
        this.meters = finite(meters) ? Math.max(0, meters) : 0;
        this.movingMs = Math.max(0, movingMs);
    }

    void alert(int newLimit, boolean enabled) {
        newLimit = Math.max(20, Math.min(140, newLimit));
        if (newLimit != limit || enabled != chimeEnabled) {
            limit = newLimit;
            chimeEnabled = enabled;
            latched = false;
            overSamples = 0;
        }
    }

    /** True once on a sustained crossing; won't repeat until safely below the limit. */
    boolean accept(Fix fix) {
        if (!finite(fix.accuracy) || fix.accuracy < 0 || fix.accuracy > 35 || fix.timeMs < 0
                || fix.timeMs <= lastFix || !finite(fix.lat) || Math.abs(fix.lat) > 90
                || !finite(fix.lon) || Math.abs(fix.lon) > 180) return false;

        long interval = previous == null ? 0 : fix.timeMs - previous.timeMs;
        boolean linked = interval >= 400 && interval <= STALE_MS;
        double step = linked ? distanceMeters(previous.lat, previous.lon, fix.lat, fix.lon) : 0;
        double noiseFloor = linked ? Math.max(3, (fix.accuracy + previous.accuracy) * .35) : 3;
        boolean plausible = linked && step / (interval / 1000d) <= MAX_MPS;
        boolean reported = fix.speedMps >= 0 && fix.speedMps <= MAX_MPS;
        double nextKmh = reported ? fix.speedMps * 3.6
                : plausible ? (step >= noiseFloor ? step * 3600d / interval : 0) : -1;
        if (nextKmh >= MOVING_KMH) {
            if (fix.bearing >= 0 && fix.bearing <= 360) heading = Math.round((float) fix.bearing) % 360;
            else if (plausible && step >= noiseFloor) heading = course(previous, fix);
        }
        if (linked && nextKmh >= 0 && (nextKmh >= MOVING_KMH || kmh >= MOVING_KMH)) {
            // Use GNSS-reported ground speed when both fixes have it, especially at walking speeds.
            if (plausible && reported && kmh >= 0 && previous.speedMps >= 0 && previous.speedMps <= MAX_MPS) {
                meters += (nextKmh + kmh) * interval / 7200d;
                movingMs += interval;
            } else if (plausible && step >= noiseFloor) {
                meters += step;
                movingMs += interval;
            }
        }
        if (!linked || nextKmh < 0) overSamples = 0;
        previous = fix;
        lastFix = fix.timeMs;
        kmh = nextKmh;

        if (!chimeEnabled || kmh < 0) return false;
        if (kmh <= limit - 4) latched = false;
        if (kmh <= limit) { overSamples = 0; return false; }
        if (!latched && ++overSamples >= 2) {
            latched = true;
            return true;
        }
        return false;
    }

    boolean hasFix(long now) { return lastFix >= 0 && now >= lastFix && now - lastFix <= STALE_MS; }
    int speed(long now) { return hasFix(now) && kmh >= 0 ? (int) Math.round(kmh) : -1; }
    int heading(long now) { return hasFix(now) ? heading : -1; }
    boolean overLimit(long now) { return chimeEnabled && hasFix(now) && kmh > limit; }
    double meters() { return meters; }
    long movingMs() { return movingMs; }

    void pause() {
        previous = null;
        lastFix = -1;
        kmh = -1;
        heading = -1;
        overSamples = 0;
    }

    void reset() {
        meters = 0;
        movingMs = 0;
        previous = null;
    }

    static String compass(int degrees) {
        if (degrees < 0) return "—";
        final String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return names[((int) Math.round(degrees / 45d)) % 8];
    }

    static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return 6_371_000d * 2d * Math.asin(Math.sqrt(Math.max(0, Math.min(1, a))));
    }

    private static int course(Fix a, Fix b) {
        double x = Math.toRadians(a.lat), y = Math.toRadians(b.lat);
        double dLon = Math.toRadians(b.lon - a.lon);
        double angle = Math.toDegrees(Math.atan2(Math.sin(dLon) * Math.cos(y),
                Math.cos(x) * Math.sin(y) - Math.sin(x) * Math.cos(y) * Math.cos(dLon)));
        return (int) Math.round(angle + 360) % 360;
    }

    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
}
