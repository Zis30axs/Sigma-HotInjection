package dev.zis30axs.sigma.hotinjection.version;

public interface VersionAdapter {
    MinecraftVersion getVersion();
    void install(VersionContext context) throws Exception;
    boolean showClientMessage(String message);
}
