package dev.zis30axs.sigma.hotinjection.agent.client;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Bounded reflective walk over the object graph reachable from the client. */
public final class ObjectGraph {
    public static final int DEFAULT_FIELD_LIMIT = 256;

    private ObjectGraph() {
    }

    /** Non-null field values of {@code owner} whose declared type belongs to the game. */
    public static List<Object> children(Object owner, int limit) {
        List<Object> values = new ArrayList<Object>();
        if (owner == null) {
            return values;
        }
        for (Class<?> current = owner.getClass();
             current != null && current != Object.class;
             current = current.getSuperclass()) {
            if (!GameAccess.isGameClass(current)) {
                continue;
            }
            for (Field field : GameAccess.declaredFields(current)) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Class<?> type = field.getType();
                if (type.isPrimitive() || type.isArray() || !GameAccess.isGameClass(type)) {
                    continue;
                }
                Object value = read(field, owner);
                if (value != null && GameAccess.isGameClass(value.getClass())) {
                    values.add(value);
                }
                if (values.size() >= limit) {
                    return values;
                }
            }
        }
        return values;
    }

    /** Breadth-first walk, {@code root} excluded from the result. */
    public static List<Node> walk(Object root, int maxDepth, int maxNodes) {
        List<Node> nodes = new ArrayList<Node>();
        if (root == null) {
            return nodes;
        }
        Map<Object, Boolean> seen = new IdentityHashMap<Object, Boolean>();
        seen.put(root, Boolean.TRUE);

        List<Object> level = new ArrayList<Object>();
        level.add(root);
        for (int depth = 1; depth <= maxDepth && !level.isEmpty() && nodes.size() < maxNodes; depth++) {
            List<Object> next = new ArrayList<Object>();
            for (Object owner : level) {
                for (Object child : children(owner, DEFAULT_FIELD_LIMIT)) {
                    if (seen.put(child, Boolean.TRUE) != null) {
                        continue;
                    }
                    nodes.add(new Node(child, depth));
                    next.add(child);
                    if (nodes.size() >= maxNodes) {
                        break;
                    }
                }
                if (nodes.size() >= maxNodes) {
                    break;
                }
            }
            level = next;
        }
        return nodes;
    }

    public static Object read(Field field, Object owner) {
        try {
            field.setAccessible(true);
            return field.get(owner);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static final class Node {
        private final Object value;
        private final int depth;

        Node(Object value, int depth) {
            this.value = value;
            this.depth = depth;
        }

        public Object getValue() { return value; }
        public int getDepth() { return depth; }
    }
}
