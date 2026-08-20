package dev.zis30axs.sigma.hotinjection.host.overlay;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import dev.zis30axs.sigma.hotinjection.host.AgentSession;
import dev.zis30axs.sigma.hotinjection.host.model.RemoteModule;

import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Window;
import java.io.Closeable;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/** Owns the external click-through HUD and keeps it aligned to the Minecraft client area. */
public final class HudOverlayWindow implements Closeable {
    private static final int GWL_EXSTYLE = -20;
    private static final int WS_EX_TRANSPARENT = 0x00000020;
    private static final int WS_EX_TOOLWINDOW = 0x00000080;
    private static final int WS_EX_LAYERED = 0x00080000;
    private static final int WS_EX_NOACTIVATE = 0x08000000;

    private final AgentSession session;
    private final MinecraftWindowTracker tracker;
    private final HudOverlayPanel panel = new HudOverlayPanel();
    private final JWindow window = new JWindow();
    private final Timer positionTimer;
    private final ScheduledExecutorService modulePoller;
    private volatile boolean closed;
    private boolean nativeStyleApplied;

    public HudOverlayWindow(long targetProcessId, AgentSession session) {
        if (session == null) throw new NullPointerException("session");
        this.session = session;
        this.tracker = isWindows() ? new MinecraftWindowTracker(targetProcessId) : null;

        window.setType(Window.Type.UTILITY);
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(false);
        window.setAutoRequestFocus(false);
        window.setBackground(new Color(0, 0, 0, 0));
        window.setContentPane(panel);

        positionTimer = new Timer(75, event -> updatePosition());
        positionTimer.setCoalesce(true);
        modulePoller = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "Sigma-HotInjection-HUD-Modules");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public void start() {
        if (!isWindows() || tracker == null) {
            System.err.println("[Sigma HotInjection] External HUD currently requires Windows.");
            return;
        }
        Runnable start = new Runnable() {
            @Override
            public void run() {
                if (closed) return;
                positionTimer.start();
                updatePosition();
            }
        };
        if (SwingUtilities.isEventDispatchThread()) start.run();
        else SwingUtilities.invokeLater(start);

        modulePoller.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                if (closed) return;
                try {
                    final List<RemoteModule> modules = session.listModules();
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            if (!closed) panel.setModules(modules);
                        }
                    });
                } catch (Throwable ignored) {
                    // The control panel can temporarily own the synchronized session; retry next tick.
                }
            }
        }, 0L, 220L, TimeUnit.MILLISECONDS);
    }

    private void updatePosition() {
        if (closed) return;
        MinecraftWindowTracker.Snapshot snapshot;
        try {
            snapshot = tracker.snapshot();
        } catch (Throwable failure) {
            hideOverlay();
            return;
        }
        if (snapshot == null || !snapshot.isForeground()) {
            hideOverlay();
            return;
        }

        Rectangle bounds = snapshot.getBounds();
        if (!bounds.equals(window.getBounds())) window.setBounds(bounds);
        if (!window.isVisible()) {
            window.setVisible(true);
            applyClickThroughStyle();
        }
        window.repaint();
    }

    private void hideOverlay() {
        if (window.isVisible()) window.setVisible(false);
    }

    private void applyClickThroughStyle() {
        if (nativeStyleApplied) return;
        try {
            Pointer pointer = Native.getComponentPointer(window);
            if (pointer == null) return;
            WinDef.HWND handle = new WinDef.HWND(pointer);
            int style = User32.INSTANCE.GetWindowLong(handle, GWL_EXSTYLE);
            User32.INSTANCE.SetWindowLong(handle, GWL_EXSTYLE,
                    style | WS_EX_TRANSPARENT | WS_EX_TOOLWINDOW | WS_EX_LAYERED | WS_EX_NOACTIVATE);
            nativeStyleApplied = true;
        } catch (Throwable failure) {
            System.err.println("[Sigma HotInjection] Could not enable HUD click-through: " + failure);
        }
    }

    @Override
    public void close() {
        closed = true;
        modulePoller.shutdownNow();
        Runnable dispose = new Runnable() {
            @Override
            public void run() {
                positionTimer.stop();
                panel.stop();
                window.dispose();
            }
        };
        if (SwingUtilities.isEventDispatchThread()) dispose.run();
        else SwingUtilities.invokeLater(dispose);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
