package dev.zis30axs.sigma.hotinjection.event;

import dev.zis30axs.sigma.hotinjection.version.MinecraftVersion;

/** Posted after the agent runtime has initialized. Cancelling suppresses the local notice. */
public final class InjectionNoticeEvent extends CancellableEvent {
    private final MinecraftVersion version;
    private String message;

    public InjectionNoticeEvent(MinecraftVersion version, String message) {
        this.version = version;
        this.message = message;
    }

    public MinecraftVersion getVersion() { return version; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
