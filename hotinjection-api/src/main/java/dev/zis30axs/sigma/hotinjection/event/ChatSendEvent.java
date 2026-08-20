package dev.zis30axs.sigma.hotinjection.event;

/**
 * Posted before HotInjection pushes text through the client's own chat send
 * path. Cancelling means no packet is ever created; the message is still shown
 * locally.
 */
public final class ChatSendEvent extends CancellableEvent {
    private String text;

    public ChatSendEvent(String text) {
        this.text = text;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
