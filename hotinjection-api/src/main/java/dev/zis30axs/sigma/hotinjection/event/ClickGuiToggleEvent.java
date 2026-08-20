package dev.zis30axs.sigma.hotinjection.event;

/** Posted before the ClickGUI opens or closes. Cancelling keeps the current state. */
public final class ClickGuiToggleEvent extends CancellableEvent {
    /** Toggle requested by the in-game hotkey. */
    public static final String SOURCE_HOTKEY = "hotkey";
    /** Toggle requested through the method registry. */
    public static final String SOURCE_METHOD = "method";
    /** Toggle requested by the GUI itself (close button, ESC). */
    public static final String SOURCE_GUI = "gui";

    private final boolean opening;
    private final String source;

    public ClickGuiToggleEvent(boolean opening, String source) {
        this.opening = opening;
        this.source = source == null || source.trim().isEmpty() ? "unknown" : source.trim();
    }

    public boolean isOpening() { return opening; }
    public boolean isClosing() { return !opening; }
    public String getSource() { return source; }
}
