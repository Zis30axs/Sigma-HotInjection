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
            if (isPid(descriptor.id(), selfPid)) {
                continue;
            }
            targetsByPid.put(descriptor.id(), new TargetJvm(descriptor.id(), descriptor.displayName()));
        }

        if (isWindows()) {
            scanWindowsJavaProcesses(targetsByPid, selfPid);
        } else {
            scanPortableJavaProcesses(targetsByPid, selfPid);
        }

        List<TargetJvm> targets = new ArrayList<TargetJvm>(targetsByPid.values());
        Collections.sort(targets, new Comparator<TargetJvm>() {
            @Override
            public int compare(TargetJvm a, TargetJvm b) {
                int scoreDifference = Integer.compare(score(b.getDisplayName()), score(a.getDisplayName()));
                if (scoreDifference != 0) {
                    return scoreDifference;
                }
                return Long.compare(pidAsLong(a), pidAsLong(b));
            }
        });
        return Collections.unmodifiableList(targets);
    }

    public void attach(String pid, File agentJar, String version, boolean showNotice) throws Exception {
        if (pid == null || pid.trim().isEmpty()) {
            throw new IllegalArgumentException("PID is required");
        }
        if (agentJar == null || !agentJar.isFile()) {
            throw new IOException("Agent JAR not found: " + agentJar);
        }

        File ackFile = File.createTempFile("sigma-hotinjection-ack-", ".txt");
        if (!ackFile.delete()) {
            ackFile.deleteOnExit();
        }
        ackFile.deleteOnExit();

        StringBuilder options = new StringBuilder("source=host;notice=").append(showNotice)
                .append(";ack=").append(ackFile.getAbsolutePath());
        if (version != null && !version.trim().isEmpty() && !"auto".equalsIgnoreCase(version)) {
            options.append(";version=").append(version.trim());
        }

        VirtualMachine vm = VirtualMachine.attach(pid.trim());
        Exception loadFailure = null;
        try {
            vm.loadAgent(agentJar.getCanonicalPath(), options.toString());
        } catch (Exception error) {
            loadFailure = error;
        } finally {
            vm.detach();
        }

        if (acknowledged(ackFile)) {
            return;
        }
        if (loadFailure != null) {
            throw loadFailure;
        }
        throw new IOException("Agent attach returned without a Sigma initialization acknowledgement.");
    }

    private static boolean acknowledged(File ackFile) {
        if (ackFile == null || !ackFile.isFile()) {
            return false;
        }
        try {
            List<String> lines = Files.readAllLines(ackFile.toPath(), StandardCharsets.UTF_8);
            return !lines.isEmpty() && "OK".equals(lines.get(0).trim());
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void scanWindowsJavaProcesses(Map<String, TargetJvm> targetsByPid, long selfPid) {
        Process process = null;
        BufferedReader reader = null;
        try {
            process = new ProcessBuilder("tasklist.exe", "/FO", "CSV", "/NH")
                    .redirectErrorStream(true)
                    .start();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.defaultCharset()));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] row = parseTasklistRow(line);
                if (row == null) {
                    continue;
                }

                String executable = row[0].toLowerCase(Locale.ROOT);
                if (!isJavaExecutable(executable)) {
                    continue;
                }

                long numericPid;
                try {
                    numericPid = Long.parseLong(row[1]);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                if (numericPid == selfPid) {
                    continue;
                }

                String pid = Long.toString(numericPid);
                TargetJvm existing = targetsByPid.get(pid);
                String commandLine = readCommandLine(numericPid);
                String discoveredName = describeJavaProcess(executable, commandLine);
                if (existing == null || score(discoveredName) > score(existing.getDisplayName())) {
                    targetsByPid.put(pid, new TargetJvm(pid, discoveredName));
                }
            }
            process.waitFor();
        } catch (Exception ignored) {
            scanPortableJavaProcesses(targetsByPid, selfPid);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static void scanPortableJavaProcesses(Map<String, TargetJvm> targetsByPid, long selfPid) {
        try {
            ProcessHandle.allProcesses().forEach(process -> {
                if (process.pid() == selfPid) {
                    return;
                }
                ProcessHandle.Info info = process.info();
                String executable = executableName(info.command().orElse(""));
                if (!isJavaExecutable(executable)) {
                    return;
                }
                String pid = Long.toString(process.pid());
                String discoveredName = describeJavaProcess(executable, info.commandLine().orElse(""));
                TargetJvm existing = targetsByPid.get(pid);
                if (existing == null || score(discoveredName) > score(existing.getDisplayName())) {
                    targetsByPid.put(pid, new TargetJvm(pid, discoveredName));
                }
            });
        } catch (UnsupportedOperationException ignored) {
            // Attach API results remain available if OS process enumeration is unsupported.
        }
    }

    private static String readCommandLine(long pid) {
        try {
            Optional<ProcessHandle> process = ProcessHandle.of(pid);
            return process.isPresent() ? process.get().info().commandLine().orElse("") : "";
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static String[] parseTasklistRow(String line) {
        if (line == null || line.isEmpty() || line.charAt(0) != '"') {
            return null;
        }
        int firstSeparator = line.indexOf("\",\"");
        if (firstSeparator < 0) {
            return null;
        }
        int secondStart = firstSeparator + 3;
        int secondEnd = line.indexOf('"', secondStart);
        if (secondEnd < 0) {
            return null;
        }
        String executable = line.substring(1, firstSeparator);
        String pid = line.substring(secondStart, secondEnd);
        return new String[] { executable, pid };
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String executableName(String command) {
        return command == null || command.isEmpty()
                ? ""
                : new File(command).getName().toLowerCase(Locale.ROOT);
    }

    private static boolean isJavaExecutable(String executable) {
        return "java".equals(executable) || "java.exe".equals(executable)
                || "javaw".equals(executable) || "javaw.exe".equals(executable);
    }

    private static String describeJavaProcess(String executable, String commandLine) {
        String line = commandLine == null ? "" : commandLine.trim();
        if (looksLikeMinecraft(line)) {
            String version = extractMinecraftVersion(line);
            if (!version.isEmpty()) {
                return "Minecraft " + version + " [" + executable + "]";
            }
            return "Minecraft [" + executable + "]";
        }

        String main = findKnownMainClass(line);
        if (!main.isEmpty()) {
            return main + " [" + executable + "]";
        }

        return (executable.isEmpty() ? "Java process" : executable) + " (OS JVM scan)";
    }

    private static int score(String value) {
        if (looksLikeMinecraft(value)) {
            return 40;
        }
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (lower.contains("javaw.exe") || lower.contains("[javaw")) {
            return 30;
        }
        if (lower.contains("java.exe") || lower.contains("[java")) {
            return 20;
        }
        return 10;
    }

    private static boolean looksLikeMinecraft(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("minecraft")
                || lower.contains("net.minecraft.client.main.main")
                || lower.contains("net.minecraft.launchwrapper.launch")
                || lower.contains("net.fabricmc.loader")
                || lower.contains("cpw.mods.modlauncher")
                || lower.contains("--assetindex")
                || lower.contains("--assetsdir")
                || lower.contains(".minecraft");
    }

    private static String extractMinecraftVersion(String commandLine) {
        Matcher matcher = VERSION_ARGUMENT.matcher(commandLine);
        if (!matcher.find()) {
            return "";
        }
        String quoted = matcher.group(1);
        return quoted != null ? quoted : Optional.ofNullable(matcher.group(2)).orElse("");
    }

    private static String findKnownMainClass(String commandLine) {
        String[] known = {
                "net.minecraft.client.main.Main",
                "net.minecraft.launchwrapper.Launch",
                "net.fabricmc.loader.impl.launch.knot.KnotClient",
                "cpw.mods.modlauncher.Launcher"
        };
        for (String candidate : known) {
            if (commandLine.contains(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    private static boolean isPid(String value, long pid) {
        try {
            return Long.parseLong(value) == pid;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static long pidAsLong(TargetJvm target) {
        try {
            return Long.parseLong(target.getPid());
        } catch (NumberFormatException ignored) {
            return Long.MAX_VALUE;
        }
    }
}
