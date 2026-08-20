package dev.zis30axs.sigma.hotinjection.agent.client;

import dev.zis30axs.sigma.hotinjection.agent.LocalToastBridge;
import dev.zis30axs.sigma.hotinjection.util.LogUtil;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Finds the local chat sink of the running client and pushes text into it.
 *
 * <p>The named lookups cover MCP, Yarn, intermediary and SRG runtimes. When
 * every name is obfuscated the structural pass takes over: it looks for an
 * object reachable from the client that owns a {@code void method(Component)}
 * plus a multi-argument sibling overload, which is what separates the chat HUD
 * from title/tab-list overlays.</p>
 */
final class ChatSink {
    private static final String[] GUI_FIELD_NAMES = {
            "ingameGUI", "gui", "inGameHud", "field_71456_v", "field_1705", "f_91065_"
    };
    private static final String[] CHAT_ACCESSOR_NAMES = {
            "getChatGUI", "getChatHud", "func_146158_b", "method_1743", "m_93076_"
    };
    private static final String[] CHAT_FIELD_NAMES = {
            "persistentChatGUI", "chatGUI", "chatHud", "chat", "field_73840_e", "field_1705"
    };
    private static final String[] ADD_METHOD_NAMES = {
            "printChatMessage", "addMessage", "addChatMessage",
            "func_146227_a", "method_1812", "m_93785_"
    };
    private static final int MIN_STRUCTURAL_SCORE = 30;
    private static final int MAX_NODES = 400;

    private static boolean resolved;
    private static Object target;
    private static Method method;
    private static Class<?> componentType;

    private ChatSink() {
    }

    static synchronized boolean isAvailable() {
        return resolve();
    }

    static boolean send(final String message) {
        if (message == null || message.trim().isEmpty() || !isAvailable()) {
            return false;
        }
        final Object component = ChatComponents.create(componentType, message);
        if (component == null) {
            return false;
        }
        final Object sink = target;
        final Method add = method;
        return GameAccess.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    add.invoke(sink, component);
                } catch (Throwable failure) {
                    LogUtil.warn("Native chat call failed (" + failure + "); using the local toast.");
                    invalidate();
                    LocalToastBridge.show(message);
                }
            }
        });
    }

    private static synchronized void invalidate() {
        target = null;
        method = null;
        componentType = null;
        resolved = true;
    }

    private static boolean resolve() {
        if (resolved) {
            return method != null;
        }
        resolved = true;
        Object mc = GameAccess.client();
        if (mc == null) {
            return false;
        }
        if (resolveNamed(mc) || resolveStructural(mc)) {
            LogUtil.info("Client chat bridge: " + target.getClass().getName() + "#" + method.getName()
                    + "(" + componentType.getName() + ")");
            return true;
        }
        LogUtil.info("No local chat sink found; using the toast fallback for client messages.");
        return false;
    }

    private static boolean resolveNamed(Object mc) {
        Field guiField = GameAccess.findField(mc.getClass(), GUI_FIELD_NAMES);
        Object gui = guiField == null ? null : read(guiField, mc);
        if (gui == null) {
            return false;
        }

        Object chat = null;
        Method accessor = GameAccess.findMethod(gui.getClass(), CHAT_ACCESSOR_NAMES);
        if (accessor != null && !Modifier.isStatic(accessor.getModifiers())) {
            try {
                chat = accessor.invoke(gui);
            } catch (Throwable ignored) {
            }
        }
        if (chat == null) {
            Field chatField = GameAccess.findField(gui.getClass(), CHAT_FIELD_NAMES);
            chat = chatField == null ? null : read(chatField, gui);
        }
        if (chat == null) {
            return false;
        }

        for (Method candidate : GameAccess.publicMethods(chat.getClass())) {
            if (!contains(ADD_METHOD_NAMES, candidate.getName()) || Modifier.isStatic(candidate.getModifiers())) {
                continue;
            }
            Class<?>[] parameters = candidate.getParameterTypes();
            if (parameters.length == 1 && ChatComponents.isComponentType(parameters[0])) {
                return accept(chat, candidate, parameters[0]);
            }
        }
        return false;
    }

    private static boolean resolveStructural(Object mc) {
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (ObjectGraph.Node node : ObjectGraph.walk(mc, 2, MAX_NODES)) {
            collect(candidates, node.getValue(), node.getDepth());
        }
        Collections.sort(candidates, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate a, Candidate b) {
                return Integer.compare(b.score, a.score);
            }
        });

        for (Candidate candidate : candidates) {
            if (candidate.score < MIN_STRUCTURAL_SCORE) {
                break;
            }
            if (ChatComponents.create(candidate.componentType, "sigma") != null) {
                return accept(candidate.owner, candidate.method, candidate.componentType);
            }
        }
        return false;
    }

    private static void collect(List<Candidate> candidates, Object owner, int depth) {
        if (owner == null) {
            return;
        }
        Class<?> ownerClass = owner.getClass();
        for (Method candidate : GameAccess.publicMethods(ownerClass)) {
            if (Modifier.isStatic(candidate.getModifiers()) || candidate.getReturnType() != void.class) {
                continue;
            }
            Class<?>[] parameters = candidate.getParameterTypes();
            if (parameters.length != 1 || !ChatComponents.isComponentType(parameters[0])) {
                continue;
            }
            candidates.add(new Candidate(owner, candidate, parameters[0],
                    score(ownerClass, candidate, parameters[0], depth)));
        }
    }

    private static int score(Class<?> ownerClass, Method candidate, Class<?> component, int depth) {
        int score = 0;
        if (ownerClass.getSimpleName().toLowerCase().contains("chat")) {
            score += 50;
        }
        String name = candidate.getName().toLowerCase();
        if (name.contains("chat") || name.contains("message")) {
            score += 30;
        }
        if (depth >= 2) {
            score += 15;
        }
        for (Method sibling : GameAccess.publicMethods(ownerClass)) {
            Class<?>[] parameters = sibling.getParameterTypes();
            if (parameters.length >= 2 && parameters[0] == component) {
                score += 25;
                break;
            }
        }
        return score;
    }

    private static boolean accept(Object owner, Method candidate, Class<?> component) {
        try {
            candidate.setAccessible(true);
        } catch (Throwable ignored) {
            return false;
        }
        target = owner;
        method = candidate;
        componentType = component;
        return true;
    }

    private static Object read(Field field, Object owner) {
        return ObjectGraph.read(field, owner);
    }

    private static boolean contains(String[] values, String value) {
        for (String candidate : values) {
            if (candidate.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static final class Candidate {
        private final Object owner;
        private final Method method;
        private final Class<?> componentType;
        private final int score;

        private Candidate(Object owner, Method method, Class<?> componentType, int score) {
            this.owner = owner;
            this.method = method;
            this.componentType = componentType;
            this.score = score;
        }
    }
}
