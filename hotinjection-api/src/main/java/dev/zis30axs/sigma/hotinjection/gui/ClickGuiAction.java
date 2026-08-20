package dev.zis30axs.sigma.hotinjection.gui;

import dev.zis30axs.sigma.hotinjection.HotInjectionRuntime;

/** Action executed when a ClickGUI button is pressed. */
public interface ClickGuiAction {
    void perform(HotInjectionRuntime runtime);
}
