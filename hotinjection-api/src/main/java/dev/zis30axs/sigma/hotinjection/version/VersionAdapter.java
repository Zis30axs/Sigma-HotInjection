package dev.zis30axs.sigma.hotinjection.version;

import dev.zis30axs.sigma.hotinjection.input.KeyProbe;

public interface VersionAdapter {
    MinecraftVersion getVersion();

    void install(VersionContext context) throws Exception;

    /**
     * Shows a message that only the local user can see. Implementations must not
     * send a chat packet to the server.
     *
     * @return true when the message was displayed somewhere.
     */
    boolean showClientMessage(String message);

    /**
     * @return a keyboard source for this version, or {@code null} when the
     *         version adapter cannot observe key state in this process.
     */
    KeyProbe createKeyProbe();

    /**
     * @return true when the local player is currently in a world. Adapters that
     *         cannot determine this should return true so features stay usable.
     */
    boolean isInWorld();
}
