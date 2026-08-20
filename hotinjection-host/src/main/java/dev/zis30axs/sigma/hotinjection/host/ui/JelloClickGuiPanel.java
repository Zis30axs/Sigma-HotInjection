package dev.zis30axs.sigma.hotinjection.host.ui;

import dev.zis30axs.sigma.hotinjection.host.AgentSession;
import dev.zis30axs.sigma.hotinjection.host.model.RemoteModule;
import dev.zis30axs.sigma.hotinjection.host.model.RemoteSetting;

import javax.swing.JPanel;
import javax.swing.SwingWorker;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Three-column Jello-inspired external ClickGUI backed by the injected agent. */
public final class JelloClickGuiPanel extends JPanel {
    private static final String[] CATEGORIES = { "COMBAT", "MOVEMENT", "PLAYER", "RENDER", "MISC", "CLIENT" };
    private static final Color TEXT = new Color(244, 246, 255);
    private static final Color MUTED = new Color(172, 178, 199);
    private static final Color ACCENT = new Color(180, 120, 255);
    private static final Color ACCENT_2 = new Color(91, 205, 255);
    private static final Color PANEL = new Color(25, 28, 39, 158);
    private static final Color PANEL_STRONG = new Color(35, 39, 53, 188);
    private static final Color BORDER = new Color(255, 255, 255, 34);

    private final AgentSession session;
    private final SkijaFontRenderer fonts = new SkijaFontRenderer();
    private final List<HitTarget> hitTargets = new ArrayList<HitTarget>();
    private volatile List<RemoteModule> modules = Collections.emptyList();
    private volatile List<RemoteSetting> settings = Collections.emptyList();
    private volatile String selectedCategory = "COMBAT";
    private volatile RemoteModule selectedModule;
    private volatile String status = "Loading modules...";
    private volatile boolean busy;
    private int moduleScroll;
    private int settingScroll;
    private int settingsPanelX = Integer.MAX_VALUE;
    private RemoteSetting dragSetting;
    private Rectangle dragBounds;
    private boolean dragHighHandle;
    private String pendingDragValue;
    private long lastDragSendNanos;
    private BufferedImage backdrop;
    private Dimension backdropSize = new Dimension();

