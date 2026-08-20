package dev.zis30axs.sigma.hotinjection.input;

/**
 * Reads raw key state from the target process. Implementations are provided by
 * version adapters because 1.7.10/1.8.9 use LWJGL 2 while 1.20.1 and newer use
 * LWJGL 3 / GLFW.
 */
public interface KeyProbe {
    /** @return false when this process offers no usable keyboard source. */
    boolean isAvailable();

    boolean isDown(HotKey key);

    /** Short human readable description of the backing input API. */
    String describe();
}
