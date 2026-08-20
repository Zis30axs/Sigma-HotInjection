package dev.zis30axs.sigma.hotinjection.gui;

public final class ClickGuiButton {
    private final String id;
    private final String label;
    private final String description;
    private final ClickGuiAction action;

    public ClickGuiButton(String id, String label, ClickGuiAction action) {
        this(id, label, "", action);
    }

    public ClickGuiButton(String id, String label, String description, ClickGuiAction action) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Button id is required");
        if (action == null) throw new NullPointerException("action");
        this.id = id;
        this.label = label == null ? id : label;
        this.description = description == null ? "" : description;
        this.action = action;
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public String getDescription() { return description; }
    public ClickGuiAction getAction() { return action; }
}
