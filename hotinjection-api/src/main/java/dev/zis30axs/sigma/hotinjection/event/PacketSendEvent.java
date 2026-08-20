package dev.zis30axs.sigma.hotinjection.event;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Posted for every packet the client is about to write to the server.
 * Cancelling drops the packet before it reaches the encoder, so the server
 * never sees it while the client keeps whatever it did locally.
 */
public final class PacketSendEvent extends CancellableEvent {
    private final Object packet;
    private String text;
    private boolean textResolved;

    public PacketSendEvent(Object packet) {
        this.packet = packet;
    }

    public Object getPacket() { return packet; }

    public String getPacketName() {
        return packet == null ? "null" : packet.getClass().getName();
    }

    /** First string payload carried by the packet, resolved on demand. */
    public String getText() {
        if (!textResolved) {
            textResolved = true;
            text = extractText(packet);
        }
        return text;
    }

    private static String extractText(Object packet) {
        if (packet == null) {
            return null;
        }
        for (Class<?> current = packet.getClass();
             current != null && current != Object.class;
             current = current.getSuperclass()) {
            Field[] fields;
            try {
                fields = current.getDeclaredFields();
            } catch (Throwable ignored) {
                continue;
            }
            for (Field field : fields) {
                if (field.getType() != String.class || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(packet);
                    if (value instanceof String) {
                        return (String) value;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }
}
