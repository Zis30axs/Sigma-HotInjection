package dev.zis30axs.sigma.hotinjection.agent.version.v1_7_10;

import dev.zis30axs.sigma.hotinjection.agent.input.KeyProbes;
import dev.zis30axs.sigma.hotinjection.agent.version.AbstractVersionAdapter;
import dev.zis30axs.sigma.hotinjection.input.KeyProbe;
import dev.zis30axs.sigma.hotinjection.version.MinecraftVersion;

public final class V1_7_10Adapter extends AbstractVersionAdapter {
    public V1_7_10Adapter() {
        super(MinecraftVersion.V1_7_10);
    }

    @Override
    public KeyProbe createKeyProbe() {
        return KeyProbes.legacy();
    }
}
