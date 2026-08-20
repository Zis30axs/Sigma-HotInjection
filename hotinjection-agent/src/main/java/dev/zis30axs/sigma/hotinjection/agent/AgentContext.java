package dev.zis30axs.sigma.hotinjection.agent;

import dev.zis30axs.sigma.hotinjection.HotInjectionRuntime;

public final class AgentContext {
    private static volatile HotInjectionRuntime runtime;

    private AgentContext() { }

    static void install(HotInjectionRuntime value) { runtime = value; }
    public static HotInjectionRuntime getRuntime() { return runtime; }
}
