package dev.zis30axs.sigma.hotinjection.agent.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/** Builds a Minecraft chat component without compiling against Minecraft. */
final class ChatComponents {
    private static final String[] LITERAL_FACTORY_NAMES = {
            "literal", "method_43470", "m_237113_", "nullToEmpty", "method_30163"
    };
    private static final String[] LITERAL_CLASS_NAMES = {
            "net.minecraft.util.ChatComponentText",
            "net.minecraft.util.text.TextComponentString",
            "net.minecraft.util.text.StringTextComponent",
            "net.minecraft.network.chat.TextComponent",
            "net.minecraft.text.LiteralText",
            "net.minecraft.class_2585"
    };

    private ChatComponents() {
    }

    /**
     * Chat components are the only game types that expose both a sibling list
     * and a plain-text view without arguments. That fingerprint survives
     * obfuscation, SRG and intermediary names alike.
     */
    static boolean isComponentType(Class<?> type) {
        if (type == null || type.isPrimitive() || type.isArray() || type.isEnum()
                || type == String.class || !GameAccess.isGameClass(type)) {
            return false;
        }
        boolean siblings = false;
        boolean text = false;
        for (Method method : GameAccess.publicMethods(type)) {
            if (method.getParameterTypes().length != 0 || Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (method.getReturnType() == List.class) {
                siblings = true;
            } else if (method.getReturnType() == String.class) {
                text = true;
            }
        }
        return siblings && text;
    }

    /** @return a component instance carrying {@code text}, or {@code null}. */
    static Object create(Class<?> componentType, String text) {
        Object component = createViaFactory(componentType, text);
        return component != null ? component : createViaConstructor(componentType, text);
    }

    private static Object createViaFactory(Class<?> componentType, String text) {
        for (String name : LITERAL_FACTORY_NAMES) {
            for (Method method : GameAccess.publicMethods(componentType)) {
                if (name.equals(method.getName()) && isLiteralFactory(componentType, method)) {
                    Object created = invokeFactory(method, text);
                    if (created != null) {
                        return created;
                    }
                }
            }
        }
        for (Method method : GameAccess.publicMethods(componentType)) {
            if (isLiteralFactory(componentType, method)) {
                Object created = invokeFactory(method, text);
                if (created != null) {
                    return created;
                }
            }
        }
        return null;
    }

    private static boolean isLiteralFactory(Class<?> componentType, Method method) {
        if (!Modifier.isStatic(method.getModifiers())) {
            return false;
        }
        Class<?>[] parameters = method.getParameterTypes();
        return parameters.length == 1 && parameters[0] == String.class
                && componentType.isAssignableFrom(method.getReturnType());
    }

    private static Object invokeFactory(Method method, String text) {
        try {
            method.setAccessible(true);
            Object created = method.invoke(null, text);
            return renders(created, text) ? created : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object createViaConstructor(Class<?> componentType, String text) {
        Class<?> named = GameAccess.findGameClass(LITERAL_CLASS_NAMES);
        if (named != null && componentType.isAssignableFrom(named)) {
            Object created = construct(named, text);
            if (created != null) {
                return created;
            }
        }
        for (Class<?> candidate : GameAccess.loadedGameClasses()) {
            if (candidate.isInterface() || Modifier.isAbstract(candidate.getModifiers())
                    || !componentType.isAssignableFrom(candidate)) {
                continue;
            }
            Object created = construct(candidate, text);
            if (created != null) {
                return created;
            }
        }
        return null;
    }

    private static Object construct(Class<?> type, String text) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(String.class);
            constructor.setAccessible(true);
            Object created = constructor.newInstance(text);
            return renders(created, text) ? created : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Verifies that the candidate really renders as {@code text} before it is used. */
    private static boolean renders(Object component, String text) {
        if (component == null) {
            return false;
        }
        boolean checked = false;
        for (Method method : GameAccess.publicMethods(component.getClass())) {
            if (Modifier.isStatic(method.getModifiers())
                    || method.getParameterTypes().length != 0
                    || method.getReturnType() != String.class) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object value = method.invoke(component);
                if (value instanceof String) {
                    checked = true;
                    if (((String) value).contains(text)) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return !checked;
    }
}
