package dev.zis30axs.sigma.hotinjection.agent.net;

import dev.zis30axs.sigma.hotinjection.agent.AgentContext;
import dev.zis30axs.sigma.hotinjection.agent.client.GameAccess;
import dev.zis30axs.sigma.hotinjection.agent.client.ObjectGraph;
import dev.zis30axs.sigma.hotinjection.event.PacketSendEvent;
import dev.zis30axs.sigma.hotinjection.util.LogUtil;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

/**
 * Installs an outbound netty handler on the client's connection so every packet
 * passes through {@link PacketSendEvent} before it is encoded.
 *
 * <p>No Minecraft mapping is involved: the channel is found by its netty type,
 * and the handler is a {@link Proxy} over {@code ChannelOutboundHandler}, so the
 * agent still compiles without a netty or Minecraft dependency. Anything the
 * handler cannot understand is forwarded untouched.</p>
 */
public final class NetworkHook {
    private static final String HANDLER_NAME = "sigma-hotinjection-outbound";
    private static final int MAX_DEPTH = 3;
    private static final int MAX_NODES = 400;

    private static volatile Object hookedChannel;
    private static volatile boolean unsupported;
    private static volatile Class<?> contextType;
    private static volatile Method contextWrite;

    private NetworkHook() {
    }

    /** @return true when the guard is active on the current connection. */
    public static synchronized boolean ensureInstalled() {
        if (unsupported) {
            return false;
        }
        try {
            Object channel = findChannel();
            if (channel == null) {
                hookedChannel = null;
                return false;
            }
            if (channel == hookedChannel) {
                return true;
            }
            return install(channel);
        } catch (Throwable failure) {
            LogUtil.warn("Packet guard installation failed: " + failure);
            return false;
        }
    }

    public static boolean isInstalled() {
        return hookedChannel != null;
    }

    public static String describe() {
        Object channel = hookedChannel;
        if (unsupported) {
            return "unsupported (no netty channel in this process)";
        }
        return channel == null ? "not installed" : "installed on " + channel.getClass().getName();
    }

    private static boolean install(Object channel) throws Exception {
        Class<?> outboundType = GameAccess.findGameClass("io.netty.channel.ChannelOutboundHandler");
        Class<?> pipelineType = GameAccess.findGameClass("io.netty.channel.ChannelPipeline");
        Class<?> handlerContextType = GameAccess.findGameClass("io.netty.channel.ChannelHandlerContext");
        if (outboundType == null || pipelineType == null || handlerContextType == null) {
            unsupported = true;
            LogUtil.warn("Netty types not found; the packet guard stays disabled.");
            return false;
        }
        contextType = handlerContextType;

        Object pipeline = call(channel, "pipeline");
        if (pipeline == null) {
            return false;
        }
        removeStaleHandler(pipelineType, pipeline);

        ClassLoader loader = outboundType.getClassLoader();
        Object proxy = Proxy.newProxyInstance(
                loader == null ? ClassLoader.getSystemClassLoader() : loader,
                new Class<?>[] { outboundType },
                new Guard());

        Method addLast = findByArity(pipelineType, "addLast", 2, String.class);
        if (addLast == null) {
            LogUtil.warn("ChannelPipeline.addLast(String, ChannelHandler) not found.");
            return false;
        }
        addLast.invoke(pipeline, HANDLER_NAME, proxy);
        hookedChannel = channel;
        LogUtil.info("Packet guard installed on " + channel.getClass().getName());
        return true;
    }

