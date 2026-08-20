package dev.zis30axs.sigma.hotinjection.agent.modules;

import dev.zis30axs.sigma.hotinjection.HotInjectionRuntime;
import dev.zis30axs.sigma.hotinjection.agent.gui.ClickGuiController;
import dev.zis30axs.sigma.hotinjection.agent.input.ClickGuiHotkey;
import dev.zis30axs.sigma.hotinjection.module.Module;
import dev.zis30axs.sigma.hotinjection.module.ModuleCategory;

/** Owns the ClickGUI front-end and its RIGHT SHIFT hotkey. */
public final class ClickGuiModule extends Module {
    private final HotInjectionRuntime runtime;
    private final ClickGuiController controller;
    private final ClickGuiHotkey hotkey;

    public ClickGuiModule(HotInjectionRuntime runtime) {
        super("clickgui", "ClickGUI", ModuleCategory.RENDER, runtime.getEventBus());
        this.runtime = runtime;
        this.controller = new ClickGuiController(runtime);
        this.hotkey = new ClickGuiHotkey(runtime);
    }

    public ClickGuiController getController() {
        return controller;
    }

    @Override
    protected void onEnable() {
        runtime.setClickGuiHost(controller);
        hotkey.start();
    }

    @Override
    protected void onDisable() {
        hotkey.stop();
        controller.shutdown();
        if (runtime.getClickGuiHost() == controller) {
            runtime.setClickGuiHost(null);
        }
    }
}
