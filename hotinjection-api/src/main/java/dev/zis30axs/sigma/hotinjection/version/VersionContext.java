package dev.zis30axs.sigma.hotinjection.version;

import dev.zis30axs.sigma.hotinjection.HotInjectionRuntime;
import java.lang.instrument.Instrumentation;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class VersionContext {
    private final HotInjectionRuntime runtime;
    private final Instrumentation instrumentation;
    private final Properties systemProperties;
    private final Map<String, String> options;

    public VersionContext(HotInjectionRuntime runtime,
                          Instrumentation instrumentation,
                          Properties systemProperties,
                          Map<String, String> options) {
        this.runtime = runtime;
        this.instrumentation = instrumentation;
        this.systemProperties = systemProperties;
        this.options = Collections.unmodifiableMap(new HashMap<String, String>(options));
    }

    public HotInjectionRuntime getRuntime() { return runtime; }
    public Instrumentation getInstrumentation() { return instrumentation; }
    public Properties getSystemProperties() { return systemProperties; }
    public String getOption(String key) { return options.get(key); }
}