    private static void removeStaleHandler(Class<?> pipelineType, Object pipeline) {
        try {
            Method get = findByArity(pipelineType, "get", 1, String.class);
            if (get == null || get.invoke(pipeline, HANDLER_NAME) == null) {
                return;
            }
            Method remove = findByArity(pipelineType, "remove", 1, String.class);
            if (remove != null) {
                remove.invoke(pipeline, HANDLER_NAME);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object findChannel() {
        Class<?> channelType = GameAccess.findGameClass("io.netty.channel.Channel");
        if (channelType == null) {
            unsupported = true;
            return null;
        }
        Object client = GameAccess.client();
        if (client == null) {
            return null;
        }
        Object direct = scanFields(client, channelType);
        if (direct != null) {
            return direct;
        }
        for (ObjectGraph.Node node : ObjectGraph.walk(client, MAX_DEPTH, MAX_NODES)) {
            Object found = scanFields(node.getValue(), channelType);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Object scanFields(Object owner, Class<?> channelType) {
        for (Class<?> current = owner.getClass();
             current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Field field : GameAccess.declaredFields(current)) {
                if (Modifier.isStatic(field.getModifiers()) || !channelType.isAssignableFrom(field.getType())) {
                    continue;
                }
                Object value = ObjectGraph.read(field, owner);
                if (value != null && isOpen(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private static boolean isOpen(Object channel) {
        Object open = call(channel, "isOpen");
        return open == null || Boolean.TRUE.equals(open);
    }

    private static Object call(Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findByArity(Class<?> type, String name, int arity, Class<?> firstParameter) {
        for (Method method : type.getMethods()) {
            if (!name.equals(method.getName())) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length != arity) {
                continue;
            }
            if (firstParameter != null && (arity == 0 || parameters[0] != firstParameter)) {
                continue;
            }
            return method;
        }
        return null;
    }

    /** Forwards every context call by name and arity; only writes are inspected. */
    private static final class Guard implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("write".equals(name) && args != null && args.length == 3) {
                return write(args[0], args[1], args[2]);
            }
            if ("toString".equals(name)) {
                return "SigmaHotInjectionOutboundGuard";
            }
            if ("hashCode".equals(name)) {
                return Integer.valueOf(System.identityHashCode(proxy));
            }
            if ("equals".equals(name)) {
                return Boolean.valueOf(args != null && args.length == 1 && proxy == args[0]);
            }
            if ("handlerAdded".equals(name) || "handlerRemoved".equals(name)) {
                return null;
            }
            if ("exceptionCaught".equals(name) && args != null && args.length == 2) {
                forward(args[0], "fireExceptionCaught", new Object[] { args[1] });
                return null;
            }
            if (args == null || args.length == 0) {
                return null;
            }
            forward(args[0], name, tail(args));
            return null;
        }

        private Object write(Object context, Object message, Object promise) throws Throwable {
            boolean cancelled = false;
            try {
                cancelled = AgentContext.post(new PacketSendEvent(message)).isCancelled();
            } catch (Throwable failure) {
                cancelled = false;
            }
            if (!cancelled) {
                forward(context, "write", new Object[] { message, promise });
                return null;
            }
            complete(promise);
            release(message);
            return null;
        }

        private void forward(Object context, String name, Object[] arguments) throws Throwable {
            Class<?> type = contextType;
            if (context == null || type == null) {
                return;
            }
            Method target;
            if ("write".equals(name) && arguments.length == 2) {
                target = contextWrite;
                if (target == null) {
                    target = findByArity(type, "write", 2, null);
                    contextWrite = target;
                }
            } else {
                target = findByArity(type, name, arguments.length, null);
            }
            if (target == null) {
                LogUtil.warn("Cannot forward netty call " + name + "/" + arguments.length);
                return;
            }
            try {
                target.invoke(context, arguments);
            } catch (InvocationTargetException wrapped) {
                throw wrapped.getCause() == null ? wrapped : wrapped.getCause();
            }
        }

        private void complete(Object promise) {
            if (promise == null) {
                return;
            }
            if (call(promise, "trySuccess") != null) {
                return;
            }
            call(promise, "setSuccess");
        }

        private void release(Object message) {
            Class<?> counted = GameAccess.findGameClass("io.netty.util.ReferenceCounted");
            if (counted != null && counted.isInstance(message)) {
                call(message, "release");
            }
        }

        private Object[] tail(Object[] args) {
            Object[] rest = new Object[args.length - 1];
            System.arraycopy(args, 1, rest, 0, rest.length);
            return rest;
        }
    }
}
