package dev.zis30axs.sigma.hotinjection.agent.client;

import dev.zis30axs.sigma.hotinjection.util.LogUtil;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mapping independent access to the Minecraft client running in this JVM.
 *
 * <p>Everything here is reflective on purpose. The agent is compiled without a
 * Minecraft dependency and has to cope with MCP, Yarn, Fabric intermediary,
 * Forge SRG and fully obfuscated runtimes across 1.7.10 up to 26.2. Every
 * lookup may fail; callers are expected to degrade instead of throwing.</p>
 */
public final class GameAccess {
    private static final String[] CLIENT_CLASS_NAMES = {
            "net.minecraft.client.Minecraft",
            "net.minecraft.client.MinecraftClient",
            "net.minecraft.class_310"
    };
    private static final String[] CLIENT_GETTER_NAMES = {
            "getMinecraft", "getInstance", "func_71410_x", "method_1551", "m_91087_"
    };
    private static final String[] WINDOW_CLASS_NAMES = {
            "com.mojang.blaze3d.platform.Window",
            "net.minecraft.client.util.Window",
            "net.minecraft.class_1041"
    };
    private static final String[] WORLD_FIELD_NAMES = {
            "theWorld", "world", "level", "field_71441_e", "field_1687", "f_91073_"
    };
    private static final String[] PLAYER_FIELD_NAMES = {
            "thePlayer", "player", "field_71439_g", "field_1724", "f_91074_"
    };

    private static volatile Instrumentation instrumentation;
    private static volatile Set<ClassLoader> gameLoaders;
    private static boolean clientResolved;
    private static Object client;
    private static Class<?> clientClass;
    private static boolean schedulerResolved;
    private static Method scheduler;
    private static boolean windowResolved;
    private static Long windowHandle;

    private GameAccess() {
    }

    public static void install(Instrumentation value) {
        instrumentation = value;
        gameLoaders = null;
    }

    /** @return the Minecraft client instance, or {@code null} when it cannot be located. */
    public static synchronized Object client() {
        if (clientResolved) {
            return client;
        }
        clientResolved = true;

        Class<?> type = findGameClass(CLIENT_CLASS_NAMES);
        Object instance = type == null ? null : instanceOf(type);
        if (instance == null) {
            Class<?> discovered = discoverClientClass();
            if (discovered != null) {
                Object discoveredInstance = instanceOf(discovered);
                if (discoveredInstance != null) {
                    type = discovered;
                    instance = discoveredInstance;
                }
            }
        }

        clientClass = instance == null ? null : instance.getClass();
        client = instance;
        if (instance == null) {
            LogUtil.warn("Minecraft client instance not found; falling back to local-only UI.");
        } else {
            LogUtil.info("Minecraft client located: " + clientClass.getName());
        }
        return client;
    }

    public static Class<?> clientClass() {
        client();
        return clientClass;
    }

