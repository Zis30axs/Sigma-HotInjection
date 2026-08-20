package dev.zis30axs.sigma.hotinjection.host.overlay;

import dev.zis30axs.sigma.hotinjection.host.model.RemoteModule;
import dev.zis30axs.sigma.hotinjection.host.ui.SkijaFontRenderer;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Transparent, click-through HUD surface rendered over the Minecraft client area. */
final class HudOverlayPanel extends JPanel {
    private static final String HUD_ID = "hud";
    private static final String ARRAY_LIST_ID = "array-list";
    private static final Color ARRAY_TEXT = new Color(255, 255, 255);
    private static final Color ARRAY_BLOOM = new Color(183, 205, 255);

    private final SkijaFontRenderer fonts = new SkijaFontRenderer();
    private final Map<String, Float> animation = new HashMap<String, Float>();
    private final Timer repaintTimer;
    private volatile List<RemoteModule> modules = Collections.emptyList();
    private long lastFrameNanos = System.nanoTime();

    HudOverlayPanel() {
        setOpaque(false);
        setBackground(new Color(0, 0, 0, 0));
        repaintTimer = new Timer(16, event -> repaint());
        repaintTimer.setCoalesce(true);
        repaintTimer.start();
    }

    void setModules(List<RemoteModule> updated) {
        modules = updated == null
                ? Collections.<RemoteModule>emptyList()
                : Collections.unmodifiableList(new ArrayList<RemoteModule>(updated));
    }

    void stop() {
        repaintTimer.stop();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            List<RemoteModule> snapshot = modules;
            updateAnimations(snapshot);
            if (isEnabled(snapshot, HUD_ID)) paintWatermark(g);
            if (isEnabled(snapshot, ARRAY_LIST_ID)) paintArrayList(g, snapshot);
        } finally {
            g.dispose();
        }
    }

    private void paintWatermark(Graphics2D g) {
        BufferedImage watermark = JelloWatermark.image();
        BufferedImage bloom = JelloWatermark.bloom();
        float uiScale = uiScale();
        int width = Math.max(120, Math.round(186.0f * uiScale));
        int height = Math.max(60, Math.round(width * watermark.getHeight() / (float) watermark.getWidth()));
        int x = Math.round(16.0f * uiScale);
        int y = Math.round(14.0f * uiScale);
        int bloomPadding = Math.max(8, Math.round(12.0f * uiScale));

        g.setComposite(AlphaComposite.SrcOver.derive(0.62f));
        g.drawImage(bloom,
                x - bloomPadding,
                y - bloomPadding,
                width + bloomPadding * 2,
                height + bloomPadding * 2,
                null);
        g.setComposite(AlphaComposite.SrcOver.derive(0.98f));
        g.drawImage(watermark, x, y, width, height, null);
        g.setComposite(AlphaComposite.SrcOver);
    }

    private void paintArrayList(Graphics2D g, List<RemoteModule> snapshot) {
        final float uiScale = uiScale();
        final float fontSize = 20.0f * uiScale;
        final int margin = Math.max(8, Math.round(10.0f * uiScale));
        final float lineHeight = 22.0f * uiScale;

        List<RemoteModule> visible = new ArrayList<RemoteModule>();
        for (RemoteModule module : snapshot) {
            if (isArrayListCandidate(module) && progress(module) > 0.015f) visible.add(module);
        }
        Collections.sort(visible, new Comparator<RemoteModule>() {
            @Override
            public int compare(RemoteModule left, RemoteModule right) {
                float leftWidth = fonts.measure(left.getName(), fontSize, SkijaFontRenderer.Weight.LIGHT);
                float rightWidth = fonts.measure(right.getName(), fontSize, SkijaFontRenderer.Weight.LIGHT);
                int width = Float.compare(rightWidth, leftWidth);
                return width != 0 ? width : left.getName().compareToIgnoreCase(right.getName());
            }
        });

        float y = Math.max(5.0f, 6.0f * uiScale);
        for (RemoteModule module : visible) {
            float progress = progress(module);
            float eased = smoothStep(progress);
            float nameWidth = fonts.measure(module.getName(), fontSize, SkijaFontRenderer.Weight.LIGHT);
            float x = getWidth() - margin - nameWidth;
            float scale = 0.86f + 0.14f * eased;
            int textAlpha = clampAlpha(Math.round(242.0f * eased));
            int bloomAlpha = clampAlpha(Math.round(92.0f * eased));

            Graphics2D item = (Graphics2D) g.create();
            try {
                float anchorX = getWidth() - margin;
                float anchorY = y + fontSize * 0.52f;
                item.translate(anchorX, anchorY);
                item.scale(scale, scale);
                item.translate(-anchorX, -anchorY);

                Color bloomColor = new Color(
                        ARRAY_BLOOM.getRed(), ARRAY_BLOOM.getGreen(), ARRAY_BLOOM.getBlue(), bloomAlpha);
                fonts.draw(item, module.getName(), x - 2.0f, y - 1.0f,
                        fontSize, bloomColor, SkijaFontRenderer.Weight.LIGHT);
                fonts.draw(item, module.getName(), x + 2.0f, y + 1.0f,
                        fontSize, bloomColor, SkijaFontRenderer.Weight.LIGHT);
                fonts.draw(item, module.getName(), x, y,
                        fontSize,
                        new Color(ARRAY_TEXT.getRed(), ARRAY_TEXT.getGreen(), ARRAY_TEXT.getBlue(), textAlpha),
                        SkijaFontRenderer.Weight.LIGHT);
            } finally {
                item.dispose();
            }
            y += lineHeight * eased;
        }
    }

    private void updateAnimations(List<RemoteModule> snapshot) {
        long now = System.nanoTime();
        float elapsed = Math.min(0.075f, Math.max(0.0f, (now - lastFrameNanos) / 1_000_000_000.0f));
        lastFrameNanos = now;
        float response = Math.min(1.0f, elapsed * 10.0f);
        for (RemoteModule module : snapshot) {
            if (!isArrayListCandidate(module)) continue;
            float current = progress(module);
            float target = module.isEnabled() ? 1.0f : 0.0f;
            float next = current + (target - current) * response;
            if (Math.abs(target - next) < 0.002f) next = target;
            animation.put(module.getId(), Float.valueOf(next));
        }
    }

    private float progress(RemoteModule module) {
        Float value = animation.get(module.getId());
        if (value == null) {
            value = Float.valueOf(module.isEnabled() ? 1.0f : 0.0f);
            animation.put(module.getId(), value);
        }
        return value.floatValue();
    }

    private boolean isArrayListCandidate(RemoteModule module) {
        if (module == null || module.getId() == null) return false;
        String id = module.getId().toLowerCase(Locale.ROOT);
        if (HUD_ID.equals(id) || ARRAY_LIST_ID.equals(id)) return false;
        return true;
    }

    private static boolean isEnabled(List<RemoteModule> modules, String id) {
        for (RemoteModule module : modules) {
            if (id.equalsIgnoreCase(module.getId())) return module.isEnabled();
        }
        return false;
    }

    private float uiScale() {
        return Math.max(0.82f, Math.min(1.55f, getHeight() / 900.0f));
    }

    private static float smoothStep(float value) {
        float clamped = Math.max(0.0f, Math.min(1.0f, value));
        return clamped * clamped * (3.0f - 2.0f * clamped);
    }

    private static int clampAlpha(int alpha) {
        return Math.max(0, Math.min(255, alpha));
    }
}
