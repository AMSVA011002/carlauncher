package lk.vezel.console;

public final class LogicTest {
    public static void main(String[] args) {
        tripAndAlert();
        navigation();
        lights();
        System.out.println("Trip, speed alert, notification parsing and illumination tests passed.");
    }

    private static void tripAndAlert() {
        TripMeter meter = new TripMeter(0, 0);
        check(meter.speed(0) == -1, "No fabricated speed before a fix");
        check(!meter.accept(fix(1_000, 100, 19)), "Reject inaccurate coordinates");
        check(!meter.hasFix(1_000), "Inaccurate fix is not live");
        check(!meter.accept(fix(1_000, 4, 19)), "No warning for a single sample");
        check(meter.accept(fix(2_000, 4, 19)), "Warn after consecutive over-limit fixes");
        check(meter.speed(2_000) == 68 && meter.heading(2_000) == 296, "GNSS speed and compass bearing");
        check(TripMeter.compass(296).equals("NW") && TripMeter.compass(359).equals("N"), "Compass labels");
        check(Math.abs(meter.meters() - 19) < .001 && meter.movingMs() == 1_000, "Trip uses ground speed");
        check(!meter.accept(fix(3_000, 4, 19)), "No repeated warning above limit");
        check(!meter.accept(fix(4_000, 4, 16)), "Hysteresis just below limit");
        check(!meter.accept(fix(5_000, 4, 19)), "Warn only once until speed drops");
        check(!meter.accept(fix(6_000, 4, 10)), "Re-arm below limit band");
        check(!meter.accept(fix(7_000, 4, 19)) && meter.accept(fix(8_000, 4, 19)), "Can warn again");
        check(!meter.hasFix(17_000) && meter.speed(17_000) == -1 && meter.heading(17_000) == -1, "Stale GPS is cleared");
        double beforeGap = meter.meters();
        meter.pause();
        meter.accept(fix(41_000, 8, 17));
        check(meter.meters() == beforeGap, "No trip increment over a foreground gap");
        meter.reset();
        check(meter.meters() == 0 && meter.movingMs() == 0, "Reset trip");
        meter.alert(40, false);
        check(!meter.accept(fix(42_000, 4, 22)) && !meter.accept(fix(43_000, 4, 22)), "Disabled alert");
        TripMeter inferred = new TripMeter(0, 0);
        inferred.accept(new TripMeter.Fix(1_000, 6.9, 79.8, 4, -1, -1));
        check(inferred.speed(1_000) == -1, "No GPS speed or segment yet");
        inferred.accept(new TripMeter.Fix(2_000, 6.9001, 79.8, 4, -1, -1));
        check(inferred.speed(2_000) >= 39 && inferred.speed(2_000) <= 41 && inferred.heading(2_000) == 0,
                "Infer movement and heading from accurate displacement");
        check(inferred.meters() > 11 && inferred.meters() < 12 && inferred.movingMs() == 1_000,
                "Count the first reliable displacement in trip totals");
    }

    private static TripMeter.Fix fix(long ms, double accuracy, double speed) {
        return new TripMeter.Fix(ms, 6.913, 79.851, accuracy, speed, 296);
    }

    private static void navigation() {
        assertHint(NavigationText.parse("400m", "Turn right onto Galle Rd", "9:14 AM ETA · 6.2 km"),
                "400 m", "Turn right onto Galle Rd", "9:14 AM ETA", NavigationText.Arrow.RIGHT);
        assertHint(NavigationText.parse("In 1.2 km, keep left at the fork"),
                "1.2 km", "keep left at the fork", "", NavigationText.Arrow.LEFT);
        assertHint(NavigationText.parse("Turn left onto Duplication Rd in 250 m"),
                "250 m", "Turn left onto Duplication Rd", "", NavigationText.Arrow.LEFT);
        assertHint(NavigationText.parse("Turn right, 100 ft"), "100 ft", "Turn right", "", NavigationText.Arrow.RIGHT);
        assertHint(NavigationText.parse("600\u202fm • Continue straight"), "600 m", "Continue straight", "", NavigationText.Arrow.STRAIGHT);
        assertHint(NavigationText.parse("In 10 m, at the roundabout take the second exit"), "10 m",
                "at the roundabout take the second exit", "", NavigationText.Arrow.OTHER);
        assertHint(NavigationText.withSummary(NavigationText.parse("Head southwest on Galle Rd"), "6 km", "ETA 17:04"),
                "", "Head southwest on Galle Rd", "17:04 ETA", NavigationText.Arrow.OTHER);
        assertHint(NavigationText.parse("Turn left, then turn right in 400 m"), "", "Turn left", "", NavigationText.Arrow.LEFT);
        assertHint(NavigationText.parse("120 m · Turn left, then turn right in 400 m"), "120 m", "Turn left", "", NavigationText.Arrow.LEFT);
        check(NavigationText.parse("20 min · 11 km", "Looking for GPS") == null, "No fake turn from trip summary");
        check(NavigationText.parse("Turn on notifications", "400 m") == null, "No fake turn from a promo");
        check(NavigationText.parse("400 m · Galle Rd") == null, "Do not guess a maneuver from road name only");
    }

    private static void assertHint(NavigationText.Hint hint, String distance, String text, String detail, NavigationText.Arrow arrow) {
        check(hint != null && hint.distance.equals(distance) && hint.instruction.equals(text)
                && hint.detail.equals(detail) && hint.arrow == arrow, "Navigation matching: " + text);
    }

    private static void lights() {
        check(Boolean.TRUE.equals(LightSignal.read(" ON ")), "OEM string on");
        check(Boolean.FALSE.equals(LightSignal.read(0)), "OEM numeric off");
        check(LightSignal.read(0.3) == null && LightSignal.read("maybe") == null, "Ignore unexpected signals");
        check(LightSignal.enabled(0, null, true), "System night fallback");
        check(!LightSignal.enabled(0, false, true), "OEM source wins over system appearance");
        check(LightSignal.enabled(1, false, false) && !LightSignal.enabled(2, true, true), "Manual override");
        check(LightSignal.night(0, true, false) && !LightSignal.night(2, true, true), "Auto and manual night");
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
