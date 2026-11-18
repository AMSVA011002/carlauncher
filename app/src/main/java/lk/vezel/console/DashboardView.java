package lk.vezel.console;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
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
    static final int STARTUP = 1, WEATHER = 2, NIGHT = 3, GPS = 4, LIMIT = 5, ROUTE = 6, LAMPS = 7,
            MEDIA = 8, PREVIOUS = 9, PLAY = 10, NEXT = 11, MAPS = 12, MUSIC = 13,
            PHONE = 14, RADIO = 15, SETTINGS = 16, APPS = 17, READ_SPEED = 18, READ_CLOCK = 19, SETUP = 20;
    private static final int READ_VEHICLE = 21;
    private static final int INK = 0xfff6efea, MUTED = 0xffaaa1a6, FAINT = 0xffafa3a0,
            AMBER = 0xffeea56e, EDGE = 0xff2c2b32, GREEN = 0xffa5d6bb;
    private static final String[] SHORTCUTS = {"Navigation", "Music", "Phone", "Radio", "Settings", "Apps"};
    private static final int[] SHORTCUT_ICONS = {R.drawable.ic_navigation, R.drawable.ic_music, R.drawable.ic_phone,
            R.drawable.ic_radio, R.drawable.ic_settings, R.drawable.ic_apps};

    static final class Data {
        boolean night, lamps, over, dialGauge, speedAlert = true, playing, setupNeeded = true;
        int speed = -1, limit = 60, heading = -1, weatherIcon = R.drawable.ic_weather;
        String clock = "--:--", period = "", date = "", trip = "0.0", drive = "00:00";
        String gps = "AWAITING GPS", temperature = "—", place = "Weather", registration = "";
        String track = "No media playing", artist = "Tap to set up media";
        String routeIdle = "Google Maps or Waze", routeDetail = "Tap to open navigation";
        NavigationText.Hint hint;
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
    private final RectF dial = new RectF(65, 145, 247, 327);
    private final float[] tickCoordinates = new float[41 * 4];
    private final Path stage = roundedPath(303, 93, 999, 486, 16), cover = roundedPath(36, 514, 84, 562, 8);
    private final Path routeLines = new Path();
    private final Shader backdrop = gradient(15, 0, W, H, 0xff19181d, 0xff0f1014),
            warmTop = new RadialGradient(863, -145, 500, 0xff362623, 0x00362623, Shader.TileMode.CLAMP),
            dialGlow = new RadialGradient(156, 228, 165, 0xff241d21, 0xff17181d, Shader.TileMode.CLAMP),
            photoHeader = gradient(303, 93, 999, 169, 0xff261c23, 0xff151319),
            orange = gradient(728, 102, 983, 163, 0xff573025, 0xff874f33),
            buttonFill = gradient(304, 518, 336, 557, 0xfff7b682, 0xffe79155),
            copper = gradient(410, 509, 518, 567, 0xff4b3027, 0xff382822),
            markFill = gradient(26, 19, 68, 61, 0xff35251e, 0xff221918),
            swatch = gradient(321, 444, 342, 469, 0xffad5c70, 0xff441321),
            arcGradient = gradient(65, 310, 247, 145, 0xffaa6443, 0xffffbb82);
    private final PowerScreen powerScreen;
    private boolean starting, photoLit;
    private Bitmap photo;
    private float scale = 1, offsetX, offsetY;

    DashboardView(Context context, Actions actions) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(0xff0a0b0e);
        setClipChildren(false);
        Typeface font;
        try { font = Typeface.createFromAsset(context.getAssets(), "fonts/instrument.ttf"); }
        catch (RuntimeException ignored) { font = bold; }
        instrument = font;
        for (int i = 0; i <= 40; i++) {
            double angle = Math.toRadians(138d + i * 264d / 40d);
            double x = Math.cos(angle), y = Math.sin(angle);
            int inner = i % 5 == 0 ? 76 : 80;
            tickCoordinates[i * 4] = (float) (156 + x * inner);
            tickCoordinates[i * 4 + 1] = (float) (236 + y * inner);
            tickCoordinates[i * 4 + 2] = (float) (156 + x * 85);
            tickCoordinates[i * 4 + 3] = (float) (236 + y * 85);
        }
        routeLines.moveTo(867, 194); routeLines.lineTo(891, 159); routeLines.lineTo(923, 150);
        routeLines.lineTo(947, 117); routeLines.lineTo(976, 111);
        routeLines.moveTo(882, 196); routeLines.lineTo(905, 166); routeLines.lineTo(937, 155);
        routeLines.lineTo(960, 124); routeLines.lineTo(985, 119);
        routeLines.moveTo(925, 198); routeLines.lineTo(941, 176); routeLines.lineTo(961, 168);
        routeLines.lineTo(984, 141);
        target(STARTUP, 24, 11, 153, 66, "Replay Honda startup", actions);
        target(READ_CLOCK, 458, 11, 566, 66, "Clock", null);
        target(SETUP, 748, 14, 829, 63, "Complete launcher setup", actions);
        target(WEATHER, 834, 12, 943, 65, "Weather", actions);
        target(NIGHT, 951, 13, 1000, 64, "Night display", actions);
        target(GPS, 187, 100, 278, 143, "Set up GPS", actions);
        target(READ_SPEED, 42, 145, 270, 305, "GPS speed", null);
        target(LIMIT, 52, 308, 260, 354, "Set speed alert", actions);
        target(ROUTE, 728, 101, 984, 164, "Navigation", actions);
        target(READ_VEHICLE, 304, 171, 998, 435, "Honda Vezel Hybrid RU3 in Wine Red", null);
        target(LAMPS, 816, 438, 985, 485, "Headlights and fog lights in the photo", actions);
        target(MEDIA, 34, 508, 252, 571, "Music information", actions);
        target(PREVIOUS, 251, 510, 295, 565, "Previous track", actions);
        target(PLAY, 295, 510, 342, 565, "Play", actions);
        target(NEXT, 342, 510, 386, 565, "Next track", actions);
        for (int i = 0; i < SHORTCUTS.length; i++) {
            float left = 411 + i * 97.7f;
            target(MAPS + i, left, 507, left + 94.7f, 569, SHORTCUTS[i], actions);
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
            hit.get(ROUTE).description(data.hint == null ? "Open navigation" : data.hint.distance + " " + data.hint.instruction);
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

    void trimCarCache() { photo = null; }

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

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);
        canvas.clipRect(0, 0, W, H);
        round(canvas, 0, 0, W, H, 0, INK, backdrop);
        round(canvas, 0, 0, W, H, 0, INK, warmTop);
        header(canvas);
        instruments(canvas);
        vehicle(canvas);
        dock(canvas);
        letter(canvas, "RU3 EDITION  /  HONDA VEZEL", 25, 591, 8, mono, 0xff918488, .7f);
        right(canvas, "PHOTO EDITION   /   1024 × 600", 999, 591, 8, mono, 0xff918488);
        if (data.night) round(canvas, 0, 0, W, H, 0, 0x3509050b, null);
        canvas.restore();
    }

    private void header(Canvas c) {
        round(c, 24, 17, 67, 60, 12, INK, markFill);
        stroke(c, 24.5f, 17.5f, 66.5f, 59.5f, 12, 0xff604332, 1);
        icon(c, R.drawable.ic_honda, 34, 27, 23, 0xffe8b07b);
        letter(c, "VEZEL", 79, 39, 19, instrument, INK, 3.2f);
        letter(c, "DRIVE CONSOLE", 80, 52, 8.5f, mono, MUTED, 1.05f);
        float clockWidth = width(data.clock, 27, instrument), periodWidth = width(data.period, 10, mono);
        float clockLeft = 512 - (clockWidth + (data.period.isEmpty() ? 0 : periodWidth + 5)) / 2;
        text(c, data.clock, clockLeft, 42, 27, instrument, INK);
        text(c, data.period, clockLeft + clockWidth + 5, 42, 10, mono, AMBER);
        center(c, data.date, 512, 58, 9, mono, MUTED);
        if (data.setupNeeded) {
            panel(c, 759, 26, 819, 50, 6, 0xff281f22, 0xff4b3736);
            circle(c, 769, 38, 2, AMBER);
            letter(c, "SETUP", 777, 41, 9, mono, 0xffe8b79a, .75f);
        }
        icon(c, data.weatherIcon, 843, 24, 25, 0xffdda477);
        text(c, data.temperature, 877, 37, 19, instrument, INK);
        text(c, trim(data.place, 62, 10, basic), 877, 51, 10, basic, MUTED);
        line(c, 947, 25, 947, 52, 1, 0xff302a2e);
        panel(c, 958, 17, 1000, 60, 11, data.night ? 0xff38261f : 0xff231f27,
                data.night ? 0xff78513a : 0xff363139);
        icon(c, data.night ? R.drawable.ic_sun : R.drawable.ic_moon, 970, 29, 18, data.night ? AMBER : 0xffdad0d2);
        line(c, 24, 76, 1000, 76, 1, 0xff2b282d);
    }

    private void instruments(Canvas c) {
        round(c, 24, 92, 288, 487, 17, INK, dialGlow);
        stroke(c, 24.5f, 92.5f, 287.5f, 486.5f, 17, EDGE, 1);
        letter(c, "GPS SPEED", 42, 121, 10, mono, 0xffc4bdba, 1.05f);
        boolean fixed = "GPS FIX".equals(data.gps);
        float pillWidth = Math.max(62, width(data.gps, 8.5f, mono) + 20);
        float pillLeft = 270 - pillWidth;
        panel(c, pillLeft, 109, 270, 129, 5, fixed ? 0xff1c2925 : 0xff24212a, fixed ? 0xff293b32 : 0xff3a313a);
        circle(c, pillLeft + 8, 119, 2.4f, fixed ? 0xff8acba6 : 0xffb68a73);
        text(c, data.gps, pillLeft + 14, 122, 8.5f, mono, fixed ? GREEN : 0xffc0a59b);
        float progress = Math.max(0, Math.min(1, data.speed / 160f));
        String value = data.speed < 0 ? "—" : Integer.toString(data.speed);
        if (data.dialGauge) drawDial(c, value, progress);
        else drawSpeedRail(c, value, progress);
        if (data.over) {
            panel(c, 52, 308, 260, 350, 8, 0xff482821, 0xffa86b4f);
            icon(c, R.drawable.ic_warning, 64, 317, 23, 0xffffbd85);
            letter(c, "LIMIT REACHED", 99, 326, 10, mono, 0xffffc98f, .65f);
            text(c, data.limit + " km/h  ·  Slow down", 99, 342, 10.5f, bold, 0xffe8b1a0);
        } else {
            circle(c, 119, 330, 13.5f, 0xffb65851);
            circle(c, 119, 330, 11.5f, 0xfff1e8e3);
            center(c, data.speedAlert ? Integer.toString(data.limit) : "–", 119, 334.5f, 12, instrument, 0xff291d21);
            letter(c, "SPEED ALERT", 140, 333, 8.5f, mono, FAINT, .8f);
        }
        line(c, 42, 362, 270, 362, 1, 0xff2a282e);
        line(c, 156, 376, 156, 417, 1, 0xff2a282e);
        letter(c, "TRIP DISTANCE", 43, 384, 8.5f, mono, MUTED, .55f);
        letter(c, "DRIVE TIME", 171, 384, 8.5f, mono, MUTED, .55f);
        String trip = trim(data.trip, 84, 24, instrument);
        text(c, trip, 42, 412, 24, instrument, INK);
        text(c, "km", 47 + width(trip, 24, instrument), 412, 12, instrument, MUTED);
        text(c, trim(data.drive, 96, 24, instrument), 171, 412, 24, instrument, INK);
        panel(c, 42, 439, 270, 473, 8, 0xff111216, 0xff26252b);
        icon(c, R.drawable.ic_compass, 51, 448, 15, 0xffdf9f73);
        letter(c, "HEADING", 73, 459, 8.5f, mono, MUTED, .6f);
        String degrees = data.heading < 0 ? "—" : data.heading + "°";
        right(c, degrees, 261, 460, 12, instrument, MUTED);
        if (data.heading >= 0) right(c, TripMeter.compass(data.heading), 257 - width(degrees, 12, instrument), 460, 12, instrument, INK);
    }

    private void drawSpeedRail(Canvas c, String value, float progress) {
        int readout = data.over ? 0xffffb890 : 0xfff8f3ed;
        text(c, value, 40, 246, data.speed >= 100 ? 98 : 115, instrument, readout);
        text(c, "km/h", 44, 272, 15, instrument, MUTED);
        int lit = Math.round(progress * 27);
        for (int i = 0; i < 27; i++) {
            float l = 42 + i * 8.47f;
            round(c, l, 287, l + 5.7f, 294, 1.7f,
                    i >= lit ? 0xff393137 : data.over ? 0xfff28c63 : 0xffea9e65, null);
        }
    }

    private void drawDial(Canvas c, String value, float progress) {
        prepare(0xff36343c, Paint.Style.STROKE, 1.35f, null);
        c.drawArc(dial, 138, 264, false, paint);
        if (progress > 0) {
            prepare(AMBER, Paint.Style.STROKE, 2.2f, arcGradient);
            c.drawArc(dial, 138, 264 * progress, false, paint);
            double angle = Math.toRadians(138 + 264 * progress);
            float x = (float) (156 + 91 * Math.cos(angle)), y = (float) (236 + 91 * Math.sin(angle));
            circle(c, x, y, 5.7f, 0x42efa465);
            circle(c, x, y, 3.8f, 0xfff6ad76);
        }
        for (int i = 0; i <= 40; i++) {
            int j = i * 4;
            boolean enabled = progress > 0 && i / 40f <= progress;
            line(c, tickCoordinates[j], tickCoordinates[j + 1], tickCoordinates[j + 2], tickCoordinates[j + 3],
                    i % 5 == 0 ? 1.4f : .85f, enabled ? 0xffdb9c71 : i % 5 == 0 ? 0xff6b5d61 : 0xff4b444c);
        }
        center(c, value, 156, 265,
                data.speed >= 100 ? 68 : 85, instrument, data.over ? 0xffffb890 : 0xfff8f3ed);
        center(c, "km/h", 156, 297, 14, instrument, 0xffb7adb0);
    }

    private void vehicle(Canvas c) {
        c.save();
        c.clipPath(stage);
        round(c, 302, 92, 1000, 487, 0, 0xff141217, null);
        round(c, 302, 92, 1000, 169, 0, INK, photoHeader);
        drawPhoto(c);
        line(c, 303, 436, 999, 436, 1, 0xff3c2e34);
        c.restore();
        stroke(c, 302.5f, 92.5f, 999.5f, 486.5f, 17, 0xff413339, 1);
        line(c, 323, 111, 335, 111, 1, AMBER);
        letter(c, "2014  /  HYBRID RU3", 342, 114, 9, mono, AMBER, 1.05f);
        text(c, "Honda Vezel", 322, 151, 32, instrument, INK);
        route(c);
        circle(c, 330, 461, 10.5f, 0xff3c1b26);
        prepare(INK, Paint.Style.FILL, 0, swatch);
        c.drawCircle(330, 461, 8.3f, paint);
        prepare(0xffa1697a, Paint.Style.STROKE, 1, null);
        c.drawCircle(330, 461, 8.1f, paint);
        letter(c, "EXTERIOR", 348, 454, 8, mono, 0xffbda9aa, .9f);
        text(c, "Wine Red", 348, 470, 13, bold, INK);
        line(c, 427, 449, 427, 474, 1, 0xff4a373d);
        if (data.registration.isEmpty()) {
            letter(c, "PHOTO EDITION", 444, 464, 9.5f, mono, 0xffb7a0a2, 1.15f);
        } else {
            letter(c, "REGISTRATION", 444, 454, 8, mono, 0xffbda9aa, .86f);
            letter(c, data.registration, 444, 470, 13, instrument, 0xffedc1a7, .98f);
        }
        panel(c, 819, 441, 983, 482, 9, data.lamps ? 0xff192820 : 0xff1e1b21, data.lamps ? 0xff46604f : 0xff50414b);
        icon(c, R.drawable.ic_headlights, 830, 453, 19, data.lamps ? GREEN : MUTED);
        letter(c, data.lamps ? "LIGHTS + FOG ON" : "LIGHTS + FOG OFF", 857, 465, 9, mono, data.lamps ? GREEN : MUTED, .65f);
    }

    private void drawPhoto(Canvas c) {
        final float left = 303, top = 169, right = 999, bottom = 436;
        Bitmap frame = car(data.lamps);
        if (frame == null) {
            round(c, left, top, right, bottom, 0, 0xff100e12, null);
            center(c, "Vehicle photo unavailable", (left + right) / 2, (top + bottom) / 2, 14, bold, MUTED);
            return;
        }
        // Crop mainly the empty sky. Match object-position: center 84% in the browser.
        float scale = Math.max((right - left) / frame.getWidth(), (bottom - top) / frame.getHeight());
        float width = frame.getWidth() * scale, height = frame.getHeight() * scale;
        float x = left + ((right - left) - width) * .5f;
        float y = top + ((bottom - top) - height) * .84f;
        c.save();
        c.clipRect(left, top, right, bottom);
        box.set(x, y, x + width, y + height);
        prepare(Color.WHITE, Paint.Style.FILL, 0, null);
        c.drawBitmap(frame, null, box, paint);
        c.restore();
    }

    private void route(Canvas c) {
        NavigationText.Hint hint = data.hint;
        boolean live = hint != null;
        round(c, 728, 102, 983, 163, 12, 0xff252028, live ? orange : null);
        stroke(c, 728.5f, 102.5f, 982.5f, 162.5f, 12, live ? 0xffa36243 : 0xff51404a, 1);
        c.save();
        box.set(729, 103, 982, 162);
        c.clipRect(box);
        prepare(live ? 0x30ffd2a1 : 0x25dca48f, Paint.Style.STROKE, 1, null);
        c.translate(0, -23);
        c.drawPath(routeLines, paint);
        c.restore();
        panel(c, 740, 112, 780, 153, 9, live ? 0xff40281e : 0xff1b1720, live ? 0xff976242 : 0xff463843);
        int arrow = R.drawable.ic_navigation;
        if (hint != null) switch (hint.arrow) {
            case LEFT: arrow = R.drawable.ic_turn_left; break;
            case RIGHT: arrow = R.drawable.ic_turn_right; break;
            case STRAIGHT: arrow = R.drawable.ic_straight; break;
            default: break;
        }
        icon(c, arrow, 746, 119, 27, live ? 0xffffc294 : AMBER);
        String headline = !live ? "Ready to navigate" : hint.distance.isEmpty() ? "Next maneuver" : hint.distance;
        int size = !live || hint.distance.isEmpty() ? 17 : 22;
        text(c, trim(headline, 170, size, instrument), 792, 127, size, instrument, 0xffffeee3);
        text(c, trim(live ? hint.instruction : data.routeIdle, 169, 11.5f, bold), 792, 142, 11.5f, bold, 0xffefdbd4);
        text(c, trim(live ? hint.detail : data.routeDetail, 169, 8.5f, mono), 792, 155, 8.5f, mono, live ? 0xffe5bba3 : MUTED);
    }

    private void dock(Canvas c) {
        panel(c, 24, 501, 391, 575, 17, 0xff191a20, EDGE);
        panel(c, 36, 514, 84, 562, 8, 0xff31232c, 0xff503b46);
        if (data.art != null && !data.art.isRecycled()) {
            c.save(); c.clipPath(cover); centerCrop(c, data.art, 36, 514, 84, 562); c.restore();
        } else icon(c, R.drawable.ic_music, 48, 526, 24, 0xffc59481);
        letter(c, "NOW PLAYING", 94, 524, 8.5f, mono, 0xffe7a773, .9f);
        text(c, trim(data.track, 151, 13, bold), 94, 540, 13, bold, INK);
        text(c, trim(data.artist, 151, 10.5f, basic), 94, 555, 10.5f, basic, MUTED);
        icon(c, R.drawable.ic_previous, 267, 529, 18, 0xffd7cbd2);
        round(c, 297, 516, 340, 559, 22, INK, buttonFill);
        stroke(c, 297.5f, 516.5f, 339.5f, 558.5f, 22, 0xfff0b186, 1);
        icon(c, data.playing ? R.drawable.ic_pause : R.drawable.ic_play, 307, 526, 23, 0xff2f1a14);
        icon(c, R.drawable.ic_next, 355, 529, 18, 0xffd7cbd2);
        panel(c, 405, 501, 1000, 575, 17, 0xff191a20, EDGE);
        for (int i = 0; i < SHORTCUTS.length; i++) {
            float left = 411 + i * 97.7f, mid = left + 47.35f;
            boolean nav = i == 0;
            if (nav) {
                round(c, left, 507, left + 94.7f, 569, 11, INK, copper);
                stroke(c, left + .5f, 507.5f, left + 94.2f, 568.5f, 11, 0xff674433, 1);
            }
            icon(c, SHORTCUT_ICONS[i], mid - 11, 519, 22, nav ? AMBER : 0xffd8d0d4);
            center(c, SHORTCUTS[i], mid, 555, 10.5f, bold, nav ? 0xfff4b98f : 0xffc8c0c6);
        }
    }

    private Bitmap car(boolean lit) {
        if (photo != null && photoLit == lit) return photo;
        try (InputStream input = getContext().getAssets().open(lit ? "vezel-photo-lit.webp" : "vezel-photo.webp")) {
            photo = BitmapFactory.decodeStream(input);
            photoLit = lit;
            return photo;
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
        textStyle(size, font, INK);
        return textPaint.measureText(value);
    }

    private String trim(String value, float maxWidth, float size, Typeface font) {
        textStyle(size, font, INK);
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
            if (isFocused()) stroke(c, 1, 1, getWidth() - 1, getHeight() - 1, 10 * scale, AMBER, Math.max(1, 2 * scale));
        }
    }

    private final class PowerScreen extends View {
        boolean leaving;
        private final Shader background = new RadialGradient(512, 282, 380, new int[]{0xff302018, 0xff100d10, 0xff09090b},
                new float[]{0, .62f, 1f}, Shader.TileMode.CLAMP);
        private final Shader halo = new RadialGradient(512, 267, 139, 0x52ffb95a, 0x00ffb95a, Shader.TileMode.CLAMP);
        private Bitmap logo, logoGlow;
        private final int[] glowOffset = new int[2];

        PowerScreen(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
            setClickable(true); // Cover the dashboard during the short sequence.
        }

        @Override protected void onDraw(Canvas c) {
            c.drawColor(0xff09090b);
            c.save(); c.translate(offsetX, offsetY); c.scale(scale, scale);
            round(c, 0, 0, W, H, 0, INK, background);
            round(c, 373, 128, 651, 406, 0, INK, halo);
            if (logo == null) {
                logo = Bitmap.createBitmap(156, 156, Bitmap.Config.ARGB_8888);
                Canvas mask = new Canvas(logo);
                icon(mask, R.drawable.ic_honda, 0, 0, 156, Color.WHITE);
                Paint gold = new Paint(Paint.ANTI_ALIAS_FLAG);
                gold.setShader(new LinearGradient(15, 16, 135, 142, new int[]{0xffffefb6, 0xffedbe76, 0xffa86a37},
                        new float[]{0, .45f, 1}, Shader.TileMode.CLAMP));
                gold.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
                mask.drawRect(0, 0, 156, 156, gold);
                Paint blur = new Paint(Paint.ANTI_ALIAS_FLAG);
                blur.setMaskFilter(new BlurMaskFilter(8, BlurMaskFilter.Blur.NORMAL));
                logoGlow = logo.extractAlpha(blur, glowOffset);
            }
            if (logoGlow != null) {
                final float z = 138f / 156f;
                float x = 443 + glowOffset[0] * z, y = 190 + glowOffset[1] * z;
                box.set(x, y, x + logoGlow.getWidth() * z, y + logoGlow.getHeight() * z);
                prepare(0x96ffb95c, Paint.Style.FILL, 0, null);
                c.drawBitmap(logoGlow, null, box, paint);
            }
            centerCrop(c, logo, 443, 190, 581, 328);
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
