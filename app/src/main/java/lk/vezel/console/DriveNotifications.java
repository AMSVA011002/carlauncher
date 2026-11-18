package lk.vezel.console;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;

/** Retains the same notification access grant for media sessions; no notification text is read. */
public final class DriveNotifications extends NotificationListenerService {
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
}
