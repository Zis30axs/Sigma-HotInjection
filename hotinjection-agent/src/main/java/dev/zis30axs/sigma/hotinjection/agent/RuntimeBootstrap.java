package dev.zis30axs.sigma.hotinjection.agent;

import dev.zis30axs.sigma.hotinjection.HotInjectionRuntime;
import dev.zis30axs.sigma.hotinjection.agent.client.ClientChat;
import dev.zis30axs.sigma.hotinjection.agent.client.GameAccess;
import dev.zis30axs.sigma.hotinjection.agent.gui.DefaultClickGuiButtons;
import dev.zis30axs.sigma.hotinjection.agent.methods.DefaultMethods;
import dev.zis30axs.sigma.hotinjection.agent.modules.ClickGuiModule;
import dev.zis30axs.sigma.hotinjection.agent.modules.LocalChatModule;
import dev.zis30axs.sigma.hotinjection.agent.modules.QuietNoticeModule;
import dev.zis30axs.sigma.hotinjection.agent.version.UnknownVersionAdapter;
import dev.zis30axs.sigma.hotinjection.agent.version.v1_7_10.V1_7_10Adapter;
import dev.zis30axs.sigma.hotinjection.agent.version.v1_8_9.V1_8_9Adapter;
import dev.zis30axs.sigma.hotinjection.agent.version.v1_20_1.V1_20_1Adapter;
import dev.zis30axs.sigma.hotinjection.agent.version.v1_21_11.V1_21_11Adapter;
import dev.zis30axs.sigma.hotinjection.agent.version.v26_2.V26_2Adapter;
import dev.zis30axs.sigma.hotinjection.event.ClientMessageEvent;
import dev.zis30axs.sigma.hotinjection.event.InjectionNoticeEvent;
import dev.zis30axs.sigma.hotinjection.method.MethodContext;
import dev.zis30axs.sigma.hotinjection.util.LogUtil;
import dev.zis30axs.sigma.hotinjection.version.MinecraftVersion;
import dev.zis30axs.sigma.hotinjection.version.VersionAdapter;
import dev.zis30axs.sigma.hotinjection.version.VersionContext;
import java.lang.instrument.Instrumentation;
import java.util.concurrent.atomic.AtomicBoolean;

final class RuntimeBootstrap {
    /** Local-only proof that the agent is live. */
    static final String INJECTION_MESSAGE = "[SIGMA] Injected!";

    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static volatile HotInjectionRuntime runtime;

    private RuntimeBootstrap() {
    }

    static void start(String source, AgentOptions options, Instrumentation instrumentation) throws Exception {
        if (!STARTED.compareAndSet(false, true)) {
            LogUtil.warn("Agent is already initialized; ignoring duplicate " + source + " request.");
            return;
        }

        GameAccess.install(instrumentation);

        HotInjectionRuntime created = new HotInjectionRuntime(instrumentation);
        runtime = created;
        AgentContext.install(created);
        registerVersions(created);

        QuietNoticeModule quietNotice = created.getModuleRegistry().register(new QuietNoticeModule(created.getEventBus()));
        if (!options.getBoolean("notice", true) || options.getBoolean("quiet", false)) {
            quietNotice.setEnabled(true);
        }

        boolean packetGuard = options.getBoolean("packetguard", true);
        ClientChat.setSendPathEnabled(packetGuard);
        LocalChatModule localChat = created.getModuleRegistry().register(new LocalChatModule(created.getEventBus()));
        if (packetGuard) {
            localChat.setEnabled(true);
        }
        DefaultMethods.register(created);

        MinecraftVersion version = VersionDetector.detect(options, System.getProperties(), instrumentation);
        VersionAdapter adapter = created.getVersionRegistry().get(version);
        if (adapter == null) {
            adapter = created.getVersionRegistry().get(MinecraftVersion.UNKNOWN);
        }
        created.activateVersion(version, adapter);
        adapter.install(new VersionContext(created, instrumentation, System.getProperties(), options.asMap()));

        DefaultClickGuiButtons.register(created);
        ClickGuiModule clickGui = created.getModuleRegistry().register(new ClickGuiModule(created));
        if (options.getBoolean("clickgui", true)) {
            clickGui.setEnabled(true);
        }

        LogUtil.info("Attached from " + source + "; Minecraft version=" + version.getId());
        LogUtil.info(created.getMethodRegistry().invoke("runtime.info", new MethodContext(created)));

        announce(created, version);
    }

    static HotInjectionRuntime getRuntime() { return runtime; }

    private static void announce(HotInjectionRuntime created, MinecraftVersion version) {
        InjectionNoticeEvent notice = created.getEventBus().post(
                new InjectionNoticeEvent(version, INJECTION_MESSAGE));
        if (notice.isCancelled()) {
            LogUtil.info("Injection notice was cancelled by the event bus.");
            return;
        }
        if (!created.sendClientMessage(ClientMessageEvent.SOURCE_INJECTION, notice.getMessage())) {
            LogUtil.warn("Injection notice could not be displayed anywhere.");
        }
    }

    private static void registerVersions(HotInjectionRuntime runtime) {
        runtime.getVersionRegistry().register(new V1_7_10Adapter());
        runtime.getVersionRegistry().register(new V1_8_9Adapter());
        runtime.getVersionRegistry().register(new V1_20_1Adapter());
        runtime.getVersionRegistry().register(new V1_21_11Adapter());
        runtime.getVersionRegistry().register(new V26_2Adapter());
        runtime.getVersionRegistry().register(new UnknownVersionAdapter());
    }
}
