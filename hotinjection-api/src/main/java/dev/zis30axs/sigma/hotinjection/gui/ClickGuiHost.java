package dev.zis30axs.sigma.hotinjection.gui;

public interface ClickGuiHost {
    boolean isAvailable();
    boolean isOpen();
    void open(String source);
    void close(String source);
    void toggle(String source);
    void dispose();
}
