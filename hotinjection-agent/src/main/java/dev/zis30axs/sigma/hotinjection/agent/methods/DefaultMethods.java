package dev.zis30axs.sigma.hotinjection.agent.methods;

import dev.zis30axs.sigma.hotinjection.HotInjectionRuntime;
import dev.zis30axs.sigma.hotinjection.agent.client.ClientChat;
import dev.zis30axs.sigma.hotinjection.event.ClickGuiToggleEvent;
import dev.zis30axs.sigma.hotinjection.event.ClientMessageEvent;
import dev.zis30axs.sigma.hotinjection.event.InjectionNoticeEvent;
import dev.zis30axs.sigma.hotinjection.gui.ClickGuiButton;
import dev.zis30axs.sigma.hotinjection.gui.ClickGuiHost;
import dev.zis30axs.sigma.hotinjection.method.HotMethod;
import dev.zis30axs.sigma.hotinjection.method.MethodContext;
import dev.zis30axs.sigma.hotinjection.module.Module;
import java.util.List;
import java.util.Locale;

public final class DefaultMethods {
    private DefaultMethods() {
    }

    public static void register(final HotInjectionRuntime runtime) {
        runtime.getMethodRegistry().register(new HotMethod() {
            @Override
            public String getName() { return "runtime.info"; }

            @Override
            public String invoke(MethodContext context, List<String> arguments) {
                return "protocol=" + HotInjectionRuntime.PROTOCOL_VERSION
                        + ";version=" + runtime.getActiveVersion().getId()
                        + ";modules=" + runtime.getModuleRegistry().all().size();
            }
        });

        runtime.getMethodRegistry().register(new HotMethod() {
            @Override
            public String getName() { return "modules.list"; }

            @Override
            public String invoke(MethodContext context, List<String> arguments) {
                StringBuilder result = new StringBuilder();
                for (Module module : runtime.getModuleRegistry().all()) {
                    if (result.length() > 0) result.append(',');
                    result.append(module.getId()).append(':').append(module.isEnabled());
                }
                return result.toString();
            }
        });

        runtime.getMethodRegistry().register(new HotMethod() {
            @Override
            public String getName() { return "module.set"; }

            @Override
            public String invoke(MethodContext context, List<String> arguments) {
                if (arguments.size() < 2) {
                    throw new IllegalArgumentException("module.set requires <id> <true|false>");
                }
                boolean enabled = Boolean.parseBoolean(arguments.get(1));
                if (!runtime.getModuleRegistry().setEnabled(arguments.get(0), enabled)) {
                    throw new IllegalArgumentException("Unknown module: " + arguments.get(0));
                }
                return "ok";
            }
        });

        runtime.getMethodRegistry().register(new HotMethod() {
            @Override
            public String getName() { return "notice.test"; }

            @Override
            public String invoke(MethodContext context, List<String> arguments) {
                String message = arguments.isEmpty() ? "Sigma HotInjection test notification" : join(arguments);
                InjectionNoticeEvent event = runtime.getEventBus().post(
                        new InjectionNoticeEvent(runtime.getActiveVersion(), message));
                if (event.isCancelled()) {
                    return "cancelled";
                }
                return runtime.sendClientMessage(ClientMessageEvent.SOURCE_METHOD, event.getMessage())
                        ? "shown" : "cancelled";
            }
        });

        runtime.getMethodRegistry().register(new HotMethod() {
            @Override
            public String getName() { return "client.message"; }

            @Override
            public String invoke(MethodContext context, List<String> arguments) {
                if (arguments.isEmpty()) {
                    throw new IllegalArgumentException("client.message requires <text...>");
                }
                return runtime.sendClientMessage(ClientMessageEvent.SOURCE_METHOD, join(arguments))
                        ? "shown" : "cancelled";
            }
        });

        runtime.getMethodRegistry().register(new HotMethod() {
            @Override
            public String getName() { return "chat.state"; }

            @Override
            public String invoke(MethodContext context, List<String> arguments) {
                return ClientChat.describe();
            }
        });

        runtime.getMethodRegistry().register(new HotMethod() {
            @Override
            public String getName() { return "clickgui.toggle"; }

            @Override
            public String invoke(MethodContext context, List<String> arguments) {
                ClickGuiHost host = requireHost(runtime);
                String mode = arguments.isEmpty() ? "toggle" : arguments.get(0).toLowerCase(Locale.ROOT);
                if ("open".equals(mode)) {
                    host.open(ClickGuiToggleEvent.SOURCE_METHOD);
                } else if ("close".equals(mode)) {
                    host.close(ClickGuiToggleEvent.SOURCE_METHOD);
                } else {
                    host.toggle(ClickGuiToggleEvent.SOURCE_METHOD);
                }
                return host.isOpen() ? "open" : "closed";
            }
        });

        runtime.getMethodRegistry().register(new HotMethod() {
            @Override
            public String getName() { return "clickgui.state"; }

            @Override
            public String invoke(MethodContext context, List<String> arguments) {
                ClickGuiHost host = runtime.getClickGuiHost();
                StringBuilder state = new StringBuilder();
                state.append("host=").append(host != null);
                state.append(";available=").append(host != null && host.isAvailable());
                state.append(";open=").append(host != null && host.isOpen());
                state.append(";buttons=");
                for (ClickGuiButton button : runtime.getClickGuiRegistry().all()) {
                    state.append(button.getId()).append(',');
                }
                return state.toString();
            }
        });

        runtime.getMethodRegistry().register(new HotMethod() {
            @Override
            public String getName() { return "clickgui.click"; }

            @Override
            public String invoke(MethodContext context, List<String> arguments) {
                if (arguments.isEmpty()) {
                    throw new IllegalArgumentException("clickgui.click requires <button-id>");
                }
                ClickGuiButton button = runtime.getClickGuiRegistry().get(arguments.get(0));
                if (button == null) {
                    throw new IllegalArgumentException("Unknown ClickGUI button: " + arguments.get(0));
                }
                button.getAction().perform(runtime);
                return "clicked";
            }
        });
    }

    private static ClickGuiHost requireHost(HotInjectionRuntime runtime) {
        ClickGuiHost host = runtime.getClickGuiHost();
        if (host == null) {
            throw new IllegalStateException("ClickGUI module is not enabled");
        }
        if (!host.isAvailable()) {
            throw new IllegalStateException("ClickGUI has no display in this process");
        }
        return host;
    }

    private static String join(List<String> arguments) {
        StringBuilder text = new StringBuilder();
        for (String argument : arguments) {
            if (text.length() > 0) text.append(' ');
            text.append(argument);
        }
        return text.toString();
    }
}
