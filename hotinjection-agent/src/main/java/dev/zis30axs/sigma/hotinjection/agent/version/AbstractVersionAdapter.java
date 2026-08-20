package dev.zis30axs.sigma.hotinjection.agent.version;

import dev.zis30axs.sigma.hotinjection.agent.LocalToastBridge;
import dev.zis30axs.sigma.hotinjection.agent.client.ClientChat;
import dev.zis30axs.sigma.hotinjection.agent.client.GameAccess;
import dev.zis30axs.sigma.hotinjection.agent.input.KeyProbes;
import dev.zis30axs.sigma.hotinjection.input.KeyProbe;
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

    /** In-game chat first, local toast when the game's chat HUD cannot be reached. */
    @Override
    public boolean showClientMessage(String message) {
        return ClientChat.send(message) || LocalToastBridge.show(message);
    }

    @Override
    public KeyProbe createKeyProbe() {
        return KeyProbes.autoDetect();
    }

    @Override
    public boolean isInWorld() {
        return GameAccess.isInWorld();
    }
}
