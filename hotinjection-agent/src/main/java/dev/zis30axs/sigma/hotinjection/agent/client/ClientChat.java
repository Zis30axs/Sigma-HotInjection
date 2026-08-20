package dev.zis30axs.sigma.hotinjection.agent.client;

/**
 * Safe local-only chat bridge placeholder. Returning false deliberately falls
 * back to the existing local toast instead of ever sending a server packet.
 */
public final class ClientChat {
    private static volatile boolean sendPathEnabled;

    private ClientChat() { }

    public static void setSendPathEnabled(boolean enabled) { sendPathEnabled = enabled; }
    public static boolean send(String message) { return false; }
    public static String describe() {
        return "localOnly=true;nativeChat=false;sendPath=" + sendPathEnabled;
    }
}
