package dev.zis30axs.sigma.hotinjection.module;

import dev.zis30axs.sigma.hotinjection.event.Event;
import dev.zis30axs.sigma.hotinjection.event.EventBus;
import dev.zis30axs.sigma.hotinjection.event.EventListener;
import dev.zis30axs.sigma.hotinjection.event.EventPriority;
import dev.zis30axs.sigma.hotinjection.event.EventSubscription;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    private final String id;
    private final String name;
    private final ModuleCategory category;
    private final EventBus eventBus;
    private final List<EventSubscription> subscriptions = new ArrayList<EventSubscription>();
    private boolean enabled;

    protected Module(String id, String name, ModuleCategory category, EventBus eventBus) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Module id is required");
        }
        this.id = id;
        this.name = name == null ? id : name;
        this.category = category == null ? ModuleCategory.MISC : category;
        this.eventBus = eventBus;
    }

    public final String getId() { return id; }
    public final String getName() { return name; }
    public final ModuleCategory getCategory() { return category; }

    public final synchronized boolean isEnabled() { return enabled; }

    public final synchronized void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
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

    protected final <E extends Event> void listen(Class<E> eventType, EventPriority priority,
                                                   EventListener<? super E> listener) {
        subscriptions.add(eventBus.subscribe(eventType, priority, listener));
    }

    protected final <E extends Event> void listen(Class<E> eventType, EventListener<? super E> listener) {
        subscriptions.add(eventBus.subscribe(eventType, listener));
    }

    protected final EventBus getEventBus() { return eventBus; }

    private void clearSubscriptions() {
        for (EventSubscription subscription : subscriptions) {
            subscription.close();
        }
        subscriptions.clear();
    }
}
