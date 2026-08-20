package dev.zis30axs.sigma.hotinjection.agent.input;

import dev.zis30axs.sigma.hotinjection.HotInjectionRuntime;
import dev.zis30axs.sigma.hotinjection.event.ClickGuiToggleEvent;
import dev.zis30axs.sigma.hotinjection.gui.ClickGuiHost;
import dev.zis30axs.sigma.hotinjection.input.HotKey;
import dev.zis30axs.sigma.hotinjection.input.KeyProbe;
import dev.zis30axs.sigma.hotinjection.util.LogUtil;
import dev.zis30axs.sigma.hotinjection.version.VersionAdapter;

/** Watches RIGHT SHIFT and toggles the ClickGUI while the player is in a world. */
public final class ClickGuiHotkey {
    private static final long POLL_INTERVAL_MS = 30L;

    private final HotInjectionRuntime runtime;
    private volatile Thread thread;
    private volatile boolean running;

    public ClickGuiHotkey(HotInjectionRuntime runtime) {
        if (runtime == null) {
            throw new NullPointerException("runtime");
        }
        this.runtime = runtime;
    }

    /** @return true when the hotkey is armed. */
    public synchronized boolean start() {
        if (running) {
            return true;
        }
        VersionAdapter adapter = runtime.getActiveAdapter();
        KeyProbe probe = adapter == null ? null : adapter.createKeyProbe();
        if (probe == null || !probe.isAvailable()) {
            LogUtil.warn("No keyboard source in this process; RSHIFT is unavailable. "
                    + "Toggle the ClickGUI with the clickgui.toggle method instead.");
            return false;
        }

        running = true;
        Thread worker = new Thread(new Loop(probe), "Sigma-HotInjection-Hotkey");
        worker.setDaemon(true);
        thread = worker;
        worker.start();
        LogUtil.info("ClickGUI hotkey armed: RIGHT SHIFT via " + probe.describe());
        return true;
    }

    public synchronized void stop() {
        running = false;
        Thread worker = thread;
        thread = null;
        if (worker != null) {
            worker.interrupt();
        }
    }

    private final class Loop implements Runnable {
        private final KeyProbe probe;

        private Loop(KeyProbe probe) {
            this.probe = probe;
        }

        @Override
        public void run() {
            boolean wasDown = false;
            while (running) {
                boolean down;
                try {
                    down = probe.isAvailable() && probe.isDown(HotKey.RIGHT_SHIFT);
                } catch (Throwable failure) {
                    LogUtil.warn("Hotkey polling stopped: " + failure);
                    return;
                }

                if (down && !wasDown) {
                    onPress();
                }
                wasDown = down;

                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        private void onPress() {
            ClickGuiHost host = runtime.getClickGuiHost();
            if (host == null || !host.isAvailable()) {
                return;
            }
            VersionAdapter adapter = runtime.getActiveAdapter();
            if (!host.isOpen() && adapter != null && !adapter.isInWorld()) {
                LogUtil.info("ClickGUI hotkey ignored: no world loaded.");
                return;
            }
            try {
                host.toggle(ClickGuiToggleEvent.SOURCE_HOTKEY);
            } catch (Throwable failure) {
                LogUtil.warn("ClickGUI toggle failed: " + failure);
            }
        }
    }
}
