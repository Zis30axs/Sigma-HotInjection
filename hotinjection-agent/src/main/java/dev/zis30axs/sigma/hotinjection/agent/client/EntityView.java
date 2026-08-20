package dev.zis30axs.sigma.hotinjection.agent.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mapping independent view of the entities around the local player.
 *
 * <p>Positions are taken from the entity bounding box instead of position
 * fields: an axis aligned box is the one structure that can still be recognised
 * in a fully obfuscated runtime, because it is the only game class that carries
 * exactly six instance doubles in {@code minX, minY, minZ, maxX, maxY, maxZ}
 * order. Everything else is resolved by mapping name first and structure
 * second, and degrades to "no entities" rather than throwing.</p>
 */
public final class EntityView {
    private static final String[] ENTITY_LIST_METHODS = {
            "getAllEntities", "entitiesForRendering", "getEntities", "getEntityList",
            "method_18112", "func_217416_b", "m_104735_"
    };
    private static final String[] ENTITY_LIST_FIELDS = {
            "loadedEntityList", "entityList", "field_72996_f", "field_2823"
    };
    private static final String[] BOX_METHODS = {
            "getBoundingBox", "getEntityBoundingBox", "getBoundingBox",
            "method_5829", "func_174813_aQ", "m_142469_"
    };
    private static final String[] BOX_FIELDS = {
            "boundingBox", "field_70121_D", "field_70046_E"
    };
    private static final String[] BOX_DOUBLE_FIELDS = {
            "minX", "minY", "minZ", "maxX", "maxY", "maxZ"
    };
    private static final String[] BOX_DOUBLE_FIELDS_SRG = {
            "field_72340_a", "field_72338_b", "field_72339_c",
            "field_72336_d", "field_72337_e", "field_72334_f"
    };

    private static final Map<Class<?>, Boolean> PLAYER_CACHE = new HashMap<Class<?>, Boolean>();
    private static final Map<Class<?>, Field> PROFILE_CACHE = new HashMap<Class<?>, Field>();
    private static volatile Method listMethod;
    private static volatile Field listField;
    private static volatile Method boxMethod;
    private static volatile Field boxField;
    private static volatile Field[] boxDoubles;
    private static volatile long lastListDiscovery;

    private EntityView() {
    }

    /**
     * @param limit hard cap on returned entities, protecting the overlay poll
     *              from very crowded worlds.
     * @return every visible entity except the local player, never {@code null}.
     */
    public static List<Target> targets(int limit) {
        Object world = GameAccess.world();
        Object self = GameAccess.player();
        if (world == null || self == null) {
            return Collections.emptyList();
        }
        Class<?> base = GameAccess.highestGameClass(self.getClass());
        List<Object> entities = snapshot(world, base);
        List<Target> targets = new ArrayList<Target>();
        for (Object entity : entities) {
            if (entity == null || entity == self) continue;
            if (base != null && !base.isInstance(entity)) continue;
            double[] box = boundingBox(entity);
            if (box == null) continue;
            boolean player = isPlayer(entity);
            targets.add(new Target(box, player, player ? profileName(entity) : ""));
            if (targets.size() >= limit) break;
        }
        return targets;
    }

