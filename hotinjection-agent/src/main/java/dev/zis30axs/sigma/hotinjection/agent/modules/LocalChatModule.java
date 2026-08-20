package dev.zis30axs.sigma.hotinjection.agent.modules;

import dev.zis30axs.sigma.hotinjection.event.EventBus;
import dev.zis30axs.sigma.hotinjection.module.Module;
import dev.zis30axs.sigma.hotinjection.module.ModuleCategory;

/** Marker module for the local-only message path. It never sends network traffic. */
public final class LocalChatModule extends Module {
    public LocalChatModule(EventBus eventBus) {
        super("local-chat", "Local Chat", ModuleCategory.CLIENT,
                "Keeps Sigma status messages local to this client.", eventBus);
    }
}
