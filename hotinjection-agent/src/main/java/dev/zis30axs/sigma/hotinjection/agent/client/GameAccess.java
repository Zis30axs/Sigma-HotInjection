package dev.zis30axs.sigma.hotinjection.agent.client;

import java.lang.instrument.Instrumentation;

/** Mapping-independent hooks that version adapters can refine later. */
public final class GameAccess {
    private static volatile Instrumentation instrumentation;

    private GameAccess() { }

    public static void install(Instrumentation value) { instrumentation = value; }
    public static Instrumentation getInstrumentation() { return instrumentation; }

    public static boolean isInWorld() {
        // External Host ClickGUI remains usable even before a mapping-specific world bridge exists.
        return true;
    }
}
