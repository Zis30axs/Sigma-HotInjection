package dev.zis30axs.sigma.hotinjection.input;

/** Mouse state source. Mirrors {@link KeyProbe} and may equally be unavailable. */
public interface MouseProbe {
    boolean isAvailable();

    boolean isDown(MouseButton button);

    /**
     * @return true when the game window currently has input focus. Probes that
     *         cannot tell should return true so features stay usable.
     */
    boolean isWindowFocused();

    String describe();

    void close();
}
