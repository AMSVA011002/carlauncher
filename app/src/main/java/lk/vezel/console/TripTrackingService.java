package lk.vezel.console;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

/** Keeps GPS eligible while another app is in front; never owns audio or a wake lock. */
public final class TripTrackingService extends Service {
    private static final String CHANNEL = "drive_tracking", DASHBOARD_ONLY = "lk.vezel.console.DASHBOARD_ONLY";
    private static final int NOTIFICATION_ID = 41, PENDING_FLAGS = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;

    private DriveTracking tracking;
    private boolean foreground, registered;
    private final BroadcastReceiver monitor = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SHUTDOWN.equals(intent.getAction())) {
                tracking.shutdown();
                stopSelf();
            } else if (tracking.canRunBackground()) tracking.refresh();
            else stopSelf();
        }
    };

    /** Call only from the visible dashboard. Location is a while-in-use permission. */
    static void startFromDashboard(Context context, DriveTracking tracking) {
        if (!tracking.needsService()) return;
        Intent intent = new Intent(context, TripTrackingService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
            else context.startService(intent);
        } catch (RuntimeException denied) {
            Log.w("VezelTracking", "Background GPS could not start; use dashboard-only GPS", denied);
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        tracking = DriveTracking.get(this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && DASHBOARD_ONLY.equals(intent.getAction())) {
            tracking.setBackgroundEnabled(false);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!foreground) {
            try {
                Notification notification = notification();
                if (Build.VERSION.SDK_INT >= 29) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
                } else startForeground(NOTIFICATION_ID, notification);
                foreground = true;
                if (!tracking.canRunBackground()) { stopSelf(); return START_NOT_STICKY; }
                IntentFilter changes = new IntentFilter(Intent.ACTION_SCREEN_ON);
                changes.addAction(Intent.ACTION_SCREEN_OFF);
                changes.addAction(Intent.ACTION_SHUTDOWN);
                changes.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
                if (Build.VERSION.SDK_INT >= 33) registerReceiver(monitor, changes, Context.RECEIVER_NOT_EXPORTED);
                else registerReceiver(monitor, changes);
                registered = true;
                tracking.serviceActive(true);
            } catch (RuntimeException unavailable) {
                Log.w("VezelTracking", "Location foreground service unavailable; use dashboard-only GPS", unavailable);
                stopSelf();
            }
        }
        // Don't silently restart GPS after an Android stop or process kill. The next visible dashboard can start it.
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        if (registered) unregisterReceiver(monitor);
        tracking.serviceActive(false);
        tracking.checkpoint();
        if (foreground) stopForeground(true);
        super.onDestroy();
    }

    @SuppressWarnings("deprecation") // Pre-O notifications use the channel-less builder.
    private Notification notification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(CHANNEL, getString(R.string.trip_tracking_title),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
            builder = new Notification.Builder(this, CHANNEL);
        } else builder = new Notification.Builder(this).setPriority(Notification.PRIORITY_LOW);
        Intent home = new Intent(Intent.ACTION_MAIN).setClass(this, DashboardActivity.class)
                .addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent open = PendingIntent.getActivity(this, 0, home, PENDING_FLAGS);
        Intent stop = new Intent(this, TripTrackingService.class).setAction(DASHBOARD_ONLY);
        PendingIntent dashboardOnly = PendingIntent.getService(this, 1, stop, PENDING_FLAGS);
        return builder.setSmallIcon(R.drawable.ic_compass)
                .setContentTitle(getString(R.string.trip_tracking_title))
                .setContentText(getString(R.string.trip_tracking_text))
                .setContentIntent(open).setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
                .addAction(new Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_compass),
                        getString(R.string.trip_tracking_dashboard_only), dashboardOnly).build())
                .build();
    }
}
