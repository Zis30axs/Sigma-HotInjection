package dev.zis30axs.sigma.hotinjection.agent.gui;

import dev.zis30axs.sigma.hotinjection.HotInjectionRuntime;
import dev.zis30axs.sigma.hotinjection.event.ClickGuiToggleEvent;
import dev.zis30axs.sigma.hotinjection.gui.ClickGuiButton;
import dev.zis30axs.sigma.hotinjection.gui.ClickGuiHost;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;

/** Compatibility in-process ClickGUI; the standalone Host controller is preferred. */
public final class SwingClickGuiHost implements ClickGuiHost {
    private final HotInjectionRuntime runtime;
    private volatile JFrame frame;

    public SwingClickGuiHost(HotInjectionRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public boolean isAvailable() {
        return !GraphicsEnvironment.isHeadless();
    }

    @Override
    public boolean isOpen() {
        JFrame current = frame;
        return current != null && current.isVisible();
    }

    @Override
    public boolean open(String source) {
        if (isOpen() || !isAvailable()) {
            return false;
        }
        ClickGuiToggleEvent event = runtime.getEventBus().post(new ClickGuiToggleEvent(source, true));
        if (event.isCancelled()) {
            return false;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                ensureFrame().setVisible(true);
            }
        });
        return true;
    }

    @Override
    public boolean close(String source) {
        if (!isOpen()) {
            return false;
        }
        ClickGuiToggleEvent event = runtime.getEventBus().post(new ClickGuiToggleEvent(source, false));
        if (event.isCancelled()) {
            return false;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (frame != null) {
                    frame.setVisible(false);
                }
            }
        });
        return true;
    }

    @Override
    public boolean toggle(String source) {
        return isOpen() ? close(source) : open(source);
    }

    @Override
    public void dispose() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (frame != null) {
                    frame.dispose();
                    frame = null;
                }
            }
        });
    }

    private JFrame ensureFrame() {
        if (frame != null) {
            return frame;
        }
        JFrame created = new JFrame("Sigma HotInjection");
        created.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        created.setAlwaysOnTop(true);
        created.setSize(430, 160);
        created.setLocationRelativeTo(null);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 16));
        for (final ClickGuiButton button : runtime.getClickGuiRegistry().all()) {
            JButton swingButton = new JButton(button.getLabel());
            swingButton.addActionListener(event -> button.getAction().perform(runtime));
            buttons.add(swingButton);
        }
        created.setContentPane(buttons);
        frame = created;
        return created;
    }
}
