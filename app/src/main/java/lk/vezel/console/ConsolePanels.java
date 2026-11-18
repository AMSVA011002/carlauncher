package lk.vezel.console;

import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parked setup and the native installed-app drawer. */
final class ConsolePanels {
    private ConsolePalette palette;
    private final DashboardActivity activity;
    private Dialog current;
    private TextView lightStatus;

    ConsolePanels(DashboardActivity activity) { this.activity = activity; }

    void dismiss() {
        clearLightStatus();
        if (current != null) { current.dismiss(); current = null; }
    }

    private void clearLightStatus() {
        if (lightStatus != null) activity.lights.showStatus(false);
        lightStatus = null;
    }

    void refreshLightStatus() {
        if (lightStatus == null) return;
        int level = activity.lights.level();
        String reading = LightSignal.validLevel(level) ? String.valueOf(level) : "not reported";
        lightStatus.setText("ANDROID BRIGHTNESS  ·  " + reading + "\n" + activity.lights.source());
    }

    void settings() {
        Shell s = shell("VEZEL DRIVE CONSOLE", "Drive settings", true);
        note(s.body, "Set up your home screen and shortcuts while parked.");
        button(s.body, "Dashboard appearance  ·  Speed & registration", true, this::dashboardAppearance);
        button(s.body, "Headlight sync  ·  Factory dimmer", true, this::headlightSync);
        section(s.body, "Dashboard colors");
        int mode = activity.lights.mode();
        choices(s.body, new String[]{"Auto", "Day", "Night"}, mode == 1 ? 2 : mode == 2 ? 1 : 0, index -> {
            int next = index == 1 ? 2 : index == 2 ? 1 : 0;
            activity.lights.setMode(next);
        });
        section(s.body, "Lights on the car photo");
        choices(s.body, new String[]{"Auto", "On", "Off"}, activity.lights.lampMode(), index -> {
            activity.lights.setLampMode(index);
        });
        note(s.body, "Auto uses the same input for the dashboard colors and photo. Screen brightness stays under the head unit’s control.");
        section(s.body, "Driving");
        button(s.body, "Speed alert  ·  " + (activity.alertEnabled() ? activity.limit() + " km/h" : "off"), true, this::speed);
        button(s.body, "Reset trip distance and drive time", false, this::confirmReset);
        section(s.body, "Android setup");
        button(s.body, "Home app, GPS and notification access", false, this::setup);
        button(s.body, "Open Android Wi-Fi settings", false, () -> {
            dismiss(); if (!activity.apps.start(new Intent(Settings.ACTION_WIFI_SETTINGS))) activity.toast("Wi-Fi settings are unavailable.");
        });
        section(s.body, "Media playback");
        toggle(s.body, "Resume the last playing media session on startup", activity.prefs.getBoolean("resume_music", true), enabled -> {
            activity.prefs.edit().putBoolean("resume_music", enabled).apply();
            if (!enabled) activity.media.cancelResume();
        });
        section(s.body, "App shortcuts");
        button(s.body, "BT Audio  ·  " + activity.apps.bluetoothLabel(activity.prefs.getString(DashboardActivity.BT_AUDIO, "")),
                false, () -> bluetoothAudio(false));
        assignment(s.body, "Music", "shortcut_music");
        assignment(s.body, "Phone", "shortcut_phone");
        assignment(s.body, "Radio", "shortcut_radio");
        section(s.body, "Welcome & Good Bye");
        button(s.body, "Start and key-off cues", false, this::powerCues);
        section(s.body, "Weather");
        toggle(s.body, "Weather from Open-Meteo", activity.weather.enabled(), activity::toggleWeather);
        note(s.body, "Weather uses rounded coordinates. Without location, the default is Colombo.");
        show(s);
    }

