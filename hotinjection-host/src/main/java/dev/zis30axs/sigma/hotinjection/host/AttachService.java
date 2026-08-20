package dev.zis30axs.sigma.hotinjection.host;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import java.io.File;
import java.io.IOException;
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

        try {
            ProcessHandle.allProcesses().forEach(process -> {
                if (process.pid() == selfPid) {
                    return;
                }

                ProcessHandle.Info info = process.info();
                String command = info.command().orElse("");
                String commandLine = info.commandLine().orElse("");
                if (!isJavaProcess(command, commandLine)) {
                    return;
                }

                String pid = Long.toString(process.pid());
                String discoveredName = describeJavaProcess(command, commandLine);
                TargetJvm existing = targetsByPid.get(pid);
                if (existing == null || (!looksLikeMinecraft(existing.getDisplayName())
                        && looksLikeMinecraft(discoveredName))) {
                    targetsByPid.put(pid, new TargetJvm(pid, discoveredName));
                }
            });
        } catch (UnsupportedOperationException ignored) {
            // Some JVM/OS combinations may not expose ProcessHandle process enumeration.
        }

        List<TargetJvm> targets = new ArrayList<TargetJvm>(targetsByPid.values());
        Collections.sort(targets, new Comparator<TargetJvm>() {
            @Override
            public int compare(TargetJvm a, TargetJvm b) {
                boolean aMinecraft = looksLikeMinecraft(a.getDisplayName());
                boolean bMinecraft = looksLikeMinecraft(b.getDisplayName());
                if (aMinecraft != bMinecraft) {
                    return aMinecraft ? -1 : 1;
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

        StringBuilder options = new StringBuilder("source=host;notice=").append(showNotice);
        if (version != null && !version.trim().isEmpty() && !"auto".equalsIgnoreCase(version)) {
            options.append(";version=").append(version.trim());
        }

        VirtualMachine vm = VirtualMachine.attach(pid.trim());
        try {
            vm.loadAgent(agentJar.getCanonicalPath(), options.toString());
        } finally {
            vm.detach();
        }
    }

    private static boolean isJavaProcess(String command, String commandLine) {
        String executable = command == null ? "" : new File(command).getName().toLowerCase(Locale.ROOT);
        if ("java".equals(executable) || "java.exe".equals(executable)
                || "javaw".equals(executable) || "javaw.exe".equals(executable)) {
            return true;
        }

        String lowerLine = commandLine == null ? "" : commandLine.toLowerCase(Locale.ROOT);
        return lowerLine.contains("java.exe") || lowerLine.contains("javaw.exe")
                || lowerLine.startsWith("java ") || lowerLine.startsWith("javaw ");
    }

    private static String describeJavaProcess(String command, String commandLine) {
        String line = commandLine == null ? "" : commandLine.trim();
        if (looksLikeMinecraft(line)) {
            String version = extractMinecraftVersion(line);
            if (!version.isEmpty()) {
                return "Minecraft " + version + " (OS JVM scan)";
            }
            return "Minecraft (OS JVM scan)";
        }

        String main = findKnownMainClass(line);
        if (!main.isEmpty()) {
            return main + " (OS JVM scan)";
        }

        String executable = command == null ? "" : new File(command).getName();
        return executable.isEmpty() ? "Java process (OS JVM scan)" : executable + " (OS JVM scan)";
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
