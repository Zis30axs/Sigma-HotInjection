package dev.zis30axs.sigma.hotinjection.module;

import dev.zis30axs.sigma.hotinjection.event.Event;
import dev.zis30axs.sigma.hotinjection.event.EventBus;
import dev.zis30axs.sigma.hotinjection.event.EventListener;
import dev.zis30axs.sigma.hotinjection.event.EventPriority;
import dev.zis30axs.sigma.hotinjection.event.EventSubscription;
import dev.zis30axs.sigma.hotinjection.module.setting.ModuleSetting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
    private final String id;
    private final String name;
    private final ModuleCategory category;
    private final String description;
    private final EventBus eventBus;
    private final List<EventSubscription> subscriptions = new ArrayList<EventSubscription>();
    private final List<ModuleSetting<?>> settings = new ArrayList<ModuleSetting<?>>();
    private boolean enabled;

    protected Module(String id, String name, ModuleCategory category, EventBus eventBus) {
        this(id, name, category, "", eventBus);
    }

    protected Module(String id, String name, ModuleCategory category, String description, EventBus eventBus) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Module id is required");
        }
        this.id = id;
        this.name = name == null ? id : name;
        this.category = category == null ? ModuleCategory.MISC : category;
        this.description = description == null ? "" : description;
        this.eventBus = eventBus;
    }

    public final String getId() { return id; }
    public final String getName() { return name; }
    public final ModuleCategory getCategory() { return category; }
    public final String getDescription() { return description; }
    public final synchronized boolean isEnabled() { return enabled; }
    public final void toggle() { setEnabled(!isEnabled()); }

    public final synchronized void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        if (enabled) {
            this.enabled = true;
            try {
                onEnable();
            } catch (RuntimeException error) {
                this.enabled = false;
                clearSubscriptions();
                throw error;
            }
        } else {
            try {
                onDisable();
            } finally {
                this.enabled = false;
                clearSubscriptions();
            }
        }
    }

    protected void onEnable() { }
    protected void onDisable() { }

    protected final <T extends ModuleSetting<?>> T setting(T setting) {
        if (setting == null) throw new NullPointerException("setting");
        synchronized (settings) {
            for (ModuleSetting<?> existing : settings) {
                if (existing.getId().equals(setting.getId())) {
                    throw new IllegalArgumentException("Duplicate setting id: " + setting.getId());
                }
            }
            settings.add(setting);
        }
        return setting;
    }

    public final List<ModuleSetting<?>> getSettings() {
        synchronized (settings) {
            return Collections.unmodifiableList(new ArrayList<ModuleSetting<?>>(settings));
        }
    }

    public final ModuleSetting<?> getSetting(String id) {
        if (id == null) return null;
        synchronized (settings) {
            for (ModuleSetting<?> setting : settings) {
                if (setting.getId().equalsIgnoreCase(id)) return setting;
            }
        }
        return null;
    }

    protected final <E extends Event> void listen(Class<E> eventType, EventPriority priority,
                                                   EventListener<? super E> listener) {
        subscriptions.add(eventBus.subscribe(eventType, priority, listener));
    }

    protected final <E extends Event> void listen(Class<E> eventType, EventListener<? super E> listener) {
        subscriptions.add(eventBus.subscribe(eventType, listener));
    }

    protected final EventBus getEventBus() { return eventBus; }

    private void clearSubscriptions() {
        for (EventSubscription subscription : subscriptions) subscription.close();
        subscriptions.clear();
    }
}
