package dev.zis30axs.sigma.hotinjection.agent.version.v1_21_11;

import dev.zis30axs.sigma.hotinjection.agent.input.KeyProbes;
import dev.zis30axs.sigma.hotinjection.agent.version.AbstractVersionAdapter;
import dev.zis30axs.sigma.hotinjection.input.KeyProbe;
import dev.zis30axs.sigma.hotinjection.version.MinecraftVersion;

public final class V1_21_11Adapter extends AbstractVersionAdapter {
    public V1_21_11Adapter() {
        super(MinecraftVersion.V1_21_11);
    }

    @Override
    public KeyProbe createKeyProbe() {
        return KeyProbes.modern();
    }
}
