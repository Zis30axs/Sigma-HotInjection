package dev.zis30axs.sigma.hotinjection.agent.client;

import dev.zis30axs.sigma.hotinjection.agent.AgentContext;
import dev.zis30axs.sigma.hotinjection.agent.net.LocalChatGuard;
import dev.zis30axs.sigma.hotinjection.agent.net.NetworkHook;
import dev.zis30axs.sigma.hotinjection.event.ChatSendEvent;
import dev.zis30axs.sigma.hotinjection.util.LogUtil;

/**
 * Client-side chat, the way a client-only command behaves: the client really
 * runs its chat send path, the registered cancel listener drops the packet, and
 * the text is echoed into the local chat HUD so the user sees it.
 */
public final class ClientChat {
    private static volatile boolean sendPathEnabled = true;

    private ClientChat() {
    }

    /** Turns the real client send path on or off (agent option {@code packetguard}). */
    public static void setSendPathEnabled(boolean enabled) {
        sendPathEnabled = enabled;
    }

    /** @return true when the message became visible to the local player. */
    public static boolean send(String message) {
        try {
            runSendPath(message);
        } catch (Throwable failure) {
            LogUtil.warn("Client chat send path failed: " + failure);
        }
        try {
            return ChatSink.send(message);
        } catch (Throwable failure) {
            LogUtil.warn("Client chat echo failed: " + failure);
            return false;
        }
    }

    /** @return true when the local chat HUD of the game can be reached. */
    public static boolean isAvailable() {
        try {
            return ChatSink.isAvailable();
        } catch (Throwable failure) {
            return false;
        }
    }

    public static String describe() {
        return "echo=" + (isAvailable() ? "chat-hud" : "toast")
                + ";sendPath=" + (sendPathEnabled ? "enabled" : "disabled")
                + ";cancelListener=" + (LocalChatGuard.isListenerActive() ? "active" : "none")
                + ";packetGuard=" + NetworkHook.describe();
    }

    private static void runSendPath(String message) {
        if (!sendPathEnabled || !GameAccess.isInWorld()) {
            return;
        }
        ChatSendEvent event = AgentContext.post(new ChatSendEvent(message));
        if (event.isCancelled()) {
            LogUtil.info("ChatSendEvent cancelled; no packet was created at all.");
            return;
        }
        ChatSender.sendThroughGame(event.getText());
    }
}
