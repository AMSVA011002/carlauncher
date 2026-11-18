package lk.vezel.console;

import java.util.Locale;

/** Firmware signals only affect the launcher illustration, never the physical lights. */
final class LightSignal {
    static Boolean read(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (number == 0) return false;
            if (number == 1) return true;
        }
        if (value instanceof String) {
            switch (((String) value).trim().toLowerCase(Locale.ROOT)) {
                case "1": case "true": case "on": return true;
                case "0": case "false": case "off": return false;
                default: break;
            }
        }
        return null;
    }

    static boolean enabled(int selection, Boolean firmware, boolean systemNight) {
        return selection == 1 || (selection == 0 && (firmware != null ? firmware : systemNight));
    }

    static boolean night(int selection, boolean lights, boolean systemNight) {
        return selection == 1 || (selection == 0 && (lights || systemNight));
    }
}
