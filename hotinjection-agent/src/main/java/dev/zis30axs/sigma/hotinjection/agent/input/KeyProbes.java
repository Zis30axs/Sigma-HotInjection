package dev.zis30axs.sigma.hotinjection.agent.input;

import dev.zis30axs.sigma.hotinjection.input.KeyProbe;
import java.lang.reflect.Method;

public final class KeyProbes {
    private KeyProbes() { }

    public static KeyProbe autoDetect() { return legacy(); }

    public static KeyProbe legacy() {
        try {
            final Class<?> keyboard = Class.forName("org.lwjgl.input.Keyboard");
            final Method isKeyDown = keyboard.getMethod("isKeyDown", Integer.TYPE);
            return new KeyProbe() {
                @Override
                public boolean isRightShiftDown() {
                    try {
                        return Boolean.TRUE.equals(isKeyDown.invoke(null, Integer.valueOf(54)));
                    } catch (Throwable ignored) {
                        return false;
                    }
                }

                @Override
                public void close() { }
            };
        } catch (Throwable ignored) {
            return unavailable();
        }
    }

    public static KeyProbe modern() {
        // GLFW requires a verified Minecraft window handle. Never guess one.
        return unavailable();
    }

    private static KeyProbe unavailable() {
        return new KeyProbe() {
            @Override public boolean isRightShiftDown() { return false; }
            @Override public void close() { }
        };
    }
}
