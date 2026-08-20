package dev.zis30axs.sigma.hotinjection.agent.gui;

import dev.zis30axs.sigma.hotinjection.HotInjectionRuntime;
import dev.zis30axs.sigma.hotinjection.agent.client.ClientChat;
import dev.zis30axs.sigma.hotinjection.gui.ClickGuiButton;
import dev.zis30axs.sigma.hotinjection.util.LogUtil;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

/** Swing based ClickGUI overlay. Identical on every supported Minecraft version. */
final class ClickGuiWindow {
    private static final Color BACKGROUND = new Color(16, 16, 21);
    private static final Color PANEL = new Color(28, 28, 36);
    private static final Color ACCENT = new Color(88, 150, 255);
    private static final Color TEXT = new Color(236, 236, 240);
    private static final Color MUTED = new Color(150, 150, 162);

    private final HotInjectionRuntime runtime;
    private final Runnable closeRequest;
    private final JWindow window = new JWindow();
    private final JPanel buttons = new JPanel();
    private final JLabel status = new JLabel();
    private Point dragOrigin;

    ClickGuiWindow(HotInjectionRuntime runtime, Runnable closeRequest) {
        this.runtime = runtime;
        this.closeRequest = closeRequest;

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBackground(BACKGROUND);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT.darker()),
                BorderFactory.createEmptyBorder(12, 14, 10, 14)));
        root.add(header(), BorderLayout.NORTH);

        buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));
        buttons.setBackground(BACKGROUND);
        root.add(buttons, BorderLayout.CENTER);

        status.setForeground(MUTED);
        status.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        root.add(status, BorderLayout.SOUTH);

        window.setContentPane(root);
        window.setAlwaysOnTop(true);
        installCloseKey();
    }

    private JPanel header() {
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setBackground(BACKGROUND);

        JLabel title = new JLabel("SIGMA  ·  HotInjection");
        title.setForeground(TEXT);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        header.add(title, BorderLayout.WEST);

        JLabel close = new JLabel("✕");
        close.setForeground(MUTED);
        close.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        close.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                closeRequest.run();
            }
        });
        header.add(close, BorderLayout.EAST);

        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                dragOrigin = event.getPoint();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                dragOrigin = null;
            }
        });
        header.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent event) {
                Point origin = dragOrigin;
                if (origin != null) {
                    window.setLocation(event.getXOnScreen() - origin.x, event.getYOnScreen() - origin.y);
                }
            }
        });
        return header;
    }

    private void installCloseKey() {
        JRootPane root = window.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("ESCAPE"), "sigma-clickgui-close");
        root.getActionMap().put("sigma-clickgui-close", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                closeRequest.run();
            }
        });
    }

    /** Rebuilds the button list from the registry. Must run on the EDT. */
    void refresh() {
        buttons.removeAll();
        for (final ClickGuiButton entry : runtime.getClickGuiRegistry().all()) {
            buttons.add(createButton(entry));
            buttons.add(Box.createVerticalStrut(6));
        }
        if (runtime.getClickGuiRegistry().size() == 0) {
            JLabel empty = new JLabel("No ClickGUI entries registered");
            empty.setForeground(MUTED);
            buttons.add(empty);
        }
        status.setText("Minecraft " + runtime.getActiveVersion().getId()
                + "  ·  RSHIFT toggles  ·  chat: " + (ClientChat.isAvailable() ? "in-game" : "local toast"));
        window.pack();
        if (window.getWidth() < 300) {
            window.setSize(new Dimension(300, Math.max(140, window.getHeight())));
        }
    }

    private JButton createButton(final ClickGuiButton entry) {
        JButton button = new JButton(entry.getLabel());
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        button.setPreferredSize(new Dimension(260, 30));
        button.setBackground(PANEL);
        button.setForeground(TEXT);
        button.setFocusPainted(false);
        button.setContentAreaFilled(true);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PANEL.brighter()),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (!entry.getDescription().isEmpty()) {
            button.setToolTipText(entry.getDescription());
        }
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                ((JButton) event.getSource()).setBackground(ACCENT.darker().darker());
            }

            @Override
            public void mouseExited(MouseEvent event) {
                ((JButton) event.getSource()).setBackground(PANEL);
            }
        });
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                try {
                    entry.getAction().perform(runtime);
                } catch (Throwable failure) {
                    LogUtil.warn("ClickGUI button '" + entry.getId() + "' failed: " + failure);
                }
            }
        });
        return button;
    }

    void show() {
        refresh();
        if (!window.isVisible()) {
            Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            window.setLocation(
                    screen.x + (screen.width - window.getWidth()) / 2,
                    screen.y + (screen.height - window.getHeight()) / 3);
        }
        window.setVisible(true);
        window.toFront();
    }

    void hide() {
        window.setVisible(false);
    }

    void dispose() {
        window.dispose();
    }
}
