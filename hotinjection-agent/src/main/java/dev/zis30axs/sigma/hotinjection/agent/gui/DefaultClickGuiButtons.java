package dev.zis30axs.sigma.hotinjection.agent.gui;

import dev.zis30axs.sigma.hotinjection.HotInjectionRuntime;
import dev.zis30axs.sigma.hotinjection.event.ClientMessageEvent;
import dev.zis30axs.sigma.hotinjection.gui.ClickGuiAction;

/** Registers the ClickGUI entries the agent ships with. */
public final class DefaultClickGuiButtons {
    /** Text shown by the TEST button. Local only, never sent to a server. */
    public static final String TEST_MESSAGE = "Already Injected!";

    private DefaultClickGuiButtons() {
    }

    public static void register(HotInjectionRuntime runtime) {
        runtime.getClickGuiRegistry().register(
                "test",
                "TEST",
                "Shows \"" + TEST_MESSAGE + "\" in your own chat only.",
                new ClickGuiAction() {
                    @Override
                    public void perform(HotInjectionRuntime target) {
                        target.sendClientMessage(ClientMessageEvent.SOURCE_CLICKGUI, TEST_MESSAGE);
                    }
                });
    }
}
