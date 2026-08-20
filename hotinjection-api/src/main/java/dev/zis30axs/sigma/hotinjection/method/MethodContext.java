package dev.zis30axs.sigma.hotinjection.method;

import dev.zis30axs.sigma.hotinjection.HotInjectionRuntime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class MethodContext {
    private final HotInjectionRuntime runtime;
    private final Map<String, Object> attributes;

    public MethodContext(HotInjectionRuntime runtime) {
        this(runtime, Collections.<String, Object>emptyMap());
    }

    public MethodContext(HotInjectionRuntime runtime, Map<String, Object> attributes) {
        this.runtime = runtime;
        this.attributes = Collections.unmodifiableMap(new HashMap<String, Object>(attributes));
    }

    public HotInjectionRuntime getRuntime() { return runtime; }
    public Object getAttribute(String key) { return attributes.get(key); }
}
