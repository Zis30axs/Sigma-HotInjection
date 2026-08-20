package dev.zis30axs.sigma.hotinjection.version;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class VersionRegistry {
    private final Map<MinecraftVersion, VersionAdapter> adapters =
            new EnumMap<MinecraftVersion, VersionAdapter>(MinecraftVersion.class);

    public synchronized <T extends VersionAdapter> T register(T adapter) {
        if (adapter == null) {
            throw new NullPointerException("adapter");
        }
        if (adapters.containsKey(adapter.getVersion())) {
            throw new IllegalArgumentException("Duplicate version adapter: " + adapter.getVersion());
        }
        adapters.put(adapter.getVersion(), adapter);
        return adapter;
    }

    public synchronized VersionAdapter get(MinecraftVersion version) {
        return adapters.get(version);
    }

    public synchronized List<VersionAdapter> all() {
        return Collections.unmodifiableList(new ArrayList<VersionAdapter>(adapters.values()));
    }
}
