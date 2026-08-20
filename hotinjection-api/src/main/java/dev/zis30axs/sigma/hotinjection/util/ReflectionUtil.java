package dev.zis30axs.sigma.hotinjection.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ReflectionUtil {
    private ReflectionUtil() {
    }

    public static Class<?> findClass(ClassLoader loader, String... names) {
        for (String name : names) {
            try {
                return Class.forName(name, false, loader);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }

    public static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            Method method = type.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    public static Field findField(Class<?> type, String... names) {
        for (String name : names) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }
}
