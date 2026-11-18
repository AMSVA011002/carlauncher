package lk.vezel.console;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;

import java.io.IOException;
import java.io.InputStream;

/** Fixed head-unit design coordinates, scaled uniformly. Real focusable Views sit over the artwork. */
final class DashboardView extends ViewGroup {
    static final int W = 1024, H = 600;
    static final int STARTUP = 1, WEATHER = 2, NIGHT = 3, GPS = 4, LIMIT = 5, LAMPS = 7,
            MEDIA = 8, PREVIOUS = 9, PLAY = 10, NEXT = 11, BT_AUDIO = 12, MUSIC = 13,
            PHONE = 14, RADIO = 15, SETTINGS = 16, APPS = 17, READ_SPEED = 18, READ_CLOCK = 19, SETUP = 20;
    private static final int READ_VEHICLE = 21;
    private static final String[] SHORTCUTS = {"BT Audio", "Music", "Phone", "Radio", "Settings", "Apps"};
    private static final int[] SHORTCUT_ICONS = {R.drawable.ic_bluetooth, R.drawable.ic_music, R.drawable.ic_phone,
            R.drawable.ic_radio, R.drawable.ic_settings, R.drawable.ic_apps};

    static final class Data {
        boolean night, lamps, over, dialGauge, speedAlert = true, playing, setupNeeded = true;
        int speed = -1, limit = 60, heading = -1, weatherIcon = R.drawable.ic_weather;
        String clock = "--:--", period = "", date = "", trip = "0.0", drive = "00:00";
        String gps = "AWAITING GPS", temperature = "—", place = "Weather", registration = "";
        String track = "No media playing", artist = "Tap to set up media";
        Bitmap art;
    }

    interface Actions { void call(int action); }

    final Data data = new Data();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Typeface basic = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface bold = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface mono = Typeface.create("monospace", Typeface.NORMAL);
    private final Typeface instrument;
    private final SparseArray<Drawable> icons = new SparseArray<>();
    private final SparseArray<Target> hit = new SparseArray<>();
    private final RectF box = new RectF();
    private static final float DIAL_X = 156f, DIAL_Y = 247f;
    private final RectF dialFace = new RectF(42, 133, 270, 361), dialArc = new RectF(58, 149, 254, 345);
    private final Path stage = roundedPath(303, 93, 999, 486, 16), cover = roundedPath(36, 514, 84, 562, 8);
    private final Path needle = new Path();
    private final Shader dayBackdrop = gradient(20, 0, W, H, 0xfff5ede2, 0xffe8ded5),
            nightBackdrop = gradient(20, 0, W, H, 0xff19151f, 0xff101118),
            dayHeader = gradient(303, 93, 999, 165, 0xfffbf6ee, 0xffeee2d5),
            nightHeader = gradient(303, 93, 999, 165, 0xff2d2029, 0xff191720),
            buttonFill = gradient(304, 518, 336, 557, 0xffffcfaa, 0xffe29863),
            markFill = gradient(26, 19, 68, 61, 0xff412c27, 0xff22191e),
            swatch = gradient(321, 444, 342, 469, 0xffad5c70, 0xff441321),
            dialDayArc = gradient(58, 345, 254, 149, 0xff2777aa, 0xff0197ba),
            dialNightArc = gradient(58, 345, 254, 149, 0xff428dea, 0xff72ecff),
            dayNeedle = gradient(0, -3.5f, 0, 3.5f, 0xffee6947, 0xffa52224),
            nightNeedle = gradient(0, -3.5f, 0, 3.5f, 0xffffa15a, 0xffee3628);
    private final Drawable badge;
    private final PowerScreen powerScreen;
    private boolean starting, photoLit, gaugeNight;
    private Bitmap photo, gauge;
    private float scale = 1, offsetX, offsetY;

