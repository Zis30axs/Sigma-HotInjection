package dev.zis30axs.sigma.hotinjection.agent;

import dev.zis30axs.sigma.hotinjection.util.LogUtil;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

final class LocalToast {
    private LocalToast() {
    }

    static boolean show(final String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        if (GraphicsEnvironment.isHeadless()) {
            LogUtil.info("CLIENT NOTICE: " + message);
            return true;
        }

        try {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    final JWindow window = new JWindow();
                    JPanel panel = new JPanel(new BorderLayout());
                    panel.setBackground(new Color(20, 20, 20));
                    panel.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(90, 90, 90)),
                            BorderFactory.createEmptyBorder(10, 14, 10, 14)));
                    JLabel label = new JLabel(message, SwingConstants.CENTER);
                    label.setForeground(Color.WHITE);
                    label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
                    panel.add(label, BorderLayout.CENTER);
                    window.setContentPane(panel);
                    window.setAlwaysOnTop(true);
                    window.pack();
                    if (window.getWidth() < 360) {
                        window.setSize(new Dimension(360, Math.max(48, window.getHeight())));
                    }
                    Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
                    window.setLocation(screen.x + screen.width - window.getWidth() - 18, screen.y + 18);
                    window.setVisible(true);
                    Timer timer = new Timer(3500, new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent event) {
                            window.dispose();
                        }
                    });
                    timer.setRepeats(false);
                    timer.start();
                }
            });
            return true;
        } catch (Throwable awtUnavailable) {
            LogUtil.info("CLIENT NOTICE: " + message);
            return true;
        }
    }
}
