package dev.zis30axs.sigma.hotinjection.agent.modules.RENDER;

import dev.zis30axs.sigma.hotinjection.HotInjectionRuntime;
import dev.zis30axs.sigma.hotinjection.module.Module;
import dev.zis30axs.sigma.hotinjection.module.ModuleCategory;

/** Controls the external Sigma Jello watermark overlay rendered by the Host. */
public final class HudModule extends Module {
    public HudModule(HotInjectionRuntime runtime) {
        super("hud", "HUD", ModuleCategory.RENDER,
                "Displays the Sigma Jello watermark in the external overlay.",
                runtime.getEventBus());
    }
}
