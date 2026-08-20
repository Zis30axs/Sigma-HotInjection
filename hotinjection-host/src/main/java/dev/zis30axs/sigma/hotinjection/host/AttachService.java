package dev.zis30axs.sigma.hotinjection.host;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AttachService {
    private static final Pattern VERSION_ARGUMENT = Pattern.compile(
            "(?i)(?:--version(?:=|\\s+))(?:\\\"([^\\\"]+)\\\"|(\\S+))");

    public List<TargetJvm> listTargets() {
        final long selfPid = ProcessHandle.current().pid();
        final Map<String, TargetJvm> targetsByPid = new LinkedHashMap<String, TargetJvm>();
        for (VirtualMachineDescriptor descriptor : VirtualMachine.list()) {
            if (isPid(descriptor.id(), selfPid)) continue;
            targetsByPid.put(descriptor.id(), new TargetJvm(descriptor.id(), descriptor.displayName()));
        }
        if (isWindows()) scanWindowsJavaProcesses(targetsByPid, selfPid);
        else scanPortableJavaProcesses(targetsByPid, selfPid);
        List<TargetJvm> targets = new ArrayList<TargetJvm>(targetsByPid.values());
        Collections.sort(targets, new Comparator<TargetJvm>() {
            @Override public int compare(TargetJvm a, TargetJvm b) {
                int scoreDifference = Integer.compare(score(b.getDisplayName()), score(a.getDisplayName()));
                if (scoreDifference != 0) return scoreDifference;
                return Long.compare(pidAsLong(a), pidAsLong(b));
            }
        });
        return Collections.unmodifiableList(targets);
    }

    public void attach(String pid, File agentJar, String version, boolean showNotice) throws Exception {
        performAttach(pid, agentJar, version, showNotice, true, null);
    }
    public void attach(String pid, File agentJar, String version, boolean showNotice, boolean clickGui) throws Exception {
        performAttach(pid, agentJar, version, showNotice, clickGui, null);
    }
    public void attach(String pid, File agentJar, String version, boolean showNotice, boolean clickGui,
                       String extraOptions) throws Exception {
        performAttach(pid, agentJar, version, showNotice, clickGui, extraOptions);
    }

    public AgentSession attachSession(String pid, File agentJar, String version, boolean showNotice) throws Exception {
        AckInfo ack = performAttach(pid, agentJar, version, showNotice, true, null);
        if (ack.port <= 0 || ack.token.isEmpty()) {
            throw new IOException("Agent initialized, but its local control channel was not available.");
        }
        return AgentSession.connect(ack.port, ack.token);
    }

    private AckInfo performAttach(String pid, File agentJar, String version, boolean showNotice,
                                  boolean clickGui, String extraOptions) throws Exception {
        if (pid == null || pid.trim().isEmpty()) throw new IllegalArgumentException("PID is required");
        if (agentJar == null || !agentJar.isFile()) throw new IOException("Agent JAR not found: " + agentJar);
        File ackFile = File.createTempFile("sigma-hotinjection-ack-", ".txt");
        if (!ackFile.delete()) ackFile.deleteOnExit();
        ackFile.deleteOnExit();
        StringBuilder options = new StringBuilder("source=host;notice=").append(showNotice)
                .append(";clickgui=").append(clickGui)
                .append(";ack=").append(ackFile.getAbsolutePath());
        if (extraOptions != null && !extraOptions.trim().isEmpty()) options.append(';').append(extraOptions.trim());
        if (version != null && !version.trim().isEmpty() && !"auto".equalsIgnoreCase(version)) {
            options.append(";version=").append(version.trim());
        }

        VirtualMachine vm = VirtualMachine.attach(pid.trim());
        Exception loadFailure = null;
        try { vm.loadAgent(agentJar.getCanonicalPath(), options.toString()); }
        catch (Exception error) { loadFailure = error; }
        finally { vm.detach(); }

        AckInfo ack = waitForAcknowledgement(ackFile, 1800L);
        if (ack.ok) return ack;
        if (loadFailure != null) throw loadFailure;
        throw new IOException("Agent attach returned without a Sigma initialization acknowledgement.");
    }

    private static AckInfo waitForAcknowledgement(File ackFile, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        AckInfo info;
        do {
            info = readAcknowledgement(ackFile);
            if (info.ok) return info;
            try { Thread.sleep(60L); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); return info; }
        } while (System.currentTimeMillis() < deadline);
        return readAcknowledgement(ackFile);
    }

    private static AckInfo readAcknowledgement(File ackFile) {
        if (ackFile == null || !ackFile.isFile()) return AckInfo.EMPTY;
        try {
            List<String> lines = Files.readAllLines(ackFile.toPath(), StandardCharsets.UTF_8);
            if (lines.isEmpty() || !"OK".equals(lines.get(0).trim())) return AckInfo.EMPTY;
            int port = -1;
            String token = "";
            String version = "unknown";
            for (String line : lines) {
                int equals = line.indexOf('=');
                if (equals <= 0) continue;
                String key = line.substring(0, equals).trim();
                String value = line.substring(equals + 1).trim();
                if ("port".equals(key)) {
                    try { port = Integer.parseInt(value); } catch (NumberFormatException ignored) { }
                } else if ("token".equals(key)) token = value;
                else if ("version".equals(key)) version = value;
            }
            return new AckInfo(true, port, token, version);
        } catch (IOException ignored) { return AckInfo.EMPTY; }
    }

    private static void scanWindowsJavaProcesses(Map<String, TargetJvm> targetsByPid, long selfPid) {
        Process process = null;
        BufferedReader reader = null;
        try {
            process = new ProcessBuilder("tasklist.exe", "/FO", "CSV", "/NH").redirectErrorStream(true).start();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.defaultCharset()));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] row = parseTasklistRow(line);
                if (row == null) continue;
                String executable = row[0].toLowerCase(Locale.ROOT);
                if (!isJavaExecutable(executable)) continue;
                long numericPid;
                try { numericPid = Long.parseLong(row[1]); } catch (NumberFormatException ignored) { continue; }
                if (numericPid == selfPid) continue;
                String pid = Long.toString(numericPid);
                TargetJvm existing = targetsByPid.get(pid);
                String discoveredName = describeJavaProcess(executable, readCommandLine(numericPid));
                if (existing == null || score(discoveredName) > score(existing.getDisplayName())) {
                    targetsByPid.put(pid, new TargetJvm(pid, discoveredName));
                }
            }
            process.waitFor();
        } catch (Exception ignored) {
            scanPortableJavaProcesses(targetsByPid, selfPid);
        } finally {
            if (reader != null) try { reader.close(); } catch (IOException ignored) { }
            if (process != null) process.destroy();
        }
    }

    private static void scanPortableJavaProcesses(Map<String, TargetJvm> targetsByPid, long selfPid) {
        try {
            ProcessHandle.allProcesses().forEach(process -> {
                if (process.pid() == selfPid) return;
                ProcessHandle.Info info = process.info();
                String executable = executableName(info.command().orElse(""));
                if (!isJavaExecutable(executable)) return;
                String pid = Long.toString(process.pid());
                String discoveredName = describeJavaProcess(executable, info.commandLine().orElse(""));
                TargetJvm existing = targetsByPid.get(pid);
                if (existing == null || score(discoveredName) > score(existing.getDisplayName())) {
                    targetsByPid.put(pid, new TargetJvm(pid, discoveredName));
                }
            });
        } catch (UnsupportedOperationException ignored) { }
    }

    private static String readCommandLine(long pid) {
        try {
            Optional<ProcessHandle> process = ProcessHandle.of(pid);
            return process.isPresent() ? process.get().info().commandLine().orElse("") : "";
        } catch (RuntimeException ignored) { return ""; }
    }

    private static String[] parseTasklistRow(String line) {
        if (line == null || line.isEmpty() || line.charAt(0) != '"') return null;
        int firstSeparator = line.indexOf("\",\"");
        if (firstSeparator < 0) return null;
        int secondStart = firstSeparator + 3;
        int secondEnd = line.indexOf('"', secondStart);
        if (secondEnd < 0) return null;
        return new String[] { line.substring(1, firstSeparator), line.substring(secondStart, secondEnd) };
    }

    private static boolean isWindows() { return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"); }
    private static String executableName(String command) {
        return command == null || command.isEmpty() ? "" : new File(command).getName().toLowerCase(Locale.ROOT);
    }
    private static boolean isJavaExecutable(String executable) {
        return "java".equals(executable) || "java.exe".equals(executable)
                || "javaw".equals(executable) || "javaw.exe".equals(executable);
    }
    private static String describeJavaProcess(String executable, String commandLine) {
        String line = commandLine == null ? "" : commandLine.trim();
        if (looksLikeMinecraft(line)) {
            String version = extractMinecraftVersion(line);
            return version.isEmpty() ? "Minecraft [" + executable + "]" : "Minecraft " + version + " [" + executable + "]";
        }
        String main = findKnownMainClass(line);
        if (!main.isEmpty()) return main + " [" + executable + "]";
        return (executable.isEmpty() ? "Java process" : executable) + " (OS JVM scan)";
    }
    private static int score(String value) {
        if (looksLikeMinecraft(value)) return 40;
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (lower.contains("javaw.exe") || lower.contains("[javaw")) return 30;
        if (lower.contains("java.exe") || lower.contains("[java")) return 20;
        return 10;
    }
    private static boolean looksLikeMinecraft(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("minecraft") || lower.contains("net.minecraft.client.main.main")
                || lower.contains("net.minecraft.launchwrapper.launch") || lower.contains("net.fabricmc.loader")
                || lower.contains("cpw.mods.modlauncher") || lower.contains("--assetindex")
                || lower.contains("--assetsdir") || lower.contains(".minecraft");
    }
    private static String extractMinecraftVersion(String commandLine) {
        Matcher matcher = VERSION_ARGUMENT.matcher(commandLine);
        if (!matcher.find()) return "";
        String quoted = matcher.group(1);
        return quoted != null ? quoted : Optional.ofNullable(matcher.group(2)).orElse("");
    }
    private static String findKnownMainClass(String commandLine) {
        String[] known = { "net.minecraft.client.main.Main", "net.minecraft.launchwrapper.Launch",
                "net.fabricmc.loader.impl.launch.knot.KnotClient", "cpw.mods.modlauncher.Launcher" };
        for (String candidate : known) if (commandLine.contains(candidate)) return candidate;
        return "";
    }
    private static boolean isPid(String value, long pid) {
        try { return Long.parseLong(value) == pid; } catch (NumberFormatException ignored) { return false; }
    }
    private static long pidAsLong(TargetJvm target) {
        try { return Long.parseLong(target.getPid()); } catch (NumberFormatException ignored) { return Long.MAX_VALUE; }
    }

    private static final class AckInfo {
        private static final AckInfo EMPTY = new AckInfo(false, -1, "", "unknown");
        private final boolean ok;
        private final int port;
        private final String token;
        @SuppressWarnings("unused") private final String version;
        private AckInfo(boolean ok, int port, String token, String version) {
            this.ok = ok;
            this.port = port;
            this.token = token;
            this.version = version;
        }
    }
}
