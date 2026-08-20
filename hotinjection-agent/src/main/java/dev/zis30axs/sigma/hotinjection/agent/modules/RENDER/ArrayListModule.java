package dev.zis30axs.sigma.hotinjection.agent.modules.RENDER;

import dev.zis30axs.sigma.hotinjection.HotInjectionRuntime;
import dev.zis30axs.sigma.hotinjection.module.Module;
import dev.zis30axs.sigma.hotinjection.module.ModuleCategory;

/** Controls the Jello-style active-module list rendered by the external Host overlay. */
public final class ArrayListModule extends Module {
    public ArrayListModule(HotInjectionRuntime runtime) {
        super("array-list", "ArrayList", ModuleCategory.RENDER,
                "Displays enabled modules with Jello-style bloom and slide animations.",
                runtime.getEventBus());
    }
}
