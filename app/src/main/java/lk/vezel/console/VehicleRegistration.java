package lk.vezel.console;

import java.util.Locale;
import java.util.regex.Pattern;

/** An optional dashboard label, separate from the unretouched photograph. */
final class VehicleRegistration {
    private static final Pattern LABEL = Pattern.compile("(?:[A-Z]{2} )?[A-Z0-9]{1,6} [A-Z0-9]{1,6}");

    private VehicleRegistration() {}

    static String normalize(String input) {
        if (input == null || input.length() > 64) return "";
        String label = input.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        return LABEL.matcher(label).matches() ? label : "";
    }
}
