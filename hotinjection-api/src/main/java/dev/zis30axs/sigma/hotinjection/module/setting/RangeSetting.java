package dev.zis30axs.sigma.hotinjection.module.setting;

import java.util.Random;

/**
 * Two-handle numeric interval inside a fixed {@code [min, max]} bound.
 *
 * <p>Used for values that should not be a single constant, such as the click
 * window of an auto clicker. The external Host draws this as one bar with two
 * draggable handles; dragging a handle can never leave the bound.</p>
 */
public final class RangeSetting extends ModuleSetting<RangeSetting.Range> {
    private final double min;
    private final double max;
    private final double step;

    public RangeSetting(String id, String name, String description,
                        double low, double high, double min, double max, double step) {
        super(id, name, description, new Range(low, high));
        if (max < min) throw new IllegalArgumentException("max must be >= min");
        this.min = min;
        this.max = max;
        this.step = step <= 0.0D ? 0.1D : step;
        setValue(new Range(low, high));
    }

    @Override protected Range normalize(Range value) {
        if (value == null) return new Range(min, max);
        double low = snap(value.getLow());
        double high = snap(value.getHigh());
        return low <= high ? new Range(low, high) : new Range(high, low);
    }

    @Override public SettingType getType() { return SettingType.RANGE; }

    @Override public String serialize() {
        Range range = getValue();
        return Double.toString(range.getLow()) + ':' + Double.toString(range.getHigh());
    }

    /** Accepts {@code "low:high"} and a single number, which collapses the range. */
    @Override public void deserialize(String value) {
        if (value == null) throw new IllegalArgumentException("value");
        String trimmed = value.trim();
        int separator = trimmed.indexOf(':');
        if (separator < 0) {
            double single = Double.parseDouble(trimmed);
            setValue(new Range(single, single));
            return;
        }
        setValue(new Range(
                Double.parseDouble(trimmed.substring(0, separator).trim()),
                Double.parseDouble(trimmed.substring(separator + 1).trim())));
    }

    @Override public double getMin() { return min; }
    @Override public double getMax() { return max; }
    @Override public double getStep() { return step; }

    public double getLow() { return getValue().getLow(); }
    public double getHigh() { return getValue().getHigh(); }

    /** @return a uniform value inside the current interval. */
    public double sample(Random random) {
        Range range = getValue();
        double low = range.getLow();
        double high = range.getHigh();
        if (high <= low || random == null) return low;
        return low + random.nextDouble() * (high - low);
    }

    private double snap(double number) {
        double clamped = Math.max(min, Math.min(max, number));
        double snapped = min + Math.round((clamped - min) / step) * step;
        return Math.max(min, Math.min(max, snapped));
    }

    /** Immutable low/high pair. */
    public static final class Range {
        private final double low;
        private final double high;

        public Range(double low, double high) {
            this.low = low;
            this.high = high;
        }

        public double getLow() { return low; }
        public double getHigh() { return high; }

        @Override public String toString() { return low + ":" + high; }
    }
}
