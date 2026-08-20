package dev.zis30axs.sigma.hotinjection.agent;

import dev.zis30axs.sigma.hotinjection.util.LogUtil;
import java.lang.instrument.Instrumentation;

public final class HotInjectionAgent {
    private HotInjectionAgent() {
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        bootstrap("premain", agentArgs, instrumentation);
    }

    public static void agentmain(String agentArgs, Instrumentation instrumentation) {
        bootstrap("attach", agentArgs, instrumentation);
    }

    private static void bootstrap(String source, String agentArgs, Instrumentation instrumentation) {
        try {
            RuntimeBootstrap.start(source, AgentOptions.parse(agentArgs), instrumentation);
        } catch (Throwable error) {
            LogUtil.error("Agent bootstrap failed", error);
            throw new RuntimeException("Sigma HotInjection bootstrap failed", error);
        }
    }
}
