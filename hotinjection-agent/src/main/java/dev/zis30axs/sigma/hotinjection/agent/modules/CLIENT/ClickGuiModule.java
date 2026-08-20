package dev.zis30axs.sigma.hotinjection.agent.modules.CLIENT;

import dev.zis30axs.sigma.hotinjection.HotInjectionRuntime;
import dev.zis30axs.sigma.hotinjection.agent.gui.ClickGuiController;
import dev.zis30axs.sigma.hotinjection.event.ClickGuiToggleEvent;
import dev.zis30axs.sigma.hotinjection.input.HotKey;
import dev.zis30axs.sigma.hotinjection.input.KeyProbe;
import dev.zis30axs.sigma.hotinjection.module.Module;
import dev.zis30axs.sigma.hotinjection.module.ModuleCategory;
import dev.zis30axs.sigma.hotinjection.module.setting.BooleanSetting;
import dev.zis30axs.sigma.hotinjection.module.setting.NumberSetting;
import dev.zis30axs.sigma.hotinjection.version.VersionAdapter;

public final class ClickGuiModule extends Module {
    private final HotInjectionRuntime runtime;
    private final BooleanSetting hotkeyEnabled = setting(new BooleanSetting(
            "right-shift", "Right Shift Hotkey",
            "Toggle the compatibility in-process GUI with Right Shift.", true));
    private final NumberSetting pollInterval = setting(new NumberSetting(
            "poll-interval", "Poll Interval", "Keyboard polling interval in milliseconds.",
            40.0D, 20.0D, 200.0D, 10.0D));
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
        runtime.setClickGuiHost(new ClickGuiController(runtime));
        VersionAdapter adapter = runtime.getActiveAdapter();
        keyProbe = adapter == null ? null : adapter.createKeyProbe();
        if (keyProbe == null || !keyProbe.isAvailable()) {
            return;
        }

        running = true;
        keyThread = new Thread(new Runnable() {
            @Override
            public void run() {
                pollKey();
            }
        }, "Sigma-HotInjection-ClickGUI-Key");
        keyThread.setDaemon(true);
        keyThread.start();
    }

    @Override
    protected void onDisable() {
        running = false;
        if (keyProbe != null) {
            keyProbe.close();
        }
        keyProbe = null;
        if (runtime.getClickGuiHost() != null) {
            runtime.getClickGuiHost().dispose();
        }
        runtime.setClickGuiHost(null);
    }

    private void pollKey() {
        boolean previous = false;
        while (running) {
            boolean down = hotkeyEnabled.getValue().booleanValue()
                    && keyProbe != null
                    && keyProbe.isAvailable()
                    && keyProbe.isDown(HotKey.RIGHT_SHIFT);
            VersionAdapter adapter = runtime.getActiveAdapter();
            if (down && !previous && (adapter == null || adapter.isInWorld())) {
                if (runtime.getClickGuiHost() != null) {
                    runtime.getClickGuiHost().toggle(ClickGuiToggleEvent.SOURCE_HOTKEY);
                }
            }
            previous = down;
            try {
                Thread.sleep(Math.max(10L, Math.round(pollInterval.getValue().doubleValue())));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
