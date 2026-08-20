package dev.zis30axs.sigma.hotinjection.agent.gui;

import dev.zis30axs.sigma.hotinjection.HotInjectionRuntime;
import dev.zis30axs.sigma.hotinjection.event.ClickGuiToggleEvent;
import dev.zis30axs.sigma.hotinjection.gui.ClickGuiHost;
import dev.zis30axs.sigma.hotinjection.util.LogUtil;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;

/** Owns the ClickGUI window and gates every state change through the event bus. */
public final class ClickGuiController implements ClickGuiHost {
    private final HotInjectionRuntime runtime;
    private final boolean headless;
    private volatile boolean open;
    private ClickGuiWindow window;

    public ClickGuiController(HotInjectionRuntime runtime) {
        if (runtime == null) {
            throw new NullPointerException("runtime");
        }
        this.runtime = runtime;
        this.headless = isHeadless();
    }

    @Override
    public boolean isAvailable() {
        return !headless;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public synchronized boolean open(String source) {
        if (open) {
            return false;
        }
        if (headless) {
            LogUtil.warn("ClickGUI cannot open: this process has no display.");
            return false;
        }
        if (!allowed(true, source)) {
            return false;
        }
        open = true;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                showWindow();
            }
        });
        LogUtil.info("ClickGUI opened (source=" + source + ").");
        return true;
    }

    @Override
    public synchronized boolean close(String source) {
        if (!open) {
            return false;
        }
        if (!allowed(false, source)) {
            return false;
        }
        open = false;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                hideWindow();
            }
        });
        LogUtil.info("ClickGUI closed (source=" + source + ").");
        return true;
    }

    @Override
    public synchronized boolean toggle(String source) {
        return open ? close(source) : open(source);
    }

    @Override
    public synchronized void dispose() {
        shutdown();
    }

    /** Drops the window; used when the owning module is disabled. */
    public synchronized void shutdown() {
        open = false;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                disposeWindow();
            }
        });
    }

    private boolean allowed(boolean opening, String source) {
        ClickGuiToggleEvent event = runtime.getEventBus().post(new ClickGuiToggleEvent(source, opening));
        if (event.isCancelled()) {
            LogUtil.info("ClickGUI " + (opening ? "open" : "close") + " cancelled by the event bus.");
            return false;
        }
        return true;
    }

    private synchronized void showWindow() {
        try {
            if (window == null) {
                window = new ClickGuiWindow(runtime, new Runnable() {
                    @Override
                    public void run() {
                        close(ClickGuiToggleEvent.SOURCE_GUI);
                    }
                });
            }
            window.show();
        } catch (Throwable failure) {
            open = false;
            LogUtil.error("ClickGUI could not be displayed", failure);
        }
    }

    private synchronized void hideWindow() {
        if (window != null) {
            try {
                window.hide();
            } catch (Throwable failure) {
                LogUtil.warn("ClickGUI could not be hidden: " + failure);
            }
        }
    }

    private synchronized void disposeWindow() {
        if (window != null) {
            try {
                window.dispose();
            } catch (Throwable ignored) {
            }
            window = null;
        }
    }

    private static boolean isHeadless() {
        try {
            return GraphicsEnvironment.isHeadless();
        } catch (Throwable failure) {
            return true;
        }
    }
}