    private void dashboardAppearance() {
        Shell s = shell("PHOTO EDITION", "Dashboard appearance", true);
        section(s.body, "Speed display");
        choices(s.body, new String[]{"Large number", "Metallic dial"}, activity.dialGauge() ? 1 : 0,
                index -> activity.setDialGauge(index == 1));
        section(s.body, "Registration label");
        note(s.body, "Your registration appears beside the Wine Red swatch. It is not drawn over the photograph. Leave it blank to hide it.");
        EditText entry = field("Optional registration, e.g. WP ABC 0123", activity.registration());
        entry.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
        entry.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        s.body.addView(entry, withMargin(6, dp(44)));
        button(s.body, "Save dashboard", true, () -> {
            String input = entry.getText().toString().trim();
            if (!input.isEmpty() && VehicleRegistration.normalize(input).isEmpty()) {
                entry.setError("Use two blocks and an optional province, e.g. WP ABC 0123");
                return;
            }
            activity.setRegistration(input);
            dismiss();
        });
        show(s);
    }

    void setup() {
        Shell s = shell("FIRST DRIVE", "Set up your console", true);
        note(s.body, "Set up these features while parked. You can try the launcher without replacing your factory Home app.");
        section(s.body, "1. Home screen");
        button(s.body, activity.homeSelected() ? "Vezel Drive Console is the Home app" : "Choose Vezel Drive Console as Home", false, () -> {
            dismiss(); activity.setAsHome();
        });
        section(s.body, "2. GPS speed, trip and heading");
        button(s.body, activity.preciseGps() ? "Precise location allowed" : "Allow precise location", false, () -> {
            dismiss(); activity.requestGps();
        });
        section(s.body, "3. Music and playback controls");
        button(s.body, activity.notificationsAllowed() ? "Notification access allowed" : "Allow notification access", false, () -> {
            dismiss(); activity.notificationSetup();
        });
        note(s.body, "If Android marks notification access as restricted for this sideload, check the app’s info menu before enabling it.");
        button(s.body, "Back to drive settings", true, this::settings);
        show(s);
    }

    private void assignment(LinearLayout group, String name, String key) {
        String label = activity.apps.label(activity.prefs.getString(key, null));
        button(group, name + "  ·  " + label, false, () -> drawer("Assign " + name.toLowerCase(Locale.ROOT) + " shortcut", key));
    }

