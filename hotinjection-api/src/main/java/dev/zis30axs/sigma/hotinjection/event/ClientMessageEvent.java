package dev.zis30axs.sigma.hotinjection.event;

public final class ClientMessageEvent extends CancellableEvent {
    public static final String SOURCE_INJECTION = "injection";
    public static final String SOURCE_METHOD = "method";
    public static final String SOURCE_CLICK_GUI = "clickgui";

    private final String source;
    private String message;

    public ClientMessageEvent(String source, String message) {
        this.source = source == null ? "unknown" : source;
        this.message = message == null ? "" : message;
    }

    public String getSource() { return source; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message == null ? "" : message; }
}
