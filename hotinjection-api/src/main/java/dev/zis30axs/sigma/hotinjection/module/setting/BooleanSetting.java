package dev.zis30axs.sigma.hotinjection.module.setting;

public final class BooleanSetting extends ModuleSetting<Boolean> {
    public BooleanSetting(String id, String name, String description, boolean value) {
        super(id, name, description, Boolean.valueOf(value));
    }
    @Override public SettingType getType() { return SettingType.BOOLEAN; }
    @Override public String serialize() { return Boolean.toString(getValue().booleanValue()); }
    @Override public void deserialize(String value) { setValue(Boolean.valueOf(Boolean.parseBoolean(value))); }
}
