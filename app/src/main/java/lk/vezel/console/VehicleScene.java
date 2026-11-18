package lk.vezel.console;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Outline;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityNodeInfo;

import java.io.IOException;

/** The texture is an ordinary child, so Android can put dashboard controls over the 3D view. */
final class VehicleScene extends TextureView implements TextureView.SurfaceTextureListener {
    private final Runnable onReadyChanged;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final int touchSlop;
    private Worker worker;
    private SurfaceTexture texture;
    private int generation, failure = -1;
    private boolean active, ready, inspection, lights, night, gestures = true;
    private float yaw = VehicleCamera.DEFAULT_YAW, pitch = VehicleCamera.DEFAULT_PITCH, zoom = 1f;
    private float lastX, lastY, span;
    private boolean moved;

    VehicleScene(Context context, Runnable onReadyChanged, Runnable inspect) {
        super(context);
        this.onReadyChanged = onReadyChanged;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setOpaque(false);
        setSurfaceTextureListener(this);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                float radius = inspection ? 0 : 16 * getWidth() / 696f;
                outline.setRoundRect(0, 0, getWidth(), getHeight(), radius);
            }
        });
        setClipToOutline(true);
        setOnClickListener(v -> { if (ready && !inspection && gestures) inspect.run(); });
        setContentDescription("Honda Vezel, 3D model. Drag to rotate and pinch to zoom.");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setFocusable(false);
        setVisibility(INVISIBLE);
    }

    boolean ready() { return ready; }

    void setActive(boolean value) {
        if (active == value) return;
        active = value;
        generation++;
        if (value) {
            setVisibility(VISIBLE);
            submit();
        } else {
            if (worker != null) worker.retire(null);
            setVisibility(INVISIBLE);
            ready(false);
        }
    }

    void setGestures(boolean enabled) {
        gestures = enabled;
        updateAccessibility();
    }

    void appearance(boolean lamps, boolean dark) {
        if (lights == lamps && night == dark) return;
        lights = lamps;
        night = dark;
        submit();
    }

    void inspection(boolean enabled) {
        inspection = enabled;
        zoom = 1;
        invalidateOutline();
        submit();
    }

    /** 0 restores the studio view, 1 looks head-on, 2 looks straight at the hatch. */
    void preset(int mode) {
        yaw = mode == 2 ? (float) Math.PI : mode == 1 ? 0 : VehicleCamera.DEFAULT_YAW;
        pitch = mode == 0 ? VehicleCamera.DEFAULT_PITCH : VehicleCamera.CLOSE_PITCH;
        zoom = mode == 0 ? 1 : VehicleCamera.CLOSE_ZOOM;
        submit();
    }

    private void turn(float dx, float dy) {
        if (dx == 0 && dy == 0) return;
        yaw = (yaw + dx) % VehicleCamera.TURN;
        pitch = VehicleCamera.clamp(pitch + dy, VehicleCamera.MIN_PITCH, VehicleCamera.MAX_PITCH);
        submit();
    }

    private void magnify(float factor) {
        float z = VehicleCamera.clamp(zoom * factor, VehicleCamera.MIN_ZOOM, VehicleCamera.MAX_ZOOM);
        if (z == zoom) return;
        zoom = z;
        submit();
    }

    private void ready(boolean value) {
        if (ready == value) return;
        ready = value;
        updateAccessibility();
        onReadyChanged.run();
    }

    private void updateAccessibility() {
        boolean interactive = ready && gestures;
        setFocusable(interactive);
        setImportantForAccessibility(interactive ? IMPORTANT_FOR_ACCESSIBILITY_YES : IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        texture = surface;
        texture.setDefaultBufferSize(VehicleRenderer.WIDTH, VehicleRenderer.HEIGHT);
        generation++;
        submit();
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        surface.setDefaultBufferSize(VehicleRenderer.WIDTH, VehicleRenderer.HEIGHT);
        submit();
    }

    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        texture = null;
        generation++;
        ready(false);
        if (worker == null) return true;
        worker.retire(surface);
        return false; // Worker releases the texture after EGL has stopped using it.
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Worker detached = worker;
        if (detached == null) return;
        // TextureView may deliver onSurfaceTextureDestroyed later in the detach pass.
        // Keep the worker alive until it has accepted ownership of that texture.
        main.post(() -> {
            if (worker == detached && !isAttachedToWindow()) {
                worker = null;
                detached.retire(null);
                detached.thread.quitSafely();
            }
        });
    }

    private void submit() {
        int w = getWidth(), h = getHeight();
        if (!active || texture == null || failure == generation || w < 2 || h < 2 || getVisibility() != VISIBLE) return;
        if (worker == null) worker = new Worker(getContext().getAssets());
        worker.queue(new Frame(texture, generation, yaw, pitch, zoom, w / (float) h, lights, night, inspection));
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!ready || !gestures) return false;
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = x; lastY = y; span = 0; moved = false;
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                span = distance(e); moved = true;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (e.getPointerCount() >= 2) {
                    float next = distance(e);
                    if (span > 8 && next > 8) magnify(next / span);
                    span = next;
                } else if (moved || Math.hypot(x - lastX, y - lastY) >= touchSlop) {
                    turn((lastX - x) * VehicleCamera.TURN / Math.max(1, getWidth()), (y - lastY) * .009f);
                    lastX = x; lastY = y; moved = true;
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                int remaining = e.getActionIndex() == 0 ? 1 : 0;
                lastX = e.getX(remaining); lastY = e.getY(remaining); span = 0;
                return true;
            case MotionEvent.ACTION_UP:
                if (!moved) performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                return true;
            default:
                return false;
        }
    }

    private static float distance(MotionEvent event) {
        return (float) Math.hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1));
    }

    @Override public boolean onGenericMotionEvent(MotionEvent event) {
        if (ready && gestures && event.getActionMasked() == MotionEvent.ACTION_SCROLL) {
            magnify((float) Math.exp(event.getAxisValue(MotionEvent.AXIS_VSCROLL) * .15f));
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    @Override public boolean onKeyDown(int code, KeyEvent event) {
        if (ready && gestures && inspection) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT: turn(.26f, 0); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT: turn(-.26f, 0); return true;
                case KeyEvent.KEYCODE_DPAD_UP: turn(0, .14f); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN: turn(0, -.14f); return true;
                case KeyEvent.KEYCODE_PLUS: case KeyEvent.KEYCODE_EQUALS: magnify(1.14f); return true;
                case KeyEvent.KEYCODE_MINUS: magnify(1 / 1.14f); return true;
                case KeyEvent.KEYCODE_MOVE_HOME: preset(0); return true;
            }
        }
        return super.onKeyDown(code, event);
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (ready && gestures) {
            info.setScrollable(true);
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, "Rotate right"));
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, "Rotate left"));
        }
    }

    @Override public boolean performAccessibilityAction(int action, android.os.Bundle arguments) {
        if (ready && gestures) {
            if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) { turn(-.26f, 0); return true; }
            if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) { turn(.26f, 0); return true; }
        }
        return super.performAccessibilityAction(action, arguments);
    }

    private static final class Frame {
        final SurfaceTexture texture;
        final int generation;
        final float yaw, pitch, zoom, aspect;
        final boolean lights, night, inspection;

        Frame(SurfaceTexture texture, int generation, float yaw, float pitch, float zoom, float aspect,
              boolean lights, boolean night, boolean inspection) {
            this.texture = texture;
            this.generation = generation;
            this.yaw = yaw; this.pitch = pitch; this.zoom = zoom; this.aspect = aspect;
            this.lights = lights; this.night = night; this.inspection = inspection;
        }
    }

    /** Serializes EGL creation, swaps and disposal. Coalesces rapid touch input. */
    private final class Worker {
        final HandlerThread thread = new HandlerThread("Vehicle 3D");
        final Handler gl;
        final AssetManager assets;
        private Frame pending;
        private boolean queued;
        private int cycle;
        private VehicleRenderer renderer;
        private int rendererGeneration = -1, failedGeneration = -1;

        Worker(AssetManager assets) {
            this.assets = assets;
            thread.start();
            gl = new Handler(thread.getLooper());
        }

        synchronized void queue(Frame frame) {
            pending = frame;
            if (!queued) {
                queued = true;
                int ticket = cycle;
                gl.post(() -> drawLatest(ticket));
            }
        }

        synchronized void retire(SurfaceTexture release) {
            pending = null;
            queued = false;
            cycle++;
            gl.post(() -> {
                closeRenderer();
                if (release != null) release.release();
            });
        }

        private void drawLatest(int ticket) {
            Frame frame;
            synchronized (this) {
                if (ticket != cycle) return;
                frame = pending; pending = null; queued = false;
            }
            if (frame == null || frame.generation == failedGeneration) return;
            boolean opened = renderer == null || rendererGeneration != frame.generation;
            try {
                if (opened) {
                    closeRenderer();
                    renderer = new VehicleRenderer();
                    renderer.open(assets, frame.texture);
                    rendererGeneration = frame.generation;
                }
                renderer.draw(frame.yaw, frame.pitch, frame.zoom, frame.inspection, frame.aspect, frame.lights, frame.night);
                if (opened) notifyUi(frame, true);
            } catch (IOException | RuntimeException | OutOfMemoryError error) {
                failedGeneration = frame.generation;
                closeRenderer();
                Log.w("Vezel3D", "Using static vehicle artwork", error);
                notifyUi(frame, false);
            }
        }

        private void notifyUi(Frame frame, boolean ok) {
            main.post(() -> {
                if (worker != this || generation != frame.generation || frame.texture != texture || !active) return;
                if (!ok) {
                    failure = frame.generation;
                    setVisibility(INVISIBLE);
                }
                ready(ok);
            });
        }

        private void closeRenderer() {
            if (renderer != null) renderer.close();
            renderer = null;
        }
    }
}