    DashboardView(Context context, Actions actions) {
        super(context);
        setWillNotDraw(false);
        setClipChildren(false);
        Typeface font;
        try { font = Typeface.createFromAsset(context.getAssets(), "fonts/instrument.ttf"); }
        catch (RuntimeException ignored) { font = bold; }
        instrument = font;
        badge = context.getDrawable(R.drawable.honda_badge);
        needle.moveTo(-17, -2.7f);
        needle.lineTo(73, -1.7f);
        needle.lineTo(83, 0);
        needle.lineTo(73, 1.7f);
        needle.lineTo(-17, 3.6f);
        needle.close();
        target(STARTUP, 24, 11, 153, 66, "Replay Honda startup", actions);
        target(READ_CLOCK, 458, 11, 566, 66, "Clock", null);
        target(SETUP, 748, 14, 829, 63, "Complete launcher setup", actions);
        target(WEATHER, 834, 12, 943, 65, "Weather", actions);
        target(NIGHT, 951, 13, 1000, 64, "Night display", actions);
        target(GPS, 179, 96, 278, 133, "Set up GPS", actions);
        target(READ_SPEED, 42, 133, 270, 359, "GPS speed", null);
        target(LIMIT, 48, 361, 264, 401, "Set speed alert", actions);
        target(READ_VEHICLE, 304, 161, 998, 435, "Honda Vezel Hybrid RU3 in Wine Red", null);
        target(LAMPS, 816, 438, 985, 485, "Headlights and fog lights in the photo", actions);
        target(MEDIA, 34, 508, 252, 571, "Music information", actions);
        target(PREVIOUS, 251, 510, 295, 565, "Previous track", actions);
        target(PLAY, 295, 510, 342, 565, "Play", actions);
        target(NEXT, 342, 510, 386, 565, "Next track", actions);
        for (int i = 0; i < SHORTCUTS.length; i++) {
            float left = 411 + i * 97.7f;
            target(BT_AUDIO + i, left, 507, left + 94.7f, 569, SHORTCUTS[i], actions);
        }
        powerScreen = new PowerScreen(context);
        powerScreen.setVisibility(GONE);
        addView(powerScreen);
        updateTargets();
    }

    private void target(int key, float l, float t, float r, float b, String name, Actions actions) {
        Target view = new Target(getContext(), new RectF(l, t, r, b));
        view.description(name);
        if (actions != null) {
            view.setOnClickListener(v -> actions.call(key));
            view.setFocusable(true);
        }
        hit.put(key, view);
        addView(view);
    }

    void refresh() {
        if (!starting) {
            hit.get(SETUP).setVisibility(data.setupNeeded ? VISIBLE : INVISIBLE);
            hit.get(READ_SPEED).description((data.speed < 0 ? "GPS speed unavailable" : data.speed + " kilometres per hour")
                    + ", trip " + data.trip + " kilometres, drive time " + data.drive
                    + (data.heading >= 0 ? ", heading " + TripMeter.compass(data.heading) + " " + data.heading + " degrees" : "")
                    + (data.over ? ", speed limit reached" : ""));
            hit.get(READ_CLOCK).description(data.clock + " " + data.period + ", " + data.date);
            hit.get(GPS).description(data.gps + ", GPS settings");
            hit.get(NIGHT).description(data.night ? "Switch to day display" : "Switch to night display");
            hit.get(LAMPS).description(data.lamps ? "Headlights and fog lights illustration on. Tap to turn off" : "Headlights and fog lights illustration off. Tap to turn on");
            hit.get(WEATHER).description("Weather: " + data.temperature + " " + data.place);
            hit.get(LIMIT).description(data.speedAlert ? "Speed alert: " + data.limit + " kilometres per hour" : "Speed alert off. Tap for settings");
            hit.get(PLAY).description(data.playing ? "Pause" : "Play");
            hit.get(MEDIA).description(data.track + ", " + data.artist);
            hit.get(READ_VEHICLE).description("Honda Vezel Hybrid RU3 in Wine Red" +
                    (data.registration.isEmpty() ? "" : ", registration " + data.registration));
        }
        invalidate();
    }

