package dev.zis30axs.sigma.hotinjection.agent.input;

import dev.zis30axs.sigma.hotinjection.input.KeyProbe;

/** Picks the keyboard source that actually exists in the target process. */
public final class KeyProbes {
    private KeyProbes() {
    }

    /** LWJGL 2 first: 1.7.10 and 1.8.9. */
    public static KeyProbe legacy() {
        KeyProbe probe = usable(Lwjgl2KeyProbe.create());
        return probe != null ? probe : usable(Lwjgl3KeyProbe.create());
    }

    /** GLFW first: 1.20.1 and newer. */
    public static KeyProbe modern() {
        KeyProbe probe = usable(Lwjgl3KeyProbe.create());
        return probe != null ? probe : usable(Lwjgl2KeyProbe.create());
    }

    /** Used when the Minecraft version is unknown. */
    public static KeyProbe autoDetect() {
        KeyProbe probe = usable(Lwjgl2KeyProbe.create());
        return probe != null ? probe : usable(Lwjgl3KeyProbe.create());
    }

    private static KeyProbe usable(KeyProbe probe) {
        return probe != null && probe.isAvailable() ? probe : null;
    }
}
