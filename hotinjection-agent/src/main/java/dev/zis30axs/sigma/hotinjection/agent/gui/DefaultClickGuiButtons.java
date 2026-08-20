package dev.zis30axs.sigma.hotinjection.agent.gui;

import dev.zis30axs.sigma.hotinjection.HotInjectionRuntime;
import dev.zis30axs.sigma.hotinjection.event.ClientMessageEvent;
import dev.zis30axs.sigma.hotinjection.gui.ClickGuiAction;
import dev.zis30axs.sigma.hotinjection.gui.ClickGuiButton;

public final class DefaultClickGuiButtons {
    private DefaultClickGuiButtons() { }

    public static void register(final HotInjectionRuntime runtime) {
        runtime.getClickGuiRegistry().register(new ClickGuiButton("test", "TEST", new ClickGuiAction() {
            @Override
            public void perform(HotInjectionRuntime ignored) {
                runtime.sendClientMessage(ClientMessageEvent.SOURCE_CLICK_GUI, "Already Injected!");
            }
        }));
    }
}