    /** @return {@code minX, minY, minZ, maxX, maxY, maxZ}, or {@code null}. */
    public static double[] boundingBox(Object entity) {
        if (entity == null) return null;
        Object box = readBox(entity);
        if (box == null) return null;
        Field[] fields = boxFields(box.getClass());
        if (fields == null) return null;
        try {
            double[] values = new double[6];
            for (int index = 0; index < 6; index++) {
                values[index] = fields[index].getDouble(box);
            }
            for (int axis = 0; axis < 3; axis++) {
                if (values[axis] > values[axis + 3]) {
                    double swap = values[axis];
                    values[axis] = values[axis + 3];
                    values[axis + 3] = swap;
                }
            }
            return values;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * A player is recognised by its authlib {@code GameProfile}. That type is a
     * library class, so the check survives obfuscation; the class name test only
     * helps deobfuscated MCP/Yarn runtimes.
     */
    public static boolean isPlayer(Object entity) {
        if (entity == null) return false;
        Class<?> type = entity.getClass();
        synchronized (PLAYER_CACHE) {
            Boolean cached = PLAYER_CACHE.get(type);
            if (cached != null) return cached.booleanValue();
        }
        boolean player = type.getName().contains("Player");
        if (!player) {
            Class<?> profile = GameAccess.findGameClass("com.mojang.authlib.GameProfile");
            player = profile != null && GameAccess.declaresFieldOfType(type, profile);
        }
        synchronized (PLAYER_CACHE) {
            PLAYER_CACHE.put(type, Boolean.valueOf(player));
        }
        return player;
    }

    /** @return the authlib profile name of a player entity, or an empty string. */
    public static String profileName(Object entity) {
        if (entity == null) return "";
        Field field = profileField(entity.getClass());
        if (field == null) return "";
        Object profile = ObjectGraph.read(field, entity);
        if (profile == null) return "";
        try {
            Object name = profile.getClass().getMethod("getName").invoke(profile);
            return name instanceof String ? (String) name : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static Field profileField(Class<?> type) {
        synchronized (PROFILE_CACHE) {
            if (PROFILE_CACHE.containsKey(type)) return PROFILE_CACHE.get(type);
        }
        Class<?> profileType = GameAccess.findGameClass("com.mojang.authlib.GameProfile");
        Field found = null;
        for (Class<?> current = type;
             profileType != null && current != null && current != Object.class && found == null;
             current = current.getSuperclass()) {
            for (Field candidate : GameAccess.declaredFields(current)) {
                if (!Modifier.isStatic(candidate.getModifiers())
                        && profileType.isAssignableFrom(candidate.getType())) {
                    candidate.setAccessible(true);
                    found = candidate;
                    break;
                }
            }
        }
        synchronized (PROFILE_CACHE) {
            PROFILE_CACHE.put(type, found);
        }
        return found;
    }

    private static Object readBox(Object entity) {
        Method method = boxMethod;
        if (method == null && boxField == null) {
            method = GameAccess.findMethod(entity.getClass(), BOX_METHODS);
            if (method != null) boxMethod = method;
            else boxField = GameAccess.findField(entity.getClass(), BOX_FIELDS);
        }
        method = boxMethod;
        if (method != null) {
            try {
                return method.invoke(entity);
            } catch (Throwable ignored) {
                return null;
            }
        }
        Field field = boxField;
        return field == null ? null : ObjectGraph.read(field, entity);
    }

    /**
     * Six box doubles by mapping name, or by declaration order when the class is
     * obfuscated. Minecraft has always declared them as min x/y/z then max
     * x/y/z, and no other game class carries exactly six instance doubles.
     */
    private static Field[] boxFields(Class<?> type) {
        Field[] cached = boxDoubles;
        if (cached != null) return cached;
        Field[] named = namedBoxFields(type, BOX_DOUBLE_FIELDS);
        if (named == null) named = namedBoxFields(type, BOX_DOUBLE_FIELDS_SRG);
        if (named == null) {
            List<Field> doubles = new ArrayList<Field>();
            for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
                for (Field field : GameAccess.declaredFields(current)) {
                    if (!Modifier.isStatic(field.getModifiers()) && field.getType() == double.class) {
                        field.setAccessible(true);
                        doubles.add(field);
                    }
                }
            }
            if (doubles.size() == 6) named = doubles.toArray(new Field[0]);
        }
        boxDoubles = named;
        return named;
    }

    private static Field[] namedBoxFields(Class<?> type, String[] names) {
        Field[] fields = new Field[6];
        for (int index = 0; index < 6; index++) {
            fields[index] = GameAccess.findField(type, names[index]);
            if (fields[index] == null || fields[index].getType() != double.class) return null;
        }
        return fields;
    }

    /** Defensive copy: the client thread owns the entity list and may mutate it mid read. */
    private static List<Object> snapshot(Object world, Class<?> base) {
        Object entities = entityCollection(world, base);
        if (entities == null) return Collections.emptyList();
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                List<Object> copy = new ArrayList<Object>();
                if (entities instanceof Collection) {
                    copy.addAll((Collection<?>) entities);
                } else if (entities instanceof Map) {
                    copy.addAll(((Map<?, ?>) entities).values());
                } else if (entities instanceof Iterable) {
                    for (Object entity : (Iterable<?>) entities) copy.add(entity);
                }
                return copy;
            } catch (Throwable ignored) {
                // Concurrent modification; try again on the next attempt.
            }
        }
        return Collections.emptyList();
    }

    private static Object entityCollection(Object world, Class<?> base) {
        Method method = listMethod;
        if (method != null) return invokeQuietly(method, world);
        Field field = listField;
        if (field != null) return ObjectGraph.read(field, world);

        Method named = GameAccess.findMethod(world.getClass(), ENTITY_LIST_METHODS);
        if (named != null) {
            Object value = invokeQuietly(named, world);
            if (matches(value, base) >= 0) {
                listMethod = named;
                return value;
            }
        }
        Field namedField = GameAccess.findField(world.getClass(), ENTITY_LIST_FIELDS);
        if (namedField != null) {
            Object value = ObjectGraph.read(namedField, world);
            if (matches(value, base) >= 0) {
                listField = namedField;
                return value;
            }
        }
        return discoverEntityCollection(world, base);
    }

    /**
     * Obfuscated fallback: the entity list is the iterable member of the world
     * that holds the most objects sharing the local player's base class.
     */
    private static Object discoverEntityCollection(Object world, Class<?> base) {
        long now = System.currentTimeMillis();
        if (now - lastListDiscovery < 2000L) return null;
        lastListDiscovery = now;
        Object best = null;
        int bestScore = 0;
        for (Method candidate : GameAccess.publicMethods(world.getClass())) {
            if (candidate.getParameterTypes().length != 0
                    || Modifier.isStatic(candidate.getModifiers())
                    || !isIterableType(candidate.getReturnType())) {
                continue;
            }
            Object value = invokeQuietly(candidate, world);
            int score = matches(value, base);
            if (score > bestScore) {
                bestScore = score;
                best = value;
                listMethod = candidate;
                listField = null;
            }
        }
        for (Class<?> current = world.getClass();
             current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Field candidate : GameAccess.declaredFields(current)) {
                if (Modifier.isStatic(candidate.getModifiers()) || !isIterableType(candidate.getType())) {
                    continue;
                }
                candidate.setAccessible(true);
                Object value = ObjectGraph.read(candidate, world);
                int score = matches(value, base);
                if (score > bestScore) {
                    bestScore = score;
                    best = value;
                    listField = candidate;
                    listMethod = null;
                }
            }
        }
        return best;
    }

