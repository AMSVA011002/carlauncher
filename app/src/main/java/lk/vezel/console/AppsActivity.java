package lk.vezel.console;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Dedicated page for all visible, launchable Android apps. No vehicle GL context is used here. */
public final class AppsActivity extends Activity {
    static final String NIGHT = "dark_display";
    private static final int TEXT = 0xfff4eae5, MUTED = 0xffb9a7a9, GOLD = 0xffedaa76;
    private AppShortcuts apps;
    private EditText search;
    private TextView count, empty;
    private AppGrid items;
    private boolean loading = true;
    private List<AppShortcuts.Entry> all = Collections.emptyList();
    private final LruCache<String, Bitmap> icons = new LruCache<>(36);

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        apps = new AppShortcuts(this);
        if (getIntent().getBooleanExtra(NIGHT, false)) {
            WindowManager.LayoutParams dim = getWindow().getAttributes();
            dim.screenBrightness = .20f;
            getWindow().setAttributes(dim);
        }
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setFocusableInTouchMode(true);
        page.setPadding(dp(22), dp(13), dp(22), dp(9));
        page.setBackground(new GradientDrawable(GradientDrawable.Orientation.TR_BL, new int[]{0xff21191d, 0xff111217, 0xff0e0f13}));
        setContentView(page);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark = new ImageView(this);
        mark.setPadding(dp(10), dp(10), dp(10), dp(10));
        mark.setImageResource(R.drawable.ic_honda);
        mark.setImageTintList(ColorStateList.valueOf(GOLD));
        mark.setBackground(box(0xff33241f, 0xff694737, 11));
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(mark, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout name = new LinearLayout(this);
        name.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = text("VEZEL  /  APPLICATIONS", 8.5f, GOLD, Typeface.MONOSPACE);
        eyebrow.setLetterSpacing(.15f);
        name.addView(eyebrow);
        TextView title = text("All apps", 28, TEXT, Typeface.create("sans-serif-medium", 0));
        if (Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        name.addView(title);
        LinearLayout.LayoutParams heading = new LinearLayout.LayoutParams(0, -2, 1);
        heading.leftMargin = dp(12);
        header.addView(name, heading);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        search.setTextSize(13);
        search.setTextColor(TEXT);
        search.setHintTextColor(MUTED);
        search.setHint("Find an app");
        search.setPadding(dp(12), 0, dp(12), 0);
        search.setBackground(box(0xff17151d, 0xff50414a, 9));
        int searchWidth = Math.min(dp(296), getResources().getDisplayMetrics().widthPixels / 3);
        header.addView(search, new LinearLayout.LayoutParams(searchWidth, dp(45)));
        Button back = new Button(this);
        back.setText("‹  Dashboard");
        back.setTextColor(TEXT);
        back.setTextSize(12);
        back.setAllCaps(false);
        back.setSingleLine(true);
        back.setContentDescription("Back to dashboard");
        back.setBackground(touchBox(0xff2c2127, 0xff65484a, 9));
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backSize = new LinearLayout.LayoutParams(dp(112), dp(45));
        backSize.leftMargin = dp(9);
        header.addView(back, backSize);
        page.addView(header, new LinearLayout.LayoutParams(-1, dp(64)));

        View rule = new View(this);
        rule.setBackgroundColor(0xff40333b);
        page.addView(rule, new LinearLayout.LayoutParams(-1, dp(1)));
        count = text("", 10, MUTED, Typeface.MONOSPACE);
        count.setGravity(Gravity.CENTER_VERTICAL);
        count.setLetterSpacing(.12f);
        page.addView(count, new LinearLayout.LayoutParams(-1, dp(35)));

        FrameLayout listArea = new FrameLayout(this);
        page.addView(listArea, new LinearLayout.LayoutParams(-1, 0, 1));
        GridView grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(dp(131));
        grid.setHorizontalSpacing(dp(10));
        grid.setVerticalSpacing(dp(10));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setClipToPadding(false);
        grid.setPadding(0, 0, 0, dp(8));
        grid.setDrawSelectorOnTop(true);
        grid.setSelector(box(0x22eca76e, 0xffe7a879, 12));
        listArea.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        empty = text("Loading apps…", 15, MUTED, Typeface.DEFAULT);
        empty.setGravity(Gravity.CENTER);
        listArea.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        items = new AppGrid();
        grid.setAdapter(items);
        grid.setEmptyView(empty);
        grid.setOnItemClickListener((parent, tile, index, id) -> {
            if (apps.launch(items.getItem(index))) finish();
            else Toast.makeText(this, "This app could not be opened.", Toast.LENGTH_SHORT).show();
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int removed, int added) {}
            @Override public void onTextChanged(CharSequence s, int start, int removed, int added) { filter(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
        search.setOnEditorActionListener((field, action, key) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            InputMethodManager input = getSystemService(InputMethodManager.class);
            if (input != null) input.hideSoftInputFromWindow(search.getWindowToken(), 0);
            if (items.getCount() > 0) grid.requestFocus(); else back.requestFocus();
            return true;
        });
        if (state != null) search.setText(state.getString("query", ""));
        page.requestFocus();
        ConsoleWindow.immersive(this);
        filter(search.getText().toString());
        new Thread(() -> {
            List<AppShortcuts.Entry> installed = apps.list();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                all = installed;
                loading = false;
                filter(search.getText().toString());
            });
        }, "List installed apps").start();
    }

    private void filter(String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        items.shown.clear();
        for (AppShortcuts.Entry entry : all) if (entry.name.toLowerCase(Locale.ROOT).contains(needle)
                || entry.pkg.toLowerCase(Locale.ROOT).contains(needle)) items.shown.add(entry);
        items.notifyDataSetChanged();
        int size = items.getCount();
        count.setText(loading ? "ANDROID  /  FINDING APPS" : size + (size == 1 ? " APP" : " APPS") + "  /  A–Z");
        empty.setText(loading ? "Loading apps…" : all.isEmpty() ? "No launchable apps found" : "No apps match your search");
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("query", search.getText().toString());
        super.onSaveInstanceState(out);
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) ConsoleWindow.immersive(this);
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW) icons.evictAll();
    }

    private TextView text(String value, float size, int color, Typeface font) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTypeface(font); view.setTextColor(color);
        view.setIncludeFontPadding(false);
        return view;
    }

    private GradientDrawable box(int background, int border, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(background); drawable.setStroke(dp(1), border); drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private Drawable touchBox(int background, int border, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(0x3de4aa85), box(background, border, radius), null);
    }

    private int dp(float value) { return Math.max(1, Math.round(value * getResources().getDisplayMetrics().density)); }

    private final class AppGrid extends BaseAdapter {
        private final List<AppShortcuts.Entry> shown = new ArrayList<>();
        @Override public int getCount() { return shown.size(); }
        @Override public AppShortcuts.Entry getItem(int position) { return shown.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View recycled, ViewGroup parent) {
            AppShortcuts.Entry item = getItem(position);
            LinearLayout tile;
            if (recycled instanceof LinearLayout) tile = (LinearLayout) recycled;
            else {
                tile = new LinearLayout(AppsActivity.this);
                tile.setLayoutParams(new AbsListView.LayoutParams(-1, dp(103)));
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setGravity(Gravity.CENTER);
                tile.setPadding(dp(7), 0, dp(7), 0);
                tile.setBackground(box(0xff1c1a21, 0xff41343d, 12));
                ImageView icon = new ImageView(AppsActivity.this);
                icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                tile.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
                TextView label = text("", 12.5f, TEXT, Typeface.create("sans-serif-medium", 0));
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(2);
                label.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams labelSize = new LinearLayout.LayoutParams(-1, -2);
                labelSize.topMargin = dp(8);
                tile.addView(label, labelSize);
            }
            ImageView iconView = (ImageView) tile.getChildAt(0);
            Bitmap iconBitmap = icons.get(item.pkg);
            if (iconBitmap == null) {
                Drawable source = item.icon(getPackageManager());
                if (source != null) {
                    int size = Math.min(dp(44), 68);
                    iconBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                    source.setBounds(0, 0, size, size);
                    try { source.draw(new Canvas(iconBitmap)); icons.put(item.pkg, iconBitmap); }
                    catch (RuntimeException unavailable) { iconBitmap = null; }
                }
            }
            if (iconBitmap == null) iconView.setImageResource(R.drawable.ic_apps);
            else iconView.setImageBitmap(iconBitmap);
            ((TextView) tile.getChildAt(1)).setText(item.name);
            return tile;
        }
    }
}
