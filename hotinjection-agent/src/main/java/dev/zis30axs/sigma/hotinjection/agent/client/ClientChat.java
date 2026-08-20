package dev.zis30axs.sigma.hotinjection.agent.client;

/** Mapping-independent local-only client chat facade. */
public final class ClientChat {
    private static volatile boolean sendPathEnabled;

    private ClientChat() {
    }

    public static void setSendPathEnabled(boolean enabled) {
        sendPathEnabled = enabled;
    }

    public static boolean isAvailable() {
        return ChatSink.isAvailable();
    }

    public static boolean send(String message) {
        boolean shown = ChatSink.send(message);
        if (sendPathEnabled) {
            ChatSender.sendThroughGame(message);
        }
        return shown;
    }

    public static String describe() {
        return "localOnly=true;nativeChat=" + isAvailable() + ";sendPath=" + sendPathEnabled;
    }
}
