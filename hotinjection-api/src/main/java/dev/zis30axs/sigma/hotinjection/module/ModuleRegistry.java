package dev.zis30axs.sigma.hotinjection.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModuleRegistry {
    private final Map<String, Module> modules = new LinkedHashMap<String, Module>();

    public synchronized <T extends Module> T register(T module) {
        if (module == null) {
            throw new NullPointerException("module");
        }
        if (modules.containsKey(module.getId())) {
            throw new IllegalArgumentException("Duplicate module id: " + module.getId());
        }
        modules.put(module.getId(), module);
        return module;
    }

    public synchronized Module get(String id) {
        return modules.get(id);
    }

    public synchronized List<Module> all() {
        return Collections.unmodifiableList(new ArrayList<Module>(modules.values()));
    }

    public synchronized boolean setEnabled(String id, boolean enabled) {
        Module module = modules.get(id);
        if (module == null) {
            return false;
        }
        module.setEnabled(enabled);
        return true;
    }

    public synchronized void disableAll() {
        for (Module module : modules.values()) {
            if (module.isEnabled()) {
                module.setEnabled(false);
            }
        }
    }
}
