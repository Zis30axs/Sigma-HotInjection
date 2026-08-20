package dev.zis30axs.sigma.hotinjection.module.setting;

public final class ModeSetting extends ModuleSetting<String> {
    private final String[] options;

    public ModeSetting(String id, String name, String description, String value, String... options) {
        super(id, name, description, value);
        if (options == null || options.length == 0) throw new IllegalArgumentException("ModeSetting requires at least one option");
        this.options = options.clone();
        setValue(value);
    }

    @Override protected String normalize(String value) {
        if (value != null) {
            for (String option : options) if (option.equalsIgnoreCase(value)) return option;
        }
        return options[0];
    }
    @Override public SettingType getType() { return SettingType.MODE; }
    @Override public String serialize() { return getValue(); }
    @Override public void deserialize(String value) { setValue(value); }
    @Override public String[] getOptions() { return options.clone(); }
}
