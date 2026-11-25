package lk.vezel.console;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
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
        final String name, pkg, category;
        final ResolveInfo activity;
        final ComponentName component;
        final int bluetoothScore;
        Entry(String name, ResolveInfo info, String category, boolean bluetooth) {
            this.name = name;
            this.category = category;
            pkg = info.activityInfo.packageName;
            activity = info;
            component = new ComponentName(pkg, info.activityInfo.name);
            int flags = info.activityInfo.applicationInfo.flags;
            bluetoothScore = bluetooth ? BluetoothAudioMatch.score(name, component.getClassName(),
                    (flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) : 0;
        }
        Drawable icon(PackageManager manager) {
            try { return activity.loadIcon(manager); } catch (RuntimeException ignored) { return null; }
        }
    }

    private final Activity activity;
    private final PackageManager manager;

    AppShortcuts(Activity activity) { this.activity = activity; manager = activity.getPackageManager(); }

    boolean startExternal(Intent intent) {
        if (intent == null) return false;
        // Keep external screens out of the Home task. Bring existing tasks forward as-is;
        // Resetting the task on launch can clear the other app's activity stack.
        Intent external = new Intent(intent).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { activity.startActivity(external); return true; }
        catch (RuntimeException ignored) { return false; }
    }

    boolean launch(String pkg) {
        if (pkg == null || pkg.isEmpty() || pkg.equals(activity.getPackageName())) return false;
        try {
            Intent intent = manager.getLaunchIntentForPackage(pkg);
            if (intent == null) intent = manager.getLeanbackLaunchIntentForPackage(pkg);
            return startExternal(intent);
        } catch (RuntimeException ignored) { return false; }
    }

    boolean launch(Entry entry) { return launchActivity(entry.component, entry.category) || launch(entry.pkg); }

    private boolean launchActivity(ComponentName component, String category) {
        if (component == null || component.getPackageName().equals(activity.getPackageName())) return false;
        Intent intent = new Intent(Intent.ACTION_MAIN).setComponent(component);
        if (category != null) intent.addCategory(category);
        return startExternal(intent);
    }

    boolean launchBluetooth(String assigned) {
        if (!assigned.isEmpty()) return launchActivity(ComponentName.unflattenFromString(assigned), null);
        List<Entry> choices = bluetoothChoices();
        if (choices.isEmpty()) return false;
        Entry best = choices.get(0);
        int score = best.bluetoothScore;
        // Let the driver pick if no strong match exists or if the top score is tied.
        return score > 0 && (choices.size() == 1 || choices.get(1).bluetoothScore < score)
                && launchActivity(best.component, best.category);
    }

    String bluetoothLabel(String assigned) {
        if (assigned.isEmpty()) return "Auto-detect";
        try {
            ComponentName component = ComponentName.unflattenFromString(assigned);
            if (component != null) return manager.getActivityInfo(component, 0).loadLabel(manager).toString();
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) { }
        return "App unavailable";
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

    List<Entry> list() { return list(false); }
    List<Entry> bluetoothChoices() { return list(true); }

    private List<Entry> list(boolean bluetooth) {
        Map<String, Entry> found = new LinkedHashMap<>();
        collect(found, Intent.CATEGORY_LAUNCHER, bluetooth);
        collect(found, Intent.CATEGORY_LEANBACK_LAUNCHER, bluetooth);
        List<Entry> result = new ArrayList<>(found.values());
        Collator comparator = Collator.getInstance();
        result.sort((a, b) -> {
            int byMatch = bluetooth ? Integer.compare(b.bluetoothScore, a.bluetoothScore) : 0;
            return byMatch != 0 ? byMatch : comparator.compare(a.name, b.name);
        });
        return result;
    }

    private void collect(Map<String, Entry> result, String category, boolean byComponent) {
        try {
            for (ResolveInfo info : manager.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                if (info.activityInfo == null || info.activityInfo.applicationInfo == null || !info.activityInfo.exported) continue;
                String pkg = info.activityInfo.packageName;
                String key = byComponent ? new ComponentName(pkg, info.activityInfo.name).flattenToString() : pkg;
                if (result.containsKey(key) || pkg.equals(activity.getPackageName())) continue;
                CharSequence label = null;
                try { label = info.loadLabel(manager); } catch (RuntimeException ignored) { /* Use the package label below. */ }
                result.put(key, new Entry(label == null || label.length() == 0 ? pkg : label.toString(), info, category, byComponent));
            }
        } catch (RuntimeException ignored) { /* Firmware may only expose a limited package list. */ }
    }
}
