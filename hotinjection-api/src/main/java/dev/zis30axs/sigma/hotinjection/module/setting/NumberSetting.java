package dev.zis30axs.sigma.hotinjection.module.setting;

public final class NumberSetting extends ModuleSetting<Double> {
    private final double min;
    private final double max;
    private final double step;

    public NumberSetting(String id, String name, String description,
                         double value, double min, double max, double step) {
        super(id, name, description, Double.valueOf(value));
        if (max < min) throw new IllegalArgumentException("max must be >= min");
        this.min = min;
        this.max = max;
        this.step = step <= 0.0D ? 0.1D : step;
        setValue(Double.valueOf(value));
    }

    @Override protected Double normalize(Double value) {
        double number = value == null ? min : value.doubleValue();
        number = Math.max(min, Math.min(max, number));
        double snapped = min + Math.round((number - min) / step) * step;
        return Double.valueOf(Math.max(min, Math.min(max, snapped)));
    }
    @Override public SettingType getType() { return SettingType.NUMBER; }
    @Override public String serialize() { return Double.toString(getValue().doubleValue()); }
    @Override public void deserialize(String value) { setValue(Double.valueOf(Double.parseDouble(value))); }
    @Override public double getMin() { return min; }
    @Override public double getMax() { return max; }
    @Override public double getStep() { return step; }
}
