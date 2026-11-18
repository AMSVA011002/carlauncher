package lk.vezel.console;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import java.util.Collections;
import java.util.List;

/** Uses only Android's public media session APIs; never starts a third-party player by itself. */
final class MediaBridge {
    private static final String LAST = "last_media_package", PLAYING = "last_media_playing";
    private final MediaSessionManager manager;
    private final AudioManager audio;
    private final Context context;
    private final SharedPreferences prefs;
    private final Runnable onChange;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MediaSessionManager.OnActiveSessionsChangedListener listener = this::select;
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) { refresh(); }
        @Override public void onMetadataChanged(MediaMetadata metadata) { publish(true); }
        @Override public void onSessionDestroyed() { main.post(MediaBridge.this::refresh); }
    };
    private List<MediaController> sessions = Collections.emptyList();
    private MediaController current;
    private Bitmap albumArtSource;
    private MediaMetadata cachedMetadata;
    private boolean watching, listening, allowed, playing;
    private String title = "", artist = "";
    private Bitmap albumArt;
    private String resumePackage;
    private long resumeUntil, doNotRecordPauseUntil;

    MediaBridge(Context context, SharedPreferences prefs, Runnable onChange) {
        this.context = context;
        this.prefs = prefs;
        this.onChange = onChange;
        manager = context.getSystemService(MediaSessionManager.class);
        audio = context.getSystemService(AudioManager.class);
    }

    void start() { watching = true; refresh(); }

    void stop() {
        watching = false;
        if (listening && manager != null) {
            try { manager.removeOnActiveSessionsChangedListener(listener); } catch (RuntimeException ignored) {}
            listening = false;
        }
        unlink();
        sessions = Collections.emptyList();
        cachedMetadata = null;
        albumArtSource = null;
        albumArt = null;
        playing = false;
        title = artist = "";
    }

    void requestResume() {
        if (prefs.getBoolean("resume_music", true) && prefs.getBoolean(PLAYING, false)) {
            resumePackage = prefs.getString(LAST, null);
            resumeUntil = SystemClock.elapsedRealtime() + 14_000L;
        }
    }

    void cancelResume() { resumePackage = null; }

    void tick() {
        if (resumePackage == null) return;
        if (SystemClock.elapsedRealtime() >= resumeUntil) resumePackage = null;
        else if (watching) refresh();
    }

    void refresh() {
        if (!watching) return;
        allowed = DriveNotifications.isAllowed(context);
        if (manager == null || !allowed) { select(Collections.emptyList()); return; }
        try {
            if (!listening) {
                manager.addOnActiveSessionsChangedListener(listener, DriveNotifications.component(context), main);
                listening = true;
            }
            select(manager.getActiveSessions(DriveNotifications.component(context)));
        } catch (RuntimeException ignored) {
            select(Collections.emptyList());
        }
    }

    private void select(List<MediaController> list) {
        if (!watching) return;
        sessions = list == null ? Collections.emptyList() : list;
        MediaController best = null;
        String last = prefs.getString(LAST, "");
        for (MediaController candidate : sessions) {
            if (isPlaying(state(candidate))) { best = candidate; break; }
            if (best == null || candidate.getPackageName().equals(last)) best = candidate;
        }
        if (!same(best, current)) {
            unlink();
            current = best;
            if (current != null) {
                try { current.registerCallback(callback, main); } catch (RuntimeException ignored) {}
            }
            cachedMetadata = null;
            albumArtSource = null;
            albumArt = null;
        }
        maybeResume();
        publish(false);
    }

    private void maybeResume() {
        if (resumePackage == null) return;
        long now = SystemClock.elapsedRealtime();
        if (now >= resumeUntil || !prefs.getBoolean("resume_music", true)) { resumePackage = null; return; }
        MediaController target = null;
        for (MediaController candidate : sessions) {
            if (isPlaying(state(candidate))) { resumePackage = null; return; }
            if (resumePackage.equals(candidate.getPackageName())) target = candidate;
        }
        PlaybackState last = state(target);
        if (target == null || last == null || (last.getActions() & (PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PLAY_PAUSE)) == 0) return;
        if (last.getState() != PlaybackState.STATE_PAUSED && last.getState() != PlaybackState.STATE_STOPPED) return;
        resumePackage = null;
        doNotRecordPauseUntil = now + 4_000L;
        try { target.getTransportControls().play(); } catch (RuntimeException ignored) {}
    }

    private void publish(boolean changedMetadata) {
        if (!watching) return;
        MediaMetadata metadata = null;
        try { if (current != null) metadata = current.getMetadata(); } catch (RuntimeException ignored) {}
        PlaybackState playback = state(current);
        playing = isPlaying(playback);
        if (current != null && playback != null) {
            String pkg = current.getPackageName();
            String last = prefs.getString(LAST, "");
            if (playing && (!last.equals(pkg) || !prefs.getBoolean(PLAYING, false))) {
                prefs.edit().putString(LAST, pkg).putBoolean(PLAYING, true).apply();
            } else if (playback.getState() == PlaybackState.STATE_PAUSED && last.equals(pkg)
                    && prefs.getBoolean(PLAYING, false) && resumePackage == null
                    && SystemClock.elapsedRealtime() >= doNotRecordPauseUntil) {
                prefs.edit().putBoolean(PLAYING, false).apply();
            }
        }
        if (changedMetadata || metadata != cachedMetadata) {
            cachedMetadata = metadata;
            title = text(metadata, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            artist = text(metadata, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                    MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, MediaMetadata.METADATA_KEY_ALBUM);
            Bitmap raw = art(metadata);
            if (raw != albumArtSource) {
                albumArtSource = raw;
                albumArt = scaled(raw);
            }
        }
        onChange.run();
    }

    private static String text(MediaMetadata data, String... keys) {
        if (data != null) for (String key : keys) {
            CharSequence value = data.getText(key);
            if (value != null && value.length() > 0) {
                String text = value.subSequence(0, Math.min(300, value.length())).toString().replace('\n', ' ').trim();
                if (!text.isEmpty()) return text;
            }
        }
        return "";
    }

    private static Bitmap art(MediaMetadata metadata) {
        if (metadata == null) return null;
        for (String key : new String[]{MediaMetadata.METADATA_KEY_DISPLAY_ICON, MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_ART}) {
            Bitmap raw = metadata.getBitmap(key);
            if (raw != null && !raw.isRecycled()) return raw;
        }
        return null;
    }

    private static Bitmap scaled(Bitmap raw) {
        if (raw == null || raw.isRecycled()) return null;
        try {
            float factor = Math.min(1f, 96f / Math.max(raw.getWidth(), raw.getHeight()));
            return Bitmap.createScaledBitmap(raw, Math.max(1, Math.round(raw.getWidth() * factor)),
                    Math.max(1, Math.round(raw.getHeight() * factor)), true);
        } catch (IllegalArgumentException ignored) { return null; }
    }

    boolean press(int key) {
        cancelResume();
        if (current != null) {
            try {
                MediaController.TransportControls controls = current.getTransportControls();
                if (key == KeyEvent.KEYCODE_MEDIA_NEXT) controls.skipToNext();
                else if (key == KeyEvent.KEYCODE_MEDIA_PREVIOUS) controls.skipToPrevious();
                else if (playing) {
                    prefs.edit().putBoolean(PLAYING, false).apply();
                    controls.pause();
                } else controls.play();
                return true;
            } catch (RuntimeException ignored) { /* Try a standard media key instead. */ }
        }
        if (audio == null || !allowed) return false;
        try {
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
            return true;
        } catch (RuntimeException ignored) { return false; }
    }

    boolean allowed() { return allowed; }
    boolean playing() { return playing; }
    String title() { return title; }
    String artist() { return artist; }
    Bitmap albumArt() { return albumArt; }
    String packageName() { return current != null ? current.getPackageName() : null; }

    private void unlink() {
        if (current != null) {
            try { current.unregisterCallback(callback); } catch (RuntimeException ignored) {}
            current = null;
        }
    }

    private static boolean same(MediaController one, MediaController two) {
        return one == null ? two == null : two != null && one.getSessionToken().equals(two.getSessionToken());
    }

    private static PlaybackState state(MediaController controller) {
        try { return controller != null ? controller.getPlaybackState() : null; }
        catch (RuntimeException ignored) { return null; }
    }

    private static boolean isPlaying(PlaybackState playback) {
        return playback != null && playback.getState() == PlaybackState.STATE_PLAYING;
    }
}
