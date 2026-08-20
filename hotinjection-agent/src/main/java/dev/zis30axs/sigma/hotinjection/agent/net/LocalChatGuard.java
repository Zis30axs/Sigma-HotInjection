package dev.zis30axs.sigma.hotinjection.agent.net;

/**
 * Arming point for local-only chat.
 *
 * <p>Before HotInjection calls the client's own chat send method it arms this
 * guard. While armed, the registered {@code local-chat} module cancels every
 * outgoing packet that carries text, so the message exists on the client only.
 * If the module is not enabled the guard reports it and the caller must not use
 * the real send path at all - the design fails closed, never towards the
 * network.</p>
 */
public final class LocalChatGuard {
    private static final Object LOCK = new Object();

    private static volatile boolean listenerActive;
    private static String armedText;
    private static boolean dropped;

    private LocalChatGuard() {
    }

    public static void setListenerActive(boolean value) {
        listenerActive = value;
    }

    public static boolean isListenerActive() {
        return listenerActive;
    }

    public static void arm(String text) {
        synchronized (LOCK) {
            armedText = text;
            dropped = false;
        }
    }

    public static void disarm() {
        synchronized (LOCK) {
            armedText = null;
        }
    }

    public static String armedText() {
        synchronized (LOCK) {
            return armedText;
        }
    }

    /** While armed, any packet carrying text is dropped. */
    public static boolean shouldCancel(String packetText) {
        if (packetText == null) {
            return false;
        }
        synchronized (LOCK) {
            return armedText != null;
        }
    }

    public static void markDropped() {
        synchronized (LOCK) {
            dropped = true;
            armedText = null;
            LOCK.notifyAll();
        }
    }

    /** @return true when the armed message was dropped before it reached the wire. */
    public static boolean awaitDropped(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        synchronized (LOCK) {
            while (!dropped) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    armedText = null;
                    return false;
                }
                try {
                    LOCK.wait(remaining);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    armedText = null;
                    return false;
                }
            }
            return true;
        }
    }
}
