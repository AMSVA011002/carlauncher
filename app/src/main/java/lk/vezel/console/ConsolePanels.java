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
    private static final int TEXT = 0xfff0e8e4, MUTED = 0xffb2a6aa, AMBER = 0xffefa66f;
    private final DashboardActivity activity;
    private Dialog current;

    ConsolePanels(DashboardActivity activity) { this.activity = activity; }

    void dismiss() { if (current != null) { current.dismiss(); current = null; } }

    void brightness(float value) {
        if (current == null || current.getWindow() == null) return;
        Window window = current.getWindow();
        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.screenBrightness = value;
        window.setAttributes(attrs);
    }

    void settings() {
        Shell s = shell("VEZEL DRIVE CONSOLE", "Drive settings", true);
        note(s.body, "Set up your home screen and shortcuts while parked.");
        section(s.body, "Display mode");
        int mode = activity.prefs.getInt(DashboardActivity.MODE, 0);
        choices(s.body, new String[]{"Auto", "Day", "Night"}, mode == 1 ? 2 : mode == 2 ? 1 : 0, index -> {
            int next = index == 1 ? 2 : index == 2 ? 1 : 0;
            activity.prefs.edit().putInt(DashboardActivity.MODE, next).apply(); activity.appearance();
        });
        section(s.body, "Headlights and fog lights illustration");
        choices(s.body, new String[]{"Follow source", "On", "Off"}, activity.prefs.getInt(DashboardActivity.LAMPS, 0), index -> {
            activity.prefs.edit().putInt(DashboardActivity.LAMPS, index).apply(); activity.appearance();
        });
        button(s.body, "Configure OEM illumination signal", false, this::illumination);
        section(s.body, "Driving");
        button(s.body, "Speed alert  ·  " + (activity.alertEnabled() ? activity.limit() + " km/h" : "off"), true, this::speed);
        button(s.body, "Reset trip distance and drive time", false, this::confirmReset);
        section(s.body, "Android setup");
        button(s.body, "Home app, GPS and notification access", false, this::setup);
        button(s.body, "Open Android Wi-Fi settings", false, () -> {
            dismiss(); if (!activity.apps.start(new Intent(Settings.ACTION_WIFI_SETTINGS))) activity.toast("Wi-Fi settings are unavailable.");
        });
        section(s.body, "Navigation & music");
        boolean waze = DriveNotifications.WAZE.equals(activity.prefs.getString("navigation_app", DriveNotifications.MAPS));
        choices(s.body, new String[]{"Google Maps", "Waze"}, waze ? 1 : 0, index -> activity.prefs.edit()
                .putString("navigation_app", index == 1 ? DriveNotifications.WAZE : DriveNotifications.MAPS).apply());
        toggle(s.body, "Resume the last playing media session on startup", activity.prefs.getBoolean("resume_music", true), enabled -> {
            activity.prefs.edit().putBoolean("resume_music", enabled).apply();
            if (!enabled) activity.media.cancelResume();
        });
        section(s.body, "App shortcuts");
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
        section(s.body, "3. Music and turn previews");
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
        TextView count = text(value[0] + " km/h", 28, AMBER, true);
        count.setGravity(Gravity.CENTER);
        s.body.addView(count, matchWrap());
        SeekBar slider = new SeekBar(activity);
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
            note(s.body, "Allow notification access to read Android media sessions and navigation notifications.");
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
            TextView temperature = text(value.temperature + "°C  ·  " + value.condition(), 23, TEXT, true);
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

    private void illumination() {
        Shell s = shell("ILLUMINATION INPUT", "Headlight source", true);
        note(s.body, activity.lightSource());
        note(s.body, "Optional: enter the documented broadcast action and extra from your head unit. Accepts a boolean, 0/1, or on/off. This only changes the dashboard.");
        EditText action = field("Vendor broadcast action", activity.prefs.getString(DashboardActivity.OEM_ACTION, ""));
        EditText extra = field("Extra key", activity.prefs.getString(DashboardActivity.OEM_EXTRA, ""));
        s.body.addView(action, withMargin(5, dp(44)));
        s.body.addView(extra, withMargin(6, dp(44)));
        button(s.body, "Save illumination source", true, () -> {
            String a = action.getText().toString().trim(), e = extra.getText().toString().trim();
            if (a.isEmpty() && e.isEmpty()) { activity.setIllumination("", ""); settings(); return; }
            if (!a.matches("[A-Za-z_][A-Za-z0-9_.]{2,159}")) { action.setError("Enter the firmware's broadcast action"); return; }
            if (!e.matches("[A-Za-z0-9_.:$-]{1,128}")) { extra.setError("Enter the firmware's extra key"); return; }
            activity.setIllumination(a, e); settings();
        });
        button(s.body, "Use Android night mode only", false, () -> { activity.setIllumination("", ""); settings(); });
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

    void drawer(String title, String assignment) {
        Shell s = shell("ANDROID SHORTCUT", title, false);
        EditText search = field("Find an installed app", "");
        s.body.addView(search, withMargin(13, dp(43)));
        List<AppShortcuts.Entry> all = activity.apps.list();
        ListView list = new ListView(activity);
        list.setDivider(new ColorDrawable(0xff2e2931));
        list.setDividerHeight(dp(1));
        list.setSelector(new ColorDrawable(0x2aeda66f));
        list.setCacheColorHint(Color.TRANSPARENT);
        AppList items = new AppList(all);
        TextView empty = text(all.isEmpty() ? "No launchable apps found" : "No matching apps", 14, MUTED, false);
        empty.setPadding(0, dp(16), 0, 0);
        list.setEmptyView(empty);
        list.setAdapter(items);
        int listHeight = Math.min(dp(260), (int) (activity.getResources().getDisplayMetrics().heightPixels * .43f));
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
            activity.prefs.edit().putString(assignment, entry.pkg).apply();
            activity.toast(entry.name + " shortcut saved.");
        });
        button(s.body, "Clear shortcut assignment", false, () -> {
            activity.prefs.edit().remove(assignment).apply(); dismiss(); activity.toast("Shortcut assignment cleared.");
        });
        show(s);
    }

    private final class AppList extends BaseAdapter {
        private final List<AppShortcuts.Entry> original;
        private final List<AppShortcuts.Entry> visible;
        AppList(List<AppShortcuts.Entry> source) { original = source; visible = new ArrayList<>(source); }
        void filter(String search) {
            String q = search.trim().toLowerCase(Locale.ROOT);
            visible.clear();
            for (AppShortcuts.Entry item : original) if (item.name.toLowerCase(Locale.ROOT).contains(q)
                    || item.pkg.toLowerCase(Locale.ROOT).contains(q)) visible.add(item);
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
                TextView name = text("", 14, TEXT, true);
                name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
                p.leftMargin = dp(10);
                row.addView(name, p);
            }
            Drawable badge = app.icon(activity.getPackageManager());
            ((ImageView) row.getChildAt(0)).setImageDrawable(badge == null ? activity.getDrawable(R.drawable.ic_apps) : badge);
            ((TextView) row.getChildAt(1)).setText(app.name);
            return row;
        }
    }

    private static final class Shell {
        final Dialog dialog;
        final LinearLayout body;
        Shell(Dialog dialog, LinearLayout body) { this.dialog = dialog; this.body = body; }
    }

    private Shell shell(String label, String title, boolean scroll) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(18), dp(14), dp(18), dp(16));
        shell.setBackground(box(0xff18171d, 0xff514039, 17));
        shell.setFocusableInTouchMode(true);
        LinearLayout head = new LinearLayout(activity);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout headings = new LinearLayout(activity);
        headings.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = text(label, 9, AMBER, false);
        eyebrow.setTypeface(Typeface.MONOSPACE);
        eyebrow.setLetterSpacing(.13f);
        headings.addView(eyebrow, matchWrap());
        TextView heading = text(title, 25, TEXT, true);
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
        rule.setBackgroundColor(0xff393038);
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
        dialog.setOnDismissListener(d -> { if (current == dialog) current = null; });
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
            window.setDimAmount(.74f);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
        current.show();
        if (window != null) {
            window.setLayout(Math.max(dp(240), Math.min(dp(505), activity.getResources().getDisplayMetrics().widthPixels - dp(32))), -2);
            brightness(activity.getWindow().getAttributes().screenBrightness);
        }
    }

    private void note(LinearLayout body, String value) {
        TextView t = text(value, 13, MUTED, false);
        t.setLineSpacing(dp(2), 1f);
        body.addView(t, withMargin(11, -2));
    }

    private void section(LinearLayout body, String value) {
        TextView t = text(value, 13, TEXT, true);
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
        b.setTextColor(selected ? 0xffffc7a6 : TEXT);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x48eba66f),
                box(selected ? 0xff4b3026 : 0xff25232b, selected ? 0xff7d533e : 0xff3b353e, 9), null));
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
        switchView.setTextColor(TEXT);
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
        entry.setTextColor(TEXT);
        entry.setHintTextColor(MUTED);
        entry.setHint(hint);
        entry.setContentDescription(hint);
        entry.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        entry.setText(value);
        entry.setBackground(box(0xff101116, 0xff45363f, 8));
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
