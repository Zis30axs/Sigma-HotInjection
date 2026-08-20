package dev.zis30axs.sigma.hotinjection.host.overlay;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import dev.zis30axs.sigma.hotinjection.host.overlay.ExtendedUser32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.awt.Rectangle;

/** Resolves and tracks the largest visible top-level window owned by the target JVM. */
final class MinecraftWindowTracker {
    private final int processId;
    private WinDef.HWND cached;

    MinecraftWindowTracker(long processId) {
        if (processId <= 0L || processId > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Unsupported process id: " + processId);
        }
        this.processId = (int) processId;
    }

    Snapshot snapshot() {
        WinDef.HWND window = cached;
        if (!isUsable(window)) {
            window = resolveLargestWindow();
            cached = window;
        }
        if (!isUsable(window)) return null;

        WinDef.RECT client = new WinDef.RECT();
        if (!User32.INSTANCE.GetClientRect(window, client)) return null;
        int width = client.right - client.left;
        int height = client.bottom - client.top;
        if (width < 160 || height < 120) return null;

        WinDef.POINT origin = new WinDef.POINT();
        origin.x = 0;
        origin.y = 0;
        if (!COORDINATES.ClientToScreen(window, origin)) return null;
        boolean foreground = window.equals(User32.INSTANCE.GetForegroundWindow());
        return new Snapshot(new Rectangle(origin.x, origin.y, width, height), foreground);
    }

    private WinDef.HWND resolveLargestWindow() {
        final WinDef.HWND[] best = new WinDef.HWND[1];
        final long[] largestArea = new long[1];
        ExtendedUser32.INSTANCE.EnumWindows(new WinUser.WNDENUMPROC() {
            @Override
            public boolean callback(WinDef.HWND window, Pointer data) {
                if (!belongsToTarget(window)
                        || !ExtendedUser32.INSTANCE.IsWindowVisible(window)
                        || ExtendedUser32.INSTANCE.IsIconic(window)) {
                    return true;
                }
                WinDef.RECT client = new WinDef.RECT();
                if (!ExtendedUser32.INSTANCE.GetClientRect(window, client)) return true;
                long width = Math.max(0, client.right - client.left);
                long height = Math.max(0, client.bottom - client.top);
                long area = width * height;
                if (area > largestArea[0]) {
                    largestArea[0] = area;
                    best[0] = window;
                }
                return true;
            }
        }, null);
        return best[0];
    }

    private boolean isUsable(WinDef.HWND window) {
        return window != null
                && belongsToTarget(window)
                && User32.INSTANCE.IsWindowVisible(window)
                && !ExtendedUser32.INSTANCE.IsIconic(window);
    }

    private boolean belongsToTarget(WinDef.HWND window) {
        if (window == null) return false;
        IntByReference actual = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(window, actual);
        return actual.getValue() == processId;
    }

    private interface CoordinateUser32 extends StdCallLibrary {
        CoordinateUser32 INSTANCE = Native.load(
                "user32", CoordinateUser32.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean ClientToScreen(WinDef.HWND window, WinDef.POINT point);
    }

    private static final CoordinateUser32 COORDINATES = CoordinateUser32.INSTANCE;

    static final class Snapshot {
        private final Rectangle bounds;
        private final boolean foreground;

        Snapshot(Rectangle bounds, boolean foreground) {
            this.bounds = bounds;
            this.foreground = foreground;
        }

        Rectangle getBounds() { return bounds; }
        boolean isForeground() { return foreground; }
    }
}
