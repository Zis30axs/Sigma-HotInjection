package dev.zis30axs.sigma.hotinjection.agent.modules.RENDER;

import dev.zis30axs.sigma.hotinjection.HotInjectionRuntime;
import dev.zis30axs.sigma.hotinjection.agent.client.EntityView;
import dev.zis30axs.sigma.hotinjection.agent.client.PlayerView;
import dev.zis30axs.sigma.hotinjection.agent.client.ScreenProjection;
import dev.zis30axs.sigma.hotinjection.module.Module;
import dev.zis30axs.sigma.hotinjection.module.ModuleCategory;
import dev.zis30axs.sigma.hotinjection.module.setting.BooleanSetting;
import dev.zis30axs.sigma.hotinjection.module.setting.ModeSetting;
import dev.zis30axs.sigma.hotinjection.module.setting.NumberSetting;
import dev.zis30axs.sigma.hotinjection.overlay.OverlayBox;
import dev.zis30axs.sigma.hotinjection.overlay.OverlaySource;
import dev.zis30axs.sigma.hotinjection.util.LogUtil;
import dev.zis30axs.sigma.hotinjection.version.VersionAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Entity box ESP drawn by the external Host overlay.
 *
 * <p>The Agent owns the geometry: it reads the entity boxes, projects them into
 * normalized client coordinates and hands the Host plain rectangles. Nothing is
 * hooked into the render pipeline, which is what keeps one implementation valid
 * from 1.7.10 to 26.2.</p>
 */
public final class EspModule extends Module implements OverlaySource {
    private static final int JELLO = 0xFFB478FF;
    private static final int CYAN = 0xFF5BCDFF;
    private static final int WHITE = 0xFFF4F6FF;
    private static final int RED = 0xFFFF6B6B;
    private static final int NEAR = 0xFF5BFFB4;

    private final ModeSetting targets = setting(new ModeSetting(
            "targets", "Targets",
            "Players are recognised by their account profile; All also boxes mobs, items and projectiles.",
            "Players", "Players", "All"));
    private final ModeSetting color = setting(new ModeSetting(
            "color", "Color", "Box color, or a mint to purple fade over the render range.",
            "Jello", "Jello", "Cyan", "White", "Red", "Distance"));
    private final NumberSetting fov = setting(new NumberSetting(
            "fov", "FOV",
            "Vertical field of view used for the projection; match the in-game FOV slider.",
            70.0D, 30.0D, 120.0D, 1.0D));
    private final NumberSetting range = setting(new NumberSetting(
            "range", "Range", "Maximum distance in blocks.", 64.0D, 8.0D, 256.0D, 4.0D));
    private final NumberSetting limit = setting(new NumberSetting(
            "limit", "Max Targets", "Upper bound on boxes per frame.", 64.0D, 8.0D, 256.0D, 8.0D));
    private final BooleanSetting names = setting(new BooleanSetting(
            "names", "Names", "Draw the account name above player boxes.", true));

    private final HotInjectionRuntime runtime;
    private boolean cameraWarned;

    public EspModule(HotInjectionRuntime runtime) {
        super("esp", "ESP", ModuleCategory.RENDER,
                "Projects entity boxes into the external overlay.",
                runtime.getEventBus());
        this.runtime = runtime;
    }

    @Override
    protected void onEnable() {
        cameraWarned = false;
    }

    @Override
    public List<OverlayBox> collectOverlay(double aspectRatio) {
        if (!isEnabled()) return Collections.emptyList();
        VersionAdapter adapter = runtime.getActiveAdapter();
        if (adapter != null && !adapter.isInWorld()) return Collections.emptyList();

        PlayerView.Camera camera = PlayerView.camera();
        if (camera == null) {
            if (!cameraWarned) {
                cameraWarned = true;
                LogUtil.warn("ESP cannot resolve the camera position or view angles in this runtime.");
            }
            return Collections.emptyList();
        }

        ScreenProjection projection = new ScreenProjection(
                camera, fov.getValue().doubleValue(), aspectRatio);
        boolean playersOnly = "Players".equalsIgnoreCase(targets.getValue());
        double maxDistance = range.getValue().doubleValue();
        int maxTargets = (int) Math.round(limit.getValue().doubleValue());
        boolean showNames = names.getValue().booleanValue();

        List<OverlayBox> boxes = new ArrayList<OverlayBox>();
        for (EntityView.Target target : EntityView.targets(maxTargets * 4)) {
            if (playersOnly && !target.isPlayer()) continue;
            double distance = distance(camera, target);
            if (distance > maxDistance) continue;
            OverlayBox box = box(projection, target, distance / maxDistance,
                    showNames ? target.getName() : "");
            if (box != null) boxes.add(box);
            if (boxes.size() >= maxTargets) break;
        }
        return boxes;
    }

    private static double distance(PlayerView.Camera camera, EntityView.Target target) {
        double dx = target.getCenterX() - camera.getX();
        double dy = target.getCenterY() - camera.getY();
        double dz = target.getCenterZ() - camera.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Projects the eight box corners and keeps their screen-space envelope. */
    private OverlayBox box(ScreenProjection projection, EntityView.Target target,
                           double rangeRatio, String label) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        boolean visible = false;
        for (int corner = 0; corner < 8; corner++) {
            double[] projected = projection.project(
                    (corner & 1) == 0 ? target.getMinX() : target.getMaxX(),
                    (corner & 2) == 0 ? target.getMinY() : target.getMaxY(),
                    (corner & 4) == 0 ? target.getMinZ() : target.getMaxZ());
            if (projected == null) continue;
            visible = true;
            minX = Math.min(minX, projected[0]);
            minY = Math.min(minY, projected[1]);
            maxX = Math.max(maxX, projected[0]);
            maxY = Math.max(maxY, projected[1]);
        }
        if (!visible) return null;
        if (maxX < -0.05D || minX > 1.05D || maxY < -0.05D || minY > 1.05D) return null;
        return new OverlayBox(minX, minY, maxX, maxY, boxColor(rangeRatio), label);
    }

    private int boxColor(double rangeRatio) {
        String mode = color.getValue();
        if ("Cyan".equalsIgnoreCase(mode)) return CYAN;
        if ("White".equalsIgnoreCase(mode)) return WHITE;
        if ("Red".equalsIgnoreCase(mode)) return RED;
        if ("Distance".equalsIgnoreCase(mode)) {
            return blend(NEAR, JELLO, Math.max(0.0D, Math.min(1.0D, rangeRatio)));
        }
        return JELLO;
    }

    private static int blend(int from, int to, double amount) {
        int alpha = channel(from, 24, to, amount);
        int red = channel(from, 16, to, amount);
        int green = channel(from, 8, to, amount);
        int blue = channel(from, 0, to, amount);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int channel(int from, int shift, int to, double amount) {
        int start = (from >>> shift) & 0xFF;
        int end = (to >>> shift) & 0xFF;
        return (int) Math.round(start + (end - start) * amount);
    }
}
