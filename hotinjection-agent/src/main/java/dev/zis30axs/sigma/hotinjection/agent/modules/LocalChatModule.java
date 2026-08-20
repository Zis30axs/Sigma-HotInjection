package dev.zis30axs.sigma.hotinjection.agent.modules;

import dev.zis30axs.sigma.hotinjection.agent.net.LocalChatGuard;
import dev.zis30axs.sigma.hotinjection.event.EventBus;
import dev.zis30axs.sigma.hotinjection.event.EventListener;
import dev.zis30axs.sigma.hotinjection.event.EventPriority;
import dev.zis30axs.sigma.hotinjection.event.PacketSendEvent;
import dev.zis30axs.sigma.hotinjection.module.Module;
import dev.zis30axs.sigma.hotinjection.module.ModuleCategory;
import dev.zis30axs.sigma.hotinjection.util.LogUtil;

/**
 * The registered cancel listener that makes HotInjection chat client-side only.
 *
 * <p>While {@link LocalChatGuard} is armed - which only happens for the moment
 * HotInjection drives the client's own chat send - every outgoing packet
 * carrying text is cancelled here, before the encoder. The client keeps the
 * message, the server never receives it.</p>
 */
public final class LocalChatModule extends Module {
    public LocalChatModule(EventBus eventBus) {
        super("local-chat", "Local Chat Guard", ModuleCategory.CLIENT, eventBus);
    }

    @Override
    protected void onEnable() {
        listen(PacketSendEvent.class, EventPriority.HIGHEST, new EventListener<PacketSendEvent>() {
            @Override
            public void onEvent(PacketSendEvent event) {
                if (event.isCancelled() || !LocalChatGuard.shouldCancel(event.getText())) {
                    return;
                }
                event.cancel();
                LocalChatGuard.markDropped();
                LogUtil.info("Cancelled outgoing " + event.getPacketName() + "; message stays client-side.");
            }
        });
        LocalChatGuard.setListenerActive(true);
    }

    @Override
    protected void onDisable() {
        LocalChatGuard.setListenerActive(false);
        LocalChatGuard.disarm();
    }
}
