package dev.zis30axs.sigma.hotinjection.agent;

import dev.zis30axs.sigma.hotinjection.util.LogUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.instrument.Instrumentation;

public final class HotInjectionAgent {
    private HotInjectionAgent() {
    }

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
            writeAcknowledgement(options.get("ack"), source);
        } catch (Throwable error) {
            LogUtil.error("Agent bootstrap failed", error);
            throw new RuntimeException("Sigma HotInjection bootstrap failed", error);
        }
    }

    private static void writeAcknowledgement(String path, String source) {
        if (path == null || path.trim().isEmpty()) {
            return;
        }

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
            writer.flush();
        } catch (IOException error) {
            LogUtil.warn("Could not write host acknowledgement: " + error.getMessage());
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
