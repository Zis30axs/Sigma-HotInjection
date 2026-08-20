package dev.zis30axs.sigma.hotinjection.agent.version;

import dev.zis30axs.sigma.hotinjection.version.MinecraftVersion;

public final class UnknownVersionAdapter extends AbstractVersionAdapter {
    public UnknownVersionAdapter() {
        super(MinecraftVersion.UNKNOWN);
    }
}
