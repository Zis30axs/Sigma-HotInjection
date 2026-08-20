package dev.zis30axs.sigma.hotinjection.agent;

import dev.zis30axs.sigma.hotinjection.HotInjectionRuntime;
import dev.zis30axs.sigma.hotinjection.agent.methods.DefaultMethods;
import dev.zis30axs.sigma.hotinjection.agent.modules.QuietNoticeModule;
import dev.zis30axs.sigma.hotinjection.agent.version.UnknownVersionAdapter;
import dev.zis30axs.sigma.hotinjection.agent.version.v1_7_10.V1_7_10Adapter;
import dev.zis30axs.sigma.hotinjection.agent.version.v1_8_9.V1_8_9Adapter;
import dev.zis30axs.sigma.hotinjection.agent.version.v1_20_1.V1_20_1Adapter;
import dev.zis30axs.sigma.hotinjection.agent.version.v1_21_11.V1_21_11Adapter;
import dev.zis30axs.sigma.hotinjection.agent.version.v26_2.V26_2Adapter;
import dev.zis30axs.sigma.hotinjection.event.InjectionNoticeEvent;
import dev.zis30axs.sigma.hotinjection.method.MethodContext;
import dev.zis30axs.sigma.hotinjection.util.LogUtil;
import dev.zis30axs.sigma.hotinjection.version.MinecraftVersion;
import dev.zis30axs.sigma.hotinjection.version.VersionAdapter;
import dev.zis30axs.sigma.hotinjection.version.VersionContext;
import java.lang.instrument.Instrumentation;
import java.util.concurrent.atomic.AtomicBoolean;

final class RuntimeBootstrap {
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static volatile HotInjectionRuntime runtime;

    private RuntimeBootstrap() {
    }

    static void start(String source, AgentOptions options, Instrumentation instrumentation) throws Exception {
        if (!STARTED.compareAndSet(false, true)) {
            LogUtil.warn("Agent is already initialized; ignoring duplicate " + source + " request.");
            return;
        }

        HotInjectionRuntime created = new HotInjectionRuntime(instrumentation);
        runtime = created;
        registerVersions(created);

        QuietNoticeModule quietNotice = created.getModuleRegistry().register(new QuietNoticeModule(created.getEventBus()));
        if (!options.getBoolean("notice", true) || options.getBoolean("quiet", false)) {
            quietNotice.setEnabled(true);
        }
        DefaultMethods.register(created);

        MinecraftVersion version = VersionDetector.detect(options, System.getProperties(), instrumentation);
        VersionAdapter adapter = created.getVersionRegistry().get(version);
        if (adapter == null) {
            adapter = created.getVersionRegistry().get(MinecraftVersion.UNKNOWN);
        }
        created.activateVersion(version, adapter);
        adapter.install(new VersionContext(created, instrumentation, System.getProperties(), options.asMap()));

        LogUtil.info("Attached from " + source + "; Minecraft version=" + version.getId());
        LogUtil.info(created.getMethodRegistry().invoke("runtime.info", new MethodContext(created)));

        InjectionNoticeEvent notice = created.getEventBus().post(new InjectionNoticeEvent(
                version,
                "Sigma HotInjection injected successfully - Minecraft " + version.getId()));
        if (!notice.isCancelled()) {
            adapter.showClientMessage(notice.getMessage());
        } else {
            LogUtil.info("Injection notice was cancelled by the event bus.");
        }
    }

    static HotInjectionRuntime getRuntime() { return runtime; }

    private static void registerVersions(HotInjectionRuntime runtime) {
        runtime.getVersionRegistry().register(new V1_7_10Adapter());
        runtime.getVersionRegistry().register(new V1_8_9Adapter());
        runtime.getVersionRegistry().register(new V1_20_1Adapter());
        runtime.getVersionRegistry().register(new V1_21_11Adapter());
        runtime.getVersionRegistry().register(new V26_2Adapter());
        runtime.getVersionRegistry().register(new UnknownVersionAdapter());
    }
}