    public JelloClickGuiPanel(AgentSession session) {
        this.session = session;
        setOpaque(true);
        setBackground(new Color(12, 13, 20));
        setCursor(Cursor.getDefaultCursor());
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) { handleClick(event.getX(), event.getY()); }
            @Override public void mousePressed(MouseEvent event) { handlePress(event.getX(), event.getY()); }
            @Override public void mouseReleased(MouseEvent event) { endDrag(); }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent event) {
                setCursor(findTarget(event.getX(), event.getY()) == null
                        ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
            @Override public void mouseDragged(MouseEvent event) { updateDrag(event.getX(), false); }
        });
        addMouseWheelListener(this::handleWheel);
        refreshModules();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ensureBackdrop();
            g.drawImage(backdrop, 0, 0, null);
            paintBloom(g);

            int margin = clamp(getWidth() / 50, 12, 24);
            int gap = clamp(getWidth() / 80, 10, 16);
            int top = margin;
            int height = Math.max(360, getHeight() - margin * 2);
            int usableWidth = Math.max(620, getWidth() - margin * 2 - gap * 2);
            int leftWidth = clamp(Math.round(usableWidth * 0.21f), 170, 220);
            int rightWidth = clamp(Math.round(usableWidth * 0.30f), 250, 330);
            int centerWidth = Math.max(280, usableWidth - leftWidth - rightWidth);
            int totalWidth = leftWidth + centerWidth + rightWidth + gap * 2;
            if (totalWidth > getWidth() - margin * 2) {
                int overflow = totalWidth - (getWidth() - margin * 2);
                int rightReduction = Math.min(overflow, Math.max(0, rightWidth - 230));
                rightWidth -= rightReduction;
                overflow -= rightReduction;
                int leftReduction = Math.min(overflow, Math.max(0, leftWidth - 160));
                leftWidth -= leftReduction;
                overflow -= leftReduction;
                centerWidth = Math.max(240, centerWidth - overflow);
            }

            int centerX = margin + leftWidth + gap;
            int rightX = centerX + centerWidth + gap;
            settingsPanelX = rightX;

            hitTargets.clear();
            paintGlass(g, margin, top, leftWidth, height, 26, PANEL);
            paintGlass(g, centerX, top, centerWidth, height, 26, PANEL);
            paintGlass(g, rightX, top, rightWidth, height, 26, PANEL_STRONG);
            paintSidebar(g, margin, top, leftWidth, height);
            paintModules(g, centerX, top, centerWidth, height);
            paintSettings(g, rightX, top, rightWidth, height);
        } finally {
            g.dispose();
        }
    }

    private void paintSidebar(Graphics2D g, int x, int y, int width, int height) {
        float sigmaSize = width < 188 ? 27.0f : 31.0f;
        float prodSize = width < 188 ? 11.0f : 13.0f;
        int sigmaX = x + 22;
        float sigmaWidth = fonts.measure("SIGMA", sigmaSize, SkijaFontRenderer.Weight.SEMIBOLD);
        float prodWidth = fonts.measure("PROD", prodSize, SkijaFontRenderer.Weight.SEMIBOLD);
        float prodX = x + width - 18.0f - prodWidth;
        while (sigmaX + sigmaWidth + 10.0f > prodX && sigmaSize > 23.0f) {
            sigmaSize -= 1.0f;
            sigmaWidth = fonts.measure("SIGMA", sigmaSize, SkijaFontRenderer.Weight.SEMIBOLD);
        }

        fonts.draw(g, "SIGMA", sigmaX, y + 22, sigmaSize, TEXT, SkijaFontRenderer.Weight.SEMIBOLD);
        fonts.draw(g, "PROD", prodX, y + 27, prodSize, new Color(221, 226, 242),
                SkijaFontRenderer.Weight.SEMIBOLD);
        fonts.draw(g, "HOT INJECTION", x + 23, y + 60, 10, MUTED, SkijaFontRenderer.Weight.REGULAR);

        int playerY = y + height - 88;
        int categoryY = y + 104;
        int categoryArea = Math.max(228, playerY - categoryY - 12);
        int categoryStep = clamp(categoryArea / CATEGORIES.length, 38, 54);
        int categoryHeight = clamp(categoryStep - 8, 32, 46);
        for (int index = 0; index < CATEGORIES.length; index++) {
            String category = CATEGORIES[index];
            Rectangle bounds = new Rectangle(x + 14, categoryY + index * categoryStep, width - 28, categoryHeight);
            boolean active = category.equals(selectedCategory);
            if (active) {
                g.setPaint(new GradientPaint(bounds.x, bounds.y, new Color(178, 119, 255, 72),
                        bounds.x + bounds.width, bounds.y + bounds.height, new Color(97, 212, 255, 42)));
                g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 16, 16);
                g.setColor(new Color(255, 255, 255, 42));
                g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 16, 16);
            }
            fonts.draw(g, displayCategory(category), bounds.x + 16,
                    bounds.y + Math.max(8, (bounds.height - 17) / 2), 16,
                    active ? TEXT : new Color(205, 209, 224),
                    active ? SkijaFontRenderer.Weight.SEMIBOLD : SkijaFontRenderer.Weight.REGULAR);
            hitTargets.add(HitTarget.category(bounds, category));
        }

        g.setColor(new Color(255, 255, 255, 16));
        g.fillRoundRect(x + 14, playerY, width - 28, 66, 17, 17);
        g.setColor(BORDER);
        g.drawRoundRect(x + 14, playerY, width - 28, 66, 17, 17);
        fonts.draw(g, "TARGET", x + 28, playerY + 12, 9, MUTED, SkijaFontRenderer.Weight.REGULAR);
        String target = fitText("Minecraft " + session.getVersion(), width - 54, 15,
                SkijaFontRenderer.Weight.SEMIBOLD);
        fonts.draw(g, target, x + 28, playerY + 31, 15, TEXT, SkijaFontRenderer.Weight.SEMIBOLD);
    }

    private void paintModules(Graphics2D g, int x, int y, int width, int height) {
        int horizontalPadding = clamp(width / 18, 16, 24);
        fonts.draw(g, "MODULE BROWSER", x + horizontalPadding, y + 22, 10, MUTED,
                SkijaFontRenderer.Weight.REGULAR);
        fonts.draw(g, displayCategory(selectedCategory), x + horizontalPadding, y + 45,
                width < 360 ? 24 : 27, TEXT, SkijaFontRenderer.Weight.SEMIBOLD);
        fonts.draw(g, fitText(status, width - horizontalPadding * 2, 11, SkijaFontRenderer.Weight.REGULAR),
                x + horizontalPadding, y + 82, 11,
                status.startsWith("Error") ? new Color(255, 136, 145) : MUTED,
                SkijaFontRenderer.Weight.REGULAR);

        int listY = y + 112;
        int bottom = y + height - 20;
        List<RemoteModule> visible = filteredModules();
        int cardY = listY - moduleScroll;
        for (RemoteModule module : visible) {
            int cardHeight = 80;
            if (cardY + cardHeight >= listY && cardY <= bottom) {
                Rectangle card = new Rectangle(x + 18, cardY, Math.max(180, width - 36), cardHeight - 8);
                boolean selected = selectedModule != null && selectedModule.getId().equals(module.getId());
                g.setColor(selected ? new Color(255, 255, 255, 28) : new Color(255, 255, 255, 15));
                g.fillRoundRect(card.x, card.y, card.width, card.height, 18, 18);
                g.setColor(selected ? new Color(184, 124, 255, 82) : BORDER);
                g.drawRoundRect(card.x, card.y, card.width, card.height, 18, 18);
                int toggleWidth = card.width < 250 ? 42 : 48;
                int toggleHeight = card.width < 250 ? 24 : 26;
                Rectangle toggle = new Rectangle(card.x + card.width - toggleWidth - 18,
                        card.y + 20, toggleWidth, toggleHeight);
                int textBudget = Math.max(80, toggle.x - card.x - 34);
                fonts.draw(g, fitText(module.getName(), textBudget, 17, SkijaFontRenderer.Weight.SEMIBOLD),
                        card.x + 18, card.y + 14, 17, TEXT, SkijaFontRenderer.Weight.SEMIBOLD);
                String detail = module.getDescription().isEmpty()
                        ? "ModuleCategory " + displayCategory(module.getCategory()) : module.getDescription();
                fonts.draw(g, fitText(detail, Math.max(80, card.width - 36), 11,
                                SkijaFontRenderer.Weight.REGULAR),
                        card.x + 18, card.y + 42, 11, MUTED, SkijaFontRenderer.Weight.REGULAR);
                paintToggle(g, toggle, module.isEnabled());
                hitTargets.add(HitTarget.module(card, module));
                hitTargets.add(HitTarget.toggle(toggle, module));
            }
            cardY += cardHeight;
        }
        if (visible.isEmpty()) {
            fonts.draw(g, fitText("No modules in this category", width - 52, 14,
                            SkijaFontRenderer.Weight.REGULAR),
                    x + 26, listY + 20, 14, MUTED, SkijaFontRenderer.Weight.REGULAR);
        }
    }

    private void paintSettings(Graphics2D g, int x, int y, int width, int height) {
        int horizontalPadding = clamp(width / 16, 16, 22);
        fonts.draw(g, "SETTINGS", x + horizontalPadding, y + 22, 10, MUTED,
                SkijaFontRenderer.Weight.REGULAR);
        RemoteModule module = selectedModule;
        float titleSize = width < 275 ? 20.0f : 25.0f;
        String title = module == null ? "Select a module" : module.getName();
        fonts.draw(g, fitText(title, width - horizontalPadding * 2, titleSize,
                        SkijaFontRenderer.Weight.SEMIBOLD),
                x + horizontalPadding, y + 45, titleSize, TEXT, SkijaFontRenderer.Weight.SEMIBOLD);
        if (module == null) {
            fonts.draw(g, fitText("Choose a module in the middle panel", width - horizontalPadding * 2,
                            12, SkijaFontRenderer.Weight.REGULAR),
                    x + horizontalPadding, y + 92, 12, MUTED, SkijaFontRenderer.Weight.REGULAR);
            return;
        }

        Rectangle state = new Rectangle(x + 18, y + 95, Math.max(190, width - 36), 58);
        g.setColor(new Color(255, 255, 255, 15));
        g.fillRoundRect(state.x, state.y, state.width, state.height, 17, 17);
        fonts.draw(g, "Module State", state.x + 16, state.y + 13, 10, MUTED, SkijaFontRenderer.Weight.REGULAR);
        fonts.draw(g, module.isEnabled() ? "Enabled" : "Disabled", state.x + 16, state.y + 31, 14, TEXT,
                SkijaFontRenderer.Weight.SEMIBOLD);
        Rectangle stateToggle = new Rectangle(state.x + state.width - 64, state.y + 17, 46, 25);
        paintToggle(g, stateToggle, module.isEnabled());
        hitTargets.add(HitTarget.toggle(stateToggle, module));

        int settingY = y + 172 - settingScroll;
        int bottom = y + height - 18;
        if (settings.isEmpty()) {
            fonts.draw(g, fitText("No configurable settings yet.", width - horizontalPadding * 2, 12,
                            SkijaFontRenderer.Weight.REGULAR),
                    x + horizontalPadding, settingY + 8, 12, MUTED, SkijaFontRenderer.Weight.REGULAR);
            return;
        }

        for (RemoteSetting setting : settings) {
            int cardHeight = isSlider(setting) ? 88 : 68;
            if (settingY + cardHeight >= y + 164 && settingY <= bottom) {
                Rectangle card = new Rectangle(x + 18, settingY, Math.max(190, width - 36), cardHeight - 8);
                g.setColor(new Color(255, 255, 255, 14));
                g.fillRoundRect(card.x, card.y, card.width, card.height, 17, 17);
                g.setColor(BORDER);
                g.drawRoundRect(card.x, card.y, card.width, card.height, 17, 17);
                fonts.draw(g, fitText(setting.getName(), card.width - 30, 11,
                                SkijaFontRenderer.Weight.REGULAR),
                        card.x + 15, card.y + 12, 11, MUTED, SkijaFontRenderer.Weight.REGULAR);

                if ("BOOLEAN".equals(setting.getType())) {
                    boolean on = Boolean.parseBoolean(setting.getValue());
                    fonts.draw(g, on ? "On" : "Off", card.x + 15, card.y + 34, 14, TEXT,
                            SkijaFontRenderer.Weight.SEMIBOLD);
                    Rectangle toggle = new Rectangle(card.x + card.width - 62, card.y + 20, 44, 24);
                    paintToggle(g, toggle, on);
                    hitTargets.add(HitTarget.setting(toggle, setting));
                } else if ("NUMBER".equals(setting.getType())) {
                    fonts.draw(g, setting.getValue(), card.x + 15, card.y + 32, 13, TEXT,
                            SkijaFontRenderer.Weight.SEMIBOLD);
                    Rectangle slider = new Rectangle(card.x + 15, card.y + 57, card.width - 30, 8);
                    double value = parseDouble(setting.getValue(), setting.getMin());
                    double ratio = setting.getMax() <= setting.getMin() ? 0.0D
                            : (value - setting.getMin()) / (setting.getMax() - setting.getMin());
                    ratio = Math.max(0.0D, Math.min(1.0D, ratio));
                    g.setColor(new Color(255, 255, 255, 30));
                    g.fillRoundRect(slider.x, slider.y, slider.width, slider.height, 8, 8);
                    g.setPaint(new GradientPaint(slider.x, slider.y, ACCENT,
                            slider.x + slider.width, slider.y, ACCENT_2));
                    g.fillRoundRect(slider.x, slider.y, (int) Math.round(slider.width * ratio), slider.height, 8, 8);
                    paintKnob(g, slider, ratio);
                    hitTargets.add(HitTarget.setting(grip(slider), setting));
                } else if ("RANGE".equals(setting.getType())) {
                    double[] range = parseRange(setting);
                    fonts.draw(g, formatNumber(range[0]) + "  –  " + formatNumber(range[1]),
                            card.x + 15, card.y + 32, 13, TEXT, SkijaFontRenderer.Weight.SEMIBOLD);
                    Rectangle slider = new Rectangle(card.x + 15, card.y + 57, card.width - 30, 8);
                    double lowRatio = ratioOf(setting, range[0]);
                    double highRatio = ratioOf(setting, range[1]);
                    g.setColor(new Color(255, 255, 255, 30));
                    g.fillRoundRect(slider.x, slider.y, slider.width, slider.height, 8, 8);
                    int lowX = (int) Math.round(slider.x + slider.width * lowRatio);
                    int highX = (int) Math.round(slider.x + slider.width * highRatio);
                    g.setPaint(new GradientPaint(slider.x, slider.y, ACCENT,
                            slider.x + slider.width, slider.y, ACCENT_2));
                    g.fillRoundRect(lowX, slider.y, Math.max(2, highX - lowX), slider.height, 8, 8);
                    paintKnob(g, slider, lowRatio);
                    paintKnob(g, slider, highRatio);
                    hitTargets.add(HitTarget.setting(grip(slider), setting));
                } else {
                    fonts.draw(g, fitText(setting.getValue(), card.width - 52, 14,
                                    SkijaFontRenderer.Weight.SEMIBOLD),
                            card.x + 15, card.y + 34, 14, TEXT, SkijaFontRenderer.Weight.SEMIBOLD);
                    Rectangle mode = new Rectangle(card.x + 12, card.y + 24, card.width - 24, 30);
                    hitTargets.add(HitTarget.setting(mode, setting));
                    fonts.draw(g, "›", card.x + card.width - 28, card.y + 30, 16, MUTED,
                            SkijaFontRenderer.Weight.SEMIBOLD);
                }
            }
            settingY += cardHeight;
        }
    }

    private void paintToggle(Graphics2D g, Rectangle bounds, boolean enabled) {
        g.setColor(enabled ? new Color(170, 112, 248, 205) : new Color(255, 255, 255, 25));
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, bounds.height, bounds.height);
        if (enabled) {
            g.setColor(new Color(166, 116, 255, 52));
            g.setStroke(new BasicStroke(5.0f));
            g.drawRoundRect(bounds.x - 2, bounds.y - 2, bounds.width + 4, bounds.height + 4,
                    bounds.height + 4, bounds.height + 4);
            g.setStroke(new BasicStroke(1.0f));
        }
        int diameter = bounds.height - 6;
        int knobX = enabled ? bounds.x + bounds.width - diameter - 3 : bounds.x + 3;
        g.setColor(new Color(248, 249, 255));
        g.fillOval(knobX, bounds.y + 3, diameter, diameter);
    }

    private static boolean isSlider(RemoteSetting setting) {
        String type = setting.getType();
        return "NUMBER".equals(type) || "RANGE".equals(type);
    }

    /** Vertically padded hit area, so a thin bar is still easy to grab. */
    private static Rectangle grip(Rectangle slider) {
        return new Rectangle(slider.x, slider.y - 8, slider.width, slider.height + 16);
    }

    private void paintKnob(Graphics2D g, Rectangle slider, double ratio) {
        int diameter = slider.height + 6;
        int x = (int) Math.round(slider.x + slider.width * ratio) - diameter / 2;
        int y = slider.y + (slider.height - diameter) / 2;
        g.setColor(new Color(166, 116, 255, 60));
        g.fillOval(x - 2, y - 2, diameter + 4, diameter + 4);
        g.setColor(new Color(248, 249, 255));
        g.fillOval(x, y, diameter, diameter);
    }

    private static double[] parseRange(RemoteSetting setting) {
        String value = setting.getValue() == null ? "" : setting.getValue();
        int separator = value.indexOf(':');
        double low = parseDouble(separator < 0 ? value : value.substring(0, separator), setting.getMin());
        double high = separator < 0 ? low : parseDouble(value.substring(separator + 1), setting.getMax());
        if (high < low) {
            double swap = low;
            low = high;
            high = swap;
        }
        return new double[] { low, high };
    }

    private static double ratioOf(RemoteSetting setting, double value) {
        if (setting.getMax() <= setting.getMin()) return 0.0D;
        double ratio = (value - setting.getMin()) / (setting.getMax() - setting.getMin());
        return Math.max(0.0D, Math.min(1.0D, ratio));
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0005D) return Long.toString(Math.round(value));
        return String.valueOf(Math.round(value * 100.0D) / 100.0D);
    }

    private void paintGlass(Graphics2D g, int x, int y, int width, int height, int radius, Color fill) {
        g.setColor(fill);
        g.fillRoundRect(x, y, width, height, radius, radius);
        g.setColor(BORDER);
        g.drawRoundRect(x, y, width, height, radius, radius);
    }

    private void paintBloom(Graphics2D g) {
        int radius = Math.max(180, getWidth() / 5);
        g.setPaint(new RadialGradientPaint(getWidth() * 0.18f, getHeight() * 0.22f, radius,
                new float[] { 0.0f, 1.0f },
                new Color[] { new Color(174, 103, 255, 50), new Color(174, 103, 255, 0) }));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setPaint(new RadialGradientPaint(getWidth() * 0.82f, getHeight() * 0.20f, radius,
                new float[] { 0.0f, 1.0f },
                new Color[] { new Color(84, 206, 255, 44), new Color(84, 206, 255, 0) }));
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    private void ensureBackdrop() {
        if (backdrop != null && backdropSize.width == getWidth() && backdropSize.height == getHeight()) return;
        int width = Math.max(1, getWidth());
        int height = Math.max(1, getHeight());
        BufferedImage source = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = source.createGraphics();
        try {
            g.setPaint(new GradientPaint(0, 0, new Color(15, 16, 25), width, height, new Color(21, 25, 39)));
            g.fillRect(0, 0, width, height);
            g.setPaint(new RadialGradientPaint(width * 0.25f, height * 0.25f, Math.max(220, width / 3),
                    new float[] {0f, 1f}, new Color[] {new Color(114, 66, 170, 100), new Color(18, 19, 28, 0)}));
            g.fillRect(0, 0, width, height);
            g.setPaint(new RadialGradientPaint(width * 0.75f, height * 0.24f, Math.max(220, width / 3),
                    new float[] {0f, 1f}, new Color[] {new Color(42, 115, 151, 75), new Color(18, 19, 28, 0)}));
            g.fillRect(0, 0, width, height);
        } finally {
            g.dispose();
        }
        backdrop = blur(source);
        backdropSize = new Dimension(width, height);
    }

    private static BufferedImage blur(BufferedImage source) {
        int size = 5;
        float[] data = new float[size * size];
        Arrays.fill(data, 1.0f / data.length);
        ConvolveOp op = new ConvolveOp(new Kernel(size, size, data), ConvolveOp.EDGE_NO_OP, null);
        BufferedImage first = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        BufferedImage second = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        op.filter(source, first);
        op.filter(first, second);
        return second;
    }

    private void handleClick(int x, int y) {
        HitTarget target = findTarget(x, y);
        if (target == null || busy) return;
        if (target.category != null) {
            selectedCategory = target.category;
            selectedModule = firstModuleInCategory(selectedCategory);
            settings = Collections.emptyList();
            settingScroll = 0;
            if (selectedModule != null) loadSettings(selectedModule);
            repaint();
            return;
        }
        if (target.module != null && target.setting == null) {
            if (target.toggle) setModuleEnabled(target.module, !target.module.isEnabled());
            else {
                selectedModule = target.module;
                settingScroll = 0;
                loadSettings(target.module);
                repaint();
            }
            return;
        }
        if (target.setting != null) {
            // Sliders are owned by the press/drag path so a click cannot fight a drag.
            if (!isSlider(target.setting)) applySetting(target.setting);
        }
    }

    private void handlePress(int x, int y) {
        HitTarget target = findTarget(x, y);
        if (target == null || target.setting == null || !isSlider(target.setting)) return;
        dragSetting = target.setting;
        dragBounds = target.bounds;
        dragHighHandle = "RANGE".equals(target.setting.getType())
                && nearestHandleIsHigh(target.setting, target.bounds, x);
        updateDrag(x, true);
    }

    /** Live drag: the local value updates every frame, the Agent every 60 ms. */
    private void updateDrag(int x, boolean immediate) {
        RemoteSetting setting = dragSetting;
        Rectangle bounds = dragBounds;
        if (setting == null || bounds == null) return;
        String next = "RANGE".equals(setting.getType())
                ? rangeValue(setting, bounds, x, dragHighHandle)
                : numberValue(setting, bounds, x);
        if (!immediate && next.equals(setting.getValue())) return;
        setting.setValue(next);
        repaint();

        long now = System.nanoTime();
        if (immediate || now - lastDragSendNanos >= 60_000_000L) {
            lastDragSendNanos = now;
            pendingDragValue = null;
            sendSetting(setting, next, false);
        } else {
            pendingDragValue = next;
        }
    }

    private void endDrag() {
        RemoteSetting setting = dragSetting;
        String pending = pendingDragValue;
        dragSetting = null;
        dragBounds = null;
        pendingDragValue = null;
        if (setting != null && pending != null) sendSetting(setting, pending, false);
    }

    private boolean nearestHandleIsHigh(RemoteSetting setting, Rectangle bounds, int x) {
        double[] range = parseRange(setting);
        int lowX = (int) Math.round(bounds.x + bounds.width * ratioOf(setting, range[0]));
        int highX = (int) Math.round(bounds.x + bounds.width * ratioOf(setting, range[1]));
        if (lowX == highX) return x >= lowX;
        return Math.abs(x - highX) < Math.abs(x - lowX);
    }

    private String numberValue(RemoteSetting setting, Rectangle bounds, int x) {
        return Double.toString(snap(setting, valueAt(setting, bounds, x)));
    }

    private String rangeValue(RemoteSetting setting, Rectangle bounds, int x, boolean high) {
        double[] range = parseRange(setting);
        double value = snap(setting, valueAt(setting, bounds, x));
        if (high) return range[0] + ":" + Math.max(value, range[0]);
        return Math.min(value, range[1]) + ":" + range[1];
    }

    private static double valueAt(RemoteSetting setting, Rectangle bounds, int x) {
        double ratio = Math.max(0.0D, Math.min(1.0D,
                (x - bounds.x) / (double) Math.max(1, bounds.width)));
        return setting.getMin() + (setting.getMax() - setting.getMin()) * ratio;
    }

    private static double snap(RemoteSetting setting, double raw) {
        double step = setting.getStep() <= 0.0D ? 0.1D : setting.getStep();
        double snapped = setting.getMin() + Math.round((raw - setting.getMin()) / step) * step;
        snapped = Math.max(setting.getMin(), Math.min(setting.getMax(), snapped));
        return Math.round(snapped * 100000.0D) / 100000.0D;
    }

    private void handleWheel(MouseWheelEvent event) {
        if (event.getX() >= settingsPanelX) settingScroll = Math.max(0, settingScroll + event.getWheelRotation() * 32);
        else moduleScroll = Math.max(0, moduleScroll + event.getWheelRotation() * 32);
        repaint();
    }

    private HitTarget findTarget(int x, int y) {
        for (int i = hitTargets.size() - 1; i >= 0; i--) {
            HitTarget target = hitTargets.get(i);
            if (target.bounds.contains(x, y)) return target;
        }
        return null;
    }

    private void refreshModules() {
        busy = true;
        new SwingWorker<List<RemoteModule>, Void>() {
            @Override protected List<RemoteModule> doInBackground() throws Exception { return session.listModules(); }
            @Override protected void done() {
                try {
                    modules = get();
                    ensureAvailableSelection();
                    status = modules.size() + " module(s) · Minecraft " + session.getVersion();
                    if (selectedModule != null) loadSettings(selectedModule);
                } catch (Exception error) {
                    status = "Error: " + rootMessage(error);
                } finally {
                    busy = false;
                    repaint();
                }
            }
        }.execute();
    }

    private void ensureAvailableSelection() {
        if (selectedModule != null && selectedCategory.equalsIgnoreCase(selectedModule.getCategory())) return;
        selectedModule = firstModuleInCategory(selectedCategory);
        if (selectedModule != null) return;
        for (String category : CATEGORIES) {
            RemoteModule first = firstModuleInCategory(category);
            if (first != null) {
                selectedCategory = category;
                selectedModule = first;
                return;
            }
        }
    }

    private void loadSettings(final RemoteModule module) {
        if (module == null) return;
        new SwingWorker<List<RemoteSetting>, Void>() {
            @Override protected List<RemoteSetting> doInBackground() throws Exception { return session.listSettings(module.getId()); }
            @Override protected void done() {
                try {
                    if (selectedModule != null && selectedModule.getId().equals(module.getId())) settings = get();
                } catch (Exception error) {
                    status = "Error: " + rootMessage(error);
                }
                repaint();
            }
        }.execute();
    }

    private void setModuleEnabled(final RemoteModule module, final boolean enabled) {
        busy = true;
        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() throws Exception {
                return Boolean.valueOf(session.setEnabled(module.getId(), enabled));
            }
            @Override protected void done() {
                try { module.setEnabled(get().booleanValue()); }
                catch (Exception error) { status = "Error: " + rootMessage(error); }
                finally { busy = false; repaint(); }
            }
        }.execute();
    }

    private void applySetting(final RemoteSetting setting) {
        String next;
        if ("BOOLEAN".equals(setting.getType())) {
            next = Boolean.toString(!Boolean.parseBoolean(setting.getValue()));
        } else if ("MODE".equals(setting.getType()) && !setting.getOptions().isEmpty()) {
            int index = setting.getOptions().indexOf(setting.getValue());
            next = setting.getOptions().get((index + 1 + setting.getOptions().size()) % setting.getOptions().size());
        } else {
            return;
        }
        busy = true;
        sendSetting(setting, next, true);
    }

    /**
     * @param blocking true for discrete edits, which hold the panel until the
     *                 Agent answered; false for drag updates, whose replies must
     *                 never overwrite a newer local value.
     */
    private void sendSetting(final RemoteSetting setting, final String requested, final boolean blocking) {
        final RemoteModule module = selectedModule;
        if (module == null) {
            busy = false;
            return;
        }
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return session.setSetting(module.getId(), setting.getId(), requested);
            }
            @Override protected void done() {
                try {
                    String confirmed = get();
                    if (blocking || dragSetting == null) setting.setValue(confirmed);
                } catch (Exception error) {
                    status = "Error: " + rootMessage(error);
                } finally {
                    if (blocking) busy = false;
                    repaint();
                }
            }
        }.execute();
    }

    private List<RemoteModule> filteredModules() {
        List<RemoteModule> result = new ArrayList<RemoteModule>();
        for (RemoteModule module : modules) {
            if (selectedCategory.equalsIgnoreCase(module.getCategory())) result.add(module);
        }
        return result;
    }

    private RemoteModule firstModuleInCategory(String category) {
        for (RemoteModule module : modules) if (category.equalsIgnoreCase(module.getCategory())) return module;
        return null;
    }

    private static String displayCategory(String category) {
        if (category == null || category.isEmpty()) return "Misc";
        String lower = category.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String fitText(String value, int pixelBudget, float fontSize, SkijaFontRenderer.Weight weight) {
        if (value == null || value.isEmpty() || pixelBudget <= 0) return "";
        if (fonts.measure(value, fontSize, weight) <= pixelBudget) return value;
        String ellipsis = "…";
        int low = 0;
        int high = value.length();
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            String candidate = value.substring(0, middle) + ellipsis;
            if (fonts.measure(candidate, fontSize, weight) <= pixelBudget) low = middle;
            else high = middle - 1;
        }
        return value.substring(0, low) + ellipsis;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double parseDouble(String value, double fallback) {
        try { return Double.parseDouble(value); } catch (NumberFormatException ignored) { return fallback; }
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    private static final class HitTarget {
        private final Rectangle bounds;
        private final String category;
        private final RemoteModule module;
        private final RemoteSetting setting;
        private final boolean toggle;

        private HitTarget(Rectangle bounds, String category, RemoteModule module, RemoteSetting setting, boolean toggle) {
            this.bounds = bounds;
            this.category = category;
            this.module = module;
            this.setting = setting;
            this.toggle = toggle;
        }
        static HitTarget category(Rectangle bounds, String category) { return new HitTarget(bounds, category, null, null, false); }
        static HitTarget module(Rectangle bounds, RemoteModule module) { return new HitTarget(bounds, null, module, null, false); }
        static HitTarget toggle(Rectangle bounds, RemoteModule module) { return new HitTarget(bounds, null, module, null, true); }
        static HitTarget setting(Rectangle bounds, RemoteSetting setting) { return new HitTarget(bounds, null, null, setting, false); }
    }
}
