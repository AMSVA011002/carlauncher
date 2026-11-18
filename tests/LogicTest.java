package lk.vezel.console;

public final class LogicTest {
    public static void main(String[] args) {
        tripAndAlert();
        gauge();
        bluetoothAudio();
        lights();
        registration();
        power();
        System.out.println("Trip, speed alert, gauge, BT Audio, illumination, power cue and registration tests passed.");
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

    private static void gauge() {
        TripMeter meter = new TripMeter(0, 0);
        meter.accept(fix(1_000, 4, 25));
        check(meter.speed(1_000) == 90 && GaugeScale.angle(meter.speed(1_000)) == 270f,
                "A real 90 km/h GPS reading points straight up at the midpoint");
        check(GaugeScale.angle(0) == 138f && GaugeScale.angle(180) == 402f, "Needle agrees with the printed zero and maximum");
        check(GaugeScale.angle(1_000) == GaugeScale.angle(180), "Clamp the needle, not the actual GPS number");
        check(GaugeScale.progress(-1) == 0f && meter.speed(20_000) == -1, "Stale fixes remain unavailable");
    }

    private static void bluetoothAudio() {
        check(BluetoothAudioMatch.score("BT Audio", "oem.player.Home", true) == 2, "Recognize a dedicated BT Audio label");
        check(BluetoothAudioMatch.score("Bluetooth-Music", "player.Home", false) == 2, "Recognize a media app without a package allowlist");
        check(BluetoothAudioMatch.score("Bluetooth", "oem.bluetooth.BtMusicActivity", true) == 1, "Discover a dedicated vendor music entry");
        check(BluetoothAudioMatch.score("Bluetooth", "oem.bluetooth.ConnectActivity", true) == 0, "Do not guess a generic Bluetooth entry is audio");
        check(BluetoothAudioMatch.score("Bluetooth Audio Settings", "oem.bluetooth.BtMusicSettings", true) == 0, "Exclude Bluetooth config");
        check(BluetoothAudioMatch.score("Phone", "oem.bluetooth.Dialer", true) == 0, "Exclude hands-free phone");
        check(BluetoothAudioMatch.score("Bluetooth", "oem.bluetooth.A2dpConnectActivity", true) == 0, "Do not select an A2DP connection UI");
        check(BluetoothAudioMatch.score("Other app", "other.BtMusicActivity", false) == 0, "Only trust class-name hints for system apps");
    }

    private static void lights() {
        check(Boolean.TRUE.equals(LightSignal.read(" ON ")), "OEM string on");
        check(Boolean.FALSE.equals(LightSignal.read(0)), "OEM numeric off");
        check(LightSignal.read(0.3) == null && LightSignal.read("maybe") == null, "Ignore unexpected signals");
        check(!LightSignal.validCalibration(70, 70) && !LightSignal.validCalibration(50, 88), "Reject identical or reversed readings");
        check(!LightSignal.validCalibration(255, -1) && !LightSignal.validCalibration(256, 44), "Reject unavailable or out-of-range settings");
        check(!LightSignal.validCalibration(74, 70) && LightSignal.validCalibration(75, 70), "Require distinct dimming");
        check(Boolean.FALSE.equals(LightSignal.factoryState(175, 50, 175)), "Lights off returns daylight at cold start or wake");
        check(Boolean.TRUE.equals(LightSignal.factoryState(175, 50, 50)), "Lights on recognizes the learned dim level");
        check(Boolean.FALSE.equals(LightSignal.factoryState(175, 50, 170)) && Boolean.TRUE.equals(LightSignal.factoryState(175, 50, 54)),
                "Small reporting differences still match the right state");
        check(LightSignal.factoryState(175, 50, 128) == null && LightSignal.factoryState(175, 50, -1) == null,
                "Do not interpret a generic brightness change or missing read as headlights");
        check(!LightSignal.automatic(null, true, true), "Do not substitute an unrelated night theme for an unmatched learned signal");
        check(LightSignal.automatic(null, false, true) && !LightSignal.automatic(null, false, false), "Android theme fallback without calibration");
        check(!LightSignal.automatic(false, false, true), "Explicit OEM OFF wins over a night theme");
        check(LightSignal.automatic(true, false, false), "Explicit OEM ON wins over a day theme");
        boolean dimmed = LightSignal.automatic(LightSignal.factoryState(175, 50, 50), true, false);
        boolean bright = LightSignal.automatic(LightSignal.factoryState(175, 50, 175), true, true);
        check(LightSignal.enabled(0, dimmed) && !LightSignal.enabled(0, bright), "Auto photo and palette follow the same reading");
        check(LightSignal.enabled(1, bright) && !LightSignal.enabled(2, dimmed), "Manual choices override automatic sync independently");
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
