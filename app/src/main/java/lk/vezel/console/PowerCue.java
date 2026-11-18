package lk.vezel.console;

/** Edge tracker for a configured ignition-state signal, never a command to change system power. */
final class PowerCue {
    enum Event { NONE, WELCOME, GOODBYE }
    private boolean ignitionOn = true;

    Event update(Boolean on) {
        if (on == null || on == ignitionOn) return Event.NONE;
        ignitionOn = on;
        return on ? Event.WELCOME : Event.GOODBYE;
    }

    void reset() { ignitionOn = true; }

    static boolean validAction(String value) { return value.matches("[A-Za-z_][A-Za-z0-9_.]{2,159}"); }
    static boolean validExtra(String value) { return value.matches("[A-Za-z0-9_.:$-]{1,128}"); }
}
