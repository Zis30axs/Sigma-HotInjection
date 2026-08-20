package dev.zis30axs.sigma.hotinjection.host.model;

import java.util.Collections;
import java.util.List;

public final class RemoteSetting {
    private final String id;
    private final String name;
    private final String type;
    private String value;
    private final double min;
    private final double max;
    private final double step;
    private final List<String> options;
    private final String description;

    public RemoteSetting(String id, String name, String type, String value,
                         double min, double max, double step, List<String> options, String description) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.value = value;
        this.min = min;
        this.max = max;
        this.step = step;
        this.options = options == null ? Collections.emptyList() : Collections.unmodifiableList(options);
        this.description = description == null ? "" : description;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getStep() { return step; }
    public List<String> getOptions() { return options; }
    public String getDescription() { return description; }
}
