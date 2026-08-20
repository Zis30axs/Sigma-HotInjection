package dev.zis30axs.sigma.hotinjection.input;

public interface KeyProbe {
    boolean isAvailable();

    boolean isDown(HotKey key);

    String describe();

    void close();
}
