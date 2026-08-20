package dev.zis30axs.sigma.hotinjection.module.setting;

public abstract class ModuleSetting<T> {
    private final String id;
    private final String name;
    private final String description;
    private T value;

    protected ModuleSetting(String id, String name, String description, T value) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Setting id is required");
        this.id = id;
        this.name = name == null ? id : name;
        this.description = description == null ? "" : description;
        this.value = value;
    }

    public final String getId() { return id; }
    public final String getName() { return name; }
    public final String getDescription() { return description; }
    public final synchronized T getValue() { return value; }
    public final synchronized void setValue(T value) { this.value = normalize(value); }
    protected T normalize(T value) { return value; }
    public abstract SettingType getType();
    public abstract String serialize();
    public abstract void deserialize(String value);
    public double getMin() { return 0.0D; }
    public double getMax() { return 0.0D; }
    public double getStep() { return 0.0D; }
    public String[] getOptions() { return new String[0]; }
}
