package dev.zis30axs.sigma.hotinjection.host.overlay;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;

public interface ExtendedUser32 extends User32 {

    ExtendedUser32 INSTANCE =
            com.sun.jna.Native.load(
                    "user32",
                    ExtendedUser32.class
            );

    boolean IsIconic(WinDef.HWND hwnd);
}