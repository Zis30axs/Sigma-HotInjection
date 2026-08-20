package dev.zis30axs.sigma.hotinjection.event;

/**
 * Posted before a local-only message is shown to the injecting user.
 *
 * <p>Cancelling drops the message. Nothing on this path ever reaches the
 * network: the message is rendered by the active version adapter into the
 * local chat GUI (or the local toast fallback) and no packet is sent.</p>
 */
public final class ClientMessageEvent extends CancellableEvent {
    /** Message produced right after the agent finished initializing. */
    public static final String SOURCE_INJECTION = "injection";
    /** Message produced by the ClickGUI TEST button. */
    public static final String SOURCE_CLICKGUI = "clickgui";
    /** Message produced by a host/Bootstrap method call. */
    public static final String SOURCE_METHOD = "method";

    private final String source;
    private String message;

    public ClientMessageEvent(String source, String message) {
        this.source = source == null || source.trim().isEmpty() ? "unknown" : source.trim();
        this.message = message;
    }

    public String getSource() { return source; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
