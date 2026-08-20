package dev.zis30axs.sigma.hotinjection.agent;

import dev.zis30axs.sigma.hotinjection.HotInjectionRuntime;
import dev.zis30axs.sigma.hotinjection.event.Event;
import dev.zis30axs.sigma.hotinjection.util.LogUtil;

/** Shared access to the running agent for low-level bridges such as Netty hooks. */
public final class AgentContext {
    private static volatile HotInjectionRuntime runtime;

    private AgentContext() {
    }

    static void install(HotInjectionRuntime value) {
        runtime = value;
    }

    public static HotInjectionRuntime getRuntime() {
        return runtime;
    }

    public static <E extends Event> E post(E event) {
        HotInjectionRuntime current = runtime;
        if (current == null || event == null) {
            return event;
        }
        try {
            return current.getEventBus().post(event);
        } catch (Throwable failure) {
            LogUtil.warn("Event listener failed for " + event.getClass().getName() + ": " + failure);
            return event;
        }
    }
}
