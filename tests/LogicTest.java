package lk.vezel.console;

public final class LogicTest {
    public static void main(String[] args) {
        tripAndAlert();
        navigation();
        lights();
        registration();
        power();
        System.out.println("Trip, speed alert, notification, illumination, power cue and registration tests passed.");
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
        TripMeter equal = new TripMeter(0, 0);
        check(!equal.accept(fix(1000, 4, 60/3.6)) && equal.limitReached(1000), "Show the warning at the selected limit");
        check(equal.accept(fix(2000, 4, 60/3.6)), "Speak after two valid fixes at the threshold, not only above it");
        check(!equal.accept(fix(3000, 4, 58/3.6)) && !equal.limitReached(3000), "Hide visual warning below the threshold");
        check(!equal.accept(fix(4000, 4, 60/3.6)) && !equal.accept(fix(5000, 4, 60/3.6)), "Avoid a repeated warning during small fluctuations");
        check(!equal.limitReached(14000), "Hide warning when the GPS speed is stale");
    }

    private static void power() {
        PowerCue state = new PowerCue();
        check(state.update(null) == PowerCue.Event.NONE, "Ignore malformed ACC state");
        check(state.update(true) == PowerCue.Event.NONE, "Do not replay Welcome on duplicate ignition-on");
        check(state.update(LightSignal.read("OFF")) == PowerCue.Event.GOODBYE, "Key-off edge cues goodbye");
        check(state.update(false) == PowerCue.Event.NONE, "Suppress duplicate key-off");
        check(state.update(LightSignal.read(1)) == PowerCue.Event.WELCOME, "Key-on cues welcome after key-off");
        state.reset();
        check(state.update(true) == PowerCue.Event.NONE, "Wake transition resets the edge tracker");
        check(PowerCue.validAction("com.example.ACC_STATE") && !PowerCue.validAction("not an action"), "Validate the configured OEM action");
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

    private static void registration() {
        check(VehicleRegistration.normalize(" wp  abc 0123 ").equals("WP ABC 0123"),
                "Keep the province and leading zero in the personal label");
        check(VehicleRegistration.normalize("abc 0123").equals("ABC 0123"), "Province is optional");
        check(VehicleRegistration.normalize(" ").isEmpty() && VehicleRegistration.normalize(null).isEmpty(),
                "No label on generic installs");
        check(VehicleRegistration.normalize("<img>").isEmpty() && VehicleRegistration.normalize("WP ABC +123").isEmpty(),
                "Reject malformed labels");
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
