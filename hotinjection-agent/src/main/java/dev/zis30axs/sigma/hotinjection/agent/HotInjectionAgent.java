package dev.zis30axs.sigma.hotinjection.agent;

import dev.zis30axs.sigma.hotinjection.agent.control.AgentControlServer;
import dev.zis30axs.sigma.hotinjection.util.LogUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.instrument.Instrumentation;

public final class HotInjectionAgent {
    private static volatile AgentControlServer controlServer;

    private HotInjectionAgent() { }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        bootstrap("premain", agentArgs, instrumentation);
    }

    public static void agentmain(String agentArgs, Instrumentation instrumentation) {
        bootstrap("attach", agentArgs, instrumentation);
    }

    private static void bootstrap(String source, String agentArgs, Instrumentation instrumentation) {
        AgentOptions options = AgentOptions.parse(agentArgs);
        try {
            RuntimeBootstrap.start(source, options, instrumentation);
            ensureControlServer();
            writeAcknowledgement(options.get("ack"), source);
        } catch (Throwable error) {
            LogUtil.error("Agent bootstrap failed", error);
            throw new RuntimeException("Sigma HotInjection bootstrap failed", error);
        }
    }

    private static void ensureControlServer() {
        if (controlServer != null || RuntimeBootstrap.getRuntime() == null) return;
        synchronized (HotInjectionAgent.class) {
            if (controlServer != null) return;
            try {
                controlServer = AgentControlServer.start(RuntimeBootstrap.getRuntime());
                LogUtil.info("Host control channel ready on 127.0.0.1:" + controlServer.getPort());
            } catch (IOException error) {
                LogUtil.warn("Host control channel unavailable: " + error.getMessage());
            }
        }
    }

    private static void writeAcknowledgement(String path, String source) {
        if (path == null || path.trim().isEmpty()) return;
        Writer writer = null;
        try {
            File target = new File(path.trim());
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                LogUtil.warn("Could not create acknowledgement directory: " + parent);
                return;
            }
            writer = new OutputStreamWriter(new FileOutputStream(target), "UTF-8");
            writer.write("OK\n");
            writer.write("source=" + source + "\n");
            if (RuntimeBootstrap.getRuntime() != null) {
                writer.write("version=" + RuntimeBootstrap.getRuntime().getActiveVersion().getId() + "\n");
            }
            AgentControlServer server = controlServer;
            if (server != null) {
                writer.write("port=" + server.getPort() + "\n");
                writer.write("token=" + server.getToken() + "\n");
            }
            writer.flush();
        } catch (IOException error) {
            LogUtil.warn("Could not write host acknowledgement: " + error.getMessage());
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (IOException ignored) { }
            }
        }
    }
}
