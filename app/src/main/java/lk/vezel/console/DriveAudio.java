package lk.vezel.console;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;

import java.io.IOException;

/** Plays a single short local clip without owning a media session or overriding system volume. */
final class DriveAudio {
    enum Cue {
        WARNING("speed-warning.mp3", AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, AudioAttributes.CONTENT_TYPE_SPEECH, .84f),
        WELCOME("welcome.wav", AudioAttributes.USAGE_MEDIA, AudioAttributes.CONTENT_TYPE_MUSIC, .80f),
        GOODBYE("goodbye.wav", AudioAttributes.USAGE_MEDIA, AudioAttributes.CONTENT_TYPE_MUSIC, .80f);

        final String asset;
        final AudioAttributes attributes;
        final float gain;
        Cue(String asset, int usage, int kind, float gain) {
            this.asset = "audio/" + asset;
            attributes = new AudioAttributes.Builder().setUsage(usage).setContentType(kind).build();
            this.gain = gain;
        }
    }

    private final Context context;
    private final AudioManager audio;
    private MediaPlayer player;
    private AudioFocusRequest request;
    private boolean legacyFocus;
    private Cue playing;
    private final AudioManager.OnAudioFocusChangeListener focus = change -> {
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) stop();
    };

    DriveAudio(Context context) {
        this.context = context;
        audio = context.getSystemService(AudioManager.class);
    }

    void play(Cue cue) {
        stop();
        if (audio == null) return;
        try (AssetFileDescriptor cueFile = context.getAssets().openFd(cue.asset)) {
            MediaPlayer next = new MediaPlayer();
            player = next;
            playing = cue;
            next.setAudioAttributes(cue.attributes);
            next.setDataSource(cueFile.getFileDescriptor(), cueFile.getStartOffset(), cueFile.getLength());
            next.setVolume(cue.gain, cue.gain);
            next.setOnCompletionListener(this::finished);
            next.setOnErrorListener((p, what, extra) -> { finished(p); return true; });
            next.setOnPreparedListener(p -> {
                if (p != player) return;
                try {
                    if (takeFocus(cue)) p.start(); else stop();
                } catch (RuntimeException unavailable) { stop(); }
            });
            next.prepareAsync();
        } catch (IOException | RuntimeException unavailable) { stop(); }
    }

    private void finished(MediaPlayer p) { if (player == p) stop(); }

    void stopWarning() { if (playing == Cue.WARNING) stop(); }
    void stopGreeting() { if (playing != Cue.WARNING) stop(); }

    @SuppressWarnings("deprecation")
    private boolean takeFocus(Cue cue) {
        if (Build.VERSION.SDK_INT >= 26) {
            request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(cue.attributes).setOnAudioFocusChangeListener(focus).build();
            if (audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return true;
            request = null;
            return false;
        }
        legacyFocus = audio.requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return legacyFocus;
    }

    @SuppressWarnings("deprecation")
    void stop() {
        if (player != null) {
            MediaPlayer last = player;
            player = null;
            last.release();
        }
        playing = null;
        try {
            if (Build.VERSION.SDK_INT >= 26 && request != null) audio.abandonAudioFocusRequest(request);
            else if (legacyFocus) audio.abandonAudioFocus(focus);
        } catch (RuntimeException ignored) { /* Some head units do not implement Android audio focus. */ }
        request = null;
        legacyFocus = false;
    }
}
