package dev.zis30axs.sigma.hotinjection.gui;

/**
 * Front-end that can actually show the ClickGUI inside the target process.
 * Implemented by the injected agent; the runtime only stores the reference so
 * modules and host methods can toggle the GUI without knowing the front-end.
 */
public interface ClickGuiHost {
    /** @return true when a ClickGUI can be displayed in this process. */
    boolean isAvailable();

    boolean isOpen();

    /** @return true when the state changed. */
    boolean open(String source);

    /** @return true when the state changed. */
    boolean close(String source);

    /** @return the state after toggling. */
    boolean toggle(String source);
}
