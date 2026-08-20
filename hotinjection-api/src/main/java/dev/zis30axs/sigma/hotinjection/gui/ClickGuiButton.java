package dev.zis30axs.sigma.hotinjection.gui;

/** A single clickable entry of the ClickGUI. Rendering is up to the active adapter/front-end. */
public final class ClickGuiButton {
    private final String id;
    private final String label;
    private final String description;
    private final ClickGuiAction action;

    public ClickGuiButton(String id, String label, String description, ClickGuiAction action) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Button id is required");
        }
        if (action == null) {
            throw new NullPointerException("action");
        }
        this.id = id.trim();
        this.label = label == null || label.trim().isEmpty() ? this.id : label.trim();
        this.description = description == null ? "" : description.trim();
        this.action = action;
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public String getDescription() { return description; }
    public ClickGuiAction getAction() { return action; }

    @Override
    public String toString() { return label; }
}
