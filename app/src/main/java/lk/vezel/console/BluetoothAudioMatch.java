package lk.vezel.console;

import java.util.Locale;
import java.util.regex.Pattern;

/** Match explicit music entries, not Android Bluetooth settings or pairing screens. */
final class BluetoothAudioMatch {
    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9]+");
    private static final Pattern AUDIO = Pattern.compile("(?:bluetooth|bt)(?:audio|music|media|player)");
    private static final Pattern A2DP = Pattern.compile("a2dp(?:sink|player|audio|music)?(?:activity|app)?");
    private static final Pattern NOT_PLAYER = Pattern.compile("settings?|pair(?:ing)?|phone|dial(?:er)?|config(?:uration)?|manager|test");

    static int score(String label, String component, boolean systemApp) {
        String name = normalize(label);
        // An explicit label wins over a class-name hint. Vendor firmware doesn't all use the same package.
        if (!NOT_PLAYER.matcher(name).find() && isAudio(name)) return 2;
        if (!systemApp || NOT_PLAYER.matcher(name).find()) return 0;
        String className = normalize(component.substring(component.lastIndexOf('.') + 1));
        return !NOT_PLAYER.matcher(className).find() && isAudio(className) ? 1 : 0;
    }

    private static boolean isAudio(String value) { return AUDIO.matcher(value).find() || A2DP.matcher(value).matches(); }

    private static String normalize(String value) { return NON_WORD.matcher(value.toLowerCase(Locale.ROOT)).replaceAll(""); }
}
