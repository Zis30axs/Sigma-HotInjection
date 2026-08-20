package dev.zis30axs.sigma.hotinjection.agent.modules;

import dev.zis30axs.sigma.hotinjection.HotInjectionRuntime;
import dev.zis30axs.sigma.hotinjection.agent.gui.SwingClickGuiHost;
import dev.zis30axs.sigma.hotinjection.event.ClickGuiToggleEvent;
import dev.zis30axs.sigma.hotinjection.input.KeyProbe;
import dev.zis30axs.sigma.hotinjection.module.Module;
import dev.zis30axs.sigma.hotinjection.module.ModuleCategory;
import dev.zis30axs.sigma.hotinjection.version.VersionAdapter;

public final class ClickGuiModule extends Module {
    private final HotInjectionRuntime runtime;
    private volatile boolean running;
    private Thread keyThread;
    private KeyProbe keyProbe;

    public ClickGuiModule(HotInjectionRuntime runtime) {
        super("click-gui", "ClickGUI", ModuleCategory.CLIENT,
                "Compatibility in-process ClickGUI. The standalone Host controller is preferred.",
                runtime.getEventBus());
        this.runtime = runtime;
    }

    @Override
    protected void onEnable() {
        runtime.setClickGuiHost(new SwingClickGuiHost(runtime));
        VersionAdapter adapter = runtime.getActiveAdapter();
        keyProbe = adapter == null ? null : adapter.createKeyProbe();
        if (keyProbe == null) return;

        running = true;
        keyThread = new Thread(new Runnable() {
            @Override public void run() { pollKey(); }
        }, "Sigma-HotInjection-ClickGUI-Key");
        keyThread.setDaemon(true);
        keyThread.start();
    }

    @Override
    protected void onDisable() {
        running = false;
        if (keyProbe != null) keyProbe.close();
        keyProbe = null;
        if (runtime.getClickGuiHost() != null) runtime.getClickGuiHost().dispose();
        runtime.setClickGuiHost(null);
    }

    private void pollKey() {
        boolean previous = false;
        while (running) {
            boolean down = keyProbe != null && keyProbe.isRightShiftDown();
            VersionAdapter adapter = runtime.getActiveAdapter();
            if (down && !previous && (adapter == null || adapter.isInWorld())) {
                if (runtime.getClickGuiHost() != null) {
                    runtime.getClickGuiHost().toggle(ClickGuiToggleEvent.SOURCE_KEY);
                }
            }
            previous = down;
            try {
                Thread.sleep(40L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
