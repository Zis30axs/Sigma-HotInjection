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
    private BufferedImage backdrop;
    private Dimension backdropSize = new Dimension();

    public JelloClickGuiPanel(AgentSession session) {
        this.session = session;
        setOpaque(true);
        setBackground(new Color(12, 13, 20));
        setCursor(Cursor.getDefaultCursor());
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) { handleClick(event.getX(), event.getY()); }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent event) {
                setCursor(findTarget(event.getX(), event.getY()) == null
                        ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
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

            int margin = 24;
            int gap = 16;
            int top = 24;
            int height = getHeight() - 48;
            int leftWidth = 210;
            int rightWidth = 318;
            int centerX = margin + leftWidth + gap;
            int rightX = getWidth() - margin - rightWidth;
            int centerWidth = Math.max(340, rightX - gap - centerX);

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
        fonts.draw(g, "SIGMA", x + 22, y + 22, 31, TEXT, SkijaFontRenderer.Weight.SEMIBOLD);
        float sigmaWidth = fonts.measure("SIGMA", 31, SkijaFontRenderer.Weight.SEMIBOLD);
        fonts.draw(g, "PROD", x + 24 + sigmaWidth, y + 27, 13, new Color(221, 226, 242),
                SkijaFontRenderer.Weight.SEMIBOLD);
        fonts.draw(g, "HOT INJECTION", x + 23, y + 60, 10, MUTED, SkijaFontRenderer.Weight.REGULAR);

        int categoryY = y + 112;
        for (int index = 0; index < CATEGORIES.length; index++) {
            String category = CATEGORIES[index];
            Rectangle bounds = new Rectangle(x + 14, categoryY + index * 54, width - 28, 46);
            boolean active = category.equals(selectedCategory);
            if (active) {
                g.setPaint(new GradientPaint(bounds.x, bounds.y, new Color(178, 119, 255, 72),
                        bounds.x + bounds.width, bounds.y + bounds.height, new Color(97, 212, 255, 42)));
                g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 16, 16);
                g.setColor(new Color(255, 255, 255, 42));
                g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 16, 16);
            }
            fonts.draw(g, displayCategory(category), bounds.x + 16, bounds.y + 13, 16,
                    active ? TEXT : new Color(205, 209, 224),
                    active ? SkijaFontRenderer.Weight.SEMIBOLD : SkijaFontRenderer.Weight.REGULAR);
            hitTargets.add(HitTarget.category(bounds, category));
        }

        int playerY = y + height - 88;
        g.setColor(new Color(255, 255, 255, 16));
        g.fillRoundRect(x + 14, playerY, width - 28, 66, 17, 17);
        g.setColor(BORDER);
        g.drawRoundRect(x + 14, playerY, width - 28, 66, 17, 17);
        fonts.draw(g, "TARGET", x + 28, playerY + 12, 9, MUTED, SkijaFontRenderer.Weight.REGULAR);
        fonts.draw(g, "Minecraft " + session.getVersion(), x + 28, playerY + 31, 15, TEXT,
                SkijaFontRenderer.Weight.SEMIBOLD);
    }

    private void paintModules(Graphics2D g, int x, int y, int width, int height) {
        fonts.draw(g, "MODULE BROWSER", x + 24, y + 22, 10, MUTED, SkijaFontRenderer.Weight.REGULAR);
        fonts.draw(g, displayCategory(selectedCategory), x + 24, y + 45, 27, TEXT,
                SkijaFontRenderer.Weight.SEMIBOLD);
        fonts.draw(g, status, x + 24, y + 82, 11,
                status.startsWith("Error") ? new Color(255, 136, 145) : MUTED,
                SkijaFontRenderer.Weight.REGULAR);

        int listY = y + 112;
        int bottom = y + height - 20;
        List<RemoteModule> visible = filteredModules();
        int cardY = listY - moduleScroll;
        for (RemoteModule module : visible) {
            int cardHeight = 80;
            if (cardY + cardHeight >= listY && cardY <= bottom) {
                Rectangle card = new Rectangle(x + 18, cardY, width - 36, cardHeight - 8);
                boolean selected = selectedModule != null && selectedModule.getId().equals(module.getId());
                g.setColor(selected ? new Color(255, 255, 255, 28) : new Color(255, 255, 255, 15));
                g.fillRoundRect(card.x, card.y, card.width, card.height, 18, 18);
                g.setColor(selected ? new Color(184, 124, 255, 82) : BORDER);
                g.drawRoundRect(card.x, card.y, card.width, card.height, 18, 18);
                fonts.draw(g, module.getName(), card.x + 18, card.y + 14, 17, TEXT,
                        SkijaFontRenderer.Weight.SEMIBOLD);
                String detail = module.getDescription().isEmpty()
                        ? "ModuleCategory " + displayCategory(module.getCategory()) : module.getDescription();
                fonts.draw(g, ellipsize(detail, Math.max(12, card.width - 128)), card.x + 18, card.y + 42, 11,
                        MUTED, SkijaFontRenderer.Weight.REGULAR);
                Rectangle toggle = new Rectangle(card.x + card.width - 72, card.y + 20, 48, 26);
                paintToggle(g, toggle, module.isEnabled());
                hitTargets.add(HitTarget.module(card, module));
                hitTargets.add(HitTarget.toggle(toggle, module));
            }
            cardY += cardHeight;
        }
        if (visible.isEmpty()) {
            fonts.draw(g, "No modules in this category", x + 26, listY + 20, 14, MUTED,
                    SkijaFontRenderer.Weight.REGULAR);
        }
    }

    private void paintSettings(Graphics2D g, int x, int y, int width, int height) {
        fonts.draw(g, "SETTINGS", x + 22, y + 22, 10, MUTED, SkijaFontRenderer.Weight.REGULAR);
        RemoteModule module = selectedModule;
        fonts.draw(g, module == null ? "Select a module" : module.getName(), x + 22, y + 45, 25, TEXT,
                SkijaFontRenderer.Weight.SEMIBOLD);
        if (module == null) {
            fonts.draw(g, "Choose a module in the middle panel", x + 22, y + 92, 12, MUTED,
                    SkijaFontRenderer.Weight.REGULAR);
            return;
        }

        Rectangle state = new Rectangle(x + 18, y + 95, width - 36, 58);
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
            fonts.draw(g, "No configurable settings yet.", x + 22, settingY + 8, 12, MUTED,
                    SkijaFontRenderer.Weight.REGULAR);
            return;
        }

        for (RemoteSetting setting : settings) {
            int cardHeight = "NUMBER".equals(setting.getType()) ? 88 : 68;
            if (settingY + cardHeight >= y + 164 && settingY <= bottom) {
                Rectangle card = new Rectangle(x + 18, settingY, width - 36, cardHeight - 8);
                g.setColor(new Color(255, 255, 255, 14));
                g.fillRoundRect(card.x, card.y, card.width, card.height, 17, 17);
                g.setColor(BORDER);
                g.drawRoundRect(card.x, card.y, card.width, card.height, 17, 17);
                fonts.draw(g, setting.getName(), card.x + 15, card.y + 12, 11, MUTED,
                        SkijaFontRenderer.Weight.REGULAR);

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
                    hitTargets.add(HitTarget.setting(slider, setting));
                } else {
                    fonts.draw(g, setting.getValue(), card.x + 15, card.y + 34, 14, TEXT,
                            SkijaFontRenderer.Weight.SEMIBOLD);
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
        if (target.setting != null) applySetting(target.setting, target.bounds, x);
    }

    private void handleWheel(MouseWheelEvent event) {
        if (event.getX() > getWidth() - 360) settingScroll = Math.max(0, settingScroll + event.getWheelRotation() * 32);
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
                    if (selectedModule == null) selectedModule = firstModuleInCategory(selectedCategory);
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

    private void applySetting(final RemoteSetting setting, Rectangle bounds, int mouseX) {
        String next = setting.getValue();
        if ("BOOLEAN".equals(setting.getType())) {
            next = Boolean.toString(!Boolean.parseBoolean(setting.getValue()));
        } else if ("NUMBER".equals(setting.getType())) {
            double ratio = Math.max(0.0D, Math.min(1.0D, (mouseX - bounds.x) / (double) Math.max(1, bounds.width)));
            double raw = setting.getMin() + (setting.getMax() - setting.getMin()) * ratio;
            double step = setting.getStep() <= 0.0D ? 0.1D : setting.getStep();
            double value = setting.getMin() + Math.round((raw - setting.getMin()) / step) * step;
            next = Double.toString(value);
        } else if ("MODE".equals(setting.getType()) && !setting.getOptions().isEmpty()) {
            int index = setting.getOptions().indexOf(setting.getValue());
            next = setting.getOptions().get((index + 1 + setting.getOptions().size()) % setting.getOptions().size());
        }
        final String requested = next;
        final RemoteModule module = selectedModule;
        if (module == null) return;
        busy = true;
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return session.setSetting(module.getId(), setting.getId(), requested);
            }
            @Override protected void done() {
                try { setting.setValue(get()); }
                catch (Exception error) { status = "Error: " + rootMessage(error); }
                finally { busy = false; repaint(); }
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

    private static String ellipsize(String value, int pixelBudget) {
        int maxChars = Math.max(10, pixelBudget / 7);
        return value.length() <= maxChars ? value : value.substring(0, Math.max(0, maxChars - 1)) + "…";
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
