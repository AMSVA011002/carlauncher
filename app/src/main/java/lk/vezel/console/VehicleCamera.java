package lk.vezel.console;

/** Orbit and perspective projection shared in behaviour with preview/vehicle.js. */
final class VehicleCamera {
    static final float TURN = (float) (2 * Math.PI);
    static final float DEFAULT_YAW = 1.13f, DEFAULT_PITCH = .084f;
    static final float CLOSE_PITCH = .10f, CLOSE_ZOOM = 1.32f;
    static final float MIN_PITCH = .065f, MAX_PITCH = 1.42f, MIN_ZOOM = .84f, MAX_ZOOM = 2.4f;
    private static final float FOV = (float) Math.tan(Math.PI / 17);
    // Same symmetric silhouette samples as the browser. Keeps the roof view clear of route and footer controls.
    private static final float[] ENVELOPE = {-2.226f,.32f,.76f, -2.20f,1.115f,.79f, -1.53f,1.55f,.59f, -.66f,1.627f,.575f,
            .42f,1.53f,.57f, .56f,1.185f,1.02f, 1.25f,1.04f,.86f, 2.17f,.32f,.68f, 2.22f,.86f,.70f, -1.31f,0,.85f, 1.31f,0,.85f};
    private static final float NEAR = .23f, FAR = 48f, DEPTH = -(FAR + NEAR) / (FAR - NEAR);

    private VehicleCamera() {}

    static float clamp(float x, float min, float max) { return Math.max(min, Math.min(max, x)); }

    static void project(float[] vp, float[] eye, float yaw, float pitch, float zoom, boolean inspection, float aspect) {
        float c = (float) Math.cos(yaw), s = (float) Math.sin(yaw);
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        float radius = Math.max((inspection ? 9.4f : 10.25f) / zoom,
                Math.abs(c) * 2.23f + Math.abs(s) * .91f +
                (Math.abs(s) * 2.23f + Math.abs(c) * .91f) /
                (FOV * aspect * (inspection ? .96f : .88f) * Math.max(1, zoom / 1.5f)));
        float nx = c * cp, ny = sp, nz = s * cp;
        float target = .80f - (inspection ? .122f * clamp((zoom - 1) * 3, 0, 1) * (float) Math.pow(Math.abs(c), 8) : 0);
        float f = 1 / FOV, fx = f / aspect, shift = inspection ? .035f : .104f;
        if (!inspection) {
            float range = (aspect < 1.6f ? .54f : .635f) * Math.max(1, zoom);
            for (int i = 0; i < ENVELOPE.length; i += 3) for (int side = -1; side <= 1; side += 2) {
                float x = ENVELOPE[i], y = ENVELOPE[i + 1] - target, z = side * ENVELOPE[i + 2];
                float up = -c * sp * x + cp * y - s * sp * z;
                float along = nx * x + ny * y + nz * z;
                radius = Math.max(radius, along + f * Math.abs(up) / range);
            }
        }
        column(vp, 0, fx * s, -f * c * sp, nx, shift);
        column(vp, 4, 0, f * cp, ny, shift);
        column(vp, 8, -fx * c, -f * s * sp, nz, shift);
        column(vp, 12, 0, -f * cp * target, -radius - sp * target, shift);
        vp[14] -= 2 * NEAR * FAR / (FAR - NEAR);
        eye[0] = nx * radius;
        eye[1] = ny * radius + target;
        eye[2] = nz * radius;
    }

    private static void column(float[] matrix, int i, float x, float y, float z, float shift) {
        matrix[i] = x;
        matrix[i + 1] = y + shift * z;
        matrix[i + 2] = DEPTH * z;
        matrix[i + 3] = -z;
    }
}
