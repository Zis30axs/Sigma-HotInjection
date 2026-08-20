package dev.zis30axs.sigma.hotinjection.agent.input;

import dev.zis30axs.sigma.hotinjection.input.HotKey;
import dev.zis30axs.sigma.hotinjection.input.KeyProbe;

public final class KeyProbes {
    private KeyProbes() {
    }

    public static KeyProbe autoDetect() {
        KeyProbe legacy = Lwjgl2KeyProbe.create();
        if (legacy != null && legacy.isAvailable()) {
            return legacy;
        }
        KeyProbe modern = Lwjgl3KeyProbe.create();
        if (modern != null && modern.isAvailable()) {
            return modern;
        }
        return unavailable("No supported keyboard backend");
    }

    public static KeyProbe legacy() {
        KeyProbe probe = Lwjgl2KeyProbe.create();
        return probe == null ? unavailable("LWJGL 2 keyboard unavailable") : probe;
    }

    public static KeyProbe modern() {
        KeyProbe probe = Lwjgl3KeyProbe.create();
        return probe == null ? unavailable("LWJGL 3/GLFW keyboard unavailable") : probe;
    }

    private static KeyProbe unavailable(final String description) {
        return new KeyProbe() {
            @Override
            public boolean isAvailable() {
                return false;
            }

            @Override
            public boolean isDown(HotKey key) {
                return false;
            }

            @Override
            public String describe() {
                return description;
            }

            @Override
            public void close() {
            }
        };
    }
}
