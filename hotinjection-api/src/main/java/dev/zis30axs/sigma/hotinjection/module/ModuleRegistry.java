package dev.zis30axs.sigma.hotinjection.module;

import java.util.List;

/** Compatibility facade for code written before ModuleManager was introduced. */
public final class ModuleRegistry {
    private final ModuleManager manager;

    public ModuleRegistry() { this(new ModuleManager()); }

    public ModuleRegistry(ModuleManager manager) {
        if (manager == null) throw new NullPointerException("manager");
        this.manager = manager;
    }

    public <T extends Module> T register(T module) { return manager.register(module); }
    public Module get(String id) { return manager.get(id); }
    public List<Module> all() { return manager.all(); }
    public boolean setEnabled(String id, boolean enabled) { return manager.setEnabled(id, enabled); }
    public void disableAll() { manager.disableAll(); }
    public ModuleManager manager() { return manager; }
}