    private void updateTargets() {
        for (int i = 0; i < hit.size(); i++) {
            boolean visible = !starting && (hit.keyAt(i) != SETUP || data.setupNeeded);
            hit.valueAt(i).setVisibility(visible ? VISIBLE : INVISIBLE);
        }
    }

    void showPowerScreen(boolean leaving) {
        powerScreen.animate().cancel();
        powerScreen.setAlpha(1f);
        powerScreen.leaving = leaving;
        powerScreen.setContentDescription(leaving ? "Honda. Good Bye." : "Honda. Welcome.");
        starting = true;
        updateTargets();
        powerScreen.setVisibility(VISIBLE);
        powerScreen.invalidate();
        powerScreen.animate().alpha(0f).setStartDelay(leaving ? 2_500L : 2_220L)
                .setDuration(380L).withEndAction(this::hidePowerScreen).start();
    }

    boolean showingGoodbye() { return starting && powerScreen.leaving; }

    boolean hidePowerScreen() {
        if (!starting) return false;
        starting = false;
        powerScreen.animate().cancel();
        powerScreen.setVisibility(GONE);
        updateTargets();
        refresh();
        return true;
    }

    void trimCarCache() { photo = null; gauge = null; }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
        setMeasuredDimension(w, h);
        scale = Math.min(w / (float) W, h / (float) H);
        offsetX = (w - W * scale) / 2;
        offsetY = (h - H * scale) / 2;
        for (int i = 0; i < hit.size(); i++) {
            Target v = hit.valueAt(i);
            v.measure(exact(Math.round(v.area.width() * scale)), exact(Math.round(v.area.height() * scale)));
        }
        powerScreen.measure(exact(w), exact(h));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < hit.size(); i++) {
            Target v = hit.valueAt(i);
            int x = Math.round(offsetX + v.area.left * scale), y = Math.round(offsetY + v.area.top * scale);
            v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
        }
        powerScreen.layout(0, 0, r - l, b - t);
    }

    private ConsolePalette palette() { return ConsolePalette.forNight(data.night); }

    @Override protected void onDraw(Canvas canvas) {
        ConsolePalette p = palette();
        canvas.drawColor(p.base);
        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);
        canvas.clipRect(0, 0, W, H);
        round(canvas, 0, 0, W, H, 0, p.base, data.night ? nightBackdrop : dayBackdrop);
        header(canvas, p);
        instruments(canvas, p);
        vehicle(canvas, p);
        dock(canvas, p);
        letter(canvas, "RU3 EDITION  /  HONDA VEZEL", 25, 591, 8, mono, p.muted, .7f);
        right(canvas, "PHOTO EDITION   /   1024 × 600", 999, 591, 8, mono, p.muted);
        canvas.restore();
    }

    private void header(Canvas c, ConsolePalette p) {
        round(c, 24, 17, 67, 60, 12, p.ink, markFill);
        stroke(c, 24.5f, 17.5f, 66.5f, 59.5f, 12, 0xff765742, 1);
        drawBadge(c, 45.5f, 38.5f, 28);
        letter(c, "VEZEL", 79, 39, 19, instrument, p.ink, 3.2f);
        letter(c, "DRIVE CONSOLE", 80, 52, 8.5f, mono, p.muted, 1.05f);
        float clockWidth = width(data.clock, 27, instrument), periodWidth = width(data.period, 10, mono);
        float clockLeft = 512 - (clockWidth + (data.period.isEmpty() ? 0 : periodWidth + 5)) / 2;
        text(c, data.clock, clockLeft, 42, 27, instrument, p.ink);
        text(c, data.period, clockLeft + clockWidth + 5, 42, 10, mono, p.accent);
        center(c, data.date, 512, 58, 9, mono, p.muted);
        if (data.setupNeeded) {
            panel(c, 759, 26, 819, 50, 6, p.selected, p.selectedEdge);
            circle(c, 769, 38, 2, p.accent);
            letter(c, "SETUP", 777, 41, 9, mono, p.selectedInk, .75f);
        }
        icon(c, data.weatherIcon, 843, 24, 25, p.accent);
        text(c, data.temperature, 877, 37, 19, instrument, p.ink);
        text(c, trim(data.place, 62, 10, basic), 877, 51, 10, basic, p.muted);
        line(c, 947, 25, 947, 52, 1, p.edge);
        panel(c, 958, 17, 1000, 60, 11, data.night ? p.selected : p.panel, data.night ? p.selectedEdge : p.edge);
        icon(c, data.night ? R.drawable.ic_sun : R.drawable.ic_moon, 970, 29, 18, p.accent);
        line(c, 24, 76, 1000, 76, 1, p.edge);
    }

    private void instruments(Canvas c, ConsolePalette p) {
        panel(c, 24, 92, 288, 487, 17, p.panel, p.edge);
        letter(c, "GPS SPEED", 42, 119, 10, mono, p.muted, 1.05f);
        boolean fixed = "GPS FIX".equals(data.gps);
        float pillWidth = Math.max(62, width(data.gps, 8.5f, mono) + 20), pillLeft = 270 - pillWidth;
        panel(c, pillLeft, 107, 270, 127, 5, fixed ? p.goodBg : p.inset, fixed ? p.goodEdge : p.edge);
        circle(c, pillLeft + 8, 117, 2.4f, fixed ? p.good : p.accent);
        text(c, data.gps, pillLeft + 14, 120, 8.5f, mono, fixed ? p.good : p.muted);
        String value = data.speed < 0 ? "—" : Integer.toString(data.speed);
        if (data.dialGauge) drawDial(c, value, p);
        else drawSpeedRail(c, value, p);
        if (data.over) {
            panel(c, 48, 362, 264, 400, 8, p.warningBg, p.warningEdge);
            icon(c, R.drawable.ic_warning, 61, 370, 22, p.warning);
            letter(c, "LIMIT REACHED", 96, 379, 10, mono, p.warning, .65f);
            text(c, data.limit + " km/h  ·  Slow down", 96, 392, 10, bold, p.warning);
        } else {
            circle(c, 116, 382, 12f, 0xffaf5149);
            circle(c, 116, 382, 10f, 0xfffbf3eb);
            center(c, data.speedAlert ? Integer.toString(data.limit) : "–", 116, 386, 11, instrument, 0xff291d21);
            letter(c, "SPEED ALERT", 135, 385, 8.5f, mono, p.muted, .8f);
        }
        line(c, 42, 405, 270, 405, 1, p.edge);
        line(c, 156, 414, 156, 444, 1, p.edge);
        letter(c, "TRIP DISTANCE", 43, 420, 8.5f, mono, p.muted, .55f);
        letter(c, "DRIVE TIME", 170, 420, 8.5f, mono, p.muted, .55f);
        String trip = trim(data.trip, 84, 21, instrument);
        text(c, trip, 42, 443, 21, instrument, p.ink);
        text(c, "km", 46 + width(trip, 21, instrument), 443, 10, instrument, p.muted);
        text(c, trim(data.drive, 100, 21, instrument), 170, 443, 21, instrument, p.ink);
        panel(c, 42, 452, 270, 477, 7, p.inset, p.edge);
        icon(c, R.drawable.ic_compass, 51, 458, 13, p.accent);
        letter(c, "HEADING", 73, 468, 8.5f, mono, p.muted, .6f);
        String degrees = data.heading < 0 ? "—" : data.heading + "°";
        right(c, degrees, 261, 468.5f, 11.5f, instrument, p.muted);
        if (data.heading >= 0) right(c, TripMeter.compass(data.heading), 256 - width(degrees, 11.5f, instrument), 468.5f, 11.5f, instrument, p.ink);
    }

    private void drawSpeedRail(Canvas c, String value, ConsolePalette p) {
        int readout = data.over ? p.warning : p.ink;
        text(c, value, 40, 273, data.speed >= 100 ? 98 : 115, instrument, readout);
        text(c, "km/h", 44, 300, 15, instrument, p.muted);
        int lit = Math.round(GaugeScale.progress(data.speed) * 27);
        for (int i = 0; i < 27; i++) {
            float l = 42 + i * 8.47f;
            round(c, l, 318, l + 5.7f, 326, 1.7f, i >= lit ? p.edge : data.over ? p.warning : p.accent, null);
        }
    }

    private void drawDial(Canvas c, String value, ConsolePalette p) {
        if (gauge == null || gaugeNight != data.night) {
            gauge = loadBitmap(data.night ? "dial-night.webp" : "dial-day.webp");
            gaugeNight = data.night;
        }
        if (gauge == null) { drawSpeedRail(c, value, p); return; }
        prepare(Color.WHITE, Paint.Style.FILL, 0, null);
        c.drawBitmap(gauge, null, dialFace, paint);
        if (data.speed >= 0) {
            float sweep = GaugeScale.angle(data.speed) - GaugeScale.START;
            if (sweep > 0) {
                if (data.night) {
                    prepare(0x4040c7fa, Paint.Style.STROKE, 6, null);
                    c.drawArc(dialArc, GaugeScale.START, sweep, false, paint);
                }
                prepare(Color.WHITE, Paint.Style.STROKE, 2.8f, data.night ? dialNightArc : dialDayArc);
                c.drawArc(dialArc, GaugeScale.START, sweep, false, paint);
            }
            c.save();
            c.translate(DIAL_X, DIAL_Y);
            c.rotate(GaugeScale.angle(data.speed));
            if (data.night) line(c, -10, 0, 81, 0, 7, 0x3dff684c);
            prepare(Color.WHITE, Paint.Style.FILL, 0, data.night ? nightNeedle : dayNeedle);
            c.drawPath(needle, paint);
            line(c, -12, -.8f, 72, -.8f, 1, 0x99ffe2b6);
            c.restore();
        }
        circle(c, DIAL_X, DIAL_Y, 11, data.night ? 0xff8eaab8 : 0xff6c8396);
        circle(c, DIAL_X, DIAL_Y, 8.4f, data.night ? 0xff172038 : 0xffc8d5dd);
        circle(c, DIAL_X, DIAL_Y, 3.6f, data.night ? 0xffcae5f2 : 0xff4a647d);
        center(c, value, DIAL_X, 297, 43, instrument,
                data.over ? (data.night ? 0xffffb897 : 0xff983d29) : data.night ? 0xffe7f6fd : 0xff183551);
        center(c, "km/h", DIAL_X, 315, 11, mono, data.night ? 0xffa2c7dd : 0xff3b5a73);
    }

    private void vehicle(Canvas c, ConsolePalette p) {
        c.save();
        c.clipPath(stage);
        round(c, 302, 92, 1000, 487, 0, p.panel, null);
        round(c, 302, 92, 1000, 160, 0, p.panel, data.night ? nightHeader : dayHeader);
        drawPhoto(c);
        line(c, 303, 436, 999, 436, 1, p.edge);
        c.restore();
        stroke(c, 302.5f, 92.5f, 999.5f, 486.5f, 17, p.edge, 1);
        line(c, 323, 110, 335, 110, 1, p.accent);
        letter(c, "2014  /  HYBRID RU3", 342, 113, 9, mono, p.accent, 1.05f);
        text(c, "Honda Vezel", 322, 146, 30, instrument, p.ink);
        String mode = data.night ? "NIGHT MODE" : "DAYLIGHT";
        float modeLeft = 979 - width(mode, 9, mono) - (mode.length() - 1) * .9f;
        circle(c, modeLeft - 10, 126, 2.3f, p.muted);
        letter(c, mode, modeLeft, 129, 9, mono, p.muted, .9f);
        circle(c, 330, 461, 10.5f, p.selected);
        prepare(Color.WHITE, Paint.Style.FILL, 0, swatch);
        c.drawCircle(330, 461, 8.3f, paint);
        prepare(0xffa1697a, Paint.Style.STROKE, 1, null);
        c.drawCircle(330, 461, 8.1f, paint);
        letter(c, "EXTERIOR", 348, 454, 8, mono, p.muted, .9f);
        text(c, "Wine Red", 348, 470, 13, bold, p.ink);
        line(c, 427, 449, 427, 474, 1, p.edge);
        if (data.registration.isEmpty()) letter(c, "PHOTO EDITION", 444, 464, 9.5f, mono, p.muted, 1.15f);
        else {
            letter(c, "REGISTRATION", 444, 454, 8, mono, p.muted, .86f);
            letter(c, data.registration, 444, 470, 13, instrument, p.accent, .98f);
        }
        int lampInk = data.lamps ? p.good : p.muted;
        panel(c, 819, 441, 983, 482, 9, data.lamps ? p.goodBg : p.inset, data.lamps ? p.goodEdge : p.edge);
        icon(c, R.drawable.ic_headlights, 830, 453, 19, lampInk);
        letter(c, data.lamps ? "LIGHTS + FOG ON" : "LIGHTS + FOG OFF", 857, 465, 9, mono, lampInk, .65f);
    }

    private void drawPhoto(Canvas c) {
        final float left = 303, top = 160, right = 999, bottom = 436;
        Bitmap frame = car(data.lamps);
        if (frame == null) {
            round(c, left, top, right, bottom, 0, 0xff100e12, null);
            center(c, "Vehicle photo unavailable", (left + right) / 2, (top + bottom) / 2, 14, bold, 0xffb7abb0);
            return;
        }
        // Preserve the supplied photo. Only the cover framing and night treatment change.
        float scale = Math.max((right - left) / frame.getWidth(), (bottom - top) / frame.getHeight());
        float width = frame.getWidth() * scale, height = frame.getHeight() * scale;
        float x = left + ((right - left) - width) * .5f;
        float y = top + ((bottom - top) - height) * .84f;
        c.save();
        c.clipRect(left, top, right, bottom);
        box.set(x, y, x + width, y + height);
        prepare(Color.WHITE, Paint.Style.FILL, 0, null);
        c.drawBitmap(frame, null, box, paint);
        if (data.night) c.drawColor(0x20080916);
        c.restore();
    }

    private void dock(Canvas c, ConsolePalette p) {
        panel(c, 24, 501, 391, 575, 17, p.panel, p.edge);
        panel(c, 36, 514, 84, 562, 8, p.selected, p.selectedEdge);
        if (data.art != null && !data.art.isRecycled()) {
            c.save(); c.clipPath(cover); centerCrop(c, data.art, 36, 514, 84, 562); c.restore();
        } else icon(c, R.drawable.ic_music, 48, 526, 24, p.accent);
        letter(c, "NOW PLAYING", 94, 524, 8.5f, mono, p.accent, .9f);
        text(c, trim(data.track, 151, 13, bold), 94, 540, 13, bold, p.ink);
        text(c, trim(data.artist, 151, 10.5f, basic), 94, 555, 10.5f, basic, p.muted);
        icon(c, R.drawable.ic_previous, 267, 529, 18, p.ink);
        round(c, 297, 516, 340, 559, 22, Color.WHITE, buttonFill);
        stroke(c, 297.5f, 516.5f, 339.5f, 558.5f, 22, p.selectedEdge, 1);
        icon(c, data.playing ? R.drawable.ic_pause : R.drawable.ic_play, 307, 526, 23, 0xff361d17);
        icon(c, R.drawable.ic_next, 355, 529, 18, p.ink);
        panel(c, 405, 501, 1000, 575, 17, p.panel, p.edge);
        for (int i = 0; i < SHORTCUTS.length; i++) {
            float left = 411 + i * 97.7f, mid = left + 47.35f;
            boolean bluetooth = i == 0;
            if (bluetooth) panel(c, left, 507, left + 94.7f, 569, 11, p.selected, p.selectedEdge);
            icon(c, SHORTCUT_ICONS[i], mid - 11, 519, 22, bluetooth ? p.accent : p.ink);
            center(c, SHORTCUTS[i], mid, 555, 10.5f, bold, bluetooth ? p.selectedInk : p.muted);
        }
    }

    private void drawBadge(Canvas c, float cx, float cy, int width) {
        if (badge == null) return;
        int height = Math.round(width * badge.getIntrinsicHeight() / (float) badge.getIntrinsicWidth());
        int x = Math.round(cx - width / 2f), y = Math.round(cy - height / 2f);
        badge.setBounds(x, y, x + width, y + height);
        badge.draw(c);
    }

    private Bitmap car(boolean lit) {
        if (photo != null && photoLit == lit) return photo;
        photo = loadBitmap(lit ? "vezel-photo-lit.webp" : "vezel-photo.webp");
        photoLit = lit;
        return photo;
    }

    private Bitmap loadBitmap(String name) {
        try (InputStream input = getContext().getAssets().open(name)) {
            return BitmapFactory.decodeStream(input);
        } catch (IOException ignored) { return null; }
    }

    private void centerCrop(Canvas c, Bitmap b, float l, float t, float r, float bot) {
        float z = Math.max((r - l) / b.getWidth(), (bot - t) / b.getHeight());
        float w = b.getWidth() * z, h = b.getHeight() * z;
        float cx = (l + r) / 2, cy = (t + bot) / 2;
        box.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        prepare(Color.WHITE, Paint.Style.FILL, 0, null);
        paint.setFilterBitmap(true);
        c.drawBitmap(b, null, box, paint);
    }

    private void icon(Canvas c, int id, float x, float y, int size, int color) {
        Drawable d = icons.get(id);
        if (d == null) {
            d = getContext().getDrawable(id);
            if (d == null) return;
            d = d.mutate();
            icons.put(id, d);
        }
        d.setTint(color);
        int l = Math.round(x), t = Math.round(y);
        d.setBounds(l, t, l + size, t + size);
        d.draw(c);
    }

    private void prepare(int color, Paint.Style style, float line, Shader shader) {
        paint.setShader(shader);
        paint.setColor(color);
        paint.setStyle(style);
        paint.setStrokeWidth(line);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    private void line(Canvas c, float x1, float y1, float x2, float y2, float w, int color) {
        prepare(color, Paint.Style.STROKE, w, null);
        c.drawLine(x1, y1, x2, y2, paint);
    }

    private void circle(Canvas c, float x, float y, float r, int color) {
        prepare(color, Paint.Style.FILL, 0, null);
        c.drawCircle(x, y, r, paint);
    }

    private void round(Canvas c, float l, float t, float r, float b, float radius, int color, Shader shade) {
        prepare(color, Paint.Style.FILL, 0, shade);
        c.drawRoundRect(l, t, r, b, radius, radius, paint);
    }

    private void stroke(Canvas c, float l, float t, float r, float b, float radius, int color, float width) {
        prepare(color, Paint.Style.STROKE, width, null);
        c.drawRoundRect(l, t, r, b, radius, radius, paint);
    }

    private void panel(Canvas c, float l, float t, float r, float b, float radius, int fill, int border) {
        round(c, l, t, r, b, radius, fill, null);
        stroke(c, l + .5f, t + .5f, r - .5f, b - .5f, radius, border, 1);
    }

    private void text(Canvas c, String value, float x, float y, float size, Typeface font, int color) {
        textStyle(size, font, color);
        c.drawText(value, x, y, textPaint);
    }

    private void center(Canvas c, String value, float x, float y, float size, Typeface font, int color) {
        text(c, value, x - width(value, size, font) / 2, y, size, font, color);
    }

    private void right(Canvas c, String value, float x, float y, float size, Typeface font, int color) {
        text(c, value, x - width(value, size, font), y, size, font, color);
    }

    private void letter(Canvas c, String value, float x, float y, float size, Typeface font, int color, float tracking) {
        textStyle(size, font, color);
        for (int i = 0; i < value.length(); i++) {
            c.drawText(value, i, i + 1, x, y, textPaint);
            x += textPaint.measureText(value, i, i + 1) + tracking;
        }
    }

    private void textStyle(float size, Typeface font, int color) {
        textPaint.setTextSize(size);
        textPaint.setTypeface(font);
        textPaint.setColor(color);
    }

    private float width(String value, float size, Typeface font) {
        textStyle(size, font, palette().ink);
        return textPaint.measureText(value);
    }

    private String trim(String value, float maxWidth, float size, Typeface font) {
        textStyle(size, font, palette().ink);
        return TextUtils.ellipsize(value, textPaint, maxWidth, TextUtils.TruncateAt.END).toString();
    }

    private static Shader gradient(float x1, float y1, float x2, float y2, int a, int b) {
        return new LinearGradient(x1, y1, x2, y2, a, b, Shader.TileMode.CLAMP);
    }

    private static Path roundedPath(float l, float t, float r, float b, float radius) {
        Path p = new Path();
        p.addRoundRect(l, t, r, b, radius, radius, Path.Direction.CW);
        return p;
    }

    private static int exact(int size) { return MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY); }

    private final class Target extends View {
        final RectF area;

        Target(Context context, RectF area) {
            super(context);
            this.area = area;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }

        void description(String s) {
            if (!TextUtils.equals(getContentDescription(), s)) setContentDescription(s);
        }

        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            if (isClickable()) info.setClassName(Button.class.getName());
        }

        @Override protected void drawableStateChanged() { super.drawableStateChanged(); invalidate(); }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (isPressed()) round(c, 1, 1, getWidth() - 1, getHeight() - 1, 10 * scale, 0x26f5aa71, null);
            if (isFocused()) stroke(c, 1, 1, getWidth() - 1, getHeight() - 1, 10 * scale, palette().accent, Math.max(1, 2 * scale));
        }
    }

    private final class PowerScreen extends View {
        boolean leaving;
        private final Shader background = new RadialGradient(512, 282, 380, new int[]{0xff302018, 0xff100d10, 0xff09090b},
                new float[]{0, .62f, 1f}, Shader.TileMode.CLAMP);
        private final Shader halo = new RadialGradient(512, 267, 139, 0x52ffb95a, 0x00ffb95a, Shader.TileMode.CLAMP);

        PowerScreen(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
            setClickable(true); // Cover the dashboard during the short sequence.
        }

        @Override protected void onDraw(Canvas c) {
            c.drawColor(0xff09090b);
            c.save(); c.translate(offsetX, offsetY); c.scale(scale, scale);
            round(c, 0, 0, W, H, 0, Color.WHITE, background);
            round(c, 373, 128, 651, 406, 0, Color.WHITE, halo);
            drawBadge(c, 512, 255, 184);
            String greeting = leaving ? "GOOD BYE" : "Welcome";
            float tracking = leaving ? 6 : 3.3f;
            float size = 37;
            letter(c, greeting, 512 - (width(greeting, size, instrument) + (greeting.length() - 1) * tracking) / 2,
                    365, size, instrument, 0xffffd98f, tracking);
            String sub = "H O N D A   /   V E Z E L";
            center(c, sub, 512, 395, 9.5f, mono, 0xffb99066);
            c.restore();
        }
    }
}
