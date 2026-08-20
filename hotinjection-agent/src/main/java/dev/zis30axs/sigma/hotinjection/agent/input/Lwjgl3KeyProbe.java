package dev.zis30axs.sigma.hotinjection.agent.input;

import dev.zis30axs.sigma.hotinjection.agent.client.GameAccess;
import dev.zis30axs.sigma.hotinjection.input.HotKey;
import dev.zis30axs.sigma.hotinjection.input.KeyProbe;
import dev.zis30axs.sigma.hotinjection.util.LogUtil;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keyboard source for LWJGL 3 / GLFW runtimes (Minecraft 1.20.1 and newer).
 *
 * <p>GLFW wants its calls on the thread that owns the window, so the key state
 * is refreshed by a task handed to the game's own executor and read back from a
 * cached flag. Only if the game exposes no task queue at all is
 * {@code glfwGetKey} called directly.</p>
 */
public final class Lwjgl3KeyProbe implements KeyProbe {
    private static final int GLFW_KEY_RIGHT_SHIFT = 344;
    private static final int GLFW_PRESS = 1;

    private final Method getKey;
    private final long window;
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private final AtomicBoolean pressed = new AtomicBoolean();
    private final AtomicBoolean directWarning = new AtomicBoolean();
    private volatile boolean broken;

    private Lwjgl3KeyProbe(Method getKey, long window) {
        this.getKey = getKey;
        this.window = window;
    }

    /** @return a probe, or {@code null} when GLFW or the window handle is unavailable. */
    public static Lwjgl3KeyProbe create() {
        Class<?> glfw = GameAccess.findGameClass("org.lwjgl.glfw.GLFW");
        if (glfw == null) {
            return null;
        }
        Method getKey;
        try {
            getKey = glfw.getMethod("glfwGetKey", long.class, int.class);
            getKey.setAccessible(true);
        } catch (Throwable ignored) {
            return null;
        }
        Long handle = GameAccess.windowHandle();
        if (handle == null) {
            LogUtil.warn("GLFW window handle could not be resolved; the RSHIFT hotkey stays disabled.");
            return null;
        }
        return new Lwjgl3KeyProbe(getKey, handle.longValue());
    }

    @Override
    public boolean isAvailable() {
        return !broken;
    }

    @Override
    public boolean isDown(HotKey key) {
        if (broken || key != HotKey.RIGHT_SHIFT) {
            return false;
        }
        refresh();
        return pressed.get();
    }

    @Override
    public String describe() {
        return "LWJGL 3 GLFW (window=0x" + Long.toHexString(window) + ")";
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
                LogUtil.warn("No game task queue found; reading GLFW key state directly.");
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
            Object state = getKey.invoke(null, Long.valueOf(window), Integer.valueOf(GLFW_KEY_RIGHT_SHIFT));
            pressed.set(state instanceof Integer && ((Integer) state).intValue() == GLFW_PRESS);
        } catch (Throwable failure) {
            broken = true;
            LogUtil.warn("GLFW key polling failed: " + failure);
        }
    }
}
