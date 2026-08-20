package dev.zis30axs.sigma.hotinjection.agent.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mapping independent view of the local player: what the crosshair points at,
 * whether a block is being mined, and where the camera is.
 *
 * <p>Names are tried first, structure second. The crosshair kind is recognised
 * by the {@code MISS/BLOCK/ENTITY} enum that every Minecraft version has used
 * for hit results, either as a field (1.7 - 1.12) or behind a getter (1.13+).</p>
 */
public final class PlayerView {
    /** What the crosshair currently points at. */
    public enum Crosshair {
        NONE, BLOCK, ENTITY, UNKNOWN
    }

    private static final String[] HIT_RESULT_FIELDS = {
            "objectMouseOver", "hitResult", "crosshairTarget",
            "field_71476_x", "field_3944", "f_91077_"
    };
    private static final String[] HIT_TYPE_METHODS = {
            "getType", "getTypeOfHit", "method_17783", "func_216346_c", "m_6662_"
    };
    private static final String[] CONTROLLER_FIELDS = {
            "playerController", "interactionManager", "gameMode",
            "field_71442_b", "field_1761", "f_91072_"
    };
    private static final String[] HITTING_FIELDS = {
            "isHittingBlock", "isDestroying", "breakingBlock",
            "field_78778_j", "field_3716", "f_105203_"
    };
    private static final String[] EYE_HEIGHT_METHODS = {
            "getEyeHeight", "getStandingEyeHeight", "method_5751", "func_70047_e", "m_20192_"
    };
    private static final String[] YAW_FIELDS = {
            "rotationYaw", "yaw", "yRot", "field_70177_z", "field_6031", "f_19857_"
    };
    private static final String[] PITCH_FIELDS = {
            "rotationPitch", "pitch", "xRot", "field_70125_A", "field_5965", "f_19858_"
    };
    private static final String[] YAW_METHODS = {
            "getYRot", "getRotationYaw", "method_36454", "m_146908_"
    };
    private static final String[] PITCH_METHODS = {
            "getXRot", "getRotationPitch", "method_36455", "m_146909_"
    };
    private static final float DEFAULT_EYE_HEIGHT = 1.62f;

    private static final Map<Class<?>, Method> HIT_TYPE_METHOD_CACHE = new HashMap<Class<?>, Method>();
    private static final Map<Class<?>, Field> HIT_TYPE_FIELD_CACHE = new HashMap<Class<?>, Field>();
    private static volatile Field hitResultField;
    private static volatile Field controllerField;
    private static volatile Field hittingField;
    private static volatile Field yawField;
    private static volatile Field pitchField;
    private static volatile Method yawMethod;
    private static volatile Method pitchMethod;
    private static volatile long lastHitDiscovery;

    private PlayerView() {
    }

    /** @return what the crosshair points at, {@code UNKNOWN} when unreadable. */
    public static Crosshair crosshair() {
        Object mc = GameAccess.client();
        if (mc == null) return Crosshair.UNKNOWN;
        Object hit = hitResult(mc);
        if (hit != null) return kind(hit);
        // A resolved accessor that currently reads null means "nothing targeted";
        // an unresolved one means this runtime cannot be read at all.
        return hitResultField == null ? Crosshair.UNKNOWN : Crosshair.NONE;
    }

