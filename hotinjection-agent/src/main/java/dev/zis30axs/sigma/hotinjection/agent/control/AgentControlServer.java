package dev.zis30axs.sigma.hotinjection.agent.control;

import dev.zis30axs.sigma.hotinjection.HotInjectionRuntime;
import dev.zis30axs.sigma.hotinjection.module.Module;
import dev.zis30axs.sigma.hotinjection.module.setting.ModuleSetting;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** Loopback-only, token-authenticated control channel used by the standalone Host ClickGUI. */
public final class AgentControlServer {
    private final HotInjectionRuntime runtime;
    private final ServerSocket serverSocket;
    private final String token;

    private AgentControlServer(HotInjectionRuntime runtime, ServerSocket serverSocket, String token) {
        this.runtime = runtime;
        this.serverSocket = serverSocket;
        this.token = token;
    }

    public static AgentControlServer start(final HotInjectionRuntime runtime) throws IOException {
        ServerSocket socket = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
        final AgentControlServer server = new AgentControlServer(runtime, socket, createToken());
        Thread acceptThread = new Thread(new Runnable() {
            @Override public void run() { server.acceptLoop(); }
        }, "Sigma-HotInjection-Control");
        acceptThread.setDaemon(true);
        acceptThread.start();
        return server;
    }

    public int getPort() { return serverSocket.getLocalPort(); }
    public String getToken() { return token; }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                final Socket socket = serverSocket.accept();
                Thread client = new Thread(new Runnable() {
                    @Override public void run() { handle(socket); }
                }, "Sigma-HotInjection-Control-Client");
                client.setDaemon(true);
                client.start();
            } catch (IOException ignored) {
                return;
            }
        }
    }

    private void handle(Socket socket) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            String hello = in.readLine();
            if (!("HELLO\t" + token).equals(hello)) {
                writeLine(out, "ERROR\tauth");
                return;
            }
            writeLine(out, "READY\t1\t" + runtime.getActiveVersion().getId());

            String line;
            while ((line = in.readLine()) != null) {
                if ("QUIT".equals(line)) {
                    writeLine(out, "BYE");
                    return;
                } else if ("PING".equals(line)) {
                    writeLine(out, "PONG");
                } else if ("MODULES".equals(line)) {
                    writeModules(out);
                } else if (line.startsWith("TOGGLE\t")) {
                    toggle(out, decode(line.substring(7)));
                } else if (line.startsWith("SET_ENABLED\t")) {
                    setEnabled(out, line);
                } else if (line.startsWith("SETTINGS\t")) {
                    writeSettings(out, decode(line.substring(9)));
                } else if (line.startsWith("SET\t")) {
                    setSetting(out, line);
                } else {
                    writeLine(out, "ERROR\tunknown-command");
                }
            }
        } catch (Throwable ignored) {
        } finally {
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    private void toggle(BufferedWriter out, String id) throws IOException {
        Module module = runtime.getModuleManager().get(id);
        if (module == null) {
            writeLine(out, "ERROR\tunknown-module");
            return;
        }
        module.toggle();
        writeLine(out, "STATE\t" + encode(module.getId()) + "\t" + module.isEnabled());
    }

    private void setEnabled(BufferedWriter out, String line) throws IOException {
        String[] parts = line.split("\\t", 3);
        if (parts.length < 3) {
            writeLine(out, "ERROR\tbad-command");
            return;
        }
        Module module = runtime.getModuleManager().get(decode(parts[1]));
        if (module == null) {
            writeLine(out, "ERROR\tunknown-module");
            return;
        }
        module.setEnabled(Boolean.parseBoolean(parts[2]));
        writeLine(out, "STATE\t" + encode(module.getId()) + "\t" + module.isEnabled());
    }

    private void writeModules(BufferedWriter out) throws IOException {
        for (Module module : runtime.getModuleManager().all()) {
            writeLine(out, "MODULE\t" + encode(module.getId())
                    + "\t" + encode(module.getName())
                    + "\t" + module.getCategory().name()
                    + "\t" + module.isEnabled()
                    + "\t" + encode(module.getDescription()));
        }
        writeLine(out, "END");
    }

    private void writeSettings(BufferedWriter out, String moduleId) throws IOException {
        Module module = runtime.getModuleManager().get(moduleId);
        if (module == null) {
            writeLine(out, "ERROR\tunknown-module");
            return;
        }
        for (ModuleSetting<?> setting : module.getSettings()) {
            StringBuilder options = new StringBuilder();
            String[] values = setting.getOptions();
            for (int i = 0; i < values.length; i++) {
                if (i > 0) options.append('\n');
                options.append(values[i]);
            }
            writeLine(out, "SETTING\t" + encode(setting.getId())
                    + "\t" + encode(setting.getName())
                    + "\t" + setting.getType().name()
                    + "\t" + encode(setting.serialize())
                    + "\t" + setting.getMin()
                    + "\t" + setting.getMax()
                    + "\t" + setting.getStep()
                    + "\t" + encode(options.toString())
                    + "\t" + encode(setting.getDescription()));
        }
        writeLine(out, "END");
    }

    private void setSetting(BufferedWriter out, String line) throws IOException {
        String[] parts = line.split("\\t", 4);
        if (parts.length < 4) {
            writeLine(out, "ERROR\tbad-command");
            return;
        }
        Module module = runtime.getModuleManager().get(decode(parts[1]));
        if (module == null) {
            writeLine(out, "ERROR\tunknown-module");
            return;
        }
        ModuleSetting<?> setting = module.getSetting(decode(parts[2]));
        if (setting == null) {
            writeLine(out, "ERROR\tunknown-setting");
            return;
        }
        try {
            setting.deserialize(decode(parts[3]));
            writeLine(out, "VALUE\t" + encode(setting.getId()) + "\t" + encode(setting.serialize()));
        } catch (RuntimeException error) {
            writeLine(out, "ERROR\tinvalid-value");
        }
    }

    private static void writeLine(BufferedWriter out, String value) throws IOException {
        out.write(value);
        out.newLine();
        out.flush();
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String createToken() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
