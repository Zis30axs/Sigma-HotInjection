package dev.zis30axs.sigma.hotinjection.method;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MethodRegistry {
    private final Map<String, HotMethod> methods = new LinkedHashMap<String, HotMethod>();

    public synchronized void register(HotMethod method) {
        if (method == null || method.getName() == null || method.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Method name is required");
        }
        if (methods.containsKey(method.getName())) {
            throw new IllegalArgumentException("Duplicate method: " + method.getName());
        }
        methods.put(method.getName(), method);
    }

    public synchronized List<String> names() {
        return Collections.unmodifiableList(new ArrayList<String>(methods.keySet()));
    }

    public String invoke(String name, MethodContext context, String... arguments) throws Exception {
        HotMethod method;
        synchronized (this) {
            method = methods.get(name);
        }
        if (method == null) {
            throw new IllegalArgumentException("Unknown method: " + name);
        }
        return method.invoke(context, Collections.unmodifiableList(Arrays.asList(arguments)));
    }
}