    void speed() {
        Shell s = shell("GPS SPEED", "Speed alert", true);
        note(s.body, "Choose your own threshold. The warning symbol appears at that speed and the spoken warning plays once after two GPS samples at or above it. This is not a road speed-limit reading.");
        int[] value = {activity.limit()};
        boolean[] enabled = {activity.alertEnabled()};
        TextView count = text(value[0] + " km/h", 28, palette.accent, true);
        count.setGravity(Gravity.CENTER);
        s.body.addView(count, matchWrap());
        SeekBar slider = new SeekBar(activity);
        slider.setProgressTintList(ColorStateList.valueOf(palette.accent));
        slider.setProgressBackgroundTintList(ColorStateList.valueOf(palette.edge));
        slider.setThumbTintList(ColorStateList.valueOf(palette.accent));
        slider.setMax(120);
        slider.setProgress(value[0] - 20);
        slider.setContentDescription("Speed alert in kilometres per hour");
        s.body.addView(slider, new LinearLayout.LayoutParams(-1, dp(48)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                value[0] = 20 + progress;
                count.setText(value[0] + " km/h");
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        toggle(s.body, "Enable speed warning", enabled[0], checked -> enabled[0] = checked);
        button(s.body, "Preview warning voice", false, activity::previewWarning);
        button(s.body, "Save speed alert", true, () -> { activity.setAlert(value[0], enabled[0]); dismiss(); });
        show(s);
    }

    private void confirmReset() {
        Shell s = shell("DRIVE LOG", "Reset trip?", true);
        note(s.body, "Both trip distance and moving time will be set to zero.");
        button(s.body, "Reset trip", true, () -> { activity.resetTrip(); dismiss(); });
        button(s.body, "Keep trip", false, this::settings);
        show(s);
    }

    void media() {
        Shell s = shell("VEZEL MEDIA", "Now playing", true);
        note(s.body, !activity.media.title().isEmpty() ? activity.media.title() + (activity.media.artist().isEmpty() ? "" : " · " + activity.media.artist())
                : "Start a track in your music app. The home screen shows the title, artist and art when the player shares them.");
        if (!activity.notificationsAllowed()) {
            note(s.body, "Allow notification access to show active Android media sessions and control playback.");
            button(s.body, "Open notification access", true, () -> { dismiss(); activity.notificationSetup(); });
        }
        button(s.body, "Open music app", activity.notificationsAllowed(), () -> { dismiss(); activity.openMusic(); });
        button(s.body, "Assign Music shortcut", false, () -> drawer("Assign music shortcut", "shortcut_music"));
        show(s);
    }

    void weather() {
        Shell s = shell("CURRENT CONDITIONS", "Weather", true);
        WeatherClient client = activity.weather;
        WeatherClient.Reading value = client.current();
        if (value != null) {
            TextView temperature = text(value.temperature + "°C  ·  " + value.condition(), 23, palette.ink, true);
            LinearLayout.LayoutParams p = matchWrap(); p.topMargin = dp(14);
            s.body.addView(temperature, p);
            note(s.body, value.place + (client.cached(value) ? " · Last saved reading" : " · Current reading"));
        } else note(s.body, !client.enabled() ? "Weather is disabled." : client.loading() ? "Updating current conditions…"
                : "No reading available. Connect the head unit to the internet and try again.");
        if (client.enabled()) note(s.body, client.usingPreset() ? "Using Colombo until an approximate location is available." : "Using your approximate location.");
        note(s.body, "Weather data: Open-Meteo.com");
        if (client.enabled()) button(s.body, "Refresh weather", true, () -> { dismiss(); client.refresh(true); });
        else button(s.body, "Enable weather", true, () -> { dismiss(); activity.toggleWeather(true); });
        show(s);
    }

    private void headlightSync() {
        Shell s = shell("FACTORY DIMMER", "Headlight sync", true);
        note(s.body, "While parked, keep the head unit’s factory dimming option enabled. Learn the two brightness levels so the photo and dashboard can follow it.");
        TextView live = text("", 13, palette.accent, true);
        live.setLineSpacing(dp(3), 1f);
        s.body.addView(live, withMargin(12, -2));
        TextView feedback = text("Switch the car lights off, wait for the display to brighten, then capture OFF.", 13, palette.muted, false);
        feedback.setLineSpacing(dp(2), 1f);
        s.body.addView(feedback, withMargin(11, -2));
        int[] off = {LightSignal.UNAVAILABLE};
        Button offButton = control("1. Capture lights OFF", true);
        Button onButton = control("2. Capture lights ON & save", false);
        onButton.setEnabled(false);
        onButton.setAlpha(.52f);
        offButton.setOnClickListener(v -> {
            off[0] = activity.lights.readBrightness();
            boolean captured = LightSignal.validLevel(off[0]);
            onButton.setEnabled(captured);
            onButton.setAlpha(captured ? 1f : .52f);
            decorate(onButton, captured);
            decorate(offButton, !captured);
            feedback.setText(captured
                    ? "OFF captured at " + off[0] + ". Turn on parking lights or headlights, wait for the screen to dim, then capture ON."
                    : "Android is not reporting brightness. Factory dim can still work, but the photo needs a readable signal.");
        });
        onButton.setOnClickListener(v -> {
            int on = activity.lights.readBrightness();
            if (activity.lights.learnFactoryDim(off[0], on)) {
                dismiss();
                activity.toast("Headlight sync saved. Switch your lights off and on to test.");
            } else {
                String reading = LightSignal.validLevel(on) ? "ON read " + on + ", OFF read " + off[0] + ". " : "No ON reading. ";
                feedback.setText(reading + "Android did not report a distinct dim level. Wait until the screen dims and try again. If the levels stay the same, this firmware may not expose its dimming to apps.");
            }
        });
        s.body.addView(offButton, withMargin(8, dp(44)));
        s.body.addView(onButton, withMargin(6, dp(44)));
        if (activity.lights.hasFactoryDim()) {
            note(s.body, "Saved readings: OFF " + activity.lights.brightLevel() + "  ·  ON " + activity.lights.dimLevel());
            button(s.body, "Forget learned readings", false, () -> { activity.lights.forgetFactoryDim(); headlightSync(); });
        }
        if (activity.lights.hasOverrides()) {
            note(s.body, "Manual colors or photo lights are currently selected.");
            button(s.body, "Restore Auto for colors and photo", false, () -> { activity.lights.followLighting(); headlightSync(); });
        }
        button(s.body, "Advanced: documented OEM signal", false, this::oemIllumination);
        show(s);
        if (current == s.dialog) {
            lightStatus = live;
            activity.lights.showStatus(true);
            refreshLightStatus();
        }
    }

    private void oemIllumination() {
        Shell s = shell("ILLUMINATION INPUT", "OEM signal", true);
        note(s.body, activity.lights.source());
        note(s.body, "Only enter an action and extra documented for this firmware. The signal replaces learned brightness and affects the dashboard only. Accepts a boolean, 0/1, or on/off.");
        EditText action = field("Vendor broadcast action", activity.prefs.getString(HeadlightSync.OEM_ACTION, ""));
        EditText extra = field("Extra key", activity.prefs.getString(HeadlightSync.OEM_EXTRA, ""));
        s.body.addView(action, withMargin(5, dp(44)));
        s.body.addView(extra, withMargin(6, dp(44)));
        button(s.body, "Save OEM signal", true, () -> {
            String a = action.getText().toString().trim(), e = extra.getText().toString().trim();
            if (!a.matches("[A-Za-z_][A-Za-z0-9_.]{2,159}")) { action.setError("Enter the firmware's broadcast action"); return; }
            if (!e.matches("[A-Za-z0-9_.:$-]{1,128}")) { extra.setError("Enter the firmware's extra key"); return; }
            if (!activity.lights.setOem(a, e)) activity.toast("This illumination broadcast could not be registered.");
            headlightSync();
        });
        button(s.body, "Use Android theme instead", false, () -> { activity.lights.setOem("", ""); headlightSync(); });
        button(s.body, "Back to Headlight sync", false, this::headlightSync);
        show(s);
    }

    private void powerCues() {
        Shell s = shell("IGNITION & POWER", "Welcome & Good Bye", true);
        note(s.body, "Welcome plays when the launcher opens after Android boots. Good Bye can play before power-off if the screen is still on and the head unit sends a notification in time.");
        toggle(s.body, "Play Welcome and Good Bye tones", activity.powerTones(), activity::setPowerTones);
        button(s.body, "Preview Welcome", true, () -> { dismiss(); activity.replayWelcome(); });
        button(s.body, "Preview Good Bye (screen only)", false, () -> { dismiss(); activity.replayGoodbye(); });
        section(s.body, "Optional key-off signal");
        note(s.body, activity.ignitionStatus());
        note(s.body, "Full shutdown instead of sleep must be set in your head unit's own power settings, if available. The launcher does not power it off.");
        note(s.body, "Use only the documented ACC broadcast and state extra (1/on or 0/off). A vendor app sender also needs its documented signature-level sender permission. Leave all empty if unknown.");
        EditText action = field("ACC broadcast action", activity.prefs.getString(DashboardActivity.ACC_ACTION, ""));
        EditText extra = field("ACC state extra", activity.prefs.getString(DashboardActivity.ACC_EXTRA, ""));
        EditText permission = field("Sender permission (vendor-app broadcasts)", activity.prefs.getString(DashboardActivity.ACC_PERMISSION, ""));
        s.body.addView(action, withMargin(5, dp(44)));
        s.body.addView(extra, withMargin(6, dp(44)));
        s.body.addView(permission, withMargin(6, dp(44)));
        button(s.body, "Save key-off source", true, () -> {
            String a = action.getText().toString().trim(), e = extra.getText().toString().trim(), p = permission.getText().toString().trim();
            if (a.isEmpty() && e.isEmpty() && p.isEmpty()) { activity.setIgnition("", "", ""); powerCues(); return; }
            if (!PowerCue.validAction(a) || a.equals(Intent.ACTION_SHUTDOWN) || a.equals(Intent.ACTION_SCREEN_ON) || a.equals(Intent.ACTION_SCREEN_OFF)) {
                action.setError("Enter the OEM ACC broadcast action, not an Android screen or shutdown event"); return;
            }
            if (!PowerCue.validExtra(e)) { extra.setError("Enter the OEM state extra"); return; }
            if (!p.isEmpty() && !PowerCue.validAction(p)) { permission.setError("Enter the documented permission name"); return; }
            activity.setIgnition(a, e, p); powerCues();
        });
        show(s);
    }

    void drawer(String title, String assignment) { picker(title, assignment, false, false); }

    void bluetoothAudio(boolean launchAfterPick) { picker("Choose BT Audio app", DashboardActivity.BT_AUDIO, true, launchAfterPick); }

    private void picker(String title, String assignment, boolean bluetooth, boolean launchAfterPick) {
        Shell s = shell("ANDROID SHORTCUT", title, false);
        if (bluetooth) note(s.body, "Choose the head unit’s Bluetooth Audio app. If music is a tab in its Bluetooth app, choose that app.");
        EditText search = field("Find an installed app", "");
        s.body.addView(search, withMargin(13, dp(43)));
        List<AppShortcuts.Entry> all = bluetooth ? activity.apps.bluetoothChoices() : activity.apps.list();
        ListView list = new ListView(activity);
        list.setDivider(new ColorDrawable(palette.edge));
        list.setDividerHeight(dp(1));
        list.setSelector(new ColorDrawable(0x2aeda66f));
        list.setCacheColorHint(Color.TRANSPARENT);
        AppList items = new AppList(all, bluetooth);
        TextView empty = text(all.isEmpty() ? "No launchable apps found" : "No matching apps", 14, palette.muted, false);
        empty.setPadding(0, dp(16), 0, 0);
        list.setEmptyView(empty);
        list.setAdapter(items);
        int listHeight = Math.min(dp(bluetooth ? 215 : 260),
                (int) (activity.getResources().getDisplayMetrics().heightPixels * (bluetooth ? .34f : .43f)));
        s.body.addView(empty, matchWrap());
        s.body.addView(list, withMargin(9, listHeight));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence st, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence st, int start, int before, int count) { items.filter(st.toString()); }
            @Override public void afterTextChanged(Editable editable) {}
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            AppShortcuts.Entry entry = items.getItem(position);
            dismiss();
            String target = bluetooth ? entry.component.flattenToString() : entry.pkg;
            activity.prefs.edit().putString(assignment, target).apply();
            if (launchAfterPick && !activity.apps.launchBluetooth(target)) activity.toast("This Bluetooth app could not be opened.");
            else if (!launchAfterPick) activity.toast(entry.name + " shortcut saved.");
        });
        button(s.body, bluetooth ? "Use automatic detection" : "Clear shortcut assignment", false, () -> {
            activity.prefs.edit().remove(assignment).apply(); dismiss();
            activity.toast(bluetooth ? "BT Audio set to auto-detect." : "Shortcut assignment cleared.");
        });
        show(s);
    }

    private final class AppList extends BaseAdapter {
        private final List<AppShortcuts.Entry> original;
        private final List<AppShortcuts.Entry> visible;
        private final boolean bluetooth;
        AppList(List<AppShortcuts.Entry> source, boolean bluetooth) {
            this.bluetooth = bluetooth;
            original = source;
            visible = new ArrayList<>(source);
        }
        void filter(String search) {
            String q = search.trim().toLowerCase(Locale.ROOT);
            visible.clear();
            for (AppShortcuts.Entry item : original) if (item.name.toLowerCase(Locale.ROOT).contains(q)
                    || item.component.flattenToString().toLowerCase(Locale.ROOT).contains(q)) visible.add(item);
            notifyDataSetChanged();
        }
        @Override public int getCount() { return visible.size(); }
        @Override public AppShortcuts.Entry getItem(int position) { return visible.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View old, ViewGroup parent) {
            AppShortcuts.Entry app = getItem(position);
            LinearLayout row;
            if (old instanceof LinearLayout) row = (LinearLayout) old;
            else {
                row = new LinearLayout(activity);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(dp(50));
                row.setPadding(dp(8), dp(5), dp(8), dp(5));
                ImageView badge = new ImageView(activity);
                badge.setScaleType(ImageView.ScaleType.FIT_CENTER);
                row.addView(badge, new LinearLayout.LayoutParams(dp(29), dp(29)));
                LinearLayout details = new LinearLayout(activity);
                details.setOrientation(LinearLayout.VERTICAL);
                TextView name = text("", 14, palette.ink, true);
                name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END);
                details.addView(name);
                if (bluetooth) {
                    TextView extra = text("", 10, palette.muted, false);
                    extra.setSingleLine(true); extra.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                    details.addView(extra);
                }
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
                p.leftMargin = dp(10);
                row.addView(details, p);
            }
            Drawable badge = app.icon(activity.getPackageManager());
            ImageView appIcon = (ImageView) row.getChildAt(0);
            appIcon.setImageDrawable(badge == null ? activity.getDrawable(R.drawable.ic_apps) : badge);
            if (badge == null) appIcon.setColorFilter(palette.accent); else appIcon.clearColorFilter();
            LinearLayout details = (LinearLayout) row.getChildAt(1);
            ((TextView) details.getChildAt(0)).setText(app.name);
            if (bluetooth) ((TextView) details.getChildAt(1)).setText(app.component.flattenToShortString());
            return row;
        }
    }

    private static final class Shell {
        final Dialog dialog;
        final LinearLayout body;
        Shell(Dialog dialog, LinearLayout body) { this.dialog = dialog; this.body = body; }
    }

    private Shell shell(String label, String title, boolean scroll) {
        palette = ConsolePalette.forNight(activity.lights.night());
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(18), dp(14), dp(18), dp(16));
        shell.setBackground(box(palette.panel, palette.edge, 17));
        shell.setFocusableInTouchMode(true);
        LinearLayout head = new LinearLayout(activity);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout headings = new LinearLayout(activity);
        headings.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = text(label, 9, palette.accent, false);
        eyebrow.setTypeface(Typeface.MONOSPACE);
        eyebrow.setLetterSpacing(.13f);
        headings.addView(eyebrow, matchWrap());
        TextView heading = text(title, 25, palette.ink, true);
        if (Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        headings.addView(heading, matchWrap());
        head.addView(headings, new LinearLayout.LayoutParams(0, -2, 1));
        Button close = control("×", false);
        close.setTextSize(23);
        close.setContentDescription("Close panel");
        close.setOnClickListener(v -> dismiss());
        head.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        shell.addView(head, matchWrap());
        View rule = new View(activity);
        rule.setBackgroundColor(palette.edge);
        shell.addView(rule, withMargin(10, dp(1)));
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        if (scroll) {
            ScrollView scroller = new ScrollView(activity) {
                @Override protected void onMeasure(int wSpec, int hSpec) {
                    int cap = Math.min(dp(460), (int) (getResources().getDisplayMetrics().heightPixels * .68f));
                    if (MeasureSpec.getMode(hSpec) != MeasureSpec.UNSPECIFIED) cap = Math.min(cap, MeasureSpec.getSize(hSpec));
                    super.onMeasure(wSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST));
                }
            };
            scroller.setFillViewport(false);
            scroller.addView(content, new ScrollView.LayoutParams(-1, -2));
            shell.addView(scroller, matchWrap());
        } else shell.addView(content, matchWrap());
        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(d -> {
            if (current == dialog) { current = null; clearLightStatus(); }
        });
        shell.requestFocus();
        return new Shell(dialog, content);
    }

    private void show(Shell shell) {
        dismiss();
        if (activity.isFinishing() || activity.isDestroyed()) return;
        current = shell.dialog;
        Window window = current.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(palette == ConsolePalette.NIGHT ? .74f : .42f);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
        current.show();
        if (window != null) {
            window.setLayout(Math.max(dp(240), Math.min(dp(505), activity.getResources().getDisplayMetrics().widthPixels - dp(32))), -2);
        }
    }

    private void note(LinearLayout body, String value) {
        TextView t = text(value, 13, palette.muted, false);
        t.setLineSpacing(dp(2), 1f);
        body.addView(t, withMargin(11, -2));
    }

    private void section(LinearLayout body, String value) {
        TextView t = text(value, 13, palette.ink, true);
        body.addView(t, withMargin(14, -2));
    }

    private Button control(String value, boolean selected) {
        Button b = new Button(activity);
        b.setAllCaps(false);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setText(value);
        b.setTextSize(13);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinimumHeight(0);
        b.setMinHeight(0);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        decorate(b, selected);
        return b;
    }

    private void decorate(Button b, boolean selected) {
        b.setSelected(selected);
        b.setTextColor(selected ? palette.selectedInk : palette.ink);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x48eba66f),
                box(selected ? palette.selected : palette.inset, selected ? palette.selectedEdge : palette.edge, 9), null));
        b.setPadding(dp(9), 0, dp(9), 0);
    }

    private void button(LinearLayout body, String value, boolean selected, Runnable click) {
        Button button = control(value, selected);
        button.setOnClickListener(v -> click.run());
        body.addView(button, withMargin(6, dp(44)));
    }

    private interface Selected { void picked(int index); }
    private interface Checked { void changed(boolean value); }

    private void choices(LinearLayout body, String[] labels, int selection, Selected listener) {
        LinearLayout row = new LinearLayout(activity);
        Button[] buttons = new Button[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            Button b = control(labels[i], i == selection);
            buttons[i] = b;
            b.setOnClickListener(v -> {
                for (int j = 0; j < buttons.length; j++) decorate(buttons[j], j == index);
                listener.picked(index);
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(43), 1f);
            if (i > 0) p.leftMargin = dp(6);
            row.addView(b, p);
        }
        body.addView(row, withMargin(6, -2));
    }

    private void toggle(LinearLayout body, String value, boolean selected, Checked change) {
        Switch switchView = new Switch(activity);
        switchView.setText(value);
        switchView.setTextColor(palette.ink);
        int[][] states = {{android.R.attr.state_checked}, {}};
        switchView.setThumbTintList(new ColorStateList(states, new int[]{palette.accent, palette.muted}));
        switchView.setTrackTintList(new ColorStateList(states, new int[]{palette.selectedEdge, palette.edge}));
        switchView.setTextSize(13);
        switchView.setGravity(Gravity.CENTER_VERTICAL);
        switchView.setMinHeight(dp(46));
        switchView.setChecked(selected);
        switchView.setOnCheckedChangeListener((button, checked) -> change.changed(checked));
        body.addView(switchView, withMargin(5, -2));
    }

    private EditText field(String hint, String value) {
        EditText entry = new EditText(activity);
        entry.setSingleLine(true);
        entry.setTextSize(14);
        entry.setTextColor(palette.ink);
        entry.setHintTextColor(palette.muted);
        entry.setHint(hint);
        entry.setContentDescription(hint);
        entry.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        entry.setText(value);
        entry.setBackground(box(palette.inset, palette.edge, 8));
        entry.setPadding(dp(11), 0, dp(11), 0);
        return entry;
    }

    private TextView text(String name, int sp, int color, boolean bold) {
        TextView label = new TextView(activity);
        label.setText(name);
        label.setTextColor(color);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return label;
    }

    private GradientDrawable box(int fill, int border, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill); bg.setStroke(dp(1), border); bg.setCornerRadius(dp(radius));
        return bg;
    }

    private LinearLayout.LayoutParams withMargin(int top, int height) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, height);
        p.topMargin = dp(top);
        return p;
    }

    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(float d) { return Math.round(d * activity.getResources().getDisplayMetrics().density); }
}
