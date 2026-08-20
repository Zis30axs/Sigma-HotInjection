package dev.zis30axs.sigma.hotinjection.agent.input;

import dev.zis30axs.sigma.hotinjection.agent.client.GameAccess;
import dev.zis30axs.sigma.hotinjection.input.MouseButton;
import dev.zis30axs.sigma.hotinjection.input.MouseProbe;
import dev.zis30axs.sigma.hotinjection.util.LogUtil;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mouse source for LWJGL 3 / GLFW runtimes (Minecraft 1.20.1 and newer).
 *
 * <p>GLFW input queries belong to the main thread, so the state is refreshed
 * through the game task queue and read from a cache. Values may therefore lag
 * by one client tick, which is irrelevant for click gating.</p>
 */
public final class Lwjgl3MouseProbe implements MouseProbe {
    private static final int GLFW_PRESS = 1;
    private static final int GLFW_FOCUSED = 0x00020001;

    private final Method getMouseButton;
    private final Method getWindowAttrib;
    private final long window;
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private final AtomicBoolean left = new AtomicBoolean();
    private final AtomicBoolean right = new AtomicBoolean();
    private final AtomicBoolean focused = new AtomicBoolean(true);
    private final AtomicBoolean directWarning = new AtomicBoolean();
    private volatile boolean broken;

    private Lwjgl3MouseProbe(Method getMouseButton, Method getWindowAttrib, long window) {
        this.getMouseButton = getMouseButton;
        this.getWindowAttrib = getWindowAttrib;
        this.window = window;
    }

    /** @return a probe, or {@code null} when GLFW or the window handle is unavailable. */
    public static Lwjgl3MouseProbe create() {
        Class<?> glfw = GameAccess.findGameClass("org.lwjgl.glfw.GLFW");
        if (glfw == null) {
            return null;
        }
        Method mouseButton;
        try {
            mouseButton = glfw.getMethod("glfwGetMouseButton", long.class, int.class);
            mouseButton.setAccessible(true);
        } catch (Throwable ignored) {
            return null;
        }
        Method attribute;
        try {
            attribute = glfw.getMethod("glfwGetWindowAttrib", long.class, int.class);
            attribute.setAccessible(true);
        } catch (Throwable ignored) {
            attribute = null;
        }
        Long handle = GameAccess.windowHandle();
        if (handle == null) {
            LogUtil.warn("GLFW window handle could not be resolved; mouse polling stays disabled.");
            return null;
        }
        return new Lwjgl3MouseProbe(mouseButton, attribute, handle.longValue());
    }

    @Override
    public boolean isAvailable() {
        return !broken;
    }

    @Override
    public boolean isDown(MouseButton button) {
        if (broken || button == null) {
            return false;
        }
        refresh();
        return button == MouseButton.LEFT ? left.get() : right.get();
    }

    @Override
    public boolean isWindowFocused() {
        if (broken) {
            return false;
        }
        refresh();
        return focused.get();
    }

    @Override
    public String describe() {
        return "LWJGL 3 GLFW mouse (window=0x" + Long.toHexString(window) + ")";
    }

    @Override
    public void close() {
        broken = true;
        left.set(false);
        right.set(false);
    }

    private void refresh() {
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        boolean scheduled = GameAccess.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    read();
                } finally {
                    refreshing.set(false);
                }
            }
        });
        if (!scheduled) {
            if (directWarning.compareAndSet(false, true)) {
                LogUtil.warn("No game task queue found; reading GLFW mouse state directly.");
            }
            try {
                read();
            } finally {
                refreshing.set(false);
            }
        }
    }

    private void read() {
        try {
            left.set(pressed(MouseButton.LEFT));
            right.set(pressed(MouseButton.RIGHT));
            focused.set(windowFocused());
        } catch (Throwable failure) {
            broken = true;
            LogUtil.warn("GLFW mouse polling failed: " + failure);
        }
    }

    private boolean pressed(MouseButton button) throws Exception {
        Object state = getMouseButton.invoke(null,
                Long.valueOf(window), Integer.valueOf(button.getIndex()));
        return state instanceof Integer && ((Integer) state).intValue() == GLFW_PRESS;
    }

    private boolean windowFocused() throws Exception {
        if (getWindowAttrib == null) {
            return true;
        }
        Object state = getWindowAttrib.invoke(null,
                Long.valueOf(window), Integer.valueOf(GLFW_FOCUSED));
        return !(state instanceof Integer) || ((Integer) state).intValue() != 0;
    }
}
