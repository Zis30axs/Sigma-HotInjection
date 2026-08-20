package dev.zis30axs.sigma.hotinjection.agent;

import dev.zis30axs.sigma.hotinjection.version.MinecraftVersion;
import java.lang.instrument.Instrumentation;
import java.util.Properties;

final class VersionDetector {
    private VersionDetector() {
    }

    static MinecraftVersion detect(AgentOptions options, Properties properties, Instrumentation instrumentation) {
        MinecraftVersion explicit = MinecraftVersion.fromId(options.get("version"));
        if (explicit != MinecraftVersion.UNKNOWN) {
            return explicit;
        }

        String forced = properties.getProperty("sigma.hotinjection.minecraftVersion");
        MinecraftVersion forcedVersion = MinecraftVersion.fromId(forced);
        if (forcedVersion != MinecraftVersion.UNKNOWN) {
            return forcedVersion;
        }

        String[] hints = new String[] {
                properties.getProperty("minecraft.version"),
                properties.getProperty("minecraft.launcher.version"),
                properties.getProperty("sun.java.command"),
                properties.getProperty("java.class.path")
        };
        for (MinecraftVersion version : MinecraftVersion.values()) {
            if (version == MinecraftVersion.UNKNOWN) {
                continue;
            }
            for (String hint : hints) {
                if (hint != null && hint.contains(version.getId())) {
                    return version;
                }
            }
        }

        if (instrumentation != null) {
            for (Class<?> type : instrumentation.getAllLoadedClasses()) {
                String name = type.getName();
                if ("net.minecraft.client.Minecraft".equals(name)
                        || "net.minecraft.client.MinecraftClient".equals(name)) {
                    break;
                }
            }
        }
        return MinecraftVersion.UNKNOWN;
    }
}
