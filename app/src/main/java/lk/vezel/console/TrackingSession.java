package lk.vezel.console;

/** One GPS stream shared by the visible dashboard and the background trip service. */
final class TrackingSession {
    interface Source {
        boolean start();
        void stop();
    }

    enum Sample { IGNORED, UPDATED, WARNING }

    private final TripMeter trip;
    private final Source source;
    private boolean sampling;
    private long samplingSince;

    TrackingSession(TripMeter trip, Source source) {
        this.trip = trip;
        this.source = source;
    }

    boolean sampling() { return sampling; }

    void update(boolean gpsReady, boolean dashboardVisible, boolean backgroundActive, long now) {
        boolean wanted = gpsReady && (dashboardVisible || backgroundActive);
        if (wanted == sampling) return;
        if (wanted) {
            samplingSince = now;
            sampling = source.start();
        } else {
            sampling = false;
            source.stop();
            // Forget the last fix, not the totals. Never bridge a period without tracking.
            trip.pause();
        }
    }

    Sample accept(TripMeter.Fix fix, long now) {
        if (!sampling || fix.timeMs <= 0 || fix.timeMs < samplingSince || fix.timeMs > now || now - fix.timeMs > TripMeter.STALE_MS) {
            return Sample.IGNORED;
        }
        return trip.accept(fix) ? Sample.WARNING : Sample.UPDATED;
    }
}
