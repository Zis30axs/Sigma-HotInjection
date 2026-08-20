package dev.zis30axs.sigma.hotinjection.agent.input;

import dev.zis30axs.sigma.hotinjection.input.MouseButton;
import dev.zis30axs.sigma.hotinjection.input.MouseProbe;

public final class MouseProbes {
    private MouseProbes() {
    }

    public static MouseProbe autoDetect() {
        MouseProbe legacy = Lwjgl2MouseProbe.create();
        if (legacy != null && legacy.isAvailable()) {
            return legacy;
        }
        MouseProbe modern = Lwjgl3MouseProbe.create();
        if (modern != null && modern.isAvailable()) {
            return modern;
        }
        return unavailable("No supported mouse backend");
    }

    public static MouseProbe legacy() {
        MouseProbe probe = Lwjgl2MouseProbe.create();
        return probe == null ? unavailable("LWJGL 2 mouse unavailable") : probe;
    }

    public static MouseProbe modern() {
        MouseProbe probe = Lwjgl3MouseProbe.create();
        return probe == null ? unavailable("LWJGL 3/GLFW mouse unavailable") : probe;
    }

    private static MouseProbe unavailable(final String description) {
        return new MouseProbe() {
            @Override
            public boolean isAvailable() {
                return false;
            }

            @Override
            public boolean isDown(MouseButton button) {
                return false;
            }

            @Override
            public boolean isWindowFocused() {
                return true;
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
