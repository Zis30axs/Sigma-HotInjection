package dev.zis30axs.sigma.hotinjection.agent;

import dev.zis30axs.sigma.hotinjection.HotInjectionRuntime;
import dev.zis30axs.sigma.hotinjection.event.Event;
import dev.zis30axs.sigma.hotinjection.util.LogUtil;

/**
 * Static handle on the running agent so low level bridges (network, chat) can
 * post events without threading the runtime through every call site.
 */
public final class AgentContext {
    private static volatile HotInjectionRuntime runtime;

    private AgentContext() {
    }

    public static void install(HotInjectionRuntime value) {
        runtime = value;
    }

    public static HotInjectionRuntime runtime() {
        return runtime;
    }

    /**
     * Posts an event and never lets a listener failure escape. Callers run on
     * the netty and render threads, where an exception would break the game.
     */
    public static <E extends Event> E post(E event) {
        HotInjectionRuntime current = runtime;
        if (current == null) {
            return event;
        }
        try {
            return current.getEventBus().post(event);
        } catch (Throwable failure) {
            LogUtil.warn("Event listener failed for " + event.getClass().getSimpleName() + ": " + failure);
            return event;
        }
    }
}