    /**
     * @return {@code TRUE} while the player controller is breaking a block,
     *         {@code FALSE} when it is not, and {@code null} when the state
     *         cannot be read in this runtime.
     */
    public static Boolean breakingBlock() {
        Object mc = GameAccess.client();
        if (mc == null) return null;
        Field controllerAccess = controllerField;
        if (controllerAccess == null) {
            controllerAccess = GameAccess.findField(mc.getClass(), CONTROLLER_FIELDS);
            if (controllerAccess == null) return null;
            controllerField = controllerAccess;
        }
        Object controller = ObjectGraph.read(controllerAccess, mc);
        if (controller == null) return null;
        Field hitting = hittingField;
        if (hitting == null || !hitting.getDeclaringClass().isAssignableFrom(controller.getClass())) {
            hitting = GameAccess.findField(controller.getClass(), HITTING_FIELDS);
            if (hitting == null || hitting.getType() != boolean.class) return null;
            hittingField = hitting;
        }
        try {
            return Boolean.valueOf(hitting.getBoolean(controller));
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** @return the eye position and view angles of the local player, or {@code null}. */
    public static Camera camera() {
        Object player = GameAccess.player();
        if (player == null) return null;
        double[] box = EntityView.boundingBox(player);
        if (box == null) return null;
        Float yaw = rotation(player, true);
        Float pitch = rotation(player, false);
        if (yaw == null || pitch == null) return null;
        return new Camera(
                (box[0] + box[3]) * 0.5D,
                box[1] + eyeHeight(player),
                (box[2] + box[5]) * 0.5D,
                yaw.floatValue(),
                pitch.floatValue());
    }

    private static Object hitResult(Object mc) {
        Field field = hitResultField;
        if (field != null) return ObjectGraph.read(field, mc);
        Field named = GameAccess.findField(mc.getClass(), HIT_RESULT_FIELDS);
        if (named != null) {
            Object value = ObjectGraph.read(named, mc);
            if (value != null && kind(value) != Crosshair.UNKNOWN) {
                hitResultField = named;
                return value;
            }
        }
        return discoverHitResult(mc);
    }

    /** Obfuscated fallback: the client member that exposes a MISS/BLOCK/ENTITY enum. */
    private static Object discoverHitResult(Object mc) {
        long now = System.currentTimeMillis();
        if (now - lastHitDiscovery < 2000L) return null;
        lastHitDiscovery = now;
        for (Class<?> current = mc.getClass();
             current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Field candidate : GameAccess.declaredFields(current)) {
                Class<?> type = candidate.getType();
                if (Modifier.isStatic(candidate.getModifiers())
                        || type.isPrimitive() || type.isArray()
                        || !GameAccess.isGameClass(type)) {
                    continue;
                }
                candidate.setAccessible(true);
                Object value = ObjectGraph.read(candidate, mc);
                if (value != null && kind(value) != Crosshair.UNKNOWN) {
                    hitResultField = candidate;
                    return value;
                }
            }
        }
        return null;
    }

    private static Crosshair kind(Object hit) {
        Class<?> type = hit.getClass();
        Method cachedMethod;
        Field cachedField;
        synchronized (HIT_TYPE_METHOD_CACHE) {
            cachedMethod = HIT_TYPE_METHOD_CACHE.get(type);
            cachedField = HIT_TYPE_FIELD_CACHE.get(type);
        }
        if (cachedMethod != null) {
            Crosshair resolved = fromEnum(invokeQuietly(cachedMethod, hit));
            if (resolved != null) return resolved;
        }
        if (cachedField != null) {
            Crosshair resolved = fromEnum(ObjectGraph.read(cachedField, hit));
            if (resolved != null) return resolved;
        }

        Method named = GameAccess.findMethod(type, HIT_TYPE_METHODS);
        if (named != null) {
            Crosshair resolved = fromEnum(invokeQuietly(named, hit));
            if (resolved != null) return remember(type, named, null, resolved);
        }
        for (Method candidate : GameAccess.publicMethods(type)) {
            if (candidate.getParameterTypes().length != 0
                    || Modifier.isStatic(candidate.getModifiers())
                    || !describesHit(candidate.getReturnType())) {
                continue;
            }
            Crosshair resolved = fromEnum(invokeQuietly(candidate, hit));
            if (resolved != null) return remember(type, candidate, null, resolved);
        }
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field candidate : GameAccess.declaredFields(current)) {
                if (Modifier.isStatic(candidate.getModifiers()) || !describesHit(candidate.getType())) {
                    continue;
                }
                candidate.setAccessible(true);
                Crosshair resolved = fromEnum(ObjectGraph.read(candidate, hit));
                if (resolved != null) return remember(type, null, candidate, resolved);
            }
        }
        return Crosshair.UNKNOWN;
    }

    private static Crosshair remember(Class<?> type, Method method, Field field, Crosshair resolved) {
        synchronized (HIT_TYPE_METHOD_CACHE) {
            if (method != null) HIT_TYPE_METHOD_CACHE.put(type, method);
            if (field != null) HIT_TYPE_FIELD_CACHE.put(type, field);
        }
        return resolved;
    }

    /** @return true for an enum that declares both {@code BLOCK} and {@code ENTITY}. */
    private static boolean describesHit(Class<?> type) {
        if (!type.isEnum()) return false;
        Object[] constants = type.getEnumConstants();
        if (constants == null) return false;
        boolean block = false;
        boolean entity = false;
        for (Object constant : constants) {
            String name = ((Enum<?>) constant).name();
            if ("BLOCK".equalsIgnoreCase(name)) block = true;
            else if ("ENTITY".equalsIgnoreCase(name)) entity = true;
        }
        return block && entity;
    }

    private static Crosshair fromEnum(Object value) {
        if (!(value instanceof Enum)) return null;
        String name = ((Enum<?>) value).name();
        if ("ENTITY".equalsIgnoreCase(name)) return Crosshair.ENTITY;
        if ("BLOCK".equalsIgnoreCase(name)) return Crosshair.BLOCK;
        if ("MISS".equalsIgnoreCase(name)) return Crosshair.NONE;
        return null;
    }

    private static float eyeHeight(Object player) {
        Method method = GameAccess.findMethod(player.getClass(), EYE_HEIGHT_METHODS);
        Object value = method == null ? null : invokeQuietly(method, player);
        if (value instanceof Number) {
            float height = ((Number) value).floatValue();
            if (height > 0.1f && height < 4.0f) return height;
        }
        return DEFAULT_EYE_HEIGHT;
    }

    private static Float rotation(Object player, boolean yaw) {
        Field cached = yaw ? yawField : pitchField;
        if (cached != null) {
            Float value = readFloat(cached, player);
            if (value != null) return value;
        }
        Method cachedMethod = yaw ? yawMethod : pitchMethod;
        if (cachedMethod != null) {
            Object value = invokeQuietly(cachedMethod, player);
            if (value instanceof Number) return Float.valueOf(((Number) value).floatValue());
        }

        Field named = GameAccess.findField(player.getClass(), yaw ? YAW_FIELDS : PITCH_FIELDS);
        if (named != null && named.getType() == float.class) {
            Float value = readFloat(named, player);
            if (value != null) {
                if (yaw) yawField = named; else pitchField = named;
                return value;
            }
        }
        Method namedMethod = GameAccess.findMethod(player.getClass(), yaw ? YAW_METHODS : PITCH_METHODS);
        if (namedMethod != null) {
            Object value = invokeQuietly(namedMethod, player);
            if (value instanceof Number) {
                if (yaw) yawMethod = namedMethod; else pitchMethod = namedMethod;
                return Float.valueOf(((Number) value).floatValue());
            }
        }
        return discoverRotation(player, yaw);
    }

    /**
     * Obfuscated fallback: an entity declares view angles as four consecutive
     * floats, {@code yaw, pitch, previousYaw, previousPitch}. A pitch is always
     * inside +/-90 degrees and both pairs stay close between ticks, which is
     * enough to tell the quadruple apart from sizes or speeds.
     */
    private static Float discoverRotation(Object player, boolean wantYaw) {
        for (Class<?> current = player.getClass();
             current != null && current != Object.class;
             current = current.getSuperclass()) {
            if (!GameAccess.isGameClass(current)) continue;
            List<Field> floats = new ArrayList<Field>();
            for (Field field : GameAccess.declaredFields(current)) {
                if (!Modifier.isStatic(field.getModifiers()) && field.getType() == float.class) {
                    field.setAccessible(true);
                    floats.add(field);
                }
            }
            for (int index = 0; index + 3 < floats.size(); index++) {
                Float yaw = readFloat(floats.get(index), player);
                Float pitch = readFloat(floats.get(index + 1), player);
                Float previousYaw = readFloat(floats.get(index + 2), player);
                Float previousPitch = readFloat(floats.get(index + 3), player);
                if (yaw == null || pitch == null || previousYaw == null || previousPitch == null) continue;
                if (Math.abs(pitch.floatValue()) > 90.5f) continue;
                if (Math.abs(previousPitch.floatValue()) > 90.5f) continue;
                if (Math.abs(wrapDegrees(yaw.floatValue() - previousYaw.floatValue())) > 90.0f) continue;
                if (Math.abs(pitch.floatValue() - previousPitch.floatValue()) > 90.0f) continue;
                yawField = floats.get(index);
                pitchField = floats.get(index + 1);
                return wantYaw ? yaw : pitch;
            }
        }
        return null;
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0f;
        if (wrapped >= 180.0f) wrapped -= 360.0f;
        if (wrapped < -180.0f) wrapped += 360.0f;
        return wrapped;
    }

    private static Float readFloat(Field field, Object owner) {
        try {
            field.setAccessible(true);
            return Float.valueOf(field.getFloat(owner));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invokeQuietly(Method method, Object owner) {
        try {
            method.setAccessible(true);
            return method.invoke(owner);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Eye position and view angles, in Minecraft world space and degrees. */
    public static final class Camera {
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;

        Camera(double x, double y, double z, float yaw, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public float getYaw() { return yaw; }
        public float getPitch() { return pitch; }
    }
}
