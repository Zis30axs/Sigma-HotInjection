package dev.zis30axs.sigma.hotinjection.agent.input;

import dev.zis30axs.sigma.hotinjection.agent.client.GameAccess;
import dev.zis30axs.sigma.hotinjection.input.MouseButton;
import dev.zis30axs.sigma.hotinjection.input.MouseProbe;

import java.lang.reflect.Method;

/** Mouse source for LWJGL 2 runtimes (Minecraft 1.7.10 and 1.8.9). */
public final class Lwjgl2MouseProbe implements MouseProbe {
    private final Method isCreated;
    private final Method isButtonDown;
    private final Method isActive;

    private Lwjgl2MouseProbe(Method isCreated, Method isButtonDown, Method isActive) {
        this.isCreated = isCreated;
        this.isButtonDown = isButtonDown;
        this.isActive = isActive;
    }

    /** @return a probe, or {@code null} when LWJGL 2 is not present. */
    public static Lwjgl2MouseProbe create() {
        Class<?> mouse = GameAccess.findGameClass("org.lwjgl.input.Mouse");
        if (mouse == null) {
            return null;
        }
        Method buttonDown = declared(mouse, "isButtonDown", int.class);
        if (buttonDown == null) {
            return null;
        }
        Class<?> display = GameAccess.findGameClass("org.lwjgl.opengl.Display");
        return new Lwjgl2MouseProbe(
                declared(mouse, "isCreated"),
                buttonDown,
                display == null ? null : declared(display, "isActive"));
    }

    @Override
    public boolean isAvailable() {
        if (isCreated == null) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(isCreated.invoke(null));
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public boolean isDown(MouseButton button) {
        if (button == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(isButtonDown.invoke(null, Integer.valueOf(button.getIndex())));
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public boolean isWindowFocused() {
        if (isActive == null) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(isActive.invoke(null));
        } catch (Throwable ignored) {
            return true;
        }
    }

    @Override
    public String describe() {
        return "LWJGL 2 Mouse";
    }

    @Override
    public void close() {
        // LWJGL owns the mouse lifecycle; the probe has no resources to release.
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