    /**
     * Runs {@code task} on the Minecraft main/render thread.
     *
     * @return true when the task was scheduled.
     */
    public static boolean execute(Runnable task) {
        Object mc = client();
        if (mc == null || task == null) {
            return false;
        }
        if (mc instanceof Executor) {
            try {
                ((Executor) mc).execute(task);
                return true;
            } catch (Throwable ignored) {
            }
        }
        Method scheduling = scheduler();
        if (scheduling != null) {
            try {
                scheduling.invoke(mc, task);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    /** @return true when a world and a local player are present, or when that cannot be determined. */
    public static boolean isInWorld() {
        Object mc = client();
        if (mc == null) {
            return false;
        }
        Field world = findField(mc.getClass(), WORLD_FIELD_NAMES);
        Field player = findField(mc.getClass(), PLAYER_FIELD_NAMES);
        if (world == null && player == null) {
            return true;
        }
        try {
            if (world != null && world.get(mc) == null) {
                return false;
            }
            return player == null || player.get(mc) != null;
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** @return the GLFW window handle for LWJGL 3 runtimes, or {@code null}. */
    public static synchronized Long windowHandle() {
        if (windowResolved) {
            return windowHandle;
        }
        windowResolved = true;
        windowHandle = resolveWindowFromField();
        if (windowHandle == null) {
            windowHandle = resolveWindowFromCurrentContext();
        }
        return windowHandle;
    }

    public static Class<?> findGameClass(String... names) {
        for (String name : names) {
            for (ClassLoader loader : gameLoaders()) {
                try {
                    return Class.forName(name, false, loader);
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    /** Walks the class hierarchy looking for any of {@code names}. */
    public static Field findField(Class<?> type, String... names) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (String name : names) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    /** Walks the class hierarchy looking for a no-argument method with any of {@code names}. */
    public static Method findMethod(Class<?> type, String... names) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (String name : names) {
                try {
                    Method method = current.getDeclaredMethod(name);
                    method.setAccessible(true);
                    return method;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    public static Field[] declaredFields(Class<?> type) {
        try {
            return type.getDeclaredFields();
        } catch (Throwable ignored) {
            return new Field[0];
        }
    }

    public static Method[] publicMethods(Class<?> type) {
        try {
            return type.getMethods();
        } catch (Throwable ignored) {
            return new Method[0];
        }
    }

    /** @return the local player instance, or {@code null} when it cannot be resolved. */
    public static Object player() {
        Object mc = client();
        if (mc == null) {
            return null;
        }
        Field field = findField(mc.getClass(), PLAYER_FIELD_NAMES);
        return field == null ? null : ObjectGraph.read(field, mc);
    }

    /** @return true when {@code type} declares a non-static field assignable to {@code fieldType}. */
    public static boolean declaresFieldOfType(Class<?> type, Class<?> fieldType) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : declaredFields(current)) {
                if (!Modifier.isStatic(field.getModifiers()) && fieldType.isAssignableFrom(field.getType())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** All loaded classes that belong to the game class loaders. */
    public static List<Class<?>> loadedGameClasses() {
        List<Class<?>> result = new ArrayList<Class<?>>();
        Instrumentation inst = instrumentation;
        if (inst == null) {
            return result;
        }
        Set<ClassLoader> loaders = gameLoaders();
        for (Class<?> type : loadedClasses(inst)) {
            ClassLoader loader = type.getClassLoader();
            if (loader != null && loaders.contains(loader) && !isIgnoredPackage(type.getName())) {
                result.add(type);
            }
        }
        return result;
    }

    public static boolean isGameClass(Class<?> type) {
        if (type == null || type.isPrimitive() || type.isArray()) {
            return false;
        }
        ClassLoader loader = type.getClassLoader();
        return loader != null && gameLoaders().contains(loader);
    }

    static Set<ClassLoader> gameLoaders() {
        Set<ClassLoader> cached = gameLoaders;
        if (cached != null) {
            return cached;
        }
        Set<ClassLoader> found = new LinkedHashSet<ClassLoader>();
        Instrumentation inst = instrumentation;
        if (inst != null) {
            for (Class<?> type : loadedClasses(inst)) {
                String name = type.getName();
                if (name.startsWith("net.minecraft.") || name.startsWith("com.mojang.blaze3d.")) {
                    ClassLoader loader = type.getClassLoader();
                    if (loader != null) {
                        found.add(loader);
                    }
                }
            }
        }
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) {
            found.add(context);
        }
        ClassLoader system = ClassLoader.getSystemClassLoader();
        if (system != null) {
            found.add(system);
        }
        gameLoaders = found;
        return found;
    }

    private static Class<?>[] loadedClasses(Instrumentation inst) {
        try {
            return inst.getAllLoadedClasses();
        } catch (Throwable ignored) {
            return new Class<?>[0];
        }
    }

    private static synchronized Method scheduler() {
        if (schedulerResolved) {
            return scheduler;
        }
        schedulerResolved = true;
        Class<?> type = clientClass();
        if (type != null) {
            for (Method method : publicMethods(type)) {
                if (Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length == 1 && parameters[0] == Runnable.class) {
                    method.setAccessible(true);
                    scheduler = method;
                    break;
                }
            }
        }
        return scheduler;
    }

    private static Object instanceOf(Class<?> type) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : declaredFields(current)) {
                if (!Modifier.isStatic(field.getModifiers()) || !type.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(null);
                    if (value != null) {
                        return value;
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        Method getter = findMethod(type, CLIENT_GETTER_NAMES);
        if (getter != null && Modifier.isStatic(getter.getModifiers())) {
            try {
                Object value = getter.invoke(null);
                if (value != null) {
                    return value;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /**
     * Structural fallback for obfuscated runtimes: the Minecraft class is the
     * large class that keeps a live singleton of itself and can schedule
     * {@link Runnable}s onto its own thread.
     */
    private static Class<?> discoverClientClass() {
        Instrumentation inst = instrumentation;
        if (inst == null) {
            return null;
        }
        Set<ClassLoader> loaders = gameLoaders();
        Class<?> best = null;
        int bestScore = -1;
        for (Class<?> type : loadedClasses(inst)) {
            if (type.isInterface() || type.isEnum() || type.isArray() || type.isPrimitive()) {
                continue;
            }
            ClassLoader loader = type.getClassLoader();
            if (loader == null || !loaders.contains(loader) || isIgnoredPackage(type.getName())) {
                continue;
            }
            Field[] fields = declaredFields(type);
            if (fields.length < 15 || !hasLiveSelfInstance(type, fields) || !canScheduleTasks(type)) {
                continue;
            }
            int score = fields.length + (type.getName().startsWith("net.minecraft.") ? 1000 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = type;
            }
        }
        if (best != null) {
            LogUtil.info("Minecraft class discovered structurally: " + best.getName());
        }
        return best;
    }

    private static boolean isIgnoredPackage(String name) {
        return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("sun.")
                || name.startsWith("jdk.") || name.startsWith("com.sun.")
                || name.startsWith("dev.zis30axs.") || name.startsWith("org.apache.")
                || name.startsWith("com.google.") || name.startsWith("io.netty.");
    }

    private static boolean hasLiveSelfInstance(Class<?> type, Field[] fields) {
        for (Field field : fields) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != type) {
                continue;
            }
            try {
                field.setAccessible(true);
                if (field.get(null) != null) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static boolean canScheduleTasks(Class<?> type) {
        if (Executor.class.isAssignableFrom(type)) {
            return true;
        }
        for (Method method : publicMethods(type)) {
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 1 && parameters[0] == Runnable.class
                    && !Modifier.isStatic(method.getModifiers())) {
                return true;
            }
        }
        return false;
    }

    private static Long resolveWindowFromField() {
        Class<?> windowType = findGameClass(WINDOW_CLASS_NAMES);
        Class<?> type = clientClass();
        if (windowType == null || type == null) {
            return null;
        }
        Object mc = client();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : declaredFields(current)) {
                if (Modifier.isStatic(field.getModifiers()) || !windowType.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Long handle = readSingleLong(field.get(mc));
                    if (handle != null) {
                        return handle;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static Long readSingleLong(Object window) {
        if (window == null) {
            return null;
        }
        List<Long> candidates = new ArrayList<Long>();
        for (Field field : declaredFields(window.getClass())) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType() != long.class) {
                continue;
            }
            try {
                field.setAccessible(true);
                long value = field.getLong(window);
                if (value != 0L) {
                    candidates.add(Long.valueOf(value));
                }
            } catch (Throwable ignored) {
            }
        }
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    /**
     * Safe last resort: ask GLFW on the render thread which window owns the
     * current GL context. No pointer is ever guessed.
     */
    private static Long resolveWindowFromCurrentContext() {
        final Class<?> glfw = findGameClass("org.lwjgl.glfw.GLFW");
        if (glfw == null) {
            return null;
        }
        final Method currentContext = findMethod(glfw, "glfwGetCurrentContext");
        if (currentContext == null || !Modifier.isStatic(currentContext.getModifiers())) {
            return null;
        }

        final AtomicLong result = new AtomicLong();
        final CountDownLatch latch = new CountDownLatch(1);
        boolean scheduled = execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Object value = currentContext.invoke(null);
                    if (value instanceof Long) {
                        result.set(((Long) value).longValue());
                    }
                } catch (Throwable ignored) {
                } finally {
                    latch.countDown();
                }
            }
        });
        if (!scheduled) {
            return null;
        }
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                return null;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        }
        long handle = result.get();
        return handle == 0L ? null : Long.valueOf(handle);
    }
}
