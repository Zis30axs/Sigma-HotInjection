package dev.zis30axs.sigma.hotinjection.agent.client;

import dev.zis30axs.sigma.hotinjection.agent.net.LocalChatGuard;
import dev.zis30axs.sigma.hotinjection.agent.net.NetworkHook;
import dev.zis30axs.sigma.hotinjection.util.LogUtil;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Drives the client's own chat send path for local-only messages.
 *
 * <p>The call is made on the game thread exactly like a typed chat message. The
 * packet it produces is then cancelled by the registered {@code local-chat}
 * module through {@code PacketSendEvent}, so nothing reaches the server. If the
 * packet guard is not installed, or no cancelling listener is active, this class
 * refuses to run at all - the message stays a purely local echo instead of
 * risking the network.</p>
 */
public final class ChatSender {
    private static final String[] SEND_METHOD_NAMES = {
            "sendChatMessage", "sendChat", "chat", "sendCommand",
            "func_71165_d", "method_1748", "method_44096", "m_246213_"
    };
    private static final Set<String> FAILED = new HashSet<String>();

    private static volatile Executor worker;
    private static volatile Object cachedOwner;
    private static volatile Method cachedMethod;

    private ChatSender() {
    }

    /** Fire-and-forget: never blocks the caller, never blocks the game thread. */
    public static void sendThroughGame(final String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (!LocalChatGuard.isListenerActive()) {
            LogUtil.info("Chat send path skipped: no packet-cancelling listener is active.");
            return;
        }
        if (!NetworkHook.ensureInstalled()) {
            LogUtil.info("Chat send path skipped: packet guard is " + NetworkHook.describe() + ".");
            return;
        }
        worker().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    dispatch(text);
                } catch (Throwable failure) {
                    LogUtil.warn("Local chat send failed: " + failure);
                }
            }
        });
    }

    private static void dispatch(String text) {
        Object owner = cachedOwner;
        Method method = cachedMethod;
        if (owner != null && method != null) {
            if (attempt(owner, method, text)) {
                return;
            }
            cachedOwner = null;
            cachedMethod = null;
        }

        for (Candidate candidate : candidates()) {
            String key = candidate.key();
            synchronized (FAILED) {
                if (FAILED.contains(key)) {
                    continue;
                }
            }
            if (attempt(candidate.owner, candidate.method, text)) {
                cachedOwner = candidate.owner;
                cachedMethod = candidate.method;
                LogUtil.info("Client chat send path: " + key);
                return;
            }
            synchronized (FAILED) {
                FAILED.add(key);
            }
        }
        LogUtil.info("No usable client chat send path; the message stays a local echo.");
    }

    private static boolean attempt(final Object owner, final Method method, final String text) {
        LocalChatGuard.arm(text);
        final CountDownLatch invoked = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        boolean scheduled = GameAccess.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    method.setAccessible(true);
                    method.invoke(owner, text);
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    invoked.countDown();
                }
            }
        });
        if (!scheduled) {
            LocalChatGuard.disarm();
            return false;
        }

        try {
            if (!invoked.await(2, TimeUnit.SECONDS)) {
                LocalChatGuard.disarm();
                return false;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            LocalChatGuard.disarm();
            return false;
        }
        if (failure.get() != null) {
            LocalChatGuard.disarm();
            return false;
        }

        boolean dropped = LocalChatGuard.awaitDropped(750);
        if (dropped) {
            LogUtil.info("Chat packet cancelled locally; the server never saw it.");
        }
        return dropped;
    }

    private static List<Candidate> candidates() {
        List<Candidate> candidates = new ArrayList<Candidate>();
        Object client = GameAccess.client();
        if (client == null) {
            return candidates;
        }

        List<Object> network = networkObjects(client);
        Object player = GameAccess.player();

        addNamed(candidates, player);
        for (Object owner : network) {
            addNamed(candidates, owner);
        }
        addNamed(candidates, client);
        for (Object owner : network) {
            addStructural(candidates, owner);
        }
        return candidates;
    }

    private static void addNamed(List<Candidate> candidates, Object owner) {
        if (owner == null) {
            return;
        }
        for (Method method : GameAccess.publicMethods(owner.getClass())) {
            if (!isSingleStringCall(method) || !contains(SEND_METHOD_NAMES, method.getName())) {
                continue;
            }
            candidates.add(new Candidate(owner, method));
        }
    }

    private static void addStructural(List<Candidate> candidates, Object owner) {
        if (owner == null) {
            return;
        }
        for (Method method : GameAccess.publicMethods(owner.getClass())) {
            if (isSingleStringCall(method)) {
                candidates.add(new Candidate(owner, method));
            }
        }
    }

    private static boolean isSingleStringCall(Method method) {
        if (Modifier.isStatic(method.getModifiers())) {
            return false;
        }
        Class<?>[] parameters = method.getParameterTypes();
        return parameters.length == 1 && parameters[0] == String.class;
    }

    /** The connection and the packet listener that owns it. */
    private static List<Object> networkObjects(Object client) {
        List<Object> result = new ArrayList<Object>();
        Class<?> channelType = GameAccess.findGameClass("io.netty.channel.Channel");
        if (channelType == null) {
            return result;
        }
        for (Object first : ObjectGraph.children(client, ObjectGraph.DEFAULT_FIELD_LIMIT)) {
            if (GameAccess.declaresFieldOfType(first.getClass(), channelType)) {
                result.add(first);
                continue;
            }
            for (Object second : ObjectGraph.children(first, ObjectGraph.DEFAULT_FIELD_LIMIT)) {
                if (GameAccess.declaresFieldOfType(second.getClass(), channelType)) {
                    result.add(first);
                    result.add(second);
                    break;
                }
            }
        }
        return result;
    }

    private static boolean contains(String[] values, String value) {
        for (String candidate : values) {
            if (candidate.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static Executor worker() {
        Executor existing = worker;
        if (existing != null) {
            return existing;
        }
        synchronized (ChatSender.class) {
            if (worker == null) {
                worker = Executors.newSingleThreadExecutor(new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable task) {
                        Thread thread = new Thread(task, "Sigma-HotInjection-Chat");
                        thread.setDaemon(true);
                        return thread;
                    }
                });
            }
            return worker;
        }
    }

    private static final class Candidate {
        private final Object owner;
        private final Method method;

        private Candidate(Object owner, Method method) {
            this.owner = owner;
            this.method = method;
        }

        private String key() {
            return owner.getClass().getName() + "#" + method.getName();
        }
    }
}
