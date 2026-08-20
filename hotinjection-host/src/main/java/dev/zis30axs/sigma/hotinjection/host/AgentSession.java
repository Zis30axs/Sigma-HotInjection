package dev.zis30axs.sigma.hotinjection.host;

import dev.zis30axs.sigma.hotinjection.host.model.RemoteBox;
import dev.zis30axs.sigma.hotinjection.host.model.RemoteModule;
import dev.zis30axs.sigma.hotinjection.host.model.RemoteSetting;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

public final class AgentSession implements Closeable {
    private final Socket socket;
    private final BufferedReader input;
    private final BufferedWriter output;
    private final String version;

    private AgentSession(Socket socket, BufferedReader input, BufferedWriter output, String version) {
        this.socket = socket;
        this.input = input;
        this.output = output;
        this.version = version;
    }

    public static AgentSession connect(int port, String token) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1", port), 1800);
        socket.setSoTimeout(3000);
        BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        BufferedWriter output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        writeLine(output, "HELLO\t" + token);
        String ready = input.readLine();
        if (ready == null || !ready.startsWith("READY\t")) {
            socket.close();
            throw new IOException("Agent control handshake failed: " + ready);
        }
        String[] parts = ready.split("\\t", 3);
        return new AgentSession(socket, input, output, parts.length >= 3 ? parts[2] : "unknown");
    }

    public String getVersion() { return version; }

    public synchronized List<RemoteModule> listModules() throws IOException {
        writeLine(output, "MODULES");
        List<RemoteModule> result = new ArrayList<RemoteModule>();
        String line;
        while ((line = input.readLine()) != null) {
            if ("END".equals(line)) break;
            if (line.startsWith("ERROR\t")) throw new IOException(line.substring(6));
            if (!line.startsWith("MODULE\t")) continue;
            String[] parts = line.split("\\t", 6);
            if (parts.length < 6) continue;
            result.add(new RemoteModule(
                    decode(parts[1]), decode(parts[2]), parts[3], Boolean.parseBoolean(parts[4]), decode(parts[5])));
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized List<RemoteSetting> listSettings(String moduleId) throws IOException {
        writeLine(output, "SETTINGS\t" + encode(moduleId));
        List<RemoteSetting> result = new ArrayList<RemoteSetting>();
        String line;
        while ((line = input.readLine()) != null) {
            if ("END".equals(line)) break;
            if (line.startsWith("ERROR\t")) throw new IOException(line.substring(6));
            if (!line.startsWith("SETTING\t")) continue;
            String[] parts = line.split("\\t", 10);
            if (parts.length < 10) continue;
            List<String> options = new ArrayList<String>();
            String rawOptions = decode(parts[8]);
            if (!rawOptions.isEmpty()) Collections.addAll(options, rawOptions.split("\\n", -1));
            result.add(new RemoteSetting(
                    decode(parts[1]), decode(parts[2]), parts[3], decode(parts[4]),
                    parseDouble(parts[5]), parseDouble(parts[6]), parseDouble(parts[7]), options, decode(parts[9])));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Requests one overlay frame. The aspect ratio of the overlay window is sent
     * along so the Agent can project world geometry without knowing the window.
     */
    public synchronized List<RemoteBox> listOverlay(double aspectRatio) throws IOException {
        writeLine(output, "OVERLAY\t" + aspectRatio);
        List<RemoteBox> result = new ArrayList<RemoteBox>();
        String line;
        while ((line = input.readLine()) != null) {
            if ("END".equals(line)) break;
            if (line.startsWith("ERROR\t")) throw new IOException(line.substring(6));
            if (!line.startsWith("BOX\t")) continue;
            String[] parts = line.split("\\t", 7);
            if (parts.length < 7) continue;
            result.add(new RemoteBox(
                    parseDouble(parts[1]), parseDouble(parts[2]),
                    parseDouble(parts[3]), parseDouble(parts[4]),
                    parseArgb(parts[5]), decode(parts[6])));
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized boolean setEnabled(String moduleId, boolean enabled) throws IOException {
        writeLine(output, "SET_ENABLED\t" + encode(moduleId) + "\t" + enabled);
        String line = input.readLine();
        if (line == null) throw new IOException("Agent control channel closed");
        if (line.startsWith("ERROR\t")) throw new IOException(line.substring(6));
        String[] parts = line.split("\\t", 3);
        return parts.length >= 3 && Boolean.parseBoolean(parts[2]);
    }

    public synchronized String setSetting(String moduleId, String settingId, String value) throws IOException {
        writeLine(output, "SET\t" + encode(moduleId) + "\t" + encode(settingId) + "\t" + encode(value));
        String line = input.readLine();
        if (line == null) throw new IOException("Agent control channel closed");
        if (line.startsWith("ERROR\t")) throw new IOException(line.substring(6));
        String[] parts = line.split("\\t", 3);
        return parts.length >= 3 ? decode(parts[2]) : value;
    }

    @Override
    public synchronized void close() throws IOException {
        try {
            if (!socket.isClosed()) writeLine(output, "QUIT");
        } finally {
            socket.close();
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

    private static double parseDouble(String value) {
        try { return Double.parseDouble(value); } catch (NumberFormatException ignored) { return 0.0D; }
    }

    private static int parseArgb(String value) {
        try { return (int) Long.parseLong(value.trim(), 16); } catch (NumberFormatException ignored) { return 0xFFFFFFFF; }
    }
}
