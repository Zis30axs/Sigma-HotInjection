package dev.zis30axs.sigma.hotinjection.agent.client;

import dev.zis30axs.sigma.hotinjection.input.MouseButton;
import dev.zis30axs.sigma.hotinjection.util.LogUtil;

import java.awt.Robot;
import java.awt.event.InputEvent;
import java.lang.reflect.Method;

/**
 * Sends clicks into the running client.
 *
 * <p>The preferred path calls the client's own mouse handlers on the game
 * thread, so the game performs a normal click including swing animation and
 * packets. When those methods cannot be resolved, the bridge falls back to a
 * synthetic AWT click, which is delivered to whatever window has focus and is
 * therefore only useful while Minecraft is focused.</p>
 */
public final class ClickBridge {
    /** Which dispatch path a module allows. */
    public enum Dispatch {
        AUTO, GAME, NATIVE;

        public static Dispatch parse(String value) {
            if (value == null) return AUTO;
            String trimmed = value.trim();
            for (Dispatch dispatch : values()) {
                if (dispatch.name().equalsIgnoreCase(trimmed)) return dispatch;
            }
            return AUTO;
        }
    }

    private static final String[] LEFT_METHODS = {
            "clickMouse", "startAttack", "doAttack",
            "func_147116_af", "method_1583", "m_91386_"
    };
    private static final String[] RIGHT_METHODS = {
            "rightClickMouse", "startUseItem", "doItemUse",
            "func_147121_ag", "method_1584", "m_91387_"
    };

    private static volatile Method leftClick;
    private static volatile Method rightClick;
    private static volatile boolean leftResolved;
    private static volatile boolean rightResolved;
    private static volatile Robot robot;
    private static volatile boolean robotResolved;

    private ClickBridge() {
    }

    /** @return true when the click was handed to the game or to the OS. */
    public static boolean click(MouseButton button, Dispatch dispatch) {
        if (button == null) return false;
        if (dispatch != Dispatch.NATIVE && gameClick(button)) return true;
        return dispatch != Dispatch.GAME && nativeClick(button);
    }

    /** @return true when the in-game click handler for {@code button} was found. */
    public static boolean isGameDispatchAvailable(MouseButton button) {
        return clickMethod(button) != null;
    }

    public static String describe(MouseButton button) {
        Method method = clickMethod(button);
        // The AWT backend is created on first use, so describing must not force it.
        return method != null ? "game handler " + method.getName() + "()" : "synthetic AWT click";
    }

    private static boolean gameClick(MouseButton button) {
        final Method method = clickMethod(button);
        final Object mc = GameAccess.client();
        if (method == null || mc == null) return false;
        return GameAccess.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    method.invoke(mc);
                } catch (Throwable ignored) {
                    // A single dropped click is preferable to killing the client thread.
                }
            }
        });
    }

    private static boolean nativeClick(MouseButton button) {
        Robot input = robot();
        if (input == null) return false;
        int mask = button == MouseButton.LEFT
                ? InputEvent.BUTTON1_DOWN_MASK
                : InputEvent.BUTTON3_DOWN_MASK;
        try {
            input.mousePress(mask);
            input.delay(1);
            input.mouseRelease(mask);
            return true;
        } catch (Throwable failure) {
            robot = null;
            robotResolved = true;
            LogUtil.warn("Synthetic click failed: " + failure);
            return false;
        }
    }

    private static Method clickMethod(MouseButton button) {
        boolean left = button == MouseButton.LEFT;
        if (left ? leftResolved : rightResolved) {
            return left ? leftClick : rightClick;
        }
        Class<?> type = GameAccess.clientClass();
        if (type == null) return null;
        Method resolved = GameAccess.findMethod(type, left ? LEFT_METHODS : RIGHT_METHODS);
        if (left) {
            leftClick = resolved;
            leftResolved = true;
        } else {
            rightClick = resolved;
            rightResolved = true;
        }
        if (resolved == null) {
            LogUtil.warn("No in-game " + (left ? "attack" : "use item")
                    + " handler found; falling back to synthetic clicks.");
        }
        return resolved;
    }

    private static Robot robot() {
        if (robotResolved) return robot;
        robotResolved = true;
        try {
            Robot created = new Robot();
            created.setAutoDelay(0);
            created.setAutoWaitForIdle(false);
            robot = created;
        } catch (Throwable failure) {
            LogUtil.warn("Synthetic click backend unavailable: " + failure);
            robot = null;
        }
        return robot;
    }
}
