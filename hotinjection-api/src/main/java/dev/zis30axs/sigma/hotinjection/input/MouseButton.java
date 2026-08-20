package dev.zis30axs.sigma.hotinjection.input;

/**
 * Mouse buttons HotInjection can observe. LWJGL 2 and GLFW agree on these two
 * indices, so every {@link MouseProbe} implementation can map them.
 */
public enum MouseButton {
    LEFT("Left", 0),
    RIGHT("Right", 1);

    private final String displayName;
    private final int index;

    MouseButton(String displayName, int index) {
        this.displayName = displayName;
        this.index = index;
    }

    public String getDisplayName() { return displayName; }

    /** @return the LWJGL 2 / GLFW button index. */
    public int getIndex() { return index; }

    @Override
    public String toString() { return displayName; }
}
