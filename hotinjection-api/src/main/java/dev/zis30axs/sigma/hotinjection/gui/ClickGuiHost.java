package dev.zis30axs.sigma.hotinjection.gui;

public interface ClickGuiHost {
    boolean isAvailable();

    boolean isOpen();

    boolean open(String source);

    boolean close(String source);

    boolean toggle(String source);

    void dispose();
}
