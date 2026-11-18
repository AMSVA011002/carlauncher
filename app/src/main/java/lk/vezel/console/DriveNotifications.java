package lk.vezel.console;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;

/** Only reads the public text of ongoing route notifications from two known navigation apps. */
public final class DriveNotifications extends NotificationListenerService {
    static final String MAPS = "com.google.android.apps.maps", WAZE = "com.waze";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static DriveNotifications connected;
    private static Runnable observer;
    private static final Runnable NOTIFY = () -> { if (observer != null) observer.run(); };

    static final class Route {
        final NavigationText.Hint hint;
        final String packageName;
        final PendingIntent open;

        Route(NavigationText.Hint hint, String packageName, PendingIntent open) {
            this.hint = hint;
            this.packageName = packageName;
            this.open = open;
        }
    }

    static ComponentName component(Context context) { return new ComponentName(context, DriveNotifications.class); }

    static boolean isAllowed(Context context) {
        try {
            ComponentName own = component(context);
            if (Build.VERSION.SDK_INT >= 27) {
                NotificationManager manager = context.getSystemService(NotificationManager.class);
                return manager != null && manager.isNotificationListenerAccessGranted(own);
            }
            String value = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
            if (value == null) return false;
            for (String item : value.split(":")) if (own.equals(ComponentName.unflattenFromString(item))) return true;
        } catch (RuntimeException ignored) { /* Some aftermarket Android builds hide this setting. */ }
        return false;
    }

    static void watch(Runnable callback) { observer = callback; }

    static Route route() {
        if (connected == null) return null;
        try {
            StatusBarNotification[] notifications = connected.getActiveNotifications();
            if (notifications == null) return null;
            Route best = null;
            long latest = -1;
            for (StatusBarNotification item : notifications) {
                if (!supported(item.getPackageName())) continue;
                Notification notification = item.getNotification();
                if (notification == null || notification.extras == null) continue;
                if ((notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
                if (!item.isOngoing() && !Notification.CATEGORY_NAVIGATION.equals(notification.category)) continue;
                if (item.getPostTime() < latest) continue;
                NavigationText.Hint hint = parse(notification.extras);
                if (hint != null) {
                    best = new Route(hint, item.getPackageName(), notification.contentIntent);
                    latest = item.getPostTime();
                }
            }
            return best;
        } catch (RuntimeException ignored) { return null; }
    }

    private static NavigationText.Hint parse(Bundle extras) {
        List<String> fields = new ArrayList<>(12);
        for (String key : new String[]{Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT,
                Notification.EXTRA_TITLE_BIG}) {
            fields.add(text(extras, key));
        }
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) for (int i = 0; i < lines.length && i < 8; i++) if (lines[i] != null) fields.add(lines[i].toString());
        return NavigationText.withSummary(NavigationText.parse(fields.toArray(new String[0])),
                text(extras, Notification.EXTRA_SUB_TEXT), text(extras, Notification.EXTRA_INFO_TEXT));
    }

    private static String text(Bundle extras, String key) {
        CharSequence value = extras.getCharSequence(key);
        return value == null ? null : value.toString();
    }

    private static boolean supported(String pkg) { return MAPS.equals(pkg) || WAZE.equals(pkg); }
    private static void changed() { MAIN.removeCallbacks(NOTIFY); MAIN.post(NOTIFY); }

    @Override public void onListenerConnected() { MAIN.post(() -> { connected = this; changed(); }); }
    @Override public void onListenerDisconnected() { disconnect(); }
    @Override public void onDestroy() { disconnect(); super.onDestroy(); }
    @Override public void onNotificationPosted(StatusBarNotification sbn) { if (sbn != null && supported(sbn.getPackageName())) changed(); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { if (sbn != null && supported(sbn.getPackageName())) changed(); }

    private void disconnect() { MAIN.post(() -> { if (connected == this) { connected = null; changed(); } }); }
}
