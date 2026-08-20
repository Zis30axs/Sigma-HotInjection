package dev.zis30axs.sigma.hotinjection.agent.client;

/**
 * World to screen projection for the external overlay.
 *
 * <p>Pure math: no game class is touched here. The basis follows Minecraft's
 * convention, where yaw 0 looks towards +Z and a positive pitch looks down, and
 * the result is normalized to the client area so the Host can scale it to the
 * overlay window without knowing anything about the game.</p>
 */
public final class ScreenProjection {
    private static final double NEAR_PLANE = 0.05D;

    private final double cameraX;
    private final double cameraY;
    private final double cameraZ;
    private final double rightX;
    private final double rightZ;
    private final double upX;
    private final double upY;
    private final double upZ;
    private final double forwardX;
    private final double forwardY;
    private final double forwardZ;
    private final double tanHalfFov;
    private final double aspectRatio;

    public ScreenProjection(PlayerView.Camera camera, double verticalFovDegrees, double aspectRatio) {
        if (camera == null) throw new NullPointerException("camera");
        this.cameraX = camera.getX();
        this.cameraY = camera.getY();
        this.cameraZ = camera.getZ();
        this.aspectRatio = aspectRatio <= 0.0D ? 16.0D / 9.0D : aspectRatio;
        double fov = Math.max(10.0D, Math.min(170.0D, verticalFovDegrees));
        this.tanHalfFov = Math.tan(Math.toRadians(fov) * 0.5D);

        double yaw = Math.toRadians(camera.getYaw());
        double pitch = Math.toRadians(camera.getPitch());
        double cosPitch = Math.cos(pitch);
        this.forwardX = -Math.sin(yaw) * cosPitch;
        this.forwardY = -Math.sin(pitch);
        this.forwardZ = Math.cos(yaw) * cosPitch;
        // Screen right stays horizontal, so it is derived from the yaw alone and
        // never degenerates when looking straight up or down.
        this.rightX = -Math.cos(yaw);
        this.rightZ = -Math.sin(yaw);
        // up = right x forward, with right.y == 0.
        this.upX = -rightZ * forwardY;
        this.upY = rightZ * forwardX - rightX * forwardZ;
        this.upZ = rightX * forwardY;
    }

    /**
     * @return {@code x, y, distance} with x/y normalized to the client area, or
     *         {@code null} when the point sits behind the camera.
     */
    public double[] project(double x, double y, double z) {
        double dx = x - cameraX;
        double dy = y - cameraY;
        double dz = z - cameraZ;
        double depth = dx * forwardX + dy * forwardY + dz * forwardZ;
        if (depth < NEAR_PLANE) return null;
        double horizontal = dx * rightX + dz * rightZ;
        double vertical = dx * upX + dy * upY + dz * upZ;
        double normalizedX = horizontal / (depth * tanHalfFov * aspectRatio);
        double normalizedY = vertical / (depth * tanHalfFov);
        return new double[] {
                0.5D + 0.5D * normalizedX,
                0.5D - 0.5D * normalizedY,
                Math.sqrt(dx * dx + dy * dy + dz * dz)
        };
    }
}
