package lk.vezel.console;

/** The factory lighting choice changes UI colors, not the head unit's backlight. */
final class ConsolePalette {
    static final ConsolePalette DAY = new ConsolePalette(
            0xffeee5da, 0xfff9f5ef, 0xfff1e9df, 0xffd7c8ba, 0xff332831, 0xff6d5b5f,
            0xff8c5236, 0xffeddfd1, 0xffc9ab97, 0xff663624,
            0xff285b48, 0xffe3eee6, 0xffa9c6b3,
            0xff893829, 0xfff7e3d8, 0xffce9688);
    static final ConsolePalette NIGHT = new ConsolePalette(
            0xff101017, 0xff1a1922, 0xff15141d, 0xff3d343f, 0xfff4eae7, 0xffb7a7ae,
            0xffeab18b, 0xff3f2b2b, 0xff775144, 0xfff5c8aa,
            0xffabd6be, 0xff1c2c26, 0xff456256,
            0xffffc08b, 0xff452623, 0xff9b5d47);

    final int base, panel, inset, edge, ink, muted, accent, selected, selectedEdge, selectedInk;
    final int good, goodBg, goodEdge, warning, warningBg, warningEdge;

    static ConsolePalette forNight(boolean night) { return night ? NIGHT : DAY; }

    private ConsolePalette(int base, int panel, int inset, int edge, int ink, int muted,
                           int accent, int selected, int selectedEdge, int selectedInk,
                           int good, int goodBg, int goodEdge, int warning, int warningBg, int warningEdge) {
        this.base = base;
        this.panel = panel;
        this.inset = inset;
        this.edge = edge;
        this.ink = ink;
        this.muted = muted;
        this.accent = accent;
        this.selected = selected;
        this.selectedEdge = selectedEdge;
        this.selectedInk = selectedInk;
        this.good = good;
        this.goodBg = goodBg;
        this.goodEdge = goodEdge;
        this.warning = warning;
        this.warningBg = warningBg;
        this.warningEdge = warningEdge;
    }
}
