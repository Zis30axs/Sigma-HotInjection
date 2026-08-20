package dev.zis30axs.sigma.hotinjection.host.model;

public final class RemoteModule {
    private final String id;
    private final String name;
    private final String category;
    private final String description;
    private boolean enabled;

    public RemoteModule(String id, String name, String category, boolean enabled, String description) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.enabled = enabled;
        this.description = description == null ? "" : description;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getDescription() { return description; }
}
