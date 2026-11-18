package lk.vezel.console;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative English-language parsing of public navigation notification fields. */
final class NavigationText {
    private static final String DISTANCE = "[0-9]{1,5}(?:[.,][0-9]{1,3})?\\s*(?:km|m|mi|ft|yd|meters?|metres?|miles?|kilometers?|kilometres?|feet|yards?)";
    private static final Pattern JUST_DISTANCE = Pattern.compile("(?i)^" + DISTANCE + "$");
    private static final Pattern BEFORE = Pattern.compile("(?i)^(?:in\\s+)?(" + DISTANCE + ")(?=$|[\\s,.:·•|—-])(?:[\\s,.:·•|—-]*)");
    private static final Pattern FOLLOWUP = Pattern.compile("(?i)(?:\\s*,\\s*|\\s+)then\\b");
    private static final Pattern SEPARATOR = Pattern.compile("\\s*[·•|]\\s*");
    private static final Pattern AFTER = Pattern.compile("(?i)(?:[,·•|—]\\s*(?:in\\s+)?|\\s+(?:in|after)\\s+)(" + DISTANCE + ")\\s*$");
    private static final Pattern COMMAND = Pattern.compile("(?i)^(?:"
            + "(?:turn|bear|keep|stay|merge|slight(?:ly)?)\\s+(?:(?:slight(?:ly)?|sharp(?:ly)?)\\s+)?(?:left|right)\\b"
            + "|(?:continue|go|proceed)\\s+(?:straight|ahead|on|onto)\\b"
            + "|head\\s+(?:north(?:east|west)?|south(?:east|west)?|east|west|toward|towards|on)\\b"
            + "|at\\s+(?:the\\s+)?roundabout\\b|enter\\s+(?:the\\s+)?roundabout\\b"
            + "|(?:take|use)\\s+(?:the\\s+)?(?:[0-9]+(?:st|nd|rd|th)?\\s+)?(?:exit|left|right|ramp)\\b"
            + "|exit\\s+(?:left|right|onto|toward|towards|[0-9])|merge\\s+onto\\b"
            + "|make\\s+(?:a\\s+)?u[ -]?turn\\b|u[ -]?turn\\b)");
    private static final Pattern SUMMARY = Pattern.compile("(?i)\\b(?:eta|remaining|arrival|arrive\\s+at|[0-9]{1,2}:[0-9]{2}\\s*[ap]m)\\b");
    private static final Pattern DETAIL_TIME = Pattern.compile("(?i)\\b([0-9]{1,2}:[0-9]{2}(?:\\s*(?:am|pm))?)\\b");

    enum Arrow { LEFT, RIGHT, STRAIGHT, OTHER }

    static final class Hint {
        final String distance, instruction, detail;
        final Arrow arrow;

        Hint(String distance, String instruction, String detail, Arrow arrow) {
            this.distance = distance;
            this.instruction = instruction;
            this.detail = detail;
            this.arrow = arrow;
        }
    }

    static Hint parse(String... fields) {
        if (fields == null) return null;
        String distance = "", instruction = "", inlineDistance = "", detail = "";
        for (String field : fields) {
            if (field == null) continue;
            for (String part : field.substring(0, Math.min(600, field.length())).split("[\\r\\n]+")) {
                String line = clean(part);
                if (line.isEmpty()) continue;
                if (detail.isEmpty()) detail = eta(line);
                if (JUST_DISTANCE.matcher(line).matches()) {
                    if (distance.isEmpty()) distance = unitSpace(line);
                    continue;
                }
                // A distance from a subsequent maneuver must not be assigned to the first turn.
                line = FOLLOWUP.split(line, 2)[0].trim();
                String candidate = line;
                String found = "";
                Matcher first = BEFORE.matcher(line);
                if (first.find()) {
                    found = unitSpace(first.group(1));
                    candidate = line.substring(first.end()).trim();
                } else {
                    Matcher last = AFTER.matcher(line);
                    if (last.find()) {
                        found = unitSpace(last.group(1));
                        candidate = line.substring(0, last.start()).trim();
                    }
                }
                candidate = SEPARATOR.split(candidate, 2)[0].trim();
                if (COMMAND.matcher(candidate).find() && !candidate.isEmpty()) {
                    if (!instruction.isEmpty() && (found.isEmpty() || !inlineDistance.isEmpty())) continue;
                    instruction = candidate;
                    inlineDistance = found;
                }
            }
        }
        if (instruction.isEmpty()) return null;
        if (!inlineDistance.isEmpty()) distance = inlineDistance;
        String lower = instruction.toLowerCase(Locale.ROOT);
        Arrow arrow;
        if (lower.contains("roundabout") || lower.contains("turn around") || lower.contains("u-turn")) arrow = Arrow.OTHER;
        else if (lower.matches("^(?:(?:turn|bear|keep|stay|merge|slight(?:ly)?|exit)\\s+(?:(?:slight(?:ly)?|sharp(?:ly)?)\\s+)?|take\\s+(?:the\\s+)?)left\\b.*")) arrow = Arrow.LEFT;
        else if (lower.matches("^(?:(?:turn|bear|keep|stay|merge|slight(?:ly)?|exit)\\s+(?:(?:slight(?:ly)?|sharp(?:ly)?)\\s+)?|take\\s+(?:the\\s+)?)right\\b.*")) arrow = Arrow.RIGHT;
        else if (lower.matches("^(?:continue|go|proceed)\\b.*")) arrow = Arrow.STRAIGHT;
        else arrow = Arrow.OTHER;
        return new Hint(distance, instruction, detail, arrow);
    }

    /** A subtitle can supply an ETA, but never a next-turn distance on its own. */
    static Hint withSummary(Hint hint, String... summaries) {
        if (hint == null || !hint.detail.isEmpty()) return hint;
        for (String summary : summaries) {
            if (summary == null) continue;
            String detail = eta(clean(summary.substring(0, Math.min(summary.length(), 600))));
            if (!detail.isEmpty()) return new Hint(hint.distance, hint.instruction, detail, hint.arrow);
        }
        return hint;
    }

    private static String eta(String text) {
        if (!SUMMARY.matcher(text).find()) return "";
        Matcher time = DETAIL_TIME.matcher(text);
        return time.find() ? time.group(1).toUpperCase(Locale.ROOT) + " ETA" : "";
    }

    private static String clean(String text) {
        return text.replaceAll("[\\u200b-\\u200f\\u202a-\\u202e\\u2066-\\u2069]", "")
                .replaceAll("[\\s\\u00a0\\u202f]+", " ").trim();
    }

    private static String unitSpace(String value) { return value.replaceFirst("(?<=\\d)\\s*(?=[A-Za-z])", " "); }
}
