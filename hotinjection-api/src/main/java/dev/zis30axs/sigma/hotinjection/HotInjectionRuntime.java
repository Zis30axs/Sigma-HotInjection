package dev.zis30axs.sigma.hotinjection;

import dev.zis30axs.sigma.hotinjection.event.ClientMessageEvent;
import dev.zis30axs.sigma.hotinjection.event.EventBus;
import dev.zis30axs.sigma.hotinjection.gui.ClickGuiHost;
import dev.zis30axs.sigma.hotinjection.gui.ClickGuiRegistry;
import dev.zis30axs.sigma.hotinjection.method.MethodRegistry;
import dev.zis30axs.sigma.hotinjection.module.ModuleRegistry;
import dev.zis30axs.sigma.hotinjection.util.LogUtil;
import dev.zis30axs.sigma.hotinjection.version.MinecraftVersion;
import dev.zis30axs.sigma.hotinjection.version.VersionAdapter;
import dev.zis30axs.sigma.hotinjection.version.VersionRegistry;
import java.lang.instrument.Instrumentation;

public final class HotInjectionRuntime {
    public static final int PROTOCOL_VERSION = 1;

    private final Instrumentation instrumentation;
    private final EventBus eventBus = new EventBus();
    private final ModuleRegistry moduleRegistry = new ModuleRegistry();
    private final MethodRegistry methodRegistry = new MethodRegistry();
    private final VersionRegistry versionRegistry = new VersionRegistry();
    private final ClickGuiRegistry clickGuiRegistry = new ClickGuiRegistry();
    private volatile MinecraftVersion activeVersion = MinecraftVersion.UNKNOWN;
    private volatile VersionAdapter activeAdapter;
    private volatile ClickGuiHost clickGuiHost;

    public HotInjectionRuntime(Instrumentation instrumentation) {
        this.instrumentation = instrumentation;
    }

    public Instrumentation getInstrumentation() { return instrumentation; }
    public EventBus getEventBus() { return eventBus; }
    public ModuleRegistry getModuleRegistry() { return moduleRegistry; }
    public MethodRegistry getMethodRegistry() { return methodRegistry; }
    public VersionRegistry getVersionRegistry() { return versionRegistry; }
    public ClickGuiRegistry getClickGuiRegistry() { return clickGuiRegistry; }
    public MinecraftVersion getActiveVersion() { return activeVersion; }
    public VersionAdapter getActiveAdapter() { return activeAdapter; }
    public ClickGuiHost getClickGuiHost() { return clickGuiHost; }

    public void setClickGuiHost(ClickGuiHost clickGuiHost) { this.clickGuiHost = clickGuiHost; }

    public void activateVersion(MinecraftVersion version, VersionAdapter adapter) {
        this.activeVersion = version == null ? MinecraftVersion.UNKNOWN : version;
        this.activeAdapter = adapter;
    }

    /**
     * Posts a {@link ClientMessageEvent} and, unless a listener cancels it, hands
     * the message to the active adapter for local-only display.
     *
     * @return true when the message was displayed.
     */
    public boolean sendClientMessage(String source, String message) {
        ClientMessageEvent event = eventBus.post(new ClientMessageEvent(source, message));
        if (event.isCancelled()) {
            LogUtil.info("Client message cancelled (source=" + event.getSource() + ").");
            return false;
        }
        VersionAdapter adapter = activeAdapter;
        if (adapter == null) {
            LogUtil.warn("No active version adapter; dropping client message: " + event.getMessage());
            return false;
        }
        return adapter.showClientMessage(event.getMessage());
    }
}
