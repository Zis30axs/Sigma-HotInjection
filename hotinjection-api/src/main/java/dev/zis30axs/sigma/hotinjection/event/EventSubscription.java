package dev.zis30axs.sigma.hotinjection.event;

import java.util.concurrent.atomic.AtomicBoolean;

public final class EventSubscription implements AutoCloseable {
    private final Runnable unsubscribe;
    private final AtomicBoolean active = new AtomicBoolean(true);

    EventSubscription(Runnable unsubscribe) {
        this.unsubscribe = unsubscribe;
    }

    public boolean isActive() {
        return active.get();
    }

    @Override
    public void close() {
        if (active.compareAndSet(true, false)) {
            unsubscribe.run();
        }
    }
}
