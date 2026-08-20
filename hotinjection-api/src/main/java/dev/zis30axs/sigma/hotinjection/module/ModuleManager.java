package dev.zis30axs.sigma.hotinjection.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ModuleManager {
    private final Map<String, Module> byId = new LinkedHashMap<String, Module>();
    private final Map<String, Module> byName = new LinkedHashMap<String, Module>();
    private final Map<ModuleCategory, List<Module>> byCategory =
            new EnumMap<ModuleCategory, List<Module>>(ModuleCategory.class);

    public ModuleManager() {
        for (ModuleCategory category : ModuleCategory.values()) {
            byCategory.put(category, new ArrayList<Module>());
        }
    }

    public synchronized <T extends Module> T register(T module) {
        if (module == null) throw new NullPointerException("module");
        String idKey = normalize(module.getId());
        String nameKey = normalize(module.getName());
        if (byId.containsKey(idKey)) throw new IllegalArgumentException("Duplicate module id: " + module.getId());
        if (byName.containsKey(nameKey)) throw new IllegalArgumentException("Duplicate module name: " + module.getName());
        byId.put(idKey, module);
        byName.put(nameKey, module);
        byCategory.get(module.getCategory()).add(module);
        return module;
    }

    public synchronized void registerAll(Module... modules) {
        if (modules == null) return;
        for (Module module : modules) register(module);
    }

    public synchronized Module get(String idOrName) {
        if (idOrName == null) return null;
        String key = normalize(idOrName);
        Module module = byId.get(key);
        return module != null ? module : byName.get(key);
    }

    public synchronized List<Module> all() {
        return Collections.unmodifiableList(new ArrayList<Module>(byId.values()));
    }

    public synchronized List<Module> byCategory(ModuleCategory category) {
        List<Module> modules = byCategory.get(category);
        if (modules == null) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<Module>(modules));
    }

    public synchronized List<Module> enabled() {
        List<Module> result = new ArrayList<Module>();
        for (Module module : byId.values()) if (module.isEnabled()) result.add(module);
        return Collections.unmodifiableList(result);
    }

    public synchronized boolean setEnabled(String idOrName, boolean enabled) {
        Module module = get(idOrName);
        if (module == null) return false;
        module.setEnabled(enabled);
        return true;
    }

    public synchronized boolean toggle(String idOrName) {
        Module module = get(idOrName);
        if (module == null) return false;
        module.toggle();
        return true;
    }

    public synchronized int size() { return byId.size(); }

    public synchronized void disableAll() {
        for (Module module : byId.values()) if (module.isEnabled()) module.setEnabled(false);
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
