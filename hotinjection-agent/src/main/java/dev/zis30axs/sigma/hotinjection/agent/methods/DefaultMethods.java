package dev.zis30axs.sigma.hotinjection.agent.methods;

import dev.zis30axs.sigma.hotinjection.HotInjectionRuntime;
import dev.zis30axs.sigma.hotinjection.event.InjectionNoticeEvent;
import dev.zis30axs.sigma.hotinjection.method.HotMethod;
import dev.zis30axs.sigma.hotinjection.method.MethodContext;
import dev.zis30axs.sigma.hotinjection.module.Module;
import dev.zis30axs.sigma.hotinjection.version.VersionAdapter;
import java.util.List;

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
                VersionAdapter adapter = runtime.getActiveAdapter();
                if (adapter == null) throw new IllegalStateException("No active version adapter");
                String message = arguments.isEmpty() ? "Sigma HotInjection test notification" : join(arguments);
                InjectionNoticeEvent event = runtime.getEventBus().post(
                        new InjectionNoticeEvent(runtime.getActiveVersion(), message));
                if (!event.isCancelled()) {
                    adapter.showClientMessage(event.getMessage());
                    return "shown";
                }
                return "cancelled";
            }
        });
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
