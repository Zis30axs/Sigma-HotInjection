package dev.zis30axs.sigma.hotinjection.gui;

public final class ClickGuiButton {
    private final String id;
    private final String label;
    private final ClickGuiAction action;

    public ClickGuiButton(String id, String label, ClickGuiAction action) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Button id is required");
        if (action == null) throw new NullPointerException("action");
        this.id = id;
        this.label = label == null ? id : label;
        this.action = action;
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public ClickGuiAction getAction() { return action; }
}
