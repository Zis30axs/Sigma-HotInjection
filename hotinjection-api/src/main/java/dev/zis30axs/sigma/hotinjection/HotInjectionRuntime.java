package dev.zis30axs.sigma.hotinjection;

import dev.zis30axs.sigma.hotinjection.event.EventBus;
import dev.zis30axs.sigma.hotinjection.method.MethodRegistry;
import dev.zis30axs.sigma.hotinjection.module.ModuleRegistry;
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
    private volatile MinecraftVersion activeVersion = MinecraftVersion.UNKNOWN;
    private volatile VersionAdapter activeAdapter;

    public HotInjectionRuntime(Instrumentation instrumentation) {
        this.instrumentation = instrumentation;
    }

    public Instrumentation getInstrumentation() { return instrumentation; }
    public EventBus getEventBus() { return eventBus; }
    public ModuleRegistry getModuleRegistry() { return moduleRegistry; }
    public MethodRegistry getMethodRegistry() { return methodRegistry; }
    public VersionRegistry getVersionRegistry() { return versionRegistry; }
    public MinecraftVersion getActiveVersion() { return activeVersion; }
    public VersionAdapter getActiveAdapter() { return activeAdapter; }

    public void activateVersion(MinecraftVersion version, VersionAdapter adapter) {
        this.activeVersion = version == null ? MinecraftVersion.UNKNOWN : version;
        this.activeAdapter = adapter;
    }
}
