package lk.vezel.console;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AppShortcuts {
    static final class Entry {
        final String name, pkg;
        final ResolveInfo activity;
        Entry(String name, ResolveInfo info) { this.name = name; pkg = info.activityInfo.packageName; activity = info; }
        Drawable icon(PackageManager manager) {
            try { return activity.loadIcon(manager); } catch (RuntimeException ignored) { return null; }
        }
    }

    private final Activity activity;
    private final PackageManager manager;

    AppShortcuts(Activity activity) { this.activity = activity; manager = activity.getPackageManager(); }

    boolean start(Intent intent) {
        if (intent == null) return false;
        try { activity.startActivity(intent); return true; }
        catch (RuntimeException ignored) { return false; }
    }

    boolean launch(String pkg) {
        if (pkg == null || pkg.isEmpty() || pkg.equals(activity.getPackageName())) return false;
        try {
            Intent intent = manager.getLaunchIntentForPackage(pkg);
            if (intent == null) intent = manager.getLeanbackLaunchIntentForPackage(pkg);
            return start(intent);
        } catch (RuntimeException ignored) { return false; }
    }

    boolean launch(Entry entry) {
        ResolveInfo info = entry.activity;
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .setComponent(new ComponentName(entry.pkg, info.activityInfo.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return start(intent) || launch(entry.pkg);
    }

    boolean home() {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                RoleManager role = activity.getSystemService(RoleManager.class);
                if (role != null && role.isRoleAvailable(RoleManager.ROLE_HOME)) return role.isRoleHeld(RoleManager.ROLE_HOME);
            } catch (RuntimeException ignored) { /* Fall back to the default Home intent on vendor builds. */ }
        }
        try {
            ResolveInfo resolved = manager.resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                    PackageManager.MATCH_DEFAULT_ONLY);
            return resolved != null && resolved.activityInfo != null && activity.getPackageName().equals(resolved.activityInfo.packageName);
        } catch (RuntimeException ignored) { return false; }
    }

    String label(String pkg) {
        if (pkg == null || pkg.isEmpty()) return "Not assigned";
        try { return manager.getApplicationLabel(manager.getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception ignored) { return "App unavailable"; }
    }

    List<Entry> list() {
        Map<String, Entry> found = new LinkedHashMap<>();
        collect(found, Intent.CATEGORY_LAUNCHER);
        collect(found, Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<Entry> result = new ArrayList<>(found.values());
        Collator comparator = Collator.getInstance();
        result.sort((a, b) -> comparator.compare(a.name, b.name));
        return result;
    }

    private void collect(Map<String, Entry> result, String category) {
        try {
            for (ResolveInfo info : manager.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                if (info.activityInfo == null || !info.activityInfo.exported) continue;
                String pkg = info.activityInfo.packageName;
                if (result.containsKey(pkg) || pkg.equals(activity.getPackageName())) continue;
                CharSequence label = info.loadLabel(manager);
                result.put(pkg, new Entry(label == null ? pkg : label.toString(), info));
            }
        } catch (RuntimeException ignored) { /* Firmware may only expose a limited package list. */ }
    }
}
