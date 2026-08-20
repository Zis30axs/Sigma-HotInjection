package dev.zis30axs.sigma.hotinjection.input;

/**
 * Keys HotInjection can observe. Kept deliberately tiny: every entry has to be
 * mapped by each version-specific {@link KeyProbe} implementation.
 */
public enum HotKey {
    RIGHT_SHIFT("Right Shift");

    private final String displayName;

    HotKey(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }

    @Override
    public String toString() { return displayName; }
}
