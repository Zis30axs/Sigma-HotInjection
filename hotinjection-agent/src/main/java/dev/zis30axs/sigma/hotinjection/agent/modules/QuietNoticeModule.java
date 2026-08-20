package dev.zis30axs.sigma.hotinjection.agent.modules;

import dev.zis30axs.sigma.hotinjection.event.EventBus;
import dev.zis30axs.sigma.hotinjection.event.EventListener;
import dev.zis30axs.sigma.hotinjection.event.EventPriority;
import dev.zis30axs.sigma.hotinjection.event.InjectionNoticeEvent;
import dev.zis30axs.sigma.hotinjection.module.Module;
import dev.zis30axs.sigma.hotinjection.module.ModuleCategory;

/** Demonstrates a real cancellable-event consumer. */
public final class QuietNoticeModule extends Module {
    public QuietNoticeModule(EventBus eventBus) {
        super("quiet-notice", "Quiet Injection Notice", ModuleCategory.CLIENT, eventBus);
    }

    @Override
    protected void onEnable() {
        listen(InjectionNoticeEvent.class, EventPriority.HIGHEST,
                new EventListener<InjectionNoticeEvent>() {
                    @Override
                    public void onEvent(InjectionNoticeEvent event) {
                        event.cancel();
                    }
                });
    }
}
