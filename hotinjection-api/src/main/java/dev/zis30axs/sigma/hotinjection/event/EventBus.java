package dev.zis30axs.sigma.hotinjection.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class EventBus {
    private final Map<Class<?>, CopyOnWriteArrayList<RegisteredListener<?>>> listeners =
            new ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<RegisteredListener<?>> >();
    private final AtomicLong sequence = new AtomicLong();

    public <E extends Event> EventSubscription subscribe(
            final Class<E> eventType,
            EventPriority priority,
            EventListener<? super E> listener) {
        if (eventType == null || priority == null || listener == null) {
            throw new NullPointerException("eventType, priority and listener are required");
        }

        CopyOnWriteArrayList<RegisteredListener<?>> bucket = listeners.get(eventType);
        if (bucket == null) {
            CopyOnWriteArrayList<RegisteredListener<?>> created = new CopyOnWriteArrayList<RegisteredListener<?>>();
            CopyOnWriteArrayList<RegisteredListener<?>> existing = listeners.putIfAbsent(eventType, created);
            bucket = existing == null ? created : existing;
        }

        final RegisteredListener<E> registered = new RegisteredListener<E>(
                eventType, priority, listener, sequence.incrementAndGet());
        final CopyOnWriteArrayList<RegisteredListener<?>> finalBucket = bucket;
        finalBucket.add(registered);

        return new EventSubscription(new Runnable() {
            @Override
            public void run() {
                finalBucket.remove(registered);
            }
        });
    }

    public <E extends Event> EventSubscription subscribe(Class<E> eventType, EventListener<? super E> listener) {
        return subscribe(eventType, EventPriority.NORMAL, listener);
    }

    public <E extends Event> E post(E event) {
        if (event == null) {
            throw new NullPointerException("event");
        }

        List<RegisteredListener<?>> matching = new ArrayList<RegisteredListener<?>>();
        for (Map.Entry<Class<?>, CopyOnWriteArrayList<RegisteredListener<?>>> entry : listeners.entrySet()) {
            if (entry.getKey().isAssignableFrom(event.getClass())) {
                matching.addAll(entry.getValue());
            }
        }

        Collections.sort(matching, new Comparator<RegisteredListener<?>>() {
            @Override
            public int compare(RegisteredListener<?> a, RegisteredListener<?> b) {
                int priority = Integer.compare(b.priority.getWeight(), a.priority.getWeight());
                return priority != 0 ? priority : Long.compare(a.sequence, b.sequence);
            }
        });

        for (RegisteredListener<?> listener : matching) {
            invoke(listener, event);
        }
        return event;
    }

    public void clear() {
        listeners.clear();
    }

    @SuppressWarnings("unchecked")
    private static <E extends Event> void invoke(RegisteredListener<?> registered, E event) {
        try {
            ((EventListener<E>) registered.listener).onEvent(event);
        } catch (RuntimeException runtime) {
            throw runtime;
        } catch (Exception checked) {
            throw new EventDispatchException("Event listener failed for " + event.getClass().getName(), checked);
        }
    }

    private static final class RegisteredListener<E extends Event> {
        private final Class<E> eventType;
        private final EventPriority priority;
        private final EventListener<? super E> listener;
        private final long sequence;

        private RegisteredListener(Class<E> eventType, EventPriority priority,
                                   EventListener<? super E> listener, long sequence) {
            this.eventType = eventType;
            this.priority = priority;
            this.listener = listener;
            this.sequence = sequence;
        }
    }

    public static final class EventDispatchException extends RuntimeException {
        public EventDispatchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
