package dev.zis30axs.sigma.hotinjection.event;

public final class ClickGuiToggleEvent extends CancellableEvent {
    public static final String SOURCE_METHOD = "method";
    public static final String SOURCE_KEY = "key";

    private final String source;
    private final boolean opening;

    public ClickGuiToggleEvent(String source, boolean opening) {
        this.source = source == null ? "unknown" : source;
        this.opening = opening;
    }

    public String getSource() { return source; }
    public boolean isOpening() { return opening; }
}
