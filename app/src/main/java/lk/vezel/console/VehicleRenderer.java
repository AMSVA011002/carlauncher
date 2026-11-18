package lk.vezel.console;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.view.Surface;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Owns one GLES 2.0 context on the render thread only. No Android UI or continuous loop here. */
final class VehicleRenderer implements AutoCloseable {
    static final int WIDTH = 640, HEIGHT = 370;
    private static final String ASSETS = "vehicle/";
    private static final int STRIDE = 28;
    private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
    private EGLContext context = EGL14.EGL_NO_CONTEXT;
    private EGLSurface surface = EGL14.EGL_NO_SURFACE;
    private boolean initialized;
    private Surface window;
    private int count, uVP, uEye, uLights, uNight;
    private final int[] measured = new int[2];
    private final float[] vp = new float[16], eye = new float[3];

    void open(AssetManager assets, SurfaceTexture texture) throws IOException {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        egl(display != EGL14.EGL_NO_DISPLAY, "No display");
        initialized = EGL14.eglInitialize(display, measured, 0, measured, 1);
        egl(initialized, "Cannot initialize EGL");
        int[] attributes = { EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 16, EGL14.EGL_STENCIL_SIZE, 0, EGL14.EGL_SAMPLE_BUFFERS, 0, EGL14.EGL_NONE };
        EGLConfig[] configs = new EGLConfig[1];
        egl(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, measured, 0) && measured[0] != 0,
                "No GLES2 window configuration");
        context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
        egl(context != EGL14.EGL_NO_CONTEXT, "Cannot create GLES2 context");
        window = new Surface(texture);
        surface = EGL14.eglCreateWindowSurface(display, configs[0], window, new int[]{EGL14.EGL_NONE}, 0);
        egl(surface != EGL14.EGL_NO_SURFACE, "Cannot open 3D window");
        egl(EGL14.eglMakeCurrent(display, surface, surface, context), "Cannot bind EGL context");
        int program = program(assets);
        GLES20.glUseProgram(program);
        loadMesh(assets, program);
        loadDecals(assets);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uDecals"), 0);
        uVP = GLES20.glGetUniformLocation(program, "uVP");
        uEye = GLES20.glGetUniformLocation(program, "uEye");
        uLights = GLES20.glGetUniformLocation(program, "uLights");
        uNight = GLES20.glGetUniformLocation(program, "uNight");
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) throw new IOException("Unable to upload 3D assets: " + Integer.toHexString(error));
    }

    void draw(float yaw, float pitch, float zoom, boolean inspection, float aspect, boolean lights, boolean night) throws IOException {
        VehicleCamera.project(vp, eye, yaw, pitch, zoom, inspection, aspect);
        // The TextureView scales this bounded buffer to the dashboard/inspection bounds.
        egl(EGL14.eglQuerySurface(display, surface, EGL14.EGL_WIDTH, measured, 0) &&
                EGL14.eglQuerySurface(display, surface, EGL14.EGL_HEIGHT, measured, 1), "Cannot size 3D window");
        GLES20.glViewport(0, 0, measured[0], measured[1]);
        float dim = night ? .61f : 1f;
        GLES20.glClearColor(.085f * dim, .072f * dim, .087f * dim, 1);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glUniformMatrix4fv(uVP, 1, false, vp, 0);
        GLES20.glUniform3fv(uEye, 1, eye, 0);
        GLES20.glUniform1f(uLights, lights ? 1 : 0);
        GLES20.glUniform1f(uNight, night ? 1 : 0);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, count, GLES20.GL_UNSIGNED_SHORT, 0);
        egl(EGL14.eglSwapBuffers(display, surface), "Cannot display 3D frame");
    }

    private static void egl(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message + ": " + Integer.toHexString(EGL14.eglGetError()));
    }

    private static int shader(int kind, String source) throws IOException {
        int id = GLES20.glCreateShader(kind);
        GLES20.glShaderSource(id, source);
        GLES20.glCompileShader(id);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new IOException(GLES20.glGetShaderInfoLog(id));
        return id;
    }

    private static String text(AssetManager assets, String name) throws IOException {
        try (InputStream input = assets.open(ASSETS + name); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[2048];
            int n;
            while ((n = input.read(buf)) != -1) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static int program(AssetManager assets) throws IOException {
        int program = GLES20.glCreateProgram();
        int vert = shader(GLES20.GL_VERTEX_SHADER, text(assets, "showroom.vert"));
        int frag = shader(GLES20.GL_FRAGMENT_SHADER, text(assets, "showroom.frag"));
        GLES20.glAttachShader(program, vert);
        GLES20.glAttachShader(program, frag);
        GLES20.glLinkProgram(program);
        GLES20.glDeleteShader(vert);
        GLES20.glDeleteShader(frag);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) throw new IOException(GLES20.glGetProgramInfoLog(program));
        return program;
    }

    private void loadMesh(AssetManager assets, int program) throws IOException {
        try (DataInputStream input = new DataInputStream(assets.open(ASSETS + "vezel.mesh", AssetManager.ACCESS_STREAMING))) {
            int magic = input.readInt(), version = Integer.reverseBytes(input.readInt());
            int vertices = Integer.reverseBytes(input.readInt());
            count = Integer.reverseBytes(input.readInt());
            if (magic != 0x565a3344 || version != 1 || vertices < 1 || vertices > 65535 || count < 1 || count > 300_000 || count % 3 != 0)
                throw new IOException("Invalid 3D mesh header");
            int[] buffers = new int[2];
            GLES20.glGenBuffers(2, buffers, 0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[0]);
            upload(input, GLES20.GL_ARRAY_BUFFER, vertices * STRIDE);
            attrib(program, "aPosition", 3, GLES20.GL_FLOAT, false, 0);
            attrib(program, "aNormal", 3, GLES20.GL_BYTE, true, 12);
            attrib(program, "aColor", 4, GLES20.GL_UNSIGNED_BYTE, true, 16);
            attrib(program, "aTex", 2, GLES20.GL_UNSIGNED_SHORT, true, 20);
            attrib(program, "aMaterial", 4, GLES20.GL_UNSIGNED_BYTE, true, 24);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, buffers[1]);
            upload(input, GLES20.GL_ELEMENT_ARRAY_BUFFER, count * 2);
            if (input.read() != -1) throw new IOException("Invalid 3D mesh length");
        }
    }

    private static void upload(DataInputStream from, int target, int size) throws IOException {
        ByteBuffer data = ByteBuffer.allocateDirect(size);
        byte[] bytes = new byte[8192];
        while (data.hasRemaining()) {
            int take = Math.min(bytes.length, data.remaining());
            from.readFully(bytes, 0, take);
            data.put(bytes, 0, take);
        }
        data.position(0);
        GLES20.glBufferData(target, size, data, GLES20.GL_STATIC_DRAW);
    }

    private static void attrib(int program, String name, int n, int type, boolean normalized, int offset) {
        int index = GLES20.glGetAttribLocation(program, name);
        GLES20.glEnableVertexAttribArray(index);
        GLES20.glVertexAttribPointer(index, n, type, normalized, STRIDE, offset);
    }

    private static InputStream decalFile(AssetManager assets) throws IOException {
        try { return assets.open(ASSETS + "local-decals.png"); }
        catch (IOException notPersonalized) { return assets.open(ASSETS + "decals.png"); }
    }

    private static void loadDecals(AssetManager assets) throws IOException {
        try (InputStream input = decalFile(assets)) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inScaled = false;
            options.inPremultiplied = false;
            Bitmap atlas = BitmapFactory.decodeStream(input, null, options);
            if (atlas == null) throw new IOException("Cannot load vehicle decals");
            try {
                if (atlas.getWidth() != 512 || atlas.getHeight() != 512) throw new IOException("Invalid vehicle decal dimensions");
                int[] texture = new int[1];
                GLES20.glGenTextures(1, texture, 0);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0]);
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, atlas, 0);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
            } finally { atlas.recycle(); }
        }
    }

    @Override public void close() {
        if (initialized) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface);
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context);
            EGL14.eglReleaseThread();
            EGL14.eglTerminate(display);
            initialized = false;
        }
        if (window != null) { window.release(); window = null; }
    }
}
