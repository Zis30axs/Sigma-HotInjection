package dev.zis30axs.sigma.hotinjection.agent.version;

import dev.zis30axs.sigma.hotinjection.agent.LocalToastBridge;
import dev.zis30axs.sigma.hotinjection.util.LogUtil;
import dev.zis30axs.sigma.hotinjection.version.MinecraftVersion;
import dev.zis30axs.sigma.hotinjection.version.VersionAdapter;
import dev.zis30axs.sigma.hotinjection.version.VersionContext;

public abstract class AbstractVersionAdapter implements VersionAdapter {
    private final MinecraftVersion version;

    protected AbstractVersionAdapter(MinecraftVersion version) {
        this.version = version;
    }

    @Override
    public final MinecraftVersion getVersion() { return version; }

    @Override
    public void install(VersionContext context) {
        LogUtil.info("Version adapter ready: " + version.getId());
    }

    @Override
    public boolean showClientMessage(String message) {
        return LocalToastBridge.show(message);
    }
}