    private static boolean isIterableType(Class<?> type) {
        return Iterable.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type);
    }

    /**
     * @return the number of sampled elements that share the entity base class,
     *         {@code 0} for an empty but plausible container and {@code -1} when
     *         the value clearly holds something else.
     */
    private static int matches(Object value, Class<?> base) {
        List<Object> sample = new ArrayList<Object>();
        try {
            Iterable<?> iterable;
            if (value instanceof Map) iterable = ((Map<?, ?>) value).values();
            else if (value instanceof Iterable) iterable = (Iterable<?>) value;
            else return -1;
            for (Object element : iterable) {
                sample.add(element);
                if (sample.size() >= 32) break;
            }
        } catch (Throwable ignored) {
            return -1;
        }
        if (base == null) return sample.size();
        int found = 0;
        int nonNull = 0;
        for (Object element : sample) {
            if (element == null) continue;
            nonNull++;
            if (base.isInstance(element)) found++;
        }
        return nonNull > 0 && found == 0 ? -1 : found;
    }

    private static Object invokeQuietly(Method method, Object owner) {
        try {
            method.setAccessible(true);
            return method.invoke(owner);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** One entity reduced to its world-space box plus the little we can name. */
    public static final class Target {
        private final double[] box;
        private final boolean player;
        private final String name;

        Target(double[] box, boolean player, String name) {
            this.box = box;
            this.player = player;
            this.name = name == null ? "" : name;
        }

        public double getMinX() { return box[0]; }
        public double getMinY() { return box[1]; }
        public double getMinZ() { return box[2]; }
        public double getMaxX() { return box[3]; }
        public double getMaxY() { return box[4]; }
        public double getMaxZ() { return box[5]; }
        public double getCenterX() { return (box[0] + box[3]) * 0.5D; }
        public double getCenterY() { return (box[1] + box[4]) * 0.5D; }
        public double getCenterZ() { return (box[2] + box[5]) * 0.5D; }
        public boolean isPlayer() { return player; }
        public String getName() { return name; }
    }
}
