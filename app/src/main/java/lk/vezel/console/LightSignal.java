package lk.vezel.console;

import java.util.Locale;

/** Interprets advisory inputs for the photo and dashboard palette, not the real lights. */
final class LightSignal {
    static final int UNAVAILABLE = -1, MAX_LEVEL = 255;
    static final int MIN_DIM_CHANGE = 5;

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

    static boolean validLevel(int level) { return level >= 0 && level <= MAX_LEVEL; }

    static boolean validCalibration(int day, int dim) {
        return validLevel(day) && validLevel(dim) && day - dim >= MIN_DIM_CHANGE;
    }

    /** A generic manual/adaptive brightness change is not proof that the lamps switched. */
    static Boolean factoryState(int day, int dim, int level) {
        if (!validCalibration(day, dim) || !validLevel(level)) return null;
        int tolerance = Math.min(10, (day - dim) / 3);
        if (Math.abs(level - dim) <= tolerance) return true;
        if (Math.abs(level - day) <= tolerance) return false;
        return null;
    }

    static boolean automatic(Boolean reported, boolean calibrated, boolean systemNight) {
        return reported != null ? reported : !calibrated && systemNight;
    }

    static boolean enabled(int selection, boolean automatic) {
        return selection == 1 || (selection == 0 && automatic);
    }
}
