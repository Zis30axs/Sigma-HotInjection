package dev.zis30axs.sigma.hotinjection.agent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

final class AgentOptions {
    private final Map<String, String> values;

    private AgentOptions(Map<String, String> values) {
        this.values = Collections.unmodifiableMap(values);
    }

    static AgentOptions parse(String raw) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        if (raw != null && !raw.trim().isEmpty()) {
            String[] parts = raw.split(";");
            for (String part : parts) {
                int equals = part.indexOf('=');
                if (equals <= 0) {
                    continue;
                }
                String key = part.substring(0, equals).trim();
                String value = part.substring(equals + 1).trim();
                if (!key.isEmpty()) {
                    values.put(key, value);
                }
            }
        }
        return new AgentOptions(values);
    }

    String get(String key) { return values.get(key); }
    boolean getBoolean(String key, boolean fallback) {
        String value = values.get(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }
    Map<String, String> asMap() { return values; }
}
