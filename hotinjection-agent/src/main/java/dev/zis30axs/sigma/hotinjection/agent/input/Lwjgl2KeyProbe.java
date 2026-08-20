package dev.zis30axs.sigma.hotinjection.agent.input;

import dev.zis30axs.sigma.hotinjection.agent.client.GameAccess;
import dev.zis30axs.sigma.hotinjection.input.HotKey;
import dev.zis30axs.sigma.hotinjection.input.KeyProbe;

import java.lang.reflect.Method;

/** Keyboard source for LWJGL 2 runtimes (Minecraft 1.7.10 and 1.8.9). */
public final class Lwjgl2KeyProbe implements KeyProbe {
    private static final int KEY_RSHIFT = 0x36;

    private final Method isCreated;
    private final Method isKeyDown;

    private Lwjgl2KeyProbe(Method isCreated, Method isKeyDown) {
        this.isCreated = isCreated;
        this.isKeyDown = isKeyDown;
    }

    /** @return a probe, or {@code null} when LWJGL 2 is not present. */
    public static Lwjgl2KeyProbe create() {
        Class<?> keyboard = GameAccess.findGameClass("org.lwjgl.input.Keyboard");
        if (keyboard == null) {
            return null;
        }
        Method keyDown = declared(keyboard, "isKeyDown", int.class);
        if (keyDown == null) {
            return null;
        }
        return new Lwjgl2KeyProbe(declared(keyboard, "isCreated"), keyDown);
    }

    @Override
    public boolean isAvailable() {
        if (isCreated == null) {
            return true;
        }
        try {
            Object created = isCreated.invoke(null);
            return Boolean.TRUE.equals(created);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public boolean isDown(HotKey key) {
        if (key != HotKey.RIGHT_SHIFT) {
            return false;
        }
        try {
            Object down = isKeyDown.invoke(null, Integer.valueOf(KEY_RSHIFT));
            return Boolean.TRUE.equals(down);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public String describe() {
        return "LWJGL 2 Keyboard";
    }

    @Override
    public void close() {
        // LWJGL owns the keyboard lifecycle; the probe has no resources to release.
    }

    private static Method declared(Class<?> type, String name, Class<?>... parameters) {
        try {
            Method method = type.getMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
